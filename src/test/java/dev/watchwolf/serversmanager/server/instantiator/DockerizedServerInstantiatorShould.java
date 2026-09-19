package dev.watchwolf.serversmanager.server.instantiator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DockerizedServerInstantiatorShould {
    /**
     * Docker reports container names with a leading '/'. Comparing them raw never matched, so
     * `closeAllLaunchedServers` skipped every container and left the Tester's servers running --
     * and the ports they held made the *next* run fail on connect.
     */
    @Test
    public void matchDockerNamesDespiteTheirLeadingSlash() {
        assertEquals("MC_Server-1700000000000", DockerizedServerInstantiator.stripDockerNamePrefix("/MC_Server-1700000000000"));
    }

    @Test
    public void leaveNamesWithoutASlashAlone() {
        assertEquals("MC_Server-1700000000000", DockerizedServerInstantiator.stripDockerNamePrefix("MC_Server-1700000000000"));
        assertNull(DockerizedServerInstantiator.stripDockerNamePrefix(null));
    }
}
