package dev.watchwolf.serversmanager.server;

public class ServerJarUnavailableException extends RuntimeException {
    public ServerJarUnavailableException() {
        super();
    }

    public ServerJarUnavailableException(String ex) {
        super(ex);
    }
}
