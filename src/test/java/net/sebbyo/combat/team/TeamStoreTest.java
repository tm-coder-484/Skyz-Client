package net.sebbyo.combat.team;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamStoreTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void createRejectsDuplicateName() {
        TeamStore s = new TeamStore();
        assertTrue(s.create(a, "Red").ok());
        assertFalse(s.create(b, "Red").ok());
    }

    @Test
    void createRejectsInvalidName() {
        TeamStore s = new TeamStore();
        assertFalse(s.create(a, "bad name!").ok());
    }

    @Test
    void cannotBeInTwoTeams() {
        TeamStore s = new TeamStore();
        s.create(a, "Red");
        assertFalse(s.create(a, "Blue").ok());
    }

    @Test
    void inviteAcceptAddsMember() {
        TeamStore s = new TeamStore();
        s.create(a, "Red");
        assertTrue(s.invite(a, b).ok());
        assertTrue(s.accept(b).ok());
        assertTrue(s.sameTeam(a, b));
    }

    @Test
    void nonLeaderCannotInvite() {
        TeamStore s = new TeamStore();
        s.create(a, "Red");
        s.invite(a, b);
        s.accept(b);
        assertFalse(s.invite(b, c).ok());
    }

    @Test
    void leaderLeaveTransfersToOldestMember() {
        TeamStore s = new TeamStore();
        s.create(a, "Red");
        s.invite(a, b);
        s.accept(b);
        s.invite(a, c);
        s.accept(c);
        assertTrue(s.leave(a).ok());
        Team t = s.teamOf(b);
        assertNotNull(t);
        assertEquals(b, t.leader); // b joined before c -> oldest remaining
    }

    @Test
    void disbandClearsAllMembers() {
        TeamStore s = new TeamStore();
        s.create(a, "Red");
        s.invite(a, b);
        s.accept(b);
        assertTrue(s.disband(a).ok());
        assertNull(s.teamOf(a));
        assertNull(s.teamOf(b));
    }

    @Test
    void persistenceRoundTrip(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("sebbyo_teams.json");
        TeamStore s = new TeamStore();
        s.create(a, "Red");
        s.invite(a, b);
        s.accept(b);
        s.save(f);

        TeamStore loaded = new TeamStore();
        loaded.load(f);
        assertTrue(loaded.sameTeam(a, b));
        assertEquals("Red", loaded.teamOf(a).name);
        assertEquals(a, loaded.teamOf(a).leader);
        assertEquals(2, loaded.teamOf(a).members.size());
    }
}
