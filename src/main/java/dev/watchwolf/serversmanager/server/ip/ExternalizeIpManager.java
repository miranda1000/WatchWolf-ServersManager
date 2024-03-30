package dev.watchwolf.serversmanager.server.ip;

import java.net.InetSocketAddress;

/**
 * Will convert an IP to local or remote depending of the outside connection
 */
public class ExternalizeIpManager implements IpManager {
    private final String localIp;
    private final String remoteIp;

    public ExternalizeIpManager(String localIp, String remoteIp) {
        this.localIp = localIp;
        this.remoteIp = remoteIp;
    }

    @Override
    public String getIp(String originalIp, InetSocketAddress callerIp) {
        if (callerIp == null) return originalIp; // we can't compare

        String []ipAndPort = originalIp.split(":");
        if (ipAndPort.length != 2) throw new IllegalArgumentException("originalIp must comply with <ip>:<port> pattern");

        if (callerIp.getAddress().isSiteLocalAddress()) {
            if (localIp == null) return originalIp; // IP was not provided; there's nothing we can do
            return localIp + ":" + ipAndPort[1];
        }
        else {
            if (remoteIp == null) return originalIp; // IP was not provided; there's nothing we can do
            return remoteIp + ":" + ipAndPort[1];
        }
    }
}
