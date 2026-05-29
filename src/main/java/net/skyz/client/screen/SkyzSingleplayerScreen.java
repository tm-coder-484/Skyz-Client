package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Skyz Singleplayer screen — owo-lib edition (Phase 2c port).
 *
 * <p>Layout structure: see {@code assets/skyz_client/owo_ui/singleplayer.xml}.
 *
 * <p>World cards are built programmatically from
 * {@code LevelStorageSource.loadLevelSummaries(...)} in {@link #rebuildList}
 * and injected into the {@code world-list} flow.
 */
public class SkyzSingleplayerScreen extends BaseUIModelScreen<FlowLayout> {

    private final SkyzTitleScreen parent;
    private final Minecraft mc;

    private List<LevelSummary> worlds = new ArrayList<>();
    private boolean loaded = false;
    private boolean failed = false;

    // Owo refs (resolved in build()).
    private FlowLayout       listContainer;
    private LabelComponent   countLabel;
    private TextBoxComponent searchBox;
    private ButtonComponent  filAllBtn, filSurvivalBtn, filCreativeBtn, filHardcoreBtn;

    private String  searchQuery   = "";
    private String  activeFilter  = "all";

    // Delete-confirmation overlay state (Java-rendered). When non-null,
    // blocks all owo input.
    private LevelSummary deletePending = null;

    public SkyzSingleplayerScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "singleplayer"));
        this.parent = parent;
        this.mc     = Minecraft.getInstance();
        loadWorlds();
    }

    /** Loads the world summaries off the disk; safe to call multiple times. */
    private void loadWorlds() {
        loaded = false;
        failed = false;
        try {
            LevelStorageSource.LevelCandidates list = mc.getLevelSource().findLevelCandidates();
            worlds = mc.getLevelSource().loadLevelSummaries(list).join();
            loaded = true;
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] World load failed: {}", e.getMessage());
            failed = true;
        }
    }

    // ─── Build (owo wiring) ──────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        SkyzClientMod.LOGGER.info("[Skyz] Singleplayer built. Worlds={}, failed={}.",
                worlds.size(), failed);

        wire(root, "btn-back",             () -> client.setScreen(parent), SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-create-world",     () -> client.setScreen(new SelectWorldScreen(this)),
                SkyzButtonRenderer.DEFAULT);
        wire(root, "btn-vanilla-selector", () -> client.setScreen(new SelectWorldScreen(this)),
                SkyzButtonRenderer.NAV_BACK);

        // Filter pills.
        filAllBtn      = root.childById(ButtonComponent.class, "btn-filter-all");
        filSurvivalBtn = root.childById(ButtonComponent.class, "btn-filter-survival");
        filCreativeBtn = root.childById(ButtonComponent.class, "btn-filter-creative");
        filHardcoreBtn = root.childById(ButtonComponent.class, "btn-filter-hardcore");
        if (filAllBtn      != null) filAllBtn.onPress(b      -> { activeFilter = "all";       refreshFilters(); rebuildList(); });
        if (filSurvivalBtn != null) filSurvivalBtn.onPress(b -> { activeFilter = "survival";  refreshFilters(); rebuildList(); });
        if (filCreativeBtn != null) filCreativeBtn.onPress(b -> { activeFilter = "creative";  refreshFilters(); rebuildList(); });
        if (filHardcoreBtn != null) filHardcoreBtn.onPress(b -> { activeFilter = "hardcore";  refreshFilters(); rebuildList(); });
        refreshFilters();

        // Search box.
        searchBox = root.childById(TextBoxComponent.class, "tb-search");
        if (searchBox != null) {
            searchBox.setDrawsBackground(false);
            searchBox.onChanged().subscribe(value -> {
                searchQuery = value;
                rebuildList();
            });
        }
        FlowLayout searchWrapper = root.childById(FlowLayout.class, "search-wrapper");
        if (searchWrapper != null) searchWrapper.surface(SkyzSurface.PILL_INPUT);

        // List container + count label.
        listContainer = root.childById(FlowLayout.class, "world-list");
        countLabel    = root.childById(LabelComponent.class, "lbl-count");

        ScrollContainer<?> scroll = root.childById(ScrollContainer.class, "scroll-world-list");
        if (scroll != null) {
            scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xCC8CD2FF)));
        }

        rebuildList();
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Singleplayer: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    private void refreshFilters() {
        ButtonComponent[] btns = {filAllBtn, filSurvivalBtn, filCreativeBtn, filHardcoreBtn};
        String[]          keys = {"all",       "survival",      "creative",      "hardcore"};
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] == null) continue;
            btns[i].renderer(activeFilter.equals(keys[i])
                    ? SkyzButtonRenderer.DEFAULT
                    : SkyzButtonRenderer.NAV_BACK);
        }
    }

    // ─── World list / cards ──────────────────────────────────────────────
    private void rebuildList() {
        if (listContainer == null) return;
        listContainer.clearChildren();

        if (failed) {
            FlowLayout msg = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            msg.horizontalAlignment(HorizontalAlignment.CENTER);
            msg.padding(Insets.vertical(20));
            msg.gap(8);
            msg.child(UIComponents.label(Component.literal(
                            "Could not load worlds. Open the vanilla selector to recover."))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED)));
            ButtonComponent open = UIComponents.button(
                            Component.literal("🌍  OPEN VANILLA SELECTOR"),
                            b -> client.setScreen(new SelectWorldScreen(this)))
                    .renderer(SkyzButtonRenderer.DEFAULT);
            open.horizontalSizing(Sizing.fixed(220));
            open.verticalSizing(Sizing.fixed(22));
            msg.child(open);
            listContainer.child(msg);
            if (countLabel != null) countLabel.text(Component.literal("0 worlds"));
            return;
        }

        if (!loaded) {
            LabelComponent loading = UIComponents
                    .label(Component.literal("Loading worlds..."))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER);
            loading.horizontalSizing(Sizing.fill(100));
            loading.margins(Insets.vertical(20));
            listContainer.child(loading);
            return;
        }

        List<LevelSummary> visible = getFiltered();
        if (countLabel != null) {
            countLabel.text(Component.literal(visible.size() + " world"
                    + (visible.size() == 1 ? "" : "s")));
        }

        if (visible.isEmpty()) {
            String msg = worlds.isEmpty()
                    ? "No worlds yet. MouseButtonEvent + Create World."
                    : "No worlds match \"" + searchQuery + "\".";
            LabelComponent empty = UIComponents
                    .label(Component.literal(msg))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER);
            empty.horizontalSizing(Sizing.fill(100));
            empty.margins(Insets.vertical(20));
            listContainer.child(empty);
            return;
        }

        for (LevelSummary w : visible) {
            listContainer.child(buildCard(w));
        }
    }

    private FlowLayout buildCard(LevelSummary w) {
        FlowLayout card = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(60));
        card.gap(10);
        card.padding(Insets.of(8, 8, 12, 12));
        card.verticalAlignment(VerticalAlignment.CENTER);
        card.surface(SkyzSurface.CARD);

        // ── Mode-icon box (square pill) ──
        boolean hardcore = w.isHardcore();
        GameType gm      = w.getGameMode();
        String icon =
                hardcore                       ? "💀"
                : gm == GameType.CREATIVE      ? "🏗"
                : gm == GameType.ADVENTURE     ? "🗡"
                : gm == GameType.SPECTATOR     ? "👻"
                                                 : "🌐";
        FlowLayout iconBox = UIContainers.horizontalFlow(Sizing.fixed(36), Sizing.fixed(36));
        iconBox.horizontalAlignment(HorizontalAlignment.CENTER);
        iconBox.verticalAlignment(VerticalAlignment.CENTER);
        iconBox.surface(SkyzSurface.PILL_INPUT);
        iconBox.child(UIComponents.label(Component.literal(icon)));
        card.child(iconBox);

        // ── Info column ──
        FlowLayout info = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        info.gap(2);

        info.child(UIComponents.label(Component.literal(w.getLevelName()))
                .color(Color.ofArgb(SkyzColors.TEXT_PRIMARY)));
        info.child(UIComponents.label(Component.literal(w.getLevelId()))
                .color(Color.ofArgb(0xFF4D8CD2)));

        String date = w.getLastPlayed() > 0
                ? new SimpleDateFormat("d MMM yyyy").format(new Date(w.getLastPlayed()))
                : "Unknown";
        String mode = getModeName(w);
        info.child(UIComponents.label(Component.literal(date + "  ·  " + mode))
                .color(Color.ofArgb(0xFF778CD2)));
        card.child(info);

        // ── Action buttons ──
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        actions.gap(3);
        actions.verticalAlignment(VerticalAlignment.CENTER);

        ButtonComponent playBtn = UIComponents
                .button(Component.literal("PLAY"), b -> playWorld(w))
                .renderer(SkyzButtonRenderer.DEFAULT);
        playBtn.horizontalSizing(Sizing.fixed(56));
        playBtn.verticalSizing(Sizing.fixed(20));
        actions.child(playBtn);

        ButtonComponent editBtn = UIComponents
                .button(Component.literal("✏"), b -> editWorld(w))
                .renderer(SkyzButtonRenderer.NAV_BACK);
        editBtn.horizontalSizing(Sizing.fixed(22));
        editBtn.verticalSizing(Sizing.fixed(20));
        actions.child(editBtn);

        ButtonComponent deleteBtn = UIComponents
                .button(Component.literal("🗑"), b -> deletePending = w)
                .renderer(SkyzButtonRenderer.QUIT);
        deleteBtn.horizontalSizing(Sizing.fixed(22));
        deleteBtn.verticalSizing(Sizing.fixed(20));
        actions.child(deleteBtn);

        card.child(actions);
        return card;
    }

    // ─── Card actions ────────────────────────────────────────────────────
    private void playWorld(LevelSummary w) {
        if (w.isLocked()) {
            if (parent != null) parent.toast("World is locked!");
            return;
        }
        // WorldOpenFlows.openWorld(String levelId, Runnable onCancel)
        mc.createWorldOpenFlows().openWorld(w.getLevelId(), () -> mc.setScreen(this));
    }

    /**
     * Open vanilla EditWorldScreen — gives the user rename, reset icon, and
     * backup actions for free.
     */
    private void editWorld(LevelSummary w) {
        try {
            LevelStorageSource.LevelStorageAccess session = mc.getLevelSource().createAccess(w.getLevelId());
            EditWorldScreen edit = EditWorldScreen.create(mc, session, saved -> {
                try { session.close(); } catch (IOException ignored) {}
                loadWorlds();
                rebuildList();
                mc.setScreen(this);
            });
            mc.setScreen(edit);
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] Edit world '{}' failed: {}", w.getLevelId(), e.getMessage());
            if (parent != null) parent.toast("Could not edit: " + e.getMessage());
        }
    }

    /**
     * Walks the world directory and deletes every file/directory bottom-up.
     */
    private void deleteWorld(LevelSummary w) {
        try (LevelStorageSource.LevelStorageAccess session = mc.getLevelSource().createAccess(w.getLevelId())) {
            Path levelRoot = session.getLevelPath(LevelResource.ROOT);
            try (var paths = Files.walk(levelRoot)) {
                paths.sorted(Comparator.reverseOrder())
                     .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] Delete world '{}' failed: {}", w.getLevelId(), e.getMessage());
            if (parent != null) parent.toast("Delete failed: " + e.getMessage());
            return;
        }
        if (parent != null) parent.toast("Deleted " + w.getLevelName());
        loadWorlds();
        rebuildList();
    }

    // ─── Filtering ───────────────────────────────────────────────────────
    private List<LevelSummary> getFiltered() {
        String q = searchQuery.toLowerCase();
        List<LevelSummary> out = new ArrayList<>();
        for (LevelSummary w : worlds) {
            boolean cat = switch (activeFilter) {
                case "survival" -> !w.isHardcore() && w.getGameMode() == GameType.SURVIVAL;
                case "creative" -> w.getGameMode() == GameType.CREATIVE;
                case "hardcore" -> w.isHardcore();
                default         -> true;
            };
            boolean term = q.isEmpty()
                    || w.getLevelName().toLowerCase().contains(q)
                    || w.getLevelId().toLowerCase().contains(q);
            if (cat && term) out.add(w);
        }
        return out;
    }

    private static String getModeName(LevelSummary w) {
        if (w.isHardcore()) return "Hardcore";
        GameType gm = w.getGameMode();
        if (gm == GameType.CREATIVE)  return "Creative";
        if (gm == GameType.ADVENTURE) return "Adventure";
        if (gm == GameType.SPECTATOR) return "Spectator";
        return "Survival";
    }

    // ─── Render (gradient bg + delete confirm overlay) ───────────────────
    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Painted by render(), so no-op here.
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // 1) Gradient background.
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);

        // 2) Owo (nav, action, toolbar, world list).
        super.render(ctx, mouseX, mouseY, delta);

        // 3) Delete-confirmation overlay (modal — eats clicks/keys).
        if (deletePending != null) drawDeleteOverlay(ctx, mouseX, mouseY);

        // 4) Toast.
        if (parent != null) parent.toast.render(ctx, width, delta);
    }

    private void drawDeleteOverlay(GuiGraphics ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0xCC050F2A);

        int bw = 360, bh = 130, bx = (width - bw) / 2, by = (height - bh) / 2;
        SkyzRenderHelper.fillRoundedRect(ctx, bx, by, bw, bh, 12, 0xEE071830);
        SkyzRenderHelper.drawRoundedBorder(ctx, bx, by, bw, bh, 12, 0xAAFF6666);

        ctx.drawCenteredString(font, "Delete world?",
                width / 2, by + 14, 0xFFFFAAAA);
        ctx.drawCenteredString(font,
                "\"" + deletePending.getLevelName() + "\" will be permanently deleted.",
                width / 2, by + 34, SkyzColors.TEXT_PRIMARY);
        ctx.drawCenteredString(font,
                "This cannot be undone.",
                width / 2, by + 48, SkyzColors.TEXT_MUTED);

        // Buttons.
        int btnY = by + bh - 32;
        boolean delHov = mx >= bx + 14         && mx <= bx + 174         && my >= btnY && my <= btnY + 22;
        boolean caHov  = mx >= bx + bw - 174   && mx <= bx + bw - 14     && my >= btnY && my <= btnY + 22;

        SkyzRenderHelper.fillRoundedRect(ctx, bx + 14,        btnY, 160, 22, 8,
                delHov ? 0xCC8C2832 : 0x80140A12);
        SkyzRenderHelper.drawRoundedBorder(ctx, bx + 14,      btnY, 160, 22, 8,
                delHov ? 0xCCFF7878 : 0x66FF8C8C);

        SkyzRenderHelper.fillRoundedRect(ctx, bx + bw - 174,  btnY, 160, 22, 8,
                caHov ? 0x66143C6E : 0x33091E46);
        SkyzRenderHelper.drawRoundedBorder(ctx, bx + bw - 174, btnY, 160, 22, 8,
                caHov ? 0x998CDCFF : 0x4D8CDCFF);

        ctx.drawCenteredString(font, "Delete forever",
                bx + 94, btnY + 7, delHov ? 0xFFFFFFFF : 0xCCFFAAAA);
        ctx.drawCenteredString(font, "Cancel",
                bx + bw - 94, btnY + 7, caHov ? 0xFFFFFFFF : SkyzColors.TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent MouseButtonEvent, boolean doubled) {
        if (deletePending != null) {
            double mx = MouseButtonEvent.x(), my = MouseButtonEvent.y();
            int bw = 360, bh = 130, bx = (width - bw) / 2, by = (height - bh) / 2;
            int btnY = by + bh - 32;
            if (mx >= bx + 14 && mx <= bx + 174 && my >= btnY && my <= btnY + 22) {
                LevelSummary w = deletePending;
                deletePending = null;
                deleteWorld(w);
                return true;
            }
            if (mx >= bx + bw - 174 && mx <= bx + bw - 14 && my >= btnY && my <= btnY + 22) {
                deletePending = null;
                return true;
            }
            return true;  // swallow other clicks while modal open
        }
        return super.mouseClicked(MouseButtonEvent, doubled);
    }

    @Override
    public void onClose() {
        if (deletePending != null) { deletePending = null; return; }
        if (client != null) client.setScreen(parent);
    }
}
