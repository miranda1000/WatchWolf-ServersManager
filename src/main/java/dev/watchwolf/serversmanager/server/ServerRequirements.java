package dev.watchwolf.serversmanager.server;

import dev.watchwolf.core.entities.ServerType;
import dev.watchwolf.core.entities.WorldType;
import dev.watchwolf.core.entities.files.ConfigFile;
import dev.watchwolf.core.entities.files.ZipFile;
import dev.watchwolf.core.entities.files.plugins.Plugin;
import dev.watchwolf.core.entities.files.plugins.UsualPlugin;
import dev.watchwolf.serversmanager.server.plugins.PluginDeserializer;
import dev.watchwolf.serversmanager.server.plugins.ServersManagerPluginDeserializer;
import dev.watchwolf.serversmanager.server.plugins.UnableToAchievePluginException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ServerRequirements {
    public static final String SHARED_TMP_FOLDER = "{pwd}/{offset}/tmp";

    private static final Logger logger = LogManager.getLogger(ServerRequirements.class.getName());
    private static boolean serverFolderInfoLogged = false;

    private static PluginDeserializer deserializer = new ServersManagerPluginDeserializer();
    private static Path serverTypesFolder = Paths.get( (System.getenv("SERVER_PATH_SHIFT") == null) ? "." : System.getenv("SERVER_PATH_SHIFT") ).resolve("server-types");

    private static void copyServerJar(String serverType, String serverVersion, Path targetFolder, String jarName) throws ServerJarUnavailableException,IOException {
        Path serverJar = serverTypesFolder.resolve(serverType + "/" + serverVersion + ".jar");
        if (!Files.exists(serverJar)) throw new ServerJarUnavailableException("Couldn't find " + serverType + " " + serverVersion + " on expected location (" + serverJar.toString() + ")");

        Files.copy(serverJar, targetFolder.resolve(jarName));
    }

    public static String getPrivateServerFolder(String id) {
        String offset = (System.getenv("SERVER_PATH_SHIFT") == null) ? "." : System.getenv("SERVER_PATH_SHIFT");
        String serverFolder = SHARED_TMP_FOLDER.replace("{pwd}", ".").replace("{offset}", offset) + "/" + id;
        return serverFolder;
    }

    static Path createServerFolder() throws IOException {
        String serverFolder = getPrivateServerFolder(String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(new File(serverFolder).toPath());
        return Paths.get(serverFolder);
    }

    /**
     * To start a Minecraft server it is required that we accept the eula (via a file)
     * @param targetFolder Out folder
     * @throws IOException Failed to create the file
     */
    private static void generateEulaFile(Path targetFolder) throws IOException {
        String acceptEulaString = "eula=true";
        Files.write(targetFolder.resolve("eula.txt"), acceptEulaString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
    }

    /**
     * Set custom timings for the getTimings request from WW-Server
     * @param targetFolder Out folder
     * @throws IOException Failed to create the file
     */
    private static void setTimingsSettings(Path targetFolder) throws IOException {
        String customTimingSettings = """
settings:
  plugin-profiling: true
""";
        Files.write(targetFolder.resolve("bukkit.yml"), customTimingSettings.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
    }

    /**
     * Set the server properties according to the desired server
     * @param targetFolder Out folder
     * @throws IOException Failed to create the file
     */
    private static void setServerProperties(Path targetFolder, int port, WorldType serverType, String seed) throws IOException {
        String []serverProperties = new String[]{
                "online-mode=false",
                "white-list=true",
                "motd=Minecraft test server",
                "max-players=100",
                "spawn-protection=0",
                "server-port=" + port,
                "level-type=" + serverType.name().toUpperCase(),
                "level-seed=" + (seed == null ? "" : seed)
        };

        Files.write(targetFolder.resolve("server.properties"), String.join("\n", serverProperties).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
    }

    /**
     * Set custom timings for the getTimings request from WW-Server
     * @param targetFolder Out folder
     * @throws IOException Failed to create the file
     */
    private static void setWatchWolfServerProperties(Path targetFolder, String targetIp, int port, String replyIp, String key) throws IOException {
        String []watchwolfServerPropertie = new String[]{
                "target-ip: " + targetIp,
                "use-port: " + port,
                "reply: " + replyIp,
                "key: " + key
        };

        Path watchwolfServerPath = targetFolder.resolve("plugins").resolve("WatchWolf");
        Files.createDirectories(watchwolfServerPath);
        Files.write(watchwolfServerPath.resolve("config.yml"), String.join("\n", watchwolfServerPropertie).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
    }

    static String getGlobalServerFolder(String localServerFolder) {
        String tmpFolderBase = SHARED_TMP_FOLDER;

        if (ServerRequirements.SHARED_TMP_FOLDER.contains("{pwd}")) {
            if (System.getenv("PARENT_PWD") == null) throw new NullPointerException("env variable PARENT_PWD undefined");
            tmpFolderBase = tmpFolderBase.replace("{pwd}", System.getenv("PARENT_PWD"));
        }

        String offset = (System.getenv("SERVER_PATH_SHIFT") == null) ? "." : System.getenv("SERVER_PATH_SHIFT");
        tmpFolderBase = tmpFolderBase.replace("{offset}", offset);

        return tmpFolderBase + "/" + getHashFromServerPath(localServerFolder);
    }

    public static void logServerFolderInfo() {
        try {
            // print all usual plugins got
            Path usualPluginsPath = deserializer.getUsualPluginsPath();
            Set<String> usualPlugins = Files.list(usualPluginsPath)
                    .filter(file -> !Files.isDirectory(file))
                    .map(file -> file.getFileName().toString())
                    .filter((name) -> name.endsWith(".jar"))
                    .collect(Collectors.toSet());
            ServerRequirements.logger.info("Usual plugins: " + usualPlugins.toString());

            // print all server types got
            Map<String, List<String>> serversAvailable = new HashMap<>();
            for (Path serverType : Files.list(serverTypesFolder).collect(Collectors.toList())) {
                serversAvailable.put(serverType.getFileName().toString(),
                        Files.list(serverType)
                                .map(f -> f.getFileName().toString())
                                .filter(f -> f.endsWith(".jar")) // only servers
                                .map(f -> f.substring(0, f.length() - 4)) // remove extension
                                .collect(Collectors.toList())
                );
            }
            ServerRequirements.logger.info("Servers available: " + serversAvailable.toString());
        } catch (IOException ex) {
            ServerRequirements.logger.error("Couldn't get information about the server folder", ex);
        } finally {
            ServerRequirements.serverFolderInfoLogged = true;
        }
    }

    /**
     * Clears the contents of the folder created by `setupFolder`
     * @param folder setupFolder return
     */
    public static void clearFolder(String folder) throws IOException {
        String serverFolder = getPrivateServerFolder(getHashFromServerPath(folder));
        FileUtils.deleteDirectory(new File(serverFolder));
    }

    public static String setupFolder(String serverType, String serverVersion, Collection<Plugin> plugins, WorldType worldType, String seed, Collection<ConfigFile> maps, Collection<ConfigFile> configFiles, String jarName) throws IOException {
        logger.traceEntry(null, serverType, serverVersion, plugins, worldType, maps, configFiles, jarName);
        if (!ServerRequirements.serverFolderInfoLogged) ServerRequirements.logServerFolderInfo();

        try (final CloseableThreadContext.Instance ctc = CloseableThreadContext.push(serverType).push(serverVersion)) {
            Path serverFolder = ServerRequirements.createServerFolder();
            logger.info("Server folder at " + serverFolder.toString());

            // copy server (type&version)
            logger.debug("Preparing folder...");
            logger.debug("Copying server jar...");
            ServerRequirements.copyServerJar(serverType, serverVersion, serverFolder, jarName);
            logger.debug("Generating eula file...");
            generateEulaFile(serverFolder);
            logger.debug("Generating timings configuration...");
            setTimingsSettings(serverFolder);
            logger.debug("Generating server properties file...");
            setServerProperties(serverFolder, 25565, worldType, seed);
            logger.debug("Generating WW-server config file...");
            setWatchWolfServerProperties(serverFolder, "127.0.0.1" /* TODO unused by WW-Server (for now) */, 25566 /* TODO don't depend on DockerizedServerInstantiator#startServer ports */, System.getenv("MACHINE_IP").trim() + ":8000" /* deprecated */, "deprecated" /* deprecated */);

            // export worlds
            logger.debug("Exporting worlds...");
            for (ConfigFile map : maps) {
                if (!(map instanceof ZipFile)) throw new IllegalArgumentException("All worlds must be zips; got `." + map.getExtension() + "` instead.");
                ((ZipFile)map).exportToDirectory(serverFolder);
            }

            // export plugins
            logger.debug("Exporting plugins...");
            String basePluginsFolder = serverFolder + "/plugins";
            List<Plugin> pluginsToAdd = new ArrayList<>(plugins);
            pluginsToAdd.add(new UsualPlugin("WatchWolf")); // always add WW-Server
            try {
                for (Plugin plugin : deserializer.filterByVersion(pluginsToAdd, serverVersion)) {
                    try {
                        logger.debug("Deserializing " + plugin + "...");
                        deserializer.deserialize(plugin, new File(basePluginsFolder));
                    } catch (Exception ex) {
                        logger.warn("Couldn't get plugin " + plugin.toString());
                        // keep going; if the plugin was required WW-Tester will stop
                    }
                }
            } catch (Exception ex) {
                logger.error("Major error while trying to export plugins", ex);
            }

            // export config files
            logger.debug("Exporting custom config files...");
            for (ConfigFile configFile : configFiles) {
                if (configFile.getOffsetPath().contains("../") || configFile.getOffsetPath().startsWith("/")) {
                    System.err.println("Got file with illegal offset (" + configFile.getOffsetPath() + "); will ignore.");
                    continue;
                }

                if (configFile instanceof ZipFile) ((ZipFile)configFile).exportToDirectory(Path.of(basePluginsFolder + configFile.getOffsetPath()));
                else configFile.saveToFile(new File(basePluginsFolder + "/" + configFile.getOffsetPath() + configFile.getName() + "." + configFile.getExtension()));
            }
            logger.debug("Done preparing server folder");

            // we must return the global folder
            return logger.traceExit(getGlobalServerFolder(serverFolder.toString()));
        }
    }

    public static String getHashFromServerPath(String path) {
        Matcher match = Pattern.compile("/(\\d+)$")
                                .matcher(path);

        if (!match.find()) throw new IllegalArgumentException("We expect .../<hash>; got '" + path + "' instead");
        return match.group(1);
    }
}
