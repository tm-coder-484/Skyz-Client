package net.skyz.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
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
 * <p>The vanilla {@code DisconnectedScreen} has 4 constructors in 26.1
 * for different combinations of {@code (parent, title, reason | DisconnectionDetails,
 * buttonLabel?)}. They all funnel into the same {@code details} field of type
 * {@link DisconnectionDetails} (a record carrying the reason + crash report path
 * + bug-report link). We don't need to redirect each constructor — just the
 * shared {@code init} method, where we shadow the relevant fields and pass
 * them through to our screen.
 *
 * <p>{@code title} comes from the {@code Screen} superclass and is exposed
 * via {@link Screen#getTitle()}.
 */
@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {

    @Shadow @Final private Screen               parent;
    @Shadow @Final private DisconnectionDetails details;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void skyz$replaceDisconnected(CallbackInfo ci) {
        DisconnectedScreen self = (DisconnectedScreen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof SkyzDisconnectedScreen)) {
            client.setScreen(new SkyzDisconnectedScreen(
                    this.parent,
                    self.getTitle(),
                    this.details != null ? this.details.reason() : null));
            ci.cancel();
        }
    }
}
