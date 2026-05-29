package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Skyz Shaders screen — owo-lib edition (Phase 2c port).
 *
 * <h3>Why the freeze went away</h3>
 *
 * The old screen used {@code CompletableFuture.runAsync(this::loadShaderPacks)}
 * which dispatches to {@link java.util.concurrent.ForkJoinPool#commonPool()}.
 * That pool's first reflective call into Iris triggers Iris class loading,
 * and Fabric's Knot loader can serialise class-load locks across all threads
 * in the pool. The render thread, which lazily resolves transitively-referenced
 * Iris types when laying out the new screen, ends up waiting on the FJP
 * worker. The result is a 200-700 ms freeze right after opening the screen.
 *
 * <p>Fix: same pattern as {@link SkyzMultiplayerScreen}'s pinger — a
 * dedicated <b>daemon thread</b> for the load (so it can't block JVM
 * shutdown), and a {@link Minecraft#execute(Runnable)} hop back to
 * the render thread before mutating any owo component state. Apply
 * (which calls {@code Iris.reload()}) goes through the same daemon path
 * since reload was a second freeze hotspot.
 *
 * <h3>Layout</h3>
 *
 * Two-pane horizontal split inside the XML chrome — left is a fixed-260
 * scrollable list of {@link ShaderPack} cards, right is a flex-width
 * {@code FlowLayout} that gets re-populated when selection changes or
 * load completes. List + info panel are both built programmatically
 * because their contents are async/state-dependent.
 *
 * <p>Iris integration is reflective. Iris ships no stable public API for
 * opening its picker screen, so we look up
 * {@code net.irisshaders.iris.gui.screen.ShaderPackScreen(Screen)} and
 * instantiate it. {@code Iris.getIrisConfig().getShaderPackName()} gives
 * us the active pack name; {@code setShaderPackName + save + Iris.reload}
 * applies a new one.
 */
public class SkyzShadersScreen extends BaseUIModelScreen<FlowLayout> {
    /** Alias for the inherited Minecraft instance (26.1 renamed the Screen field client->minecraft). */
    private final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();


    private static final boolean HAS_IRIS =
            FabricLoader.getInstance().isModLoaded("iris");

    /**
     * One row in the shader list. {@code fileName} is what we feed back to
     * Iris's {@code setShaderPackName} (so {@code "BSL Shaders.zip"}, not
     * {@code "BSL Shaders"}). The sentinel {@code "(off)"} maps to the
     * "no shaders" entry.
     */
    record ShaderPack(String name, String fileName, boolean isEnabled) {}

    private final SkyzTitleScreen parent;

    /** Replaced atomically when load completes. Read by the render path. */
    private volatile List<ShaderPack> packs = new ArrayList<>();
    private volatile boolean loading = true;
    private volatile boolean applying = false;
    private int selectedPack = 0;

    // Owo refs (resolved in build()).
    private FlowLayout listContainer;
    private FlowLayout infoPanel;
    private LabelComponent irisStatusLabel;

    public SkyzShadersScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "shaders"));
        this.parent = parent;
    }

    // ─── Build ───────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        wire(root, "btn-back",      () -> client.setScreen(parent), SkyzButtonRenderer.NAV_BACK);
        wire(root, "btn-refresh",   this::scheduleLoad,             SkyzButtonRenderer.NAV_BACK);

        ButtonComponent openIris = root.childById(ButtonComponent.class, "btn-open-iris");
        if (openIris != null) {
            openIris.renderer(SkyzButtonRenderer.DEFAULT);
            openIris.onPress(b -> {
                if (!openIrisShaderScreen() && parent != null) {
                    parent.toast("Could not open Iris screen.");
                }
            });
            openIris.active(HAS_IRIS);
        }

        irisStatusLabel = root.childById(LabelComponent.class, "lbl-iris-status");
        if (irisStatusLabel != null) {
            irisStatusLabel.text(Component.literal(HAS_IRIS ? "🌅 Iris Active" : "⚠ Iris not installed"));
            irisStatusLabel.color(Color.ofArgb(HAS_IRIS ? 0xFF8CD2FF : 0xFFFF9944));
        }

        ScrollContainer<?> scroll = root.childById(ScrollContainer.class, "scroll-list");
        if (scroll != null) {
            scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xCC8CD2FF)));
        }

        listContainer = root.childById(FlowLayout.class, "shader-list");
        infoPanel     = root.childById(FlowLayout.class, "info-panel");
        if (infoPanel != null) infoPanel.surface(SkyzSurface.CARD);

        rebuildList();
        rebuildInfoPanel();

        // Kick off the initial async load.
        scheduleLoad();
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Shaders: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    // ─── Async load ──────────────────────────────────────────────────────
    /**
     * Reads {@code shaderpacks/} off disk and queries Iris for the active
     * pack name on a daemon thread, then hops back to the render thread
     * via {@link Minecraft#execute(Runnable)} to mutate owo state.
     *
     * <p>Daemon thread (vs {@code CompletableFuture.runAsync}) avoids the
     * common ForkJoinPool whose worker can deadlock against the render
     * thread on Iris class loading.
     */
    private void scheduleLoad() {
        if (loading) return;   // already running
        loading = true;
        rebuildList();         // show "Loading shader packs..."

        Thread t = new Thread(() -> {
            List<ShaderPack> result = doLoadShaderPacks();
            postToRenderThread(() -> {
                packs = result;
                loading = false;
                if (selectedPack >= packs.size()) selectedPack = 0;
                rebuildList();
                rebuildInfoPanel();
            });
        }, "skyz-shaders-load");
        t.setDaemon(true);
        t.start();
    }

    /** Disk + reflection work — must run off the render thread. */
    private List<ShaderPack> doLoadShaderPacks() {
        List<ShaderPack> result = new ArrayList<>();
        String activeName = getActiveShaderpackName();

        File shaderpacksDir = new File(Minecraft.getInstance().gameDirectory, "shaderpacks");
        if (shaderpacksDir.exists() && shaderpacksDir.isDirectory()) {
            File[] files = shaderpacksDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    if (n.endsWith(".zip") || n.endsWith(".txt") || f.isDirectory()) {
                        String displayName = n.replace(".zip", "").replace(".txt", "");
                        boolean active = displayName.equals(activeName) || n.equals(activeName);
                        result.add(new ShaderPack(displayName, n, active));
                    }
                }
            }
        }

        // "Off (vanilla)" entry at the top — sentinel filename "(off)".
        boolean noneActive = activeName == null || activeName.isEmpty() || activeName.equals("(off)");
        result.add(0, new ShaderPack("No Shaders (Vanilla)", "(off)", noneActive));
        return result;
    }

    /**
     * Hops back to the render thread before mutating owo component state.
     * Skips the action if the user has already navigated away from this
     * screen (so we don't poke into a discarded component tree).
     */
    private void postToRenderThread(Runnable r) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen != this) return;
            r.run();
        });
    }

    // ─── List / info panel rebuilders ────────────────────────────────────
    private void rebuildList() {
        if (listContainer == null) return;
        listContainer.clearChildren();

        if (loading) {
            listContainer.child(buildPlaceholderRow(applying
                    ? "Applying shader pack..."
                    : "Loading shader packs..."));
            return;
        }

        if (packs.isEmpty()) {
            listContainer.child(buildPlaceholderRow("No shader packs found"));
            return;
        }

        for (int i = 0; i < packs.size(); i++) {
            listContainer.child(buildPackCard(i, packs.get(i)));
        }
    }

    private FlowLayout buildPlaceholderRow(String text) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        row.padding(Insets.vertical(14));
        row.horizontalAlignment(HorizontalAlignment.CENTER);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(UIComponents.label(Component.literal(text))
                .color(Color.ofArgb(SkyzColors.TEXT_MUTED)));
        return row;
    }

    private FlowLayout buildPackCard(int idx, ShaderPack p) {
        boolean selected = idx == selectedPack;

        FlowLayout card = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(48));
        card.gap(8);
        card.padding(Insets.of(6, 6, 10, 10));
        card.verticalAlignment(VerticalAlignment.CENTER);
        card.surface(packCardSurface(selected, p.isEnabled()));

        // Active strip on the left for the currently-loaded pack.
        FlowLayout strip = UIContainers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
        if (p.isEnabled()) {
            strip.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                    comp.x() + comp.width(), comp.y() + comp.height(), 0xFF8CD2FF));
        }
        card.child(strip);

        // Icon.
        String icon = p.fileName().equals("(off)") ? "🟫" : "🌅";
        card.child(UIComponents.label(Component.literal(icon))
                .horizontalSizing(Sizing.fixed(14)));

        // Name + filename column.
        FlowLayout col = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content());
        col.gap(2);
        col.child(UIComponents.label(Component.literal(truncate(p.name(), 26)))
                .color(Color.ofArgb(p.isEnabled() ? 0xFF8CD2FF : SkyzColors.TEXT_PRIMARY)));
        col.child(UIComponents.label(Component.literal(p.isEnabled() ? "✦ Active" : truncate(p.fileName(), 30)))
                .color(Color.ofArgb(p.isEnabled() ? 0x668CD2FF : 0x558CD2FF)));
        card.child(col);

        // Apply button (always visible — clearer than hover-revealed).
        ButtonComponent apply = UIComponents
                .button(Component.literal(p.isEnabled() ? "Active" : "Apply"),
                        b -> { if (!p.isEnabled()) applyShaderPack(p.fileName()); })
                .renderer(p.isEnabled() ? SkyzButtonRenderer.NAV_BACK : SkyzButtonRenderer.DEFAULT);
        apply.horizontalSizing(Sizing.fixed(48));
        apply.verticalSizing(Sizing.fixed(18));
        apply.active(!p.isEnabled() && !loading && !applying);
        card.child(apply);

        // MouseButtonEvent anywhere on the card to select for the right-pane info.
        final int row = idx;
        card.mouseDown().subscribe((MouseButtonEvent, doubled) -> {
            if (selectedPack != row) {
                selectedPack = row;
                rebuildList();
                rebuildInfoPanel();
            }
            return true;
        });

        return card;
    }

    private static Surface packCardSurface(boolean selected, boolean enabled) {
        return (ctx, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            int bg     = selected ? 0xCC143C6E
                       : enabled  ? 0x66143C6E
                                  : 0x55091E46;
            int border = selected ? 0xCC8CDCFF
                       : enabled  ? 0x778CD2FF
                                  : 0x338CD2FF;
            SkyzRenderHelper.fillRoundedRect(ctx, x, y, w, h, 4, bg);
            SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, 4, border);
        };
    }

    private void rebuildInfoPanel() {
        if (infoPanel == null) return;
        infoPanel.clearChildren();

        infoPanel.child(UIComponents.label(Component.literal("PACK INFO"))
                .color(Color.ofArgb(0xCC8CD2FF)));
        FlowLayout divider = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x338CD2FF));
        divider.margins(Insets.bottom(6));
        infoPanel.child(divider);

        ShaderPack sel = packs.isEmpty() ? null
                : (selectedPack < packs.size() ? packs.get(selectedPack) : null);

        if (sel != null) {
            infoPanel.child(UIComponents.label(Component.literal(sel.name()))
                    .color(Color.ofArgb(0xFF8CD2FF)));
            infoPanel.child(UIComponents.label(Component.literal("File: " + sel.fileName()))
                    .color(Color.ofArgb(0x4D8CD2FF)));
            infoPanel.child(UIComponents.label(Component.literal(
                            "Status: " + (sel.isEnabled() ? "✦ Active" : "Inactive")))
                    .color(Color.ofArgb(sel.isEnabled() ? 0xFF8CD2FF : SkyzColors.TEXT_MUTED)));
        }

        // Spacer
        FlowLayout gap = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(8));
        infoPanel.child(gap);

        if (!HAS_IRIS) {
            // Install-Iris call-out.
            FlowLayout warn = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            warn.padding(Insets.of(6, 6, 8, 8));
            warn.gap(2);
            warn.surface((ctx, comp) -> {
                int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                SkyzRenderHelper.fillRoundedRect(ctx, x, y, w, h, 4, 0x33FF9900);
                SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, 4, 0x66FF9900);
            });
            warn.child(UIComponents.label(Component.literal("⚠ Iris Shaders not installed"))
                    .color(Color.ofArgb(0xFFFF9944)));
            warn.child(UIComponents.label(Component.literal("To use shader packs, install:"))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED)));
            warn.child(UIComponents.label(Component.literal("Iris + Sodium from modrinth.com"))
                    .color(Color.ofArgb(0x668CD2FF)));
            warn.child(UIComponents.label(Component.literal("modrinth.com/mod/iris"))
                    .color(Color.ofArgb(0x558CD2FF)));
            infoPanel.child(warn);
        } else {
            infoPanel.child(UIComponents.label(Component.literal("Shaderpacks folder:"))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED)));
            String path = new File(Minecraft.getInstance().gameDirectory, "shaderpacks")
                    .getAbsolutePath();
            infoPanel.child(UIComponents.label(Component.literal(truncate(path, 60)))
                    .color(Color.ofArgb(0x558CD2FF)));

            FlowLayout gap2 = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(6));
            infoPanel.child(gap2);

            infoPanel.child(UIComponents.label(Component.literal("Quick tips:"))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED)));
            infoPanel.child(UIComponents.label(Component.literal("· Drop .zip files into shaderpacks/"))
                    .color(Color.ofArgb(0x778CD2FF)));
            infoPanel.child(UIComponents.label(Component.literal("· Use 'Refresh' to reload the list"))
                    .color(Color.ofArgb(0x778CD2FF)));
            infoPanel.child(UIComponents.label(Component.literal("· 'Open Full Iris Screen' for full settings"))
                    .color(Color.ofArgb(0x778CD2FF)));
        }
    }

    // ─── Apply (also async) ──────────────────────────────────────────────
    /**
     * Set the active shader pack via Iris reflection, then reload Iris
     * and refresh our pack list. All on a daemon thread because
     * {@code Iris.reload()} can take 100s of ms — same hop-back to
     * render thread before mutating owo state.
     */
    private void applyShaderPack(String packName) {
        if (!HAS_IRIS) {
            if (parent != null) parent.toast("Install Iris Shaders to use shader packs!");
            return;
        }
        if (applying) return;
        applying = true;
        loading = true;       // shows the placeholder row
        rebuildList();

        Thread t = new Thread(() -> {
            String error = null;
            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                Method getConfig = irisClass.getMethod("getIrisConfig");
                Object config    = getConfig.invoke(null);

                if (packName.equals("(off)")) {
                    // Iris has a couple of API drift points around this method;
                    // the apiimpl variant is the most stable one across Iris
                    // versions for 1.21.x.
                    try {
                        Method setShadersEnabled = config.getClass()
                                .getMethod("setShadersEnabledAndApply", boolean.class);
                        setShadersEnabled.invoke(config, false);
                    } catch (NoSuchMethodException ignored) {
                        // Older Iris: just set name to empty + reload.
                        Method setName = config.getClass().getMethod("setShaderPackName", String.class);
                        setName.invoke(config, "");
                        Method save = config.getClass().getMethod("save");
                        save.invoke(config);
                        Method reload = irisClass.getMethod("reload");
                        reload.invoke(null);
                    }
                } else {
                    Method setName = config.getClass().getMethod("setShaderPackName", String.class);
                    setName.invoke(config, packName);
                    Method save = config.getClass().getMethod("save");
                    save.invoke(config);
                    Method reload = irisClass.getMethod("reload");
                    reload.invoke(null);
                }
            } catch (Exception e) {
                error = e.getMessage();
                SkyzClientMod.LOGGER.warn("[Skyz] Apply shader '{}' failed: {}", packName, e.getMessage());
            }

            // Re-fetch the active pack name now that Iris has reloaded.
            List<ShaderPack> result = doLoadShaderPacks();
            String finalError = error;
            postToRenderThread(() -> {
                packs = result;
                loading = false;
                applying = false;
                rebuildList();
                rebuildInfoPanel();
                if (parent != null) {
                    if (finalError != null) {
                        parent.toast("Apply failed: " + finalError);
                    } else {
                        parent.toast("Applied: " +
                                (packName.equals("(off)") ? "No Shaders" : packName));
                    }
                }
            });
        }, "skyz-shaders-apply");
        t.setDaemon(true);
        t.start();
    }

    // ─── Iris helpers ────────────────────────────────────────────────────
    private String getActiveShaderpackName() {
        if (!HAS_IRIS) return null;
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method getConfig   = irisClass.getMethod("getIrisConfig");
            Object config      = getConfig.invoke(null);
            Method getName     = config.getClass().getMethod("getShaderPackName");
            Object result      = getName.invoke(config);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean openIrisShaderScreen() {
        if (!HAS_IRIS) return false;
        try {
            Class<?> screenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            Constructor<?> ctor = screenClass.getConstructor(Screen.class);
            Screen shaderScreen = (Screen) ctor.newInstance(this);
            Minecraft.getInstance().setScreen(shaderScreen);
            return true;
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] Could not open Iris screen: {}", e.getMessage());
            return false;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }

    // ─── Lifecycle / render ──────────────────────────────────────────────
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { if (client != null) client.setScreen(parent); }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // No-op — render() handles the background.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (parent != null) parent.toast.render(ctx, width, delta);
    }
}
