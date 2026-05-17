package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.option.GameOptions;
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
 * {@code SimpleOption<T>.asWidget} machinery and recreating them would
 * risk breaking option persistence. {@link SkyzOptionsScreen} routes
 * directly to the same vanilla sub-screen constructors.
 */
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin {

    @Shadow @Final private Screen      parent;
    @Shadow @Final private GameOptions settings;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceOptions(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof SkyzOptionsScreen)) {
            client.setScreen(new SkyzOptionsScreen(this.parent, this.settings));
            ci.cancel();
        }
    }
}
