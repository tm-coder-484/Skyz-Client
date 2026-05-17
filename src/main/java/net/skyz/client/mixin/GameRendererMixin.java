package net.skyz.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplies the computed FOV by SkyzClientState.fovMultiplier so the user
 * slider in the HUD editor smoothly scales the view (0.5x – 2.0x).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void skyz$applyFovMultiplier(Camera camera, float tickDelta, boolean changingFov,
                                         CallbackInfoReturnable<Float> cir) {
        float mult = SkyzClientState.fovMultiplier;
        if (mult != 1.0f && mult > 0.05f) {
            cir.setReturnValue(cir.getReturnValueF() * mult);
        }
    }
}
