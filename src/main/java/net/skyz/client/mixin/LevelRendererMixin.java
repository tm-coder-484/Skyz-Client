package net.skyz.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.skyz.client.render.SkyzEspRenderer;
import net.skyz.client.util.SkyzClientState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Skyz's 3-D ESP boxes in world space.
 *
 * <p>26.1 replaced the immediate-mode {@code WorldRenderer.render(...)} (the
 * 1.21.11 hook) with retained {@code LevelRenderer.renderLevel(...)} taking a
 * {@link CameraRenderState} and a position {@link Matrix4fc}. We inject at the
 * TAIL, rebuild a camera-relative {@link PoseStack} (position-matrix ×
 * translate(-cameraPos)) and let {@link SkyzEspRenderer} draw the outlines into
 * the frame's buffer source — the same approach vanilla debug renderers use.
 *
 * <p>This replaces Fabric's {@code WorldRenderEvents.LAST} (the
 * {@code fabric-rendering-v1} module is not shipped for 26.1).
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void skyz$renderEsp(GraphicsResourceAllocator allocator,
                                DeltaTracker deltaTracker,
                                boolean renderBlockOutline,
                                CameraRenderState camera,
                                Matrix4fc positionMatrix,
                                GpuBufferSlice fogBuffer,
                                Vector4f fogColor,
                                boolean renderSky,
                                ChunkSectionsToRender chunkSections,
                                CallbackInfo ci) {

        if (!SkyzClientState.storageEsp && !SkyzClientState.playerEsp
                && !SkyzClientState.mobEsp && !SkyzClientState.blockEsp) return;

        Vec3 camPos = camera.pos;
        if (camPos == null) return;

        // Reconstruct the world→view transform: the position matrix carries the
        // camera rotation/projection basis; translating by -cameraPos puts world
        // coordinates into camera space so absolute-coordinate shapes line up.
        PoseStack pose = new PoseStack();
        pose.mulPose(positionMatrix);
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();

        SkyzEspRenderer.render(pose, buffers);
    }
}
