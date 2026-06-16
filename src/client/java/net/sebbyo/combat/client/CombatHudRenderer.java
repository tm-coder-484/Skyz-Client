package net.sebbyo.combat.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Combat timer pill, drawn bottom-centre. Borderless, and self-decays from the last
 * value the server sent so a missed update (death, disconnect, switching servers)
 * can never leave it stuck on screen.
 */
public final class CombatHudRenderer {

    private static volatile int seconds = 0;
    private static volatile long setAtMs = 0L;

    private CombatHudRenderer() {}

    public static void setSeconds(int s) {
        seconds = s;
        setAtMs = System.currentTimeMillis();
    }

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            int base = seconds;
            if (base <= 0) return;
            // Decay locally from the last server update so the timer is self-clearing.
            int secs = base - (int) ((System.currentTimeMillis() - setAtMs) / 1000L);
            if (secs <= 0) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            int sw = ctx.getScaledWindowWidth();
            int sh = ctx.getScaledWindowHeight();
            int w = 110, h = 16;
            int x = (sw - w) / 2;
            int y = sh - 70; // moved up from the health bar

            // Subtle backdrop (no border).
            ctx.fill(x, y, x + w, y + h, 0x99000000);

            // Depleting bar (20s window).
            float frac = Math.max(0f, Math.min(1f, secs / 20f));
            int barW = (int) ((w - 6) * frac);
            ctx.fill(x + 3, y + h - 4, x + 3 + barW, y + h - 2, 0xFFE2554C);

            // Label.
            String label = "⚔ " + secs + "s";
            int tw = mc.textRenderer.getWidth(label);
            ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + 3, 0xFFFFFFFF, true);
        });
    }
}
