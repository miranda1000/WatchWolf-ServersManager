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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class ServersManager implements Closeable {
    public static final String TARGET_SERVER_JAR = "server.jar";
    private static Path LOGS_FOLDER_BASE = Paths.get(((System.getenv("SERVER_PATH_SHIFT") == null) ? "." : System.getenv("SERVER_PATH_SHIFT")) + "/logs");

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
    public ThrowableServer startServer(final String serverType, final String serverVersion, Collection<Plugin> plugins, WorldType worldType, String seed, Collection<ConfigFile> maps, Collection<ConfigFile> configFiles, InetSocketAddress serverRequestee) throws IOException,ServerJarUnavailableException {
        final String path = ServerRequirements.setupFolder(serverType, serverVersion, plugins, worldType, seed, maps, configFiles, TARGET_SERVER_JAR);
        final Date serverCreatedAt = new Date();

        System.out.println("Starting " + serverType + " " + serverVersion + " server on " + path + "...");
        final Server server = this.serverInstantiator.startServer(Paths.get(path), TARGET_SERVER_JAR, DockerUtilities.getJavaVersion(serverVersion));
        server.setIp(this.ipManager.getIp(server.getIp(), serverRequestee));

        // keep the logs
        String serverUUID = ServerRequirements.getHashFromServerPath(path);
        Path outLogsFolder = LOGS_FOLDER_BASE.resolve(serverUUID);
        try {
            Files.createDirectories(outLogsFolder);

            Map<String,String> info = new HashMap<>();
            info.put("serverType", serverType);
            info.put("serverVersion", serverVersion);
            info.put("uuid", serverUUID);
            info.put("createdAt", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(serverCreatedAt));
            info.put("ip", server.getIp());
            for (Map.Entry<String,String> e : info.entrySet()) Files.writeString(outLogsFolder.resolve("info.txt"), e.getKey() + " = " + e.getValue() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // we won't subscribe if we can't create the info file
            final Path logsFile = outLogsFolder.resolve("latest.log");
            server.subscribeToServerMessageEvents((msg) -> {
                try {
                    Files.writeString(logsFile, msg + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException ignore) {}
            });
        } catch (Exception ex) {
            System.err.println("Failed to copy logs info file: " + ex.toString());
        }

        // we need to perform some cleanup if the server stops
        server.subscribeToServerStoppedEvents(() -> {
            // and clear the folder
            System.out.println("Server stopped; clearing folder...");
            try {
                ServerRequirements.clearFolder(path);
            } catch (IOException ex) {
                System.err.println(ex.toString());
            }
        });

        return new ThrowableServer(server);
    }
}
