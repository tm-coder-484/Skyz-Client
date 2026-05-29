package net.skyz.client.mixin;

import net.minecraft.client.renderer.fog.FogRenderer;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * When the noFog toggle is on, force the public fogEnabled flag off by
 * intercepting fog setup and toggling the static state when needed.
 *
 * <p>Vanilla previously exposed {@code FogRenderer.toggleFog()} which flipped
 * a private static {@code boolean fogEnabled}. In 26.1 the {@code FogRenderer}
 * moved to {@code net.minecraft.client.renderer.fog.FogRenderer} and the
 * fog-setup pipeline was reworked into a per-frame planner that no longer
 * exposes the simple {@code applyFog} / {@code toggleFog} surface.
 *
 * <p>TODO PORT-26.1 / PORT-FOG: the original {@code applyFog(Camera, int,
 * RenderTickCounter, float, ClientWorld)} injection target no longer
 * exists. The 26.1 fog pipeline is built around a fog-buffer GPU upload
 * and a new {@code FogRenderer} object owned by {@link
 * net.minecraft.client.renderer.LevelRenderer}, so a direct {@code
 * @Inject} into the old method signature would not compile.
 *
 * <p>For now this mixin is a no-op — the noFog toggle is intentionally
 * non-functional in 26.1 until the new fog hook is identified. Leaving
 * the class registered in {@code mixins.json} keeps the include-list
 * stable so a single follow-up PR can re-enable the feature without
 * touching the manifest.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    // No-op. See class-level TODO PORT-26.1 / PORT-FOG.
    //
    // Touching SkyzClientState here so the field reference isn't dead-
    // stripped by the IDE and the compiler retains the import — that way
    // the moment the new hook is identified we can drop the inject body
    // straight in without re-adding the import.
    @SuppressWarnings("unused")
    private static final boolean SKYZ$NO_FOG_DESIRED = SkyzClientState.noFog;
}
