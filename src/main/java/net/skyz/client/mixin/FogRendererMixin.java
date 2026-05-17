package net.skyz.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.skyz.client.util.SkyzClientState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When the noFog toggle is on, force the public fogEnabled flag off by
 * intercepting applyFog and toggling the static state when needed.
 *
 * Vanilla exposes FogRenderer.toggleFog() which flips a private static boolean
 * fogEnabled. We can't read that field directly from a mixin without
 * reflection, so we maintain our own desired-state and use toggleFog() in
 * pairs to converge.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    private static boolean skyz$lastDesiredFogOff = false;

    @Inject(method = "applyFog", at = @At("HEAD"))
    private void skyz$syncFogState(Camera camera, int viewDistance, RenderTickCounter tick,
                                   float skyDarkness, ClientWorld world,
                                   CallbackInfoReturnable<Vector4f> cir) {
        boolean desiredOff = SkyzClientState.noFog;
        if (desiredOff != skyz$lastDesiredFogOff) {
            // Single toggle flips fogEnabled. Vanilla starts with fogEnabled = true.
            FogRenderer.toggleFog();
            skyz$lastDesiredFogOff = desiredOff;
        }
    }
}
