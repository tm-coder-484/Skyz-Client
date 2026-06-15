package net.sebbyo.combat.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/** Style-A combat timer pill, drawn bottom-centre above the health bar. */
public final class CombatHudRenderer {

    private static volatile int seconds = 0;

    private CombatHudRenderer() {}

    public static void setSeconds(int s) {
        seconds = s;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            int secs = seconds;
            if (secs <= 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            int sw = ctx.getScaledWindowWidth();
            int sh = ctx.getScaledWindowHeight();
            int w = 120, h = 18;
            int x = (sw - w) / 2;
            int y = sh - 55;

            // Pill body + 1px red frame.
            ctx.fill(x, y, x + w, y + h, 0xC00C1119);
            ctx.fill(x, y, x + w, y + 1, 0xFFD9433B);
            ctx.fill(x, y + h - 1, x + w, y + h, 0xFFD9433B);
            ctx.fill(x, y, x + 1, y + h, 0xFFD9433B);
            ctx.fill(x + w - 1, y, x + w, y + h, 0xFFD9433B);

            // Depleting bar (assumes a 20s max window).
            float frac = Math.max(0f, Math.min(1f, secs / 20f));
            int barW = (int) ((w - 8) * frac);
            ctx.fill(x + 4, y + h - 5, x + 4 + barW, y + h - 3, 0xFFE2554C);

            // Label: sword glyph + seconds.
            String label = "⚔ " + secs + "s";
            int tw = mc.textRenderer.getWidth(label);
            ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + 4, 0xFFFFFFFF, true);
        });
    }
}
