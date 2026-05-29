package net.skyz.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.skyz.client.screen.SkyzDeathScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link DeathScreen} with {@link SkyzDeathScreen}.
 *
 * <p>Reads {@code message} (the cause-of-death text) and {@code isHardcore}
 * via {@code @Shadow} so we can pass them through to our screen — same
 * data vanilla uses to decide between Respawn and Game-Over labels.
 */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {

    @Shadow @Final private Component causeOfDeath;
    @Shadow @Final private boolean   hardcore;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceDeathScreen(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof SkyzDeathScreen)) {
            client.setScreen(new SkyzDeathScreen(this.causeOfDeath, this.hardcore));
            ci.cancel();
        }
    }
}
