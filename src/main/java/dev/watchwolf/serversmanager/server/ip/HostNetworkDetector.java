package dev.watchwolf.serversmanager.server.ip;

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Answers whether this process shares the host's network namespace.
 *
 * It decides whether the address a requester reached us on is worth anything to them. The Minecraft
 * servers we start are *sibling* containers: their ports are published on the **host**, not in our
 * namespace. So when we run in a container of our own -- the default `docker compose` deployment
 * publishes `8000:8000` on a bridge network -- every connection reaches us as the bridge gateway and
 * our own side of it is a `172.x` address nobody outside the bridge can route to. Handing that back
 * would be strictly worse than the `MACHINE_IP` guess it replaced.
 *
 * With `network_mode: host`, or when running straight on the host, our address *is* the host's, and
 * it is the best answer available: it is the interface the requester demonstrably reached.
 *
 * The signal is that a bridged container sees only `lo` and its own `eth*`, while a process sharing
 * the host namespace sees the host's Docker bridges (`docker0`, `br-<id>`) -- which must exist,
 * since we start containers through them.
 */
public class HostNetworkDetector {
    private static final Path[] CONTAINER_MARKERS = new Path[]{
            Paths.get("/.dockerenv"),      // Docker
            Paths.get("/run/.containerenv") // Podman
    };

    private Boolean sharesHostNetwork;

    /**
     * @return Whether the addresses this process sees are the host's own
     */
    public synchronized boolean sharesHostNetwork() {
        if (this.sharesHostNetwork == null) this.sharesHostNetwork = !isContainerized() || seesHostDockerBridges();
        return this.sharesHostNetwork;
    }

    static boolean isContainerized() {
        for (Path marker : CONTAINER_MARKERS) {
            if (Files.exists(marker)) return true;
        }
        return false;
    }

    /**
     * @return Whether a Docker bridge interface is visible from here
     */
    static boolean seesHostDockerBridges() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return false;

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                String name = networkInterface.getName();
                if (name == null) continue;
                if (name.equals("docker0") || name.startsWith("br-")) return true;
            }
        } catch (IOException ignore) {
            // can't tell; assume the conservative answer
        }
        return false;
    }
}
