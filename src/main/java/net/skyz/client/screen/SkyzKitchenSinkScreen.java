package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;

/**
 * Phase 1 validation screen for the Skyz design system. Currently a
 * minimum-viable rig: just enough XML to confirm owo loads the model
 * and renders something. We add templates back once the core path is
 * proven working.
 *
 * Hot-reload: in dev-mode owo re-parses the XML on every screen open,
 * so iterating on kitchen_sink.xml means save → close-and-reopen the
 * screen → see change. No gradlew build, no MC restart.
 */
public class SkyzKitchenSinkScreen extends BaseUIModelScreen<FlowLayout> {
    /** Alias for the inherited Minecraft instance (26.1 renamed the Screen field client->minecraft). */
    private final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();


    private final Screen parent;

    public SkyzKitchenSinkScreen(Screen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "kitchen_sink"));
        this.parent = parent;
    }

    @Override
    protected void build(FlowLayout root) {
        // Diagnostic log: confirm model populated the root from the XML.
        SkyzClientMod.LOGGER.info("[Skyz] Kitchen sink built. Root has {} child(ren).",
                root.children().size());

        // Wire each button's id to a console log so we can verify lookup.
        wireLog(root, "ks-singleplayer", "Singleplayer");
        wireLog(root, "ks-multiplayer", "Multiplayer");
        wireLog(root, "ks-mods",        "Mods");
        wireLog(root, "ks-settings",    "Settings");
        wireLog(root, "ks-quit",        "Quit");
    }

    private void wireLog(FlowLayout root, String id, String label) {
        try {
            ButtonComponent btn = root.childById(ButtonComponent.class, id);
            if (btn == null) {
                SkyzClientMod.LOGGER.warn("[Skyz] Kitchen sink: button id '{}' not found.", id);
                return;
            }
            btn.onPress(b -> SkyzClientMod.LOGGER.info("[Skyz] {} clicked.", label));
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] Kitchen sink: failed to wire '{}': {}", id, e.getMessage());
        }
    }

    @Override
    public void onClose() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
