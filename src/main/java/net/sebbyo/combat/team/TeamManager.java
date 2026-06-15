package net.sebbyo.combat.team;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.sebbyo.combat.SebbyoCombatMod;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Server-facing wrapper over {@link TeamStore}: world-folder persistence + lookups. */
public final class TeamManager {

    public static final TeamManager INSTANCE = new TeamManager();

    private TeamManager() {}

    private final TeamStore store = new TeamStore();
    private Path file;

    public TeamStore store() {
        return store;
    }

    public boolean sameTeam(UUID a, UUID b) {
        return store.sameTeam(a, b);
    }

    public Team teamOf(UUID id) {
        return store.teamOf(id);
    }

    public void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("sebbyo_teams.json");
        try {
            store.load(file);
        } catch (IOException e) {
            SebbyoCombatMod.LOGGER.warn("[Sebbyo] Failed to load teams: {}", e.toString());
        }
    }

    public void save() {
        if (file == null) return;
        try {
            store.save(file);
        } catch (IOException e) {
            SebbyoCombatMod.LOGGER.warn("[Sebbyo] Failed to save teams: {}", e.toString());
        }
    }
}
