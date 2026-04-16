package net.skyz.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.skyz.client.util.*;
import net.skyz.client.util.SkyzTheme;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Shaders screen integrating with Iris.
 *
 * - Scans .minecraft/shaderpacks/ for real installed shader packs
 * - If Iris is present: "Open Iris Shaders" button opens ShaderPackScreen via reflection
 *   (Iris has no stable public API for screen opening; we use reflection as recommended
 *    by community practice)
 * - Shows enable/disable toggle via IrisApi.v0 if available
 * - Falls back to a styled list with instructions if Iris isn't installed
 */
public class SkyzShadersScreen extends Screen {

    private final SkyzTitleScreen parent;

    private static final boolean HAS_IRIS =
            FabricLoader.getInstance().isModLoaded("iris");

    // Real shader packs found on disk
    private final List<ShaderPack> packs = new ArrayList<>();
    private int selectedPack = 0;
    private int scrollOffset = 0;

    record ShaderPack(String name, String fileName, boolean isEnabled) {}

    private static final int NAV_H  = 30;
    private static final int PAD    = 12;
    private static final int LIST_W = 260;
    private static final int ROW_H  = 48;

    public SkyzShadersScreen(SkyzTitleScreen parent) {
        super(Text.literal("Shaders"));
        this.parent = parent;
        // loadShaderPacks() called in init() on the render thread
    }

    private void loadShaderPacks() {
        packs.clear();
        // Get the active shaderpack name from Iris if possible
        String activeName = getActiveShaderpackName();

        // Scan shaderpacks folder
        File shaderpacksDir = new File(net.minecraft.client.MinecraftClient.getInstance()
                .runDirectory, "shaderpacks");
        if (shaderpacksDir.exists() && shaderpacksDir.isDirectory()) {
            File[] files = shaderpacksDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    if (n.endsWith(".zip") || n.endsWith(".txt") || f.isDirectory()) {
                        String displayName = n.replace(".zip", "").replace(".txt", "");
                        boolean active = displayName.equals(activeName) || n.equals(activeName);
                        packs.add(new ShaderPack(displayName, n, active));
                    }
                }
            }
        }

        // Always add "Off (Vanilla)" at the top
        boolean noneActive = activeName == null || activeName.isEmpty() || activeName.equals("(off)");
        packs.add(0, new ShaderPack("No Shaders (Vanilla)", "(off)", noneActive));
    }

    /** Get the currently loaded shaderpack name from Iris via reflection. */
    private String getActiveShaderpackName() {
        if (!HAS_IRIS) return null;
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method   getConfig = irisClass.getMethod("getIrisConfig");
            Object   config    = getConfig.invoke(null);
            Method   getName   = config.getClass().getMethod("getShaderPackName");
            Object   result    = getName.invoke(config);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Open Iris ShaderPackScreen via reflection. */
    private boolean openIrisShaderScreen() {
        if (!HAS_IRIS) return false;
        try {
            Class<?> screenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            java.lang.reflect.Constructor<?> ctor = screenClass.getConstructor(Screen.class);
            Screen shaderScreen = (Screen) ctor.newInstance(this);
            net.minecraft.client.MinecraftClient.getInstance().setScreen(shaderScreen);
            return true;
        } catch (Exception e) {
            net.skyz.client.SkyzClientMod.LOGGER.warn("[Skyz Client] Could not open Iris screen: {}", e.getMessage());
            return false;
        }
    }

    /** Apply a shader pack by name via Iris API reflection. */
    private void applyShaderPack(String packName) {
        if (!HAS_IRIS) { parent.toast("Install Iris Shaders to use shader packs!"); return; }
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method   getConfig = irisClass.getMethod("getIrisConfig");
            Object   config    = getConfig.invoke(null);

            if (packName.equals("(off)")) {
                Method setShadersEnabled = null;
                try {
                    Class<?> apiClass = Class.forName("net.irisshaders.iris.apiimpl.IrisApiV0ConfigImpl");
                    setShadersEnabled = apiClass.getMethod("setShadersEnabledAndApply", boolean.class);
                    Object apiInst = irisClass.getField("irisConfig").get(null);
                    setShadersEnabled.invoke(config, false);
                } catch (Exception ignored) {}
            } else {
                Method setShaderPackName = config.getClass().getMethod("setShaderPackName", String.class);
                setShaderPackName.invoke(config, packName);
                Method save = config.getClass().getMethod("save");
                save.invoke(config);
                // Reload
                Method reload = irisClass.getMethod("reload");
                reload.invoke(null);
            }
            loadShaderPacks(); // refresh list
            parent.toast("Applied: " + (packName.equals("(off)") ? "No Shaders" : packName));
        } catch (Exception e) {
            parent.toast("Could not apply shader: " + e.getMessage());
            // Fall back to opening the full Iris screen
            openIrisShaderScreen();
        }
    }

    private boolean loading = true;

    @Override
    protected void init() {
        addDrawableChild(SkyzButton.of(width - 82, 7, 74, 18, "\u2190 Back",
                () -> client.setScreen(parent)));

        if (HAS_IRIS) {
            addDrawableChild(SkyzButton.of(PAD, NAV_H + 4, 180, 18,
                    "\uD83C\uDF05 Open Full Iris Shaders Screen",
                    () -> {
                        if (!openIrisShaderScreen())
                            parent.toast("Could not open Iris screen.");
                    }));
        }

        addDrawableChild(SkyzButton.of(HAS_IRIS ? PAD + 188 : PAD, NAV_H + 4, 70, 18,
                "\u21BB Refresh",
                () -> {
                    loading = true;
                    java.util.concurrent.CompletableFuture.runAsync(this::loadShaderPacks)
                        .thenRun(() -> { loading = false; parent.toast("Refreshed!"); });
                }));

        // Load shader packs asynchronously so we don't freeze the render thread
        loading = true;
        java.util.concurrent.CompletableFuture.runAsync(this::loadShaderPacks)
            .thenRun(() -> loading = false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, width, height, SkyzTheme.BG1, SkyzTheme.BG2);
        drawNavBar(ctx);
        if (loading) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Loading shader packs...",
                    width/2, height/2, SkyzColors.TEXT_MUTED);
        } else {
            drawShaderList(ctx, mouseX, mouseY);
            drawInfoPanel(ctx, mouseX, mouseY);
        }
        super.render(ctx, mouseX, mouseY, delta);
        parent.toast.render(ctx, width, delta);
    }

    private void drawNavBar(DrawContext ctx) {
        ctx.drawTextWithShadow(textRenderer, "SKYZ", 10, 10, 0xFFF0F8FF);
        int x = 10 + textRenderer.getWidth("SKYZ") + 6;
        ctx.drawTextWithShadow(textRenderer, "/", x, 10, 0x4D8CD2FF);
        x += textRenderer.getWidth("/") + 6;
        ctx.drawTextWithShadow(textRenderer, "SHADERS", x, 10, 0x888CD2FF);

        // Iris status badge
        String badge = HAS_IRIS
                ? "\uD83C\uDF05 Iris Active"
                : "\u26A0 Iris not installed";
        int badgeCol = HAS_IRIS ? 0x668CD2FF : 0x99FF9944;
        ctx.drawTextWithShadow(textRenderer, badge,
                width - PAD - textRenderer.getWidth(badge), 10, badgeCol);

        SkyzRenderHelper.drawDivider(ctx, 0, NAV_H, width);
    }

    private void drawShaderList(DrawContext ctx, int mx, int my) {
        int x = PAD, y = NAV_H + 30, w = LIST_W, h = height - NAV_H - 30;
        SkyzRenderHelper.fillPanel(ctx, x, y, w, h, 0x44081830, 0x228CD2FF);

        ctx.drawTextWithShadow(textRenderer, "Shader Packs (" + (packs.size() - 1) + " found)",
                x + 10, y + 10, SkyzColors.TEXT_PRIMARY);
        if (!HAS_IRIS) {
            ctx.drawTextWithShadow(textRenderer, "Install Iris to enable", x + 10, y + 22, 0x99FF9944);
        }
        SkyzRenderHelper.fillRect(ctx, x + 1, y + (HAS_IRIS ? 24 : 34), w - 2, 1, 0x228CD2FF);

        int rowY  = y + (HAS_IRIS ? 30 : 40) - scrollOffset;
        int listH = h - (HAS_IRIS ? 30 : 40) - 10;
        ctx.enableScissor(x, y + (HAS_IRIS ? 30 : 40), x + w, y + h - 10);

        for (int i = 0; i < packs.size(); i++) {
            ShaderPack p    = packs.get(i);
            boolean    sel  = i == selectedPack;
            boolean    hov  = mx >= x + 1 && mx <= x + w - 1 && my >= rowY && my <= rowY + ROW_H;

            if (sel)       SkyzRenderHelper.fillRect(ctx, x + 1, rowY, w - 2, ROW_H, 0x44143C6E);
            else if (hov)  SkyzRenderHelper.fillRect(ctx, x + 1, rowY, w - 2, ROW_H, 0x22143C6E);

            // Active indicator
            if (p.isEnabled())
                SkyzRenderHelper.fillRect(ctx, x + 1, rowY, 3, ROW_H, 0xCC8CD2FF);

            String icon = p.fileName().equals("(off)") ? "\uD83D\uDFEB" : "\uD83C\uDF05";
            ctx.drawTextWithShadow(textRenderer, icon, x + 10, rowY + 10, 0xFFFFFFFF);

            String name = p.name();
            while (textRenderer.getWidth(name) > w - 50 && name.length() > 3)
                name = name.substring(0, name.length() - 3) + "...";
            ctx.drawTextWithShadow(textRenderer, name, x + 28, rowY + 8,
                    p.isEnabled() ? 0xFF8CD2FF : SkyzColors.TEXT_PRIMARY);
            ctx.drawTextWithShadow(textRenderer, p.isEnabled() ? "\u2726 Active" : p.fileName(),
                    x + 28, rowY + 22, p.isEnabled() ? 0x558CD2FF : 0x338CD2FF);

            // Apply button on hover / selected
            if (hov || sel) {
                int bx = x + w - 52, by = rowY + ROW_H / 2 - 8;
                boolean bHov = mx >= bx && mx <= bx + 46 && my >= by && my <= by + 16;
                SkyzRenderHelper.fillPanel(ctx, bx, by, 46, 16,
                        bHov ? 0x661E6EC8 : 0x441864A0, 0x558CD2FF);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        p.isEnabled() ? "Active" : "Apply",
                        bx + 23, by + 4,
                        p.isEnabled() ? 0x8890D2FF : (bHov ? 0xFFFFFFFF : 0xCCDDFFFF));
            }

            rowY += ROW_H;
        }
        ctx.disableScissor();

        // Shaderpack folder hint at bottom
        ctx.drawTextWithShadow(textRenderer, "Drop .zip into shaderpacks/ folder",
                x + 8, y + h - 16, 0x338CD2FF);
    }

    private void drawInfoPanel(DrawContext ctx, int mx, int my) {
        int x = PAD + LIST_W + 10;
        int y = NAV_H + 30;
        int w = width - x - PAD;
        int h = height - NAV_H - 30;

        SkyzRenderHelper.fillPanel(ctx, x, y, w, h, 0x44081830, 0x228CD2FF);

        ShaderPack sel = packs.isEmpty() ? null : packs.get(selectedPack);
        ctx.drawTextWithShadow(textRenderer, "Pack Info", x + 10, y + 10, SkyzColors.TEXT_PRIMARY);
        SkyzRenderHelper.fillRect(ctx, x + 1, y + 24, w - 2, 1, 0x228CD2FF);

        int iy = y + 32;
        if (sel != null) {
            ctx.drawTextWithShadow(textRenderer, sel.name(), x + 10, iy, 0xFF8CD2FF); iy += 14;
            ctx.drawTextWithShadow(textRenderer, "File: " + sel.fileName(), x + 10, iy, 0x4D8CD2FF); iy += 14;
            ctx.drawTextWithShadow(textRenderer, "Status: " + (sel.isEnabled() ? "\u2726 Active" : "Inactive"),
                    x + 10, iy, sel.isEnabled() ? 0x8890D2FF : SkyzColors.TEXT_MUTED); iy += 22;
        }

        if (!HAS_IRIS) {
            iy += 10;
            SkyzRenderHelper.fillPanel(ctx, x + 10, iy, w - 20, 60, 0x33FF9900, 0x55FF9900);
            ctx.drawTextWithShadow(textRenderer, "\u26A0 Iris Shaders not installed", x + 16, iy + 8, 0xFFFF9944);
            ctx.drawTextWithShadow(textRenderer, "To use shader packs, install:", x + 16, iy + 20, SkyzColors.TEXT_MUTED);
            ctx.drawTextWithShadow(textRenderer, "Iris + Sodium from modrinth.com", x + 16, iy + 32, 0x668CD2FF);
            ctx.drawTextWithShadow(textRenderer, "modrinth.com/mod/iris", x + 16, iy + 44, 0x558CD2FF);
        } else {
            iy += 10;
            ctx.drawTextWithShadow(textRenderer, "Shaderpacks folder:", x + 10, iy, SkyzColors.TEXT_MUTED); iy += 12;
            String path = new File(net.minecraft.client.MinecraftClient.getInstance().runDirectory, "shaderpacks").getAbsolutePath();
            while (textRenderer.getWidth(path) > w - 20 && path.length() > 10)
                path = "..." + path.substring(Math.min(path.length(), path.length() - (w - 20) / 5));
            ctx.drawTextWithShadow(textRenderer, path, x + 10, iy, 0x338CD2FF); iy += 22;

            ctx.drawTextWithShadow(textRenderer, "Quick tips:", x + 10, iy, SkyzColors.TEXT_MUTED); iy += 14;
            ctx.drawTextWithShadow(textRenderer, "\u00B7 Drop .zip files into shaderpacks/", x + 16, iy, 0x558CD2FF); iy += 12;
            ctx.drawTextWithShadow(textRenderer, "\u00B7 Use 'Refresh' to reload the list", x + 16, iy, 0x558CD2FF); iy += 12;
            ctx.drawTextWithShadow(textRenderer, "\u00B7 'Open Full Iris Screen' for", x + 16, iy, 0x558CD2FF); iy += 12;
            ctx.drawTextWithShadow(textRenderer, "   full shader settings", x + 16, iy, 0x558CD2FF);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        int lx = PAD, ly = NAV_H + 30 + (HAS_IRIS ? 30 : 40);
        int rowY = ly - scrollOffset;
        for (int i = 0; i < packs.size(); i++) {
            ShaderPack p = packs.get(i);
            if (my >= rowY && my <= rowY + ROW_H && mx >= lx && mx <= lx + LIST_W) {
                selectedPack = i;
                // Check if Apply button clicked
                int bx = lx + LIST_W - 52, by = rowY + ROW_H / 2 - 8;
                if (mx >= bx && mx <= bx + 46 && my >= by && my <= by + 16 && !p.isEnabled()) {
                    applyShaderPack(p.fileName());
                }
                return true;
            }
            rowY += ROW_H;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        int listH  = height - NAV_H - 30 - (HAS_IRIS ? 30 : 40) - 10;
        int totalH = packs.size() * ROW_H;
        scrollOffset = (int) Math.max(0, Math.min(Math.max(0, totalH - listH), scrollOffset - v * 16));
        return true;
    }

    @Override public boolean shouldPause() { return false; }
}
