package net.skyz.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
// TODO[PORT-26.1]: re-add `import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;`
// and the SkyzEspRenderer import once the Fabric rendering module ships for 26.1.
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.item.Items;
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
    public static final String MOD_VERSION = "5.0.0";
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
    public static KeyMapping KITCHEN_SINK_KEY;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Skyz Client] Initialising v{} for Minecraft 26.1.2", MOD_VERSION);

        // Load saved HUD layout and settings
        net.skyz.client.util.SkyzHudState.initDefaults(854, 480);
        net.skyz.client.util.SkyzConfig.load();

        // Kick off the background ESP scan daemon. Idempotent — internal
        // guard means double-calls are harmless.
        net.skyz.client.util.SkyzClientState.startEspScanThread();

        // TODO[PORT-26.1]: ESP world-rendering is deferred. The Fabric
        // rendering module (fabric-rendering-v1 → WorldRenderEvents) is NOT
        // shipped in Fabric API 0.145.4+26.1.2 yet (per Fabric's 26.1 blog:
        // "Renderer/Indigo modules may not be available for the initial
        // release"). Re-enable SkyzEspRenderer registration once that module
        // lands, or switch to a LevelRenderer mixin. The ESP scan thread
        // (above) still runs and maintains blockEspMatches; only the
        // box-drawing is dormant.

        // Register the kitchen-sink dev keybind (default: K). KeyBinding
        // categories are first-class records in 1.21.11; MISC lumps Skyz
        // dev keys with other miscellaneous bindings in the controls UI.
        // TODO[PORT-26.1]: Fabric keybinding module (keybinding.v1.KeyBindingHelper) is not
        // shipped in Fabric API 0.145.4+26.1.2 yet, and KeyMapping.CATEGORY_MISC was removed
        // (categories are now KeyMapping.Category objects). The kitchen-sink dev key is left
        // unregistered until the module lands; the consumeClick loop below guards on null.
        KITCHEN_SINK_KEY = null;

        // Store server info on join so AutoReconnectManager can reconnect after kicks.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerData info = handler.getServerData();
            if (info != null) AutoReconnectManager.INSTANCE.onJoin(info);
        });

        // CPS counter: poll mouse buttons each client tick (20 times/sec)
        // This is less precise than per-frame but avoids needing a Mouse mixin.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            AutoReconnectManager.INSTANCE.tick(client);
            if (client.player == null) return;

            boolean leftDown  = client.options.keyAttack.isDown();
            boolean rightDown = client.options.keyUse.isDown();

            // CPS tracking
            if (leftDown  && !wasLeftDown)  net.skyz.client.util.SkyzClientState.recordClick();
            if (rightDown && !wasRightDown) net.skyz.client.util.SkyzClientState.recordClick();

            // Combo counter — left MouseButtonEvent press on a mob counts as a hit
            if (leftDown && !wasLeftDown && client.crosshairPickEntity != null) {
                net.skyz.client.util.SkyzClientState.registerHit();
            }

            wasLeftDown  = leftDown;
            wasRightDown = rightDown;

            // Totem pop: detect via offhand totem disappearing when health is critically low.
            // isUsingItem() with a totem is the clearest signal.
            float hp = client.player.getHealth();
            boolean totemInOffhand = client.player
                    .getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                    .is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
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
            while (KITCHEN_SINK_KEY != null && KITCHEN_SINK_KEY.consumeClick()) {
                // TODO(port): verify Mojang field name — likely `client.screen` (field) per
                // Minecraft.java in 26.1; was `currentScreen` in yarn.
                if (client.screen == null) {
                    client.setScreen(new net.skyz.client.screen.SkyzKitchenSinkScreen(null));
                }
            }
        });

        // Auto-GG: scan incoming game messages for win/loss keywords
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            try {
                net.skyz.client.util.SkyzClientState.onChatMessage(
                        Minecraft.getInstance(), message.getString());
            } catch (Exception ignored) {}
        });
        ClientReceiveMessageEvents.CHAT.register((message, sig, sender, params, ts) -> {
            try {
                net.skyz.client.util.SkyzClientState.onChatMessage(
                        Minecraft.getInstance(), message.getString());
            } catch (Exception ignored) {}
        });
    }
}
