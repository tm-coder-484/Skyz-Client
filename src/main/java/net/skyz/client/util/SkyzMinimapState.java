package net.skyz.client.util;

/**
 * Shared state for the minimap renderer.
 * Lives outside the @Mixin class so it can have public static members.
 */
public final class SkyzMinimapState {
    private SkyzMinimapState() {}

    /** Set to true to force a terrain resample next render frame. */
    public static volatile boolean dirty = false;

    public static void invalidate() { dirty = true; }
}
