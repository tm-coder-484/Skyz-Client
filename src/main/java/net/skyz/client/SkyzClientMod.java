package net.skyz.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class SkyzClientMod implements ClientModInitializer {

    public static final String MOD_ID = "skyz_client";
    public static final String MOD_NAME = "Skyz Client";
    public static final String MOD_VERSION = "2.5.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Set true before ConnectScreen.connect() so TitleScreenMixin re-opens multiplayer after disconnect. */
    public static boolean returnToMultiplayer = false;

    // CPS tracking via tick polling - no mixin needed
    private static boolean wasLeftDown  = false;
    private static boolean wasRightDown = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Skyz Client] Initialising v{} for Minecraft 1.21.11", MOD_VERSION);

        // Load saved HUD layout and settings
        net.skyz.client.util.SkyzHudState.initDefaults(854, 480);
        net.skyz.client.util.SkyzConfig.load();

        // CPS counter: poll mouse buttons each client tick (20 times/sec)
        // This is less precise than per-frame but avoids needing a Mouse mixin.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean leftDown  = client.options.attackKey.isPressed();
            boolean rightDown = client.options.useKey.isPressed();

            // CPS tracking
            if (leftDown  && !wasLeftDown)  net.skyz.client.util.SkyzClientState.recordClick();
            if (rightDown && !wasRightDown) net.skyz.client.util.SkyzClientState.recordClick();

            // Combo counter \u2014 left click press on a mob counts as a hit
            if (leftDown && !wasLeftDown && client.targetedEntity != null) {
                net.skyz.client.util.SkyzClientState.registerHit();
            }

            wasLeftDown  = leftDown;
            wasRightDown = rightDown;

            // Totem pop: detect health dropping to near-zero while totem is in offhand
            float hp = client.player.getHealth();
            if (hp <= 0.5f && hp > 0) {
                net.minecraft.item.ItemStack offhand = client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.OFFHAND);
                if (offhand.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
                    net.skyz.client.util.SkyzClientState.registerTotemPop();
                }
            }

            // Totem pop detection \u2014 watch for the totem use animation
            if (client.player.isUsingItem()) {
                net.minecraft.item.ItemStack active = client.player.getActiveItem();
                if (active.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
                    // Will be refined later; basic detection
                }
            }

            net.skyz.client.util.SkyzClientState.tick(client);
        });
    }
}
