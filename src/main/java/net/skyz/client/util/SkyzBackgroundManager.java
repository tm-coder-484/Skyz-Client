package net.skyz.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
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

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static SkyzBackgroundManager INSTANCE;
    public static SkyzBackgroundManager getInstance() {
        if (INSTANCE == null) INSTANCE = new SkyzBackgroundManager();
        return INSTANCE;
    }

    private static final String SAVE_FILE = "skyz_client_bg.txt";
    // Static texture identifier - used with registerTexture()
    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath("skyz_client", "custom_background");

    private boolean hasTexture    = false;
    private boolean loadAttempted = false;
    private String  loadedPath    = null;
    private boolean dragging      = false;

    /** Identifier assigned by registerDynamicTexture. Starts null until first load. */
    private Identifier registeredId = null;

    private SkyzBackgroundManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

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
    public boolean draw(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        if (!hasTexture || registeredId == null) return false;
        // TODO(port): verify GuiGraphicsExtractor texture-blit signature in 26.1.2.
        // In Mojang mappings the equivalent of yarn's drawTexturedQuad is `blit`
        // with an overload that includes a RenderType/RenderPipeline argument; the
        // older 4-arg/uv overload may be gone. Likely:
        //   ctx.blit(RenderType.guiTextured(), registeredId, 0, 0, 0f, 0f, screenW, screenH, screenW, screenH);
        // Falling back to drawTexturedQuad name for now — likely a compile error.
        ctx.blit(registeredId, 0, 0, screenW, screenH, 0f, 1f, 0f, 1f);
        return true;
    }

    public void drawDim(GuiGraphicsExtractor ctx, int screenW, int screenH) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && registeredId != null) {
            mc.getTextureManager().release(registeredId);
            registeredId = null;
        }
        new File(SAVE_FILE).delete();
        SkyzClientMod.LOGGER.info("[Skyz Client] Background cleared.");
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void loadImage(File file) {
        if (!file.exists() || !file.isFile()) {
            SkyzClientMod.LOGGER.warn("[Skyz Client] Image not found: {}", file);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // Schedule onto render thread
        mc.execute(() -> {
            try (InputStream is = new FileInputStream(file)) {
                NativeImage img = NativeImage.read(is);

                // TODO(port): verify DynamicTexture (was NativeImageBackedTexture in yarn)
                // constructor signature for 26.1.2 — historically takes
                // (Supplier<String> labelSupplier, NativeImage image).
                final String fname = file.getName();
                DynamicTexture tex = new DynamicTexture(
                        () -> "skyz_client:background/" + fname, img);

                // Use a fixed Identifier; register(...) replaces any prior
                registeredId = TEXTURE_ID;
                mc.getTextureManager().register(registeredId, tex);

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
                if (mc.screen instanceof net.skyz.client.screen.SkyzTitleScreen ts) {
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
