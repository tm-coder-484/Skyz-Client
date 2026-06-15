package net.sebbyo.combat.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.sebbyo.combat.SebbyoCombatMod;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

/** Applies the persisted reward/penalty ledger to players as infinite status effects. */
public final class EffectManager {

    public static final EffectManager INSTANCE = new EffectManager();

    private EffectManager() {}

    private static final List<String> ALL_IDS =
            Stream.concat(EffectCatalog.REWARDS.stream(), EffectCatalog.PENALTIES.stream()).toList();

    private final Map<UUID, EffectLedger> ledgers = new HashMap<>();
    private final Random rng = new Random();
    private Path file;

    public EffectLedger ledger(UUID id) {
        return ledgers.computeIfAbsent(id, k -> new EffectLedger());
    }

    public void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("sebbyo_rewards.json");
        try {
            EffectStore.load(file, ledgers);
        } catch (IOException e) {
            SebbyoCombatMod.LOGGER.warn("[Sebbyo] Failed to load rewards: {}", e.toString());
        }
    }

    public void save() {
        if (file == null) return;
        try {
            EffectStore.save(file, ledgers);
        } catch (IOException e) {
            SebbyoCombatMod.LOGGER.warn("[Sebbyo] Failed to save rewards: {}", e.toString());
        }
    }

    /** A PvP kill: grant a random reward + peel one penalty level off the killer. */
    public void onKill(ServerPlayerEntity killer) {
        ledger(killer.getUuid()).onKill(rng);
        applyToPlayer(killer);
        save();
    }

    /** Any death: lose one reward level, or (if buff-less) gain one penalty level. */
    public void onDeath(UUID playerId) {
        ledger(playerId).onDeath(rng);
        save();
    }

    /** Re-applies the ledger to a player (call on join + after respawn). */
    public void applyToPlayer(ServerPlayerEntity p) {
        for (String id : ALL_IDS) {
            RegistryEntry<StatusEffect> entry = entry(id);
            if (entry != null) p.removeStatusEffect(entry);
        }
        EffectLedger l = ledger(p.getUuid());
        for (Map.Entry<String, Integer> e : l.view().entrySet()) {
            RegistryEntry<StatusEffect> entry = entry(e.getKey());
            if (entry != null) {
                p.addStatusEffect(new StatusEffectInstance(
                        entry, StatusEffectInstance.INFINITE, e.getValue(), false, false, true));
            }
        }
    }

    private static RegistryEntry<StatusEffect> entry(String id) {
        StatusEffect effect = Registries.STATUS_EFFECT.get(Identifier.of(id));
        return effect == null ? null : Registries.STATUS_EFFECT.getEntry(effect);
    }
}
