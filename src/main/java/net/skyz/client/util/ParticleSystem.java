package net.skyz.client.util;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * Floating particle motes matching the HTML canvas particle system.
 *
 * Each particle is rendered as a stack of concentric, alpha-fading squares —
 * cheap to draw but reads as a soft glowing star at MC GUI scale, much closer
 * to the HTML reference than a single pixel.
 *
 * Brightness layers (from outer to inner):
 *   • r=3  outer halo, very low alpha — bleeds the colour into the background
 *   • r=2  mid ring, low alpha
 *   • r=1  core ring
 *   • 1×1  bright centre dot
 *
 * Shimmer particles flash a brighter cross pattern to mimic CSS twinkle.
 */
public class ParticleSystem {

    private static final int COUNT = 110;
    private static final Random RNG = new Random();

    private final float[] x, y, vx, vy, life, maxLife, r;
    private final boolean[] shimmer;
    private final int[] tint;
    private int screenW, screenH;
    private boolean enabled = true;

    /** Subtle palette of cool whites + light blues — picked per-particle in spawn(). */
    private static final int[] PALETTE = {
            0xFFFFFFFF, // pure white
            0xFFE8F4FF, // ice white
            0xFFC0E0FF, // soft blue
            0xFFA8D4FF, // sky blue
            0xFFB4E6FF, // pale cyan
    };

    public ParticleSystem() {
        x       = new float[COUNT];
        y       = new float[COUNT];
        vx      = new float[COUNT];
        vy      = new float[COUNT];
        life    = new float[COUNT];
        maxLife = new float[COUNT];
        r       = new float[COUNT];
        shimmer = new boolean[COUNT];
        tint    = new int[COUNT];
    }

    public void resize(int w, int h) {
        screenW = w;
        screenH = h;
        for (int i = 0; i < COUNT; i++) spawnMote(i, true);
    }

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }

    private void spawnMote(int i, boolean randomY) {
        x[i]       = RNG.nextFloat() * screenW;
        y[i]       = randomY ? RNG.nextFloat() * screenH : screenH + 4;
        vx[i]      = (RNG.nextFloat() - 0.5f) * 0.10f;
        vy[i]      = -(0.08f + RNG.nextFloat() * 0.22f);
        life[i]    = 0;
        maxLife[i] = 400 + RNG.nextFloat() * 700;
        r[i]       = 0.5f + RNG.nextFloat() * 1.6f;
        shimmer[i] = RNG.nextFloat() < 0.35f;
        tint[i]    = PALETTE[RNG.nextInt(PALETTE.length)];
    }

    public void tick(GuiGraphics ctx, float delta) {
        if (!enabled || screenW == 0) return;

        for (int i = 0; i < COUNT; i++) {
            x[i]    += vx[i] * delta;
            y[i]    += vy[i] * delta;
            life[i] += delta;

            if (life[i] >= maxLife[i] || y[i] < -4) {
                spawnMote(i, false);
                continue;
            }

            float t = life[i] / maxLife[i];
            // Smooth fade-in, hold, fade-out envelope.
            float alpha = t < 0.12f ? t / 0.12f
                        : t > 0.82f ? (1f - t) / 0.18f
                        :             1f;

            int px = (int) x[i], py = (int) y[i];
            int colour = tint[i] & 0x00FFFFFF;

            if (shimmer[i]) {
                drawShimmer(ctx, px, py, alpha, colour);
            } else {
                drawSoftMote(ctx, px, py, r[i], alpha, colour);
            }
        }
    }

    /**
     * Soft round-ish mote: nested squares with falling alpha, finished with a
     * single bright centre pixel. Pixelated by nature, but multiple alpha
     * layers make the edge look softly blended at MC scale.
     */
    private void drawSoftMote(GuiGraphics ctx, int px, int py,
                              float radius, float alphaScale, int rgb) {
        float a = Math.max(0f, Math.min(1f, alphaScale));

        // Outer halo (very faint, biggest extent).
        int aOuter = (int)(a * 0.10f * 255);
        if (aOuter > 0) ctx.fill(px - 3, py - 3, px + 3, py + 3, (aOuter << 24) | rgb);

        // Mid halo.
        int aMid = (int)(a * 0.22f * 255);
        if (aMid > 0)   ctx.fill(px - 2, py - 2, px + 2, py + 2, (aMid << 24) | rgb);

        // Inner ring — only for the larger motes.
        if (radius > 0.9f) {
            int aIn = (int)(a * 0.40f * 255);
            if (aIn > 0) ctx.fill(px - 1, py - 1, px + 1, py + 1, (aIn << 24) | rgb);
        }

        // Bright centre.
        int aCore = (int)(a * 0.85f * 255);
        if (aCore > 0)  ctx.fill(px, py, px + 1, py + 1, (aCore << 24) | rgb);
    }

    /**
     * Twinkling shimmer particle — same soft halo as the regular mote PLUS a
     * cross pattern of bright pixels for the sparkle effect.
     */
    private void drawShimmer(GuiGraphics ctx, int px, int py,
                             float alphaScale, int rgb) {
        float a = Math.max(0f, Math.min(1f, alphaScale));

        // Halo background (same as soft mote).
        int aOuter = (int)(a * 0.14f * 255);
        if (aOuter > 0) ctx.fill(px - 3, py - 3, px + 3, py + 3, (aOuter << 24) | rgb);
        int aMid = (int)(a * 0.26f * 255);
        if (aMid > 0)   ctx.fill(px - 2, py - 2, px + 2, py + 2, (aMid << 24) | rgb);

        // Sparkle cross — 4-direction extensions of the bright centre.
        int aSpike = (int)(a * 0.55f * 255);
        if (aSpike > 0) {
            ctx.fill(px - 2, py,     px + 3, py + 1, (aSpike << 24) | rgb); // horiz
            ctx.fill(px,     py - 2, px + 1, py + 3, (aSpike << 24) | rgb); // vert
        }

        // Bright white core (always white, regardless of tint).
        int aCore = (int)(a * 1.0f * 255);
        if (aCore > 0)  ctx.fill(px, py, px + 1, py + 1, (aCore << 24) | 0xFFFFFF);
    }
}
