package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.skyz.client.render.SkyzHudRenderer;
import net.skyz.client.util.SkyzClientState;
import net.skyz.client.util.SkyzHudState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the vanilla in-game HUD render and dispatches to Skyz subsystems.
 *
 * <p>Phase 2c port: this used to be ~960 lines of mixed concerns
 * (per-element rendering, minimap cache, ESP, autoTotem, biome lookup).
 * Almost all of the HUD-element rendering is now in
 * {@link SkyzHudRenderer} — that left this class small enough to read at
 * a glance, and lets the renderer be tweaked / restyled without touching
 * the mixin.
 *
 * <p>What remains here:
 * <ul>
 *   <li>The {@code @Inject} hook itself, plus the once-per-session timer
 *       and CPS update tick (those live with the hook because nothing
 *       else fires reliably every render frame).</li>
 *   <li>{@link #tickAutoTotem(MinecraftClient)} — pure interaction-manager
 *       logic, no rendering. Driven from the same render-frame cadence as
 *       the HUD because that's how the original implementation hooked it.</li>
 * </ul>
 *
 * <p>ESP / hack rendering used to live here too but moved to
 * {@code WorldRendererMixin} to draw real 3-D outlines instead of 2-D
 * world-to-screen projections.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    /** Wallclock at which this play session started. Read by Session Timer. */
    private static long sessionStart = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void skyz$render(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int W = ctx.getScaledWindowWidth();
        int H = ctx.getScaledWindowHeight();
        SkyzHudState.initDefaults(W, H);

        if (sessionStart == 0) sessionStart = System.currentTimeMillis();
        SkyzClientState.updateCps();

        // Draw all enabled HUD elements via the polished renderer.
        SkyzHudRenderer.render(ctx, client, W, H, sessionStart);

        // 2D ESP removed — handled by WorldRendererMixin with real 3D boxes.
        if (SkyzClientState.autoTotem) tickAutoTotem(client);
    }

    // ─── Auto Totem ──────────────────────────────────────────────────────
    /**
     * Swap the first totem in inventory into the offhand whenever the
     * offhand isn't already a totem. The vanilla container click is the
     * cleanest way to do this without going through {@code PlayerInventory}
     * directly — same pattern other client mods use.
     */
    private void tickAutoTotem(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        net.minecraft.item.ItemStack offhand = client.player
                .getEquippedStack(net.minecraft.entity.EquipmentSlot.OFFHAND);
        if (offhand.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) return;

        net.minecraft.entity.player.PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
                client.interactionManager.clickSlot(
                        client.player.currentScreenHandler.syncId,
                        40,                        // offhand slot in the player inventory screen
                        i < 9 ? i : i,             // hotbar button index
                        net.minecraft.screen.slot.SlotActionType.SWAP,
                        client.player);
                break;
            }
        }
    }
}
