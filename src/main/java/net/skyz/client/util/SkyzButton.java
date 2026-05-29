package net.skyz.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Faithful port of the HTML {@code .mc-btn} look:
 *
 *   - Vertical glass body with a subtle top sheen
 *   - 1-px gradient highlight strip across the top edge ({@code ::after})
 *   - Inner inset shadow at the bottom
 *   - Centered icon + UPPERCASE label with letter-spacing
 *   - Hover: lift (translateY -1), brighter body, accent border, soft glow halo
 *   - Soft drop shadow under the body
 */
public class SkyzButton extends AbstractWidget {

    public enum Style { NORMAL, DANGER }

    private final Runnable action;
    private final Style    style;
    private final String   rawLabel;
    private float          hoverAnim = 0f;

    public SkyzButton(int x, int y, int w, int h, String label, Runnable action, Style style) {
        super(x, y, w, h, Component.literal(label));
        this.action   = action;
        this.style    = style;
        this.rawLabel = label;
    }

    public static SkyzButton of(int x, int y, int w, int h, String label, Runnable action) {
        return new SkyzButton(x, y, w, h, label, action, Style.NORMAL);
    }

    public static SkyzButton danger(int x, int y, int w, int h, String label, Runnable action) {
        return new SkyzButton(x, y, w, h, label, action, Style.DANGER);
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        action.run();
    }

    @Override
    public void playDownSound(SoundManager sm) {
        super.playDownSound(sm);
    }

    // ── Render ───────────────────────────────────────────────────────────
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        hoverAnim = isHovered()
                ? Math.min(1f, hoverAnim + delta * 0.20f)
                : Math.max(0f, hoverAnim - delta * 0.20f);

        int bx = getX();
        int by = getY() - Math.round(hoverAnim);   // lift on hover (translateY(-1))
        int w  = getWidth();
        int h  = getHeight();
        int radius = Math.min(h / 2, Math.min(8, w / 2));

        // ── Palette per style ──
        int idleTop, idleBot, hoverTop, hoverBot;
        int idleBorder, hoverBorder;
        int idleHi, hoverHi;
        int idleText, hoverText;
        int glowRgb;

        if (style == Style.DANGER) {
            idleTop     = 0x99502028;  idleBot     = 0x99200810;
            hoverTop    = 0xCC78283A;  hoverBot    = 0xDD2A0810;
            idleBorder  = 0x44FF7878;  hoverBorder = 0xAAFF9090;
            idleHi      = 0x40FFB4B4;  hoverHi     = 0x88FFD0D0;
            idleText    = 0xE6FFC4C4;  hoverText   = 0xFFFFE0E0;
            glowRgb     = 0xFF6464;
        } else {
            // Slate-blue translucent glass (matches HTML btn-bg/btn-hover with hue boost)
            idleTop     = 0xAA4A608A;  idleBot     = 0xAA12182A;
            hoverTop    = 0xCC6886B4;  hoverBot    = 0xDD181F36;
            idleBorder  = 0x66B4D8FF;  hoverBorder = 0xCCC8E4FF;
            idleHi      = 0x55FFFFFF;  hoverHi     = 0x99FFFFFF;
            idleText    = 0xF2EEF6FF;  hoverText   = 0xFFFFFFFF;
            glowRgb     = SkyzTheme.ACCENT1 & 0x00FFFFFF;
        }

        int top      = SkyzColors.lerp(idleTop,     hoverTop,     hoverAnim);
        int bot      = SkyzColors.lerp(idleBot,     hoverBot,     hoverAnim);
        int border   = SkyzColors.lerp(idleBorder,  hoverBorder,  hoverAnim);
        int hi       = SkyzColors.lerp(idleHi,      hoverHi,      hoverAnim);
        int textCol  = SkyzColors.lerp(idleText,    hoverText,    hoverAnim);

        // ── 1. Drop shadow under the button (HTML 0 4px 20px rgba(0,0,0,0.3)) ──
        SkyzRenderHelper.fillRoundedRect(ctx, bx,     by + h,     w,     2, radius, 0x44000000);
        SkyzRenderHelper.fillRoundedRect(ctx, bx - 1, by + h + 1, w + 2, 2, radius, 0x33000000);
        SkyzRenderHelper.fillRoundedRect(ctx, bx - 2, by + h + 3, w + 4, 2, radius, 0x1A000000);

        // ── 2. Hover glow halo (0 0 24px rgba(100,190,255,0.22)) ──
        if (hoverAnim > 0.05f) {
            int glowAlpha = (int)(0x44 * hoverAnim);
            SkyzRenderHelper.fillRoundedRect(ctx, bx - 3, by - 2, w + 6, h + 4, radius + 2,
                    (glowAlpha << 24) | glowRgb);
        }

        // ── 3. Body — vertical glass gradient ──
        int avg = SkyzColors.lerp(top, bot, 0.5f);
        SkyzRenderHelper.fillRoundedRect(ctx, bx, by, w, h, radius, avg);
        if (h > 4 && w > radius * 2) {
            SkyzRenderHelper.fillGradientV(ctx, bx + radius, by + 1, w - 2*radius, h / 2,
                    extractTint(top, 0x33), 0x00000000);
            SkyzRenderHelper.fillGradientV(ctx, bx + radius, by + h / 2, w - 2*radius, h - h / 2 - 1,
                    0x00000000, extractTint(bot, 0x44));
        }

        // ── 4. Top inner highlight strip (HTML inset 0 1px 0 rgba(255,255,255,0.1)) ──
        if (h > 4 && w > radius * 2) {
            SkyzRenderHelper.fillRect(ctx, bx + radius, by + 1, w - 2*radius, 1, hi);
        }
        // ── 5. Top accent gradient strip (HTML .mc-btn::after) ──
        // 1px line, fades transparent → bright → transparent across the top
        if (w > 12) {
            int accentAlpha = (int)(0x33 + 0x44 * hoverAnim);
            int inset = (int)(w * 0.12);
            int sliceCount = 16;
            int stripW = w - inset * 2;
            for (int i = 0; i < sliceCount; i++) {
                int sx = bx + inset + (stripW * i) / sliceCount;
                int ex = bx + inset + (stripW * (i + 1)) / sliceCount;
                float t = i / (float) sliceCount;
                // triangle wave: 0 at ends, 1 in middle
                float a = 1f - Math.abs(t * 2f - 1f);
                int aa = (int) (accentAlpha * a);
                if (aa <= 0) continue;
                ctx.fill(sx, by, ex, by + 1, (aa << 24) | 0xFFFFFF);
            }
        }
        // ── 6. Bottom inner shadow (inset 0 -1px 0 rgba(0,0,0,0.2)) ──
        if (h > 6 && w > radius * 2) {
            SkyzRenderHelper.fillRect(ctx, bx + radius, by + h - 2, w - 2*radius, 1, 0x44000000);
        }

        // ── 7. Hover sweep (subtle moving white sliver) ──
        if (hoverAnim > 0.05f) {
            int sweepW = (int) ((w - 2 * radius) * hoverAnim);
            int alpha  = (int) (0x18 * hoverAnim);
            if (sweepW > 0 && alpha > 0) {
                ctx.fill(bx + radius, by + 2, bx + radius + sweepW, by + 3,
                        (alpha << 24) | 0xFFFFFF);
            }
        }

        // ── 8. Border ──
        SkyzRenderHelper.drawRoundedBorder(ctx, bx, by, w, h, radius, border);

        // ── 9. Centered icon + UPPERCASE letter-spaced label (HTML .mc-btn) ──
        Font tr = Minecraft.getInstance().font;
        drawCenteredIconLabel(ctx, tr, bx + w / 2, by + (h - 8) / 2, textCol);
    }

    /**
     * Splits the button label into icon (everything up to the first double-space
     * separator) and text, then renders icon + UPPERCASE letter-spaced text
     * centered as a group. Uses a 1-px letter-spacing for the Feather feel.
     */
    private void drawCenteredIconLabel(GuiGraphicsExtractor ctx, Font tr,
                                       int cx, int y, int colour) {
        String icon, text;
        int splitIdx = rawLabel.indexOf("  ");
        if (splitIdx > 0) {
            icon = rawLabel.substring(0, splitIdx);
            text = rawLabel.substring(splitIdx + 2);
        } else {
            icon = "";
            text = rawLabel;
        }
        String upper = text.toUpperCase();
        int spacing = 1;
        int textW = SkyzRenderHelper.textWidthSpaced(tr, upper, spacing);
        int iconW = icon.isEmpty() ? 0 : tr.width(icon);
        int gap   = (icon.isEmpty() || text.isEmpty()) ? 0 : 5;
        int totalW = iconW + gap + textW;

        int sx = cx - totalW / 2;
        if (!icon.isEmpty()) {
            ctx.text(tr, icon, sx, y, colour, true);
            sx += iconW + gap;
        }
        SkyzRenderHelper.drawTextSpaced(ctx, tr, upper, sx, y, spacing, colour, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }

    /** Returns {@code colour} with alpha forced to {@code maxAlpha} (used for sheen tints). */
    private static int extractTint(int colour, int maxAlpha) {
        return ((maxAlpha & 0xFF) << 24) | (colour & 0x00FFFFFF);
    }
}
