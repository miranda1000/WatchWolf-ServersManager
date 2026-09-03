package dev.watchwolf.serversmanager.server.ip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Answers `startServer` with the address the requester demonstrably reached us on, keeping the
 * server's port.
 *
 * `MACHINE_IP` is `hostname -I | awk '{print $1}'` -- whichever interface the kernel happens to list
 * first. On a machine with a VirtualBox host-only adapter, a VPN or a second NIC, that is routinely
 * an address the Tester cannot route to, and the Tester has no way of knowing: it dutifully connects
 * to what it was told and fails minutes later with "connection refused" on a port it never saw.
 *
 * We can do better, because the requester already proved which of our addresses works -- they used
 * it to reach us. The local end of that conversation is the answer, and no configuration can get it
 * wrong.
 *
 * That only holds while our addresses are the host's, which is what {@link HostNetworkDetector}
 * checks; otherwise we fall back to whoever we wrap ({@link ExternalizeIpManager}). Either way the
 * chosen source is logged, so a bug report says where the address came from.
 */
public class ReachedAddressIpManager implements IpManager {
    private static final Logger logger = LogManager.getLogger(ReachedAddressIpManager.class.getName());

    /**
     * How we work out which of our addresses a peer reached us on.
     */
    public interface LocalAddressResolver {
        /**
         * @param peer Who connected to us
         * @return Our side of that conversation, or null if it can't be worked out
         */
        InetAddress resolve(InetSocketAddress peer);
    }

    private final IpManager fallback;
    private final HostNetworkDetector networkDetector;
    private final LocalAddressResolver localAddressResolver;

    public ReachedAddressIpManager(IpManager fallback) {
        this(fallback, new HostNetworkDetector(), ReachedAddressIpManager::routeTo);
    }

    public ReachedAddressIpManager(IpManager fallback, HostNetworkDetector networkDetector, LocalAddressResolver localAddressResolver) {
        this.fallback = fallback;
        this.networkDetector = networkDetector;
        this.localAddressResolver = localAddressResolver;
    }

    @Override
    public String getIp(String originalIp, InetSocketAddress callerIp) {
        String []ipAndPort = originalIp.split(":");
        if (ipAndPort.length != 2) throw new IllegalArgumentException("originalIp must comply with <ip>:<port> pattern");

        String reachedAddress = this.getReachedAddress(callerIp);
        if (reachedAddress == null) {
            String fallbackIp = this.fallback.getIp(originalIp, callerIp);
            logger.info("Answering " + fallbackIp + " (source: MACHINE_IP/PUBLIC_IP; the address " +
                    ((callerIp == null) ? "the requester used is unknown" : "we were reached on is not one the requester can route to") + ")");
            return fallbackIp;
        }

        String ip = reachedAddress + ":" + ipAndPort[1];
        logger.info("Answering " + ip + " (source: the address " + callerIp.getAddress().getHostAddress() + " reached us on)");
        return ip;
    }

    /**
     * @return The address the caller reached us on, or null if it is not one we can hand out
     */
    private String getReachedAddress(InetSocketAddress callerIp) {
        if (callerIp == null || callerIp.getAddress() == null) return null; // we can't know
        if (!this.networkDetector.sharesHostNetwork()) return null; // our addresses aren't the host's

        InetAddress local = this.localAddressResolver.resolve(callerIp);
        if (local == null || local.isAnyLocalAddress()) return null; // "0.0.0.0" names no interface
        return local.getHostAddress();
    }

    /**
     * Asks the routing table which of our addresses would be used to talk back to <peer>. That is
     * the local end of the connection the peer opened -- connecting a datagram socket only consults
     * the routing table, it sends nothing.
     */
    private static InetAddress routeTo(InetSocketAddress peer) {
        try (DatagramSocket probe = new DatagramSocket()) {
            probe.connect(peer.getAddress(), (peer.getPort() > 0) ? peer.getPort() : 1);
            InetAddress local = probe.getLocalAddress();
            return (local == null || local.isAnyLocalAddress()) ? null : local;
        } catch (Exception ex) {
            logger.warn("Couldn't work out which address " + peer + " reached us on: " + ex.toString());
            return null;
        }
    }
}
