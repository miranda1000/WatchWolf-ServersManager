package dev.watchwolf.serversmanager.rpc;

import dev.watchwolf.core.rpc.channel.sockets.SocketMessageChannel;
import dev.watchwolf.core.rpc.stubs.serversmanager.ServersManagerLocalStub;

import java.net.InetSocketAddress;

public class RequesteeIpGetterFromStubGetMethod implements RequesteeIpGetter {
    private final ServersManagerLocalStub stub;
    public RequesteeIpGetterFromStubGetMethod(ServersManagerLocalStub stub) {
        this.stub = stub;
    }

    /**
     * Gets the IP of the latest server started
     * @return Requestee IP
     */
    @Override
    public InetSocketAddress getRequesteeIp() {
        if (!(this.stub.getLatestMessageChannelPetitionNode() instanceof SocketMessageChannel)) return null; // we can't know

        try {
            SocketMessageChannel socketChannel = (SocketMessageChannel) this.stub.getLatestMessageChannelPetitionNode();
            return new InetSocketAddress(socketChannel.getHost(), socketChannel.getPort());
        } catch (Exception ex) {
            return null; // couldn't get
        }
    }
}
