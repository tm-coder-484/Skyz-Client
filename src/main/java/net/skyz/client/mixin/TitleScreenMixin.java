package net.skyz.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.skyz.client.screen.SkyzTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla TitleScreen with SkyzTitleScreen.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceTitleScreen(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof SkyzTitleScreen)) {
            client.setScreen(new SkyzTitleScreen());
            ci.cancel();
        }
    }
}
