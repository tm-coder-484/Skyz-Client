package net.sebbyo.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.sebbyo.combat.combat.CombatManager;
import net.sebbyo.combat.team.TeamManager;

/**
 * The single damage-interception point. Resolves the player ultimately responsible
 * for a hit (melee / projectile owner / potion thrower), blocks friendly fire
 * between teammates, and tags both parties of a genuine PvP hit.
 */
public final class DamageCore {

    private DamageCore() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return true;
            ServerPlayerEntity attacker = resolveAttacker(source);
            if (attacker == null || attacker == victim) return true; // not PvP
            if (TeamManager.INSTANCE.sameTeam(attacker.getUuid(), victim.getUuid())) {
                return false; // friendly fire: cancel; no tag, no reward
            }
            CombatManager.INSTANCE.onPvpHit(victim, attacker);
            return true;
        });
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
