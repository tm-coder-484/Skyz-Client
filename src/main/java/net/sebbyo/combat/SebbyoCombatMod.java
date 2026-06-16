package net.sebbyo.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.sebbyo.combat.combat.CombatManager;
import net.sebbyo.combat.effect.EffectManager;
import net.sebbyo.combat.net.CombatTimerPayload;
import net.sebbyo.combat.team.TeamCommands;
import net.sebbyo.combat.team.TeamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server / common entrypoint. Runs on dedicated servers <b>and</b> integrated
 * servers (a singleplayer world opened to friends via LAN or Essential), enforcing
 * all combat / team / effect rules for every connected player.
 */
public class SebbyoCombatMod implements ModInitializer {

    public static final String MOD_ID = "sebbyo_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Sebbyo Combat] Initialising (server/common).");

        // Payload type — registered on both sides so the codec is known everywhere.
        PayloadTypeRegistry.playS2C().register(CombatTimerPayload.ID, CombatTimerPayload.CODEC);

        // Shared damage interception (friendly fire + PvP tagging).
        DamageCore.register();

        // Combat timer tick-down.
        ServerTickEvents.END_SERVER_TICK.register(server -> CombatManager.INSTANCE.tick(server));

        // Combat log: kill a tagged player who disconnects.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CombatManager.INSTANCE.onDisconnect(handler.player));

        // Re-apply persisted effects on join (covers reconnect + restart).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            EffectManager.INSTANCE.applyToPlayer(handler.player);
            net.sebbyo.combat.team.PartyVisuals.apply(server, handler.player);
        });

        // Ender pearl / wind charge resets the tag (only if already tagged).
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity sp) {
                var stack = player.getStackInHand(hand);
                if (stack.isOf(Items.ENDER_PEARL) || stack.isOf(Items.WIND_CHARGE)) {
                    CombatManager.INSTANCE.resetIfTagged(sp);
                }
            }
            return ActionResult.PASS;
        });

        // Kill reward (covers melee, projectile, and combat-log kills uniformly).
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed, source) -> {
            if (killer instanceof ServerPlayerEntity k && killed instanceof ServerPlayerEntity) {
                EffectManager.INSTANCE.onKill(k);
            }
        });

        // Death: clear combat tag + apply the lose-buff / gain-penalty step.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) {
                CombatManager.INSTANCE.clearTag(p);
                EffectManager.INSTANCE.onDeath(p.getUuid());
            }
        });

        // Respawn: re-apply the updated ledger (vanilla cleared effects on death).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            EffectManager.INSTANCE.applyToPlayer(newPlayer);
            net.sebbyo.combat.team.PartyVisuals.apply(
                    ((net.minecraft.server.world.ServerWorld) newPlayer.getEntityWorld()).getServer(), newPlayer);
        });

        // Persistence load/save tied to the world.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TeamManager.INSTANCE.load(server);
            EffectManager.INSTANCE.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TeamManager.INSTANCE.save();
            EffectManager.INSTANCE.save();
        });

        // /team commands.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TeamCommands.register(dispatcher));
    }
}
