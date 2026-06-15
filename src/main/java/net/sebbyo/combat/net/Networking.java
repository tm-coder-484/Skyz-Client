package net.sebbyo.combat.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Sends the combat timer to a player: custom payload if they're modded, else action bar. */
public final class Networking {

    private Networking() {}

    public static void sendTimer(ServerPlayerEntity player, int seconds) {
        if (ServerPlayNetworking.canSend(player, CombatTimerPayload.ID)) {
            ServerPlayNetworking.send(player, new CombatTimerPayload(seconds));
        } else if (seconds > 0) {
            player.sendMessage(Text.literal("⚔ In combat: " + seconds + "s"), true);
        }
    }
}
