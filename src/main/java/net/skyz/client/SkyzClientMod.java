package net.skyz.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import net.skyz.client.util.AutoReconnectManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class SkyzClientMod implements ClientModInitializer {

    public static final String MOD_ID = "skyz_client";
    public static final String MOD_NAME = "Skyz Client";
    /**
     * Version string. Single source of truth for what the screens display
     * — title screen splash, pause menu subtitle, settings client-info row,
     * HUD clock strip all reference {@code SkyzClientMod.MOD_VERSION}, so
     * a version bump here propagates everywhere without grepping for
     * hardcoded version literals.
     */
    public static final String MOD_VERSION = "4.3.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Set true before ConnectScreen.connect() so TitleScreenMixin re-opens multiplayer after disconnect. */
    public static boolean returnToMultiplayer = false;

    // CPS tracking via tick polling - no mixin needed
    private static boolean wasLeftDown  = false;
    private static boolean wasRightDown = false;
    private static boolean wasNearDead  = false; // for totem detection

    /**
     * Dev keybind: opens the owo-lib kitchen-sink screen for live design-system
     * tweaks. With owo's hot-reload, edit any _templates/*.xml then re-press
     * this keybind to see the change without rebuilding the mod.
     */
    public static KeyBinding KITCHEN_SINK_KEY;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Skyz Client] Initialising v{} for Minecraft 1.21.11", MOD_VERSION);

        // Load saved HUD layout and settings
        net.skyz.client.util.SkyzHudState.initDefaults(854, 480);
        net.skyz.client.util.SkyzConfig.load();

        // Kick off the background ESP scan daemon. Idempotent — internal
        // guard means double-calls are harmless.
        net.skyz.client.util.SkyzClientState.startEspScanThread();

        // Register the kitchen-sink dev keybind (default: K). KeyBinding
        // categories are first-class records in 1.21.11; MISC lumps Skyz
        // dev keys with other miscellaneous bindings in the controls UI.
        KITCHEN_SINK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skyz_client.kitchen_sink",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.MISC));

        // Store server info on join so AutoReconnectManager can reconnect after kicks.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo info = handler.getServerInfo();
            if (info != null) AutoReconnectManager.INSTANCE.onJoin(info);
        });

        // CPS counter: poll mouse buttons each client tick (20 times/sec)
        // This is less precise than per-frame but avoids needing a Mouse mixin.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            AutoReconnectManager.INSTANCE.tick(client);
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

            // Totem pop: detect via offhand totem disappearing when health is critically low.
            // isUsingItem() with a totem is the clearest signal.
            float hp = client.player.getHealth();
            boolean totemInOffhand = client.player
                    .getEquippedStack(net.minecraft.entity.EquipmentSlot.OFFHAND)
                    .isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING);
            if (!wasNearDead && hp > 0 && hp <= 1.0f && totemInOffhand) {
                wasNearDead = true;
            } else if (wasNearDead && (hp >= 2.0f || !totemInOffhand)) {
                // Health recovered, or totem was consumed from offhand = pop
                net.skyz.client.util.SkyzClientState.registerTotemPop();
                wasNearDead = false;
            } else if (!totemInOffhand && hp > 4.0f) {
                wasNearDead = false;
            }

                        net.skyz.client.util.SkyzClientState.tick(client);

            // Dev kitchen-sink keybind. Only opens when no other screen is
            // active so it doesn't interrupt mid-screen editing.
            while (KITCHEN_SINK_KEY != null && KITCHEN_SINK_KEY.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new net.skyz.client.screen.SkyzKitchenSinkScreen(null));
                }
            }
        });

        // Auto-GG: scan incoming game messages for win/loss keywords
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            try {
                net.skyz.client.util.SkyzClientState.onChatMessage(
                        MinecraftClient.getInstance(), message.getString());
            } catch (Exception ignored) {}
        });
        ClientReceiveMessageEvents.CHAT.register((message, sig, sender, params, ts) -> {
            try {
                net.skyz.client.util.SkyzClientState.onChatMessage(
                        MinecraftClient.getInstance(), message.getString());
            } catch (Exception ignored) {}
        });
    }
}
