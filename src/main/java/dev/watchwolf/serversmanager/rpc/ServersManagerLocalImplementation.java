package dev.watchwolf.serversmanager.rpc;

import dev.watchwolf.core.entities.WorldType;
import dev.watchwolf.core.entities.files.ConfigFile;
import dev.watchwolf.core.entities.files.plugins.Plugin;
import dev.watchwolf.core.rpc.stubs.serversmanager.CapturedExceptionEvent;
import dev.watchwolf.core.rpc.stubs.serversmanager.ServerStartedEvent;
import dev.watchwolf.core.rpc.stubs.serversmanager.ServersManagerPetitions;
import dev.watchwolf.serversmanager.server.ServerJarUnavailableException;
import dev.watchwolf.serversmanager.server.ServersManager;
import dev.watchwolf.serversmanager.server.instantiator.ThrowableServer;

import java.io.IOException;
import java.util.Collection;

/**
 * An implementation of ServersManager using Docker to launch the servers.
 */
public class ServersManagerLocalImplementation implements ServersManagerPetitions {
    private final ServersManager serversManager;
    private final ServerStartedEvent serverStartedEventManager;
    private final CapturedExceptionEvent capturedExceptionEventManager;
    private final RequesteeIpGetter requesteeIpGetter;

    public ServersManagerLocalImplementation(ServersManager serversManager, ServerStartedEvent serverStartedEventManager, CapturedExceptionEvent capturedExceptionEventManager, RequesteeIpGetter ipGetter) {
        this.serversManager = serversManager;
        this.serverStartedEventManager = serverStartedEventManager;
        this.capturedExceptionEventManager = capturedExceptionEventManager;
        this.requesteeIpGetter = ipGetter;
    }

    @Override
    public void nop() throws IOException { }

    @Override
    public String startServer(final String serverType, final String serverVersion, Collection<Plugin> plugins, WorldType worldType, String seed, Collection<ConfigFile> maps, Collection<ConfigFile> configFiles) throws IOException {
        System.out.println("Starting server...");
        try {
            // requesteeIpGetter will work because `startServer` gets called on a syncronized environment (by `forwardCall`), so we'll have the IP of the client calling this function
            final ThrowableServer server = this.serversManager.startServer(serverType, serverVersion, plugins, worldType, seed, maps, configFiles, this.requesteeIpGetter.getRequesteeIp());

            server.subscribeToServerStartedEvents(this.serverStartedEventManager);
            server.subscribeToServerStoppedEvents(() -> {
                System.out.println("Server " + serverType + " " + serverVersion + " (" + server.getIp() + ") stopped");
            });
            server.subscribeToExceptionEvents((msg) -> {
                System.err.println("Got exception on " + serverType + " " + serverVersion + " server:\n" + msg);
                this.capturedExceptionEventManager.capturedException(msg);
            });

            return server.getIp();
        } catch (ServerJarUnavailableException ex) {
            String errorMessage = "Couldn't start a " + serverType + " server, on " + serverVersion + ": " + ex.toString();
            System.err.println(errorMessage);
            capturedExceptionEventManager.capturedException(errorMessage);
            return ""; // no server
        }
    }
}
