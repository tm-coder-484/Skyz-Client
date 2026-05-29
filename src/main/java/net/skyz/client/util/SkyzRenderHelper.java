package net.skyz.client.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/**
 * Low-level rendering helpers for all Skyz screens.
 * Provides rounded panels, circles and glass effects matching skyz-client.html.
 */
public final class SkyzRenderHelper {

    private SkyzRenderHelper() {}

    // ── Solid rectangles ─────────────────────────────────────────────────

    public static void fillRect(GuiGraphics ctx, int x, int y, int w, int h, int colour) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + h, colour);
    }

    // ── Gradient fills ───────────────────────────────────────────────────

    public static void fillGradientV(GuiGraphics ctx, int x, int y, int w, int h,
                                     int colourTop, int colourBottom) {
        if (w <= 0 || h <= 0) return;
        ctx.fillGradient(x, y, x + w, y + h, colourTop, colourBottom);
    }

    public static void fillGradientH(GuiGraphics ctx, int x, int y, int w, int h,
                                     int colourLeft, int colourRight) {
        if (w <= 0 || h <= 0) return;
        int slices = 16;
        for (int i = 0; i < slices; i++) {
            int sx  = x + (w * i / slices);
            int ex  = x + (w * (i + 1) / slices);
            float t = (float) i / slices;
            int col = SkyzColors.lerp(colourLeft, colourRight, t);
            ctx.fill(sx, y, ex, y + h, col);
        }
    }

    // ── Bordered panels (now subtly rounded for the glass look) ──────────

    /**
     * Soft-rounded panel — automatically picks a radius based on the size.
     * Tiny widgets get radius 2, mid-size get 4, large get 6. Keeps the
     * Feather glass aesthetic without per-call-site changes.
     */
    public static void fillPanel(GuiGraphics ctx, int x, int y, int w, int h,
                                 int fill, int border) {
        if (w <= 0 || h <= 0) return;
        int small = Math.min(w, h);
        int r = small < 10 ? Math.max(1, small / 4)
              : small < 22 ? 3
              : small < 60 ? 5
              :              7;
        fillRoundedRect(ctx, x, y, w, h, r, fill);
        // 1-px top sheen — only on panels tall enough for it not to look weird
        if (h > 6) fillRect(ctx, x + r, y + 1, w - 2*r, 1, 0x14FFFFFF);
        drawRoundedBorder(ctx, x, y, w, h, r, border);
    }

    public static void fillCard(GuiGraphics ctx, int x, int y, int w, int h) {
        fillGlassPanel(ctx, x, y, w, h, 7, SkyzColors.CARD_BG, SkyzColors.CARD_BORDER);
    }

    // ── Dividers ─────────────────────────────────────────────────────────

    public static void drawDivider(GuiGraphics ctx, int x, int y, int w) {
        int third = w / 3;
        fillGradientH(ctx, x,             y, third,       1, 0x00AADDFF, 0x33AADDFF);
        fillRect(ctx,      x + third,     y, w - third*2, 1, 0x33AADDFF);
        fillGradientH(ctx, x + w - third, y, third,       1, 0x33AADDFF, 0x00AADDFF);
    }

    // ── Rounded rectangles ───────────────────────────────────────────────

    /**
     * Filled rounded rectangle. Corners approximated with horizontal scanline arcs.
     */
    public static void fillRoundedRect(GuiGraphics ctx, int x, int y, int w, int h,
                                       int radius, int colour) {
        if (w <= 0 || h <= 0) return;
        if (radius <= 0) { fillRect(ctx, x, y, w, h, colour); return; }
        int r = Math.min(radius, Math.min(w, h) / 2);

        // Center cross
        fillRect(ctx, x + r, y,         w - 2*r, h,       colour);
        fillRect(ctx, x,     y + r,     w,       h - 2*r, colour);

        // Four corners as quarter-disc scanlines
        for (int dy = 0; dy < r; dy++) {
            double t = dy + 0.5;
            int dx = (int) Math.round(Math.sqrt((double) r*r - t*t));
            if (dx <= 0) continue;
            int yTop = y + r - 1 - dy;
            int yBot = y + h - r + dy;
            fillRect(ctx, x + r - dx,  yTop, dx, 1, colour);
            fillRect(ctx, x + w - r,   yTop, dx, 1, colour);
            fillRect(ctx, x + r - dx,  yBot, dx, 1, colour);
            fillRect(ctx, x + w - r,   yBot, dx, 1, colour);
        }
    }

    /**
     * Filled rounded rectangle with a vertical gradient. Each scan-line is
     * filled with the gradient colour interpolated from {@code colourTop}
     * (row 0) to {@code colourBottom} (row h-1). The horizontal extent of
     * each row is masked to the rounded shape, so the gradient hugs the
     * curved corners cleanly — no rectangular highlight artefacts the way
     * a separate {@code fillGradientV} sheen on top of a rounded body
     * produces.
     */
    public static void fillRoundedRectGradient(GuiGraphics ctx, int x, int y, int w, int h,
                                               int radius, int colourTop, int colourBottom) {
        if (w <= 0 || h <= 0) return;
        if (radius <= 0) { fillGradientV(ctx, x, y, w, h, colourTop, colourBottom); return; }
        int r = Math.min(radius, Math.min(w, h) / 2);

        for (int row = 0; row < h; row++) {
            int xOff;
            if (row < r) {
                // Top corner row.
                double dy = r - row - 0.5;
                int dx = (int) Math.round(Math.sqrt(Math.max(0, (double) r*r - dy*dy)));
                xOff = r - dx;
            } else if (row >= h - r) {
                // Bottom corner row.
                double dy = row - (h - r) + 0.5;
                int dx = (int) Math.round(Math.sqrt(Math.max(0, (double) r*r - dy*dy)));
                xOff = r - dx;
            } else {
                xOff = 0;
            }
            xOff = Math.max(0, xOff);

            float t = (h <= 1) ? 0f : (float) row / (float) (h - 1);
            int colour = SkyzColors.lerp(colourTop, colourBottom, t);

            int rowStart = x + xOff;
            int rowEnd   = x + w - xOff;
            if (rowEnd > rowStart) {
                ctx.fill(rowStart, y + row, rowEnd, y + row + 1, colour);
            }
        }
    }

    /**
     * 1-px rounded outline. Uses Bresenham-like sampling for the corner curve.
     */
    public static void drawRoundedBorder(GuiGraphics ctx, int x, int y, int w, int h,
                                         int radius, int colour) {
        if (w <= 0 || h <= 0) return;
        if (radius <= 0) {
            fillRect(ctx, x, y, w, 1, colour);
            fillRect(ctx, x, y + h - 1, w, 1, colour);
            fillRect(ctx, x, y, 1, h, colour);
            fillRect(ctx, x + w - 1, y, 1, h, colour);
            return;
        }
        int r = Math.min(radius, Math.min(w, h) / 2);

        // Straight edges
        fillRect(ctx, x + r,     y,         w - 2*r, 1, colour);
        fillRect(ctx, x + r,     y + h - 1, w - 2*r, 1, colour);
        fillRect(ctx, x,         y + r,     1, h - 2*r, colour);
        fillRect(ctx, x + w - 1, y + r,     1, h - 2*r, colour);

        // Corners — outline only
        for (int dy = 0; dy < r; dy++) {
            double t  = dy + 0.5;
            int dxOut = (int) Math.round(Math.sqrt((double) r*r - t*t));
            if (dxOut <= 0) continue;
            int yTop = y + r - 1 - dy;
            int yBot = y + h - r + dy;
            // outer perimeter pixel of each corner
            fillRect(ctx, x + r - dxOut,         yTop, 1, 1, colour);
            fillRect(ctx, x + w - r + dxOut - 1, yTop, 1, 1, colour);
            fillRect(ctx, x + r - dxOut,         yBot, 1, 1, colour);
            fillRect(ctx, x + w - r + dxOut - 1, yBot, 1, 1, colour);
        }
    }

    /**
     * Rounded panel: filled body + 1-px accent border + subtle inner top shimmer
     * (matches the .G / .card design from the HTML).
     */
    public static void fillRoundedPanel(GuiGraphics ctx, int x, int y, int w, int h,
                                        int radius, int fill, int border) {
        fillRoundedRect(ctx, x, y, w, h, radius, fill);
        drawRoundedBorder(ctx, x, y, w, h, radius, border);
        int r = Math.min(radius, Math.min(w, h) / 2);
        // Top shimmer (1-px highlight, inset by the corner radius so it stays inside the curve)
        fillRect(ctx, x + r, y + 1, w - 2*r, 1, 0x14FFFFFF);
    }

    /**
     * Glassy rounded panel — background dim + vertical highlight gradient + accent border + shimmer.
     * Approximates the CSS backdrop-filter:blur + linear glass overlay used in the HTML.
     */
    public static void fillGlassPanel(GuiGraphics ctx, int x, int y, int w, int h,
                                      int radius, int baseFill, int border) {
        // Body fill (semi-opaque, like the panel-bg variable)
        fillRoundedRect(ctx, x, y, w, h, radius, baseFill);
        // Subtle diagonal sheen — vertical light->transparent gradient over the top half
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (h > r * 2 + 2) {
            int sheenH = Math.max(6, h / 3);
            fillGradientV(ctx, x + r, y + 1, w - 2*r, sheenH, 0x18FFFFFF, 0x00FFFFFF);
        }
        // 1-px top shimmer line
        fillRect(ctx, x + r, y + 1, w - 2*r, 1, 0x22FFFFFF);
        // Border
        drawRoundedBorder(ctx, x, y, w, h, radius, border);
    }

    // ── Circles ──────────────────────────────────────────────────────────

    public static void fillCircle(GuiGraphics ctx, int cx, int cy, int radius, int colour) {
        if (radius <= 0) return;
        int r = radius;
        for (int dy = -r; dy < r; dy++) {
            double t = dy + 0.5;
            int dx = (int) Math.round(Math.sqrt((double) r*r - t*t));
            if (dx <= 0) continue;
            fillRect(ctx, cx - dx, cy + dy, dx * 2, 1, colour);
        }
    }

    public static void drawCircleOutline(GuiGraphics ctx, int cx, int cy, int radius, int colour) {
        if (radius <= 0) return;
        int r = radius;
        int prevDx = -1;
        for (int dy = -r; dy < r; dy++) {
            double t = dy + 0.5;
            int dx = (int) Math.round(Math.sqrt((double) r*r - t*t));
            if (dx <= 0) continue;
            // Single perimeter pixel each side; if jump > 1, fill the gap to keep the outline contiguous
            if (prevDx >= 0 && Math.abs(dx - prevDx) > 1) {
                int lo = Math.min(dx, prevDx);
                int hi = Math.max(dx, prevDx);
                for (int gx = lo; gx < hi; gx++) {
                    fillRect(ctx, cx - gx,     cy + dy, 1, 1, colour);
                    fillRect(ctx, cx + gx - 1, cy + dy, 1, 1, colour);
                }
            }
            fillRect(ctx, cx - dx,     cy + dy, 1, 1, colour);
            fillRect(ctx, cx + dx - 1, cy + dy, 1, 1, colour);
            prevDx = dx;
        }
    }

    /**
     * Circular glass button body — base disc + radial-style highlight + accent ring.
     */
    public static void fillGlassCircle(GuiGraphics ctx, int cx, int cy, int radius,
                                       int baseFill, int border) {
        fillCircle(ctx, cx, cy, radius, baseFill);
        // Top sheen — smaller offset disc with white alpha
        if (radius >= 5) {
            int r2 = Math.max(2, radius - 3);
            for (int dy = -r2; dy < 0; dy++) {
                double t = dy + 0.5;
                int dx = (int) Math.round(Math.sqrt((double) r2*r2 - t*t));
                if (dx <= 0) continue;
                int alpha = 0x18 - (-dy) * 0x02;
                if (alpha <= 0) continue;
                fillRect(ctx, cx - dx, cy + dy - radius/3, dx * 2, 1,
                        (alpha << 24) | 0xFFFFFF);
            }
        }
        // Accent ring (outline)
        drawCircleOutline(ctx, cx, cy, radius, border);
    }

    // ── Letter-spaced text ───────────────────────────────────────────────

    /** Width of {@code text} when rendered with {@code spacing} extra px between glyphs. */
    public static int textWidthSpaced(net.minecraft.client.gui.Font tr,
                                      String text, int spacing) {
        if (text == null || text.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            total += tr.width(c);
            if (i < text.length() - 1) total += spacing;
        }
        return total;
    }

    /** Draws each character of {@code text} separately with {@code spacing} extra px between them. */
    public static void drawTextSpaced(net.minecraft.client.gui.GuiGraphics ctx,
                                      net.minecraft.client.gui.Font tr,
                                      String text, int x, int y, int spacing,
                                      int colour, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            ctx.drawString(tr, c, cursor, y, colour, shadow);
            cursor += tr.width(c) + spacing;
        }
    }

    /** Centered letter-spaced text. */
    public static void drawTextSpacedCentered(net.minecraft.client.gui.GuiGraphics ctx,
                                              net.minecraft.client.gui.Font tr,
                                              String text, int cx, int y, int spacing,
                                              int colour, boolean shadow) {
        int w = textWidthSpaced(tr, text, spacing);
        drawTextSpaced(ctx, tr, text, cx - w / 2, y, spacing, colour, shadow);
    }

    // ── Soft shadow / glow halo around a panel ───────────────────────────

    /**
     * Cheap drop-shadow approximation: 3 receding rectangles below the element.
     * Use for cards/buttons that need to "pop" off the background.
     */
    public static void drawSoftShadow(GuiGraphics ctx, int x, int y, int w, int h, int radius) {
        int r = Math.max(0, radius);
        fillRoundedRect(ctx, x - 1, y + 2, w + 2, h, r, 0x33000000);
        fillRoundedRect(ctx, x - 2, y + 4, w + 4, h, r, 0x22000000);
        fillRoundedRect(ctx, x - 3, y + 6, w + 6, h, r, 0x14000000);
    }

    // ── HUD glass panel (matches the .G class in the HUD template) ──────

    /**
     * Glass HUD panel with a thin accent strip along the top — drawn with
     * the same point-coords convention as ctx.fill (x1, y1, x2, y2).
     */
    public static void drawHudPanel(net.minecraft.client.gui.GuiGraphics ctx,
                                    int x1, int y1, int x2, int y2,
                                    int bgColor, int accentColor) {
        int x = Math.min(x1, x2), y = Math.min(y1, y2);
        int w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
        if (w <= 0 || h <= 0) return;
        int r = h < 8 ? 1 : h < 14 ? 2 : 3;
        // Body
        fillRoundedRect(ctx, x, y, w, h, r, bgColor);
        // Top sheen (full width, leaves curved corners alone since we inset by r)
        if (h > 4) fillRect(ctx, x + r, y + 1, w - 2*r, 1, 0x16FFFFFF);
        // Accent border
        drawRoundedBorder(ctx, x, y, w, h, r,
                ((accentColor & 0x00FFFFFF) | ((Math.min(0xFF, ((accentColor >>> 24) & 0xFF) + 0x33)) << 24)));
        // Top accent strip
        if (accentColor != 0)
            fillRect(ctx, x + r, y, w - 2*r, 1, accentColor);
    }

    /**
     * Accent glow halo (used on hover for buttons / active cards).
     */
    public static void drawGlow(GuiGraphics ctx, int x, int y, int w, int h, int radius, int colour) {
        int r = Math.max(0, radius);
        int base = colour & 0x00FFFFFF;
        fillRoundedRect(ctx, x - 2, y - 2, w + 4, h + 4, r + 2, (0x22 << 24) | base);
        fillRoundedRect(ctx, x - 1, y - 1, w + 2, h + 2, r + 1, (0x33 << 24) | base);
    }

    /**
     * Polished Skyz HUD panel — same visual language as the ported screens
     * (gradient body, top sheen, top accent stripe, soft border). Draws with
     * the same x/y/w/h convention as {@link #fillRoundedRect}.
     *
     * <p>{@code accent} is the colour of the 1-px stripe along the top edge:
     * pass an element-specific colour (green for "good", red for "bad", or
     * the Skyz blue {@code 0xFF8CD2FF} as a neutral default). Passing {@code 0}
     * suppresses the stripe entirely.
     */
    public static void drawSkyzHudPanel(GuiGraphics ctx, int x, int y, int w, int h, int accent) {
        if (w <= 0 || h <= 0) return;
        int r = h < 8 ? 1 : h < 14 ? 2 : 3;
        // Gradient body — slightly lighter top → darker bottom for depth.
        fillRoundedRectGradient(ctx, x, y, w, h, r, 0xC8132C5A, 0xCC061533);
        // Top sheen — narrow white highlight just below the top edge.
        if (h > 4) fillRect(ctx, x + r, y + 1, w - 2 * r, 1, 0x1AFFFFFF);
        // 1-px Skyz-blue border
        drawRoundedBorder(ctx, x, y, w, h, r, 0x558CD2FF);
        // Top accent stripe (skip the curved corners by insetting r px each side)
        if (accent != 0 && w > 2 * r) fillRect(ctx, x + r, y, w - 2 * r, 1, accent);
    }

    /**
     * Skyz progress bar — rounded ends, gradient fill, subtle track.
     * Draws inside the rectangle (x, y, w, h); the caller is responsible
     * for any padding from a containing panel.
     *
     * <p>{@code pct} is the 0–100 fill percentage. {@code colTop} and
     * {@code colBot} define the gradient of the FILLED portion; the empty
     * portion uses a faint track colour so the bar is always visible at 0%.
     */
    public static void drawSkyzBar(GuiGraphics ctx, int x, int y, int w, int h,
                                    int pct, int colTop, int colBot) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(h / 2, 3);
        // Track (background)
        fillRoundedRect(ctx, x, y, w, h, r, 0x55061533);
        // Filled portion
        int filled = Math.max(0, Math.min(w, (int) ((long) w * pct / 100)));
        if (filled > 0) {
            // For very narrow fills, fall back to a square fill so the radius
            // doesn't eat the entire visible bar.
            int fr = filled < r * 2 ? 0 : r;
            fillRoundedRectGradient(ctx, x, y, filled, h, fr, colTop, colBot);
        }
        // Highlight line on top of the bar (1px) — gives the rounded glass look
        if (h >= 4) fillRect(ctx, x + r, y, w - 2 * r, 1, 0x33FFFFFF);
    }
}
