package net.skyz.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.skyz.client.SkyzClientMod;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;

/**
 * Drag-and-drop custom background image for Skyz Client.
 *
 * 1.21.11 API notes used here:
 *  - NativeImageBackedTexture(Supplier<String> nameSupplier, NativeImage image)
 *  - TextureManager.registerDynamicTexture(String prefix, NativeImageBackedTexture) -> Identifier
 *  - DrawContext.drawTexturedQuad(Identifier, int x1, int y1, int x2, int y2,
 *                                  float u1, float u2, float v1, float v2)
 *    (the public overload with no RenderPipeline)
 */
public class SkyzBackgroundManager {

    // \u2500\u2500 Singleton \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private static SkyzBackgroundManager INSTANCE;
    public static SkyzBackgroundManager getInstance() {
        if (INSTANCE == null) INSTANCE = new SkyzBackgroundManager();
        return INSTANCE;
    }

    private static final String SAVE_FILE = "skyz_client_bg.txt";
    // Static texture identifier - used with registerTexture()
    private static final net.minecraft.util.Identifier TEXTURE_ID =
            net.minecraft.util.Identifier.of("skyz_client", "custom_background");

    private boolean hasTexture    = false;
    private boolean loadAttempted = false;
    private String  loadedPath    = null;
    private boolean dragging      = false;

    /** Identifier assigned by registerDynamicTexture. Starts null until first load. */
    private Identifier registeredId = null;

    private SkyzBackgroundManager() {}

    // \u2500\u2500 Public API \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    public void tryLoadSaved() {
        if (loadAttempted) return;
        loadAttempted = true;
        File f = new File(SAVE_FILE);
        if (!f.exists()) return;
        try {
            String path = Files.readString(f.toPath()).trim();
            if (!path.isEmpty()) loadImage(new File(path));
        } catch (IOException e) {
            SkyzClientMod.LOGGER.warn("[Skyz Client] Could not read saved background: {}", e.getMessage());
        }
    }

    public boolean onFileDrop(List<Path> paths) {
        dragging = false;
        for (Path p : paths) {
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".png") || name.endsWith(".jpg")
                    || name.endsWith(".jpeg") || name.endsWith(".bmp")) {
                loadImage(p.toFile());
                return true;
            }
        }
        return false;
    }

    /**
     * Draw the background image stretched to fill the screen.
     * Uses drawTexturedQuad(Identifier, x1, y1, x2, y2, u1, u2, v1, v2)
     * which is the public overload with no RenderPipeline in 1.21.11.
     */
    public boolean draw(DrawContext ctx, int screenW, int screenH) {
        if (!hasTexture || registeredId == null) return false;
        // u1=0, u2=1, v1=0, v2=1 \u2192 full texture stretched across the screen
        ctx.drawTexturedQuad(registeredId, 0, 0, screenW, screenH, 0f, 1f, 0f, 1f);
        return true;
    }

    public void drawDim(DrawContext ctx, int screenW, int screenH) {
        ctx.fill(0, 0, screenW, screenH, 0xBB050F2A);
    }

    public void setDragging(boolean d) { this.dragging = d; }
    public boolean isDragging()         { return dragging; }
    public boolean hasBackground()      { return hasTexture; }
    public String  getLoadedPath()      { return loadedPath; }

    public void clear() {
        hasTexture = false;
        loadedPath = null;
        // Note: we leave registeredId alone - MC will GC the texture naturally
        // (registerDynamicTexture allocates a unique ID, destroying would need the id)
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && registeredId != null) {
            mc.getTextureManager().destroyTexture(registeredId);
            registeredId = null;
        }
        new File(SAVE_FILE).delete();
        SkyzClientMod.LOGGER.info("[Skyz Client] Background cleared.");
    }

    // \u2500\u2500 Internal \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private void loadImage(File file) {
        if (!file.exists() || !file.isFile()) {
            SkyzClientMod.LOGGER.warn("[Skyz Client] Image not found: {}", file);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // Schedule onto render thread
        mc.execute(() -> {
            try (InputStream is = new FileInputStream(file)) {
                NativeImage img = NativeImage.read(is);

                // 1.21.11: NativeImageBackedTexture(Supplier<String>, NativeImage)
                final String fname = file.getName();
                NativeImageBackedTexture tex = new NativeImageBackedTexture(
                        () -> "skyz_client:background/" + fname, img);

                // Use a fixed Identifier; registerTexture(Identifier, Texture) replaces any prior
                registeredId = TEXTURE_ID;
                mc.getTextureManager().registerTexture(registeredId, tex);

                hasTexture = true;
                loadedPath = file.getAbsolutePath();

                // Persist
                try {
                    Files.writeString(new File(SAVE_FILE).toPath(), loadedPath);
                } catch (IOException e) {
                    SkyzClientMod.LOGGER.warn("[Skyz Client] Could not save background path: {}", e.getMessage());
                }

                SkyzClientMod.LOGGER.info("[Skyz Client] Background set: {} (id={})",
                        file.getName(), registeredId);

                // Show toast
                if (mc.currentScreen instanceof net.skyz.client.screen.SkyzTitleScreen ts) {
                    ts.toast("Background set: " + file.getName());
                }

            } catch (IOException e) {
                SkyzClientMod.LOGGER.warn("[Skyz Client] Failed to load image '{}': {}",
                        file.getName(), e.getMessage());
                hasTexture = false;
            }
        });
    }
}
