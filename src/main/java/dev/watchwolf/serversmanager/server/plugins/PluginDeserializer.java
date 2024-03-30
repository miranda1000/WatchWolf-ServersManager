package dev.watchwolf.serversmanager.server.plugins;

import dev.watchwolf.core.entities.files.plugins.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface PluginDeserializer {
    /**
     * Places a plugin into a folder
     * @param plugin Plugin to deserialize
     * @param outDirectory Target directory
     */
    void deserialize(Plugin plugin, File outDirectory) throws IOException,UnableToAchievePluginException;

    /**
     * Returns the plugins according to the target mc version
     * @param plugins Plugins
     * @param targetMcVersion Server version
     * @return Plugins that can be added, with the required data added
     */
    List<Plugin> filterByVersion(List<Plugin> plugins, String targetMcVersion) throws IOException;

    /**
     * Returns the path of all the usual plugins
     * @return Usual plugins location
     */
    Path getUsualPluginsPath();
}
