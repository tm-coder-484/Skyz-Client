package net.skyz.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A slide-down toast notification matching the HTML .toast element.
 * Show by calling {@link #show(String)}.  Automatically disappears after 2.8 s.
 */
public class Toast {

    private String message  = "";
    private float  timer    = 0f;      // counts up while showing
    private float  anim     = 0f;      // 0 = hidden (off top), 1 = fully shown
    private boolean visible = false;

    private static final float SHOW_DURATION = 2.8f * 20f; // ticks
    private static final float ANIM_SPEED    = 0.12f;

    public void show(String msg) {
        this.message = msg;
        this.timer   = 0f;
        this.visible = true;
    }

    /** Called every render frame. {@code delta} is fractional ticks. */
    public void render(DrawContext ctx, int screenW, float delta) {
        if (!visible && anim <= 0f) return;

        // Drive timer
        if (visible) {
            timer += delta;
            if (timer >= SHOW_DURATION) {
                visible = false;
            }
        }

        // Animate slide
        float target = visible ? 1f : 0f;
        anim += (target - anim) * ANIM_SPEED * delta * 6f;
        if (anim < 0.001f && !visible) { anim = 0f; return; }

        var tr = MinecraftClient.getInstance().textRenderer;
        int msgW   = tr.getWidth(message);
        int padH   = 9, padW = 20;
        int boxW   = msgW + padW * 2;
        int boxH   = 8 + padH * 2;
        int bx     = (screenW - boxW) / 2;
        int by     = (int)(-boxH + (boxH + 18) * anim);    // slides down from -boxH

        // Background panel
        SkyzRenderHelper.fillPanel(ctx, bx, by, boxW, boxH,
                0xE6050F32, 0x4D8CD2FF);

        // Text centred
        ctx.drawCenteredTextWithShadow(tr, message,
                screenW / 2, by + padH, SkyzColors.TEXT_PRIMARY);
    }
}
