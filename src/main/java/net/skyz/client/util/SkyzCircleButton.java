package net.skyz.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Circular icon button — Feather-Client style. Used for back arrows,
 * media toggles and any small icon-only action button.
 */
public class SkyzCircleButton extends AbstractWidget {

    public enum Style { NORMAL, ACCENT, DANGER }

    private final Runnable action;
    private final String   icon;
    private final Style    style;
    private float          hoverAnim = 0f;

    public SkyzCircleButton(int cx, int cy, int radius, String icon,
                            Runnable action, Style style) {
        super(cx - radius, cy - radius, radius * 2, radius * 2, Component.literal(icon));
        this.action = action;
        this.icon   = icon;
        this.style  = style;
    }

    public static SkyzCircleButton of(int cx, int cy, int radius, String icon, Runnable action) {
        return new SkyzCircleButton(cx, cy, radius, icon, action, Style.NORMAL);
    }

    public static SkyzCircleButton accent(int cx, int cy, int radius, String icon, Runnable action) {
        return new SkyzCircleButton(cx, cy, radius, icon, action, Style.ACCENT);
    }

    public static SkyzCircleButton danger(int cx, int cy, int radius, String icon, Runnable action) {
        return new SkyzCircleButton(cx, cy, radius, icon, action, Style.DANGER);
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        action.run();
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        // Restrict hit-area to the circle, not the bounding box
        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2;
        int r  = getWidth() / 2;
        double dx = mx - cx, dy = my - cy;
        return active && visible && (dx * dx + dy * dy) <= (double) r * r;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        hoverAnim = isHovered()
                ? Math.min(1f, hoverAnim + delta * 0.20f)
                : Math.max(0f, hoverAnim - delta * 0.20f);

        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2;
        int r  = getWidth() / 2;

        int idleFill, hoverFill, idleBorder, hoverBorder, idleText, hoverText;
        switch (style) {
            case ACCENT -> {
                idleFill   = 0xCC1A4A8C;
                hoverFill  = 0xEE2A6EC8;
                idleBorder = (SkyzTheme.ACCENT2 & 0x00FFFFFF) | 0x88000000;
                hoverBorder= (SkyzTheme.ACCENT1 & 0x00FFFFFF) | 0xFF000000;
                idleText   = 0xFFE0F4FF;
                hoverText  = 0xFFFFFFFF;
            }
            case DANGER -> {
                idleFill   = 0xCC2A0A14;
                hoverFill  = 0xEE5A1428;
                idleBorder = SkyzColors.DANGER_BORDER;
                hoverBorder= 0xCCFF7878;
                idleText   = SkyzColors.DANGER_TEXT;
                hoverText  = 0xFFFFC8C8;
            }
            default -> {
                idleFill   = 0xCC0A1F40;
                hoverFill  = 0xDD143A6E;
                idleBorder = SkyzTheme.BTN_BORDER;
                hoverBorder= (SkyzTheme.ACCENT2 & 0x00FFFFFF) | 0xCC000000;
                idleText   = SkyzColors.TEXT_MUTED;
                hoverText  = 0xFFFFFFFF;
            }
        }

        // Glow halo on hover
        if (hoverAnim > 0.05f) {
            int glowAlpha = (int) (0x33 * hoverAnim);
            int glowCol   = (style == Style.DANGER ? 0xFF6464 : (SkyzTheme.ACCENT1 & 0x00FFFFFF));
            SkyzRenderHelper.fillCircle(ctx, cx, cy, r + 2, (glowAlpha << 24) | glowCol);
        }

        int fill   = SkyzColors.lerp(idleFill,   hoverFill,   hoverAnim);
        int border = SkyzColors.lerp(idleBorder, hoverBorder, hoverAnim);

        SkyzRenderHelper.fillGlassCircle(ctx, cx, cy, r, fill, border);

        // Icon — vertically centered. Many emoji glyphs render off-baseline
        // so use 1-px upward nudge for visual centering.
        int textCol = SkyzColors.lerp(idleText, hoverText, hoverAnim);
        ctx.centeredText(
                Minecraft.getInstance().font,
                icon, cx, cy - 4, textCol);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
