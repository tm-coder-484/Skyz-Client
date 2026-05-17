package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

/**
 * Skyz Pause Menu — owo-lib edition (Phase 2d port).
 *
 * <p>Replaces vanilla {@code GameMenuScreen} via
 * {@link net.skyz.client.mixin.GameMenuScreenMixin}. Vanilla constructs
 * {@code GameMenuScreen(true)} for the actual paused-with-UI case and
 * {@code GameMenuScreen(false)} during world load to pause the game
 * without showing a menu — the mixin only redirects the {@code true}
 * case so we never accidentally show a menu mid-loading.
 *
 * <p>Functionality is preserved 1:1 with vanilla:
 * <ul>
 *   <li>Resume → close screen, lock cursor.</li>
 *   <li>Achievements → {@link AdvancementsScreen}.</li>
 *   <li>Statistics → {@link StatsScreen}.</li>
 *   <li>Send Feedback / Report Bugs → opens {@code aka.ms/snapshot*} URLs
 *       via {@code Util.getOperatingSystem().open(...)}.</li>
 *   <li>Options → vanilla {@link OptionsScreen}.</li>
 *   <li>Open to LAN → {@link OpenToLanScreen} (only enabled when an
 *       integrated server is running, i.e. singleplayer).</li>
 *   <li>Save and Quit / Disconnect — vanilla's three-branch logic:
 *       integrated → TitleScreen, multiplayer → MultiplayerScreen,
 *       Realms → RealmsMainScreen (we route Realms back to TitleScreen
 *       since we don't have a Realms re-skin and don't want to depend
 *       on the Realms class lookup).</li>
 * </ul>
 */
public class SkyzPauseMenuScreen extends BaseUIModelScreen<FlowLayout> {

    /** Vanilla GameMenuScreen's `showMenu` field — we pass it through verbatim. */
    public final boolean showMenu;

    public SkyzPauseMenuScreen(boolean showMenu) {
        super(FlowLayout.class, Identifier.of("skyz_client", "pause_menu"));
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
            String mcVersion = SharedConstants.getGameVersion().name();
            subtitle.text(Text.literal(
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
            boolean canLan = MinecraftClient.getInstance().isIntegratedServerRunning()
                    && !MinecraftClient.getInstance().getServer().isRemote();
            lanBtn.active(canLan);
        }

        // Wide quit button — label varies with connection mode.
        ButtonComponent quitBtn = root.childById(ButtonComponent.class, "btn-quit");
        if (quitBtn != null) {
            quitBtn.renderer(SkyzButtonRenderer.QUIT);
            quitBtn.onPress(b -> quitToTitle());
            boolean integrated = MinecraftClient.getInstance().isIntegratedServerRunning();
            quitBtn.setMessage(Text.literal(integrated
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
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(null);
        mc.mouse.lockCursor();
    }

    private void openAdvancements() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && player.networkHandler != null) {
            MinecraftClient.getInstance().setScreen(
                    new AdvancementsScreen(player.networkHandler.getAdvancementHandler(), this));
        }
    }

    private void openStatistics() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            MinecraftClient.getInstance().setScreen(new StatsScreen(this, player.getStatHandler()));
        }
    }

    private void openFeedbackUrl() {
        // Same target vanilla uses for "Send Feedback".
        Util.getOperatingSystem().open("https://aka.ms/snapshotfeedback?ref=game");
    }

    private void openBugsUrl() {
        Util.getOperatingSystem().open("https://aka.ms/snapshotbugs?ref=game");
    }

    private void openOptions() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new OptionsScreen(this, mc.options));
    }

    private void openLan() {
        MinecraftClient.getInstance().setScreen(new OpenToLanScreen(this));
    }

    /**
     * Vanilla's pause-menu quit logic: tear down the world (integrated +
     * save, or just disconnect for multiplayer), then return to Title or
     * Multiplayer screen depending on connection mode.
     *
     * <p>Realms case routes to TitleScreen — we don't have a Realms
     * re-skin and {@code MinecraftClient} in 1.21.11 doesn't expose a
     * direct "is connected to Realms" check anyway. The user can
     * navigate to Realms from the title if they want.
     */
    private void quitToTitle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean integrated = mc.isIntegratedServerRunning();

        if (mc.world != null) {
            mc.world.disconnect(Text.translatable("disconnect.lost"));
        }

        if (integrated) {
            // Save world + show progress screen while saving.
            mc.disconnectWithProgressScreen();
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
            mc.setScreen(new MultiplayerScreen(titleScreen));
        }
    }

    // ─── Lifecycle / render ──────────────────────────────────────────────
    /**
     * Vanilla's GameMenuScreen.shouldPause() returns true (the game
     * pauses while the menu is open in singleplayer). Match it so
     * gameplay actually pauses while we're on screen.
     */
    @Override public boolean shouldPause() { return true; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Vanilla blurs/dims the world behind the menu; we do a soft
        // dim + Skyz tint over whatever is behind.
        ctx.fillGradient(0, 0, width, height, 0xCC050F2A, 0xCC0A1F50);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!showMenu) return;
        // Soft Skyz-tinted veil over whatever was behind (paused world).
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,
                width, height, SkyzTheme.BG1 & 0x00FFFFFF | 0xC0000000,
                SkyzTheme.BG3 & 0x00FFFFFF | 0xC0000000);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
