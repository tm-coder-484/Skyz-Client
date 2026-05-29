package net.skyz.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses pumpkin-blur, fire, and portal overlays based on Skyz toggles.
 *
 * <p>Targets the private {@code renderTextureOverlay(GuiGraphicsExtractor,
 * Identifier, float)} helper on {@link Gui} — the 26.1 Mojang name
 * for what used to be {@code InGameHud#renderOverlay(DrawContext,
 * Identifier, float)} in yarn.
 */
@Mixin(Gui.class)
public abstract class InGameHudOverlayMixin {

    @Inject(method = "extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V",
            at = @At("HEAD"), cancellable = true)
    private void skyz$suppressOverlay(GuiGraphicsExtractor ctx, Identifier id, float opacity, CallbackInfo ci) {
        if (id == null) return;
        String path = id.getPath();
        if (SkyzClientState.noPumpkinBlur && path.contains("pumpkin")) {
            ci.cancel();
        } else if (SkyzClientState.noFireOverlay && path.contains("fire")) {
            ci.cancel();
        }
    }
}
