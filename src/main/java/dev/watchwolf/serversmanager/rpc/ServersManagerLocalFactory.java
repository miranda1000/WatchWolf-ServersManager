package dev.watchwolf.serversmanager.rpc;

import dev.watchwolf.core.rpc.RPCImplementer;
import dev.watchwolf.core.rpc.RPCImplementerFactory;
import dev.watchwolf.core.rpc.stubs.serversmanager.ServersManagerLocalStub;
import dev.watchwolf.serversmanager.server.ServersManager;
import dev.watchwolf.serversmanager.server.instantiator.DockerizedServerInstantiator;

public class ServersManagerLocalFactory implements RPCImplementerFactory {
    @Override
    public RPCImplementer build() {
        ServersManagerLocalStub stub = new ServersManagerLocalStub();

        ServersManager serversManager = new ServersManager(new DockerizedServerInstantiator());
        RequesteeIpGetter ipGetter = new RequesteeIpGetterFromStubGetMethod(stub);

        ServersManagerLocalImplementation localImplementation = new ServersManagerLocalImplementation(serversManager, stub, stub, ipGetter);
        stub.setRunner(localImplementation);
        stub.subscribeToCloseEvents(serversManager);

        return stub;
    }
}
