package net.skyz.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.skyz.client.screen.SkyzMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link GenericMessageScreen} with {@link SkyzMessageScreen}.
 * Inherits the title from the {@link net.minecraft.client.gui.screens.Screen}
 * superclass via {@link GenericMessageScreen#getTitle()} so server kick messages
 * and "Saving world…" text come through untouched.
 *
 * <p>Construction of {@link SkyzMessageScreen} is wrapped in try/catch
 * because owo-lib's {@code BaseUIModelScreen} ctor throws
 * "Couldn't find UI model file for skyz_client:loading_message" when the
 * UIModel registry hasn't been populated yet. Vanilla shows
 * {@link GenericMessageScreen} very early in the launch sequence — before owo's
 * resource-reload listener has parsed our XML — so the very first swap
 * attempt at game start would crash. We swallow the failure and let
 * vanilla render its plain message screen instead; once resources finish
 * loading, every subsequent GenericMessageScreen swap goes through cleanly.
 */
@Mixin(GenericMessageScreen.class)
public class MessageScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceMessageScreen(CallbackInfo ci) {
        GenericMessageScreen self = (GenericMessageScreen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof SkyzMessageScreen) return;
        try {
            SkyzMessageScreen swap = new SkyzMessageScreen(self.getTitle());
            client.setScreen(swap);
            ci.cancel();
        } catch (Throwable ignored) {
            // owo UIModel not loaded yet (early launch). Fall through to
            // vanilla GenericMessageScreen — no crash, no replacement this once.
        }
    }
}
