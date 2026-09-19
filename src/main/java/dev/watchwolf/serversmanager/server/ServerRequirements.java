package dev.watchwolf.serversmanager.server;

import dev.watchwolf.core.entities.ServerType;
import dev.watchwolf.core.utils.Version;
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

    /**
     * bedrock, 2 dirt, grass; plains biome. The classic superflat, in the three shapes Minecraft has
     * asked for it in. No structures are requested: a test world has no use for villages, and every
     * extra field is one more thing a version can disagree about.
     * @see #getFlatGeneratorSettings(String)
     */
    static final String FLAT_GENERATOR_SETTINGS_LEGACY = "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1";
    static final String FLAT_GENERATOR_SETTINGS_NAMESPACED_IDS = "minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains";
    static final String FLAT_GENERATOR_SETTINGS_JSON = "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\"}";

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
    private static void setServerProperties(Path targetFolder, int port, WorldType worldType, String serverVersion, String seed) throws IOException {
        List<String> serverProperties = new ArrayList<>(Arrays.asList(
                "online-mode=false",
                "white-list=true",
                "motd=Minecraft test server",
                "max-players=100",
                "spawn-protection=0",
                "server-port=" + port,
                "level-type=" + worldType.name().toUpperCase(),
                "level-seed=" + (seed == null ? "" : seed)
        ));
        if (worldType == WorldType.FLAT) serverProperties.add("generator-settings=" + getFlatGeneratorSettings(serverVersion));

        Files.write(targetFolder.resolve("server.properties"), String.join("\n", serverProperties).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
    }

    /**
     * The superflat preset to write next to `level-type=FLAT`.
     *
     * `level-type=FLAT` with an empty `generator-settings` is not a valid server: from 1.16 the
     * server parses the (empty) value as the generator's JSON config and every single run starts
     * with `-- Server error -- No key layers in MapLike[{}]`, which buries whatever really went
     * wrong. The format changed twice, so the preset has to match the version:
     *
     * <ul>
     *   <li>up to 1.12.2 -- `<format version>;<layers>;<biome id>`</li>
     *   <li>1.13 to 1.15.2 -- `<layers>;<biome name>`, no format version</li>
     *   <li>1.16 and later -- JSON</li>
     * </ul>
     *
     * @param serverVersion Minecraft version the server will run
     * @return A `generator-settings` value that version understands
     */
    static String getFlatGeneratorSettings(String serverVersion) {
        Version version;
        try {
            version = new Version(serverVersion);
        } catch (IllegalArgumentException ex) {
            // a custom server type may be versioned however it likes; assume it is a current one
            logger.warn("Couldn't read '" + serverVersion + "' as a Minecraft version; assuming the modern generator-settings format");
            return FLAT_GENERATOR_SETTINGS_JSON;
        }

        if (version.roundTo(2).compareTo("1.13") < 0) return FLAT_GENERATOR_SETTINGS_LEGACY; // up to 1.12.2
        if (version.roundTo(2).compareTo("1.16") < 0) return FLAT_GENERATOR_SETTINGS_NAMESPACED_IDS; // 1.13 .. 1.15.2
        return FLAT_GENERATOR_SETTINGS_JSON;
    }

    /**
     * Set custom timings for the getTimings request from WW-Server
     * @param targetFolder Out folder
     * @param targetIp The ip that the server should listen to the requests (WW-Tester ip)
     * @param port The port that the WW-Server socket should use
     * @throws IOException Failed to create the file
     */
    private static void setWatchWolfServerProperties(Path targetFolder, String targetIp, int port) throws IOException {
        String []watchwolfServerPropertie = new String[]{
                "target-ip: " + targetIp,
                "use-port: " + port
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
            for (Path serverType : Files.list(serverTypesFolder)
                                            .filter(path -> Files.isDirectory(path))
                                            .collect(Collectors.toList())) {
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
            ServerRequirements.logger.warn("Couldn't get information about the server folder", ex);
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
            setServerProperties(serverFolder, 25565, worldType, serverVersion, seed);
            logger.debug("Generating WW-server config file...");
            setWatchWolfServerProperties(serverFolder, "127.0.0.1" /* TODO unused by WW-Server (for now) */, 25566 /* TODO don't depend on DockerizedServerInstantiator#startServer ports */);

            // export worlds
            logger.debug("Exporting worlds...");
            for (ConfigFile map : maps) {
                if (!(map instanceof ZipFile)) throw new IllegalArgumentException("All worlds must be zips; got `." + map.getExtension() + "` instead.");
                ((ZipFile)map).exportToDirectory(serverFolder.resolve(map.getName()));
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
