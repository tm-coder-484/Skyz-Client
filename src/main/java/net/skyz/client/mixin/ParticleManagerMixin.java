package net.skyz.client.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When particleLimiter is on, drop a fraction of particles before they enter
 * the active list. Skips ~60% which keeps explosions/footsteps visible but
 * prevents particle storms (sand, lava) from tanking FPS.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true)
    private void skyz$dropParticles(Particle particle, CallbackInfo ci) {
        if (SkyzClientState.particleLimiter && Math.random() > 0.4) {
            ci.cancel();
        }
    }
}
