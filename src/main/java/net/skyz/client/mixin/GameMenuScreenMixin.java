package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.skyz.client.screen.SkyzPauseMenuScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link GameMenuScreen} with {@link SkyzPauseMenuScreen}.
 *
 * <p>Only redirects when {@code showMenu == true}. Vanilla constructs
 * {@code GameMenuScreen(false)} during world load to pause the game
 * without showing UI; intercepting that case would surface a Skyz panel
 * on top of the loading screen, which isn't what we want.
 *
 * <p>Same pattern as {@link TitleScreenMixin}: inject at HEAD of
 * {@code init}, swap the active screen, cancel.
 */
@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin {

    @Shadow @Final private boolean showMenu;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replacePauseMenu(CallbackInfo ci) {
        if (!this.showMenu) return;  // World-load case — leave vanilla alone.

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof SkyzPauseMenuScreen)) {
            client.setScreen(new SkyzPauseMenuScreen(true));
            ci.cancel();
        }
    }
}
