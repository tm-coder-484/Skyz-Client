package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

/**
 * Skyz-styled replacement for vanilla {@link ProgressScreen}.
 *
 * <p>Vanilla {@code ProgressScreen} implements {@code ProgressListener}
 * and the operation calling it pushes title / task / percentage updates
 * over time. Our mixin can't easily intercept those listener callbacks
 * without forwarding to a stand-in object, so instead we keep the vanilla
 * {@code ProgressScreen} alive as the listener target and render the
 * Skyz UI on top via direct field access — see
 * {@link net.skyz.client.mixin.ProgressScreenMixin}.
 *
 * <p>This class holds a reference to the source vanilla screen and reads
 * its title / task / progress every frame to keep the UI fresh.
 */
public class SkyzProgressScreen extends BaseUIModelScreen<FlowLayout> {

    private final ProgressScreen source;
    private final Accessor accessor;

    private LabelComponent titleLbl;
    private LabelComponent taskLbl;
    private LabelComponent pctLbl;
    private FlowLayout     barPlaceholder;

    public SkyzProgressScreen(ProgressScreen source, Accessor accessor) {
        super(FlowLayout.class, Identifier.of("skyz_client", "progress"));
        this.source   = source;
        this.accessor = accessor;
    }

    /**
     * Read-only view onto vanilla {@code ProgressScreen}'s private fields.
     * Implemented in {@link net.skyz.client.mixin.ProgressScreenMixin} via
     * {@code @Shadow} so we don't need reflection.
     */
    public interface Accessor {
        Text title();
        Text task();
        int  progress();
        boolean done();
    }

    @Override
    protected void build(FlowLayout root) {
        FlowLayout card = root.childById(FlowLayout.class, "card");
        if (card != null) card.surface(SkyzSurface.CARD);

        titleLbl       = root.childById(LabelComponent.class, "lbl-title");
        taskLbl        = root.childById(LabelComponent.class, "lbl-task");
        pctLbl         = root.childById(LabelComponent.class, "lbl-pct");
        barPlaceholder = root.childById(FlowLayout.class, "bar-placeholder");
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Pull fresh values from the source screen — they change as the
        // operation progresses (e.g. "Saving level..." → "Saving chunks...").
        Text title = accessor.title();
        Text task  = accessor.task();
        int  pct   = accessor.progress();

        if (titleLbl != null && title != null)  titleLbl.text(title);
        if (taskLbl  != null && task  != null)  taskLbl.text(task);
        if (pctLbl   != null) pctLbl.text(Text.literal(Math.max(0, Math.min(100, pct)) + "%"));

        super.render(ctx, mouseX, mouseY, delta);

        // Draw the actual progress bar over the placeholder. Reading the
        // placeholder's bounds after super.render gives us laid-out coords.
        if (barPlaceholder != null) {
            int x = barPlaceholder.x();
            int y = barPlaceholder.y();
            int w = barPlaceholder.width();
            int h = barPlaceholder.height();
            int clamped = Math.max(0, Math.min(100, pct));
            SkyzRenderHelper.drawSkyzBar(ctx, x, y, w, h, clamped,
                    0xFF4AB8F0, 0xFF1864A0);
        }
    }
}
