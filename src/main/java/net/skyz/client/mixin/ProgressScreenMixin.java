package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.text.Text;
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
 * screen object and pushes {@code setTitle / setTask / progressStagePercentage}
 * onto it as the operation proceeds. We can't easily redirect those
 * listener calls to our screen without proxying, so instead we let the
 * <i>vanilla</i> screen keep receiving updates and have our Skyz screen
 * read its private fields each frame via the {@code @Shadow}-derived
 * {@link SkyzProgressScreen.Accessor}.
 *
 * <p>This is the same "two-screen" pattern Iris uses for its own progress
 * dialogue — the vanilla object stays alive as the listener target while
 * a styled UI sits on top of it.
 *
 * <p>Inject point is {@code render} rather than {@code init} because
 * {@code ProgressScreen} doesn't override {@code init} (it inherits the
 * empty default from {@link net.minecraft.client.gui.screen.Screen}).
 * On the very first render frame we swap to the Skyz screen and cancel
 * the vanilla render path; subsequent frames the worker keeps writing
 * to the vanilla object's fields while {@link SkyzProgressScreen} reads
 * them via {@link SkyzProgressScreen.Accessor}.
 */
@Mixin(ProgressScreen.class)
public class ProgressScreenMixin implements SkyzProgressScreen.Accessor {

    @Shadow private Text    title;
    @Shadow private Text    task;
    @Shadow private int     progress;
    @Shadow private boolean done;

    @Override public Text    title()    { return this.title; }
    @Override public Text    task()     { return this.task; }
    @Override public int     progress() { return this.progress; }
    @Override public boolean done()     { return this.done; }

    /**
     * Inject into {@code setTitle} rather than {@code render} or {@code init}.
     *
     * <p>{@link ProgressScreen} <i>doesn't override</i> {@code init()} or
     * {@code render()} — both are inherited from {@link
     * net.minecraft.client.gui.screen.Screen} with the default empty
     * implementations. Mixin can't inject into inherited bytecode that
     * doesn't actually exist on the target class, which was why the
     * earlier render-injected version never fired (and Mixin logged
     * "Cannot remap" warnings).
     *
     * <p>{@code setTitle(Text)} <i>is</i> overridden on ProgressScreen and
     * is called by the operation almost immediately after the screen
     * becomes the active screen ("Saving world…", "Reloading resources…",
     * etc.). We don't cancel — letting the vanilla method run keeps the
     * {@code title} field updated, which {@link SkyzProgressScreen} reads
     * via {@link SkyzProgressScreen.Accessor}.
     */
    @Inject(method = "setTitle", at = @At("HEAD"))
    private void skyz$replaceProgress(net.minecraft.text.Text title, CallbackInfo ci) {
        ProgressScreen self = (ProgressScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == self
                && !(client.currentScreen instanceof SkyzProgressScreen)) {
            client.setScreen(new SkyzProgressScreen(self, this));
        }
    }
}
