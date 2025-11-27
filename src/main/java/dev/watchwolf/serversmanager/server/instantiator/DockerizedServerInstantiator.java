package dev.watchwolf.serversmanager.server.instantiator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import dev.watchwolf.serversmanager.server.ServerRequirements;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DockerizedServerInstantiator implements ServerInstantiator {
    public interface StdioCallback {
        void onLineGot(String line, boolean stderr);
    }

    public static class StdioAdapter extends ResultCallback.Adapter<Frame> implements StdioCallback {
        private final String serverId;
        private final StdioCallback callback;

        public StdioAdapter(String serverId) {
            this.serverId = serverId;
            this.callback = (String line, boolean stderr) -> {
                System.out.println("[" + this.serverId + "] " + (stderr ? "(err) " : "") + line);
            };
        }

        public StdioAdapter(String serverId, StdioCallback callback) {
            this.serverId = serverId;
            this.callback = callback;
        }

        @Override
        public void onNext(Frame object) {
            String line = new String(object.getPayload());
            if (!line.endsWith("\n")) System.err.println("Expected full line (ending with '\n'), got '" + line + "' instead");
            else line = line.substring(0, line.length()-1);
            this.onLineGot(line, object.getStreamType().equals(StreamType.STDERR));
        }

        @Override
        public void onLineGot(String line, boolean stderr) {
            this.callback.onLineGot(line, stderr);
        }
    }

    public static class DockerContainerStoppedObserver extends Thread {
        private static final int DELAY_BETWEEN_CHECKS = 1_500;

        private final Logger logger;
        private final String serverId;
        private final Server callable;

        public DockerContainerStoppedObserver(String serverId, Server callable) {
            this.logger = LogManager.getLogger(DockerContainerStoppedObserver.class.getName());

            this.serverId = serverId;
            this.callable = callable;
        }

        public String getServerId() {
            return this.serverId;
        }

        @Override
        public void run() {
            this.logger.info("Listening for Docker " + this.serverId + " until it stops...");

            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            final DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

            boolean dockerStarted;
            do {
                ListContainersCmd listContainersCmd = dockerClient.listContainersCmd()
                        .withNameFilter(Collections.singletonList(this.serverId));

                List<Container> servers = listContainersCmd.exec();
                dockerStarted = !servers.isEmpty();

                try {
                    if (dockerStarted) Thread.sleep(DELAY_BETWEEN_CHECKS);
                } catch (InterruptedException ignore) {}
            } while (dockerStarted);

            // server stopped
            this.logger.info("Docker " + this.serverId + " closed");
            this.callable.raiseServerStoppedEvent();
        }
    }

    public static final int BASE_PORT = 8001;

    private final ArrayList<DockerContainerStoppedObserver> serverListeners = new ArrayList<>();
    private static final Logger logger = LogManager.getLogger(DockerizedServerInstantiator.class.getName());

    private static String getDockerCommand(String jarName, String ram) {
        logger.traceEntry(null, jarName, ram);
        String ramParam = "-XX:MaxRAMFraction=1"; // unlimited memory
        if (ram != null) {
            // RAM limit specified
            // TODO `java_ram_param="-Xmx${memory^^}"`
        }

        return logger.traceExit("java " + ramParam + " -jar " + jarName + " nogui"); // run the server
    }

    /**
     * Gets the max port used by ServersManager instances, and then adds one.
     * @return Next port that ServersManager will have to use
     */
    private static synchronized int getNextServerPort() {
        logger.traceEntry();
        // get the server containers running
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        final DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        // prepare command to retrieve the list of (running) containers with a matching name
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd()
                // implicit with `listContainersCmd()` without `withShowAll(true)`
                /*.withStatusFilter(Arrays.asList("running"))*/;

        List<Container> exec = listContainersCmd.exec();
        System.out.println("Got " + exec.size() + " server containers running");

        // get the ports being used
        Set<Integer> usedPorts = new HashSet<>();
        for (Container server : exec) {
            for (ContainerPort port : server.getPorts()) usedPorts.add(port.getPublicPort());
        }
        System.out.println("Got the following used ports: " + usedPorts.toString());

        // get the first free port
        int freePort = BASE_PORT;
        while (usedPorts.contains(freePort) || usedPorts.contains(freePort+1)) freePort += 2; // we need 2 consecutive ports
        return logger.traceExit(freePort);
    }

    private static String getStartedServerIp(String containerId) {
        logger.traceEntry(null, containerId);
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        final DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        // prepare command to retrieve the list of (running) containers with the provided ID
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd()
                // implicit with `listContainersCmd()` without `withShowAll(true)`
                //.withStatusFilter(Arrays.asList("running"))
                .withIdFilter(Arrays.asList(containerId));

        List<Container> exec = listContainersCmd.exec();
        if (exec.isEmpty()) throw new IllegalArgumentException("Couldn't find any active container with ID " + containerId);
        if (exec.size() > 1) System.err.println("Got more than one containers while filtering with ID " + containerId);

        Container serverContainer = exec.get(0);
        Set<Integer> containerPorts = new HashSet<>();
        for (ContainerPort port : serverContainer.getPorts()) {
            containerPorts.add(port.getPublicPort());
        }

        List<Integer> ports = containerPorts.stream().sorted().toList();
        if (containerPorts.size() != 2) throw new IllegalArgumentException("Expecting 2 ports on docker container " + containerId + "; got " + ports.toString() + " instead");
        if (ports.get(0) != ports.get(1)-1) System.err.println("Expecting docker container ports to be consecutive; got " + ports.toString() + " instead");

        return logger.traceExit("127.0.0.1:" + ports.get(0));
    }

    /**
     * Attaches Docker output to a callback method
     * @param dockerClient Docker client
     * @param container Container to attach
     * @param callback Object with the callback method to call
     */
    private static void attachStdio(DockerClient dockerClient, CreateContainerResponse container, ResultCallback<Frame> callback) {
        logger.traceEntry(null, dockerClient, container, callback);
        dockerClient.logContainerCmd(container.getId())
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(callback);
        logger.traceExit();
    }

    private static String getDockerImageForJavaVersion(int javaVersion) {
        logger.traceEntry(null, javaVersion);
        String jdk_docker = "openjdk";
        String image = jdk_docker + ":" + javaVersion;
        return logger.traceExit(image);
    }

    @Override
    public Server startServer(Path folderLocation, String entrypoint, int javaVersion) {
        synchronized (DockerizedServerInstantiator.class) {
            logger.traceEntry(null, folderLocation, entrypoint, javaVersion);
        }
        final String serverId = "MC_Server-" + ServerRequirements.getHashFromServerPath(folderLocation.toString());
        String dockerCmd = getDockerCommand(entrypoint, null); // TODO specify ram

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        final DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        CreateContainerResponse container;
        synchronized (DockerizedServerInstantiator.class) {
            int port = getNextServerPort();
            int socketPort = port + 1;

            // equivalent to:
            // docker run -i --rm --name "$id" -p "$port:$port/tcp" -p "$port:$port/udp" -p "$socket_port:$socket_port" ${memory:+"--memory=$memory"} ${cpus:+"--cpus=$cpus"} -v "$(pwd)/$path":/server "openjdk:$java_version"
            // TODO specify max memory
            String dockerImage = DockerizedServerInstantiator.getDockerImageForJavaVersion(javaVersion);
            container = dockerClient.createContainerCmd(dockerImage)
                    .withName(serverId)
                    .withHostConfig(new HostConfig()
                            .withPortBindings(
                                    PortBinding.parse(port + ":25565/tcp"),
                                    PortBinding.parse(port + ":25565/udp"),
                                    PortBinding.parse(socketPort + ":25566")
                            )
                            .withAutoRemove(true)
                            .withBinds(Bind.parse(folderLocation.toString() + ":/server")))
                    .withExposedPorts(new ExposedPort(25565, InternetProtocol.TCP),
                            new ExposedPort(25565, InternetProtocol.UDP),
                            new ExposedPort(25566, InternetProtocol.TCP))
                    .withWorkingDir("/server")
                    .withEntrypoint("/bin/sh", "-c")
                    .withCmd(dockerCmd).exec();
            dockerClient.startContainerCmd(container.getId()).exec();
        }

        final AtomicReference<Server> server = new AtomicReference<>();
        StdioCallback stdioCallback = (line, err) -> {
            if (server.get() == null) return; // still not linked (this shouldn't be called)
            server.get().raiseServerMessageEvent(line);
        };
        DockerizedServerInstantiator.attachStdio(dockerClient, container, new StdioAdapter(serverId, stdioCallback));

        server.set(new Server(DockerizedServerInstantiator.getStartedServerIp(container.getId())));

        synchronized (this) {
            // launch server stopped event when stopped
            DockerContainerStoppedObserver observer = new DockerContainerStoppedObserver(serverId, server.get());
            this.serverListeners.add(observer);
            observer.start();
        }

        synchronized (DockerizedServerInstantiator.class) {
            return logger.traceExit(server.get());
        }
    }

    public void closeAllLaunchedServers() {
        synchronized (DockerizedServerInstantiator.class) {
            logger.traceEntry();
        }
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        final DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd()
                .withNameFilter(Collections.singletonList("MC_Server-*"));

        List<Container> servers = listContainersCmd.exec();
        System.out.println("Server dockers got: " + servers.toString());
        for (Container container : servers) {
            boolean anyNameMatch = false;
            synchronized (this) {
                for (String name : container.getNames()) {
                    anyNameMatch |= this.serverListeners.stream().anyMatch(lis -> lis.getServerId().equals(name));
                }
            }
            if (!anyNameMatch) continue; // not launched by this instantiator; skip

            System.out.println("Stopping container " + container.getId() + "...");
            try {
                dockerClient.killContainerCmd(container.getId()).exec();
            } catch (Exception ignore) {}
        }
        synchronized (DockerizedServerInstantiator.class) {
            logger.traceExit();
        }
    }

    @Override
    public void close() {
        synchronized (DockerizedServerInstantiator.class) {
            logger.traceEntry();
        }
        this.closeAllLaunchedServers(); // this will cause all the threads to exit

        // wait for all the threads to stop
        for (DockerContainerStoppedObserver observer : this.serverListeners) {
            try {
                observer.join();
            } catch (InterruptedException ignore) {}
        }
        synchronized (DockerizedServerInstantiator.class) {
            logger.traceExit();
        }
    }
}
