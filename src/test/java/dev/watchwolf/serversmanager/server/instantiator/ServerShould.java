package dev.watchwolf.serversmanager.server.instantiator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ServerShould {
    protected static final int WAIT_TIMEOUT = 60,
                            SMALL_ASSERT_TIMEOUT = 20;

    protected Server getServer(String ip) {
        return new Server(ip);
    }

    protected Server getServer() {
        return new Server("127.0.0.1:25565");
    }

    @Test
    void reportItsIp() {
        String ip = "127.0.0.1:25555";

        Server uut = getServer(ip);

        assertEquals(ip, uut.getIp());
    }

    @Test
    void notifyServerStartedEvent() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server uut = getServer();
        uut.subscribeToServerStartedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.incrementAndGet();
                syncronizedObject.notify();
            }
        });

        synchronized (syncronizedObject) {
            // we didn't launch the event, so nothing should invoke
            try {
                syncronizedObject.wait(SMALL_ASSERT_TIMEOUT);
            } catch (InterruptedException ignored) {}
            assertEquals(0, syncronizedObject.get(), "Event was raised before invoking the function");
        }

        uut.raiseServerStartedEvent();

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(1, syncronizedObject.get(), "Event was not risen, or was risen more than once");
        }
    }

    @Test
    void detectServerStartedByMessage() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server uut = getServer();
        uut.subscribeToServerStartedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.incrementAndGet();
                syncronizedObject.notify();
            }
        });

        final String startupSequence = """
[18:47:41] [Server thread/INFO]: Preparing level "world"
[18:47:44] [Server thread/INFO]: Preparing start region for dimension minecraft:overworld
[18:47:44] [Server thread/INFO]: Time elapsed: 118 ms
[18:47:44] [Server thread/INFO]: Preparing start region for dimension minecraft:the_nether
[18:47:44] [Server thread/INFO]: Time elapsed: 101 ms
[18:47:44] [Server thread/INFO]: Preparing start region for dimension minecraft:the_end
[18:47:44] [Server thread/INFO]: Time elapsed: 81 ms
[18:47:45] [Server thread/INFO]: [MineIt] WorldGuard plugin detected.
[18:47:45] [Server thread/INFO]: Running delayed init tasks
"""; // next we get the 'Done' message

        for (String line : startupSequence.split("\n")) uut.raiseServerMessageEvent(line);

        synchronized (syncronizedObject) {
            // we didn't launch the event, so nothing should invoke
            try {
                syncronizedObject.wait(SMALL_ASSERT_TIMEOUT);
            } catch (InterruptedException ignored) {}
            assertEquals(0, syncronizedObject.get(), "Event was raised before invoking the function");
        }

        uut.raiseServerMessageEvent("[18:47:45] [Server thread/INFO]: Done (6.656s)! For help, type \"help\"");

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(1, syncronizedObject.get(), "Event was " + ((syncronizedObject.get() == 0) ? "not risen" : "risen more than once"));
        }
    }

    @Test
    void detectServerStartedByMessageOnOldVersions() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server uut = getServer();
        uut.subscribeToServerStartedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.incrementAndGet();
                syncronizedObject.notify();
            }
        });

        uut.raiseServerMessageEvent("[09:36:31 INFO]: Done (32.378s)! For help, type \"help\"");

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(1, syncronizedObject.get(), "Event was " + ((syncronizedObject.get() == 0) ? "not risen" : "risen more than once"));
        }
    }

    @Test
    void detectServerStartedByMessageOnOldPaperVersions() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server uut = getServer();
        uut.subscribeToServerStartedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.incrementAndGet();
                syncronizedObject.notify();
            }
        });

        uut.raiseServerMessageEvent("[09:36:31 INFO]: Done (32.378s)! For help, type \"help\" or \"?\"");

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(1, syncronizedObject.get(), "Event was " + ((syncronizedObject.get() == 0) ? "not risen" : "risen more than once"));
        }
    }

    @Test
    void notifyServerStoppedEvent() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server uut = getServer();
        uut.subscribeToServerStoppedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.incrementAndGet();
                syncronizedObject.notify();
            }
        });

        synchronized (syncronizedObject) {
            // we didn't launch the event, so nothing should invoke
            try {
                syncronizedObject.wait(SMALL_ASSERT_TIMEOUT);
            } catch (InterruptedException ignored) {}
            assertEquals(0, syncronizedObject.get(), "Event was raised before invoking the function");
        }

        uut.raiseServerStoppedEvent();

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(1, syncronizedObject.get(), "Event was not risen, or was risen more than once");
        }
    }

    @Test
    void notifyMessageEvent() throws Exception {
        final AtomicReference<String> syncronizedObject = new AtomicReference<>(null);
        final String sending = "Hello world!";

        Server uut = getServer();
        uut.subscribeToServerMessageEvents((msg) -> {
            synchronized (syncronizedObject) {
                syncronizedObject.set(msg);
                syncronizedObject.notify();
            }
        });

        synchronized (syncronizedObject) {
            // we didn't launch the event, so nothing should invoke
            try {
                syncronizedObject.wait(SMALL_ASSERT_TIMEOUT);
            } catch (InterruptedException ignored) {}
            assertNull(syncronizedObject.get(), "Event was raised before invoking the function");
        }

        uut.raiseServerMessageEvent(sending);

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(sending, syncronizedObject.get(), "Event was not risen");
        }
    }

    @Test
    void notifyMultipleMessagesEvent() throws Exception {
        final ArrayList<String> syncronizedObject = new ArrayList<>();
        final String []sending = new String[]{"Hello world!", "This is a test"};

        Server uut = getServer();
        uut.subscribeToServerMessageEvents((msg) -> {
            synchronized (syncronizedObject) {
                syncronizedObject.add(msg);
                syncronizedObject.notify();
            }
        });

        synchronized (syncronizedObject) {
            // we didn't launch the event, so nothing should invoke
            try {
                syncronizedObject.wait(SMALL_ASSERT_TIMEOUT);
            } catch (InterruptedException ignored) {}
            assertEquals(0, syncronizedObject.size(), "Event was raised before invoking the function");
        }

        for (int index = 0; index < sending.length; index++) {
            uut.raiseServerMessageEvent(sending[index]);
        }

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(sending.length, syncronizedObject.size(), "Event was not risen");
            for (int index = 0; index < sending.length; index++) {
                assertEquals(sending[index], syncronizedObject.get(index));
            }
        }
    }
}
