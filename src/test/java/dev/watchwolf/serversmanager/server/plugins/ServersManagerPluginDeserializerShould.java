package dev.watchwolf.serversmanager.server.plugins;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import dev.watchwolf.core.utils.Version;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServersManagerPluginDeserializerShould {
    public static class PluginDeserializerMock extends ServersManagerPluginDeserializer implements Closeable {
        private FileSystem fs;
        private Path pathMock;
        public PluginDeserializerMock() throws IOException {
            this.fs = Jimfs.newFileSystem(Configuration.unix());
            this.pathMock = fs.getPath("/usual-plugins");
            Files.createDirectories(this.pathMock);
        }

        @Override
        public Path getUsualPluginsPath() {
            return this.pathMock;
        }

        @Override
        public void close() throws IOException {
            this.fs.close();
        }
    }

    @Test
    public void getUsualPluginCompatibleVersions() throws Exception {
        try (PluginDeserializerMock pluginDeserializer = new PluginDeserializerMock()) {
            String []plugins = new String[]{
                    "DecoyPlugin-1.0-1.8-LATEST.jar", // shouldn't show
                    "TargetPlugin-0.1-1.8-1.19.jar", // uncompatible for 1.20
                    "TargetPlugin-1.1-1.8-LATEST.jar",
                    "TargetPlugin-2.0-1.20-LATEST.jar"
            };
            for (String plugin : plugins) Files.createFile(pluginDeserializer.getUsualPluginsPath().resolve(plugin));

            assertEquals(Arrays.asList(new Version("1.1"), new Version("2.0")), pluginDeserializer.getCompatibleVersions("TargetPlugin", "1.20"));
            assertEquals(Arrays.asList(new Version("0.1"), new Version("1.1")), pluginDeserializer.getCompatibleVersions("TargetPlugin", "1.19"),
                    "Expected v0.1 to be reported and 2.0 to not be in MC 1.19");
            assertEquals(Arrays.asList(new Version("0.1"), new Version("1.1")), pluginDeserializer.getCompatibleVersions("TargetPlugin", "1.8"));
            assertEquals(Collections.emptyList(), pluginDeserializer.getCompatibleVersions("TargetPlugin", "1.7"),
                    "Expected no versions to be reported pre-1.8");
        }
    }

    @Test
    public void reportNoCompatibleVersionsIfUsualPluginDoesntExist() throws Exception {
        try (PluginDeserializerMock pluginDeserializer = new PluginDeserializerMock()) {
            assertEquals(Collections.emptyList(), pluginDeserializer.getCompatibleVersions("MyNonExistentPlugin", "1.20"),
                    "Expected to get no compatible versions of a plugin that isn't there; got something instead");
        }
    }

    @Test
    public void getSpigotPluginDownloadUrl() throws Exception {
        String pluginUrl = "https://www.spigotmc.org/resources/portalgun-1-8-1-20.44746/";
        String expectedDownloadUrl = "https://api.spiget.org/v2/resources/44746/download";

        String got = ServersManagerPluginDeserializer.getSpigotDownloadUrl(pluginUrl);

        assertEquals(expectedDownloadUrl, got);
    }
}
