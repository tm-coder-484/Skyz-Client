package net.skyz.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.skyz.client.util.SkyzClientState;

/**
 * Draws 3-D ESP box outlines in world space — <b>26.1 port</b>.
 *
 * <p>Ported from the 1.21.11 {@code WorldRendererMixin}. 26.1 reworked the
 * world-render pipeline (retained {@code renderLevel}, {@code CameraRenderState},
 * {@code ShapeRenderer}, {@code RenderTypes.LINES}) and Fabric's
 * {@code fabric-rendering-v1} module is not shipped for 26.1, so the boxes are
 * now drawn from {@link net.skyz.client.mixin.LevelRendererMixin} which injects
 * at the tail of {@code LevelRenderer.renderLevel} and hands us a camera-relative
 * {@link PoseStack} plus the frame's buffer source.
 *
 * <p>Storage ESP walks the block-entity maps of nearby chunks each frame (gated
 * behind the toggle); player / mob ESP iterate live entities; block ESP reuses
 * the background scan thread's {@code blockEspMatches}.
 */
public final class SkyzEspRenderer {

    private SkyzEspRenderer() {}

    /**
     * @param pose    pose stack already transformed into camera space
     *                (view-rotation * translate(-cameraPos)), so world-coordinate
     *                shapes map straight onto screen.
     * @param buffers the frame's buffer source; we draw into {@code RenderTypes.LINES}
     *                and flush that batch ourselves.
     */
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers) {
        if (!SkyzClientState.storageEsp && !SkyzClientState.playerEsp
                && !SkyzClientState.mobEsp && !SkyzClientState.blockEsp) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        VertexConsumer lines = buffers.getBuffer(RenderTypes.LINES);
        boolean drew = false;

        int range = Math.max(4, Math.min(32, SkyzClientState.espRange));

        // ── Storage ESP — chests/barrels/hoppers/spawners/etc. ──
        if (SkyzClientState.storageEsp) {
            int pcx = client.player.chunkPosition().x();
            int pcz = client.player.chunkPosition().z();
            for (int cx = pcx - range; cx <= pcx + range; cx++) {
                for (int cz = pcz - range; cz <= pcz + range; cz++) {
                    LevelChunk chunk;
                    try { chunk = client.level.getChunkSource().getChunk(cx, cz, false); }
                    catch (Throwable t) { continue; }
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        int col = storageColour(be);
                        if (col == 0) continue;
                        drawBox(pose, lines, new AABB(be.getBlockPos()), col);
                        drew = true;
                    }
                }
            }
        }

        // ── Player ESP — health-tinted box (red→green) ──
        if (SkyzClientState.playerEsp) {
            float maxDist = range * 16f;
            for (Player p : client.level.players()) {
                if (p == client.player) continue;
                if (p.distanceTo(client.player) > maxDist) continue;
                float hp = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
                drawBox(pose, lines, p.getBoundingBox(), col(1f - hp, hp, 0.2f));
                drew = true;
            }
        }

        // ── Mob ESP — red hostile, green passive ──
        if (SkyzClientState.mobEsp) {
            float maxDist = range * 16f;
            for (Entity e : client.level.entitiesForRendering()) {
                if (!(e instanceof Mob mob)) continue;
                if (e.distanceTo(client.player) > maxDist) continue;
                boolean hostile = mob instanceof Enemy;
                drawBox(pose, lines, mob.getBoundingBox(),
                        col(hostile ? 1f : 0.2f, hostile ? 0.2f : 1f, 0.2f));
                drew = true;
            }
        }

        // ── Block ESP — from the background scan thread's precomputed matches ──
        if (SkyzClientState.blockEsp) {
            java.util.List<SkyzClientState.BlockEspMatch> matches = SkyzClientState.blockEspMatches;
            if (matches != null) {
                for (SkyzClientState.BlockEspMatch m : matches) {
                    drawBox(pose, lines, new AABB(m.pos()), 0xFF000000 | (m.color() & 0x00FFFFFF));
                    drew = true;
                }
            }
        }

        if (drew) buffers.endBatch(RenderTypes.LINES);
    }

    /** Draw an axis-aligned box outline at world coordinates in the given ARGB colour. */
    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB box, int argb) {
        VoxelShape shape = Shapes.create(box);
        ShapeRenderer.renderShape(pose, lines, shape, 0.0, 0.0, 0.0, argb, 1.0f);
    }

    /** Pack three 0..1 floats into an opaque ARGB int. */
    private static int col(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, Math.round(r * 255f)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255f)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255f)));
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    /** ARGB colour for a storage block entity, or 0 if it's not one we highlight. */
    private static int storageColour(BlockEntity be) {
        if (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity) return 0xFFFFAB00; // orange
        if (be instanceof BarrelBlockEntity)                                          return 0xFFCC8733; // tan
        if (be instanceof HopperBlockEntity)                                          return 0xFFABABAB; // grey
        if (be instanceof SpawnerBlockEntity)                                         return 0xFFFF4545; // red
        if (be instanceof ShulkerBoxBlockEntity)                                      return 0xFFAB45FF; // purple
        if (be instanceof FurnaceBlockEntity)                                         return 0xFFFF8745; // ember
        if (be instanceof DispenserBlockEntity)                                       return 0xFF888888; // slate
        return 0;
    }
}
