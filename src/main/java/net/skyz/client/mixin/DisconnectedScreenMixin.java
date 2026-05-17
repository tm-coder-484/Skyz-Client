package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.DisconnectionInfo;
import net.skyz.client.screen.SkyzDisconnectedScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link DisconnectedScreen} with {@link SkyzDisconnectedScreen}.
 *
 * <p>The vanilla {@code DisconnectedScreen} has 4 constructors in 1.21.11
 * for different combinations of {@code (parent, title, reason | DisconnectionInfo,
 * buttonLabel?)}. They all funnel into the same {@code info} field of type
 * {@link DisconnectionInfo} (a record carrying the reason + crash report path
 * + bug-report link). We don't need to redirect each constructor — just the
 * shared {@code init} method, where we shadow the relevant fields and pass
 * them through to our screen.
 *
 * <p>{@code title} comes from the {@code Screen} superclass and is exposed
 * via {@link Screen#getTitle()}.
 */
@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {

    @Shadow @Final private Screen            parent;
    @Shadow @Final private DisconnectionInfo info;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceDisconnected(CallbackInfo ci) {
        DisconnectedScreen self = (DisconnectedScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof SkyzDisconnectedScreen)) {
            client.setScreen(new SkyzDisconnectedScreen(
                    this.parent,
                    self.getTitle(),
                    this.info != null ? this.info.reason() : null));
            ci.cancel();
        }
    }
}
