package net.skyz.client.util;

import net.minecraft.client.gui.DrawContext;

/**
 * Low-level rendering helpers for all Skyz screens.
 *
 * 1.21.11 notes:
 *  - ctx.fill(x1, y1, x2, y2, color) \u2014 still works (x2/y2 are exclusive)
 *    We use x,y,w,h internally and convert.
 *  - ctx.fillGradient(startX, startY, endX, endY, colorStart, colorEnd)
 *    Still works, vertical gradient only.
 *  - No Matrix calls here \u2014 those are in screens directly.
 */
public final class SkyzRenderHelper {

    private SkyzRenderHelper() {}

    // \u2500\u2500 Solid rectangles \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /** Fill a rectangle given x, y, width, height. */
    public static void fillRect(DrawContext ctx, int x, int y, int w, int h, int colour) {
        ctx.fill(x, y, x + w, y + h, colour);
    }

    // \u2500\u2500 Gradient fills \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /** Vertical gradient (top colour -> bottom colour). */
    public static void fillGradientV(DrawContext ctx, int x, int y, int w, int h,
                                     int colourTop, int colourBottom) {
        ctx.fillGradient(x, y, x + w, y + h, colourTop, colourBottom);
    }

    /**
     * Approximate horizontal gradient using 16 thin vertical slices.
     * (MC's fillGradient is always vertical; we fake horizontal this way.)
     */
    public static void fillGradientH(DrawContext ctx, int x, int y, int w, int h,
                                     int colourLeft, int colourRight) {
        int slices = 16;
        for (int i = 0; i < slices; i++) {
            int sx  = x + (w * i / slices);
            int ex  = x + (w * (i + 1) / slices);
            float t = (float) i / slices;
            int col = SkyzColors.lerp(colourLeft, colourRight, t);
            ctx.fill(sx, y, ex, y + h, col);
        }
    }

    // \u2500\u2500 Bordered panels \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /** Filled rectangle with a 1-pixel border. */
    public static void fillPanel(DrawContext ctx, int x, int y, int w, int h,
                                 int fill, int border) {
        fillRect(ctx, x,         y,         w, 1, border);  // top
        fillRect(ctx, x,         y + h - 1, w, 1, border);  // bottom
        fillRect(ctx, x,         y,         1, h, border);  // left
        fillRect(ctx, x + w - 1, y,         1, h, border);  // right
        fillRect(ctx, x + 1, y + 1, w - 2, h - 2, fill);   // inner
    }

    /** Card-style panel with a subtle top shimmer. */
    public static void fillCard(DrawContext ctx, int x, int y, int w, int h) {
        fillPanel(ctx, x, y, w, h, SkyzColors.CARD_BG, SkyzColors.CARD_BORDER);
        fillRect(ctx, x + 1, y + 1, w - 2, 1, 0x1AFFFFFF);
    }

    // \u2500\u2500 Dividers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /** Horizontal divider that fades at both ends. */
    public static void drawDivider(DrawContext ctx, int x, int y, int w) {
        int third = w / 3;
        fillGradientH(ctx, x,             y, third,       1, 0x00AADDFF, 0x33AADDFF);
        fillRect(ctx,      x + third,     y, w - third*2, 1, 0x33AADDFF);
        fillGradientH(ctx, x + w - third, y, third,       1, 0x33AADDFF, 0x00AADDFF);
    }
}
