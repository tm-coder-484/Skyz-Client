package net.skyz.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import net.skyz.client.util.*;
import net.skyz.client.util.SkyzTheme;

import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;

/**
 * Main Skyz Client title screen - v2.5.0 for Minecraft 1.21.11.
 *
 * Fixed in this version:
 *  - Button layout: calculated from screen centre so nothing clips
 *  - All navigation targets now have real custom screens
 *  - Music via MC SoundManager (MUSIC_MENU)
 *  - Drag-and-drop background via SkyzBackgroundManager
 *  - Mod integrations detected at runtime
 */
public class SkyzTitleScreen extends Screen {

    // Shared utilities (package-visible for sub-screens)
    final ParticleSystem particles = new ParticleSystem();
    final Toast          toast     = new Toast();
    /** Set by TitleScreenMixin when player disconnects from a server. */
    public boolean pendingOpenMultiplayer = false;
    private float        tick      = 0f;
    private boolean      musicMuted = false;

    private static final String[] SPLASHES = {
        "Look up. Fly higher.",
        "Now running on 1.21.11 \u2726",
        "Sodium-powered frames \u2726",
        "Touch the clouds",
        "Open skies ahead",
        "v2.5.0 \u2014 sharper than ever \u2726",
        "Iris shaders: activated \u2726",
        "Essential by EssentialGG \u2726",
        "Drag a PNG/JPG to set your background!",
    };
    private final String splash;

    // Button geometry - calculated dynamically in init()
    private static final int BTN_W   = 200;
    private static final int BTN_H   = 20;
    private static final int BTN_GAP = 3;

    public SkyzTitleScreen() {
        super(Text.literal("Skyz Client"));
        splash = SPLASHES[(int)(Math.random() * SPLASHES.length)];
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Init
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    @Override
    protected void init() {
        particles.resize(width, height);
        SkyzBackgroundManager.getInstance().tryLoadSaved();
        if (!musicMuted) SkyzAudioManager.getInstance().play();

        int cx = width / 2;

        // 8 rows (some are half pairs, all same height) + 1 extra spacer before quit
        int numRows = 8;
        int totalH  = numRows * BTN_H + (numRows - 1) * BTN_GAP + 4; // +4 spacer before quit

        // Logo bottom: never push buttons off screen
        int logoBottom = Math.min((int)(height * 0.40f), height - totalH - 16);
        int startY     = logoBottom + 8; // tight gap after logo
        int y          = startY;
        int h          = BTN_W / 2 - BTN_GAP / 2;

        row(cx, y, BTN_W, "\uD83C\uDF0D  Singleplayer",
                () -> client.setScreen(new SkyzSingleplayerScreen(this)));
        y += BTN_H + BTN_GAP;

        row(cx, y, BTN_W, "\u25C8  Multiplayer",
                () -> client.setScreen(new SkyzMultiplayerScreen(this)));
        y += BTN_H + BTN_GAP;

        row(cx, y, BTN_W, "\uD83E\uDDE9  Mods",
                () -> client.setScreen(new SkyzModsScreen(this)));
        y += BTN_H + BTN_GAP;

        // Row: Options | Skyz Settings
        half(cx, y, "\u2699  Options",
                () -> client.setScreen(new OptionsScreen(this, client.options)));
        half(cx + h + BTN_GAP, y, "\u2726  Skyz Settings",
                () -> client.setScreen(new SkyzSettingsScreen(this)));
        y += BTN_H + BTN_GAP;

        // Row: Skin Editor | Cosmetics
        half(cx, y, "\uD83E\uDDCD  Skin Editor",
                () -> toast("Skin Editor \u2014 coming soon!"));
        half(cx + h + BTN_GAP, y, "\u2728  Cosmetics",
                () -> toast("Install Essential: modrinth.com/mod/essential"));
        y += BTN_H + BTN_GAP;

        // Row: Shaders | HUD Editor
        half(cx, y, "\uD83C\uDF05  Shaders",
                () -> client.setScreen(new SkyzShadersScreen(this)));
        half(cx + h + BTN_GAP, y, "\uD83C\uDF9B  HUD Editor",
                () -> client.setScreen(new SkyzHudEditorScreen(this)));
        y += BTN_H + BTN_GAP;

        row(cx, y, BTN_W, "\uD83D\uDC65  Friends",
                () -> client.setScreen(new SkyzFriendsScreen(this)));
        y += BTN_H + BTN_GAP + 4; // small spacer before quit

        addDrawableChild(SkyzButton.danger(
                cx - BTN_W / 2, y, BTN_W, BTN_H,
                "\u2297  Quit Game",
                () -> client.scheduleStop()));

        // Re-open multiplayer screen after server disconnect
        if (pendingOpenMultiplayer) {
            pendingOpenMultiplayer = false;
            client.setScreen(new SkyzMultiplayerScreen(this));
        }
    }

    private void row(int cx, int y, int w, String label, Runnable action) {
        addDrawableChild(SkyzButton.of(cx - w / 2, y, w, BTN_H, label, action));
    }

    private void half(int x, int y, String label, Runnable action) {
        int w = BTN_W / 2 - BTN_GAP / 2;
        addDrawableChild(SkyzButton.of(x - BTN_W / 2, y, w, BTN_H, label, action));
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Render
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        tick += delta;

        SkyzBackgroundManager bg = SkyzBackgroundManager.getInstance();
        if (bg.hasBackground()) {
            bg.draw(ctx, width, height);
            bg.drawDim(ctx, width, height);
        } else {
            drawGradientBackground(ctx);
        }

        particles.tick(ctx, delta);
        drawLogo(ctx);

        super.render(ctx, mouseX, mouseY, delta); // draws buttons

        drawHudClock(ctx);
        drawSplash(ctx);
        drawMuteButton(ctx, mouseX, mouseY);
        drawBgHint(ctx);

        if (bg.isDragging()) drawDropZoneOverlay(ctx);

        toast.render(ctx, width, delta);
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Background
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private void drawGradientBackground(DrawContext ctx) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,          width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height/3,   width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height*2/3, width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
        // Radial glow
        int gcx = width / 2, gcy = height / 3, gr = Math.min(width, height) / 2;
        for (int ring = gr; ring > 0; ring -= 10) {
            int a = (int)(10 * (1f - (float)ring/gr));
            if (a <= 0) continue;
            for (int dy = -ring; dy <= ring; dy += 5) {
                double hw = Math.sqrt(Math.max(0, (double)ring*ring - (double)dy*dy));
                ctx.fill((int)(gcx-hw), gcy+dy, (int)(gcx+hw), gcy+dy+5, (a<<24)|0x46A0FF);
            }
        }
        SkyzRenderHelper.fillGradientV(ctx, 0, height*3/4, width, height/4, 0x00051432, 0x8C051432);
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Logo
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private void drawLogo(DrawContext ctx) {
        int cx     = width / 2;
        int scale  = 4;
        int logoY  = (int)(height * 0.12f);
        String logo    = "SKYZ";
        String tagline = "Client  \u00B7  Java Edition  \u00B7  1.21.11";
        int logoW = textRenderer.getWidth(logo) * scale;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(textRenderer, logo, (cx - logoW/2)/scale, (logoY+2)/scale, 0x4064A8FF, false);
        ctx.drawText(textRenderer, logo, (cx - logoW/2)/scale, logoY/scale,     0xFFF0F8FF, false);
        ctx.getMatrices().popMatrix();

        int taglineY = logoY + scale*9 + 8;
        ctx.drawCenteredTextWithShadow(textRenderer, tagline, cx, taglineY, 0x8BB4E6FF);

        int lineY = taglineY + 14;
        SkyzRenderHelper.fillGradientH(ctx, cx-110, lineY, 110, 1, 0x00A0D4FF, 0xA0A0D4FF);
        SkyzRenderHelper.fillGradientH(ctx, cx,     lineY, 110, 1, 0xA0A0D4FF, 0x00A0D4FF);

        float pulse    = (float)(Math.sin(tick * 0.05) * 0.3 + 0.7);
        int   dotAlpha = (int)(pulse * 200);
        SkyzRenderHelper.fillRect(ctx, cx-2, lineY-2, 4, 4, (dotAlpha<<24)|0x8CD2FF);
        SkyzRenderHelper.fillRect(ctx, cx-1, lineY-1, 2, 2, 0xE68CD2FF);
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // HUD overlays
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private void drawHudClock(DrawContext ctx) {
        Calendar now = Calendar.getInstance();
        String hud = String.format("Skyz Client  \u00B7  v2.5.0  \u00B7  %02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        ctx.drawCenteredTextWithShadow(textRenderer, hud, width/2, height-12, 0x4D8CD2FF);
    }

    private void drawSplash(DrawContext ctx) {
        ctx.drawTextWithShadow(textRenderer, splash,
                width - textRenderer.getWidth(splash) - 6, 6, 0x618CC8FF);
    }

    private void drawMuteButton(DrawContext ctx, int mx, int my) {
        String icon = musicMuted ? "\uD83D\uDD07 Music OFF" : "\uD83D\uDD0A Music ON";
        int bw = textRenderer.getWidth(icon) + 14, bh = 14;
        int bx = width - bw - 6, by = height - bh - 16;
        boolean hov = mx>=bx && mx<=bx+bw && my>=by && my<=by+bh;
        SkyzRenderHelper.fillPanel(ctx, bx, by, bw, bh,
                hov ? 0x44143C6E : 0x22091E46, hov ? 0x558CD2FF : 0x228CD2FF);
        ctx.drawTextWithShadow(textRenderer, icon, bx+7, by+3,
                musicMuted ? 0x558CD2FF : SkyzColors.TEXT_MUTED);
    }

    private void drawBgHint(DrawContext ctx) {
        SkyzBackgroundManager bg = SkyzBackgroundManager.getInstance();
        String hint = bg.hasBackground()
                ? "\uD83D\uDDBC Drag image to change BG  |  [X] Clear"
                : "\uD83D\uDDBC Drag PNG/JPG here to set background";
        ctx.drawTextWithShadow(textRenderer, hint, 6, height-12, 0x2E8CD2FF);
    }

    private void drawDropZoneOverlay(DrawContext ctx) {
        ctx.fill(0, 0, width, height, 0xCC050F2A);
        int p = 20, col = 0xCC8CD2FF;
        SkyzRenderHelper.fillRect(ctx, p,       p,       width-p*2, 2,    col);
        SkyzRenderHelper.fillRect(ctx, p,       height-p-2, width-p*2, 2, col);
        SkyzRenderHelper.fillRect(ctx, p,       p,       2, height-p*2,   col);
        SkyzRenderHelper.fillRect(ctx, width-p-2, p,     2, height-p*2,   col);
        ctx.drawCenteredTextWithShadow(textRenderer, "Drop your image here",           width/2, height/2-12, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "PNG, JPG or BMP \u2014 any size",width/2, height/2+4,  0x888CD2FF);
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Input
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    @Override
    public void onFilesDropped(List<Path> paths) {
        SkyzBackgroundManager bg = SkyzBackgroundManager.getInstance();
        bg.setDragging(false);
        if (!bg.onFileDrop(paths)) toast("Not an image. Use PNG, JPG or BMP.");
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        // Mute toggle
        String icon = musicMuted ? "\uD83D\uDD07 Music OFF" : "\uD83D\uDD0A Music ON";
        int bw = textRenderer.getWidth(icon)+14, bh = 14;
        int bx = width-bw-6, by = height-bh-16;
        if (mx>=bx && mx<=bx+bw && my>=by && my<=by+bh) {
            musicMuted = !SkyzAudioManager.getInstance().toggle();
            toast(musicMuted ? "Music muted" : "Music playing");
            return true;
        }

        // Clear BG hint
        SkyzBackgroundManager bg = SkyzBackgroundManager.getInstance();
        if (bg.hasBackground()) {
            int hx = 6 + textRenderer.getWidth("\uD83D\uDDBC Drag image to change BG  |  ");
            int hy = height - 12;
            if (mx>=hx && mx<=hx+textRenderer.getWidth("[X] Clear") && my>=hy-1 && my<=hy+9) {
                bg.clear(); toast("Background cleared"); return true;
            }
        }
        return false;
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Lifecycle
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    public void toast(String msg) { toast.show(msg); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        particles.resize(width, height);
    }

    @Override
    public void removed() {
        SkyzAudioManager.getInstance().stop();
    }
}
