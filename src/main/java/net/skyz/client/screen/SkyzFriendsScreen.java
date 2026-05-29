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
 * Skyz Friends screen — owo-lib edition (Phase 2c port).
 *
 * <h3>Why the original errored even when Essential was installed</h3>
 *
 * The previous version did:
 * <pre>{@code
 * Class<?> cls = Class.forName("gg.essential.gui.friends.FriendsScreen");
 * essentialScreen = (Screen) cls.getDeclaredConstructors()[0].newInstance();
 * }</pre>
 * {@code getDeclaredConstructors()[0]} picks an arbitrary constructor. On
 * Essential's Kotlin-generated screens that's typically a synthetic
 * Kotlin-DSL constructor with non-no-arg parameters, so {@code newInstance()}
 * throws {@code IllegalArgumentException: argument type mismatch} and the
 * code lands in the "social screen not found" error path.
 *
 * <p>Fix: explicitly request {@code getConstructor()} (the no-arg one)
 * for each candidate class name we know about across Essential versions,
 * and additionally fall back to Essential's
 * {@code EssentialAPI.getInstance().getGuiUtil().openScreen(...)} helper
 * when direct instantiation works but Essential expects to be told via
 * its own GUI util.
 *
 * <h3>Screen behaviour</h3>
 *
 * Unlike the original, we don't auto-open Essential during init. We show
 * a Skyz-styled card with the current state (installed + version, or
 * not-installed CTA) and an explicit OPEN button. This avoids surprising
 * the user with a screen swap they didn't ask for, and lets us surface
 * any reflection error inline instead of silently falling through.
 */
public class SkyzFriendsScreen extends BaseUIModelScreen<FlowLayout> {

    /**
     * Class names we'll try in order when opening Essential's social
     * screen. Listed newest-first because Essential renames its screens
     * occasionally — the modern (post-2024) home is {@code SocialMenu},
     * legacy versions used {@code FriendsScreen} or {@code SocialMenuScreen}.
     */
    private static final String[] ESSENTIAL_SCREEN_CLASSES = {
            "gg.essential.gui.friends.SocialMenu",
            "gg.essential.gui.friends.FriendsScreen",
            "gg.essential.gui.menu.SocialMenuScreen",
    };

    private final SkyzTitleScreen parent;
    private final boolean         hasEssential;
    private final String          essentialVersion;

    /** Last error message from openEssentialSocial(), shown in the card. */
    private String lastError = "";

    private FlowLayout card;

    public SkyzFriendsScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "friends"));
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
            SkyzClientMod.LOGGER.warn("[Skyz] Friends: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    private void rebuildCard() {
        if (card == null) return;
        card.clearChildren();

        // Heading.
        LabelComponent heading = UIComponents.label(Component.literal("👥  FRIENDS"))
                .color(Color.ofArgb(0xFFEEF6FF));
        card.child(heading);

        FlowLayout divider = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x338CD2FF));
        divider.margins(Insets.of(2, 6, 0, 0));
        card.child(divider);

        if (hasEssential) {
            // Status line.
            card.child(UIComponents.label(Component.literal("✓  Essential v" + essentialVersion + " detected"))
                    .color(Color.ofArgb(0xFF4CFA87))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));
            card.child(UIComponents.label(Component.literal("MouseButtonEvent below to open the friends list, party invites, and chat."))
                    .color(Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));

            FlowLayout gap = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(8));
            card.child(gap);

            // Primary action.
            ButtonComponent open = UIComponents
                    .button(Component.literal("👥  OPEN ESSENTIAL SOCIAL"),
                            b -> openEssentialSocial())
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
            // Not-installed call to action.
            card.child(UIComponents.label(Component.literal("Friends requires the Essential mod"))
                    .color(Color.ofArgb(0xFFFFBB44))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .horizontalSizing(Sizing.fill(100)));
            card.child(UIComponents.label(Component.literal("Essential adds friends, parties, cosmetics, and servers."))
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

            ButtonComponent copy = UIComponents
                    .button(Component.literal("📋  COPY essential.gg LINK"),
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
     * Static counterpart of {@link #openEssentialSocial()} — used by the
     * title screen's Friends button so clicking goes straight to Essential's
     * social menu, skipping the intermediate Skyz card entirely. Returns
     * {@code true} if a social screen was actually shown; the caller
     * (e.g. {@link SkyzTitleScreen}) can fall back to opening
     * {@link SkyzFriendsScreen} as the install-prompt / error page on
     * {@code false}.
     *
     * <p>Mirrors the structure of
     * {@link SkyzCosmeticsScreen#tryOpenEssentialWardrobe()} — three
     * paths tried in order:
     * <ol>
     *   <li><b>Kotlin singleton</b> — {@code SocialMenu.INSTANCE.open()} /
     *       {@code .show()}. Modern Essential exposes its social UI as a
     *       Kotlin {@code object} with a no-arg open method.</li>
     *   <li><b>No-arg constructor</b> — older Essential versions where
     *       the social menu was a regular {@code Screen} subclass.</li>
     *   <li><b>EssentialAPI helper</b> —
     *       {@code EssentialAPI.getInstance().getGuiUtil().openSocialMenu()}.</li>
     * </ol>
     */
    public static boolean tryOpenEssentialSocial() {
        if (!FabricLoader.getInstance().isModLoaded("essential")) return false;

        // Path 1: Kotlin object singleton.
        for (String className : ESSENTIAL_SCREEN_CLASSES) {
            try {
                Class<?> cls = Class.forName(className);
                Object instance;
                try {
                    instance = cls.getField("INSTANCE").get(null);
                } catch (NoSuchFieldException nsfe) {
                    continue;
                }
                if (instance != null) {
                    for (String method : new String[]{"open", "show", "openSocial", "display"}) {
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

        // Path 2: no-arg constructor → setScreen.
        for (String className : ESSENTIAL_SCREEN_CLASSES) {
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
            for (String method : new String[]{"openSocialMenu", "openSocial", "openFriends"}) {
                try {
                    Method m = guiUtil.getClass().getMethod(method);
                    m.invoke(guiUtil);
                    return true;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable t) {
            SkyzClientMod.LOGGER.warn("[Skyz] Essential: API openSocialMenu failed: {}",
                    t.getMessage());
        }

        return false;
    }

    /**
     * Instance-side opener used by the OPEN ESSENTIAL SOCIAL button on this
     * screen's card. Delegates to the static helper and surfaces failures
     * inline (red call-out box on the card) so the user sees what went wrong.
     */
    private void openEssentialSocial() {
        if (!hasEssential) return;
        if (tryOpenEssentialSocial()) {
            lastError = "";
            return;
        }
        lastError = "Couldn't open Essential social (v" + essentialVersion
                + "). No compatible social class / API method found — see logs.";
        if (parent != null) parent.toast(lastError);
        rebuildCard();
    }

    /**
     * Hand a Screen to Essential's own {@code GuiUtil.openScreen}. Returns
     * true if the helper exists and the call went through. Essential
     * occasionally needs this entry point for proper Elementa lifecycle.
     */
    private static boolean openViaEssentialGuiUtil(Screen screen) {
        try {
            Class<?> apiCls = Class.forName("gg.essential.api.EssentialAPI");
            Method getInstance = apiCls.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method getGuiUtil = api.getClass().getMethod("getGuiUtil");
            Object guiUtil = getGuiUtil.invoke(api);
            // openScreen(Class<? extends Screen>) — most Essential versions.
            try {
                Method openScreen = guiUtil.getClass().getMethod("openScreen", Class.class);
                openScreen.invoke(guiUtil, screen.getClass());
                return true;
            } catch (NoSuchMethodException ignored) {}
            // openScreen(Screen) — newer signature.
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
        // No-op — render() handles the background.
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
