package net.skyz.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.skyz.client.util.*;
import net.skyz.client.util.SkyzTheme;

public class SkyzModConfigScreen extends Screen {

    private final SkyzModsScreen   modsScreen;
    private final SkyzTitleScreen  titleScreen;
    private final String modName, modVersion;
    private final boolean bundled;

    private int    activeTab = 0;
    private static final String[] TABS = {"\u2699 General", "\u2328 Keybinds", "\uD83D\uDD2C Advanced", "\u2139 About"};

    // Persistent toggle states stored per-mod by name
    private static final java.util.Map<String, java.util.Map<String,Boolean>> MOD_TOGGLES = new java.util.HashMap<>();
    private java.util.Map<String,Boolean> myToggles;
    private final int[] sliders = new int[8];

    private static final int NAV_H     = 28;
    private static final int TAB_W     = 130;
    private static final int CONTENT_X = TAB_W + 24;
    private static final int ROW_H     = 26;

    public SkyzModConfigScreen(SkyzModsScreen modsScreen, SkyzTitleScreen titleScreen,
                               String modName, String modVersion, boolean bundled) {
        super(Text.literal("Config \u2014 " + modName));
        this.modsScreen   = modsScreen;
        this.titleScreen  = titleScreen;
        this.modName      = modName;
        this.modVersion   = modVersion;
        this.bundled      = bundled;
        sliders[0] = 100; sliders[1] = 100; sliders[2] = 70;
        sliders[3] = 50;  sliders[4] = 64;  sliders[5] = 80;
        myToggles = MOD_TOGGLES.computeIfAbsent(modName, k -> new java.util.HashMap<>());
    }

    @Override
    protected void init() {
        addDrawableChild(SkyzButton.of(width-82, 6, 74, 18, "\u2190 Mods",
                () -> client.setScreen(modsScreen)));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, width, height, SkyzTheme.BG1, SkyzTheme.BG2);
        drawNavBar(ctx);
        drawTabNav(ctx, mouseX, mouseY);
        drawContent(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
        titleScreen.toast.render(ctx, width, delta);
    }

    private void drawNavBar(DrawContext ctx) {
        int x = 10;
        ctx.drawTextWithShadow(textRenderer, "SKYZ", x, 10, 0xFFF0F8FF);
        x += textRenderer.getWidth("SKYZ")+6;
        ctx.drawTextWithShadow(textRenderer, "/", x, 10, 0x4D8CD2FF);
        x += textRenderer.getWidth("/")+6;
        ctx.drawTextWithShadow(textRenderer, "MODS", x, 10, 0x888CD2FF);
        x += textRenderer.getWidth("MODS")+6;
        ctx.drawTextWithShadow(textRenderer, "/", x, 10, 0x4D8CD2FF);
        x += textRenderer.getWidth("/")+6;
        ctx.drawTextWithShadow(textRenderer, modName.toUpperCase(), x, 10, 0x888CD2FF);
        SkyzRenderHelper.drawDivider(ctx, 0, NAV_H, width);
    }

    private void drawTabNav(DrawContext ctx, int mx, int my) {
        int x = 10, y = NAV_H + 10;
        ctx.drawTextWithShadow(textRenderer, "SETTINGS", x, y, 0x2E8CD2FF);
        y += 14;
        for (int i = 0; i < TABS.length; i++) {
            boolean active  = i == activeTab;
            boolean hovered = mx>=x && mx<=x+TAB_W && my>=y && my<=y+20;
            int bg = active ? 0x4414507A : hovered ? 0x22143C6E : 0;
            SkyzRenderHelper.fillRect(ctx, x, y, TAB_W, 20, bg);
            if (active) SkyzRenderHelper.fillRect(ctx, x, y, 2, 20, 0xCC8CD2FF);
            ctx.drawTextWithShadow(textRenderer, TABS[i], x+10, y+6,
                    active ? SkyzColors.TEXT_PRIMARY : SkyzColors.TEXT_MUTED);
            y += 24;
        }
    }

    private void drawContent(DrawContext ctx, int mx, int my) {
        int x = CONTENT_X, y = NAV_H+10, w = width-x-14;
        switch (activeTab) {
            case 0 -> drawGeneral(ctx, x, y, w, mx, my);
            case 1 -> drawKeybinds(ctx, x, y, w);
            case 2 -> drawAdvanced(ctx, x, y, w);
            case 3 -> drawAbout(ctx, x, y, w);
        }
    }

    private void drawGeneral(DrawContext ctx, int x, int y, int w, int mx, int my) {
        switch (modName) {
            case "Sodium"       -> drawSodiumSettings(ctx, x, y, w, mx, my);
            case "Iris Shaders" -> drawIrisSettings(ctx, x, y, w, mx, my);
            case "Mod Menu"     -> drawModMenuSettings(ctx, x, y, w, mx, my);
            case "Essential"    -> drawEssentialSettings(ctx, x, y, w, mx, my);
            default             -> drawGenericSettings(ctx, x, y, w, mx, my);
        }
    }

    private void drawSodiumSettings(DrawContext ctx, int x, int y, int w, int mx, int my) {
        drawSectionHeader(ctx, x, y, "Rendering (Sodium 0.8.2)"); y+=16;
        drawToggleRow(ctx, x, y, w, "Chunk Culling",          "Skip rendering off-screen chunks", 0, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Fog Occlusion",          "Hide geometry obscured by fog",    1, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Compact Vertex Format",  "Reduces VRAM usage",               2, mx, my); y+=ROW_H;
        y+=8; drawSectionHeader(ctx, x, y, "Performance"); y+=16;
        drawSliderRow(ctx, x, y, w, "CPU Render-Ahead Limit", 1, 9,   0, mx, my); y+=ROW_H;
        drawSliderRow(ctx, x, y, w, "Chunk Build Threads",    1, 8,   1, mx, my);
    }

    private void drawIrisSettings(DrawContext ctx, int x, int y, int w, int mx, int my) {
        drawSectionHeader(ctx, x, y, "Iris Shaders 1.10.4"); y+=16;
        drawToggleRow(ctx, x, y, w, "Shaders Enabled",            "Toggle all shader effects",  0, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Entity Shadows",             "Cast shadows from entities", 1, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Smooth Sun Path Rotation",   "",                           2, mx, my); y+=ROW_H;
        y+=8; drawSectionHeader(ctx, x, y, "Quality"); y+=16;
        drawSliderRow(ctx, x, y, w, "Shadow Distance",     32, 256, 4, mx, my); y+=ROW_H;
        drawSliderRow(ctx, x, y, w, "Max Shadow Distance",  8,  64, 5, mx, my);
    }

    private void drawModMenuSettings(DrawContext ctx, int x, int y, int w, int mx, int my) {
        drawSectionHeader(ctx, x, y, "Mod Menu 17.0.0-\u03B21"); y+=16;
        drawToggleRow(ctx, x, y, w, "Show Library Mods",       "Show API/library mods in list",    0, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Show Mod Update Badges",  "Highlight mods with updates",      1, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Config Buttons",          "Show \u2699 config icon per mod",  2, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Quick Config Access",     "Ctrl+click mod to configure",      3, mx, my);
    }

    private void drawEssentialSettings(DrawContext ctx, int x, int y, int w, int mx, int my) {
        drawSectionHeader(ctx, x, y, "Essential 1.3.4 by EssentialGG"); y+=16;
        drawToggleRow(ctx, x, y, w, "World Hosting",   "Host your world for friends for free", 0, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Friends List",    "See friends online in Minecraft",      1, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Cosmetics",       "Capes, wings & emotes",                2, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Skin Manager",    "Manage & apply skins in-game",         3, mx, my); y+=ROW_H;
        y+=8; drawSectionHeader(ctx, x, y, "Notifications"); y+=16;
        drawToggleRow(ctx, x, y, w, "Friend Join Alerts",    "", 4, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "World Invite Popups",   "", 5, mx, my); y+=14;
        ctx.drawTextWithShadow(textRenderer, "\u2139 modrinth.com/mod/essential", x+4, y, 0x4D8CD2FF);
    }

    private void drawGenericSettings(DrawContext ctx, int x, int y, int w, int mx, int my) {
        drawSectionHeader(ctx, x, y, "General"); y+=16;
        drawToggleRow(ctx, x, y, w, "Enabled",          "Toggle this mod on or off", 0, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Show in F5",       "",                          1, mx, my); y+=ROW_H;
        drawToggleRow(ctx, x, y, w, "Show in Spectator","",                          2, mx, my); y+=ROW_H;
        y+=8; drawSectionHeader(ctx, x, y, "Display"); y+=16;
        drawSliderRow(ctx, x, y, w, "Scale",   50, 200, 0, mx, my); y+=ROW_H;
        drawSliderRow(ctx, x, y, w, "Opacity", 10, 100, 1, mx, my);
    }

    private void drawKeybinds(DrawContext ctx, int x, int y, int w) {
        drawSectionHeader(ctx, x, y, "Keybinds"); y+=16;
        for (String[] b : new String[][]{{"Toggle Mod","K"},{"Open Config","None"},{"Reload","None"}}) {
            SkyzRenderHelper.fillRect(ctx, x, y, w, ROW_H-2, 0x22091E46);
            ctx.drawTextWithShadow(textRenderer, b[0], x+8, y+9, SkyzColors.TEXT_PRIMARY);
            int bw = textRenderer.getWidth(b[1])+14;
            SkyzRenderHelper.fillPanel(ctx, x+w-bw-4, y+5, bw, 16, 0x33143C6E, 0x3A8CD2FF);
            ctx.drawTextWithShadow(textRenderer, b[1], x+w-bw+3, y+9, SkyzColors.TEXT_MUTED);
            y += ROW_H;
        }
    }

    private void drawAdvanced(DrawContext ctx, int x, int y, int w) {
        drawSectionHeader(ctx, x, y, "Advanced"); y+=16;
        ctx.drawTextWithShadow(textRenderer, "Advanced options for " + modName + " v" + modVersion + ".", x+4, y, SkyzColors.TEXT_MUTED);
    }

    private void drawAbout(DrawContext ctx, int x, int y, int w) {
        drawSectionHeader(ctx, x, y, "About"); y+=16;
        drawInfoRow(ctx, x, y, w, "Mod",        modName);       y+=ROW_H;
        drawInfoRow(ctx, x, y, w, "Version",    modVersion);    y+=ROW_H;
        drawInfoRow(ctx, x, y, w, "MC Version", "1.21.11");     y+=ROW_H;
        drawInfoRow(ctx, x, y, w, "Loader",     "Fabric 0.16.10"); y+=ROW_H;
        drawInfoRow(ctx, x, y, w, "Bundled",    bundled ? "Yes \u2726" : "No");
    }

    // \u2500\u2500 Primitives \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private void drawSectionHeader(DrawContext ctx, int x, int y, String title) {
        ctx.drawTextWithShadow(textRenderer, title.toUpperCase(), x, y, 0x2E8CD2FF);
        SkyzRenderHelper.fillRect(ctx, x, y+10, textRenderer.getWidth(title.toUpperCase())+20, 1, 0x1A8CD2FF);
    }

    private void drawToggleRow(DrawContext ctx, int x, int y, int w,
                               String label, String sub, int idx, int mx, int my) {
        SkyzRenderHelper.fillRect(ctx, x, y, w, ROW_H-2, 0x1A091E46);
        ctx.drawTextWithShadow(textRenderer, label, x+8, y+(sub.isEmpty()?9:5), SkyzColors.TEXT_PRIMARY);
        if (!sub.isEmpty()) ctx.drawTextWithShadow(textRenderer, sub, x+8, y+15, 0x4D8CD2FF);
        // Use myToggles map keyed by label for persistent state
        boolean on = myToggles.getOrDefault(label, idx % 3 != 2);
        int tx = x+w-44, ty = y+(ROW_H-14)/2;
        SkyzRenderHelper.fillPanel(ctx, tx, ty, 36, 14,
                on ? 0x551E6EC8 : 0x33143254,
                on ? 0x6464BEFF : 0x2A8CD2FF);
        int thumbX = on ? tx+21 : tx+3;
        SkyzRenderHelper.fillRect(ctx, thumbX, ty+3, 10, 8, on ? 0xCC64C8FF : 0x7890AACC);
    }

    private void drawSliderRow(DrawContext ctx, int x, int y, int w,
                               String label, int min, int max, int idx, int mx, int my) {
        SkyzRenderHelper.fillRect(ctx, x, y, w, ROW_H-2, 0x1A091E46);
        ctx.drawTextWithShadow(textRenderer, label, x+8, y+9, SkyzColors.TEXT_PRIMARY);
        int val = (idx < sliders.length) ? sliders[idx] : 50;
        int sW  = 120, sX = x+w-sW-10, sY = y+(ROW_H-4)/2;
        SkyzRenderHelper.fillRect(ctx, sX, sY, sW, 4, 0x338CD2FF);
        SkyzRenderHelper.fillRect(ctx, sX, sY, (int)(sW*(float)(val-min)/(max-min)), 4, 0x9964C8FF);
        ctx.drawTextWithShadow(textRenderer, String.valueOf(val),
                sX - textRenderer.getWidth(String.valueOf(val))-6, y+9, SkyzColors.TEXT_MUTED);
    }

    private void drawInfoRow(DrawContext ctx, int x, int y, int w, String k, String v) {
        SkyzRenderHelper.fillRect(ctx, x, y, w, ROW_H-2, 0x1A091E46);
        ctx.drawTextWithShadow(textRenderer, k, x+8, y+9, SkyzColors.TEXT_MUTED);
        ctx.drawTextWithShadow(textRenderer, v, x+w-textRenderer.getWidth(v)-8, y+9, SkyzColors.TEXT_PRIMARY);
    }

    // \u2500\u2500 Input \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    
    private java.util.List<String> getToggleLabels() {
        return switch (modName) {
            case "Sodium"    -> java.util.List.of("Chunk Culling","Fog Occlusion","Compact Vertex Format");
            case "Iris"      -> java.util.List.of("Shaders Enabled","Entity Shadows","Smooth Sun Path");
            case "ModMenu"   -> java.util.List.of("Show Library Mods","Show Update Badges","Config Buttons","Quick Config");
            case "Essential" -> java.util.List.of("World Hosting","Friends List","Cosmetics","Skin Manager","Friend Alerts","World Invites");
            default          -> java.util.List.of("Enabled","Debug Mode","Verbose Logging");
        };
    }

    private void applyToggle(String key, boolean value) {
        titleScreen.toast(key + ": " + (value ? "ON" : "OFF"));
    }

@Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        // Tab clicks
        int tabX = 10, tabY = NAV_H + 24;
        for (int i = 0; i < TABS.length; i++) {
            if (mx>=tabX && mx<=tabX+TAB_W && my>=tabY && my<=tabY+20) {
                activeTab = i; return true;
            }
            tabY += 24;
        }

        // Toggle clicks (general tab) - collect all labels and toggle by row index
        if (activeTab == 0) {
            int x = CONTENT_X, y = NAV_H+26, w = width-CONTENT_X-14;
            java.util.List<String> labels = getToggleLabels();
            for (int i = 0; i < labels.size(); i++) {
                int tx = x+w-44;
                if (mx>=tx && mx<=tx+36 && my>=y && my<=y+ROW_H) {
                    String key = labels.get(i);
                    myToggles.put(key, !myToggles.getOrDefault(key, i % 3 != 2));
                    applyToggle(key, myToggles.get(key));
                    return true;
                }
                y += ROW_H;
                if (i == 3) y += 24;
            }
        }
        return false;
    }

    @Override public boolean shouldPause() { return false; }
}
