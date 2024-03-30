package dev.watchwolf.serversmanager.server.instantiator;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public interface ServerInstantiator extends Closeable {
    /**
     * Instantiates a server given an already prepared folder
     * @param folderLocation    Server folder
     * @param entrypoint        Server jar file name
     * @param javaVersion       Compatible java version to run the server jar
     * @return Server with its IP and some events
     */
    Server startServer(Path folderLocation, String entrypoint, int javaVersion);

    @Override
    default void close() {}
}
