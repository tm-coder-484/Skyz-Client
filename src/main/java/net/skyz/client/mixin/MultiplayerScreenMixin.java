package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.skyz.client.screen.SkyzMultiplayerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link MultiplayerScreen} with {@link SkyzMultiplayerScreen}.
 *
 * <p>Catches the case where MC's disconnect path opens vanilla
 * {@code MultiplayerScreen} directly — e.g. when the user clicks
 * "Disconnect" on the pause menu, or hits "Back to title menu" on the
 * disconnected screen after being kicked from a server. Without this
 * mixin the user would land on vanilla MultiplayerScreen instead of our
 * Skyz-styled one.
 *
 * <p>Injection is at {@code init() RETURN}, NOT {@code HEAD}. An earlier
 * version did HEAD + {@code ci.cancel()} which crashed on disconnect:
 * SpruceUI (and possibly other mods) wrap the screen-tick call in
 * MinecraftClient.tick, and during the screen-swap window vanilla
 * {@code MultiplayerScreen.tick()} could still be reached with its
 * {@code serverListWidget} field still null because init had been
 * canceled before that field was created. Result: a crash like
 * {@code NullPointerException: ... because this.field_3046 is null} from
 * {@code MultiplayerScreen#tick} as soon as the disconnect transition
 * finished.
 *
 * <p>RETURN injection lets vanilla init run to completion — the server
 * list widget, pinger, and all related state are fully set up before we
 * swap to {@link SkyzMultiplayerScreen}. Even if a stale reference to
 * vanilla survives the swap and gets ticked, every field it touches is
 * initialised so it doesn't NPE.
 *
 * <p>Vanilla MultiplayerScreen's {@code parent} field (the screen the
 * user came from) is shadowed and passed through to
 * SkyzMultiplayerScreen's generic-parent constructor. If the parent is a
 * {@link net.skyz.client.screen.SkyzTitleScreen} the Skyz screen's
 * toast helper still works; otherwise toasts no-op.
 */
@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {

    @Shadow @Final private Screen parent;

    @Inject(method = "init", at = @At("RETURN"))
    private void skyz$replaceMultiplayer(CallbackInfo ci) {
        MultiplayerScreen self = (MultiplayerScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        // Only redirect when we're actually the active screen — guards
        // against init() being called for re-layout (window resize)
        // while we're not the current screen.
        if (client.currentScreen != self) return;
        if (!(client.currentScreen instanceof SkyzMultiplayerScreen)) {
            client.setScreen(new SkyzMultiplayerScreen(this.parent));
        }
    }
}
