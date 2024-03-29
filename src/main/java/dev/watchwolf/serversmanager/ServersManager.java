package dev.watchwolf.serversmanager;

import dev.watchwolf.core.rpc.RPC;
import dev.watchwolf.core.rpc.RPCFactory;
import dev.watchwolf.core.rpc.channel.MessageChannel;
import dev.watchwolf.core.rpc.channel.sockets.server.ServerSocketChannelFactory;
import dev.watchwolf.serversmanager.rpc.ServersManagerLocalFactory;
import dev.watchwolf.serversmanager.server.instantiator.DockerizedServerInstantiator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServersManager {
    /**
     * Use port 8000 for Servers Manager
     */
    public static final int SERVERS_MANAGER_PORT = (DockerizedServerInstantiator.BASE_PORT - 1);

    private static Logger logger = LogManager.getLogger(ServersManager.class.getName());

    private static boolean started = false;
    private static List<RPC> activeConnections = new ArrayList<>();
    private static MessageChannel serverSocketChannel = null;
    private static Thread processDataThread = null;

    private static synchronized boolean isStarted() {
        return ServersManager.started;
    }

    public static void main(String[] args) {
        synchronized (ServersManager.class) {
            if (isStarted()) throw logger.throwing(new RuntimeException("ServersManager is already running!"));
            ServersManager.started = true;
        }

        RPC rpcMaster = null;
        try {
            logger.info("Waiting for first connection...");
            rpcMaster = new RPCFactory().build(new ServersManagerLocalFactory(), new ServerSocketChannelFactory("0.0.0.0", SERVERS_MANAGER_PORT));
            synchronized (ServersManager.class) {
                serverSocketChannel = rpcMaster._getRemoteConnection();
            }
        } catch (IOException ex) {
            logger.error(ex);
        }

        if (rpcMaster == null) {
            logger.info("Closing program as we couldn't establish the first connection");
            stop();
            return;
        }

        try {
            rpcMaster.createConnection();
        } catch (IOException|InterruptedException ex) {
            logger.error("Got an error while trying to establish the first connection", ex);
            stop();
            return;
        }
        activeConnections.add(rpcMaster);

        // read socket data thread
        processDataThread = new Thread(() -> {
            while (isStarted()) {
                List<RPC> processing;
                synchronized (ServersManager.class) {
                    // delete already closed sessions
                    activeConnections.stream().filter(connection -> !connection.isRunning()).forEach(connection -> {
                        try {
                            connection.close();
                        } catch (IOException ignore) {}
                    });
                    activeConnections.removeIf(connection -> !connection.isRunning());

                    processing = new ArrayList<>(activeConnections);
                }

                for (int index = 0; index < processing.size() && isStarted(); index++) {
                    RPC currentlyProcessing = processing.get(index);
                    try {
                        currentlyProcessing.processOneCall();
                    } catch (IOException ex) {
                        logger.warn(ex);
                    }

                    try {
                        Thread.sleep(200); // give it some break
                    } catch (InterruptedException ignore) {}
                }

                try {
                    Thread.sleep(200); // give it some break
                } catch (InterruptedException ignore) {}
            }
        });
        processDataThread.start();

        while (isStarted()) {
            try {
                RPC serversManagerInstance = new RPCFactory().build(new ServersManagerLocalFactory(), rpcMaster);
                serversManagerInstance.createConnection();
                synchronized (ServersManager.class) {
                    activeConnections.add(serversManagerInstance);
                }
            } catch (IOException|InterruptedException ex) {
                logger.warn(ex);
            }

            try {
                Thread.sleep(200); // give it some break
            } catch (InterruptedException ignore) {}
        }
    }

    // TODO call on ctrl-c
    public static synchronized void stop() {
        logger.info("Closing ServersManager...");
        ServersManager.started = false;

        // closing the server will cause all the `run()` to get unstuck
        try {
            if (serverSocketChannel != null && !serverSocketChannel.isClosed()) serverSocketChannel.close();
        } catch (IOException ignore) {}

        try {
            processDataThread.join(8_000);
        } catch (InterruptedException ignore) {}
    }
}
