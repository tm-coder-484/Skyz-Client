package net.skyz.client.screen;

import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.skyz.client.util.*;
import net.skyz.client.util.SkyzTheme;

import java.util.*;

/**
 * Mods browser using FabricLoader to display actually installed mods.
 *
 * - Real mod list from FabricLoader.getInstance().getAllMods()
 * - "Open Mod Menu" button uses ModMenuApi.createModsScreen() if Mod Menu is installed
 * - "Config" button uses ModMenuApi config screen factories if available, else falls back
 *   to SkyzModConfigScreen for known mods
 * - Iris shaders integration via reflection (no hard compile dep)
 * - Search and category filters on real mod metadata
 */
public class SkyzModsScreen extends Screen {

    private final SkyzTitleScreen parent;

    // Loaded at init from FabricLoader
    private List<ModContainer> allMods = new ArrayList<>();

    private String searchQuery  = "";
    private String activeFilter = "all";
    private int    scrollOffset = 0;

    // Whether Mod Menu is available at runtime
    private static final boolean HAS_MODMENU =
            FabricLoader.getInstance().isModLoaded("modmenu");

    // Whether Iris is available at runtime
    private static final boolean HAS_IRIS =
            FabricLoader.getInstance().isModLoaded("iris");

    private static final int NAV_H     = 30;
    private static final int TOOLBAR_H = 26;
    private static final int CARD_H    = 60;
    private static final int CARD_GAP  = 5;
    private static final int PAD       = 12;

    // Category filters - we derive category from mod env + known mods list
    private static final String[][] FILTERS = {
        {"all","All"},{"perf","Performance"},{"visual","Visual"},{"pvp","PvP"},
        {"util","Utility"},{"lib","Library"}
    };

    // Known mod ID -> category mapping for display purposes
    private static final Map<String, String> MOD_CATEGORIES = new HashMap<>();
    static {
        MOD_CATEGORIES.put("sodium",        "perf");
        MOD_CATEGORIES.put("lithium",       "perf");
        MOD_CATEGORIES.put("ferrite-core",  "perf");
        MOD_CATEGORIES.put("entityculling", "perf");
        MOD_CATEGORIES.put("memoryleakfix", "perf");
        MOD_CATEGORIES.put("krypton",       "perf");
        MOD_CATEGORIES.put("iris",          "visual");
        MOD_CATEGORIES.put("indium",        "visual");
        MOD_CATEGORIES.put("continuity",    "visual");
        MOD_CATEGORIES.put("cit-resewn",    "visual");
        MOD_CATEGORIES.put("modmenu",       "util");
        MOD_CATEGORIES.put("cloth-config",  "lib");
        MOD_CATEGORIES.put("fabric-api",    "lib");
        MOD_CATEGORIES.put("essential",     "util");
        MOD_CATEGORIES.put("betterf3",      "util");
    }

    // Known mod ID -> friendly icon
    private static final Map<String, String> MOD_ICONS = new HashMap<>();
    static {
        MOD_ICONS.put("sodium",        "\u26A1");
        MOD_ICONS.put("lithium",       "\uD83E\uDEA8");
        MOD_ICONS.put("ferrite-core",  "\uD83D\uDD29");
        MOD_ICONS.put("entityculling", "\uD83D\uDC41");
        MOD_ICONS.put("iris",          "\uD83C\uDF05");
        MOD_ICONS.put("indium",        "\uD83D\uDD35");
        MOD_ICONS.put("modmenu",       "\uD83D\uDCCB");
        MOD_ICONS.put("cloth-config",  "\uD83E\uDDF5");
        MOD_ICONS.put("fabric-api",    "\uD83E\uDDF5");
        MOD_ICONS.put("essential",     "\u2B50");
        MOD_ICONS.put("betterf3",      "\u2139");
        MOD_ICONS.put("skyz_client",   "\uD83C\uDF24");
    }

    public SkyzModsScreen(SkyzTitleScreen parent) {
        super(Text.literal("Mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(SkyzButton.of(width - 82, 7, 74, 18, "\u2190 Back",
                () -> client.setScreen(parent)));

        // "Open Mod Menu" button - uses real ModMenuApi if present
        if (HAS_MODMENU) {
            addDrawableChild(SkyzButton.of(PAD, NAV_H + 4, 150, 18,
                    "\uD83D\uDCCB Open Full Mod Menu",
                    () -> client.setScreen(ModMenuApi.createModsScreen(this))));
        }

        // Load real mod list from FabricLoader
        allMods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        // Sort: known mods first, then alphabetical
        allMods.sort(Comparator.comparing(m -> m.getMetadata().getName().toLowerCase()));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, width, height, SkyzTheme.BG1, SkyzTheme.BG2);
        drawNavBar(ctx);
        drawToolbar(ctx, mouseX, mouseY);
        drawModList(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
        parent.toast.render(ctx, width, delta);
    }

    private void drawNavBar(DrawContext ctx) {
        ctx.drawTextWithShadow(textRenderer, "SKYZ", 10, 10, 0xFFF0F8FF);
        int x = 10 + textRenderer.getWidth("SKYZ") + 6;
        ctx.drawTextWithShadow(textRenderer, "/", x, 10, 0x4D8CD2FF);
        x += textRenderer.getWidth("/") + 6;
        ctx.drawTextWithShadow(textRenderer, "MODS", x, 10, 0x888CD2FF);

        // Show Iris status in top right
        if (HAS_IRIS) {
            String irisVer = FabricLoader.getInstance().getModContainer("iris")
                    .map(m -> "Iris " + m.getMetadata().getVersion().getFriendlyString())
                    .orElse("Iris");
            ctx.drawTextWithShadow(textRenderer, "\uD83C\uDF05 " + irisVer,
                    width - PAD - textRenderer.getWidth("\uD83C\uDF05 " + irisVer), 10, 0x668CD2FF);
        }

        SkyzRenderHelper.drawDivider(ctx, 0, NAV_H, width);
    }

    private void drawToolbar(DrawContext ctx, int mx, int my) {
        int ty = NAV_H + 4;
        int btnOffX = HAS_MODMENU ? PAD + 158 : PAD;

        // Filter buttons
        int fx = btnOffX;
        for (String[] f : FILTERS) {
            boolean active = activeFilter.equals(f[0]);
            int fw = textRenderer.getWidth(f[1]) + 14;
            SkyzRenderHelper.fillPanel(ctx, fx, ty, fw, 18,
                    active ? 0x553C6E90 : 0x33091E46,
                    active ? 0x998CD2FF : 0x338CD2FF);
            ctx.drawTextWithShadow(textRenderer, f[1], fx + 7, ty + 5,
                    active ? 0xFFFFFFFF : SkyzColors.TEXT_MUTED);
            fx += fw + 4;
        }

        // Count
        List<ModContainer> vis = getFiltered();
        String cnt = vis.size() + " mod" + (vis.size() == 1 ? "" : "s") + " installed";
        ctx.drawTextWithShadow(textRenderer, cnt,
                width - PAD - textRenderer.getWidth(cnt), ty + 5, 0x4D8CD2FF);
    }

    private void drawModList(DrawContext ctx, int mx, int my) {
        List<ModContainer> mods = getFiltered();
        int listTop = NAV_H + TOOLBAR_H + 6;
        int listH   = height - listTop - 8;
        int cardW   = width - PAD * 2;
        int y       = listTop - scrollOffset;

        ctx.enableScissor(0, listTop, width, height - 8);
        for (ModContainer mod : mods) {
            if (y + CARD_H >= listTop && y <= listTop + listH)
                drawModCard(ctx, mod, PAD, y, cardW, CARD_H, mx, my);
            y += CARD_H + CARD_GAP;
        }
        ctx.disableScissor();

        // Scrollbar
        int totalH = mods.size() * (CARD_H + CARD_GAP);
        if (totalH > listH && mods.size() > 0) {
            SkyzRenderHelper.fillRect(ctx, width - 4, listTop, 3, listH, 0x1A8CD2FF);
            int th  = Math.max(20, (int)((float) listH / totalH * listH));
            int ty2 = listTop + (int)((float) scrollOffset / Math.max(1, totalH - listH) * (listH - th));
            SkyzRenderHelper.fillRect(ctx, width - 4, ty2, 3, th, 0x558CD2FF);
        }
    }

    private void drawModCard(DrawContext ctx, ModContainer mod,
                             int x, int y, int w, int h, int mx, int my) {
        ModMetadata meta = mod.getMetadata();
        String id        = meta.getId();
        boolean hov      = mx >= x && mx <= x + w && my >= y && my <= y + h;

        SkyzRenderHelper.fillPanel(ctx, x, y, w, h,
                hov ? 0x800C2A5A : SkyzColors.CARD_BG,
                hov ? 0x478CD2FF : SkyzColors.CARD_BORDER);

        // Icon
        String icon = MOD_ICONS.getOrDefault(id, "\uD83E\uDDE9");
        ctx.drawTextWithShadow(textRenderer, icon, x + 10, y + (h - 8) / 2, 0xFFFFFFFF);

        // Name + version + id
        int tx = x + 28;
        String name    = meta.getName();
        String version = meta.getVersion().getFriendlyString();
        ctx.drawTextWithShadow(textRenderer, name, tx, y + 6, SkyzColors.TEXT_PRIMARY);
        ctx.drawTextWithShadow(textRenderer, "v" + version + "  \u00B7  " + id, tx, y + 17, 0x4D8CD2FF);

        // Description (truncated)
        String desc = meta.getDescription();
        if (desc != null && !desc.isEmpty()) {
            int maxW = w - 200;
            while (textRenderer.getWidth(desc) > maxW && desc.length() > 4)
                desc = desc.substring(0, desc.length() - 4) + "...";
            ctx.drawTextWithShadow(textRenderer, desc, tx, y + 29, 0x778CD2FF);
        } else {
            ctx.drawTextWithShadow(textRenderer, "No description.", tx, y + 29, 0x338CD2FF);
        }

        // Category tag
        String cat    = getCategoryFor(id, meta);
        String catLbl = switch (cat) {
            case "perf"   -> "Performance";
            case "visual" -> "Visual";
            case "pvp"    -> "PvP";
            case "lib"    -> "Library";
            default       -> "Utility";
        };
        int tagCol = switch (cat) {
            case "perf"   -> 0x9950DCA0;
            case "visual" -> 0x99C896FF;
            case "pvp"    -> 0x99FF6464;
            case "lib"    -> 0x99999999;
            default       -> 0x99FFD050;
        };
        int tagW = textRenderer.getWidth(catLbl) + 8;
        int tagX = x + w - tagW - 110;
        SkyzRenderHelper.fillPanel(ctx, tagX, y + (h - 14) / 2, tagW, 14,
                SkyzColors.withAlpha(tagCol, 0x22), SkyzColors.withAlpha(tagCol, 0x55));
        ctx.drawTextWithShadow(textRenderer, catLbl, tagX + 4, y + (h - 14) / 2 + 3, tagCol);

        // Config button
        int btnX = x + w - 100, btnY = y + (h - 16) / 2;
        boolean hasConfig = hasConfigScreen(id);
        boolean cfgHov    = mx >= btnX && mx <= btnX + 46 && my >= btnY && my <= btnY + 16;
        SkyzRenderHelper.fillPanel(ctx, btnX, btnY, 46, 16,
                hasConfig ? (cfgHov ? 0x66143C6E : 0x33091E46) : 0x1A091E46,
                hasConfig ? 0x3A8CD2FF : 0x1A8CD2FF);
        ctx.drawCenteredTextWithShadow(textRenderer, "\u2699 Config", btnX + 23, btnY + 4,
                hasConfig ? (cfgHov ? SkyzColors.TEXT_WHITE : SkyzColors.TEXT_MUTED) : 0x338CD2FF);

        // Env badge
        String env = meta.getEnvironment().toString();
        if (env.equalsIgnoreCase("client")) {
            ctx.drawTextWithShadow(textRenderer, "[C]", x + w - 52, y + 6, 0x558CD2FF);
        }
    }

    // \u2500\u2500 Config screen resolution \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /** Returns true if we can open a real config screen for this mod. */
    private boolean hasConfigScreen(String modId) {
        // ModMenu: try reflection to check for a registered config screen
        if (HAS_MODMENU) {
            try {
                Class<?> mmClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
                java.lang.reflect.Method m = mmClass.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screen.Screen.class);
                Object result = m.invoke(null, modId, this);
                if (result != null) return true;
            } catch (Exception ignored) {}
        }
        // Fallback: bundled known mods always have Skyz config screen
        return List.of("sodium","iris","modmenu","lithium","ferrite-core","entityculling","essential").contains(modId);
    }

    /** Open config for a mod. Tries ModMenu registry first, then Skyz fallback. */
    private void openConfigFor(ModContainer mod) {
        String id   = mod.getMetadata().getId();
        String name = mod.getMetadata().getName();
        String ver  = mod.getMetadata().getVersion().getFriendlyString();

        // Try ModMenu's internal getConfigScreen via reflection
        if (HAS_MODMENU) {
            try {
                Class<?> mmClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
                java.lang.reflect.Method m = mmClass.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screen.Screen.class);
                net.minecraft.client.gui.screen.Screen cfg =
                    (net.minecraft.client.gui.screen.Screen) m.invoke(null, id, this);
                if (cfg != null) { client.setScreen(cfg); return; }
            } catch (Exception ignored) {}
        }

        // Fall through to Skyz config screen for any mod we know, or generic for others
        client.setScreen(new SkyzModConfigScreen(this, parent, name, ver, true));
    }

    // \u2500\u2500 Input \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        List<ModContainer> mods = getFiltered();
        int listH  = height - (NAV_H + TOOLBAR_H + 6) - 8;
        int totalH = mods.size() * (CARD_H + CARD_GAP);
        scrollOffset = (int) Math.max(0, Math.min(Math.max(0, totalH - listH), scrollOffset - v * 16));
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        // Filter buttons
        int ty      = NAV_H + 4;
        int btnOffX = HAS_MODMENU ? PAD + 158 : PAD;
        int fx      = btnOffX;
        for (String[] f : FILTERS) {
            int fw = textRenderer.getWidth(f[1]) + 14;
            if (mx >= fx && mx <= fx + fw && my >= ty && my <= ty + 18) {
                activeFilter = f[0]; scrollOffset = 0; return true;
            }
            fx += fw + 4;
        }

        // Card config buttons
        List<ModContainer> mods = getFiltered();
        int listTop = NAV_H + TOOLBAR_H + 6;
        int cardW   = width - PAD * 2;
        int y       = listTop - scrollOffset;
        for (ModContainer mod : mods) {
            int btnX = PAD + cardW - 100, btnY = y + (CARD_H - 16) / 2;
            if (mx >= btnX && mx <= btnX + 46 && my >= btnY && my <= btnY + 16) {
                openConfigFor(mod); return true;
            }
            y += CARD_H + CARD_GAP;
        }
        return false;
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

    // \u2500\u2500 Helpers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private String getCategoryFor(String id, ModMetadata meta) {
        if (MOD_CATEGORIES.containsKey(id)) return MOD_CATEGORIES.get(id);
        // Infer from provides/dependencies
        for (var dep : meta.getDependencies()) {
            if (dep.getModId().equals("sodium")) return "perf";
        }
        return "util";
    }

    private static final java.util.Set<String> SYSTEM_MOD_PREFIXES = java.util.Set.of(
        "minecraft", "java", "fabricloader", "mixinextras", "fabric-", "com_", "io_",
        "org_", "net_", "asm", "authlib", "brigadier", "datafixerupper"
    );

    private boolean isSystemMod(String id) {
        if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")) return true;
        // Filter low-level library/API mods from the main list
        for (String prefix : SYSTEM_MOD_PREFIXES) {
            if (id.startsWith(prefix)) return true;
        }
        // Filter by environment - but show client mods
        return false;
    }

    private List<ModContainer> getFiltered() {
        String q = searchQuery.toLowerCase();
        List<ModContainer> out = new ArrayList<>();
        for (ModContainer mod : allMods) {
            ModMetadata meta = mod.getMetadata();
            String id        = meta.getId();

            // Skip internal/system mods that users don't care about
            if (isSystemMod(id)) continue;

            // Category filter
            if (!activeFilter.equals("all")) {
                String cat = getCategoryFor(id, meta);
                if (!cat.equals(activeFilter)) continue;
            }

            // Search filter
            if (!q.isEmpty()) {
                String name = meta.getName().toLowerCase();
                String desc = meta.getDescription() != null ? meta.getDescription().toLowerCase() : "";
                if (!name.contains(q) && !id.contains(q) && !desc.contains(q)) continue;
            }

            out.add(mod);
        }
        return out;
    }
}
