package net.sebbyo.combat.combat;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.sebbyo.combat.net.Networking;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Owns combat tags: timer tick-down, tagging, sync, and combat-log death. */
public final class CombatManager {

    public static final CombatManager INSTANCE = new CombatManager();

    private CombatManager() {}

    public static final int COMBAT_TICKS = 400; // 20 seconds

    private final Map<UUID, CombatState> states = new HashMap<>();

    public boolean isInCombat(UUID id) {
        return states.containsKey(id);
    }

    /** A genuine PvP hit: tag both parties; record the victim's last attacker. */
    public void onPvpHit(ServerPlayerEntity victim, ServerPlayerEntity attacker) {
        tag(victim, attacker.getUuid());
        tag(attacker, null);
        sync(victim);
        sync(attacker);
    }

    public void tag(ServerPlayerEntity p, UUID attackerUuid) {
        if (p.isCreative() || p.isSpectator()) return;
        CombatState st = states.computeIfAbsent(p.getUuid(), k -> new CombatState());
        st.remainingTicks = COMBAT_TICKS;
        if (attackerUuid != null) st.lastAttacker = attackerUuid;
    }

    /** Ender-pearl / wind-charge: reset to 20s only if already in combat. */
    public void resetIfTagged(ServerPlayerEntity p) {
        CombatState st = states.get(p.getUuid());
        if (st != null) {
            st.remainingTicks = COMBAT_TICKS;
            sync(p);
        }
    }

    public void clearTag(UUID id) {
        states.remove(id);
    }

    /** Clear the tag and immediately tell the client to hide the timer (used on death). */
    public void clearTag(ServerPlayerEntity p) {
        states.remove(p.getUuid());
        Networking.sendTimer(p, 0);
    }

    public void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, CombatState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CombatState> e = it.next();
            CombatState st = e.getValue();
            st.remainingTicks--;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (st.remainingTicks <= 0) {
                it.remove();
                if (p != null) Networking.sendTimer(p, 0);
            } else if (p != null && st.remainingTicks % 20 == 0) {
                Networking.sendTimer(p, seconds(st));
            }
        }
    }

    private void sync(ServerPlayerEntity p) {
        CombatState st = states.get(p.getUuid());
        Networking.sendTimer(p, st == null ? 0 : seconds(st));
    }

    private static int seconds(CombatState st) {
        return (st.remainingTicks + 19) / 20;
    }

    /** Combat log: kill the disconnecting player, crediting their last attacker. */
    public void onDisconnect(ServerPlayerEntity p) {
        CombatState st = states.remove(p.getUuid());
        if (st == null) return; // not tagged -> normal logout
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        ServerPlayerEntity killer = (st.lastAttacker == null)
                ? null : world.getServer().getPlayerManager().getPlayer(st.lastAttacker);
        DamageSource src = killer != null
                ? world.getDamageSources().playerAttack(killer)
                : world.getDamageSources().generic();
        p.damage(world, src, Float.MAX_VALUE); // lethal -> drops items + XP at logout spot
    }
}
