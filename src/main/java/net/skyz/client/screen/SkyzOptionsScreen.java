package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.TelemetryInfoScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.lang.reflect.Constructor;
import java.util.function.Function;

/**
 * Skyz Options (top-level) — owo-lib edition (Phase 2d port).
 *
 * <p>Replaces vanilla {@code OptionsScreen} via
 * {@link net.skyz.client.mixin.OptionsScreenMixin}. This is purely a
 * routing screen: each button opens a vanilla sub-screen
 * (Skin / Sound / Video / Controls / Language / Chat / Resource Packs /
 * Accessibility / Telemetry / Credits) so all the underlying
 * {@code OptionInstance<T>}-driven settings keep working untouched.
 *
 * <p>The {@code parent} screen is what we return to on Done — typically
 * the title screen or pause menu, since vanilla constructs OptionsScreen
 * from both.
 */
public class SkyzOptionsScreen extends BaseUIModelScreen<FlowLayout> {

    /**
     * Sodium presence check. When Sodium is installed the Video button
     * opens Sodium's own settings GUI (its replacement for the vanilla
     * Video Settings screen) — same behaviour the user gets in vanilla
     * play, where Sodium's mixin on the Options screen swaps the MouseButtonEvent
     * target. Our SkyzOptionsScreen replaces the vanilla one, so Sodium's
     * mixin doesn't fire and we have to do the swap ourselves.
     */
    private static final boolean HAS_SODIUM =
            FabricLoader.getInstance().isModLoaded("sodium");
    private static final boolean HAS_IRIS =
            FabricLoader.getInstance().isModLoaded("iris");
    private static final boolean HAS_DH =
            FabricLoader.getInstance().isModLoaded("distanthorizons");
    private static final boolean HAS_MODMENU =
            FabricLoader.getInstance().isModLoaded("modmenu");

    private final Screen  parent;
    private final Options options;

    public SkyzOptionsScreen(Screen parent, Options options) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "options"));
        this.parent  = parent;
        this.options = options;
    }

    // ─── Build ───────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        wire(root, "btn-back", () -> onClose(), SkyzButtonRenderer.NAV_BACK);

        // Style the surrounding scroll so its scrollbar matches the rest
        // of the Skyz screens. Without this the bar reverts to vanilla
        // styling which clashes with the blue theme.
        io.wispforest.owo.ui.container.ScrollContainer<?> scroll =
                root.childById(io.wispforest.owo.ui.container.ScrollContainer.class, "scroll-options");
        if (scroll != null) {
            scroll.scrollbar(io.wispforest.owo.ui.container.ScrollContainer.Scrollbar.flat(
                    Color.ofArgb(0xCC8CD2FF)));
        }

        FlowLayout card = root.childById(FlowLayout.class, "card");
        if (card == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Options: card flow not found.");
            return;
        }
        card.surface(SkyzSurface.CARD);

        // Header.
        LabelComponent header = UIComponents.label(Component.literal("⚙  GAME OPTIONS"))
                .color(Color.ofArgb(0xFFEEF6FF));
        card.child(header);
        FlowLayout divider = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x338CD2FF));
        divider.margins(Insets.bottom(8));
        card.child(divider);

        // Mod shortcuts row — mirrors vanilla's behaviour of pinning a
        // Distant Horizons (and other big-mod) button to the top of the
        // options screen when the mod is installed. Each button opens the
        // mod's own config screen directly — no need to drill through
        // Video Settings to reach Sodium etc. Row only renders the
        // shortcuts for mods actually present, so people without any of
        // these installed don't see an empty row.
        FlowLayout shortcutsRow = buildModShortcutsRow();
        if (shortcutsRow != null) {
            card.child(shortcutsRow);
            // Thin sub-divider below shortcuts so they read as a separate
            // section from the routing grid below.
            FlowLayout subDiv = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
            subDiv.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                    comp.x() + comp.width(), comp.y() + comp.height(), 0x228CD2FF));
            subDiv.margins(Insets.vertical(6));
            card.child(subDiv);
        }

        // Routing rows — each row is a 2-col grid of buttons. Vanilla's
        // OptionsScreen lays them out in a 2-column grid; we match that.
        Object[][] rows = {
                {"🧍  SKIN CUSTOMIZATION",  (ButtonAction) () -> open(p -> new SkinCustomizationScreen(p, options)),
                 "🔊  SOUND",                (ButtonAction) () -> open(p -> new SoundOptionsScreen(p, options))},
                {"🖥  VIDEO",                (ButtonAction) this::openVideoSettings,
                 "🎮  CONTROLS",             (ButtonAction) () -> open(p -> new ControlsScreen(p, options))},
                {"🌐  LANGUAGE",             (ButtonAction) () -> open(p -> new LanguageSelectScreen(
                        p, options, Minecraft.getInstance().getLanguageManager())),
                 "💬  CHAT",                 (ButtonAction) () -> open(p -> new ChatOptionsScreen(p, options))},
                {"📦  RESOURCE PACKS",       (ButtonAction) this::openResourcePacks,
                 "♿  ACCESSIBILITY",        (ButtonAction) () -> open(p -> new AccessibilityOptionsScreen(p, options))},
                {"📡  TELEMETRY",            (ButtonAction) () -> open(p -> new TelemetryInfoScreen(p, options)),
                 "ℹ  CREDITS & ATTRIBUTION", (ButtonAction) this::openCredits},
        };
        for (Object[] row : rows) {
            FlowLayout flow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            flow.gap(6);
            flow.verticalAlignment(VerticalAlignment.CENTER);
            flow.child(buildRouteButton((String) row[0], (ButtonAction) row[1]));
            flow.child(buildRouteButton((String) row[2], (ButtonAction) row[3]));
            card.child(flow);
        }

        // Done button at the bottom.
        FlowLayout doneRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        doneRow.margins(Insets.top(8));
        doneRow.horizontalAlignment(io.wispforest.owo.ui.core.HorizontalAlignment.CENTER);
        ButtonComponent done = UIComponents.button(Component.literal("DONE"), b -> onClose())
                .renderer(SkyzButtonRenderer.DEFAULT);
        done.horizontalSizing(Sizing.fixed(280));
        done.verticalSizing(Sizing.fixed(22));
        doneRow.child(done);
        card.child(doneRow);
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Options: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    private FlowLayout buildRouteButton(String label, ButtonAction action) {
        FlowLayout cell = UIContainers.horizontalFlow(Sizing.fill(49), Sizing.content());
        ButtonComponent btn = UIComponents.button(Component.literal(label), b -> action.run())
                .renderer(SkyzButtonRenderer.NAV_BACK);
        btn.horizontalSizing(Sizing.fill(100));
        btn.verticalSizing(Sizing.fixed(22));
        cell.child(btn);
        return cell;
    }

    // ─── Routing ─────────────────────────────────────────────────────────
    @FunctionalInterface
    private interface ButtonAction { void run(); }

    private void open(Function<Screen, Screen> screenFactory) {
        Minecraft.getInstance().setScreen(screenFactory.apply(this));
    }

    /**
     * Resource Packs sub-screen — vanilla {@link PackSelectionScreen} needs the
     * pack manager and a "what to do on close" applier.
     *
     * <p>Two important non-obvious things this lambda has to do that the
     * earlier version of this method skipped:
     * <ul>
     *   <li><b>Navigate back to ourselves.</b> {@code PackSelectionScreen.onClose()}
     *       only runs the applier + cleans up its directory watcher — it
     *       does NOT call {@code setScreen(parent)}. That responsibility
     *       belongs to the applier. Without our own {@code mc.setScreen(this)},
     *       Done/Escape on the pack screen left the user at whatever MC
     *       restored after the resource reload (typically title screen,
     *       which looked like "the game reloaded but didn't go back").</li>
     *   <li><b>Only reload when the pack list actually changed.</b>
     *       Calling {@code reloadResourcePacks()} unconditionally triggers
     *       the loading screen even when the user just looked at the
     *       packs and pressed Done without changes. Vanilla diffs the
     *       new list against the old one and skips the reload otherwise.</li>
     * </ul>
     */
    private void openResourcePacks() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new PackSelectionScreen(
                mc.getResourcePackRepository(),
                manager -> {
                    java.util.List<String> oldList = new java.util.ArrayList<>(options.resourcePacks);
                    java.util.List<String> newList = new java.util.ArrayList<>();
                    options.resourcePacks.clear();
                    options.incompatibleResourcePacks.clear();
                    for (var profile : manager.getSelectedPacks()) {
                        if (!profile.isFixedPosition()) {
                            newList.add(profile.getId());
                            if (!profile.getCompatibility().isCompatible()) {
                                options.incompatibleResourcePacks.add(profile.getId());
                            }
                        }
                    }
                    options.resourcePacks.addAll(newList);
                    options.save();

                    // Always navigate back to ourselves — PackSelectionScreen.onClose()
                    // doesn't do this on its own.
                    mc.setScreen(this);

                    // Only reload if the pack set actually changed. The async
                    // reload kicks in a loading screen; restoring to `this`
                    // first ensures we land back on the Options screen once
                    // the reload finishes.
                    if (!newList.equals(oldList)) {
                        mc.reloadResourcePacks();
                    }
                },
                mc.getResourcePackDirectory(),
                Component.translatable("resourcePack.title")));
    }

    private void openCredits() {
        Minecraft.getInstance().setScreen(
                new CreditsAndAttributionScreen(this));
    }

    /**
     * Video settings router. With Sodium installed we open Sodium's own
     * video settings GUI (Sodium's full vanilla-replacement screen). With
     * neither installed, falls through to vanilla {@link VideoSettingsScreen}.
     * Logs at WARN if Sodium is present but the open attempt fails so the
     * failure is visible without a debugger attached.
     */
    private void openVideoSettings() {
        if (HAS_SODIUM) {
            if (tryOpenSodiumOptions(this)) return;
            SkyzClientMod.LOGGER.warn(
                    "[Skyz] Sodium is installed but its options screen could not be opened "
                            + "— falling back to vanilla Video Settings. Check that your Sodium "
                            + "version exposes SodiumOptionsGUI(Screen) or a ModMenu factory.");
        }
        open(p -> new VideoSettingsScreen(p, options));
    }

    /**
     * Builds the top "mod shortcuts" container. Returns null if no
     * shortcut-eligible mod is installed so the caller can skip the row +
     * divider entirely.
     *
     * <p>Buttons are laid out in a 2-column grid — earlier the labels were
     * crammed into a single horizontal flow which truncated text like
     * "DISTANT HORIZONS" to ellipsis on a 420-wide card. 2-col with
     * fill(49) cells gives each label ~200px of comfortable width.
     */
    private FlowLayout buildModShortcutsRow() {
        java.util.List<ShortcutDef> shortcuts = new java.util.ArrayList<>();
        if (HAS_SODIUM) shortcuts.add(new ShortcutDef("⚡  SODIUM",
                () -> { if (!tryOpenSodiumOptions(this)) toastFail("Sodium"); }));
        if (HAS_IRIS) shortcuts.add(new ShortcutDef("🌅  IRIS",
                () -> { if (!tryOpenIrisOptions(this)) toastFail("Iris"); }));
        if (HAS_DH) shortcuts.add(new ShortcutDef("🌄  DISTANT HORIZONS",
                () -> { if (!tryOpenDistantHorizonsOptions(this)) toastFail("Distant Horizons"); }));
        if (HAS_MODMENU) shortcuts.add(new ShortcutDef("📋  MOD MENU",
                () -> { if (!tryOpenModMenu(this)) toastFail("Mod Menu"); }));

        if (shortcuts.isEmpty()) return null;

        // Container is a vertical flow of horizontal 2-col rows.
        FlowLayout container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(4);
        for (int i = 0; i < shortcuts.size(); i += 2) {
            FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(6);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(buildShortcutButton(shortcuts.get(i)));
            if (i + 1 < shortcuts.size()) {
                row.child(buildShortcutButton(shortcuts.get(i + 1)));
            } else {
                // Pad the trailing half so the single button doesn't expand
                // to full row width.
                row.child(UIContainers.horizontalFlow(Sizing.fill(49), Sizing.fixed(22)));
            }
            container.child(row);
        }
        return container;
    }

    private FlowLayout buildShortcutButton(ShortcutDef s) {
        FlowLayout cell = UIContainers.horizontalFlow(Sizing.fill(49), Sizing.content());
        ButtonComponent btn = UIComponents.button(Component.literal(s.label), b -> s.action.run())
                .renderer(SkyzButtonRenderer.DEFAULT);
        btn.horizontalSizing(Sizing.fill(100));
        btn.verticalSizing(Sizing.fixed(22));
        cell.child(btn);
        return cell;
    }

    private record ShortcutDef(String label, Runnable action) {}

    private void toastFail(String name) {
        SkyzClientMod.LOGGER.warn("[Skyz] Could not open {} config screen.", name);
    }

    /**
     * Generic ModMenu factory lookup. ModMenu keeps a registry of config
     * screen factories keyed by mod ID — when present this is the cleanest
     * way to reach a mod's config screen because the mod itself controls
     * the factory. Falls back to {@code null} on any failure so callers can
     * try a direct-reflection path.
     *
     * <p>ModMenu's method has been renamed across versions
     * ({@code createConfigScreen} vs {@code getConfigScreen}) — we probe
     * both.
     */
    private static Screen openViaModMenu(String modId, Screen parent) {
        if (!HAS_MODMENU) return null;
        try {
            Class<?> mmClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
            // Try both known method names. Both take (modId, parentScreen).
            for (String methodName : new String[]{"getConfigScreen", "createConfigScreen"}) {
                try {
                    java.lang.reflect.Method m = mmClass.getMethod(methodName, String.class, Screen.class);
                    Object result = m.invoke(null, modId, parent);
                    if (result instanceof Screen s) return s;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            SkyzClientMod.LOGGER.debug("[Skyz] ModMenu factory lookup for {} failed: {}",
                    modId, e.getMessage());
        }
        return null;
    }

    /**
     * Tries to open Sodium's options GUI. Sodium 0.8+ made its
     * {@code VideoSettingsScreen(Screen)} constructor private and exposes a
     * public static {@code createScreen(Screen)} factory as the canonical
     * entry point — that's the path we try first.
     *
     * <p>Confirmed against {@code sodium-fabric-0.8.7+mc1.21.11.jar}:
     * <pre>
     *   private VideoSettingsScreen(Screen);   // not directly callable
     *   public static Screen createScreen(Screen);  // ← THIS is the API
     * </pre>
     *
     * <p>Strategy:
     * <ol>
     *   <li>{@code ModMenu.getConfigScreen("sodium", parent)} — only works
     *       if Sodium has registered a ModMenu factory; recent Sodium ships
     *       without ModMenu integration so this typically returns null.</li>
     *   <li>{@code VideoSettingsScreen.createScreen(Screen)} — the public
     *       factory, intended API for Sodium 0.8+.</li>
     *   <li>{@code SodiumOptionsGUI(Screen)} reflective ctor — legacy fallback
     *       for older Sodium versions that exposed a public constructor.</li>
     * </ol>
     */
    public static boolean tryOpenSodiumOptions(Screen prev) {
        if (!HAS_SODIUM) {
            SkyzClientMod.LOGGER.info("[Skyz] Sodium not loaded; skipping open.");
            return false;
        }

        // 1. ModMenu factory.
        Screen viaMM = openViaModMenu("sodium", prev);
        if (viaMM != null) {
            SkyzClientMod.LOGGER.info("[Skyz] Opening Sodium via ModMenu factory ({}).",
                    viaMM.getClass().getName());
            Minecraft.getInstance().setScreen(viaMM);
            return true;
        }

        // 2. Public static factory — Sodium 0.6+ (and 0.8+) ships
        // {@code VideoSettingsScreen.createScreen(Screen)} as the intended
        // public entry point. Older Sodium (with SodiumOptionsGUI) doesn't
        // have this method; we silently fall through to step 3.
        String[] factoryClasses = {
                "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen",
                "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"
        };
        for (String className : factoryClasses) {
            try {
                Class<?> cls = Class.forName(className);
                java.lang.reflect.Method m = cls.getMethod("createScreen", Screen.class);
                Object result = m.invoke(null, prev);
                if (result instanceof Screen s) {
                    Minecraft.getInstance().setScreen(s);
                    SkyzClientMod.LOGGER.info("[Skyz] Opened Sodium via {}.createScreen(Screen).", className);
                    return true;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // try next
            } catch (Exception e) {
                SkyzClientMod.LOGGER.warn("[Skyz] Sodium {}.createScreen failed: {}",
                        className, e.toString());
            }
        }

        // 3. Legacy public-ctor fallback for very old Sodium versions.
        String[] ctorCandidates = {
                "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI",
                "me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI"
        };
        for (String className : ctorCandidates) {
            try {
                Class<?> cls = Class.forName(className);
                Constructor<?> ctor = cls.getDeclaredConstructor(Screen.class);
                ctor.setAccessible(true);
                Screen sodiumScreen = (Screen) ctor.newInstance(prev);
                Minecraft.getInstance().setScreen(sodiumScreen);
                SkyzClientMod.LOGGER.info("[Skyz] Opened Sodium via legacy {}(Screen) ctor.", className);
                return true;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // try next
            } catch (Exception e) {
                SkyzClientMod.LOGGER.warn("[Skyz] Sodium {}(Screen) failed: {}",
                        className, e.toString());
            }
        }

        SkyzClientMod.LOGGER.warn(
                "[Skyz] Could not find any usable Sodium options entry point. "
                        + "Please report your Sodium version so we can add its class path.");
        return false;
    }

    /**
     * Tries to open Iris's shader picker screen. Iris ships
     * {@code net.irisshaders.iris.gui.screen.ShaderPackScreen(Screen)} as a
     * stable entry point.
     */
    public static boolean tryOpenIrisOptions(Screen prev) {
        if (!HAS_IRIS) return false;
        Screen viaMM = openViaModMenu("iris", prev);
        if (viaMM != null) {
            Minecraft.getInstance().setScreen(viaMM);
            return true;
        }
        try {
            Class<?> cls = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            Constructor<?> ctor = cls.getConstructor(Screen.class);
            Screen iris = (Screen) ctor.newInstance(prev);
            Minecraft.getInstance().setScreen(iris);
            return true;
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] Iris open failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Tries to open Distant Horizons' config screen. DH ships its config
     * GUI through a custom framework and exposes a {@code ModMenu} factory,
     * which is by far the cleanest way to reach it — direct-reflection
     * paths would be brittle across DH versions. If ModMenu isn't present
     * we attempt a couple of known DH class paths and finally give up.
     */
    public static boolean tryOpenDistantHorizonsOptions(Screen prev) {
        if (!HAS_DH) return false;
        Screen viaMM = openViaModMenu("distanthorizons", prev);
        if (viaMM != null) {
            Minecraft.getInstance().setScreen(viaMM);
            return true;
        }

        // Fallback class paths — DH 2.x uses internal config UI classes that
        // change between versions. These are best-effort.
        String[] candidates = {
                "com.seibel.distanthorizons.core.config.gui.QuickEnableScreen",
                "com.seibel.distanthorizons.core.config.gui.ConfigScreen",
                "com.seibel.distanthorizons.fabric.modSupport.FabricModConfigScreenFactory"
        };
        for (String className : candidates) {
            try {
                Class<?> cls = Class.forName(className);
                try {
                    Constructor<?> ctor = cls.getDeclaredConstructor(Screen.class);
                    ctor.setAccessible(true);
                    Screen dh = (Screen) ctor.newInstance(prev);
                    Minecraft.getInstance().setScreen(dh);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // Try a no-arg ctor.
                    try {
                        Constructor<?> noArg = cls.getDeclaredConstructor();
                        noArg.setAccessible(true);
                        Object inst = noArg.newInstance();
                        if (inst instanceof Screen s) {
                            Minecraft.getInstance().setScreen(s);
                            return true;
                        }
                    } catch (NoSuchMethodException ignored2) {}
                }
            } catch (ClassNotFoundException ignored) {
                // try next
            } catch (Exception e) {
                SkyzClientMod.LOGGER.warn(
                        "[Skyz] Distant Horizons open ({}) failed: {}",
                        className, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Open the ModMenu mod-list screen if installed. Used by the top
     * shortcut row when ModMenu is present.
     */
    public static boolean tryOpenModMenu(Screen prev) {
        if (!HAS_MODMENU) return false;
        try {
            Class<?> apiClass = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
            java.lang.reflect.Method m = apiClass.getMethod("createModsScreen", Screen.class);
            Object result = m.invoke(null, prev);
            if (result instanceof Screen s) {
                Minecraft.getInstance().setScreen(s);
                return true;
            }
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] ModMenu open failed: {}", e.getMessage());
        }
        return false;
    }

    // ─── Lifecycle / render ──────────────────────────────────────────────
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // No-op: render() handles bg.
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
