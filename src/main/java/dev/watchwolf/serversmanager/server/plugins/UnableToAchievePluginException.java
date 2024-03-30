package dev.watchwolf.serversmanager.server.plugins;

import java.io.IOException;

public class UnableToAchievePluginException extends IOException {
    public UnableToAchievePluginException() {
        super();
    }

    public UnableToAchievePluginException(String ex) {
        super(ex);
    }
}
