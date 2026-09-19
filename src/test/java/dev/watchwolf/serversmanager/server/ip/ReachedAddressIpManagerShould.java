package dev.watchwolf.serversmanager.server.ip;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * `MACHINE_IP` is whichever interface `hostname -I` lists first. With a VirtualBox host-only adapter
 * enabled that is `192.168.56.1`, which the Tester cannot route to -- and it only finds out minutes
 * later, when the server socket refuses the connection.
 */
public class ReachedAddressIpManagerShould {
    private static final String MACHINE_IP = "192.168.56.1"; // the wrong guess from the bug report
    private static final String PUBLIC_IP = "8.8.4.4";

    private static HostNetworkDetector networkOf(boolean sharesHostNetwork) {
        return new HostNetworkDetector() {
            @Override
            public boolean sharesHostNetwork() {
                return sharesHostNetwork;
            }
        };
    }

    private static IpManager ipManager(boolean sharesHostNetwork, String reachedOn) {
        return new ReachedAddressIpManager(new ExternalizeIpManager(MACHINE_IP, PUBLIC_IP),
                networkOf(sharesHostNetwork),
                (peer) -> {
                    try {
                        return (reachedOn == null) ? null : InetAddress.getByName(reachedOn);
                    } catch (UnknownHostException ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    @Test
    public void answerWithTheAddressTheRequesterReachedUsOn() {
        IpManager uut = ipManager(true, "192.168.1.50");

        assertEquals("192.168.1.50:8001", uut.getIp("127.0.0.1:8001", new InetSocketAddress("192.168.1.77", 45000)));
    }

    @Test
    public void keepThePortOfTheServerThatWasStarted() {
        IpManager uut = ipManager(true, "192.168.1.50");

        assertEquals("192.168.1.50:8003", uut.getIp("127.0.0.1:8003", new InetSocketAddress("192.168.1.77", 45000)));
    }

    @Test
    public void answerWithLoopbackWhenThatIsWhatWasReached() {
        // a Tester on the same machine reached us on 127.0.0.1, and the published ports are there too
        IpManager uut = ipManager(true, "127.0.0.1");

        assertEquals("127.0.0.1:8001", uut.getIp("127.0.0.1:8001", new InetSocketAddress("127.0.0.1", 45000)));
    }

    @Test
    public void fallBackToTheConfiguredIpWhenOurAddressesAreNotTheHostsOnes() {
        // bridged container: we were reached on 172.18.0.2, which is meaningless outside the bridge,
        // and the servers we start publish their ports on the host anyway
        IpManager uut = ipManager(false, "172.18.0.2");

        assertEquals(MACHINE_IP + ":8001", uut.getIp("127.0.0.1:8001", new InetSocketAddress("172.18.0.1", 45000)));
    }

    @Test
    public void fallBackToTheConfiguredIpWhenTheLocalAddressCannotBeWorkedOut() {
        IpManager uut = ipManager(true, null);

        assertEquals(MACHINE_IP + ":8001", uut.getIp("127.0.0.1:8001", new InetSocketAddress("192.168.1.77", 45000)));
    }

    @Test
    public void fallBackToTheConfiguredIpWhenTheLocalAddressNamesNoInterface() {
        IpManager uut = ipManager(true, "0.0.0.0");

        assertEquals(MACHINE_IP + ":8001", uut.getIp("127.0.0.1:8001", new InetSocketAddress("192.168.1.77", 45000)));
    }

    @Test
    public void fallBackToTheConfiguredIpWhenWeDoNotKnowWhoAsked() {
        IpManager uut = ipManager(true, "192.168.1.50");

        assertEquals("127.0.0.1:8001", uut.getIp("127.0.0.1:8001", null));
    }

    @Test
    public void stillAnswerRemoteRequestersWithThePublicIpWhenFallingBack() {
        IpManager uut = ipManager(false, "172.18.0.2");

        assertEquals(PUBLIC_IP + ":8001", uut.getIp("127.0.0.1:8001", new InetSocketAddress("8.8.8.8", 45000)));
    }
}
