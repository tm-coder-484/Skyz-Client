package net.skyz.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import net.minecraft.world.level.storage.LevelSummary;
import net.skyz.client.util.*;
import net.skyz.client.util.SkyzTheme;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Custom Singleplayer screen using MC's actual world list.
 *
 * 1.21.11 API notes:
 *  - LevelSummary.getDisplayName() returns String directly
 *  - LevelSummary.getGameMode() returns GameMode enum - compare with == GameMode.CREATIVE etc.
 *  - LevelStorage.loadSummaries() takes a LevelStorage.LevelList, obtained via getLevelList()
 *  - client field is protected in Screen; store reference in constructor
 *  - World loading via SelectWorldScreen for reliability
 */
public class SkyzSingleplayerScreen extends Screen {

    private final SkyzTitleScreen parent;
    private final MinecraftClient mc;

    private List<LevelSummary> worlds   = new ArrayList<>();
    private boolean            loaded   = false;
    private boolean            failed   = false;

    private String searchQuery  = "";
    private String activeFilter = "all";
    private int    scrollOffset = 0;

    private static final int NAV_H     = 30;
    private static final int TOOLBAR_H = 28;
    private static final int CARD_H    = 72;
    private static final int CARD_GAP  = 6;
    private static final int PAD       = 12;

    public SkyzSingleplayerScreen(SkyzTitleScreen parent) {
        super(Text.literal("Singleplayer"));
        this.parent = parent;
        this.mc     = MinecraftClient.getInstance();
        loadWorlds();
    }

    private void loadWorlds() {
        try {
            // getLevelList() returns the list of saved worlds in 1.21.11
            var list = mc.getLevelStorage().getLevelList();
            worlds = mc.getLevelStorage().loadSummaries(list).join();
            loaded = true;
        } catch (Exception e) {
            net.skyz.client.SkyzClientMod.LOGGER.warn("[Skyz] World load failed: {}", e.getMessage());
            failed = true;
        }
    }

    @Override
    protected void init() {
        addDrawableChild(SkyzButton.of(width - 82, 7, 74, 18, "\u2190 Back",
                () -> client.setScreen(parent)));
        addDrawableChild(SkyzButton.of(PAD, NAV_H + 4, 130, 20, "+ Create World",
                () -> client.setScreen(new SelectWorldScreen(this))));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, width, height, SkyzTheme.BG1, SkyzTheme.BG2);
        drawNavBar(ctx);
        drawToolbar(ctx, mouseX, mouseY);

        if (failed) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Could not load worlds. Click to open vanilla world selector.",
                    width / 2, height / 2 - 14, SkyzColors.TEXT_MUTED);
            addDrawableChild(SkyzButton.of(width / 2 - 100, height / 2 + 4, 200, 20,
                    "\uD83C\uDF0D Open World Selection",
                    () -> client.setScreen(new SelectWorldScreen(this))));
        } else if (!loaded) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Loading worlds...", width / 2, height / 2, SkyzColors.TEXT_MUTED);
        } else {
            drawWorldList(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);
        parent.toast.render(ctx, width, delta);
    }

    private void drawNavBar(DrawContext ctx) {
        ctx.drawTextWithShadow(textRenderer, "SKYZ", 10, 10, 0xFFF0F8FF);
        int x = 10 + textRenderer.getWidth("SKYZ") + 6;
        ctx.drawTextWithShadow(textRenderer, "/", x, 10, 0x4D8CD2FF);
        x += textRenderer.getWidth("/") + 6;
        ctx.drawTextWithShadow(textRenderer, "SINGLEPLAYER", x, 10, 0x888CD2FF);
        SkyzRenderHelper.drawDivider(ctx, 0, NAV_H, width);
    }

    private void drawToolbar(DrawContext ctx, int mx, int my) {
        int ty = NAV_H + 4;
        int sX = PAD + 138, sW = Math.min(220, width - sX - PAD - 80);
        SkyzRenderHelper.fillPanel(ctx, sX, ty, sW, 20, 0x55091E46, 0x338CD2FF);
        String q = searchQuery.isEmpty() ? "Search worlds..." : searchQuery;
        ctx.drawTextWithShadow(textRenderer, q, sX + 6, ty + 6,
                searchQuery.isEmpty() ? 0x388CD2FF : SkyzColors.TEXT_PRIMARY);

        int fx = sX + sW + 8;
        for (String[] f : new String[][]{{"all","All"},{"survival","Survival"},{"creative","Creative"},{"hardcore","Hardcore"}}) {
            boolean active = activeFilter.equals(f[0]);
            int fw = textRenderer.getWidth(f[1]) + 14;
            SkyzRenderHelper.fillPanel(ctx, fx, ty, fw, 20,
                    active ? 0x553C6E90 : 0x33091E46, active ? 0x998CD2FF : 0x338CD2FF);
            ctx.drawTextWithShadow(textRenderer, f[1], fx + 7, ty + 6,
                    active ? 0xFFFFFFFF : SkyzColors.TEXT_MUTED);
            fx += fw + 4;
        }

        List<LevelSummary> vis = getFiltered();
        String cnt = vis.size() + " world" + (vis.size() == 1 ? "" : "s");
        ctx.drawTextWithShadow(textRenderer, cnt, width - PAD - textRenderer.getWidth(cnt), ty + 6, 0x4D8CD2FF);
    }

    private void drawWorldList(DrawContext ctx, int mx, int my) {
        List<LevelSummary> filtered = getFiltered();
        int listTop = NAV_H + TOOLBAR_H + 8;
        int listH   = height - listTop - 8;
        int cardW   = width - PAD * 2;
        int y       = listTop - scrollOffset;

        ctx.enableScissor(0, listTop, width, height - 8);
        for (LevelSummary w : filtered) {
            if (y + CARD_H >= listTop && y <= listTop + listH)
                drawCard(ctx, w, PAD, y, cardW, CARD_H, mx, my);
            y += CARD_H + CARD_GAP;
        }
        ctx.disableScissor();

        if (filtered.isEmpty() && loaded) {
            int cY = NAV_H + TOOLBAR_H + 8 + (height - NAV_H - TOOLBAR_H - 16) / 2;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    searchQuery.isEmpty() ? "No worlds yet. Create one!" : "No worlds match \"" + searchQuery + "\"",
                    width / 2, cY, SkyzColors.TEXT_MUTED);
        }

        // Scrollbar
        int totalH = filtered.size() * (CARD_H + CARD_GAP);
        if (totalH > listH && filtered.size() > 0) {
            SkyzRenderHelper.fillRect(ctx, width - 4, listTop, 3, listH, 0x1A8CD2FF);
            int th  = Math.max(20, (int)((float) listH / totalH * listH));
            int ty2 = listTop + (int)((float) scrollOffset / Math.max(1, totalH - listH) * (listH - th));
            SkyzRenderHelper.fillRect(ctx, width - 4, ty2, 3, th, 0x558CD2FF);
        }
    }

    private void drawCard(DrawContext ctx, LevelSummary w,
                          int x, int y, int ww, int h, int mx, int my) {
        boolean hov = mx >= x && mx <= x + ww && my >= y && my <= y + h;
        SkyzRenderHelper.fillPanel(ctx, x, y, ww, h,
                hov ? 0x800C2A5A : SkyzColors.CARD_BG, hov ? 0x478CD2FF : SkyzColors.CARD_BORDER);

        // Icon based on game mode
        boolean hardcore = w.isHardcore();
        GameMode gm = w.getGameMode();
        String emoji = hardcore ? "\uD83D\uDC80"
                : gm == GameMode.CREATIVE ? "\uD83C\uDFD7"
                : gm == GameMode.ADVENTURE ? "\uD83D\uDDE1"
                : "\uD83C\uDF10";
        ctx.drawTextWithShadow(textRenderer, emoji, x + 12, y + (h - 8) / 2, 0xFFFFFFFF);

        int tx = x + 30;
        // getDisplayName() returns String in 1.21.11
        String displayName = w.getDisplayName();
        ctx.drawTextWithShadow(textRenderer, displayName, tx, y + 8, SkyzColors.TEXT_PRIMARY);
        ctx.drawTextWithShadow(textRenderer, w.getName(), tx, y + 20, 0x4D8CD2FF);

        // Last played
        String date = w.getLastPlayed() > 0
                ? new SimpleDateFormat("d MMM yyyy").format(new Date(w.getLastPlayed()))
                : "Unknown";
        String mode = getModeName(w);
        ctx.drawTextWithShadow(textRenderer, date + "  \u00B7  " + mode, tx, y + 32, 0x558CD2FF);

        // Mode tag
        int tagCol = hardcore ? 0x99FF4444
                : gm == GameMode.CREATIVE ? 0x994DB4FF
                : 0x9944CC88;
        int tagW = textRenderer.getWidth(mode) + 8;
        SkyzRenderHelper.fillPanel(ctx, tx, y + 46, tagW, 13,
                SkyzColors.withAlpha(tagCol, 0x22), SkyzColors.withAlpha(tagCol, 0x66));
        ctx.drawTextWithShadow(textRenderer, mode, tx + 4, y + 49, tagCol);

        // Buttons
        int bx = x + ww - 122, by = y + (h - 16) / 2;
        drawSmallBtn(ctx, "Play",   bx,      by, 52, 16, true,  mx, my);
        drawSmallBtn(ctx, "Edit",   bx + 56, by, 38, 16, false, mx, my);
        drawSmallBtn(ctx, "Del",    bx + 98, by, 30, 16, false, mx, my);
    }

    private String getModeName(LevelSummary w) {
        if (w.isHardcore()) return "Hardcore";
        GameMode gm = w.getGameMode();
        if (gm == GameMode.CREATIVE)   return "Creative";
        if (gm == GameMode.ADVENTURE)  return "Adventure";
        if (gm == GameMode.SPECTATOR)  return "Spectator";
        return "Survival";
    }

    private void drawSmallBtn(DrawContext ctx, String label, int x, int y, int w, int h,
                              boolean primary, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        SkyzRenderHelper.fillPanel(ctx, x, y, w, h,
                primary ? (hov ? 0x661E6EC8 : 0x441864A0) : (hov ? 0x66143C6E : 0x33091E46),
                primary ? 0x558CD2FF : 0x338CD2FF);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2,
                hov ? 0xFFFFFFFF : SkyzColors.TEXT_MUTED);
    }

    // \u2500\u2500 Input \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        // Filter buttons
        int ty = NAV_H + 4, sX = PAD + 138;
        int sW = Math.min(220, width - sX - PAD - 80);
        int fx = sX + sW + 8;
        for (String[] f : new String[][]{{"all","All"},{"survival","Survival"},{"creative","Creative"},{"hardcore","Hardcore"}}) {
            int fw = textRenderer.getWidth(f[1]) + 14;
            if (mx >= fx && mx <= fx + fw && my >= ty && my <= ty + 20) {
                activeFilter = f[0]; scrollOffset = 0; return true;
            }
            fx += fw + 4;
        }

        // Card buttons
        if (loaded) {
            List<LevelSummary> filtered = getFiltered();
            int listTop = NAV_H + TOOLBAR_H + 8;
            int cardW   = width - PAD * 2;
            int y       = listTop - scrollOffset;
            for (LevelSummary w : filtered) {
                int bx = PAD + cardW - 122, by = y + (CARD_H - 16) / 2;
                // Play
                if (mx >= bx && mx <= bx + 52 && my >= by && my <= by + 16) {
                    openWorld(w); return true;
                }
                // Edit
                if (mx >= bx+56 && mx <= bx+94 && my >= by && my <= by+16) {
                    parent.toast("Edit: " + w.getDisplayName()); return true;
                }
                // Delete
                if (mx >= bx+98 && mx <= bx+128 && my >= by && my <= by+16) {
                    parent.toast("Delete: " + w.getDisplayName()); return true;
                }
                y += CARD_H + CARD_GAP;
            }
        }
        return false;
    }

    private void openWorld(LevelSummary w) {
        if (w.isLocked()) { parent.toast("World is locked!"); return; }
        // IntegratedServerLoader.start(String worldName, Runnable onCancel)
        mc.createIntegratedServerLoader().start(w.getName(), () -> mc.setScreen(this));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        int listH  = height - (NAV_H + TOOLBAR_H + 8) - 8;
        int totalH = getFiltered().size() * (CARD_H + CARD_GAP);
        scrollOffset = (int) Math.max(0, Math.min(Math.max(0, totalH - listH), scrollOffset - v * 16));
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        String s = input.asString();
        if (!s.isEmpty()) { searchQuery += s; scrollOffset = 0; }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == 259 && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollOffset = 0; return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }

    private List<LevelSummary> getFiltered() {
        String q = searchQuery.toLowerCase();
        List<LevelSummary> out = new ArrayList<>();
        for (LevelSummary w : worlds) {
            boolean cat = switch (activeFilter) {
                case "survival" -> !w.isHardcore() && w.getGameMode() == GameMode.SURVIVAL;
                case "creative" -> w.getGameMode() == GameMode.CREATIVE;
                case "hardcore" -> w.isHardcore();
                default         -> true;
            };
            boolean term = q.isEmpty()
                    || w.getDisplayName().toLowerCase().contains(q)
                    || w.getName().toLowerCase().contains(q);
            if (cat && term) out.add(w);
        }
        return out;
    }
}
