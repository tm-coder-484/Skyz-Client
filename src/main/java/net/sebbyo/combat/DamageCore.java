package net.sebbyo.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.sebbyo.combat.combat.CombatManager;
import net.sebbyo.combat.team.TeamManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single damage-interception point. Resolves the player responsible for a hit
 * (melee / projectile owner / potion thrower), blocks friendly fire between party
 * members (with a throttled message), and tags both parties of a real PvP hit.
 */
public final class DamageCore {

    private DamageCore() {}

    private static final Map<UUID, Long> lastBlockMsg = new HashMap<>();

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return true;
            ServerPlayerEntity attacker = resolveAttacker(source);
            if (attacker == null || attacker == victim) return true; // not PvP
            if (TeamManager.INSTANCE.sameTeam(attacker.getUuid(), victim.getUuid())) {
                notifyBlocked(attacker, victim);
                return false; // friendly fire: cancel; no tag, no reward
            }
            CombatManager.INSTANCE.onPvpHit(victim, attacker);
            return true;
        });
    }

    /** Action-bar "X is in your party." to the attacker, at most once per second. */
    private static void notifyBlocked(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        long now = System.currentTimeMillis();
        if (now - lastBlockMsg.getOrDefault(attacker.getUuid(), 0L) >= 1000L) {
            attacker.sendMessage(Text.literal(victim.getNameForScoreboard() + " is in your party.")
                    .formatted(Formatting.RED), true);
            lastBlockMsg.put(attacker.getUuid(), now);
        }
    }

    /** The {@link ServerPlayerEntity} responsible for this damage, or null. */
    public static ServerPlayerEntity resolveAttacker(DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity p) return p;
        Entity direct = source.getSource();
        if (direct instanceof ServerPlayerEntity p) return p;
        if (direct instanceof ProjectileEntity proj && proj.getOwner() instanceof ServerPlayerEntity p) return p;
        if (attacker instanceof ProjectileEntity proj && proj.getOwner() instanceof ServerPlayerEntity p) return p;
        return null;
    }
}
