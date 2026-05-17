package net.skyz.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.skyz.client.util.SkyzClientState;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders 3D ESP box outlines in world space.
 *
 * Confirmed from yarn-1.21.11+build.4 tiny mappings:
 *  - RenderLayers (class ijt) = net.minecraft.client.render.RenderLayers
 *    has static LINES field of type RenderLayer (not on RenderLayer class itself!)
 *  - RenderLayer.draw(BuiltBuffer) exists as instance method
 *  - RenderLayer.getDrawMode() / getVertexFormat() avoid hardcoding constants
 *  - BufferBuilder.endNullable() returns nullable BuiltBuffer
 *  - VertexConsumer has .normal() and .lineWidth() in 1.21.11
 *  - WorldRenderer.render() 9th param is GpuBufferSlice fogBuffer (not FogData)
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void skyz$renderEsp(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f basicProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci) {

        if (!SkyzClientState.storageEsp && !SkyzClientState.playerEsp
                && !SkyzClientState.mobEsp && !SkyzClientState.blockEsp)
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Vec3d camPos = camera.getCameraPos();
        Matrix4f camT = new Matrix4f().translation(
                (float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
        Matrix4f draw = new Matrix4f(positionMatrix).mul(camT);

        // RenderLayers.LINES is the correct 1.21.11 Yarn name.
        // getDrawMode()/getVertexFormat() ensure we always match what the layer expects.
        RenderLayer linesLayer = RenderLayers.LINES;
        BufferBuilder buf = Tessellator.getInstance().begin(
                linesLayer.getDrawMode(), linesLayer.getVertexFormat());

        boolean drew = false;

        if (SkyzClientState.storageEsp) {
            int px = (int) client.player.getX();
            int py = (int) client.player.getY();
            int pz = (int) client.player.getZ();
            int espR = Math.max(4, Math.min(32, SkyzClientState.espRange));
            int cubeR = espR * 16;
            java.util.Set<net.minecraft.block.entity.BlockEntity> seen = new java.util.HashSet<>();
            java.util.List<net.minecraft.block.entity.BlockEntity> list = new java.util.ArrayList<>();
            for (int cx = (px >> 4) - espR; cx <= (px >> 4) + espR; cx++) {
                for (int cz = (pz >> 4) - espR; cz <= (pz >> 4) + espR; cz++) {
                    net.minecraft.world.chunk.WorldChunk chunk =
                            client.world.getChunkManager().getWorldChunk(cx, cz, false);
                    if (chunk == null) continue;
                    for (var be : chunk.getBlockEntities().values())
                        if (seen.add(be)) list.add(be);
                }
            }
            try { for (var be : client.world.getBlockEntities()) if (seen.add(be)) list.add(be); }
            catch (Exception ignored) {}
            for (var be : list) {
                BlockPos bp = be.getPos();
                if (Math.abs(bp.getX()-px)>cubeR||Math.abs(bp.getY()-py)>cubeR||Math.abs(bp.getZ()-pz)>cubeR) continue;
                float r, g, b;
                if (be instanceof net.minecraft.block.entity.ChestBlockEntity
                        || be instanceof net.minecraft.block.entity.TrappedChestBlockEntity)
                    { r=1f; g=0.67f; b=0f; }
                else if (be instanceof net.minecraft.block.entity.BarrelBlockEntity)
                    { r=0.8f; g=0.53f; b=0.2f; }
                else if (be instanceof net.minecraft.block.entity.HopperBlockEntity)
                    { r=0.67f; g=0.67f; b=0.67f; }
                else if (be instanceof net.minecraft.block.entity.MobSpawnerBlockEntity)
                    { r=1f; g=0.27f; b=0.27f; }
                else if (be instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity)
                    { r=0.67f; g=0.27f; b=1f; }
                else if (be instanceof net.minecraft.block.entity.FurnaceBlockEntity)
                    { r=1f; g=0.53f; b=0.27f; }
                else if (be instanceof net.minecraft.block.entity.DispenserBlockEntity)
                    { r=0.53f; g=0.53f; b=0.53f; }
                else continue;
                drawBox(buf, draw, bp.getX(), bp.getY(), bp.getZ(),
                        bp.getX()+1, bp.getY()+1, bp.getZ()+1, r, g, b, 1f);
                drew = true;
            }
        }

        if (SkyzClientState.playerEsp) {
            float playerMaxDist = Math.max(4, Math.min(32, SkyzClientState.espRange)) * 16f;
            for (PlayerEntity p : client.world.getPlayers()) {
                if (p == client.player) continue;
                if (p.distanceTo(client.player) > playerMaxDist) continue;
                float hp = Math.max(0f, p.getHealth() / p.getMaxHealth());
                double hw = p.getWidth() / 2.0;
                drawBox(buf, draw,
                        p.getX()-hw, p.getY(), p.getZ()-hw,
                        p.getX()+hw, p.getY()+p.getHeight(), p.getZ()+hw,
                        1f-hp, hp, 0.2f, 1f);
                drew = true;
            }
        }

        if (SkyzClientState.mobEsp) {
            float mobMaxDist = Math.max(4, Math.min(32, SkyzClientState.espRange)) * 16f;
            for (net.minecraft.entity.Entity e : client.world.getEntities()) {
                if (!(e instanceof net.minecraft.entity.mob.MobEntity mob)) continue;
                if (e.distanceTo(client.player) > mobMaxDist) continue;
                boolean hostile = mob instanceof net.minecraft.entity.mob.HostileEntity
                        || mob instanceof net.minecraft.entity.mob.PhantomEntity;
                double hw = e.getWidth() / 2.0;
                drawBox(buf, draw,
                        e.getX()-hw, e.getY(), e.getZ()-hw,
                        e.getX()+hw, e.getY()+e.getHeight(), e.getZ()+hw,
                        hostile ? 1f : 0.2f, hostile ? 0.2f : 1f, 0.2f, 1f);
                drew = true;
            }
        }

        if (SkyzClientState.blockEsp) {
            java.util.List<SkyzClientState.BlockEspMatch> matches = SkyzClientState.blockEspMatches;
            if (matches != null && !matches.isEmpty()) {
                for (SkyzClientState.BlockEspMatch m : matches) {
                    BlockPos p = m.pos();
                    int col = m.color();
                    float r = ((col >> 16) & 0xFF) / 255f;
                    float g = ((col >>  8) & 0xFF) / 255f;
                    float b =  (col        & 0xFF) / 255f;
                    drawBox(buf, draw,
                            p.getX(),     p.getY(),     p.getZ(),
                            p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                            r, g, b, 1f);
                    drew = true;
                }
            }
        }

        if (drew) {
            try {
                BuiltBuffer built = buf.endNullable();
                if (built != null) {
                    // Disable depth test so ESP boxes render through walls
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    linesLayer.draw(built);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                }
            } catch (Exception ignored) {}
        } else {
            try { buf.endNullable(); } catch (Exception ignored) {}
        }
    }

    private static void drawBox(BufferBuilder buf, Matrix4f m,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        float ax=(float)x1, ay=(float)y1, az=(float)z1;
        float bx=(float)x2, by=(float)y2, bz=(float)z2;
        ln(buf,m,ax,ay,az,bx,ay,az,r,g,b,a); ln(buf,m,bx,ay,az,bx,ay,bz,r,g,b,a);
        ln(buf,m,bx,ay,bz,ax,ay,bz,r,g,b,a); ln(buf,m,ax,ay,bz,ax,ay,az,r,g,b,a);
        ln(buf,m,ax,by,az,bx,by,az,r,g,b,a); ln(buf,m,bx,by,az,bx,by,bz,r,g,b,a);
        ln(buf,m,bx,by,bz,ax,by,bz,r,g,b,a); ln(buf,m,ax,by,bz,ax,by,az,r,g,b,a);
        ln(buf,m,ax,ay,az,ax,by,az,r,g,b,a); ln(buf,m,bx,ay,az,bx,by,az,r,g,b,a);
        ln(buf,m,bx,ay,bz,bx,by,bz,r,g,b,a); ln(buf,m,ax,ay,bz,ax,by,bz,r,g,b,a);
    }

    // 1.21.11 lines format: POSITION + COLOR + NORMAL + LINE_WIDTH
    private static void ln(BufferBuilder buf, Matrix4f m,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float r, float g, float b, float a) {
        float dx = x2-x1, dy = y2-y1, dz = z2-z1;
        float len = Math.max(1e-6f, (float) Math.sqrt(dx*dx + dy*dy + dz*dz));
        float nx = dx/len, ny = dy/len, nz = dz/len;
        buf.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz).lineWidth(2.0f);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz).lineWidth(2.0f);
    }
}
