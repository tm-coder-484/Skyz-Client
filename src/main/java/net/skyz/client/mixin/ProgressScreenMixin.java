package net.skyz.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import net.skyz.client.screen.SkyzProgressScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@link ProgressScreen} with {@link SkyzProgressScreen}.
 *
 * <p>{@code ProgressScreen} implements {@code ProgressListener} — the
 * caller (resource reload, world save, etc.) holds a reference to the
 * screen object and pushes {@code progressStartNoAbort / progressStage /
 * progressStagePercentage} onto it as the operation proceeds. We can't
 * easily redirect those listener calls to our screen without proxying,
 * so instead we let the <i>vanilla</i> screen keep receiving updates and
 * have our Skyz screen read its private fields each frame via the
 * {@code @Shadow}-derived {@link SkyzProgressScreen.Accessor}.
 *
 * <p>This is the same "two-screen" pattern Iris uses for its own progress
 * dialogue — the vanilla object stays alive as the listener target while
 * a styled UI sits on top of it.
 *
 * <p>Inject point is {@code progressStartNoAbort} (the 26.1 Mojang name
 * for what used to be {@code setTitle(Text)}) rather than {@code init} or
 * {@code render} because {@code ProgressScreen} doesn't override
 * {@code init} (it inherits the empty default from
 * {@link net.minecraft.client.gui.screens.Screen}). On the first
 * progress-callback we swap to the Skyz screen; subsequent callbacks the
 * worker keeps writing to the vanilla object's fields while
 * {@link SkyzProgressScreen} reads them via
 * {@link SkyzProgressScreen.Accessor}.
 *
 * <p>Field-name mapping (yarn → mojang):
 * <ul>
 *   <li>{@code title} → {@code header}</li>
 *   <li>{@code task} → {@code stage}</li>
 *   <li>{@code done} → {@code stop}</li>
 * </ul>
 */
@Mixin(ProgressScreen.class)
public class ProgressScreenMixin implements SkyzProgressScreen.Accessor {

    @Shadow private Component header;
    @Shadow private Component stage;
    @Shadow private int       progress;
    @Shadow private boolean   stop;

    @Override public Component title()    { return this.header; }
    @Override public Component task()     { return this.stage; }
    @Override public int       progress() { return this.progress; }
    @Override public boolean   done()     { return this.stop; }

    /**
     * Inject into {@code progressStartNoAbort} (the renamed
     * {@code setTitle(Component)} in 26.1) rather than {@code render} or
     * {@code init}.
     *
     * <p>{@link ProgressScreen} <i>doesn't override</i> {@code init()} or
     * {@code render()} — both are inherited from {@link
     * net.minecraft.client.gui.screens.Screen} with the default empty
     * implementations. Mixin can't inject into inherited bytecode that
     * doesn't actually exist on the target class, which was why the
     * earlier render-injected version never fired (and Mixin logged
     * "Cannot remap" warnings).
     *
     * <p>{@code progressStartNoAbort(Component)} <i>is</i> overridden on
     * ProgressScreen (it implements ProgressListener) and is called by
     * the operation almost immediately after the screen becomes the
     * active screen ("Saving world…", "Reloading resources…", etc.). We
     * don't cancel — letting the vanilla method run keeps the {@code
     * header} field updated, which {@link SkyzProgressScreen} reads via
     * {@link SkyzProgressScreen.Accessor}.
     */
    @Inject(method = "progressStartNoAbort", at = @At("HEAD"))
    private void skyz$replaceProgress(Component title, CallbackInfo ci) {
        ProgressScreen self = (ProgressScreen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client.screen == self
                && !(client.screen instanceof SkyzProgressScreen)) {
            client.setScreen(new SkyzProgressScreen(self, this));
        }
    }
}
