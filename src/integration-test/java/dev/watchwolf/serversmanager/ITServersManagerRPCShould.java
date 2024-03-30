package dev.watchwolf.serversmanager;

import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerMount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static dev.watchwolf.serversmanager.server.ServerRequirements.getPrivateServerFolder;
import static dev.watchwolf.serversmanager.server.instantiator.ITDockerizedServerInstantiatorShould.getDockerClient;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(2*60)
public class ITServersManagerRPCShould {
    private static Logger LOGGER = LogManager.getLogger(ITServersManagerRPCShould.class.getName());

    private Thread mainThread;
    private ArrayList<Throwable> mainThreadExceptions;

    public static byte []getStartServerSequence() {
        return new byte[]{
                // target: servers manager ; return: no ; operation: start server
                0b0001_0_000, 0b00000000,
                // server type (String; "Spigot")
                0x06, 0x00, // 6 character-long string
                'S', 'p', 'i', 'g', 'o', 't',
                // server version (String; "1.19")
                0x04, 0x00, // 4 character-long string
                '1', '.', '1', '9',
                // plugins (empty)
                0x00, 0x00,
                // server type (flat)
                0x01, 0x00,
                // seed (none)
                0x00, 0x00,
                // maps & config files (arrays; all empty)
                0x00, 0x00,
                0x00, 0x00
        };
    }

    public static String getString(BufferedReader in, int lenght) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < lenght; n++) sb.append((char)in.read());
        return sb.toString();
    }

    public static Thread getJarMainRunnable(final ArrayList<Throwable> exceptions) {
        return new Thread(() -> {
            try {
                ServersManager.main(new String[]{});
            } catch (Throwable ex) {
                ex.printStackTrace();
                if (exceptions != null) {
                    synchronized (exceptions) {
                        exceptions.add(ex);
                    }
                }
            }
        });
    }

    public static void stopJarMainRunnable() {
        ServersManager.stop();
    }

    public static Socket waitUntilReadyAndConnect(String host, int port) throws TimeoutException {
        int tries = 10;
        while(tries > 0) {
            try {
                Socket socket = new Socket(host, port);
                socket.setSoTimeout(20_000); // if no data in 20s, raise timeout
                return socket;
            } catch(IOException ex) {
                LOGGER.debug("Couldn't connect to " + host + ":" + port + "; trying again after 2s...");
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ignore) {}
                tries--;
            }
        }
        throw new TimeoutException("Exceeded max retries to connect to " + host + ":" + port);
    }

    public static String[] killAllDockerServers() {
        List<String> serverFolderPaths = new ArrayList<>();

        LOGGER.debug("Stopping all server dockers...");
        ListContainersCmd listContainersCmd = getDockerClient().listContainersCmd()
                        .withNameFilter(Collections.singletonList("MC_Server-*"));

        List<Container> servers = listContainersCmd.exec();
        LOGGER.debug("Server dockers got: " + servers.toString());
        for (Container container : servers) {
            // get the folder that server was using
            for (ContainerMount mount : container.getMounts()) {
                if (!mount.getDestination().equals("/server")) LOGGER.warn("Got non-server mount: " + mount.getSource());

                /*serverFolderPaths.add(mount.getSource());*/ // getSource will return a different folder due to how WSL Docker works
            }
            if (container.getNames().length != 1) LOGGER.warn("Got different than 1 container name: " + Arrays.toString(container.getNames()));
            else serverFolderPaths.add(getPrivateServerFolder(container.getNames()[0].replace("MC_Server-", "")));

            // kill the server
            LOGGER.debug("Stopping container " + container.getId() + "...");
            try {
                getDockerClient().killContainerCmd(container.getId()).exec();
            } catch (Exception ignore) {}
        }

        return LOGGER.traceExit(serverFolderPaths.toArray(new String[0]));
    }

    public static int getByte(BufferedReader in, int timeout) throws TimeoutException,IOException {
        Integer val = null;
        long startingAt = System.currentTimeMillis();
        while (val == null && (System.currentTimeMillis() - startingAt) < timeout) {
            try {
                val = in.read();
            } catch (SocketTimeoutException ignore) {}
        }

        if (val == null) throw new TimeoutException("Couldn't get one byte in " + timeout + "ms");
        return val;
    }

    @BeforeEach
    public void launchJarRunnable() {
        LOGGER.info("Launching main thread...");

        // launch ServersManager.main in separate thread
        this.mainThreadExceptions = new ArrayList<>();
        this.mainThread = getJarMainRunnable(this.mainThreadExceptions);
        this.mainThread.start();
    }

    @AfterEach
    public void cleanup() throws Throwable {
        LOGGER.traceEntry();
        ArrayList<Throwable> exceptions;
        synchronized (this.mainThreadExceptions) {
            exceptions = new ArrayList<>(this.mainThreadExceptions);
        }

        LOGGER.info("Waiting for main thread to exit...");
        stopJarMainRunnable();

        if (!exceptions.isEmpty()) {
            LOGGER.warn("Got exceptions on main thread:");
            for (Throwable ex : exceptions) LOGGER.warn(ex);

            throw exceptions.get(0); // the first exception is most provably the one that caused the crash
        }

        mainThread.join(8_000);
        assertFalse(mainThread.isAlive(), "Expected jar thread to be stopped; got otherwise instead");
    }

    @Test
    public void triggerAServerStartAfterStartServerRPCSequence() throws Exception {
        killAllDockerServers(); // just in case, as this may create conflicts on the "clear folder" check

        String []serverFolders = null;
        try(Socket clientSocket = waitUntilReadyAndConnect("127.0.0.1", 8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            // send hardcoded "Start Server" sequence
            LOGGER.info("Sending 'start server' request...");
            clientSocket.getOutputStream().write(getStartServerSequence());

            // we expect to get the IP
            int startPacket = getByte(in, 20_000);

            // from: servers manager ; return: yes ; operation: start server
            assertEquals(0b0001_1_000, startPacket, "We were expecting a return from servers manager of the 'start server' request; got " + System.out.format("%08d%n", startPacket) + " instead");
            assertEquals(0b00000000, in.read());
            int ipLength = in.read() | (in.read()<<8);
            LOGGER.debug("IP size: " + ipLength);
            assertTrue(ipLength > 0, "Empty (or invalid array) got");
            String ip = getString(in, ipLength);
            LOGGER.debug("IP got: " + ip);
            assertEquals(8001, Integer.valueOf(ip.split(":")[1]));
        } finally {
            // killing the server docker should also be treated as 'server closed'
            LOGGER.info("docker cleanup");
            serverFolders = killAllDockerServers();
        }

        // are the server contents clear?
        assertTrue(serverFolders.length > 0, "Expected (at least) one server to be closed; got 0 instead");
        Thread.sleep(5_000); // wait for the event to reach WW-ServersManager TODO maybe we could get the 'server stopped' event?
        for (String serverFolder : serverFolders) {
            LOGGER.debug("Checking if folder " + serverFolder + " was deleted...");
            assertFalse(new File(serverFolder).getCanonicalFile().exists(), "Expected server folder to be clear; got existing folder instead");
        }
    }

    @Test
    public void supportMultipleSessions() throws Exception {
        try(Socket clientSocket = waitUntilReadyAndConnect("127.0.0.1", 8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            Socket client2Socket = waitUntilReadyAndConnect("127.0.0.1", 8000);
            BufferedReader in2 = new BufferedReader(new InputStreamReader(client2Socket.getInputStream()))) {
            // send hardcoded "Start Server" sequence
            LOGGER.info("Sending 'start server' request...");
            clientSocket.getOutputStream().write(getStartServerSequence());
            client2Socket.getOutputStream().write(getStartServerSequence());

            // we expect to get the IP
            int startPacket = getByte(in, 20_000);
            int startPacket2 = getByte(in2, 20_000);

            assertEquals(0b0001_1_000, startPacket, "We were expecting a return from servers manager of the 'start server' request; got " + System.out.format("%08d%n", startPacket) + " instead");
            assertEquals(0b00000000, in.read());
            int ipLength = in.read() | (in.read()<<8);
            assertTrue(ipLength > 0, "Empty (or invalid array) got on client 1");
            LOGGER.debug("Succeed reading the first `startServer` request");

            assertEquals(0b0001_1_000, startPacket2, "We were expecting a return from servers manager of the 'start server' request from the second client; got " + System.out.format("%08d%n", startPacket2) + " instead");
            assertEquals(0b00000000, in2.read());
            ipLength = in2.read() | (in2.read()<<8);
            assertTrue(ipLength > 0, "Empty (or invalid array) got on client 2");
            LOGGER.debug("Succeed reading the second `startServer` request");
        } finally {
            LOGGER.info("docker cleanup");
            killAllDockerServers();
        }
    }
}
