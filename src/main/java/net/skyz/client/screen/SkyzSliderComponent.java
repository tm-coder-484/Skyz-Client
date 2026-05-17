package net.skyz.client.screen;

import io.wispforest.owo.ui.component.SliderComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.DrawContext;
import net.skyz.client.util.SkyzRenderHelper;

/**
 * Skyz-styled slider — replaces vanilla's button-textured slider rendering
 * with a rounded glass track + gradient fill + circular knob.
 *
 * <p>Owo's {@link SliderComponent} extends MC's {@code SliderWidget}, which
 * inherits {@code renderWidget(...)} from {@code ClickableWidget}. Overriding
 * {@code renderWidget} lets us replace the entire visual without calling
 * super, while value handling, click/drag, and the {@code onChanged} event
 * stream stay inherited.
 *
 * <p>Drop-in replacement: anywhere we used
 * {@code UIComponents.slider(Sizing.fill(100))} we now use
 * {@code new SkyzSliderComponent(Sizing.fill(100))}. The
 * {@code value() / message() / onChanged()} API is unchanged.
 */
public class SkyzSliderComponent extends SliderComponent {

    public SkyzSliderComponent(Sizing horizontalSizing) {
        super(horizontalSizing);
    }

    /**
     * Override the inherited render method to paint a Skyz track + knob
     * instead of the vanilla button slider. We do not call {@code super}
     * so vanilla's button background never renders.
     *
     * <p>Layout:
     * <ul>
     *   <li>Full-width rounded track (radius = h/2 → pill shape).</li>
     *   <li>Filled portion left of the knob — Skyz blue gradient.</li>
     *   <li>Top sheen highlight for the glass look.</li>
     *   <li>Circular knob at the value position with a subtle drop shadow.</li>
     *   <li>Brighter border / knob colour when hovered.</li>
     * </ul>
     */
    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        double val = this.value();
        boolean hovered = this.isHovered() || this.isFocused();
        boolean active  = this.active;

        int r = Math.max(2, h / 2);

        // Track — rounded dark backdrop.
        int trackBg     = active ? 0x88061533 : 0x55061533;
        int trackBorder = !active ? 0x55555560
                : hovered ? 0xCC8CDCFF : 0x778CD2FF;
        SkyzRenderHelper.fillRoundedRect(ctx, x, y, w, h, r, trackBg);

        // Filled portion — Skyz blue gradient up to the knob.
        int filledW = (int) Math.round(w * val);
        if (filledW > 0 && active) {
            int fillR = filledW < r * 2 ? 0 : r;
            int top = hovered ? 0xFF6AD0FF : 0xFF4AB8F0;
            int bot = hovered ? 0xFF2870C0 : 0xFF1864A0;
            SkyzRenderHelper.fillRoundedRectGradient(ctx, x, y, filledW, h, fillR, top, bot);
        }

        // Top sheen — narrow white highlight strip below the top edge.
        if (h > 4) {
            int sheenAlpha = active ? 0x18 : 0x10;
            ctx.fill(x + r, y + 1, x + w - r, y + 2, (sheenAlpha << 24) | 0xFFFFFF);
        }

        // 1-px border on top.
        SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, r, trackBorder);

        // Knob — outlined circle at the value position.
        int knobX = x + Math.max(r, Math.min(w - r, (int) Math.round(w * val)));
        int knobY = y + h / 2;
        int knobR = Math.max(3, h / 2 - 1);
        // Drop shadow.
        SkyzRenderHelper.fillCircle(ctx, knobX, knobY + 1, knobR + 1, 0x99000000);
        // Body — brighter when hovered.
        int knobCol = !active ? 0xFF888888
                : hovered ? 0xFF8CDCFF : 0xFFEAF4FF;
        SkyzRenderHelper.fillCircle(ctx, knobX, knobY, knobR, knobCol);
        // Inner highlight (top-left dot) for glass look.
        SkyzRenderHelper.fillCircle(ctx, knobX - 1, knobY - 1, Math.max(1, knobR / 2), 0x66FFFFFF);
    }
}
