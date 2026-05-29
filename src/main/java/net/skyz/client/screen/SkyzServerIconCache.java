package net.skyz.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.ServerIconTexture;
import net.skyz.client.SkyzClientMod;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Process-lifetime cache of server-favicon textures. Survives screen
 * lifecycle so navigating away from and back to the Multiplayer screen
 * doesn't churn the GPU re-uploading 64×64 favicons we already have.
 *
 * Bytes-equality check before re-upload: if a server's favicon hasn't
 * changed since last upload, reuse the existing texture. Vanilla
 * {@code JoinMultiplayerScreen}'s server-list widget uses the exact same
 * equality short-circuit per entry.
 *
 * Thread safety: only ever called from the render thread (build() and
 * rebuild() in screens) — no synchronisation needed.
 */
public final class SkyzServerIconCache {

    // TODO(port-26.1): Verify exact Mojang names for ServerIconTexture
    // (forServer / upload / clearIcon) and ServerData (ip / getIconBytes)
    // against mcsrc.dev once available. The names below match the post-1.21
    // Mojang convention (renamed from yarn WorldIcon / ServerInfo).
    private SkyzServerIconCache() {}

    private static final Map<String, ServerIconTexture> ICONS = new HashMap<>();
    private static final Map<String, byte[]>    BYTES = new HashMap<>();

    /**
     * Returns a {@link ServerIconTexture} for the given server, creating one on
     * first request and re-uploading the texture only when the favicon
     * bytes have changed. {@code ServerIconTexture.getTextureLocation()} falls
     * back to vanilla's {@code unknown_server.png} when no favicon has been
     * loaded — caller can use the returned icon's id unconditionally.
     */
    public static ServerIconTexture getOrUpload(ServerData info) {
        ServerIconTexture icon = ICONS.computeIfAbsent(info.ip,
                addr -> ServerIconTexture.forServer(
                        Minecraft.getInstance().getTextureManager(), addr));

        byte[] current = info.getIconBytes();
        byte[] cached  = BYTES.get(info.ip);
        if (Arrays.equals(current, cached)) return icon;

        if (current == null) {
            try { icon.clearIcon(); } catch (Exception ignored) {}
        } else {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(current));
                icon.upload(image); // takes ownership of `image`
            } catch (Exception e) {
                SkyzClientMod.LOGGER.warn(
                        "[Skyz] Favicon load failed for {}: {}",
                        info.ip, e.getMessage());
            }
        }
        BYTES.put(info.ip, current);
        return icon;
    }

    /**
     * Drop the entry for an address — call when the user removes a server
     * from the list so we don't leak the texture.
     */
    public static void invalidate(String address) {
        ServerIconTexture icon = ICONS.remove(address);
        BYTES.remove(address);
        if (icon != null) {
            try { icon.close(); } catch (Exception ignored) {}
        }
    }
}
