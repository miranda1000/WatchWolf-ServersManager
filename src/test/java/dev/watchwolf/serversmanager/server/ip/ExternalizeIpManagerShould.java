package dev.watchwolf.serversmanager.server.ip;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExternalizeIpManagerShould {
    @Test
    public void returnTheLocalIpIfRequestWasMadeInTheSameMachine() {
        String machineIp = "192.168.1.50";

        IpManager ipManager = new ExternalizeIpManager(machineIp, "8.8.4.4");

        String appropriateIp = ipManager.getIp("127.0.0.1:4000", new InetSocketAddress(machineIp, 45000));

        assertEquals(machineIp + ":4000", appropriateIp);
    }

    @Test
    public void returnTheLocalIpIfRequestWasMadeInTheSameMachineAndUnspecifiedRemoteIp() {
        String machineIp = "192.168.1.50";

        IpManager ipManager = new ExternalizeIpManager(machineIp, null);

        String appropriateIp = ipManager.getIp("127.0.0.1:4000", new InetSocketAddress(machineIp, 45000));

        assertEquals(machineIp + ":4000", appropriateIp);
    }

    @Test
    public void returnTheInputIfRequestWasMadeInTheSameMachineAndUnspecifiedLocalIp() {
        String in = "127.0.0.1:4000";

        IpManager ipManager = new ExternalizeIpManager(null, null);

        String appropriateIp = ipManager.getIp(in, new InetSocketAddress("192.168.1.50", 45000));

        assertEquals(in, appropriateIp);
    }

    @Test
    public void returnTheLocalIpIfRequestWasMadeInTheSameLan() {
        String machineIp = "192.168.1.50";

        IpManager ipManager = new ExternalizeIpManager(machineIp, "8.8.4.4");

        String appropriateIp = ipManager.getIp("127.0.0.1:4000", new InetSocketAddress("192.168.0.5", 45000));

        assertEquals(machineIp + ":4000", appropriateIp);
    }

    @Test
    public void returnTheRemoteIpIfRequestWasMadeOutsideTheLan() {
        String remoteIp = "8.8.4.4";

        IpManager ipManager = new ExternalizeIpManager("192.168.1.50", remoteIp);

        String appropriateIp = ipManager.getIp("127.0.0.1:4000", new InetSocketAddress("8.8.8.8", 45000));

        assertEquals(remoteIp + ":4000", appropriateIp);
    }

    @Test
    public void returnTheRemoteIpIfRequestWasMadeOutsideTheLanAndUnspecifiedLocalIp() {
        String remoteIp = "8.8.4.4";

        IpManager ipManager = new ExternalizeIpManager(null, remoteIp);

        String appropriateIp = ipManager.getIp("127.0.0.1:4000", new InetSocketAddress("8.8.8.8", 45000));

        assertEquals(remoteIp + ":4000", appropriateIp);
    }

    @Test
    public void returnTheInputIfRequestWasMadeOutsideTheLanAndUnspecifiedRemoteIp() {
        String in = "127.0.0.1:4000";

        IpManager ipManager = new ExternalizeIpManager(null, null);

        String appropriateIp = ipManager.getIp(in, new InetSocketAddress("8.8.8.8", 45000));

        assertEquals(in, appropriateIp);
    }

    @Test
    public void returnTheInputIfUnspecifiedConnection() {
        String in = "127.0.0.1:4000";

        IpManager ipManager = new ExternalizeIpManager("192.168.1.50", "8.8.4.4");

        String appropriateIp = ipManager.getIp(in, null);

        assertEquals(in, appropriateIp);
    }
}
