package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.*;

import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;

/**
 * Skyz title screen — owo-lib edition (Phase 2a of the migration).
 *
 * Visual reference: skyz-client.html  (#page-main, .main-btns).
 *
 * Rendering split:
 *   • XML model (assets/skyz_client/owo_ui/title.xml) lays out and styles
 *     the 11-button main column. owo handles button layout, hover, and
 *     MouseButtonEvent dispatch via the wirings in {@link #build}.
 *   • This class still renders, by hand, everything that needs GuiGraphics
 *     control: the gradient / drag-drop image background, particle system,
 *     SKYZ logo with multi-pass glow + pulsing accent dot, splash text,
 *     bottom HUD strip, drag-PNG hint, the circular mute toggle, the
 *     drop-zone overlay, and toast messages.
 *
 * MC's vanilla {@link #renderBackground} is no-op'd so our gradient isn't
 * overwritten by the dirt panorama. {@code BaseOwoScreen.render} already
 * calls {@code uiAdapter.render(...)} after {@code renderBackground}, so we
 * draw bg + logo first, then super.render() draws the owo column on top,
 * then we draw HUD overlays + toast on top of that.
 */
public class SkyzTitleScreen extends BaseUIModelScreen<FlowLayout> {

    final ParticleSystem particles = new ParticleSystem();
    final Toast          toast     = new Toast();

    /** Set by TitleScreenMixin when player disconnects from a server. */
    public boolean pendingOpenMultiplayer = false;

    private float   tick       = 0f;
    private boolean musicMuted = false;

    private static final String[] SPLASHES = {
        "Look up. Fly higher.",
        "Now running on 1.21.11 ✦",
        "Sodium-powered frames ✦",
        "Touch the clouds",
        "Open skies ahead",
        "v" + SkyzClientMod.MOD_VERSION + " — sharper than ever ✦",
        "Iris shaders: activated ✦",
        "Essential by EssentialGG ✦",
        "Drag a PNG/JPG to set your background!",
    };
    private final String splash;

    public SkyzTitleScreen() {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "title"));
        splash = SPLASHES[(int)(Math.random() * SPLASHES.length)];
    }

    // ── owo model wiring ─────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        SkyzClientMod.LOGGER.info("[Skyz] Title built. Root has {} child(ren).",
                root.children().size());

        wire(root, "btn-singleplayer",  () -> client.setScreen(new SkyzSingleplayerScreen(this)));
        wire(root, "btn-multiplayer",   () -> client.setScreen(new SkyzMultiplayerScreen(this)));
        wire(root, "btn-mods",          () -> client.setScreen(new SkyzModsScreen(this)));
        wire(root, "btn-options",       () -> client.setScreen(new OptionsScreen(this, client.options)));
        wire(root, "btn-skyz-settings", () -> client.setScreen(new SkyzSettingsScreen(this)));
        // Cosmetics — opens Essential's wardrobe directly. Falls back to
        // SkyzCosmeticsScreen (install prompt / error page) if Essential is
        // missing or reflection fails.
        wire(root, "btn-cosmetics",     () -> {
            if (!SkyzCosmeticsScreen.tryOpenEssentialWardrobe()) {
                client.setScreen(new SkyzCosmeticsScreen(this));
            }
        });
        wire(root, "btn-hud-editor",    () -> client.setScreen(new SkyzHudEditorScreen(this)));
        wire(root, "btn-shaders",       () -> client.setScreen(new SkyzShadersScreen(this)));
        // Friends — same pattern as Cosmetics: open Essential's social menu
        // directly, fall back to SkyzFriendsScreen on failure.
        wire(root, "btn-friends",       () -> {
            if (!SkyzFriendsScreen.tryOpenEssentialSocial()) {
                client.setScreen(new SkyzFriendsScreen(this));
            }
        });
        wire(root, "btn-quit",          () -> client.scheduleStop(),
                                        SkyzButtonRenderer.QUIT);
    }

    private void wire(FlowLayout root, String id, Runnable action) {
        wire(root, id, action, SkyzButtonRenderer.DEFAULT);
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        try {
            ButtonComponent btn = root.childById(ButtonComponent.class, id);
            if (btn == null) {
                SkyzClientMod.LOGGER.warn("[Skyz] Title: button id '{}' not found.", id);
                return;
            }
            btn.onPress(b -> action.run());
            btn.renderer(renderer);
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] Title: failed to wire '{}': {}", id, e.getMessage());
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void init() {
        super.init();   // BaseUIModelScreen builds the adapter and calls build()
        particles.resize(width, height);
        SkyzBackgroundManager.getInstance().tryLoadSaved();
        if (!musicMuted) SkyzAudioManager.getInstance().play();

        if (pendingOpenMultiplayer) {
            pendingOpenMultiplayer = false;
            client.setScreen(new SkyzMultiplayerScreen(this));
        }
    }

    @Override
    public void removed() {
        SkyzAudioManager.getInstance().stop();
    }

    @Override public boolean shouldPause() { return false; }

    public void toast(String msg) { toast.show(msg); }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // No-op: render() draws our own background before the owo column.
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        tick += delta;

        // 1) Background (drag-image or gradient)
        SkyzBackgroundManager bg = SkyzBackgroundManager.getInstance();
        if (bg.hasBackground()) {
            bg.draw(ctx, width, height);
            bg.drawDim(ctx, width, height);
        } else {
            drawGradientBackground(ctx);
        }

        // 2) Particles
        particles.tick(ctx, delta);

        // 3) SKYZ logo + tagline + dot (drawn under the owo column)
        drawLogo(ctx);

        // 4) owo column (the main-btns)
        super.render(ctx, mouseX, mouseY, delta);

        // 5) HUD overlays (drawn over everything else)
        drawHudClock(ctx);
        drawSplash(ctx);
        drawMuteButton(ctx, mouseX, mouseY);

        if (bg.isDragging()) drawDropZoneOverlay(ctx);
        toast.render(ctx, width, delta);
    }

    // ── Background gradient ──────────────────────────────────────────────
    private void drawGradientBackground(GuiGraphics ctx) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,            width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,   width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3, width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);

        int gcx = width / 2, gcy = height / 3, gr = Math.min(width, height) / 2;
        for (int ring = gr; ring > 0; ring -= 10) {
            int a = (int)(10 * (1f - (float)ring / gr));
            if (a <= 0) continue;
            for (int dy = -ring; dy <= ring; dy += 5) {
                double hw = Math.sqrt(Math.max(0, (double)ring * ring - (double)dy * dy));
                ctx.fill((int)(gcx - hw), gcy + dy, (int)(gcx + hw), gcy + dy + 5,
                        (a << 24) | 0x46A0FF);
            }
        }
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 3/4, width, height / 4,
                0x00051432, 0x8C051432);
    }

    // ── Logo (cinzel-ish multi-pass glow + pulsing dot) ──────────────────
    private void drawLogo(GuiGraphics ctx) {
        int cx     = width / 2;
        int scale  = 4;
        int logoY  = (int)(height * 0.05f);
        String logo = "SKYZ";

        int letterSpacing  = 3;
        int totalUnscaledW = SkyzRenderHelper.textWidthSpaced(font, logo, letterSpacing);
        int totalScaledW   = totalUnscaledW * scale;
        int startUnscaledX = (cx - totalScaledW / 2) / scale;
        int unscaledY      = logoY / scale;

        ctx.pose().pushMatrix();
        ctx.pose().scale(scale, scale);

        int[][] offsets = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1},
            {-2, 0}, {2, 0}, {0, -2}, {0, 2}
        };
        for (int[] off : offsets) {
            SkyzRenderHelper.drawTextSpaced(ctx, font, logo,
                    startUnscaledX + off[0], unscaledY + off[1],
                    letterSpacing, 0x224AB8F0, false);
        }
        SkyzRenderHelper.drawTextSpaced(ctx, font, logo,
                startUnscaledX, unscaledY + 1, letterSpacing, 0x884064A8, false);
        SkyzRenderHelper.drawTextSpaced(ctx, font, logo,
                startUnscaledX, unscaledY, letterSpacing, 0xFFF0F8FF, false);

        ctx.pose().popMatrix();

        int taglineY = logoY + scale * 9 + 6;
        String tagline = "CLIENT   ·   JAVA EDITION   ·   1.21.11";
        SkyzRenderHelper.drawTextSpacedCentered(ctx, font, tagline, cx, taglineY,
                1, 0xCCB4E6FF, true);

        int lineY = taglineY + 11;
        int lineHalf = 130;
        SkyzRenderHelper.fillGradientH(ctx, cx - lineHalf, lineY, lineHalf, 1, 0x00A0D4FF, 0xCCA0D4FF);
        SkyzRenderHelper.fillGradientH(ctx, cx,            lineY, lineHalf, 1, 0xCCA0D4FF, 0x00A0D4FF);

        float pulse    = (float)(Math.sin(tick * 0.05) * 0.3 + 0.7);
        int   dotAlpha = (int)(pulse * 220);
        SkyzRenderHelper.fillCircle(ctx, cx, lineY, 5, ((dotAlpha / 3) << 24) | (SkyzTheme.ACCENT2 & 0x00FFFFFF));
        SkyzRenderHelper.fillCircle(ctx, cx, lineY, 3, (dotAlpha << 24) | (SkyzTheme.ACCENT2 & 0x00FFFFFF));
        SkyzRenderHelper.fillCircle(ctx, cx, lineY, 1, 0xFFFFFFFF);
    }

    // ── HUD overlays ─────────────────────────────────────────────────────
    private void drawHudClock(GuiGraphics ctx) {
        Calendar now = Calendar.getInstance();
        String hud = String.format("Skyz Client  ·  v%s  ·  %02d:%02d",
                SkyzClientMod.MOD_VERSION,
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        ctx.drawCenteredString(font, hud, width / 2, height - 12, 0x4D8CD2FF);
    }

    private void drawSplash(GuiGraphics ctx) {
        ctx.drawString(font, splash,
                width - font.width(splash) - 6, 6, 0x618CC8FF);
    }

    private void drawDropZoneOverlay(GuiGraphics ctx) {
        ctx.fill(0, 0, width, height, 0xCC050F2A);
        int p = 18, col = 0xCC8CD2FF;
        SkyzRenderHelper.drawRoundedBorder(ctx, p,     p,     width - p * 2,     height - p * 2,     14, col);
        SkyzRenderHelper.drawRoundedBorder(ctx, p + 1, p + 1, width - p * 2 - 2, height - p * 2 - 2, 13, col);
        ctx.drawCenteredString(font, "Drop your image here",          width / 2, height / 2 - 12, 0xFFFFFFFF);
        ctx.drawCenteredString(font, "PNG, JPG or BMP — any size", width / 2, height / 2 + 4,  0x888CD2FF);
    }

    // ── Mute button (drawn manually because BaseUIModelScreen does not
    //    render Screen.addRenderableWidget widgets) ─────────────────────────
    private static final int MUTE_R     = 11;
    private int muteCx() { return width  - MUTE_R - 10; }
    private int muteCy() { return height - MUTE_R - 18; }

    private boolean mouseInMute(double mx, double my) {
        double dx = mx - muteCx();
        double dy = my - muteCy();
        return dx * dx + dy * dy <= MUTE_R * MUTE_R;
    }

    private void drawMuteButton(GuiGraphics ctx, int mx, int my) {
        int cx = muteCx(), cy = muteCy();
        boolean hover = mouseInMute(mx, my);

        int bgAlpha    = hover ? 0xCC : 0x88;
        int ringAlpha  = hover ? 0xCC : 0x66;

        SkyzRenderHelper.fillCircle(ctx, cx, cy, MUTE_R,     (bgAlpha   << 24) | 0x10406A);
        SkyzRenderHelper.fillCircle(ctx, cx, cy, MUTE_R - 1, (ringAlpha << 24) | (SkyzTheme.ACCENT2 & 0x00FFFFFF));
        SkyzRenderHelper.fillCircle(ctx, cx, cy, MUTE_R - 2, ((bgAlpha + 0x10) << 24) | 0x102A4A);

        String icon = musicMuted ? "🔇" : "🔊"; // 🔇 / 🔊
        int w = font.width(icon);
        ctx.drawString(font, icon, cx - w / 2, cy - 4, hover ? 0xFFFFFFFF : 0xCCCCEFFF, false);
    }

    // ── Input ────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent MouseButtonEvent, boolean doubled) {
        double mx = MouseButtonEvent.x(), my = MouseButtonEvent.y();

        // Mute toggle (drawn by us, hit-tested manually).
        if (mouseInMute(mx, my)) {
            musicMuted = !SkyzAudioManager.getInstance().toggle();
            toast(musicMuted ? "Music muted" : "Music playing");
            return true;
        }

        // owo (button column) gets next swing.
        return super.mouseClicked(MouseButtonEvent, doubled);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        SkyzBackgroundManager bg = SkyzBackgroundManager.getInstance();
        bg.setDragging(false);
        if (!bg.onFileDrop(paths)) toast("Not an image. Use PNG, JPG or BMP.");
    }
}
