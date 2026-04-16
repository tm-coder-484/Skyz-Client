package net.skyz.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

/**
 * Styled button matching the "mc-btn" design.
 *
 * In MC 1.21.11, PressableWidget.renderWidget() is final.
 * We must override drawIcon() for custom visuals, and onPress(AbstractInput).
 */
public class SkyzButton extends PressableWidget {

    public enum Style { NORMAL, DANGER }

    private final Runnable action;
    private final Style style;
    private float hoverAnim = 0f;

    public SkyzButton(int x, int y, int w, int h, String label, Runnable action, Style style) {
        super(x, y, w, h, Text.literal(label));
        this.action = action;
        this.style  = style;
    }

    public static SkyzButton of(int x, int y, int w, int h, String label, Runnable action) {
        return new SkyzButton(x, y, w, h, label, action, Style.NORMAL);
    }

    public static SkyzButton danger(int x, int y, int w, int h, String label, Runnable action) {
        return new SkyzButton(x, y, w, h, label, action, Style.DANGER);
    }

    // Called by PressableWidget when clicked/space/enter pressed
    @Override
    public void onPress(AbstractInput input) {
        action.run();
    }

    // drawIcon is called inside PressableWidget.renderWidget (which is final).
    // We draw our entire custom appearance here.
    @Override
    protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hoverAnim = isHovered()
                ? Math.min(1f, hoverAnim + delta * 0.15f)
                : Math.max(0f, hoverAnim - delta * 0.15f);

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // Background
        int bgIdle  = (style == Style.DANGER) ? 0x33280820 : SkyzColors.BTN_BG;
        int bgHover = (style == Style.DANGER) ? SkyzColors.DANGER_HOVER : SkyzColors.BTN_HOVER;
        int bg      = SkyzColors.lerp(bgIdle, bgHover, hoverAnim);
        SkyzRenderHelper.fillRect(ctx, x, y, w, h, bg);

        // Border
        int bIdle  = (style == Style.DANGER) ? SkyzColors.DANGER_BORDER : SkyzColors.BTN_BORDER;
        int bHover = (style == Style.DANGER) ? 0x73FF7878 : 0x99A0E0FF;
        int border = SkyzColors.lerp(bIdle, bHover, hoverAnim);
        SkyzRenderHelper.fillRect(ctx, x,       y,       w, 1, border);
        SkyzRenderHelper.fillRect(ctx, x,       y+h-1,   w, 1, border);
        SkyzRenderHelper.fillRect(ctx, x,       y,       1, h, border);
        SkyzRenderHelper.fillRect(ctx, x+w-1,   y,       1, h, border);

        // Top shimmer
        SkyzRenderHelper.fillRect(ctx, x+1, y+1, w-2, 1, 0x1AFFFFFF);

        // Hover sweep
        if (hoverAnim > 0) {
            int sw = (int)((w-2) * hoverAnim);
            SkyzRenderHelper.fillRect(ctx, x+1, y+1, sw, h-2,
                    SkyzColors.withAlpha(0xFFFFFFFF, (int)(18 * hoverAnim)));
        }

        // Label
        int tIdle  = (style == Style.DANGER) ? SkyzColors.DANGER_TEXT : SkyzColors.TEXT_PRIMARY;
        int tCol   = SkyzColors.lerp(tIdle, SkyzColors.TEXT_WHITE, hoverAnim);
        ctx.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                getMessage(), x + w/2, y + (h-8)/2, tCol);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
