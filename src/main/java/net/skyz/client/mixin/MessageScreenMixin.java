package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.skyz.client.screen.SkyzMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link MessageScreen} with {@link SkyzMessageScreen}.
 * Inherits the title from the {@link net.minecraft.client.gui.screen.Screen}
 * superclass via {@link MessageScreen#getTitle()} so server kick messages
 * and "Saving world…" text come through untouched.
 *
 * <p>Construction of {@link SkyzMessageScreen} is wrapped in try/catch
 * because owo-lib's {@code BaseUIModelScreen} ctor throws
 * "Couldn't find UI model file for skyz_client:loading_message" when the
 * UIModel registry hasn't been populated yet. Vanilla shows
 * {@link MessageScreen} very early in the launch sequence — before owo's
 * resource-reload listener has parsed our XML — so the very first swap
 * attempt at game start would crash. We swallow the failure and let
 * vanilla render its plain message screen instead; once resources finish
 * loading, every subsequent MessageScreen swap goes through cleanly.
 */
@Mixin(MessageScreen.class)
public class MessageScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceMessageScreen(CallbackInfo ci) {
        MessageScreen self = (MessageScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof SkyzMessageScreen) return;
        try {
            SkyzMessageScreen swap = new SkyzMessageScreen(self.getTitle());
            client.setScreen(swap);
            ci.cancel();
        } catch (Throwable ignored) {
            // owo UIModel not loaded yet (early launch). Fall through to
            // vanilla MessageScreen — no crash, no replacement this once.
        }
    }
}
