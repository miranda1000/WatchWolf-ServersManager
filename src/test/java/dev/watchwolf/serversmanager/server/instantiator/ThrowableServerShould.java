package dev.watchwolf.serversmanager.server.instantiator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// as the wrapper should keep the functionality of a regular Server, we'll inherit the tests
public class ThrowableServerShould extends ServerShould {
    @Override
    protected Server getServer(String ip) {
        return new ThrowableServer(super.getServer(ip));
    }

    @Override
    protected Server getServer() {
        return new ThrowableServer(super.getServer());
    }

    @Test
    void notifyServerStartedEventsDefinedOnWrapper() throws Exception {
        final AtomicBoolean syncronizedObject = new AtomicBoolean(false);

        Server uut = super.getServer();
        assertEquals(Server.class, uut.getClass());
        uut.subscribeToServerStartedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.set(true);
                syncronizedObject.notify();
            }
        });

        uut = new ThrowableServer(uut);

        synchronized (syncronizedObject) {
            // we didn't launch the event, so nothing should invoke
            try {
                syncronizedObject.wait(SMALL_ASSERT_TIMEOUT);
            } catch (InterruptedException ignored) {}
            assertFalse(syncronizedObject.get(), "Event was raised before invoking the function");
        }

        uut.raiseServerStartedEvent();

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertTrue(syncronizedObject.get(), "Event was not risen");
        }
    }

    @Test
    void notifyServerStoppedEventsDefinedOnWrapper() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server uut = super.getServer();
        assertEquals(Server.class, uut.getClass());
        uut.subscribeToServerStoppedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.incrementAndGet();
                syncronizedObject.notify();
            }
        });

        uut = new ThrowableServer(uut);

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
    void notifyMessageEventsDefinedOnWrapper() throws Exception {
        final AtomicReference<String> syncronizedObject = new AtomicReference<>(null);
        final String sending = "Hello world!";

        Server uut = super.getServer();
        uut.subscribeToServerMessageEvents((msg) -> {
            synchronized (syncronizedObject) {
                syncronizedObject.set(msg);
                syncronizedObject.notify();
            }
        });

        uut = new ThrowableServer(uut);

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
    void notifyExceptionEvents() throws Exception {
        final AtomicReference<String> syncronizedObject = new AtomicReference<>(null);
        final String exception = """
Error occurred while enabling MineIt-MineableGems v1.1 (Is it up to date?)
java.lang.IllegalStateException: zip file closed
    at java.util.zip.ZipFile.ensureOpen(ZipFile.java:840) ~[?:?]
    at java.util.zip.ZipFile.getEntry(ZipFile.java:339) ~[?:?]
    at java.util.jar.JarFile.getEntry(JarFile.java:517) ~[?:?]
    at java.util.jar.JarFile.getJarEntry(JarFile.java:472) ~[?:?]
    at org.bukkit.plugin.java.PluginClassLoader.findClass(PluginClassLoader.java:172) ~[spigot-api-1.20.4-R0.1-SNAPSHOT.jar:?]
    at java.lang.ClassLoader.loadClass(ClassLoader.java:592) ~[?:?]
    at org.bukkit.plugin.java.PluginClassLoader.loadClass0(PluginClassLoader.java:117) ~[spigot-api-1.20.4-R0.1-SNAPSHOT.jar:?]
    at org.bukkit.plugin.java.PluginClassLoader.loadClass(PluginClassLoader.java:112) ~[spigot-api-1.20.4-R0.1-SNAPSHOT.jar:?]
    at java.lang.ClassLoader.loadClass(ClassLoader.java:525) ~[?:?]
    at com.rogermiranda1000.mineit.mineable_gems.MinableGems.onEnable(MinableGems.java:83) ~[?:?]
    at org.bukkit.plugin.java.JavaPlugin.setEnabled(JavaPlugin.java:266) ~[spigot-api-1.20.4-R0.1-SNAPSHOT.jar:?]
    at org.bukkit.plugin.java.JavaPluginLoader.enablePlugin(JavaPluginLoader.java:342) ~[spigot-api-1.20.4-R0.1-SNAPSHOT.jar:?]
    at org.bukkit.plugin.SimplePluginManager.enablePlugin(SimplePluginManager.java:480) ~[spigot-api-1.20.4-R0.1-SNAPSHOT.jar:?]
    at org.bukkit.craftbukkit.v1_20_R3.CraftServer.enablePlugin(CraftServer.java:541) ~[spigot-1.20.4-R0.1-SNAPSHOT.jar:4042-Spigot-c198da2-7e43f3b]
    at org.bukkit.craftbukkit.v1_20_R3.CraftServer.enablePlugins(CraftServer.java:455) ~[spigot-1.20.4-R0.1-SNAPSHOT.jar:4042-Spigot-c198da2-7e43f3b]
    at net.minecraft.server.MinecraftServer.loadWorld0(MinecraftServer.java:623) ~[spigot-1.20.4-R0.1-SNAPSHOT.jar:4042-Spigot-c198da2-7e43f3b]
    at net.minecraft.server.MinecraftServer.loadLevel(MinecraftServer.java:409) ~[spigot-1.20.4-R0.1-SNAPSHOT.jar:4042-Spigot-c198da2-7e43f3b]
    at net.minecraft.server.dedicated.DedicatedServer.e(DedicatedServer.java:250) ~[spigot-1.20.4-R0.1-SNAPSHOT.jar:4042-Spigot-c198da2-7e43f3b]
    at net.minecraft.server.MinecraftServer.w(MinecraftServer.java:1000) ~[spigot-1.20.4-R0.1-SNAPSHOT.jar:4042-Spigot-c198da2-7e43f3b]
    at net.minecraft.server.MinecraftServer.lambda$0(MinecraftServer.java:304) ~[spigot-1.20.4-R0.1-SNAPSHOT.jar:4042-Spigot-c198da2-7e43f3b]
    at java.lang.Thread.run(Thread.java:840) ~[?:?]""";
        final String log = """
[08:48:35] [Server thread/INFO]: [MineableGems] Enabling MineableGems v1.11.3
[08:48:36] [Server thread/INFO]: [MineIt-MineableGems] Enabling MineIt-MineableGems v1.1
[08:48:36] [Server thread/INFO]: [MineIt-MineableGems] Disabling MineIt-MineableGems v1.1
[08:48:36] [Server thread/ERROR]:\s""" + exception + "\n[08:48:36] [Server thread/INFO]: Done (21.650s)! For help, type \"help\"";

        ThrowableServer uut = (ThrowableServer) getServer();
        uut.subscribeToExceptionEvents((msg) -> {
            synchronized (syncronizedObject) {
                syncronizedObject.set(msg);
                syncronizedObject.notify();
            }
        });

        // exceptions are processed through the regular messages
        for (String logLine : log.split("\n")) {
            uut.raiseServerMessageEvent(logLine); // we'll get the messagess one by one
        }

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(exception, syncronizedObject.get(), "Strings don't match:\n  Expecting:\n" + exception + "\n  Got:\n" + syncronizedObject.get());
        }
    }

    @Test
    void notifyNoExceptionIfSetupSuccessfully() throws Exception {
        final ArrayList<String> syncronizedObject = new ArrayList<>();
        final String log = """
2024-03-28 18:15:19 Unbundling libraries to /server/bundler
2024-03-28 18:15:19 Unpacking spigot-1.19-R0.1-SNAPSHOT.jar (versions:spigot-1.19-R0.1-SNAPSHOT.jar) to /server/bundler/versions/spigot-1.19-R0.1-SNAPSHOT.jar
2024-03-28 18:15:20 Unpacking asm-9.3.jar (libraries:asm-9.3.jar) to /server/bundler/libraries/asm-9.3.jar
2024-03-28 18:15:20 Unpacking authlib-3.5.41.jar (libraries:authlib-3.5.41.jar) to /server/bundler/libraries/authlib-3.5.41.jar
2024-03-28 18:15:20 Unpacking brigadier-1.0.18.jar (libraries:brigadier-1.0.18.jar) to /server/bundler/libraries/brigadier-1.0.18.jar
2024-03-28 18:15:20 Unpacking bungeecord-chat-1.16-R0.4.jar (libraries:bungeecord-chat-1.16-R0.4.jar) to /server/bundler/libraries/bungeecord-chat-1.16-R0.4.jar
2024-03-28 18:15:20 Unpacking checker-qual-3.12.0.jar (libraries:checker-qual-3.12.0.jar) to /server/bundler/libraries/checker-qual-3.12.0.jar
2024-03-28 18:15:20 Unpacking commons-codec-1.11.jar (libraries:commons-codec-1.11.jar) to /server/bundler/libraries/commons-codec-1.11.jar
2024-03-28 18:15:20 Unpacking commons-io-2.11.0.jar (libraries:commons-io-2.11.0.jar) to /server/bundler/libraries/commons-io-2.11.0.jar
2024-03-28 18:15:20 Unpacking commons-lang-2.6.jar (libraries:commons-lang-2.6.jar) to /server/bundler/libraries/commons-lang-2.6.jar
2024-03-28 18:15:20 Unpacking commons-lang3-3.12.0.jar (libraries:commons-lang3-3.12.0.jar) to /server/bundler/libraries/commons-lang3-3.12.0.jar
2024-03-28 18:15:20 Unpacking datafixerupper-5.0.28.jar (libraries:datafixerupper-5.0.28.jar) to /server/bundler/libraries/datafixerupper-5.0.28.jar
2024-03-28 18:15:20 Unpacking error_prone_annotations-2.7.1.jar (libraries:error_prone_annotations-2.7.1.jar) to /server/bundler/libraries/error_prone_annotations-2.7.1.jar
2024-03-28 18:15:20 Unpacking failureaccess-1.0.1.jar (libraries:failureaccess-1.0.1.jar) to /server/bundler/libraries/failureaccess-1.0.1.jar
2024-03-28 18:15:20 Unpacking fastutil-8.5.6.jar (libraries:fastutil-8.5.6.jar) to /server/bundler/libraries/fastutil-8.5.6.jar
2024-03-28 18:15:22 Unpacking gson-2.8.9.jar (libraries:gson-2.8.9.jar) to /server/bundler/libraries/gson-2.8.9.jar
2024-03-28 18:15:22 Unpacking guava-31.0.1-jre.jar (libraries:guava-31.0.1-jre.jar) to /server/bundler/libraries/guava-31.0.1-jre.jar
2024-03-28 18:15:22 Unpacking httpclient-4.5.13.jar (libraries:httpclient-4.5.13.jar) to /server/bundler/libraries/httpclient-4.5.13.jar
2024-03-28 18:15:22 Unpacking httpcore-4.4.14.jar (libraries:httpcore-4.4.14.jar) to /server/bundler/libraries/httpcore-4.4.14.jar
2024-03-28 18:15:22 Unpacking j2objc-annotations-1.3.jar (libraries:j2objc-annotations-1.3.jar) to /server/bundler/libraries/j2objc-annotations-1.3.jar
2024-03-28 18:15:22 Unpacking javabridge-1.2.24.jar (libraries:javabridge-1.2.24.jar) to /server/bundler/libraries/javabridge-1.2.24.jar
2024-03-28 18:15:22 Unpacking javax.inject-1.jar (libraries:javax.inject-1.jar) to /server/bundler/libraries/javax.inject-1.jar
2024-03-28 18:15:22 Unpacking jcl-over-slf4j-1.7.32.jar (libraries:jcl-over-slf4j-1.7.32.jar) to /server/bundler/libraries/jcl-over-slf4j-1.7.32.jar
2024-03-28 18:15:22 Unpacking jline-2.12.1.jar (libraries:jline-2.12.1.jar) to /server/bundler/libraries/jline-2.12.1.jar
2024-03-28 18:15:22 Unpacking jna-5.10.0.jar (libraries:jna-5.10.0.jar) to /server/bundler/libraries/jna-5.10.0.jar
2024-03-28 18:15:23 Unpacking jna-platform-5.10.0.jar (libraries:jna-platform-5.10.0.jar) to /server/bundler/libraries/jna-platform-5.10.0.jar
2024-03-28 18:15:23 Unpacking jopt-simple-5.0.4.jar (libraries:jopt-simple-5.0.4.jar) to /server/bundler/libraries/jopt-simple-5.0.4.jar
2024-03-28 18:15:23 Unpacking json-simple-1.1.1.jar (libraries:json-simple-1.1.1.jar) to /server/bundler/libraries/json-simple-1.1.1.jar
2024-03-28 18:15:23 Unpacking jsr305-3.0.2.jar (libraries:jsr305-3.0.2.jar) to /server/bundler/libraries/jsr305-3.0.2.jar
2024-03-28 18:15:23 Unpacking listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar (libraries:listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar) to /server/bundler/libraries/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
2024-03-28 18:15:23 Unpacking log4j-api-2.17.0.jar (libraries:log4j-api-2.17.0.jar) to /server/bundler/libraries/log4j-api-2.17.0.jar
2024-03-28 18:15:23 Unpacking log4j-core-2.17.0.jar (libraries:log4j-core-2.17.0.jar) to /server/bundler/libraries/log4j-core-2.17.0.jar
2024-03-28 18:15:23 Unpacking log4j-iostreams-2.17.0.jar (libraries:log4j-iostreams-2.17.0.jar) to /server/bundler/libraries/log4j-iostreams-2.17.0.jar
2024-03-28 18:15:23 Unpacking log4j-slf4j18-impl-2.17.0.jar (libraries:log4j-slf4j18-impl-2.17.0.jar) to /server/bundler/libraries/log4j-slf4j18-impl-2.17.0.jar
2024-03-28 18:15:23 Unpacking logging-1.0.0.jar (libraries:logging-1.0.0.jar) to /server/bundler/libraries/logging-1.0.0.jar
2024-03-28 18:15:23 Unpacking maven-artifact-3.8.5.jar (libraries:maven-artifact-3.8.5.jar) to /server/bundler/libraries/maven-artifact-3.8.5.jar
2024-03-28 18:15:23 Unpacking maven-builder-support-3.8.5.jar (libraries:maven-builder-support-3.8.5.jar) to /server/bundler/libraries/maven-builder-support-3.8.5.jar
2024-03-28 18:15:23 Unpacking maven-model-3.8.5.jar (libraries:maven-model-3.8.5.jar) to /server/bundler/libraries/maven-model-3.8.5.jar
2024-03-28 18:15:23 Unpacking maven-model-builder-3.8.5.jar (libraries:maven-model-builder-3.8.5.jar) to /server/bundler/libraries/maven-model-builder-3.8.5.jar
2024-03-28 18:15:23 Unpacking maven-repository-metadata-3.8.5.jar (libraries:maven-repository-metadata-3.8.5.jar) to /server/bundler/libraries/maven-repository-metadata-3.8.5.jar
2024-03-28 18:15:23 Unpacking maven-resolver-api-1.6.3.jar (libraries:maven-resolver-api-1.6.3.jar) to /server/bundler/libraries/maven-resolver-api-1.6.3.jar
2024-03-28 18:15:23 Unpacking maven-resolver-connector-basic-1.7.3.jar (libraries:maven-resolver-connector-basic-1.7.3.jar) to /server/bundler/libraries/maven-resolver-connector-basic-1.7.3.jar
2024-03-28 18:15:23 Unpacking maven-resolver-impl-1.6.3.jar (libraries:maven-resolver-impl-1.6.3.jar) to /server/bundler/libraries/maven-resolver-impl-1.6.3.jar
2024-03-28 18:15:23 Unpacking maven-resolver-provider-3.8.5.jar (libraries:maven-resolver-provider-3.8.5.jar) to /server/bundler/libraries/maven-resolver-provider-3.8.5.jar
2024-03-28 18:15:23 Unpacking maven-resolver-spi-1.6.3.jar (libraries:maven-resolver-spi-1.6.3.jar) to /server/bundler/libraries/maven-resolver-spi-1.6.3.jar
2024-03-28 18:15:23 Unpacking maven-resolver-transport-http-1.7.3.jar (libraries:maven-resolver-transport-http-1.7.3.jar) to /server/bundler/libraries/maven-resolver-transport-http-1.7.3.jar
2024-03-28 18:15:23 Unpacking maven-resolver-util-1.6.3.jar (libraries:maven-resolver-util-1.6.3.jar) to /server/bundler/libraries/maven-resolver-util-1.6.3.jar
2024-03-28 18:15:23 Unpacking mysql-connector-java-8.0.29.jar (libraries:mysql-connector-java-8.0.29.jar) to /server/bundler/libraries/mysql-connector-java-8.0.29.jar
2024-03-28 18:15:23 Unpacking netty-buffer-4.1.77.Final.jar (libraries:netty-buffer-4.1.77.Final.jar) to /server/bundler/libraries/netty-buffer-4.1.77.Final.jar
2024-03-28 18:15:23 Unpacking netty-codec-4.1.77.Final.jar (libraries:netty-codec-4.1.77.Final.jar) to /server/bundler/libraries/netty-codec-4.1.77.Final.jar
2024-03-28 18:15:23 Unpacking netty-common-4.1.77.Final.jar (libraries:netty-common-4.1.77.Final.jar) to /server/bundler/libraries/netty-common-4.1.77.Final.jar
2024-03-28 18:15:23 Unpacking netty-handler-4.1.77.Final.jar (libraries:netty-handler-4.1.77.Final.jar) to /server/bundler/libraries/netty-handler-4.1.77.Final.jar
2024-03-28 18:15:23 Unpacking netty-resolver-4.1.77.Final.jar (libraries:netty-resolver-4.1.77.Final.jar) to /server/bundler/libraries/netty-resolver-4.1.77.Final.jar
2024-03-28 18:15:23 Unpacking netty-transport-4.1.77.Final.jar (libraries:netty-transport-4.1.77.Final.jar) to /server/bundler/libraries/netty-transport-4.1.77.Final.jar
2024-03-28 18:15:23 Unpacking netty-transport-classes-epoll-4.1.77.Final.jar (libraries:netty-transport-classes-epoll-4.1.77.Final.jar) to /server/bundler/libraries/netty-transport-classes-epoll-4.1.77.Final.jar
2024-03-28 18:15:18 OpenJDK 64-Bit Server VM warning: Option MaxRAMFraction was deprecated in version 10.0 and will likely be removed in a future release.
2024-03-28 18:15:23 Unpacking netty-transport-native-epoll-4.1.77.Final-linux-aarch_64.jar (libraries:netty-transport-native-epoll-4.1.77.Final-linux-aarch_64.jar) to /server/bundler/libraries/netty-transport-native-epoll-4.1.77.Final-linux-aarch_64.jar
2024-03-28 18:15:23 Unpacking netty-transport-native-epoll-4.1.77.Final-linux-x86_64.jar (libraries:netty-transport-native-epoll-4.1.77.Final-linux-x86_64.jar) to /server/bundler/libraries/netty-transport-native-epoll-4.1.77.Final-linux-x86_64.jar
2024-03-28 18:15:23 Unpacking netty-transport-native-unix-common-4.1.77.Final.jar (libraries:netty-transport-native-unix-common-4.1.77.Final.jar) to /server/bundler/libraries/netty-transport-native-unix-common-4.1.77.Final.jar
2024-03-28 18:15:23 Unpacking org.eclipse.sisu.inject-0.3.5.jar (libraries:org.eclipse.sisu.inject-0.3.5.jar) to /server/bundler/libraries/org.eclipse.sisu.inject-0.3.5.jar
2024-03-28 18:15:24 Unpacking oshi-core-5.8.5.jar (libraries:oshi-core-5.8.5.jar) to /server/bundler/libraries/oshi-core-5.8.5.jar
2024-03-28 18:15:24 Unpacking plexus-interpolation-1.26.jar (libraries:plexus-interpolation-1.26.jar) to /server/bundler/libraries/plexus-interpolation-1.26.jar
2024-03-28 18:15:24 Unpacking plexus-utils-3.3.0.jar (libraries:plexus-utils-3.3.0.jar) to /server/bundler/libraries/plexus-utils-3.3.0.jar
2024-03-28 18:15:24 Unpacking protobuf-java-3.19.4.jar (libraries:protobuf-java-3.19.4.jar) to /server/bundler/libraries/protobuf-java-3.19.4.jar
2024-03-28 18:15:24 Unpacking slf4j-api-1.8.0-beta4.jar (libraries:slf4j-api-1.8.0-beta4.jar) to /server/bundler/libraries/slf4j-api-1.8.0-beta4.jar
2024-03-28 18:15:24 Unpacking snakeyaml-1.30.jar (libraries:snakeyaml-1.30.jar) to /server/bundler/libraries/snakeyaml-1.30.jar
2024-03-28 18:15:24 Unpacking spigot-api-1.19-R0.1-SNAPSHOT.jar (libraries:spigot-api-1.19-R0.1-SNAPSHOT.jar) to /server/bundler/libraries/spigot-api-1.19-R0.1-SNAPSHOT.jar
2024-03-28 18:15:24 Unpacking sqlite-jdbc-3.36.0.3.jar (libraries:sqlite-jdbc-3.36.0.3.jar) to /server/bundler/libraries/sqlite-jdbc-3.36.0.3.jar
2024-03-28 18:15:24 Starting server
2024-03-28 18:15:25 Loading libraries, please wait...
2024-03-28 18:15:28 [17:15:28] [ServerMain/INFO]: Building unoptimized datafixer
2024-03-28 18:15:30 [17:15:30] [ServerMain/INFO]: Environment: authHost='https://authserver.mojang.com', accountsHost='https://api.mojang.com', sessionHost='https://sessionserver.mojang.com', servicesHost='https://api.minecraftservices.com', name='PROD'
2024-03-28 18:15:30 [17:15:30] [ServerMain/INFO]: Found new data pack file/bukkit, loading it automatically
2024-03-28 18:15:32 [17:15:32] [ServerMain/INFO]: Loaded 7 recipes
2024-03-28 18:15:33 [17:15:28] [ServerMain/INFO]: Building unoptimized datafixer
2024-03-28 18:15:33 [17:15:30] [ServerMain/INFO]: Environment: authHost='https://authserver.mojang.com', accountsHost='https://api.mojang.com', sessionHost='https://sessionserver.mojang.com', servicesHost='https://api.minecraftservices.com', name='PROD'
2024-03-28 18:15:33 [17:15:30] [ServerMain/INFO]: Found new data pack file/bukkit, loading it automatically
2024-03-28 18:15:33 [17:15:32] [ServerMain/INFO]: Loaded 7 recipes
2024-03-28 18:15:33 [17:15:33] [Server thread/INFO]: Starting minecraft server version 1.19
2024-03-28 18:15:33 [17:15:33] [Server thread/INFO]: Loading properties
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: This server is running CraftBukkit version 3553-Spigot-14a2382-ef09464 (MC: 1.19) (Implementing API version 1.19-R0.1-SNAPSHOT)
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: Debug logging is disabled
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: Server Ping Player Sample Count: 12
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: Using 4 threads for Netty based IO
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: Default game type: SURVIVAL
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: Generating keypair
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: Starting Minecraft server on *:25565
2024-03-28 18:15:34 [17:15:34] [Server thread/INFO]: Using epoll channel type
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: [WatchWolf] Loading WatchWolf v0.2.1
2024-03-28 18:15:35 [17:15:35] [Server thread/WARN]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
2024-03-28 18:15:35 [17:15:35] [Server thread/WARN]: The server will make no attempt to authenticate usernames. Beware.
2024-03-28 18:15:35 [17:15:35] [Server thread/WARN]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
2024-03-28 18:15:35 [17:15:35] [Server thread/WARN]: To change this, set "online-mode" to "true" in the server.properties file.
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Preparing level "world"
2024-03-28 18:15:35 [17:15:35] [Server thread/ERROR]: No key layers in MapLike[{}]
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: -------- World Settings For [world] --------
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Entity Activation Range: An 32 / Mo 32 / Ra 48 / Mi 16 / Tiv true / Isa false
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Entity Tracking Range: Pl 48 / An 48 / Mo 48 / Mi 32 / Other 64
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Hopper Transfer: 8 Hopper Check: 1 Hopper Amount: 1 Hopper Can Load Chunks: false
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Custom Map Seeds:  Village: 10387312 Desert: 14357617 Igloo: 14357618 Jungle: 14357619 Swamp: 14357620 Monument: 10387313 Ocean: 14357621 Shipwreck: 165745295 End City: 10387313 Slime: 987234911 Nether: 30084232 Mansion: 10387319 Fossil: 14357921 Portal: 34222645
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Experience Merge Radius: 3.0
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Mob Spawn Range: 6
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Cactus Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Cane Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Melon Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Mushroom Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Pumpkin Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Sapling Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Beetroot Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Carrot Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Potato Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Wheat Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: NetherWart Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Vine Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Cocoa Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Bamboo Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: SweetBerry Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Kelp Growth Modifier: 100%
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Max TNT Explosions: 100
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Tile Max Tick Time: 50ms Entity max Tick Time: 50ms
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Item Despawn Rate: 6000
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Item Merge Radius: 2.5
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: View Distance: 10
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Simulation Distance: 10
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Allow Zombie Pigmen to spawn from portal blocks: true
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Arrow Despawn Rate: 1200 Trident Respawn Rate:1200
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Zombie Aggressive Towards Villager: true
2024-03-28 18:15:35 [17:15:35] [Server thread/INFO]: Nerfing mobs spawned from spawners: false
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: -------- World Settings For [world_nether] --------
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Entity Activation Range: An 32 / Mo 32 / Ra 48 / Mi 16 / Tiv true / Isa false
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Entity Tracking Range: Pl 48 / An 48 / Mo 48 / Mi 32 / Other 64
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Hopper Transfer: 8 Hopper Check: 1 Hopper Amount: 1 Hopper Can Load Chunks: false
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Custom Map Seeds:  Village: 10387312 Desert: 14357617 Igloo: 14357618 Jungle: 14357619 Swamp: 14357620 Monument: 10387313 Ocean: 14357621 Shipwreck: 165745295 End City: 10387313 Slime: 987234911 Nether: 30084232 Mansion: 10387319 Fossil: 14357921 Portal: 34222645
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Experience Merge Radius: 3.0
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Mob Spawn Range: 6
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Cactus Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Cane Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Melon Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Mushroom Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Pumpkin Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Sapling Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Beetroot Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Carrot Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Potato Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Wheat Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: NetherWart Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Vine Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Cocoa Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Bamboo Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: SweetBerry Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Kelp Growth Modifier: 100%
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Max TNT Explosions: 100
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Tile Max Tick Time: 50ms Entity max Tick Time: 50ms
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Item Despawn Rate: 6000
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Item Merge Radius: 2.5
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: View Distance: 10
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Simulation Distance: 10
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Allow Zombie Pigmen to spawn from portal blocks: true
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Arrow Despawn Rate: 1200 Trident Respawn Rate:1200
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Zombie Aggressive Towards Villager: true
2024-03-28 18:15:44 [17:15:44] [Server thread/INFO]: Nerfing mobs spawned from spawners: false
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: -------- World Settings For [world_the_end] --------
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Entity Activation Range: An 32 / Mo 32 / Ra 48 / Mi 16 / Tiv true / Isa false
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Entity Tracking Range: Pl 48 / An 48 / Mo 48 / Mi 32 / Other 64
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Hopper Transfer: 8 Hopper Check: 1 Hopper Amount: 1 Hopper Can Load Chunks: false
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Custom Map Seeds:  Village: 10387312 Desert: 14357617 Igloo: 14357618 Jungle: 14357619 Swamp: 14357620 Monument: 10387313 Ocean: 14357621 Shipwreck: 165745295 End City: 10387313 Slime: 987234911 Nether: 30084232 Mansion: 10387319 Fossil: 14357921 Portal: 34222645
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Experience Merge Radius: 3.0
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Mob Spawn Range: 6
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Cactus Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Cane Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Melon Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Mushroom Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Pumpkin Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Sapling Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Beetroot Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Carrot Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Potato Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Wheat Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: NetherWart Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Vine Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Cocoa Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Bamboo Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: SweetBerry Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Kelp Growth Modifier: 100%
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Max TNT Explosions: 100
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Tile Max Tick Time: 50ms Entity max Tick Time: 50ms
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Item Despawn Rate: 6000
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Item Merge Radius: 2.5
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: View Distance: 10
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Simulation Distance: 10
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Allow Zombie Pigmen to spawn from portal blocks: true
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Arrow Despawn Rate: 1200 Trident Respawn Rate:1200
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Zombie Aggressive Towards Villager: true
2024-03-28 18:15:59 [17:15:59] [Server thread/INFO]: Nerfing mobs spawned from spawners: false
2024-03-28 18:16:14 [17:16:14] [Server thread/INFO]: Preparing start region for dimension minecraft:overworld
2024-03-28 18:16:14 [17:16:14] [Worker-Main-23/INFO]: Preparing spawn area: 0%
2024-03-28 18:16:19 [17:16:19] [Worker-Main-12/INFO]: Preparing spawn area: 91%
2024-03-28 18:16:19 [17:16:19] [Server thread/INFO]: Time elapsed: 5315 ms
2024-03-28 18:16:19 [17:16:19] [Server thread/INFO]: Preparing start region for dimension minecraft:the_nether
2024-03-28 18:16:19 [17:16:19] [Worker-Main-6/INFO]: Preparing spawn area: 0%
2024-03-28 18:16:29 [17:16:29] [Worker-Main-20/INFO]: Preparing spawn area: 96%
2024-03-28 18:16:29 [17:16:29] [Server thread/INFO]: Time elapsed: 9685 ms
2024-03-28 18:16:29 [17:16:29] [Server thread/INFO]: Preparing start region for dimension minecraft:the_end
2024-03-28 18:16:29 [17:16:29] [Worker-Main-1/INFO]: Preparing spawn area: 0%
2024-03-28 18:16:34 [17:16:34] [Worker-Main-8/INFO]: Preparing spawn area: 69%
2024-03-28 18:16:34 [17:16:34] [Server thread/INFO]: Time elapsed: 5497 ms
2024-03-28 18:16:34 [17:16:34] [Server thread/INFO]: [WatchWolf] Enabling WatchWolf v0.2.1
2024-03-28 18:16:34 [17:16:34] [Server thread/ERROR]: No key layers in MapLike[{}]
2024-03-28 18:16:34 [17:16:34] [Server thread/ERROR]: No key layers in MapLike[{}]
2024-03-28 18:16:34 [17:16:34] [Server thread/INFO]: [WatchWolf] Loading socket data...
2024-03-28 18:16:34 [17:16:34] [Server thread/INFO]: [WatchWolf] Running timings manager in Spigot mode
2024-03-28 18:16:34 [17:16:34] [Server thread/INFO]: Done (59.700s)! For help, type \"help\"""";

        ThrowableServer uut = (ThrowableServer) getServer();
        uut.subscribeToExceptionEvents((msg) -> {
            synchronized (syncronizedObject) {
                syncronizedObject.add(msg);
                syncronizedObject.notify();
            }
        });

        // exceptions are processed through the regular messages
        for (String logLine : log.split("\n")) {
            uut.raiseServerMessageEvent(logLine); // we'll get the messagess one by one
        }

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertTrue(syncronizedObject.isEmpty(), "Expected no errors got; Got instead: " + syncronizedObject.toString());
        }
    }

    @Test
    void detectServerStartedByMessageDefinedOnWrapper() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server uut = super.getServer();
        assertEquals(Server.class, uut.getClass());
        uut.subscribeToServerStartedEvents(() -> {
            synchronized (syncronizedObject) {
                syncronizedObject.incrementAndGet();
                syncronizedObject.notify();
            }
        });

        uut = new ThrowableServer(uut);

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
    void detectServerStartedByMessageCalledOnWrapper() throws Exception {
        final AtomicInteger syncronizedObject = new AtomicInteger(0);

        Server wrapper = super.getServer();
        assertEquals(Server.class, wrapper.getClass());

        ThrowableServer uut = new ThrowableServer(wrapper);
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

        for (String line : startupSequence.split("\n")) wrapper.raiseServerMessageEvent(line);

        synchronized (syncronizedObject) {
            // we didn't launch the event, so nothing should invoke
            try {
                syncronizedObject.wait(SMALL_ASSERT_TIMEOUT);
            } catch (InterruptedException ignored) {}
            assertEquals(0, syncronizedObject.get(), "Event was raised before invoking the function");
        }

        wrapper.raiseServerMessageEvent("[18:47:45] [Server thread/INFO]: Done (6.656s)! For help, type \"help\"");

        synchronized (syncronizedObject) {
            syncronizedObject.wait(WAIT_TIMEOUT);
            assertEquals(1, syncronizedObject.get(), "Event was " + ((syncronizedObject.get() == 0) ? "not risen" : "risen more than once"));
        }
    }
}
