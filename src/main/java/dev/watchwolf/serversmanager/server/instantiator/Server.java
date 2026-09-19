package dev.watchwolf.serversmanager.server.instantiator;

import dev.watchwolf.core.rpc.stubs.serversmanager.ServerStartedEvent;
import dev.watchwolf.server.ServerStopNotifier;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

public class Server implements ServerMessageEvent {
    protected final Logger logger = LogManager.getLogger(this.getClass().getName());

    /**
     * '[18:47:41 <severity>]: ' or '[18:47:41] [Server thread/<severity>]: '
     */
    private final String headerRegex = "^\\[\\d{2}:\\d{2}:\\d{2}(?:(?:\\] \\[Server thread\\/{severity})|(?: {severity}))\\]: ";
    protected final String headerInfoRegex = headerRegex.replace("{severity}", "INFO"),
                            headerErrorRegex = headerRegex.replace("{severity}", "ERROR");

    private String ip;

    /**
     * How to take this server down. Set by whoever instantiated it, since only they know what
     * "stopping" means for it.
     */
    private Runnable stopper;

    protected final Collection<ServerStartedEvent> serverStartedListeners;
    protected final Collection<ServerStopNotifier> serverStoppedListeners;
    protected final Collection<ServerMessageEvent> serverMessageListeners;

    public Server(String ip) {
        this.ip = ip;
        this.serverStartedListeners = new ArrayList<>();
        this.serverStoppedListeners = new ArrayList<>();
        this.serverMessageListeners = new ArrayList<>();

        this.subscribeToServerMessageEvents(this);
    }

    void raiseServerStartedEvent() throws IOException {
        this.logger.traceEntry();
        for (ServerStartedEvent e : this.serverStartedListeners) e.serverStarted();
    }

    void raiseServerStoppedEvent() {
        this.logger.traceEntry();
        for (ServerStopNotifier e : this.serverStoppedListeners) e.onServerStop();
    }

    /**
     * Called for each line the server sends
     * @param msg Text
     */
    void raiseServerMessageEvent(String msg) {
        // this will invoke `onMessageEvent` on this class
        for (ServerMessageEvent e : this.serverMessageListeners) e.onMessageEvent(msg);
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * @param stopper What takes this server down
     */
    public void setStopper(Runnable stopper) {
        this.stopper = stopper;
    }

    /**
     * Takes the server down. Does nothing if whoever instantiated it didn't say how.
     * The "stopped" event is raised by whatever notices the server is gone, not from here.
     */
    public void stop() {
        this.logger.traceEntry();
        if (this.stopper == null) {
            this.logger.warn("Asked to stop " + this.getIp() + ", but no one said how to");
            return;
        }
        this.stopper.run();
    }

    public String getIp() {
        return this.ip;
    }
    
    public Server subscribeToServerStartedEvents(ServerStartedEvent subscriber) {
        this.logger.traceEntry(null, subscriber);
        this.serverStartedListeners.add(subscriber);
        return this;
    }

    public Server subscribeToServerStoppedEvents(ServerStopNotifier subscriber) {
        this.logger.traceEntry(null, subscriber);
        this.serverStoppedListeners.add(subscriber);
        return this;
    }

    public Server subscribeToServerMessageEvents(ServerMessageEvent subscriber) {
        this.logger.traceEntry(null, subscriber);
        this.serverMessageListeners.add(subscriber);
        return this;
    }

    @Override
    public void onMessageEvent(String msg) {
        this.logger.traceEntry(null, msg);
        Pattern serverStartedPattern = Pattern.compile(headerInfoRegex + "Done \\([^)]+\\)! For help, type \\\"help\\\"( or \\\"\\?\\\")?$");
        if (serverStartedPattern.matcher(msg).find()) {
            try {
                this.raiseServerStartedEvent(); // server started
            } catch (IOException ex) {
                this.logger.throwing(ex);
            }
        }
    }
}
