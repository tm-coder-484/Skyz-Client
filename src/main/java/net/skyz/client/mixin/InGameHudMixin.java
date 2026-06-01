package net.skyz.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 *   <li>{@link #tickAutoTotem(Minecraft)} — pure interaction-manager
 *       logic, no rendering. Driven from the same render-frame cadence as
 *       the HUD because that's how the original implementation hooked it.</li>
 * </ul>
 *
 * <p>ESP / hack rendering used to live here too but moved to the world-
 * render hook to draw real 3-D outlines instead of 2-D world-to-screen
 * projections.
 *
 * <p>Targets {@link Gui} — the 26.1 Mojang name for what used to be
 * {@code InGameHud}. The render signature is
 * {@code render(GuiGraphicsExtractor, DeltaTracker)}.
 */
@Mixin(Gui.class)
public class InGameHudMixin {

    /** Wallclock at which this play session started. Read by Session Timer. */
    private static long sessionStart = 0;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void skyz$render(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        int W = ctx.guiWidth();
        int H = ctx.guiHeight();
        SkyzHudState.initDefaults(W, H);

        if (sessionStart == 0) sessionStart = System.currentTimeMillis();
        SkyzClientState.updateCps();

        // Draw all enabled HUD elements via the polished renderer.
        SkyzHudRenderer.render(ctx, client, W, H, sessionStart);

        // 2D ESP removed — handled by WorldRenderEvents.LAST with real 3D boxes.
        if (SkyzClientState.autoTotem) tickAutoTotem(client);
    }

    // ─── Auto Totem ──────────────────────────────────────────────────────
    /**
     * Swap the first totem in inventory into the offhand whenever the
     * offhand isn't already a totem. The vanilla container MouseButtonEvent is the
     * cleanest way to do this without going through {@code Inventory}
     * directly — same pattern other client mods use.
     */
    private void tickAutoTotem(Minecraft client) {
        if (client.player == null || client.gameMode == null) return;
        net.minecraft.world.item.ItemStack offhand = client.player
                .getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if (offhand.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) return;

        net.minecraft.world.entity.player.Inventory inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
                // 26.1: handleInventoryMouseClick + ClickType.SWAP -> handleContainerInput + ContainerInput.SWAP
                client.gameMode.handleContainerInput(
                        client.player.containerMenu.containerId,
                        40,                                  // offhand slot in the player inventory menu
                        i < 9 ? i : i,                       // hotbar button index
                        net.minecraft.world.inventory.ContainerInput.SWAP,
                        client.player);
                break;
            }
        }
    }
}
