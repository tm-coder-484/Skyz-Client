package net.sebbyo.combat.team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A team: a unique name, a current leader, and an ordered member list (oldest
 * first — index 0 is the longest-standing member, used for leadership transfer).
 * Plain public fields so Gson can serialize it directly.
 */
public final class Team {

    public String name;
    public UUID leader;
    public List<UUID> members = new ArrayList<>();

    public Team() {}

    public Team(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }
}
