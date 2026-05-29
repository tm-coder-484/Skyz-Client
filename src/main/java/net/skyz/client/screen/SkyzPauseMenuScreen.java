package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

/**
 * Skyz Pause Menu — owo-lib edition (Phase 2d port).
 *
 * <p>Replaces vanilla {@code PauseScreen} via
 * {@link net.skyz.client.mixin.GameMenuScreenMixin}. Vanilla constructs
 * {@code PauseScreen(true)} for the actual paused-with-UI case and
 * {@code PauseScreen(false)} during world load to pause the game
 * without showing a menu — the mixin only redirects the {@code true}
 * case so we never accidentally show a menu mid-loading.
 *
 * <p>Functionality is preserved 1:1 with vanilla:
 * <ul>
 *   <li>Resume → close screen, lock cursor.</li>
 *   <li>Achievements → {@link AdvancementsScreen}.</li>
 *   <li>Statistics → {@link StatsScreen}.</li>
 *   <li>Send Feedback / Report Bugs → opens {@code aka.ms/snapshot*} URLs
 *       via {@code Util.getPlatform().openUri(...)}.</li>
 *   <li>Options → vanilla {@link OptionsScreen}.</li>
 *   <li>Open to LAN → {@link ShareToLanScreen} (only enabled when an
 *       integrated server is running, i.e. singleplayer).</li>
 *   <li>Save and Quit / Disconnect — vanilla's three-branch logic:
 *       integrated → TitleScreen, multiplayer → JoinMultiplayerScreen,
 *       Realms → RealmsMainScreen (we route Realms back to TitleScreen
 *       since we don't have a Realms re-skin and don't want to depend
 *       on the Realms class lookup).</li>
 * </ul>
 */
public class SkyzPauseMenuScreen extends BaseUIModelScreen<FlowLayout> {
    /** Alias for the inherited Minecraft instance (26.1 renamed the Screen field client->minecraft). */
    private final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();


    /** Vanilla PauseScreen's `showPauseMenu` field — we pass it through verbatim. */
    public final boolean showMenu;

    public SkyzPauseMenuScreen(boolean showMenu) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "pause_menu"));
        this.showMenu = showMenu;
    }

    // ─── Build ───────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        if (!showMenu) {
            // Match vanilla behaviour — no UI when the screen was opened
            // for the "pause game without UI" case (used during world load).
            return;
        }

        FlowLayout card = root.childById(FlowLayout.class, "card");
        if (card != null) card.surface(SkyzSurface.CARD);

        // Subtitle: a tiny version line, like the title screen.
        LabelComponent subtitle = root.childById(LabelComponent.class, "lbl-subtitle");
        if (subtitle != null) {
            // GameVersion.name() in 1.21.x — was getName() in earlier MC.
            String mcVersion = SharedConstants.getCurrentVersion().name();
            subtitle.text(Component.literal(
                    "Skyz Client v" + SkyzClientMod.MOD_VERSION + "  ·  Minecraft " + mcVersion));
        }

        wire(root, "btn-resume",       this::resumeGame,             SkyzButtonRenderer.DEFAULT);
        wire(root, "btn-achievements", this::openAdvancements,       SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-statistics",   this::openStatistics,         SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-feedback",     this::openFeedbackUrl,        SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-bugs",         this::openBugsUrl,            SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-options",      this::openOptions,            SkyzButtonRenderer.NAV_BACK);

        // Open to LAN — only enabled in singleplayer.
        ButtonComponent lanBtn = root.childById(ButtonComponent.class, "btn-lan");
        if (lanBtn != null) {
            lanBtn.renderer(SkyzButtonRenderer.NAV_BACK);
            lanBtn.onPress(b -> openLan());
            boolean canLan = Minecraft.getInstance().hasSingleplayerServer()
                    && !Minecraft.getInstance().getSingleplayerServer().isPublished();
            lanBtn.active(canLan);
        }

        // Wide quit button — label varies with connection mode.
        ButtonComponent quitBtn = root.childById(ButtonComponent.class, "btn-quit");
        if (quitBtn != null) {
            quitBtn.renderer(SkyzButtonRenderer.QUIT);
            quitBtn.onPress(b -> quitToTitle());
            boolean integrated = Minecraft.getInstance().hasSingleplayerServer();
            quitBtn.setMessage(Component.literal(integrated
                    ? "⏻  SAVE AND QUIT TO TITLE"
                    : "⏻  DISCONNECT"));
        }
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] PauseMenu: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    // ─── Actions ─────────────────────────────────────────────────────────
    private void resumeGame() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        mc.mouseHandler.grabMouse();
    }

    private void openAdvancements() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            Minecraft.getInstance().setScreen(
                    new AdvancementsScreen(player.connection.getAdvancements(), this));
        }
    }

    private void openStatistics() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Minecraft.getInstance().setScreen(new StatsScreen(this, player.getStats()));
        }
    }

    private void openFeedbackUrl() {
        // Same target vanilla uses for "Send Feedback".
        Util.getPlatform().openUri("https://aka.ms/snapshotfeedback?ref=game");
    }

    private void openBugsUrl() {
        Util.getPlatform().openUri("https://aka.ms/snapshotbugs?ref=game");
    }

    private void openOptions() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new OptionsScreen(this, mc.options, false));
    }

    private void openLan() {
        Minecraft.getInstance().setScreen(new ShareToLanScreen(this));
    }

    /**
     * Vanilla's pause-menu quit logic: tear down the world (integrated +
     * save, or just disconnect for multiplayer), then return to Title or
     * Multiplayer screen depending on connection mode.
     *
     * <p>Realms case routes to TitleScreen — we don't have a Realms
     * re-skin and {@code Minecraft} in 26.1 doesn't expose a
     * direct "is connected to Realms" check anyway. The user can
     * navigate to Realms from the title if they want.
     */
    private void quitToTitle() {
        Minecraft mc = Minecraft.getInstance();
        boolean integrated = mc.hasSingleplayerServer();

        if (mc.level != null) {
            mc.level.disconnect(Component.translatable("disconnect.lost"));
        }

        if (integrated) {
            // Save world + show progress screen while saving.
            mc.disconnectWithSavingScreen();
        } else {
            // Multiplayer: vanilla calls disconnect(Screen, boolean, boolean)
            // — we use the convenience overload that just shows a generic
            // "disconnected" screen.
            mc.disconnect(new TitleScreen(), false, false);
        }

        TitleScreen titleScreen = new TitleScreen();
        if (integrated) {
            mc.setScreen(titleScreen);
        } else {
            mc.setScreen(new JoinMultiplayerScreen(titleScreen));
        }
    }

    // ─── Lifecycle / render ──────────────────────────────────────────────
    /**
     * Vanilla's PauseScreen.isPauseScreen() returns true (the game
     * pauses while the menu is open in singleplayer). Match it so
     * gameplay actually pauses while we're on screen.
     */
    @Override public boolean isPauseScreen() { return true; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Vanilla blurs/dims the world behind the menu; we do a soft
        // dim + Skyz tint over whatever is behind.
        ctx.fillGradient(0, 0, width, height, 0xCC050F2A, 0xCC0A1F50);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (!showMenu) return;
        // Soft Skyz-tinted veil over whatever was behind (paused world).
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,
                width, height, SkyzTheme.BG1 & 0x00FFFFFF | 0xC0000000,
                SkyzTheme.BG3 & 0x00FFFFFF | 0xC0000000);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }
}
