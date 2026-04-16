package net.skyz.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.skyz.client.util.*;

/**
 * Friends/Social screen.
 * If Essential mod is loaded, opens Essential's social UI via reflection.
 * Otherwise shows a friendly install prompt.
 */
public class SkyzFriendsScreen extends Screen {

    private final SkyzTitleScreen parent;
    private final boolean hasEssential;
    private Screen essentialScreen = null;
    private String statusMsg = "";

    public SkyzFriendsScreen(SkyzTitleScreen parent) {
        super(Text.literal("Friends"));
        this.parent = parent;
        this.hasEssential = FabricLoader.getInstance().isModLoaded("essential");
    }

    @Override
    protected void init() {
        addDrawableChild(SkyzButton.of(width - 82, 7, 74, 18, "\u2190 Back",
                () -> client.setScreen(parent)));

        if (hasEssential) {
            tryOpenEssentialSocial();
        }
    }

    private void tryOpenEssentialSocial() {
        try {
            // Try Essential's FriendsScreen via reflection
            Class<?> cls = Class.forName("gg.essential.gui.friends.FriendsScreen");
            essentialScreen = (Screen) cls.getDeclaredConstructors()[0].newInstance();
            client.setScreen(essentialScreen);
        } catch (Exception e1) {
            try {
                // Alternative: SocialMenuScreen
                Class<?> cls = Class.forName("gg.essential.gui.menu.SocialMenuScreen");
                essentialScreen = (Screen) cls.getDeclaredConstructors()[0].newInstance();
                client.setScreen(essentialScreen);
            } catch (Exception e2) {
                statusMsg = "Essential loaded but social screen not found (v" +
                        FabricLoader.getInstance().getModContainer("essential")
                                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                                .orElse("?") + ")";
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Background
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, width, height/2, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height/2, width, height/2, SkyzTheme.BG2, SkyzTheme.BG3);
        ctx.fill(0, 0, width, 28, 0x88000000);
        ctx.fill(0, 27, width, 28, 0x558CD2FF);
        ctx.drawCenteredTextWithShadow(textRenderer, "\uD83D\uDC65  Friends", width/2, 10, SkyzColors.TEXT_PRIMARY);

        int cy = height/2 - 40;

        if (!hasEssential) {
            // No Essential installed
            ctx.fill(width/2-120, cy-8, width/2+120, cy+90, 0x88000000);
            ctx.fill(width/2-120, cy-8, width/2+120, cy-7, 0x558CD2FF);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "\uD83D\uDC65 Friends requires Essential mod", width/2, cy+4, 0xFFFFBB44);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Essential adds friends, cosmetics and servers.", width/2, cy+20, SkyzColors.TEXT_MUTED);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Install it from essential.gg or CurseForge.", width/2, cy+34, SkyzColors.TEXT_MUTED);
            // Install button drawn by addDrawableChild below - done in init() but we add it here conditionally
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "essential.gg", width/2, cy+60, 0xFF4CFA87);
        } else if (!statusMsg.isEmpty()) {
            // Essential loaded but couldn't open UI
            ctx.drawCenteredTextWithShadow(textRenderer, "\u26A0 " + statusMsg,
                    width/2, cy + 10, 0xFFFFBB44);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Try opening Essential directly from your mods.", width/2, cy+26, SkyzColors.TEXT_MUTED);
        }

        super.render(ctx, mouseX, mouseY, delta);
        parent.toast.render(ctx, width, delta);
    }

    @Override public boolean charTyped(CharInput input) { return false; }
}
