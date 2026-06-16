package net.sebbyo.combat.team;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors a {@code /party} onto a vanilla scoreboard team so members get the vanilla
 * visuals — team-coloured nametags + glow, see-invisible teammates, and locator-bar
 * grouping — plus applies an infinite {@code Glowing} effect for the outline. The
 * scoreboard team is managed entirely by the mod (the player never needs op).
 */
public final class PartyVisuals {

    private PartyVisuals() {}

    private static final List<Formatting> COLORS = List.of(
            Formatting.AQUA, Formatting.GREEN, Formatting.YELLOW, Formatting.LIGHT_PURPLE,
            Formatting.GOLD, Formatting.RED, Formatting.BLUE, Formatting.DARK_AQUA);

    private static net.minecraft.scoreboard.Team ensureTeam(MinecraftServer server, Team party) {
        Scoreboard sb = server.getScoreboard();
        net.minecraft.scoreboard.Team t = sb.getTeam(party.name);
        if (t == null) {
            t = sb.addTeam(party.name);
            t.setColor(COLORS.get(Math.floorMod(party.name.hashCode(), COLORS.size())));
            t.setFriendlyFireAllowed(false);
            t.setShowFriendlyInvisibles(true);
        }
        return t;
    }

    /** Put a player on their party's scoreboard team and apply the glow (or clear if not in a party). */
    public static void apply(MinecraftServer server, ServerPlayerEntity p) {
        Team party = TeamManager.INSTANCE.teamOf(p.getUuid());
        Scoreboard sb = server.getScoreboard();
        String name = p.getNameForScoreboard();
        if (party == null) {
            sb.clearTeam(name);
            p.removeStatusEffect(StatusEffects.GLOWING);
            return;
        }
        sb.addScoreHolderToTeam(name, ensureTeam(server, party));
        p.addStatusEffect(new StatusEffectInstance(
                StatusEffects.GLOWING, StatusEffectInstance.INFINITE, 0, false, false, true));
    }

    /** Remove a player from their scoreboard team and clear the glow (on leave/disband). */
    public static void clear(MinecraftServer server, ServerPlayerEntity p) {
        server.getScoreboard().clearTeam(p.getNameForScoreboard());
        p.removeStatusEffect(StatusEffects.GLOWING);
    }

    /** Delete the backing scoreboard team (on disband or when a party empties). */
    public static void deleteTeam(MinecraftServer server, String partyName) {
        Scoreboard sb = server.getScoreboard();
        net.minecraft.scoreboard.Team t = sb.getTeam(partyName);
        if (t != null) sb.removeTeam(t);
    }

    /** Re-apply visuals to every online member of a party. */
    public static void refreshParty(MinecraftServer server, Team party) {
        if (party == null) return;
        for (UUID m : party.members) {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(m);
            if (online != null) apply(server, online);
        }
    }
}
