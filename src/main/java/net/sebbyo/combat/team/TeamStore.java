package net.sebbyo.combat.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Team membership, invites, and friendly-fire lookups. Pure-ish (UUID-keyed, no
 * Minecraft types) so it unit-tests off-game. Teams persist to JSON; pending
 * invites are transient (in-memory only).
 */
public final class TeamStore {

    /** Command-feedback result. */
    public record Result(boolean ok, String message) {
        public static Result ok(String m) { return new Result(true, m); }
        public static Result fail(String m) { return new Result(false, m); }
    }

    private record Invite(String team, UUID inviter) {}

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Team> byName = new HashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public Team teamOf(UUID player) {
        String n = byPlayer.get(player);
        return n == null ? null : byName.get(n);
    }

    public boolean sameTeam(UUID a, UUID b) {
        String x = byPlayer.get(a);
        return x != null && x.equals(byPlayer.get(b));
    }

    public Collection<Team> all() { return byName.values(); }

    public Result create(UUID leader, String name) {
        if (name == null || !NAME.matcher(name).matches())
            return Result.fail("Invalid name. Use 1-16 letters, digits, or underscores.");
        if (byPlayer.containsKey(leader)) return Result.fail("You are already in a team.");
        if (byName.containsKey(name)) return Result.fail("A team named '" + name + "' already exists.");
        Team t = new Team(name, leader);
        byName.put(name, t);
        byPlayer.put(leader, name);
        return Result.ok("Created team '" + name + "'.");
    }

    public Result invite(UUID leader, UUID target) {
        Team t = teamOf(leader);
        if (t == null || !t.leader.equals(leader)) return Result.fail("Only the team leader can invite.");
        if (byPlayer.containsKey(target)) return Result.fail("That player is already in a team.");
        invites.put(target, new Invite(t.name, leader));
        return Result.ok("Invite sent.");
    }

    public Result accept(UUID target) {
        Invite inv = invites.remove(target);
        if (inv == null) return Result.fail("You have no pending invite.");
        if (byPlayer.containsKey(target)) return Result.fail("You are already in a team.");
        Team t = byName.get(inv.team());
        if (t == null) return Result.fail("That team no longer exists.");
        t.members.add(target);
        byPlayer.put(target, t.name);
        return Result.ok("Joined team '" + t.name + "'.");
    }

    public Result decline(UUID target) {
        return invites.remove(target) != null ? Result.ok("Invite declined.") : Result.fail("No pending invite.");
    }

    public Result leave(UUID player) {
        Team t = teamOf(player);
        if (t == null) return Result.fail("You are not in a team.");
        boolean wasLeader = t.leader.equals(player);
        t.members.remove(player);
        byPlayer.remove(player);
        if (t.members.isEmpty()) {
            byName.remove(t.name);
            return Result.ok("You left and disbanded '" + t.name + "' (it was empty).");
        }
        if (wasLeader) {
            t.leader = t.members.get(0); // oldest remaining member
            return Result.ok("You left '" + t.name + "'; leadership transferred.");
        }
        return Result.ok("You left '" + t.name + "'.");
    }

    public Result disband(UUID leader) {
        Team t = teamOf(leader);
        if (t == null || !t.leader.equals(leader)) return Result.fail("Only the team leader can disband.");
        for (UUID m : t.members) byPlayer.remove(m);
        byName.remove(t.name);
        return Result.ok("Disbanded team '" + t.name + "'.");
    }

    // ── persistence (teams only; invites are transient) ──

    public void save(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(byName, w);
        }
    }

    public void load(Path file) throws IOException {
        byName.clear();
        byPlayer.clear();
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Map<String, Team> loaded = GSON.fromJson(r, new TypeToken<Map<String, Team>>() {}.getType());
            if (loaded != null) {
                byName.putAll(loaded);
                for (Team t : byName.values())
                    for (UUID m : t.members) byPlayer.put(m, t.name);
            }
        }
    }
}
