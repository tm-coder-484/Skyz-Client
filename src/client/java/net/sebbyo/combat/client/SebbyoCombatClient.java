package net.sebbyo.combat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.sebbyo.combat.SebbyoCombatMod;
import net.sebbyo.combat.net.CombatTimerPayload;

/** Client entrypoint: receives the synced combat time and renders the HUD timer. */
public class SebbyoCombatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SebbyoCombatMod.LOGGER.info("[Sebbyo Combat] Initialising (client).");

        ClientPlayNetworking.registerGlobalReceiver(CombatTimerPayload.ID, (payload, context) -> {
            int secs = payload.seconds();
            context.client().execute(() -> CombatHudRenderer.setSeconds(secs));
        });

        CombatHudRenderer.register();
    }
}
