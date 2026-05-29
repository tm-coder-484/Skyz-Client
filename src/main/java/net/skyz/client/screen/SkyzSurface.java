package net.skyz.client.screen;

import io.wispforest.owo.ui.core.Surface;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;

/**
 * Reusable owo {@link Surface}s that draw the Skyz "glass card" aesthetic
 * matching skyz-client.html .card / .world-card / .server-card.
 *
 * Surfaces are drawn behind a component's children — apply via
 * {@code component.surface(SkyzSurface.CARD)}.
 */
public final class SkyzSurface {

    private SkyzSurface() {}

    /** Default card surface — soft dark-blue rounded panel with a subtle border. */
    public static final Surface CARD = (ctx, comp) -> {
        int r = 6;
        SkyzRenderHelper.fillRoundedRect(
                ctx, comp.x(), comp.y(), comp.width(), comp.height(),
                r, SkyzColors.CARD_BG);
        SkyzRenderHelper.drawRoundedBorder(
                ctx, comp.x(), comp.y(), comp.width(), comp.height(),
                r, SkyzColors.CARD_BORDER);
    };

    /** Toolbar / search-box backdrop — narrower, slightly more saturated. */
    public static final Surface PILL_INPUT = (ctx, comp) -> {
        int r = Math.min(8, comp.height() / 2);
        SkyzRenderHelper.fillRoundedRect(
                ctx, comp.x(), comp.y(), comp.width(), comp.height(),
                r, 0x55091E46);
        SkyzRenderHelper.drawRoundedBorder(
                ctx, comp.x(), comp.y(), comp.width(), comp.height(),
                r, 0x338CD2FF);
    };
}
