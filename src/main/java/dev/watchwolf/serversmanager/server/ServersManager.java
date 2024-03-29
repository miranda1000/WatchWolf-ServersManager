package dev.watchwolf.serversmanager.server;

import dev.watchwolf.core.entities.WorldType;
import dev.watchwolf.core.entities.files.ConfigFile;
import dev.watchwolf.core.entities.files.plugins.Plugin;
import dev.watchwolf.core.utils.DockerUtilities;
import dev.watchwolf.serversmanager.server.instantiator.Server;
import dev.watchwolf.serversmanager.server.instantiator.ServerInstantiator;
import dev.watchwolf.serversmanager.server.instantiator.ThrowableServer;
import dev.watchwolf.serversmanager.server.ip.ExternalizeIpManager;
import dev.watchwolf.serversmanager.server.ip.IpManager;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

public class ServersManager implements Closeable {
    public static final String TARGET_SERVER_JAR = "server.jar";

    private final ServerInstantiator serverInstantiator;
    private final IpManager ipManager;

    public ServersManager(ServerInstantiator serverInstantiator) {
        this.serverInstantiator = serverInstantiator;
        this.ipManager = new ExternalizeIpManager(System.getenv("MACHINE_IP"), System.getenv("PUBLIC_IP"));
    }

    @Override
    public void close() {
        this.serverInstantiator.close();
    }

    /**
     * Starts a server given the required parameters
     * @param serverType
     * @param serverVersion
     * @param plugins
     * @param worldType
     * @param seed MC server seed; Empty if random
     * @param maps
     * @param configFiles
     * @param serverRequestee IP&Port WW-Tester is using
     * @return Created server IP&port
     */
    public ThrowableServer startServer(String serverType, String serverVersion, Collection<Plugin> plugins, WorldType worldType, String seed, Collection<ConfigFile> maps, Collection<ConfigFile> configFiles, InetSocketAddress serverRequestee) throws IOException,ServerJarUnavailableException {
        final String path = ServerRequirements.setupFolder(serverType, serverVersion, plugins, worldType, seed, maps, configFiles, TARGET_SERVER_JAR);

        System.out.println("Starting " + serverType + " " + serverVersion + " server on " + path + "...");
        Server server = this.serverInstantiator.startServer(Paths.get(path), TARGET_SERVER_JAR, DockerUtilities.getJavaVersion(serverVersion));
        server.setIp(this.ipManager.getIp(server.getIp(), serverRequestee));

        // we need to perform some cleanup if the server stops
        server.subscribeToServerStoppedEvents(() -> {
            // and clear the folder
            System.out.println("Server stopped; clearing folder...");
            // TODO get logs before deleting everything
            try {
                ServerRequirements.clearFolder(path);
            } catch (IOException ex) {
                System.err.println(ex.toString());
            }
        });

        return new ThrowableServer(server);
    }
}
