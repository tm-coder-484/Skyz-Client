package net.skyz.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplies the computed FOV by SkyzClientState.fovMultiplier so the user
 * slider in the HUD editor smoothly scales the view (0.5x – 2.0x).
 *
 * <p>In 26.1 {@code GameRenderer#getFov} returns {@code float} (it was
 * {@code double} pre-1.21.2). The {@code Camera} import moved from
 * {@code net.minecraft.client.render.Camera} to {@code
 * net.minecraft.client.Camera}.
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
