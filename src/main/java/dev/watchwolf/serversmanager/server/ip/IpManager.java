package dev.watchwolf.serversmanager.server.ip;

import java.net.InetSocketAddress;

public interface IpManager {
    /**
     * Docker behaves differently depending
     * @param originalIp
     * @param callerIp
     * @return
     */
    String getIp(String originalIp, InetSocketAddress callerIp);
}
