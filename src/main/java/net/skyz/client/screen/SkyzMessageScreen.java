package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

/**
 * Skyz-styled replacement for vanilla {@code MessageScreen}.
 *
 * <p>Used as the generic status overlay between gameplay states —
 * "Saving world…", "Disconnecting…", "Loading…", server-redirect prompts
 * etc. Receives the original title text from the vanilla mixin and
 * displays it in a Skyz-themed centred card with an animated dot pulse
 * to make it visually obvious the game hasn't frozen.
 *
 * <p>{@link #shouldCloseOnEsc()} returns false (matches vanilla) so
 * pressing Escape during a save doesn't bypass the operation.
 */
public class SkyzMessageScreen extends BaseUIModelScreen<FlowLayout> {
    /** Alias for the inherited Minecraft instance (26.1 renamed the Screen field client->minecraft). */
    private final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();


    private final Component message;
    private long startMs = 0;

    public SkyzMessageScreen(Component message) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "loading_message"));
        this.message = message != null ? message : Component.literal("Loading…");
    }

    @Override
    protected void build(FlowLayout root) {
        FlowLayout card = root.childById(FlowLayout.class, "card");
        if (card != null) card.surface(SkyzSurface.CARD);

        LabelComponent msg = root.childById(LabelComponent.class, "lbl-message");
        if (msg != null) msg.text(message);
    }

    @Override public boolean isPauseScreen()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Gradient veil — matches the rest of the Skyz screens.
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (startMs == 0) startMs = System.currentTimeMillis();
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Three-dot pulse below the message — quick visual cue that the
        // game is alive and working. Phased over 1500 ms.
        long t = System.currentTimeMillis() - startMs;
        int phase = (int) ((t / 250L) % 4);
        int cy = height / 2 + 38;
        int cx = width / 2;
        for (int i = 0; i < 3; i++) {
            int alpha = (i == phase) ? 0xFF : 0x66;
            int color = (alpha << 24) | 0x8CD2FF;
            SkyzRenderHelper.fillCircle(ctx, cx + (i - 1) * 12, cy, 3, color);
        }
    }
}
