package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Skyz Cosmetics screen — owo-lib edition.
 *
 * <p>Cosmetics in this ecosystem are Essential's Wardrobe. We detect
 * Essential via FabricLoader and either surface a Skyz-styled launcher
 * card with an "OPEN ESSENTIAL WARDROBE" button, or an install prompt
 * if Essential isn't loaded.
 *
 * <p>The opener follows the same defensive pattern as
 * {@link SkyzFriendsScreen#openEssentialSocial()}: try several known
 * class-name candidates with explicit no-arg constructor, falling back
 * through Essential's own {@code GuiUtil} helpers. Errors surface inline
 * (red box on the card) and via toast — never silent.
 */
public class SkyzCosmeticsScreen extends BaseUIModelScreen<FlowLayout> {

    /**
     * Class names tried in order when opening Essential's wardrobe.
     * Listed newest-first — Essential renames screens occasionally
     * across major versions. Modern (post-2024) is {@code Wardrobe}
     * (a Kotlin {@code object}, hence the singleton-INSTANCE handling
     * below); older versions used {@code WardrobeScreen} or
     * {@code CosmeticsScreen}.
     */
    private static final String[] ESSENTIAL_WARDROBE_CLASSES = {
            "gg.essential.gui.wardrobe.Wardrobe",
            "gg.essential.gui.wardrobe.WardrobeScreen",
            "gg.essential.gui.menu.WardrobeScreen",
            "gg.essential.gui.cosmetics.CosmeticsScreen",
    };

    /**
     * Attempt to open Essential's Wardrobe directly (skipping our card).
     * Returns {@code true} if a wardrobe screen was successfully shown.
     *
     * <p>Used by {@link SkyzTitleScreen}'s Cosmetics button so clicking
     * goes straight to Essential's UI when Essential is installed and
     * working. On any failure (Essential missing, reflection error,
     * incompatible version), returns {@code false} and the caller can
     * fall back to opening {@link SkyzCosmeticsScreen} as the install
     * prompt / error page.
     *
     * <p>Tries in order:
     * <ol>
     *   <li>For each candidate class, the {@code INSTANCE} field
     *       (Kotlin {@code object} singleton) — modern Essential
     *       exposes its wardrobe as a Kotlin object with an
     *       {@code open()} or {@code show()} method.</li>
     *   <li>The no-arg constructor — older Essential versions where
     *       the wardrobe was a regular {@code Screen} subclass.</li>
     *   <li>{@code EssentialAPI.getInstance().getGuiUtil().openWardrobe()}
     *       — the public API helper, which works across most versions
     *       but isn't always present.</li>
     * </ol>
     */
    public static boolean tryOpenEssentialWardrobe() {
        if (!FabricLoader.getInstance().isModLoaded("essential")) return false;

        // Path 1: Kotlin object singleton with open()/show() method.
        for (String className : ESSENTIAL_WARDROBE_CLASSES) {
            try {
                Class<?> cls = Class.forName(className);
                Object instance = null;
                try {
                    java.lang.reflect.Field f = cls.getField("INSTANCE");
                    instance = f.get(null);
                } catch (NoSuchFieldException ignored) {}
                if (instance != null) {
                    // Try common method names — Essential uses different ones
                    // across versions ("open" / "show" / "openWardrobe").
                    for (String method : new String[]{"open", "show", "openWardrobe", "display"}) {
                        try {
                            Method m = instance.getClass().getMethod(method);
                            m.invoke(instance);
                            return true;
                        } catch (NoSuchMethodException ignored) {}
                    }
                }
            } catch (ClassNotFoundException cnf) {
                // Try next.
            } catch (Throwable t) {
                SkyzClientMod.LOGGER.warn(
                        "[Skyz] Essential: singleton open of {} failed: {}",
                        className, t.getMessage());
            }
        }

        // Path 2: direct no-arg construction → setScreen.
        for (String className : ESSENTIAL_WARDROBE_CLASSES) {
            try {
                Class<?> cls = Class.forName(className);
                Constructor<?> ctor;
                try {
                    ctor = cls.getConstructor();
                } catch (NoSuchMethodException nsme) {
                    ctor = null;
                    for (Constructor<?> c : cls.getDeclaredConstructors()) {
                        if (c.getParameterCount() == 0) {
                            c.setAccessible(true);
                            ctor = c;
                            break;
                        }
                    }
                    if (ctor == null) continue;
                }
                Screen target = (Screen) ctor.newInstance();
                if (!openViaEssentialGuiUtil(target)) {
                    Minecraft.getInstance().setScreen(target);
                }
                return true;
            } catch (ClassNotFoundException cnf) {
                // Try next.
            } catch (Throwable t) {
                SkyzClientMod.LOGGER.warn(
                        "[Skyz] Essential: ctor open of {} failed: {}",
                        className, t.getMessage());
            }
        }

        // Path 3: EssentialAPI helper.
        try {
            Class<?> apiCls = Class.forName("gg.essential.api.EssentialAPI");
            Method getInstance = apiCls.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method getGuiUtil = api.getClass().getMethod("getGuiUtil");
            Object guiUtil = getGuiUtil.invoke(api);
            for (String method : new String[]{"openWardrobe", "openCosmetics", "openCosmeticStudio"}) {
                try {
                    Method m = guiUtil.getClass().getMethod(method);
                    m.invoke(guiUtil);
                    return true;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable t) {
            SkyzClientMod.LOGGER.warn("[Skyz] Essential: API openWardrobe failed: {}",
                    t.getMessage());
        }

        return false;
    }

    private final SkyzTitleScreen parent;
    private final boolean         hasEssential;
    private final String          essentialVersion;

    private String     lastError = "";
    private FlowLayout card;

    public SkyzCosmeticsScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "cosmetics"));
        this.parent = parent;
        this.hasEssential = FabricLoader.getInstance().isModLoaded("essential");
        this.essentialVersion = FabricLoader.getInstance()
                .getModContainer("essential")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    // ─── Build ───────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        wire(root, "btn-back", () -> Minecraft.getInstance().setScreen(parent), SkyzButtonRenderer.NAV_BACK);

        card = root.childById(FlowLayout.class, "content-card");
        if (card != null) {
            card.surface(SkyzSurface.CARD);
            rebuildCard();
        }
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] Cosmetics: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    private void rebuildCard() {
        if (card == null) return;
        card.clearChildren();

        // Heading.
        card.child(UIComponents.label(Component.literal("✨  COSMETICS"))
                .color(Color.ofArgb(0xFFEEF6FF)));
        FlowLayout divider = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x338CD2FF));
        divider.margins(Insets.of(2, 6, 0, 0));
        card.child(divider);

        if (hasEssential) {
            card.child(UIComponents.label(Component.literal(
                            "✓  Essential v" + essentialVersion + " detected"))
                    .color(Color.ofArgb(0xFF4CFA87))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));
            card.child(UIComponents.label(Component.literal(
                            "Cosmetics, capes, emotes and outfits via the Essential wardrobe."))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));

            FlowLayout gap = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(8));
            card.child(gap);

            ButtonComponent open = UIComponents.button(
                            Component.literal("✨  OPEN ESSENTIAL WARDROBE"),
                            b -> openEssentialWardrobe())
                    .renderer(SkyzButtonRenderer.DEFAULT);
            open.horizontalSizing(Sizing.fixed(240));
            open.verticalSizing(Sizing.fixed(22));
            card.child(open);

            if (!lastError.isEmpty()) {
                FlowLayout errBox = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
                errBox.padding(Insets.of(6, 6, 8, 8));
                errBox.gap(2);
                errBox.margins(Insets.top(8));
                errBox.surface((ctx, comp) -> {
                    int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                    SkyzRenderHelper.fillRoundedRect(ctx, x, y, w, h, 4, 0x33FF6644);
                    SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, 4, 0x66FF6644);
                });
                errBox.child(UIComponents.label(Component.literal("⚠ " + lastError))
                        .color(Color.ofArgb(0xFFFFAA88))
                        .horizontalTextAlignment(HorizontalAlignment.CENTER)
                        .horizontalSizing(Sizing.fill(100)));
                errBox.child(UIComponents.label(Component.literal(
                                "Try opening Essential from the Mods menu instead."))
                        .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                        .horizontalTextAlignment(HorizontalAlignment.CENTER)
                        .horizontalSizing(Sizing.fill(100)));
                card.child(errBox);
            }
        } else {
            card.child(UIComponents.label(Component.literal("Cosmetics requires the Essential mod"))
                    .color(Color.ofArgb(0xFFFFBB44))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));
            card.child(UIComponents.label(Component.literal(
                            "Essential adds cosmetics, capes, emotes, and a friend list."))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));

            FlowLayout gap = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(6));
            card.child(gap);

            card.child(UIComponents.label(Component.literal("Install from:"))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));
            card.child(UIComponents.label(Component.literal("essential.gg"))
                    .color(Color.ofArgb(0xFF4CFA87))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));

            FlowLayout gap2 = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(8));
            card.child(gap2);

            ButtonComponent copy = UIComponents.button(
                            Component.literal("📋  COPY essential.gg LINK"),
                            b -> {
                                Minecraft.getInstance().keyboardHandler.setClipboard("https://essential.gg");
                                if (parent != null) parent.toast("Copied essential.gg to clipboard");
                            })
                    .renderer(SkyzButtonRenderer.DEFAULT);
            copy.horizontalSizing(Sizing.fixed(220));
            copy.verticalSizing(Sizing.fixed(22));
            card.child(copy);
        }
    }

    // ─── Essential opener ────────────────────────────────────────────────
    /**
     * Open Essential's Wardrobe via reflection. Same defensive pattern as
     * {@link SkyzFriendsScreen#openEssentialSocial()}:
     * <ol>
     *   <li>Iterate the candidate class names.</li>
     *   <li>Use {@code getConstructor()} (no-arg) — never
     *       {@code getDeclaredConstructors()[0]} since that picks
     *       Kotlin synthetic constructors that need args.</li>
     *   <li>Fall back to {@code EssentialAPI.getGuiUtil().openWardrobe()}
     *       if direct instantiation fails on every candidate.</li>
     * </ol>
     */
    /**
     * Instance entry point — used by the OPEN ESSENTIAL WARDROBE button on
     * this screen's card. Delegates to the static helper and surfaces any
     * failure inline (red error box on the card) so the user sees what
     * went wrong.
     */
    private void openEssentialWardrobe() {
        if (!hasEssential) return;
        if (tryOpenEssentialWardrobe()) {
            lastError = "";
            return;
        }
        lastError = "Couldn't open Essential wardrobe (v" + essentialVersion
                + "). No compatible wardrobe class / API method found — see logs.";
        if (parent != null) parent.toast(lastError);
        rebuildCard();
    }

    /** Same helper as {@link SkyzFriendsScreen} — Essential's GuiUtil openScreen. */
    private static boolean openViaEssentialGuiUtil(Screen screen) {
        try {
            Class<?> apiCls = Class.forName("gg.essential.api.EssentialAPI");
            Method getInstance = apiCls.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method getGuiUtil = api.getClass().getMethod("getGuiUtil");
            Object guiUtil = getGuiUtil.invoke(api);
            try {
                Method openScreen = guiUtil.getClass().getMethod("openScreen", Class.class);
                openScreen.invoke(guiUtil, screen.getClass());
                return true;
            } catch (NoSuchMethodException ignored) {}
            try {
                Method openScreen = guiUtil.getClass().getMethod("openScreen", Screen.class);
                openScreen.invoke(guiUtil, screen);
                return true;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return false;
    }

    // ─── Lifecycle / render ──────────────────────────────────────────────
    @Override public boolean shouldPause() { return false; }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // No-op — render() handles bg.
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
        super.render(ctx, mouseX, mouseY, delta);
        if (parent != null) parent.toast.render(ctx, width, delta);
    }
}
