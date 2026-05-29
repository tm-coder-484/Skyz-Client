package net.skyz.client.screen;

import com.terraformersmc.modmenu.api.ModMenuApi;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skyz Mods screen — owo-lib edition (Phase 2b port).
 *
 * Layout structure: see assets/skyz_client/owo_ui/mods.xml.
 *
 * Cards are built programmatically — same pattern as Multiplayer. Each card
 * is a horizontal flow with a {@link SkyzSurface#CARD} backdrop holding:
 * icon-box (mod emoji), info column (name + version·id + description),
 * category pill, and a CONFIG button (active only if the mod publishes a
 * ModMenu config screen or is in the curated fallback list).
 *
 * Filtering / search rebuilds the list — set is small (typically 10–60
 * mods) and rebuilds are cheap.
 */
public class SkyzModsScreen extends BaseUIModelScreen<FlowLayout> {

    private static final boolean HAS_MODMENU = FabricLoader.getInstance().isModLoaded("modmenu");

    private static final String[][] FILTER_BUTTONS = {
            {"all",    "btn-filter-all"},
            {"perf",   "btn-filter-perf"},
            {"visual", "btn-filter-visual"},
            {"pvp",    "btn-filter-pvp"},
            {"util",   "btn-filter-util"},
            {"lib",    "btn-filter-lib"},
    };

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

    private static final Map<String, String> MOD_ICONS = new HashMap<>();
    static {
        MOD_ICONS.put("sodium",        "⚡");
        MOD_ICONS.put("lithium",       "🪨");
        MOD_ICONS.put("ferrite-core",  "🔩");
        MOD_ICONS.put("entityculling", "👁");
        MOD_ICONS.put("iris",          "🌅");
        MOD_ICONS.put("indium",        "🔵");
        MOD_ICONS.put("modmenu",       "📋");
        MOD_ICONS.put("cloth-config",  "🧵");
        MOD_ICONS.put("fabric-api",    "🧵");
        MOD_ICONS.put("essential",     "⭐");
        MOD_ICONS.put("betterf3",      "ℹ");
        MOD_ICONS.put("skyz_client",   "🌤");
    }

    private static final Set<String> SYSTEM_MOD_PREFIXES = Set.of(
            "minecraft", "java", "fabricloader", "mixinextras", "fabric-",
            "com_", "io_", "org_", "net_", "asm", "authlib", "brigadier",
            "datafixerupper");

    private static final Set<String> KNOWN_CONFIGURABLE = Set.of(
            "sodium", "iris", "modmenu", "lithium", "ferrite-core",
            "entityculling", "essential");

    /** Maps category id -> (label, hex base RGB) for category pills. */
    private static final Map<String, int[]> CAT_RGB = Map.of(
            "perf",   new int[]{ 80, 220, 160},
            "visual", new int[]{200, 150, 255},
            "pvp",    new int[]{255, 100, 100},
            "lib",    new int[]{170, 170, 170},
            "hud",    new int[]{100, 200, 255},
            "util",   new int[]{255, 200,  80}
    );
    private static final Map<String, String> CAT_LABEL = Map.of(
            "perf",   "PERFORMANCE",
            "visual", "VISUAL",
            "pvp",    "PVP",
            "lib",    "LIBRARY",
            "hud",    "HUD",
            "util",   "UTILITY"
    );

    private final SkyzTitleScreen parent;
    private final List<ModContainer> allMods;

    private FlowLayout       modsListContainer;
    private LabelComponent   countLabel;
    private TextBoxComponent searchBox;
    private final Map<String, ButtonComponent> filterBtns = new HashMap<>();

    private String searchQuery  = "";
    private String activeFilter = "all";

    public SkyzModsScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "mods"));
        this.parent  = parent;
        this.allMods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        this.allMods.sort(Comparator.comparing(m -> m.getMetadata().getName().toLowerCase()));
    }

    // ── Build ────────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        wire(root, "btn-back",
                () -> client.setScreen(parent),
                SkyzButtonRenderer.NAV_BACK);

        // ModMenu hand-off — disabled if ModMenu isn't installed.
        ButtonComponent modmenuBtn = root.childById(ButtonComponent.class, "btn-modmenu");
        if (modmenuBtn != null) {
            if (HAS_MODMENU) {
                modmenuBtn.renderer(SkyzButtonRenderer.NAV_BACK);
                modmenuBtn.onPress(b ->
                        client.setScreen(ModMenuApi.createModsScreen(this)));
            } else {
                modmenuBtn.active(false);
                modmenuBtn.renderer(SkyzButtonRenderer.NAV_BACK);
            }
        }

        // Filter pills.
        for (String[] f : FILTER_BUTTONS) {
            String filterId = f[0];
            String btnId    = f[1];
            ButtonComponent btn = root.childById(ButtonComponent.class, btnId);
            if (btn == null) continue;
            filterBtns.put(filterId, btn);
            btn.onPress(b -> {
                activeFilter = filterId;
                refreshFilters();
                rebuildModsList();
            });
        }
        refreshFilters();

        // Search wrapper styling + text-box.
        FlowLayout searchWrapper = root.childById(FlowLayout.class, "search-wrapper");
        if (searchWrapper != null) searchWrapper.surface(SkyzSurface.PILL_INPUT);

        searchBox = root.childById(TextBoxComponent.class, "tb-search");
        if (searchBox != null) {
            searchBox.setDrawsBackground(false);
            searchBox.onChanged().subscribe(v -> {
                searchQuery = v;
                rebuildModsList();
            });
        }

        // List container + count label.
        countLabel        = root.childById(LabelComponent.class, "lbl-count");
        modsListContainer = root.childById(FlowLayout.class, "mods-list");

        // Style the wrapping scroll container — flat blue scrollbar to match
        // the rest of the Skyz screens. Without this the list rendered as a
        // single tall column off the bottom of the screen with no way to
        // reach the lower entries.
        ScrollContainer<?> scroll = root.childById(ScrollContainer.class, "scroll-mods-list");
        if (scroll != null) {
            scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xCC8CD2FF)));
        }

        SkyzClientMod.LOGGER.info(
                "[Skyz] Mods wirings: search={}, count={}, mods-list={}, total-mods={}",
                searchBox != null, countLabel != null, modsListContainer != null,
                allMods.size());

        rebuildModsList();
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Mods: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    private void refreshFilters() {
        for (Map.Entry<String, ButtonComponent> e : filterBtns.entrySet()) {
            e.getValue().renderer(activeFilter.equals(e.getKey())
                    ? SkyzButtonRenderer.DEFAULT
                    : SkyzButtonRenderer.NAV_BACK);
        }
    }

    // ── Mods list ────────────────────────────────────────────────────────
    private void rebuildModsList() {
        if (modsListContainer == null) return;
        modsListContainer.clearChildren();

        List<ModContainer> visible = getFiltered();
        if (countLabel != null) {
            countLabel.text(Component.literal(visible.size() + " mod"
                    + (visible.size() == 1 ? "" : "s")));
        }

        if (visible.isEmpty()) {
            LabelComponent empty = UIComponents
                    .label(Component.literal("No mods match \"" + searchQuery + "\"."))
                    .color(io.wispforest.owo.ui.core.Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER);
            empty.horizontalSizing(Sizing.fill(100));
            empty.margins(Insets.vertical(20));
            modsListContainer.child(empty);
            return;
        }

        for (ModContainer mod : visible) {
            modsListContainer.child(buildCard(mod));
        }
    }

    private FlowLayout buildCard(ModContainer mod) {
        ModMetadata meta = mod.getMetadata();
        String id   = meta.getId();
        String name = meta.getName();
        String ver  = meta.getVersion().getFriendlyString();
        String desc = meta.getDescription();
        if (desc == null || desc.isEmpty()) desc = "No description.";
        String cat  = getCategoryFor(id, meta);
        String icon = MOD_ICONS.getOrDefault(id, "🧩");

        // Card root.
        FlowLayout card = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(58));
        card.gap(10);
        card.padding(Insets.of(8, 8, 12, 12));
        card.verticalAlignment(VerticalAlignment.CENTER);
        card.surface(SkyzSurface.CARD);

        // Icon box (square panel with emoji).
        FlowLayout iconBox = UIContainers.horizontalFlow(Sizing.fixed(32), Sizing.fixed(32));
        iconBox.horizontalAlignment(HorizontalAlignment.CENTER);
        iconBox.verticalAlignment(VerticalAlignment.CENTER);
        iconBox.surface(SkyzSurface.PILL_INPUT);
        LabelComponent iconLbl = UIComponents
                .label(Component.literal(icon))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(0xFFFFFFFF));
        iconBox.child(iconLbl);
        card.child(iconBox);

        // Info column.
        FlowLayout info = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        info.gap(2);

        LabelComponent nameLbl = UIComponents
                .label(Component.literal(name))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(SkyzColors.TEXT_PRIMARY));
        info.child(nameLbl);

        LabelComponent metaLbl = UIComponents
                .label(Component.literal("v" + ver + "  ·  " + id))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(0xFF4D8CD2));
        info.child(metaLbl);

        // Trim long descriptions to one rendered line (owo's label can wrap
        // but we want consistent card heights here).
        String descTrim = desc.replace('\n', ' ');
        if (descTrim.length() > 90) descTrim = descTrim.substring(0, 87) + "...";
        LabelComponent descLbl = UIComponents
                .label(Component.literal(descTrim))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(0xCC93B6E6));
        descLbl.horizontalSizing(Sizing.fill(100));
        info.child(descLbl);

        card.child(info);

        // Category pill.
        card.child(buildCatPill(cat));

        // Config button — disabled if the mod publishes nothing configurable.
        boolean hasConfig = HAS_MODMENU || KNOWN_CONFIGURABLE.contains(id);
        ButtonComponent cfg = UIComponents
                .button(Component.literal("⚙  CONFIG"), b -> openConfigFor(mod))
                .renderer(hasConfig ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        cfg.active(hasConfig);
        cfg.horizontalSizing(Sizing.fixed(72));
        cfg.verticalSizing(Sizing.fixed(20));
        card.child(cfg);

        return card;
    }

    /**
     * Category pill ({@code <flow-layout>} surfaced with a tinted rounded
     * panel, label centred). Colour comes from {@link #CAT_RGB} so each
     * category reads at a glance — perf=green, visual=violet, pvp=red, etc.
     */
    private FlowLayout buildCatPill(String cat) {
        int[] rgb    = CAT_RGB.getOrDefault(cat, CAT_RGB.get("util"));
        String label = CAT_LABEL.getOrDefault(cat, "UTILITY");
        int rgbPack  = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        int bg       = 0x33000000 | rgbPack;
        int border   = 0x66000000 | rgbPack;
        int textCol  = 0xCC000000 | rgbPack;

        FlowLayout pill = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(14));
        pill.padding(Insets.horizontal(6));
        pill.verticalAlignment(VerticalAlignment.CENTER);
        pill.surface((ctx, comp) -> {
            int r = 3;
            SkyzRenderHelper.fillRoundedRect(ctx, comp.x(), comp.y(),
                    comp.width(), comp.height(), r, bg);
            SkyzRenderHelper.drawRoundedBorder(ctx, comp.x(), comp.y(),
                    comp.width(), comp.height(), r, border);
        });
        pill.child(UIComponents
                .label(Component.literal(label))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(textCol)));
        return pill;
    }

    /**
     * Resolves a config screen for the given mod via ModMenu reflection
     * (so we don't hard-depend on ModMenu's API at compile time) and
     * falls back to {@link SkyzModConfigScreen} placeholder for known
     * configurable mods we don't have a path to.
     */
    private void openConfigFor(ModContainer mod) {
        String id   = mod.getMetadata().getId();
        String name = mod.getMetadata().getName();
        String ver  = mod.getMetadata().getVersion().getFriendlyString();

        // Prefer the SkyzOptionsScreen mod-specific helpers — they do
        // ModMenu first AND fall back to direct reflection / known class
        // paths. ModMenu's getConfigScreen on its own returns null for
        // Sodium on some setups (factory not exposed through that method),
        // which is what was making the CONFIG button drop to the
        // placeholder screen instead of opening Sodium proper.
        if ("sodium".equals(id) && SkyzOptionsScreen.tryOpenSodiumOptions(this)) return;
        if ("iris".equals(id)    && SkyzOptionsScreen.tryOpenIrisOptions(this))    return;
        if ("distanthorizons".equals(id)
                && SkyzOptionsScreen.tryOpenDistantHorizonsOptions(this)) return;

        // Generic ModMenu fall-through for everything else.
        if (HAS_MODMENU) {
            try {
                Class<?> mmClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
                // Try both known method names (renamed across versions).
                java.lang.reflect.Method m = null;
                try { m = mmClass.getMethod("getConfigScreen", String.class, Screen.class); }
                catch (NoSuchMethodException ignored) {}
                if (m == null) {
                    try { m = mmClass.getMethod("createConfigScreen", String.class, Screen.class); }
                    catch (NoSuchMethodException ignored) {}
                }
                if (m != null) {
                    Screen cfg = (Screen) m.invoke(null, id, this);
                    if (cfg != null) { client.setScreen(cfg); return; }
                }
            } catch (Exception ignored) {}
        }
        client.setScreen(new SkyzModConfigScreen(this, parent, name, ver, true));
    }

    // ── Filtering ────────────────────────────────────────────────────────
    private List<ModContainer> getFiltered() {
        String q = searchQuery.toLowerCase();
        List<ModContainer> out = new ArrayList<>();
        for (ModContainer mod : allMods) {
            ModMetadata meta = mod.getMetadata();
            String id        = meta.getId();
            if (isSystemMod(id)) continue;

            if (!"all".equals(activeFilter)) {
                String cat = getCategoryFor(id, meta);
                if (!cat.equals(activeFilter)) continue;
            }
            if (!q.isEmpty()) {
                String name = meta.getName().toLowerCase();
                String desc = meta.getDescription() != null
                        ? meta.getDescription().toLowerCase() : "";
                if (!name.contains(q) && !id.contains(q) && !desc.contains(q)) continue;
            }
            out.add(mod);
        }
        return out;
    }

    private String getCategoryFor(String id, ModMetadata meta) {
        if (MOD_CATEGORIES.containsKey(id)) return MOD_CATEGORIES.get(id);
        for (var dep : meta.getDependencies()) {
            if (dep.getModId().equals("sodium")) return "perf";
        }
        return "util";
    }

    private boolean isSystemMod(String id) {
        if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader"))
            return true;
        for (String prefix : SYSTEM_MOD_PREFIXES) {
            if (id.startsWith(prefix)) return true;
        }
        return false;
    }

    // ── Lifecycle / render ───────────────────────────────────────────────
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // No-op; render() handles the gradient.
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,            width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,   width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3, width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
        super.render(ctx, mouseX, mouseY, delta);
        if (parent != null) parent.toast.render(ctx, width, delta);
    }
}
