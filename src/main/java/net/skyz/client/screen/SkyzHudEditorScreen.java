package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzClientState;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzConfig;
import net.skyz.client.util.SkyzHudState;
import net.skyz.client.util.SkyzRenderHelper;

import java.util.List;

/**
 * Skyz HUD Editor — owo-lib edition (Phase 2c port).
 *
 * Two-pane layout: a fake-game preview on the left where the user drags HUD
 * elements around, and a sidebar on the right with two tabs:
 *   <ol>
 *     <li>HUD Elements — per-element enable pills + MouseButtonEvent-to-select</li>
 *     <li>Built-in Mods — toggles + sliders for QoL/visual mods</li>
 *   </ol>
 *
 * <h3>Why partial owo, partial Java render</h3>
 *
 * The screen chrome (top "← BACK" + bottom toolbar) and the entire sidebar
 * (tab strip, scrollable lists, all rows) are owo components defined by
 * {@code assets/skyz_client/owo_ui/hud_editor.xml} + programmatic
 * construction in {@link #buildSidebar()}. This buys us the same Skyz button
 * styling, hover halos, and consistent layout as the rest of the ported
 * screens.
 *
 * The preview area, however, is rendered manually in {@link #render(GuiGraphics, int, int, float)}
 * because each HUD element has free-form (x, y, w, h) coordinates the user
 * sets by dragging. owo's flow / grid / stack layouts can't model that — and
 * the element previews need to read the same {@code SkyzHudState.ELEMENTS}
 * that the in-game HUD renderer uses, so we keep them as direct GuiGraphics
 * calls. Drag-and-drop lives in {@link #mouseClicked} / {@link #mouseDragged}
 * / {@link #mouseReleased}, which give super.mouseClicked() priority so owo
 * components (back, tab strip, toolbar buttons) get clicks first.
 *
 * <h3>Sidebar collapse</h3>
 *
 * The {@code [◄] / [▶]} button toggles {@link #sidebarOpen}. On toggle we
 * tear down and rebuild the sidebar component tree (cheap — only ~50 owo
 * components) and reposition it. When closed it shrinks to a 22-px strip
 * containing only the toggle button so the user can place HUD elements over
 * the right edge of the preview.
 *
 * @see SkyzHudState
 * @see SkyzClientState
 */
public class SkyzHudEditorScreen extends BaseUIModelScreen<FlowLayout> {

    // ─── Layout constants ────────────────────────────────────────────────
    private static final int SIDEBAR_W      = 196;   // visible content width
    private static final int SIDEBAR_PAD    = 8;     // gutter for scrollbar
    private static final int SIDEBAR_FULL_W = SIDEBAR_W + SIDEBAR_PAD;
    private static final int SIDEBAR_STRIP  = 22;    // collapsed-sidebar width
    private static final int BOTTOM_BAR_H   = 26;
    private static final int TOP_BAR_H      = 26;

    private static final int[] GRIDS = {4, 8, 16, 32};

    // ─── Built-in Mods table (mirrored from old screen) ──────────────────
    // {label, description, stateField, type}
    // Special types: HEADER (divider), WARN (red banner)
    private static final String[][] MODS = {
        {"__HEADER__",       "OTHER BUILT-IN MODS",                "",                "HEADER"},
        {"Toggle Sprint",    "Auto-sprint when moving forward",    "toggleSprint",    "toggle"},
        {"Toggle Sneak",     "Hold sneak without holding key",     "toggleSneak",     "toggle"},
        {"Fullbright",       "Max gamma — see in the dark",        "fullbright",      "toggle"},
        {"No Fog",           "Remove fog distance",                "noFog",           "toggle"},
        {"No Pumpkin Blur",  "Remove pumpkin head overlay",        "noPumpkinBlur",   "toggle"},
        {"Anti-AFK",         "Micro-movement vs AFK kicks",        "antiAfk",         "toggle"},
        {"Auto GG",          "Type 'gg' after game ends",          "autoGG",          "toggle"},
        {"No Fire Overlay",  "Remove fire screen effect",          "noFireOverlay",   "toggle"},
        {"Colored Hitboxes", "Show entity hitboxes with color",    "coloredHitboxes", "toggle"},
        {"Toggle Chat",      "Toggle chat visibility",             "toggleChat",      "toggle"},
        {"Chat Timestamps",  "Show time on each chat message",     "chatTimestamps",  "toggle"},
        {"Dynamic FPS",      "Limit FPS when tabbed or AFK",       "dynamicFps",      "toggle"},
        {"Particle Limiter", "Reduce particles for performance",   "particleLimiter", "toggle"},
        {"FOV Changer",      "Adjust FOV 0.5×–2.0×",               "fovMultiplier",   "slider"},
        {"Autoclicker CPS",  "Auto-MouseButtonEvent rate (toggle in keybinds)","autoclickerCps", "slider"},
        {"__WARN__",         "HACKS — USE AT YOUR OWN RISK",       "",                "WARN"},
        {"Storage ESP",      "Chests/barrels/spawners thru walls", "storageEsp",      "toggle"},
        {"Player ESP",       "Players visible through walls",      "playerEsp",       "toggle"},
        {"Item ESP",         "Item hitboxes through walls",        "itemEsp",         "toggle"},
        {"Block ESP",        "Configurable block highlight",       "blockEsp",        "toggle"},
        {"Mob ESP",          "Mob outlines through walls",         "mobEsp",          "toggle"},
        {"Ore Highlighter",  "Highlight ores through stone",       "oreHighlighter",  "toggle"},
        {"Auto Totem",       "Auto-swap totem to offhand",         "autoTotem",       "toggle"},
        {"Trajectories",     "Show bow/pearl/rod paths",           "trajectories",    "toggle"},
    };

    // ─── Surfaces ────────────────────────────────────────────────────────
    /**
     * Sidebar background — near-opaque dark panel with a left accent line.
     * Originally 0xD8 alpha, but HUD-element previews drawn behind it bled
     * through visibly. Bumped to 0xF6 — still has a touch of glass feel
     * without showing through to the preview underneath.
     */
    private static final Surface SIDEBAR_SURFACE = (ctx, comp) -> {
        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
        SkyzRenderHelper.fillRoundedRect(ctx, x, y, w, h, 6, 0xF6050F2A);
        SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, 6, 0x448CD2FF);
        // Left edge accent
        ctx.fill(x, y + 6, x + 1, y + h - 6, 0x778CD2FF);
    };

    /** Bottom toolbar background — same Skyz glass tint, no left accent. */
    private static final Surface BOTTOM_BAR_SURFACE = (ctx, comp) -> {
        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
        SkyzRenderHelper.fillRoundedRect(ctx, x, y, w, h, 0, 0xCC050F2A);
        // Top accent line
        ctx.fill(x, y, x + w, y + 1, 0x338CD2FF);
    };

    /** Section header (HUD Elements categories). */
    private static final Surface SECTION_HEADER_SURFACE = (ctx, comp) -> {
        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
        ctx.fill(x, y, x + w, y + h, 0x11FFFFFF);
        // Subtle bottom underline
        ctx.fill(x + 4, y + h - 1, x + w - 4, y + h, 0x338CD2FF);
    };

    /** Warning banner (HACKS section). */
    private static final Surface WARN_SURFACE = (ctx, comp) -> {
        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
        ctx.fill(x, y, x + w, y + h, 0x44440000);
        ctx.fill(x, y, x + w, y + 1, 0xAAFF4444);
        ctx.fill(x, y + h - 1, x + w, y + h, 0xAAFF4444);
    };

    /** Per-row hover surface for HUD-element rows. */
    private static final Surface ROW_SURFACE = (ctx, comp) -> {
        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
        if (comp.isInBoundingBox(comp.x() + w / 2.0, comp.y() + h / 2.0)) {
            ctx.fill(x, y, x + w, y + h, 0x11091E46);
        }
    };

    // ─── State ───────────────────────────────────────────────────────────
    private final SkyzTitleScreen parent;
    private List<SkyzHudState.HudElementState> elements;

    private SkyzHudState.HudElementState selected = null;
    private SkyzHudState.HudElementState dragging = null;
    private int dragOffX, dragOffY;

    private int     gridIdx     = 1;     // → GRIDS[1] == 8
    private boolean snapEnabled = true;
    private int     activeTab   = 0;     // 0 = HUD elements, 1 = Built-in mods
    private boolean sidebarOpen = true;

    // owo refs (resolved in build())
    private FlowLayout      rootRef;
    private FlowLayout      sidebar;
    private FlowLayout      tabContent;
    private ButtonComponent tab0Btn, tab1Btn;
    private LabelComponent  snapLabel;
    private ButtonComponent gridBtn, snapToggleBtn;

    public SkyzHudEditorScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "hud_editor"));
        this.parent = parent;
    }

    // ─── Build (called on screen open + resize) ──────────────────────────
    @Override
    protected void build(FlowLayout root) {
        this.rootRef = root;

        if (!SkyzHudState.initialized) SkyzHudState.initDefaults(width, height);
        elements = SkyzHudState.ELEMENTS;

        // ── Chrome buttons ──
        wire(root, "btn-back",        () -> client.setScreen(parent),   SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-grid",        this::cycleGrid,                  SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-snap-toggle", this::toggleSnap,                 SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-save",        this::doSave,                     SkyzButtonRenderer.DEFAULT);
        wire(root, "btn-reset",       this::doReset,                    SkyzButtonRenderer.QUIT);

        // ── Resolve refs for live label updates ──
        snapLabel     = root.childById(LabelComponent.class,  "lbl-snap");
        gridBtn       = root.childById(ButtonComponent.class, "btn-grid");
        snapToggleBtn = root.childById(ButtonComponent.class, "btn-snap-toggle");

        FlowLayout bottomBar = root.childById(FlowLayout.class, "bottom-bar");
        if (bottomBar != null) bottomBar.surface(BOTTOM_BAR_SURFACE);

        // ── Sidebar (programmatic, absolute-positioned over the root) ──
        sidebar = buildSidebar();
        applySidebarPositioning();
        root.child(sidebar);

        rebuildTabContent();
        updateChromeLabels();

        SkyzClientMod.LOGGER.info("[Skyz] HUD Editor built. Sidebar open={}, tab={}, elements={}.",
                sidebarOpen, activeTab, elements.size());
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] HUD Editor: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    // ─── Sidebar construction ────────────────────────────────────────────
    private FlowLayout buildSidebar() {
        // Height leaves a gap from bottom-bar so it doesn't overlap.
        int sideH = Math.max(60, height - TOP_BAR_H - BOTTOM_BAR_H - 4);

        FlowLayout side = UIContainers.verticalFlow(
                Sizing.fixed(sidebarOpen ? SIDEBAR_FULL_W : SIDEBAR_STRIP),
                Sizing.fixed(sideH));
        side.padding(Insets.of(4, 4, 4, 4));
        side.gap(4);
        side.surface(SIDEBAR_SURFACE);

        // Collapse / expand button (same row across both states).
        FlowLayout topRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        topRow.verticalAlignment(VerticalAlignment.CENTER);
        ButtonComponent toggleBtn = UIComponents
                .button(Component.literal(sidebarOpen ? "◄" : "▶"), b -> toggleSidebar())
                .renderer(SkyzButtonRenderer.NAV_BACK);
        toggleBtn.horizontalSizing(Sizing.fixed(sidebarOpen ? 22 : 14));
        toggleBtn.verticalSizing(Sizing.fixed(18));
        topRow.child(toggleBtn);

        if (sidebarOpen) {
            // ── Tab strip ──
            tab0Btn = UIComponents
                    .button(Component.literal("HUD Elements"), b -> setActiveTab(0))
                    .renderer(activeTab == 0 ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
            tab0Btn.horizontalSizing(Sizing.fixed(86));
            tab0Btn.verticalSizing(Sizing.fixed(18));

            tab1Btn = UIComponents
                    .button(Component.literal("Built-in Mods"), b -> setActiveTab(1))
                    .renderer(activeTab == 1 ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
            tab1Btn.horizontalSizing(Sizing.fixed(86));
            tab1Btn.verticalSizing(Sizing.fixed(18));

            FlowLayout tabRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            tabRow.gap(2);
            tabRow.margins(Insets.left(4));
            tabRow.child(tab0Btn);
            tabRow.child(tab1Btn);
            topRow.child(tabRow);
        }
        side.child(topRow);

        if (sidebarOpen) {
            tabContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            tabContent.gap(4);

            ScrollContainer<?> scroll = UIContainers.verticalScroll(
                    Sizing.fill(100), Sizing.expand(100), tabContent);
            scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xCC8CD2FF)));
            scroll.scrollbarThiccness(3);
            scroll.scrollStep(20);
            side.child(scroll);
        }

        return side;
    }

    private void applySidebarPositioning() {
        if (sidebar == null) return;
        int x = sidebarOpen
                ? width - SIDEBAR_FULL_W - 4
                : width - SIDEBAR_STRIP - 4;
        sidebar.positioning(Positioning.absolute(x, TOP_BAR_H + 2));
    }

    private void toggleSidebar() {
        sidebarOpen = !sidebarOpen;
        if (sidebar != null && rootRef != null) {
            rootRef.removeChild(sidebar);
            sidebar = buildSidebar();
            applySidebarPositioning();
            rootRef.child(sidebar);
            if (sidebarOpen) rebuildTabContent();
        }
    }

    private void setActiveTab(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        if (tab0Btn != null) tab0Btn.renderer(activeTab == 0 ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        if (tab1Btn != null) tab1Btn.renderer(activeTab == 1 ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        rebuildTabContent();
    }

    private void rebuildTabContent() {
        if (tabContent == null) return;
        tabContent.clearChildren();
        if (activeTab == 0) populateHudTab();
        else                populateModsTab();
    }

    // ─── HUD Elements tab ────────────────────────────────────────────────
    private void populateHudTab() {
        String lastCat = "";
        for (SkyzHudState.HudElementState el : elements) {
            if (!el.category.equals(lastCat)) {
                lastCat = el.category;
                tabContent.child(buildSectionHeader(catLabel(lastCat)));
            }
            tabContent.child(buildHudRow(el));
        }

        // ── Snap to grid toggle ──
        tabContent.child(buildSectionHeader("OPTIONS"));
        tabContent.child(buildToggleRowDirect(
                "Snap to grid",
                "Aligns dragged elements to the grid",
                snapEnabled,
                v -> {
                    snapEnabled = v;
                    updateChromeLabels();
                }));

        // ── Vanilla suppression ──
        tabContent.child(buildSectionHeader("REPLACE VANILLA"));
        tabContent.child(buildToggleRowDirect(
                "Hide vanilla Health",
                "Use Skyz health bar instead",
                SkyzHudState.hideVanillaHealth,
                v -> SkyzHudState.hideVanillaHealth = v));
        tabContent.child(buildToggleRowDirect(
                "Hide vanilla Hunger",
                "Use Skyz hunger bar instead",
                SkyzHudState.hideVanillaHunger,
                v -> SkyzHudState.hideVanillaHunger = v));
        tabContent.child(buildToggleRowDirect(
                "Hide vanilla Armor",
                "Use Skyz armor bar instead",
                SkyzHudState.hideVanillaArmor,
                v -> SkyzHudState.hideVanillaArmor = v));

        // Selected element info card.
        if (selected != null) {
            FlowLayout card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            card.padding(Insets.of(4, 4, 6, 6));
            card.surface(SkyzSurface.CARD);
            card.margins(Insets.top(4));
            card.gap(2);
            card.child(UIComponents.label(Component.literal(selected.name))
                    .color(Color.ofArgb(0xFF8CD2FF)));
            card.child(UIComponents.label(Component.literal("X " + selected.x + "  Y " + selected.y))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED)));
            tabContent.child(card);
        }
    }

    private FlowLayout buildSectionHeader(String text) {
        FlowLayout hdr = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(13));
        hdr.padding(Insets.of(2, 2, 6, 4));
        hdr.verticalAlignment(VerticalAlignment.CENTER);
        hdr.surface(SECTION_HEADER_SURFACE);
        hdr.child(UIComponents.label(Component.literal(text))
                .color(Color.ofArgb(0xCC8CD2FF)));
        return hdr;
    }

    private FlowLayout buildHudRow(SkyzHudState.HudElementState el) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);

        ButtonComponent pill = makePill(el.enabled, v -> {
            el.enabled = v;
            // The pill rebuild swaps the renderer + label, no row rebuild needed.
        });
        row.child(pill);

        row.child(UIComponents.label(Component.literal(el.icon))
                .color(Color.ofArgb(0xFFFFFFFF))
                .horizontalSizing(Sizing.fixed(12)));

        LabelComponent name = UIComponents.label(Component.literal(el.name));
        name.color(Color.ofArgb(el.enabled ? SkyzColors.TEXT_PRIMARY : SkyzColors.TEXT_MUTED));
        name.horizontalSizing(Sizing.expand(100));
        row.child(name);

        // MouseButtonEvent anywhere on the row (not just the pill) to select for drag.
        // The pill button consumes its own clicks first, so we only fire when
        // the user clicked elsewhere on the row.
        row.mouseDown().subscribe((MouseButtonEvent, doubled) -> {
            selected = el;
            rebuildTabContent();   // refresh the bottom info card
            return true;
        });

        return row;
    }

    // ─── Built-in Mods tab ───────────────────────────────────────────────
    private void populateModsTab() {
        for (String[] mod : MODS) {
            switch (mod[3]) {
                case "HEADER" -> tabContent.child(buildModHeader(mod[1]));
                case "WARN"   -> tabContent.child(buildModWarn(mod[1]));
                case "slider" -> tabContent.child(buildSliderRow(mod[0], mod[1], mod[2]));
                default       -> tabContent.child(buildToggleRowField(mod[0], mod[1], mod[2]));
            }
        }
    }

    private FlowLayout buildModHeader(String text) {
        FlowLayout hdr = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        hdr.padding(Insets.vertical(4));
        hdr.horizontalAlignment(HorizontalAlignment.CENTER);
        hdr.verticalAlignment(VerticalAlignment.CENTER);
        hdr.surface(SECTION_HEADER_SURFACE);
        hdr.child(UIComponents.label(Component.literal(text))
                .color(Color.ofArgb(SkyzColors.TEXT_PRIMARY)));
        return hdr;
    }

    private FlowLayout buildModWarn(String text) {
        FlowLayout warn = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        warn.padding(Insets.of(4, 4, 4, 4));
        warn.horizontalAlignment(HorizontalAlignment.CENTER);
        warn.gap(2);
        warn.surface(WARN_SURFACE);
        warn.child(UIComponents.label(Component.literal("⚠ " + text + " ⚠"))
                .color(Color.ofArgb(0xFFFF4444)));
        warn.child(UIComponents.label(Component.literal("May violate server rules. You are responsible."))
                .color(Color.ofArgb(0x88FF8888)));
        return warn;
    }

    private FlowLayout buildToggleRowField(String name, String desc, String field) {
        return buildToggleRowDirect(name, desc, getToggle(field),
                v -> {
                    setToggle(field, v);
                    if (parent != null) parent.toast(name + ": " + (v ? "ON" : "OFF"));
                    SkyzConfig.save();
                });
    }

    private FlowLayout buildToggleRowDirect(String name, String desc,
                                             boolean initial,
                                             java.util.function.Consumer<Boolean> onChange) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(34));
        row.padding(Insets.of(3, 3, 6, 6));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout text = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content());
        text.gap(1);
        text.child(UIComponents.label(Component.literal(name))
                .color(Color.ofArgb(initial ? SkyzColors.TEXT_PRIMARY : SkyzColors.TEXT_MUTED)));
        text.child(UIComponents.label(Component.literal(desc))
                .color(Color.ofArgb(0x778CD2FF)));
        row.child(text);

        ButtonComponent pill = makePill(initial, onChange);
        row.child(pill);

        return row;
    }

    private FlowLayout buildSliderRow(String name, String desc, String field) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(48));
        row.padding(Insets.of(3, 3, 6, 6));
        row.gap(2);

        FlowLayout topLine = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        topLine.verticalAlignment(VerticalAlignment.CENTER);
        topLine.child(UIComponents.label(Component.literal(name))
                .color(Color.ofArgb(SkyzColors.TEXT_PRIMARY))
                .horizontalSizing(Sizing.expand(100)));
        LabelComponent valueLbl = UIComponents.label(Component.literal(formatSlider(field)))
                .color(Color.ofArgb(0xFF8CD2FF));
        topLine.child(valueLbl);
        row.child(topLine);

        row.child(UIComponents.label(Component.literal(desc))
                .color(Color.ofArgb(0x778CD2FF)));

        // Skyz-styled slider — same pill/track/knob look as the settings
        // sliders. SkyzSliderComponent extends owo's SliderComponent so
        // value()/onChanged() behave identically; only the rendering
        // differs from vanilla's button-textured slider.
        SkyzSliderComponent slider = new SkyzSliderComponent(Sizing.fill(100));
        slider.verticalSizing(Sizing.fixed(12));
        slider.value(getSliderFrac(field));
        slider.message(v -> Component.literal(""));   // we render value in the top-line label instead
        slider.onChanged().subscribe(value -> {
            setSliderFrac(field, (float) value);
            valueLbl.text(Component.literal(formatSlider(field)));
        });
        row.child(slider);

        return row;
    }

    /**
     * Builds a Skyz pill toggle. Visually: blue glass when on, subtle outline
     * when off. The button rebuilds its own label/renderer in its onPress so
     * we don't need to refresh the parent row.
     */
    private ButtonComponent makePill(boolean initial, java.util.function.Consumer<Boolean> onChange) {
        boolean[] state = {initial};
        ButtonComponent btn = UIComponents.button(
                Component.literal(state[0] ? "ON" : "OFF"),
                b -> {});
        btn.renderer(state[0] ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        btn.horizontalSizing(Sizing.fixed(36));
        btn.verticalSizing(Sizing.fixed(16));
        btn.onPress(b -> {
            state[0] = !state[0];
            btn.setMessage(Component.literal(state[0] ? "ON" : "OFF"));
            btn.renderer(state[0] ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
            onChange.accept(state[0]);
        });
        return btn;
    }

    // ─── Chrome actions ──────────────────────────────────────────────────
    private void cycleGrid() {
        gridIdx = (gridIdx + 1) % GRIDS.length;
        updateChromeLabels();
    }

    private void toggleSnap() {
        snapEnabled = !snapEnabled;
        updateChromeLabels();
    }

    private void doSave() {
        SkyzConfig.save();
        if (parent != null) parent.toast("Saved!");
    }

    private void doReset() {
        SkyzHudState.initialized = false;
        SkyzHudState.ELEMENTS.clear();
        SkyzHudState.initDefaults(width, height);
        elements = SkyzHudState.ELEMENTS;
        selected = null;
        dragging = null;
        rebuildTabContent();
        if (parent != null) parent.toast("Layout reset");
    }

    private void updateChromeLabels() {
        if (snapLabel != null) {
            snapLabel.text(Component.literal("Snap: " + (snapEnabled ? GRIDS[gridIdx] + "px" : "OFF")));
            snapLabel.color(Color.ofArgb(snapEnabled ? 0xFF8CD2FF : SkyzColors.TEXT_MUTED));
        }
        if (gridBtn != null) gridBtn.setMessage(Component.literal("Grid: " + GRIDS[gridIdx] + "px"));
        if (snapToggleBtn != null) snapToggleBtn.setMessage(Component.literal(snapEnabled ? "Snap ON" : "Snap OFF"));
    }

    private int snapGrid() { return GRIDS[gridIdx]; }

    private static String catLabel(String cat) {
        return switch (cat) {
            case "bars"    -> "BARS";
            case "effects" -> "EFFECTS";
            case "input"   -> "INPUT";
            case "combat"  -> "COMBAT";
            default        -> "INFO";
        };
    }

    // ─── Render (preview + super) ────────────────────────────────────────
    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // No-op — render() handles the simulated game background ourselves.
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // 1) Compute live preview width — shrinks/grows when the sidebar
        //    collapses. Used for both rendering bounds and scissor clip.
        int previewW = (sidebarOpen ? width - SIDEBAR_FULL_W - 8 : width - SIDEBAR_STRIP - 8);
        previewW = Math.max(0, previewW);

        // 2) Solid base — fills the *entire* screen so the sidebar's panel
        //    sits on a uniform colour rather than a gradient that would
        //    show seams under the sidebar's near-opaque surface.
        ctx.fill(0, 0, width, height, 0xFF050F2A);

        // 3) Game preview (gradient sky/ground + element previews) is
        //    scissor-clipped to the preview area so HUD elements at
        //    default positions on the right side don't bleed through the
        //    sidebar. Without this, a Compass element at top-right would
        //    show through the tab strip.
        ctx.enableScissor(0, 0, previewW, height);

        SkyzRenderHelper.fillGradientV(ctx, 0, 0,
                previewW, (int) (height * .55f), 0xFF4A7FC0, 0xFF6AA0D8);
        SkyzRenderHelper.fillGradientV(ctx, 0, (int) (height * .55f),
                previewW, height, 0xFF5A7A45, 0xFF3D5A2A);
        ctx.drawCenteredString(font,
                "Drag elements • Enable in sidebar • Toggle sidebar with [◄]",
                previewW / 2, height / 2 - 4, 0x22FFFFFF);

        if (dragging != null && snapEnabled) {
            int g = snapGrid();
            for (int gx = 0; gx < previewW; gx += g) ctx.fill(gx, 0, gx + 1, height, 0x07FFFFFF);
            for (int gy = 0; gy < height;  gy += g) ctx.fill(0, gy, previewW, gy + 1, 0x07FFFFFF);
        }

        if (dragging != null) {
            int g = snapEnabled ? snapGrid() : 1;
            int nx = Math.max(0, Math.min(previewW - dragging.w, (int)lastMouseX - dragOffX));
            int ny = Math.max(0, Math.min(height   - dragging.h, (int)lastMouseY - dragOffY));
            dragging.x = Math.round((float) nx / g) * g;
            dragging.y = Math.round((float) ny / g) * g;
        }

        for (var el : elements) drawElPreview(ctx, el, (int)lastMouseX, (int)lastMouseY);

        ctx.disableScissor();

        // 4) Owo (sidebar + chrome) on top.
        super.render(ctx, mouseX, mouseY, delta);

        // 5) Toast (re-uses parent screen's toast manager).
        if (parent != null) parent.toast.render(ctx, width, delta);

        // Update stored mouse coords for the drag computation above.
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    /** Last known mouse position — used by render() to update drag positions. */
    private double lastMouseX, lastMouseY;

    private void drawElPreview(GuiGraphics ctx, SkyzHudState.HudElementState el, int mx, int my) {
        boolean hov  = mx >= el.x && mx <= el.x + el.w && my >= el.y && my <= el.y + el.h;
        boolean sel  = el == selected;
        boolean drag = el == dragging;
        if (!el.enabled && !hov && !sel && !drag) return;
        int bg  = drag ? 0xCC1A5A8A : sel ? 0x993C8ABE : !el.enabled ? 0x44143C6E : 0xB8050F30;
        int brd = drag || sel ? 0xFF8CD2FF : !el.enabled ? 0x44446688 : 0x668CD2FF;
        SkyzRenderHelper.drawHudPanel(ctx, el.x, el.y, el.x + el.w, el.y + el.h, bg, brd);
        ctx.drawString(font, el.icon, el.x + 3, el.y + (el.h - 8) / 2,
                el.enabled ? 0xFFFFFFFF : 0x88FFFFFF, false);
        String lbl = el.name;
        while (font.width(lbl) > el.w - 18 && lbl.length() > 3)
            lbl = lbl.substring(0, lbl.length() - 3) + "..";
        ctx.drawString(font, lbl, el.x + 16, el.y + (el.h - 8) / 2,
                el.enabled ? 0xCCDDFFFF : 0x55DDFFFF, false);
    }

    // ─── Input ───────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent MouseButtonEvent, boolean doubled) {
        double mx = MouseButtonEvent.x(), my = MouseButtonEvent.y();
        int previewW = (sidebarOpen ? width - SIDEBAR_FULL_W - 8 : width - SIDEBAR_STRIP - 8);

        // Drag detection runs FIRST when the MouseButtonEvent is in the preview area.
        // We don't gate on super.mouseClicked() here because owo's root
        // flow-layout's empty mid-spacer can swallow the MouseButtonEvent as
        // "consumed" via its focus-handler side-effect, which would stop
        // drag from ever starting. The preview area has no real owo
        // content anyway, so it's safe to claim clicks there for ourselves.
        boolean inPreview = mx >= 0 && mx <= previewW
                && my >= 0 && my < height;
        if (inPreview) {
            // Topmost element wins (iterate in reverse).
            for (int i = elements.size() - 1; i >= 0; i--) {
                var el = elements.get(i);
                if (mx >= el.x && mx <= el.x + el.w && my >= el.y && my <= el.y + el.h) {
                    selected = el;
                    dragging = el;
                    dragOffX = (int) mx - el.x;
                    dragOffY = (int) my - el.y;
                    if (activeTab == 0) rebuildTabContent();
                    return true;
                }
            }
            // MouseButtonEvent in empty preview area — deselect.
            if (selected != null && activeTab == 0) {
                selected = null;
                rebuildTabContent();
            } else {
                selected = null;
            }
        }

        // Defer to owo for sidebar / chrome / anything else.
        return super.mouseClicked(MouseButtonEvent, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent MouseButtonEvent, double dx, double dy) {
        // While we're dragging a HUD element, the preview render() updates
        // its position from the live mouse coords, so nothing to do here.
        return super.mouseDragged(MouseButtonEvent, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent MouseButtonEvent) {
        dragging = null;
        return super.mouseReleased(MouseButtonEvent);
    }

    @Override public boolean charTyped(CharacterEvent in) { return false; }

    @Override
    public void onClose() {
        client.setScreen(parent);
    }

    // ─── State get/set helpers (mirror old screen) ───────────────────────
    private boolean getToggle(String f) { return switch (f) {
        case "toggleSprint"    -> SkyzClientState.toggleSprint;
        case "toggleSneak"     -> SkyzClientState.toggleSneak;
        case "fullbright"      -> SkyzClientState.fullbright;
        case "noFog"           -> SkyzClientState.noFog;
        case "noPumpkinBlur"   -> SkyzClientState.noPumpkinBlur;
        case "antiAfk"         -> SkyzClientState.antiAfk;
        case "autoGG"          -> SkyzClientState.autoGG;
        case "noFireOverlay"   -> SkyzClientState.noFireOverlay;
        case "coloredHitboxes" -> SkyzClientState.coloredHitboxes;
        case "toggleChat"      -> SkyzClientState.toggleChat;
        case "chatTimestamps"  -> SkyzClientState.chatTimestamps;
        case "dynamicFps"      -> SkyzClientState.dynamicFps;
        case "particleLimiter" -> SkyzClientState.particleLimiter;
        case "storageEsp"      -> SkyzClientState.storageEsp;
        case "playerEsp"       -> SkyzClientState.playerEsp;
        case "itemEsp"         -> SkyzClientState.itemEsp;
        case "blockEsp"        -> SkyzClientState.blockEsp;
        case "mobEsp"          -> SkyzClientState.mobEsp;
        case "oreHighlighter"  -> SkyzClientState.oreHighlighter;
        case "autoTotem"       -> SkyzClientState.autoTotem;
        case "trajectories"    -> SkyzClientState.trajectories;
        default -> false;
    }; }

    private void setToggle(String f, boolean v) { switch (f) {
        case "toggleSprint"    -> SkyzClientState.toggleSprint    = v;
        case "toggleSneak"     -> SkyzClientState.toggleSneak     = v;
        case "fullbright"      -> SkyzClientState.fullbright      = v;
        case "noFog"           -> SkyzClientState.noFog           = v;
        case "noPumpkinBlur"   -> SkyzClientState.noPumpkinBlur   = v;
        case "antiAfk"         -> SkyzClientState.antiAfk         = v;
        case "autoGG"          -> SkyzClientState.autoGG          = v;
        case "noFireOverlay"   -> SkyzClientState.noFireOverlay   = v;
        case "coloredHitboxes" -> SkyzClientState.coloredHitboxes = v;
        case "toggleChat"      -> SkyzClientState.toggleChat      = v;
        case "chatTimestamps"  -> SkyzClientState.chatTimestamps  = v;
        case "dynamicFps"      -> SkyzClientState.dynamicFps      = v;
        case "particleLimiter" -> SkyzClientState.particleLimiter = v;
        case "storageEsp"      -> SkyzClientState.storageEsp      = v;
        case "playerEsp"       -> SkyzClientState.playerEsp       = v;
        case "itemEsp"         -> SkyzClientState.itemEsp         = v;
        case "blockEsp"        -> SkyzClientState.blockEsp        = v;
        case "mobEsp"          -> SkyzClientState.mobEsp          = v;
        case "oreHighlighter"  -> SkyzClientState.oreHighlighter  = v;
        case "autoTotem"       -> SkyzClientState.autoTotem       = v;
        case "trajectories"    -> SkyzClientState.trajectories    = v;
    } }

    /** Returns the slider 0..1 fraction for the given field. */
    private double getSliderFrac(String f) { return switch (f) {
        case "fovMultiplier"  -> (SkyzClientState.fovMultiplier - 0.5f) / 1.5f;
        case "autoclickerCps" -> (SkyzClientState.autoclickerCps - 1) / 19.0;
        default -> 0.5;
    }; }

    /** Writes back from a 0..1 fraction. */
    private void setSliderFrac(String f, float frac) {
        switch (f) {
            case "fovMultiplier"  -> SkyzClientState.fovMultiplier  = 0.5f + frac * 1.5f;
            case "autoclickerCps" -> SkyzClientState.autoclickerCps = Math.round(1 + frac * 19);
        }
    }

    /** Human-readable current value for the slider's right-aligned label. */
    private String formatSlider(String f) { return switch (f) {
        case "fovMultiplier"  -> String.format("%.2f×", SkyzClientState.fovMultiplier);
        case "autoclickerCps" -> SkyzClientState.autoclickerCps + " cps";
        default -> "";
    }; }
}
