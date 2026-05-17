package net.skyz.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses pumpkin-blur, fire, and portal overlays based on Skyz toggles.
 * Targets the private renderOverlay(DrawContext, Identifier, float) helper.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudOverlayMixin {

    @Inject(method = "renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V",
            at = @At("HEAD"), cancellable = true)
    private void skyz$suppressOverlay(DrawContext ctx, Identifier id, float opacity, CallbackInfo ci) {
        if (id == null) return;
        String path = id.getPath();
        if (SkyzClientState.noPumpkinBlur && path.contains("pumpkin")) {
            ci.cancel();
        } else if (SkyzClientState.noFireOverlay && path.contains("fire")) {
            ci.cancel();
        }
    }
}
