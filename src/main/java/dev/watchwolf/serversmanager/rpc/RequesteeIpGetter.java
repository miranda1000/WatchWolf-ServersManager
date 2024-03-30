package dev.watchwolf.serversmanager.rpc;

import java.net.InetSocketAddress;

public interface RequesteeIpGetter {
    /**
     * Gets the IP of the latest server started
     * @return Requestee IP
     */
    InetSocketAddress getRequesteeIp();
}
