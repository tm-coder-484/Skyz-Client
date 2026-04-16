package net.skyz.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.skyz.client.util.*;

/**
 * Skyz Settings - themes, interface toggles, sliders, client info.
 * Theme changes take effect immediately via SkyzTheme.apply().
 */
public class SkyzSettingsScreen extends Screen {

    private final SkyzTitleScreen parent;

    // Interface toggles: [particles, splash text, animations, glow effects, compact cards]
    private final boolean[] toggles = {true, false, true, true, false};
    // Sliders: [blur 0-40, glow 0-100, particle count 0-100, ui scale 50-200]
    private final int[]     sliders = {20,    70,    60,          100};

    private int  draggingSlider = -1;
    private int  sliderStartX;

    private static final int NAV_H   = 30;
    private static final int PAD     = 14;
    private static final int ROW_H   = 26;
    private static final int COL2_X  = 260; // second column of toggles

    public SkyzSettingsScreen(SkyzTitleScreen parent) {
        super(Text.literal("Skyz Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(SkyzButton.of(width - 82, 7, 74, 18, "\u2190 Back",
                () -> client.setScreen(parent)));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Background uses the CURRENT theme colors
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, width, height / 3,
                SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3, width, height * 2 / 3,
                SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2 / 3, width, height / 3,
                SkyzTheme.BG3, SkyzTheme.BG1);

        drawNavBar(ctx);

        int y = NAV_H + 10;
        // \u2500\u2500 Theme section \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        y = drawSectionHeader(ctx, "Theme", y);
        y = drawThemeGrid(ctx, y, mouseX, mouseY);

        // \u2500\u2500 Interface section \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        y = drawSectionHeader(ctx, "Interface", y + 10);
        y = drawToggles(ctx, y, mouseX, mouseY);
        y = drawSliders(ctx, y, mouseX, mouseY);

        // \u2500\u2500 Client info section \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        y = drawSectionHeader(ctx, "Client Info", y + 10);
        drawClientInfo(ctx, y);

        super.render(ctx, mouseX, mouseY, delta);
        parent.toast.render(ctx, width, delta);
    }

    private void drawNavBar(DrawContext ctx) {
        ctx.drawTextWithShadow(textRenderer, "SKYZ", 10, 10, 0xFFF0F8FF);
        int x = 10 + textRenderer.getWidth("SKYZ") + 6;
        ctx.drawTextWithShadow(textRenderer, "/", x, 10, SkyzTheme.ACCENT_DIM);
        x += textRenderer.getWidth("/") + 6;
        ctx.drawTextWithShadow(textRenderer, "SETTINGS", x, 10, 0x888CD2FF);
        SkyzRenderHelper.drawDivider(ctx, 0, NAV_H, width);
    }

    private int drawSectionHeader(DrawContext ctx, String title, int y) {
        ctx.drawTextWithShadow(textRenderer, title.toUpperCase(), PAD, y, SkyzTheme.ACCENT_DIM);
        SkyzRenderHelper.fillRect(ctx, PAD, y + 11, width - PAD * 2, 1, SkyzTheme.DIVIDER);
        return y + 20;
    }

    private int drawThemeGrid(DrawContext ctx, int y, int mx, int my) {
        int cols = SkyzTheme.THEMES.length;
        int cw   = (width - PAD * 2 - (cols - 1) * 6) / cols;
        int ch   = 48;

        for (int i = 0; i < SkyzTheme.THEMES.length; i++) {
            int tx = PAD + i * (cw + 6);
            boolean sel = i == SkyzTheme.getCurrent();
            boolean hov = mx >= tx && mx <= tx + cw && my >= y && my <= y + ch;

            String[] t = SkyzTheme.THEMES[i];
            int c1 = (int) Long.parseLong(t[1].substring(2), 16);
            int c2 = (int) Long.parseLong(t[3].substring(2), 16);
            int ac = (int) Long.parseLong(t[5].substring(2), 16);

            // Gradient card
            SkyzRenderHelper.fillGradientV(ctx, tx, y, cw, ch, c1, c2);

            // Accent stripe at top
            SkyzRenderHelper.fillRect(ctx, tx, y, cw, 2, sel ? (ac | 0xFF000000) : (ac & 0x00FFFFFF) | (hov ? 0x99000000 : 0x44000000));

            // Border
            int brdCol = sel ? (ac | 0xFF000000) : hov ? ((ac & 0x00FFFFFF) | 0x88000000) : ((ac & 0x00FFFFFF) | 0x33000000);
            SkyzRenderHelper.fillRect(ctx, tx,        y,       cw, 1,  brdCol);
            SkyzRenderHelper.fillRect(ctx, tx,        y+ch-1,  cw, 1,  brdCol);
            SkyzRenderHelper.fillRect(ctx, tx,        y,       1,  ch, brdCol);
            SkyzRenderHelper.fillRect(ctx, tx+cw-1,   y,       1,  ch, brdCol);

            // Checkmark if selected
            if (sel) ctx.drawCenteredTextWithShadow(textRenderer, "\u2713", tx + cw/2, y + 8, 0xFFFFFFFF);

            // Accent dot
            SkyzRenderHelper.fillRect(ctx, tx + cw/2 - 4, y + 20, 8, 8, (ac | 0xFF000000));

            // Name
            ctx.drawCenteredTextWithShadow(textRenderer, t[0], tx + cw/2, y + ch - 14, 0xCCFFFFFF);
        }
        return y + ch + 8;
    }

    private int drawToggles(DrawContext ctx, int y, int mx, int my) {
        String[][] rows = {
            {"Particle Effects",   "Ambient floating particles"},
            {"Splash Text",        "Rotating tip text on title"},
            {"Animations",         "Hover & transition effects"},
            {"Glow Effects",       "Accent color glow on panels"},
            {"Compact Mode",       "Smaller cards in lists"},
        };
        // 2-column layout
        int halfW = (width - PAD * 2) / 2 - 8;
        for (int i = 0; i < rows.length; i++) {
            int col = i % 2, row = i / 2;
            int rx = PAD + col * (halfW + 16);
            int ry = y + row * ROW_H;

            SkyzRenderHelper.fillRect(ctx, rx, ry, halfW, ROW_H - 2, 0x1A091E46);
            ctx.drawTextWithShadow(textRenderer, rows[i][0], rx + 8, ry + 5, SkyzColors.TEXT_PRIMARY);
            ctx.drawTextWithShadow(textRenderer, rows[i][1], rx + 8, ry + 15, SkyzColors.TEXT_MUTED & 0x88FFFFFF | (SkyzColors.TEXT_MUTED & 0xFF000000) >> 1);

            boolean on = i < toggles.length && toggles[i];
            int tx = rx + halfW - 44;
            int ty = ry + (ROW_H - 2)/2 - 7;
            // Toggle pill
            SkyzRenderHelper.fillPanel(ctx, tx, ty, 38, 14,
                    on ? 0x55143C8A : 0x33143254,
                    on ? SkyzTheme.ACCENT_DIM : 0x2A8CD2FF);
            // Knob
            SkyzRenderHelper.fillRect(ctx, on ? tx + 23 : tx + 3, ty + 3, 10, 8,
                    on ? (SkyzTheme.ACCENT1 | 0xFF000000) : 0x7890AACC);
        }
        int rowsUsed = (rows.length + 1) / 2;
        return y + rowsUsed * ROW_H + 6;
    }

    private int drawSliders(DrawContext ctx, int y, int mx, int my) {
        String[][] sliderDef = {
            {"Blur Intensity",    "0",  "40",  "px"},
            {"Glow Strength",     "0",  "100", "%"},
            {"Particle Count",    "0",  "100", "%"},
        };
        int sW = 160;
        for (int i = 0; i < sliderDef.length; i++) {
            SkyzRenderHelper.fillRect(ctx, PAD, y, width - PAD*2, ROW_H - 2, 0x1A091E46);
            ctx.drawTextWithShadow(textRenderer, sliderDef[i][0], PAD + 8, y + 9, SkyzColors.TEXT_PRIMARY);

            int max = Integer.parseInt(sliderDef[i][2]);
            String val = sliders[i] + sliderDef[i][3];
            ctx.drawTextWithShadow(textRenderer,
                    val, width - PAD - sW - textRenderer.getWidth(val) - 10, y + 9,
                    SkyzColors.TEXT_MUTED);

            int sX = width - PAD - sW - 4;
            int sY = y + (ROW_H - 2) / 2;
            float pct = (float) sliders[i] / max;

            SkyzRenderHelper.fillRect(ctx, sX, sY - 2, sW, 4, SkyzTheme.DIVIDER);
            SkyzRenderHelper.fillRect(ctx, sX, sY - 2, (int)(sW * pct), 4, SkyzTheme.ACCENT_DIM);
            int kx = sX + (int)(sW * pct) - 5;
            boolean hovSlider = mx >= kx && mx <= kx + 10 && my >= sY - 6 && my <= sY + 6;
            SkyzRenderHelper.fillPanel(ctx, kx, sY - 5, 10, 10,
                    hovSlider ? SkyzTheme.BTN_BG : 0x88143C6E,
                    hovSlider ? (SkyzTheme.ACCENT1 | 0xFF000000) : (SkyzTheme.ACCENT2 | 0xFF000000));
            y += ROW_H;
        }
        return y + 4;
    }

    private void drawClientInfo(DrawContext ctx, int y) {
        // Dynamically read actual loaded mod versions via FabricLoader
        String sodiumVer = getModVersion("sodium",    "not installed");
        String irisVer   = getModVersion("iris",      "not installed");
        String mmVer     = getModVersion("modmenu",   "not installed");
        String essVer    = getModVersion("essential", "not installed");

        String[][] info = {
            {"Skyz Client",    "v2.5.0"},
            {"Minecraft",      "1.21.11"},
            {"Fabric Loader",  getModVersion("fabricloader", "0.16.10")},
            {"Sodium",         sodiumVer},
            {"Iris Shaders",   irisVer},
            {"Mod Menu",       mmVer},
            {"Essential",      essVer},
        };
        for (String[] row : info) {
            boolean installed = !row[1].equals("not installed");
            SkyzRenderHelper.fillRect(ctx, PAD, y, width - PAD * 2, 22, 0x1A091E46);
            ctx.drawTextWithShadow(textRenderer, row[0], PAD + 8, y + 7, SkyzColors.TEXT_MUTED);
            int col = installed ? SkyzColors.TEXT_PRIMARY : 0x66888888;
            ctx.drawTextWithShadow(textRenderer, row[1],
                    width - PAD - textRenderer.getWidth(row[1]) - 8, y + 7, col);
            // status dot
            int dot = installed ? SkyzColors.STATUS_GREEN : SkyzColors.STATUS_RED;
            SkyzRenderHelper.fillRect(ctx, width - PAD - textRenderer.getWidth(row[1]) - 18, y + 10, 6, 6, dot);
            y += 24;
        }
    }

    private String getModVersion(String modId, String fallback) {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(modId)
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse(fallback);
    }

    // \u2500\u2500 Input \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    // \u2500\u2500 Shared y-position calculator (mirrors render() exactly) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private int themeGridTop() {
        // y starts at NAV_H+10, drawSectionHeader adds 20 \u2192 grid starts at NAV_H+30
        return NAV_H + 10 + 20; // = 60 when NAV_H=30
    }
    private int togglesTop() {
        int y = themeGridTop() + 48 + 8; // after grid
        // drawSectionHeader("Interface", y+10) \u2192 y+10+20 = y+30
        return y + 10 + 20;
    }
    private int slidersTop() {
        int y = togglesTop();
        int numRows = 5;
        int rowsUsed = (numRows + 1) / 2; // = 3 rows
        return y + rowsUsed * ROW_H + 6;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        // \u2500\u2500 Theme grid \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        int cols = SkyzTheme.THEMES.length;
        int cw = (width - PAD * 2 - (cols - 1) * 6) / cols, ch = 48;
        int themeY = themeGridTop();
        for (int i = 0; i < SkyzTheme.THEMES.length; i++) {
            int tx = PAD + i * (cw + 6);
            if (mx >= tx && mx <= tx + cw && my >= themeY && my <= themeY + ch) {
                SkyzTheme.apply(i);
                parent.toast("Theme: " + SkyzTheme.THEMES[i][0]);
                return true;
            }
        }

        // \u2500\u2500 Toggle switches \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        int toggleY = togglesTop();
        int halfW   = (width - PAD * 2) / 2 - 8;
        int numToggles = 5;
        for (int i = 0; i < numToggles; i++) {
            int col = i % 2, row = i / 2;
            int rx = PAD + col * (halfW + 16);
            int ry = toggleY + row * ROW_H;
            // Toggle pill: right side of the half-column card
            int tx = rx + halfW - 44;
            int ty = ry + (ROW_H - 2) / 2 - 7;
            if (mx >= tx && mx <= tx + 38 && my >= ty && my <= ty + 14) {
                if (i < toggles.length) {
                    toggles[i] = !toggles[i];
                    applyToggle(i);
                }
                return true;
            }
        }

        // \u2500\u2500 Sliders \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        int sliderY = slidersTop();
        int sW = 160;
        int[] maxes = {40, 100, 100};
        for (int i = 0; i < 3; i++) {
            int sX = width - PAD - sW - 4;
            int sy = sliderY + (ROW_H - 2) / 2;
            // Hit area covers the full slider track plus knob overhang
            if (mx >= sX && mx <= sX + sW && my >= sy - 10 && my <= sy + 10) {
                draggingSlider = i;
                sliderStartX   = sX;
                sliders[i] = Math.max(0, Math.min(maxes[i],
                        (int)((mx - sX) / sW * maxes[i])));
                applySlider(i);
                return true;
            }
            sliderY += ROW_H;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (draggingSlider >= 0) {
            double mx = click.x();
            int sW = 160, sX = width - PAD - sW - 4;
            int[] maxes = {40, 100, 100};
            int v = Math.max(0, Math.min(maxes[draggingSlider],
                    (int)((mx - sX) / sW * maxes[draggingSlider])));
            sliders[draggingSlider] = v;
            applySlider(draggingSlider);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = -1;
        return super.mouseReleased(click);
    }

    private void applyToggle(int i) {
        switch (i) {
            case 0 -> parent.particles.setEnabled(toggles[0]);
            // Future: case 1 splash text, case 2 animations, etc.
        }
    }

    private void applySlider(int i) {
        // Future: pass blur/glow/particle count to renderers
    }

    @Override public boolean shouldPause() { return false; }
}
