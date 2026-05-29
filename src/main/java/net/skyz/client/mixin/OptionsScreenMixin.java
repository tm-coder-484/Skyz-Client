package net.skyz.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.skyz.client.screen.SkyzOptionsScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla top-level {@link OptionsScreen} with {@link SkyzOptionsScreen}.
 *
 * <p>Only the top-level (routing) Options screen is replaced. The leaf
 * sub-screens (Video, Controls, etc.) stay vanilla — they're tied to
 * {@code OptionInstance<T>.createButton} machinery and recreating them would
 * risk breaking option persistence. {@link SkyzOptionsScreen} routes
 * directly to the same vanilla sub-screen constructors.
 */
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin {

    @Shadow @Final private Screen  lastScreen;
    @Shadow @Final private Options options;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceOptions(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof SkyzOptionsScreen)) {
            client.setScreen(new SkyzOptionsScreen(this.lastScreen, this.options));
            ci.cancel();
        }
    }
}
