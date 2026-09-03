package dev.watchwolf.serversmanager.server.instantiator;

import dev.watchwolf.core.rpc.stubs.serversmanager.CapturedExceptionEvent;
import dev.watchwolf.core.rpc.stubs.serversmanager.ServerStartedEvent;
import dev.watchwolf.server.ServerStopNotifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThrowableServer extends Server {
    private final Server wrappedServer;
    private final Collection<CapturedExceptionEvent> capturedExceptionListeners;
    private StringBuilder exception;

    public ThrowableServer(Server s) {
        super(s.getIp());

        this.wrappedServer = s;
        this.capturedExceptionListeners = new ArrayList<>();
        this.exception = null;
        this.setSubEventManagerAsSelf();
    }

    private void setSubEventManagerAsSelf() {
        this.serverMessageListeners.clear(); // the super constructor will subscribe this object to messages, but the wrapped server is already subscribed

        // now the listener are us
        this.wrappedServer.serverMessageListeners.remove(this.wrappedServer);
        this.wrappedServer.subscribeToServerMessageEvents(this);

        // "the server stopped" is noticed by whoever watches the container, and it only ever knows
        // about the wrapped server -- so the wrapper has to listen to it, the same way it does for
        // messages. Without this, everything subscribed through the wrapper (the RPC layer, and so
        // the Tester) is never told a server died.
        this.wrappedServer.subscribeToServerStoppedEvents(this::raiseOwnServerStoppedEvent);
    }

    /**
     * Notifies only what subscribed through the wrapper. The wrapped server's own listeners are
     * notified by the wrapped server itself, and doing it here too would fire them twice.
     */
    private void raiseOwnServerStoppedEvent() {
        this.logger.traceEntry();
        for (ServerStopNotifier e : this.serverStoppedListeners) e.onServerStop();
    }

    void raiseExceptionEvent(String msg) {
        this.logger.traceEntry(null, msg);
        for (CapturedExceptionEvent e : this.capturedExceptionListeners) {
            try {
                e.capturedException(msg);
            } catch (IOException ignore) {}
        }
    }

    @Override
    void raiseServerStartedEvent() throws IOException {
        this.wrappedServer.raiseServerStartedEvent();
        for (ServerStartedEvent e : this.serverStartedListeners) e.serverStarted();
    }

    @Override
    void raiseServerStoppedEvent() {
        // our own listeners are reached through the subscription setSubEventManagerAsSelf installed,
        // so that a stop noticed on the wrapped server and one raised here behave identically
        this.wrappedServer.raiseServerStoppedEvent();
    }

    @Override
    public void stop() {
        this.wrappedServer.stop();
    }

    @Override
    void raiseServerMessageEvent(String msg) {
        this.logger.traceEntry(null, msg);
        this.wrappedServer.raiseServerMessageEvent(msg);
        for (ServerMessageEvent e : this.serverMessageListeners) e.onMessageEvent(msg);
    }

    public Server subscribeToExceptionEvents(CapturedExceptionEvent subscriber) {
        this.logger.traceEntry(null, subscriber);
        this.capturedExceptionListeners.add(subscriber);
        return this;
    }

    @Override
    public void onMessageEvent(String msg) {
        // original event handler already called
        super.onMessageEvent(msg);

        if (this.exception == null) {
            // listen for new exceptions
            Pattern startingExceptionPattern = Pattern.compile(headerErrorRegex + "(.*)$");
            Matcher matcher = startingExceptionPattern.matcher(msg);
            if (matcher.find()) {
                // following there's an exception
                this.logger.info("Getting start of exception...");
                this.exception = new StringBuilder()
                        .append(matcher.group(1)); // add the start of exception
            }
        }
        else {
            // did the exception finish?
            Pattern finishingExceptionPattern = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}");
            if (finishingExceptionPattern.matcher(msg).find()) {
                // exception completed; launch event
                this.logger.debug("Exception completed; launching event...");
                this.raiseExceptionEvent(this.exception.toString());
                this.exception = null; // back to listen
            }
            else {
                // keep getting traces
                if (!this.exception.isEmpty()) this.exception.append('\n');
                this.exception.append(msg);
            }
        }
    }
}
