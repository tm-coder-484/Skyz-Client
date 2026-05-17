package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SliderComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzClientState;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzConfig;
import net.skyz.client.util.SkyzMinimapState;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.util.function.Consumer;

/**
 * Skyz Settings screen — owo-lib edition (Phase 2c port).
 *
 * <p>One scroll container wrapping a vertical flow of sections:
 * <ol>
 *   <li><b>Theme</b> — 6 theme cards, each a custom-surface tile painted
 *       with the theme's BG1→BG3 gradient + accent stripe. Click to apply.</li>
 *   <li><b>Interface</b> — 5 toggles (particle effects, splash text,
 *       animations, glow, compact mode) in a 2-col grid + 3 placeholder
 *       sliders (blur intensity, glow strength, particle count).</li>
 *   <li><b>Minimap & ESP</b> — 4 toggles (cave mode, show leaves, show
 *       entities, storage ESP) in a 2-col grid + minimap zoom slider.</li>
 *   <li><b>Client Info</b> — version rows for Skyz, MC, Fabric, Sodium,
 *       Iris, ModMenu, Essential. Green/red dot per row reflecting
 *       installed status.</li>
 * </ol>
 *
 * <p>Each section header is a styled label + thin underline. Toggle pills
 * follow the same makePill pattern as {@link SkyzHudEditorScreen} (button
 * with label/renderer that flips on click). Sliders use owo's built-in
 * {@link SliderComponent} with a separate value-label that updates from
 * the {@code onChanged} callback.
 *
 * <p>The Interface toggles + sliders aren't currently wired to backing
 * fields beyond the particles toggle (parity with the original screen);
 * future work could persist them through {@link SkyzConfig}.
 */
public class SkyzSettingsScreen extends BaseUIModelScreen<FlowLayout> {

    private final SkyzTitleScreen parent;

    // Owo refs.
    private FlowLayout contentRoot;
    private FlowLayout themeSection;     // rebuilt on theme change to refresh "selected" highlights

    public SkyzSettingsScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.of("skyz_client", "settings"));
        this.parent = parent;
    }

    // ─── Build ───────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        wire(root, "btn-back", () -> client.setScreen(parent), SkyzButtonRenderer.NAV_BACK);

        ScrollContainer<?> scroll = root.childById(ScrollContainer.class, "scroll-settings");
        if (scroll != null) {
            scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xCC8CD2FF)));
        }

        contentRoot = root.childById(FlowLayout.class, "content");
        if (contentRoot == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Settings: content flow not found.");
            return;
        }

        // Theme section — kept as a field reference so applyTheme() can
        // rebuild it without tearing down the whole content tree.
        themeSection = buildThemeSection();
        contentRoot.child(themeSection);

        contentRoot.child(buildInterfaceSection());
        contentRoot.child(buildMinimapSection());
        contentRoot.child(buildBlockEspSection());
        contentRoot.child(buildClientInfoSection());
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Settings: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    // ─── Section: Theme ──────────────────────────────────────────────────
    /**
     * Theme grid is a <b>2-row × 3-col</b> grid of cards. Earlier this was
     * a single horizontal flow that overflowed on narrow GUIs (rightmost
     * cards unclickable), then briefly an embedded horizontal scroll —
     * which was capturing the vertical scroll-wheel events when the mouse
     * hovered over the theme strip and blocking the page's parent
     * vertical scroll ("scrollbar shows but doesn't actually scroll").
     *
     * <p>Static 2-row grid sidesteps both problems: every card always
     * fits, every card is always clickable, and nothing fights the
     * page-level scroll for wheel events.
     */
    private FlowLayout buildThemeSection() {
        FlowLayout sec = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sec.gap(6);
        sec.child(buildSectionHeader("THEME"));

        int themesPerRow = 3;
        for (int row = 0; row < (SkyzTheme.THEMES.length + themesPerRow - 1) / themesPerRow; row++) {
            FlowLayout rowFlow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(56));
            rowFlow.gap(6);
            for (int col = 0; col < themesPerRow; col++) {
                int idx = row * themesPerRow + col;
                if (idx >= SkyzTheme.THEMES.length) {
                    // Pad the trailing column(s) so the layout doesn't expand
                    // the last real card into the empty slot.
                    rowFlow.child(UIContainers.horizontalFlow(Sizing.expand(33), Sizing.fixed(56)));
                } else {
                    rowFlow.child(buildThemeCard(idx));
                }
            }
            sec.child(rowFlow);
        }
        return sec;
    }

    /**
     * One tile in the theme grid. The surface paints the theme gradient,
     * a top accent stripe, and a brighter border when this is the
     * currently-selected theme.
     */
    private FlowLayout buildThemeCard(int idx) {
        String[] t = SkyzTheme.THEMES[idx];
        int c1 = parseHex(t[1]);
        int c2 = parseHex(t[3]);
        int ac = parseHex(t[5]);
        boolean selected = idx == SkyzTheme.getCurrent();

        // Card width = 1/3 of the row (3-col grid). expand(33) lets each
        // card share the row evenly with its 2 siblings.
        FlowLayout card = UIContainers.verticalFlow(Sizing.expand(33), Sizing.fixed(56));
        card.padding(Insets.of(2, 4, 2, 2));
        card.horizontalAlignment(HorizontalAlignment.CENTER);
        card.gap(2);
        card.surface(themeCardSurface(c1, c2, ac, selected));

        // Color-swatch stripe in the middle — rounded pill shape so the
        // edges match the surrounding card.
        FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(28), Sizing.fixed(8));
        swatch.surface((ctx, comp) -> SkyzRenderHelper.fillRoundedRect(ctx,
                comp.x(), comp.y(), comp.width(), comp.height(), 4,
                ac | 0xFF000000));
        swatch.margins(Insets.top(8));
        card.child(swatch);

        // Theme name.
        LabelComponent name = UIComponents.label(Text.literal(t[0]))
                .color(Color.ofArgb(selected ? 0xFFFFFFFF : 0xCCDDEEFF));
        name.margins(Insets.top(8));
        card.child(name);

        // Click-to-apply via mouseDown on the card itself (the card isn't a
        // button so we wire the event manually).
        final int themeIdx = idx;
        card.mouseDown().subscribe((click, doubled) -> {
            applyTheme(themeIdx);
            return true;
        });
        return card;
    }

    /**
     * Theme card body — rounded gradient panel with a Skyz-themed border.
     * The selected card gets a brighter, thicker accent border.
     *
     * <p>Earlier this used flat {@code fill()} rectangles with hard corners
     * and a top accent stripe. The new version uses
     * {@link SkyzRenderHelper#fillRoundedRectGradient} so the gradient
     * follows the rounded shape, plus {@link SkyzRenderHelper#drawRoundedBorder}
     * for the outline. The selection-tick check moved to a small
     * accent-coloured pill in the top-right corner so it doesn't sit on
     * top of the rounded edge.
     */
    private static Surface themeCardSurface(int c1, int c2, int ac, boolean selected) {
        return (ctx, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            int r = 6;

            // Rounded gradient body. fillRoundedRectGradient does the
            // top-light → bottom-dark scan-line gradient that hugs the
            // rounded shape (no rectangular sheen artefacts).
            SkyzRenderHelper.fillRoundedRectGradient(ctx, x, y, w, h, r, c1, c2);

            // Border — brighter + accent-tinted when selected.
            int brd = selected ? (ac | 0xFF000000) : ((ac & 0x00FFFFFF) | 0x55000000);
            SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, r, brd);
            if (selected) {
                // Outer glow for the selected card.
                SkyzRenderHelper.drawRoundedBorder(ctx, x - 1, y - 1, w + 2, h + 2, r + 1,
                        (ac & 0x00FFFFFF) | 0x55000000);
            }

            // Selected ✓ — a tiny tick in the top-right corner inside the
            // rounded border.
            if (selected) {
                int tx = x + w - 12;
                int ty = y + 4;
                SkyzRenderHelper.fillRoundedRect(ctx, tx, ty, 8, 8, 2, ac | 0xFF000000);
                ctx.fill(tx + 2, ty + 4, tx + 3, ty + 6, 0xFFFFFFFF);
                ctx.fill(tx + 3, ty + 5, tx + 5, ty + 6, 0xFFFFFFFF);
                ctx.fill(tx + 5, ty + 3, tx + 6, ty + 5, 0xFFFFFFFF);
            }
        };
    }

    private void applyTheme(int idx) {
        SkyzTheme.apply(idx);
        if (parent != null) parent.toast("Theme: " + SkyzTheme.THEMES[idx][0]);
        SkyzConfig.save();
        // Rebuild ALL sections so the theme grid's "selected" highlight
        // updates and the rest stays in lock-step. Cheaper than tracking
        // insert-at-index for individual sections.
        if (contentRoot != null) {
            contentRoot.clearChildren();
            themeSection = buildThemeSection();
            contentRoot.child(themeSection);
            contentRoot.child(buildInterfaceSection());
            contentRoot.child(buildMinimapSection());
            contentRoot.child(buildBlockEspSection());
            contentRoot.child(buildClientInfoSection());
        }
    }

    // ─── Section: Interface ──────────────────────────────────────────────
    /**
     * Interface toggles + sliders. Earlier this section was 5 placeholder
     * toggles + 3 placeholder sliders that didn't save or do anything —
     * the user noticed and asked for real wirings. Now every row drives
     * actual state:
     *
     * <ul>
     *   <li><b>Particles</b> — {@code parent.particles.setEnabled()}.</li>
     *   <li><b>Chat Timestamps</b> — {@code SkyzClientState.chatTimestamps}.</li>
     *   <li><b>Fullbright</b> — {@code SkyzClientState.fullbright} (gamma override).</li>
     *   <li><b>No Fog</b> — {@code SkyzClientState.noFog}.</li>
     *   <li><b>No Pumpkin Overlay</b> — {@code SkyzClientState.noPumpkinBlur}.</li>
     *   <li><b>No Fire Overlay</b> — {@code SkyzClientState.noFireOverlay}.</li>
     *   <li><b>FOV</b> — vanilla {@code GameOptions.getFov()} (30–110).</li>
     *   <li><b>FOV Multiplier</b> — {@code SkyzClientState.fovMultiplier} (0.5–2.0).</li>
     *   <li><b>Brightness</b> — vanilla {@code GameOptions.getGamma()} (0–100%).</li>
     * </ul>
     *
     * All persist via {@link SkyzConfig#save()} (Skyz fields) or
     * {@code options.write()} (vanilla fields).
     */
    private FlowLayout buildInterfaceSection() {
        var options = MinecraftClient.getInstance().options;

        FlowLayout sec = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sec.gap(6);
        sec.child(buildSectionHeader("INTERFACE"));

        // 2-col grid of toggles. Each entry: name, desc, getter, setter.
        Object[][] toggles = {
            {"Particle Effects", "Ambient floating particles in menus",
                    (java.util.function.BooleanSupplier) () -> parent != null && parent.particles.isEnabled(),
                    (Consumer<Boolean>) v -> { if (parent != null) parent.particles.setEnabled(v); SkyzConfig.save(); }},
            {"Chat Timestamps",  "[12:34] prefix in chat lines",
                    (java.util.function.BooleanSupplier) () -> SkyzClientState.chatTimestamps,
                    (Consumer<Boolean>) v -> { SkyzClientState.chatTimestamps = v; SkyzConfig.save(); }},
            {"Fullbright",       "Maximum gamma override",
                    (java.util.function.BooleanSupplier) () -> SkyzClientState.fullbright,
                    (Consumer<Boolean>) v -> { SkyzClientState.fullbright = v; SkyzConfig.save(); }},
            {"No Fog",           "Disable distance fog rendering",
                    (java.util.function.BooleanSupplier) () -> SkyzClientState.noFog,
                    (Consumer<Boolean>) v -> { SkyzClientState.noFog = v; SkyzConfig.save(); }},
            {"No Pumpkin Overlay", "Hide pumpkin-on-head viewmodel",
                    (java.util.function.BooleanSupplier) () -> SkyzClientState.noPumpkinBlur,
                    (Consumer<Boolean>) v -> { SkyzClientState.noPumpkinBlur = v; SkyzConfig.save(); }},
            {"No Fire Overlay",  "Hide fire on screen when burning",
                    (java.util.function.BooleanSupplier) () -> SkyzClientState.noFireOverlay,
                    (Consumer<Boolean>) v -> { SkyzClientState.noFireOverlay = v; SkyzConfig.save(); }},
        };

        for (int i = 0; i < toggles.length; i += 2) {
            FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(8);
            row.child(makeToggleRow(toggles[i]));
            if (i + 1 < toggles.length) {
                row.child(makeToggleRow(toggles[i + 1]));
            } else {
                row.child(emptyHalf());
            }
            sec.child(row);
        }

        // Sliders — every value here is a real, persisted setting.
        sec.child(buildIntSliderRow("FOV", "°",
                options.getFov().getValue(), 30, 110,
                v -> { options.getFov().setValue(v); options.write(); }));
        sec.child(buildIntSliderRow("FOV Multiplier", "%",
                Math.round(SkyzClientState.fovMultiplier * 100f), 50, 200,
                v -> { SkyzClientState.fovMultiplier = v / 100f; SkyzConfig.save(); }));
        sec.child(buildIntSliderRow("Brightness", "%",
                (int) Math.round(options.getGamma().getValue() * 100), 0, 100,
                v -> { options.getGamma().setValue(v / 100.0); options.write(); }));
        return sec;
    }

    /**
     * Convenience: take the {@code Object[]} row spec from
     * {@link #buildInterfaceSection()} and turn it into a wired-up
     * {@link #buildToggleRow}. Hides the cast noise from the iteration loop.
     */
    @SuppressWarnings("unchecked")
    private FlowLayout makeToggleRow(Object[] spec) {
        String name = (String) spec[0];
        String desc = (String) spec[1];
        java.util.function.BooleanSupplier getter = (java.util.function.BooleanSupplier) spec[2];
        Consumer<Boolean> setter = (Consumer<Boolean>) spec[3];
        return buildToggleRow(name, desc, getter.getAsBoolean(), setter);
    }

    // ─── Section: Minimap & ESP ──────────────────────────────────────────
    private FlowLayout buildMinimapSection() {
        FlowLayout sec = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sec.gap(6);
        sec.child(buildSectionHeader("MINIMAP & ESP"));

        // Row 1: Cave Mode | Show Leaves
        FlowLayout r1 = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        r1.gap(8);
        r1.child(buildToggleRow("Cave Mode",
                "Show terrain below player when underground",
                SkyzClientState.minimapCaveMode,
                v -> { SkyzClientState.minimapCaveMode = v;
                       SkyzMinimapState.invalidate();
                       SkyzConfig.save(); }));
        r1.child(buildToggleRow("Show Leaves",
                "Use top of leaves instead of motion-blocking",
                SkyzClientState.minimapShowLeaves,
                v -> { SkyzClientState.minimapShowLeaves = v;
                       SkyzMinimapState.invalidate();
                       SkyzConfig.save(); }));
        sec.child(r1);

        // Row 2: Show Entities | Storage ESP
        FlowLayout r2 = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        r2.gap(8);
        r2.child(buildToggleRow("Show Entities",
                "Render players and mobs as dots on the minimap",
                SkyzClientState.minimapShowEntities,
                v -> { SkyzClientState.minimapShowEntities = v; SkyzConfig.save(); }));
        r2.child(buildToggleRow("Storage ESP",
                "Highlight chests / barrels through walls",
                SkyzClientState.storageEsp,
                v -> { SkyzClientState.storageEsp = v; SkyzConfig.save(); }));
        sec.child(r2);

        // Zoom slider.
        sec.child(buildIntSliderRow(
                "Minimap Zoom", "blk",
                SkyzClientState.minimapZoom, 16, 96,
                v -> {
                    SkyzClientState.minimapZoom = v;
                    SkyzMinimapState.invalidate();
                    SkyzConfig.save();
                }));
        return sec;
    }

    // ─── Section: Block ESP ──────────────────────────────────────────────
    /**
     * Block ESP section. Master toggle + Spawner Alert side-by-side, then
     * the global ESP range slider, then a 2-col grid of group toggles
     * (each with a colour swatch + name + ON/OFF pill).
     */
    private FlowLayout buildBlockEspSection() {
        FlowLayout sec = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sec.gap(4);
        sec.child(buildSectionHeader("BLOCK ESP"));

        // Master toggle row — Block ESP master + Spawner Alert.
        FlowLayout masterRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        masterRow.gap(8);
        masterRow.child(buildToggleRow("Block ESP (Master)",
                "Render outlines around toggled blocks below",
                SkyzClientState.blockEsp,
                v -> { SkyzClientState.blockEsp = v; SkyzConfig.save(); }));
        masterRow.child(buildToggleRow("Spawner Alert",
                "Ping for spawners, trial spawners & ominous vaults",
                SkyzClientState.spawnerAlert,
                v -> { SkyzClientState.spawnerAlert = v; SkyzConfig.save(); }));
        sec.child(masterRow);

        // ESP range slider (chunks).
        sec.child(buildIntSliderRow("ESP Range", "ch",
                SkyzClientState.espRange, 4, 32,
                v -> { SkyzClientState.espRange = v; SkyzConfig.save(); }));

        // Block group toggles, 2 per row.
        java.util.List<SkyzClientState.BlockEspGroup> groups = SkyzClientState.BLOCK_ESP_GROUPS;
        for (int i = 0; i < groups.size(); i += 2) {
            FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(8);
            row.child(buildBlockEspRow(groups.get(i)));
            if (i + 1 < groups.size()) row.child(buildBlockEspRow(groups.get(i + 1)));
            else row.child(emptyHalf());
            sec.child(row);
        }
        return sec;
    }

    /**
     * One block-ESP toggle row — colored swatch + name + ON/OFF pill.
     * Sits in the same 2-column grid as the regular toggle rows, so
     * Sizing.fill(49) keeps the alignment.
     */
    private FlowLayout buildBlockEspRow(SkyzClientState.BlockEspGroup g) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(49), Sizing.fixed(28));
        row.padding(Insets.of(4, 4, 8, 8));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface(ROW_SURFACE);

        // Color swatch.
        final int swatchCol = g.color | 0xFF000000;
        FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(10), Sizing.fixed(10));
        swatch.surface((ctx, comp) -> SkyzRenderHelper.fillRoundedRect(
                ctx, comp.x(), comp.y(), comp.width(), comp.height(), 2, swatchCol));
        row.child(swatch);

        row.child(UIComponents.label(Text.literal(g.name))
                .color(Color.ofArgb(SkyzColors.TEXT_PRIMARY))
                .horizontalSizing(Sizing.expand(100)));
        row.child(makePill(g.enabled, v -> { g.enabled = v; SkyzConfig.save(); }));
        return row;
    }

    // ─── Section: Client Info ────────────────────────────────────────────
    private FlowLayout buildClientInfoSection() {
        FlowLayout sec = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sec.gap(4);
        sec.child(buildSectionHeader("CLIENT INFO"));

        String[][] info = {
            {"Skyz Client",   "v" + SkyzClientMod.MOD_VERSION},
            {"Minecraft",     "1.21.11"},
            {"Fabric Loader", getModVersion("fabricloader", "0.16.10")},
            {"Sodium",        getModVersion("sodium",        "not installed")},
            {"Iris Shaders",  getModVersion("iris",          "not installed")},
            {"Mod Menu",      getModVersion("modmenu",       "not installed")},
            {"Essential",     getModVersion("essential",     "not installed")},
        };
        for (String[] row : info) sec.child(buildInfoRow(row[0], row[1]));
        return sec;
    }

    private FlowLayout buildInfoRow(String name, String value) {
        boolean ok = !value.equals("not installed");
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.padding(Insets.of(2, 2, 8, 8));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface(ROW_SURFACE);

        row.child(UIComponents.label(Text.literal(name))
                .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                .horizontalSizing(Sizing.expand(100)));

        // Status dot — green if installed, red if not.
        FlowLayout dot = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(7));
        int dotCol = ok ? SkyzColors.STATUS_GREEN : SkyzColors.STATUS_RED;
        dot.surface((ctx, comp) -> SkyzRenderHelper.fillCircle(ctx,
                comp.x() + comp.width() / 2, comp.y() + comp.height() / 2,
                3, dotCol));
        row.child(dot);

        row.child(UIComponents.label(Text.literal(value))
                .color(Color.ofArgb(ok ? SkyzColors.TEXT_PRIMARY : 0x66888888)));

        return row;
    }

    private static String getModVersion(String id, String fallback) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse(fallback);
    }

    // ─── Reusable row builders ───────────────────────────────────────────
    private FlowLayout buildSectionHeader(String text) {
        FlowLayout hdr = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        hdr.gap(2);
        hdr.padding(Insets.top(4));
        hdr.child(UIComponents.label(Text.literal(text))
                .color(Color.ofArgb(0xCC8CD2FF)));
        // Underline
        FlowLayout underline = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        underline.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x338CD2FF));
        hdr.child(underline);
        return hdr;
    }

    /**
     * Shared row surface — rounded panel with a subtle Skyz-blue border
     * and a darker rounded body. Used by toggle rows, slider rows, and
     * info rows so they all match. Centralised in {@link #ROW_SURFACE}
     * so we don't repeat the same lambda on every row.
     */
    private static final io.wispforest.owo.ui.core.Surface ROW_SURFACE = (ctx, comp) -> {
        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
        SkyzRenderHelper.fillRoundedRect(ctx, x, y, w, h, 4, 0x44091E46);
        SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, 4, 0x338CD2FF);
    };

    /**
     * Two-line toggle row. Used in 2-column grids inside Interface and
     * Minimap sections.
     */
    private FlowLayout buildToggleRow(String name, String desc,
                                       boolean initial,
                                       Consumer<Boolean> onChange) {
        // Use fill(49) instead of fill(50) — see SKYZ_PORT_NOTES.md gotcha #3.
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(49), Sizing.fixed(34));
        row.padding(Insets.of(4, 4, 10, 10));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface(ROW_SURFACE);

        FlowLayout text = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content());
        text.gap(1);
        text.child(UIComponents.label(Text.literal(name))
                .color(Color.ofArgb(initial ? SkyzColors.TEXT_PRIMARY : SkyzColors.TEXT_MUTED)));
        text.child(UIComponents.label(Text.literal(desc))
                .color(Color.ofArgb(0x778CD2FF)));
        row.child(text);

        row.child(makePill(initial, onChange));
        return row;
    }

    /**
     * Filler used to balance a 2-col toggle grid when the row has only one
     * real toggle (i.e. an odd number of toggles total).
     */
    private FlowLayout emptyHalf() {
        FlowLayout filler = UIContainers.horizontalFlow(Sizing.fill(49), Sizing.fixed(34));
        return filler;
    }

    /**
     * Slider row that operates on integer values in a [min..max] range.
     * Owo's {@link SliderComponent} stores a 0..1 value internally; we
     * convert at the boundary so callers can stay in integer space.
     */
    private FlowLayout buildIntSliderRow(String name, String unit,
                                          int initial, int min, int max,
                                          Consumer<Integer> onChange) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(5, 6, 10, 10));
        row.gap(3);
        row.surface(ROW_SURFACE);

        FlowLayout topLine = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        topLine.verticalAlignment(VerticalAlignment.CENTER);
        topLine.child(UIComponents.label(Text.literal(name))
                .color(Color.ofArgb(SkyzColors.TEXT_PRIMARY))
                .horizontalSizing(Sizing.expand(100)));
        LabelComponent valueLbl = UIComponents.label(Text.literal(initial + " " + unit))
                .color(Color.ofArgb(0xFF8CD2FF));
        topLine.child(valueLbl);
        row.child(topLine);

        // Use SkyzSliderComponent — a SliderComponent subclass with the
        // vanilla button-texture rendering replaced by a Skyz rounded
        // track + gradient fill + circular knob. Behaves identically
        // (value(), onChanged(), etc.) so the conversion is a one-line swap.
        SkyzSliderComponent slider = new SkyzSliderComponent(Sizing.fill(100));
        slider.verticalSizing(Sizing.fixed(12));
        // Convert int range to 0..1.
        double frac = max > min ? (double) (initial - min) / (max - min) : 0.0;
        slider.value(frac);
        slider.message(v -> Text.literal(""));
        slider.onChanged().subscribe(value -> {
            int v = (int) Math.round(min + value * (max - min));
            valueLbl.text(Text.literal(v + " " + unit));
            onChange.accept(v);
        });
        row.child(slider);
        return row;
    }

    /**
     * Skyz-styled ON/OFF pill. Shared with {@link SkyzHudEditorScreen}'s
     * makePill — same look, same flip-on-click behaviour.
     */
    private ButtonComponent makePill(boolean initial, Consumer<Boolean> onChange) {
        boolean[] state = {initial};
        ButtonComponent btn = UIComponents.button(
                Text.literal(state[0] ? "ON" : "OFF"),
                b -> {});
        btn.renderer(state[0] ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        btn.horizontalSizing(Sizing.fixed(40));
        btn.verticalSizing(Sizing.fixed(16));
        btn.onPress(b -> {
            state[0] = !state[0];
            btn.setMessage(Text.literal(state[0] ? "ON" : "OFF"));
            btn.renderer(state[0] ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
            onChange.accept(state[0]);
        });
        return btn;
    }

    private static int parseHex(String s) {
        if (s == null || s.length() < 3) return 0xFF000000;
        return (int) Long.parseLong(s.substring(2), 16);
    }

    // ─── Lifecycle / render ──────────────────────────────────────────────
    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Painted by render(), so no-op here.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
        super.render(ctx, mouseX, mouseY, delta);
        if (parent != null) parent.toast.render(ctx, width, delta);
    }
}
