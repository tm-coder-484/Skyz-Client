package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
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

    @Shadow @Final private Text    message;
    @Shadow @Final private boolean isHardcore;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceDeathScreen(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof SkyzDeathScreen)) {
            client.setScreen(new SkyzDeathScreen(this.message, this.isHardcore));
            ci.cancel();
        }
    }
}
