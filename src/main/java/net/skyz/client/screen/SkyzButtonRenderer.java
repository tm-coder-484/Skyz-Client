package net.skyz.client.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import net.skyz.client.util.SkyzRenderHelper;

/**
 * Skyz-themed glass button renderers, applied to owo {@link ButtonComponent}
 * instances via {@code btn.renderer(SkyzButtonRenderer.DEFAULT)}.
 *
 * The renderer is responsible only for the BUTTON CHROME (background, border,
 * highlights). owo's {@code ButtonComponent} draws the text itself, centred,
 * after the renderer runs — so we never have to worry about labels here.
 *
 * Visual reference: skyz-client.html .mc-btn (default) and .mc-btn.quit.
 *   bg          rgba(20,60,110,0.40)
 *   bg:hover    rgba(40,100,170,0.55)
 *   border      rgba(140,210,255,0.35)
 *   border-radius 14 px on a 48 px-tall HTML button (≈ 30 % of height)
 *   inset top   rgba(255,255,255,0.10)
 *   inset bot   rgba(0,0,0,0.20)
 *
 * Implementation note: the body uses
 * {@link SkyzRenderHelper#fillRoundedRectGradient} so the vertical
 * top-light → bottom-dark shading hugs the rounded shape on every scan-line.
 * Earlier versions used a separate rectangular {@code fillGradientV} sheen
 * on top of a solid rounded body, which produced a visible rectangular
 * highlight band in the middle (because the sheen rectangle sat between
 * the corner curves rather than following them). The single-pass scan-line
 * gradient eliminates that artefact entirely.
 */
public final class SkyzButtonRenderer {

    private SkyzButtonRenderer() {}

    /**
     * Corner radius. {@link SkyzRenderHelper#fillRoundedRectGradient} clamps
     * to {@code min(w, h) / 2}, so passing 9 on a 19-px-tall button results
     * in a true pill shape.
     */
    private static final int CORNER_R = 9;

    // ── Default Skyz button (blue glass) ─────────────────────────────────
    public static final ButtonComponent.Renderer DEFAULT = (ctx, btn, delta) -> {
        int x = btn.getX();
        int y = btn.getY();
        int w = btn.getWidth();
        int h = btn.getHeight();
        boolean active = btn.active;
        boolean hover  = active && btn.isHovered();

        int bgTop, bgBottom, border;
        if (!active) {
            bgTop = bgBottom = 0x33000000;
            border = 0x59555560;
        } else if (hover) {
            // Brighter, with a top-light -> bottom-dark vertical shading.
            bgTop    = 0xCC3070C0;
            bgBottom = 0xB3163C72;
            border   = 0xCC8CDCFF;
        } else {
            bgTop    = 0x802864AE;
            bgBottom = 0x66102C5A;
            border   = 0x808CDCFF;
        }

        // Body — single-pass scan-line gradient that respects the curve.
        SkyzRenderHelper.fillRoundedRectGradient(ctx, x, y, w, h, CORNER_R, bgTop, bgBottom);

        // 1-px outline.
        SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, CORNER_R, border);

        // Hover glow — soft outer halo, drawn one pixel beyond the border.
        if (hover) {
            SkyzRenderHelper.drawRoundedBorder(
                    ctx, x - 1, y - 1, w + 2, h + 2, CORNER_R + 1, 0x408CDCFF);
        }
    };

    // ── Quit-styled button (red-tinted glass) ────────────────────────────
    public static final ButtonComponent.Renderer QUIT = (ctx, btn, delta) -> {
        int x = btn.getX();
        int y = btn.getY();
        int w = btn.getWidth();
        int h = btn.getHeight();
        boolean active = btn.active;
        boolean hover  = active && btn.isHovered();

        int bgTop, bgBottom, border;
        if (!active) {
            bgTop = bgBottom = 0x33000000;
            border = 0x59555560;
        } else if (hover) {
            bgTop    = 0xCC8C2832;
            bgBottom = 0x995A1A20;
            border   = 0xCCFF7878;
        } else {
            bgTop    = 0x40140A12;
            bgBottom = 0x33000000;
            border   = 0x66FF8C8C;
        }

        SkyzRenderHelper.fillRoundedRectGradient(ctx, x, y, w, h, CORNER_R, bgTop, bgBottom);
        SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, CORNER_R, border);

        if (hover) {
            SkyzRenderHelper.drawRoundedBorder(
                    ctx, x - 1, y - 1, w + 2, h + 2, CORNER_R + 1, 0x40FF7878);
        }
    };

    // ── Nav-back / small action style (subtle, less prominent) ───────────
    public static final ButtonComponent.Renderer NAV_BACK = (ctx, btn, delta) -> {
        int x = btn.getX();
        int y = btn.getY();
        int w = btn.getWidth();
        int h = btn.getHeight();
        boolean active = btn.active;
        boolean hover  = active && btn.isHovered();

        int bgTop, bgBottom, border;
        if (!active) {
            bgTop = bgBottom = 0x33000000;
            border = 0x59555560;
        } else if (hover) {
            bgTop    = 0x802864AE;
            bgBottom = 0x66143C6E;
            border   = 0x998CDCFF;
        } else {
            bgTop    = 0x4D0A2850;
            bgBottom = 0x4D061A38;
            border   = 0x4D8CDCFF;
        }

        SkyzRenderHelper.fillRoundedRectGradient(ctx, x, y, w, h, CORNER_R, bgTop, bgBottom);
        SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, CORNER_R, border);
    };
}
