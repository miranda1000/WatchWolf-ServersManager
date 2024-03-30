package dev.watchwolf.serversmanager;

import dev.watchwolf.core.entities.WorldType;
import dev.watchwolf.core.entities.files.plugins.Plugin;
import dev.watchwolf.core.rpc.stubs.serversmanager.CapturedExceptionEvent;
import dev.watchwolf.core.rpc.stubs.serversmanager.ServerStartedEvent;
import dev.watchwolf.core.rpc.stubs.serversmanager.ServersManagerPetitions;
import dev.watchwolf.serversmanager.rpc.RequesteeIpGetter;
import dev.watchwolf.serversmanager.rpc.ServersManagerLocalImplementation;
import dev.watchwolf.serversmanager.server.ServersManager;
import dev.watchwolf.serversmanager.server.instantiator.DockerizedServerInstantiator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.watchwolf.serversmanager.ITServersManagerRPCShould.killAllDockerServers;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10*60)
public class ITServersManagerShould {
    public static final int MINUTES_WAIT_FOR_SERVER_TO_START_UNTIL_TIMEOUT = 7;

    public static class LocalIpRequesteeMock implements RequesteeIpGetter {
        @Override
        public InetSocketAddress getRequesteeIp() {
            return new InetSocketAddress("127.0.0.1", 45000);
        }
    }

    public static String startServer(String version, ServerStartedEvent onServerStart, CapturedExceptionEvent capturedExceptionEventManager) throws IOException {
        RequesteeIpGetter ipGetter = new LocalIpRequesteeMock();

        ServersManager serversManager = new ServersManager(new DockerizedServerInstantiator());
        ServersManagerPetitions serversManagerPetitions = new ServersManagerLocalImplementation(serversManager, onServerStart, capturedExceptionEventManager, ipGetter);
        ArrayList<Plugin> plugins = new ArrayList<>();
        String seed = "1";

        // start the server
        return serversManagerPetitions.startServer("Spigot", version, plugins, WorldType.FLAT, seed, new ArrayList<>(), new ArrayList<>());
    }

    public static String startServer(String version, ServerStartedEvent onServerStart) throws IOException {
        CapturedExceptionEvent capturedExceptionEventManager = (err) -> {
            System.err.println("[e] Error: " + err);
        };

        return startServer(version, onServerStart, capturedExceptionEventManager);
    }

    @Test
    public void startAServer() throws Exception {
        try {
            final AtomicBoolean started = new AtomicBoolean(false);

            ServerStartedEvent serverStartedEventManager = () -> {
                System.out.println("[!] Server started");
                synchronized (started) {
                    if (started.get()) throw new RuntimeException("Got two 'server started' events");
                    started.set(true);
                    started.notify();
                }
            };

            // start the server
            String serverIp = startServer("1.19", serverStartedEventManager);
            assertNotEquals("", serverIp, "Expected a server IP; got error instead");

            // wait for server to notify
            int timeout = MINUTES_WAIT_FOR_SERVER_TO_START_UNTIL_TIMEOUT * 60_000;
            synchronized (started) {
                started.wait(timeout);
                assertTrue(started.get(), "Didn't get server started event");
            }
        } finally {
            assertTrue(killAllDockerServers().length > 0, "Expected (at least) one server to be closed; got 0 instead"); // TODO close server without killing docker
            // TODO clear folder
        }
    }

    @Test
    public void reportErrorIfDesiredServerNotPresent() throws Exception {
        String expectedError = "Couldn't start a Spigot server, on 1.0";

        ServerStartedEvent serverStartedEventManager = () -> {
            throw new RuntimeException("Got 'server started' even when it should be impossible to start this server");
        };

        final ArrayList<String> errors = new ArrayList<>();
        CapturedExceptionEvent capturedExceptionEventManager = (err) -> {
            System.err.println("[e] Error: " + err);
            synchronized (errors) {
                errors.add(err);
                errors.notify();
            }
        };

        // (try to) start the server
        String serverIp = startServer("1.0", serverStartedEventManager, capturedExceptionEventManager); // TODO close server
        assertEquals("", serverIp, "Expected error while starting the server; got IP instead");

        // wait for server to notify
        boolean foundError = false;
        long startingAt = System.currentTimeMillis();
        int timeout = MINUTES_WAIT_FOR_SERVER_TO_START_UNTIL_TIMEOUT*60_000;
        while (!foundError && (System.currentTimeMillis() - startingAt) < timeout) {
            try {
                synchronized (errors) {
                    errors.wait(5_000);
                    if (errors.get(errors.size()-1).startsWith(expectedError)) {
                        foundError = true;
                    }
                }
            } catch (InterruptedException ignore) {} // 5s elapsed; try again later
        }

        synchronized (errors) {
            assertTrue(foundError, "Couldn't get the expected error '" + expectedError + " [...]'. List of errors got: " + errors.toString());
        }
    }
}
