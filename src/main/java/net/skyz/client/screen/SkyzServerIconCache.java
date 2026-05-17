package net.skyz.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.WorldIcon;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
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
 * {@link net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget}
 * uses the exact same equality short-circuit per entry.
 *
 * Thread safety: only ever called from the render thread (build() and
 * rebuild() in screens) — no synchronisation needed.
 */
public final class SkyzServerIconCache {

    private SkyzServerIconCache() {}

    private static final Map<String, WorldIcon> ICONS = new HashMap<>();
    private static final Map<String, byte[]>    BYTES = new HashMap<>();

    /**
     * Returns a {@link WorldIcon} for the given server, creating one on
     * first request and re-uploading the texture only when the favicon
     * bytes have changed. {@code WorldIcon.getTextureId()} falls back to
     * vanilla's {@code unknown_server.png} when no favicon has been
     * loaded — caller can use the returned icon's id unconditionally.
     */
    public static WorldIcon getOrUpload(ServerInfo info) {
        WorldIcon icon = ICONS.computeIfAbsent(info.address,
                addr -> WorldIcon.forServer(
                        MinecraftClient.getInstance().getTextureManager(), addr));

        byte[] current = info.getFavicon();
        byte[] cached  = BYTES.get(info.address);
        if (Arrays.equals(current, cached)) return icon;

        if (current == null) {
            try { icon.destroy(); } catch (Exception ignored) {}
        } else {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(current));
                icon.load(image); // takes ownership of `image`
            } catch (Exception e) {
                SkyzClientMod.LOGGER.warn(
                        "[Skyz] Favicon load failed for {}: {}",
                        info.address, e.getMessage());
            }
        }
        BYTES.put(info.address, current);
        return icon;
    }

    /**
     * Drop the entry for an address — call when the user removes a server
     * from the list so we don't leak the texture.
     */
    public static void invalidate(String address) {
        WorldIcon icon = ICONS.remove(address);
        BYTES.remove(address);
        if (icon != null) {
            try { icon.close(); } catch (Exception ignored) {}
        }
    }
}
