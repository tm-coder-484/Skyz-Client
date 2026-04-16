package net.skyz.client.util;

import net.minecraft.client.MinecraftClient;

/**
 * Central state for all built-in mod toggles.
 * tick() is called every game tick from SkyzClientMod's ClientTickEvents.
 */
public final class SkyzClientState {
    private SkyzClientState() {}

    // CPS tracking - ring buffer
    public static int cps = 0;
    private static final long[] CPS_CLICKS = new long[20];
    private static int cpsHead = 0;

    public static void recordClick() {
        CPS_CLICKS[cpsHead % 20] = System.currentTimeMillis();
        cpsHead++;
    }

    public static void updateCps() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long t : CPS_CLICKS) if (t > 0 && now - t < 1000) count++;
        cps = count;
    }

    // QoL Toggles
    public static boolean toggleSprint    = false;
    public static boolean toggleSneak     = false;
    public static boolean toggleChat      = true;
    public static boolean fullbright      = false;
    public static boolean noFog           = false;
    public static boolean noPumpkinBlur   = false;
    public static boolean antiAfk         = false;
    public static boolean autoGG          = false;

    // Visual toggles
    public static float   fovMultiplier   = 1.0f;
    public static boolean coloredHitboxes = false;
    public static boolean noFireOverlay   = false;

    // Anti-AFK state
    private static int  afkTick     = 0;
    private static boolean afkDir   = false;

    // Combo counter
    public static int  comboCount   = 0;
    public static long lastHitTime  = 0;
    private static final long COMBO_RESET_MS = 3500;

    // Totem pop counter
    public static int totemPops = 0;

    /**
     * Called every game tick from SkyzClientMod.
     */
    public static void tick(MinecraftClient client) {
        if (client.player == null) return;

        // Fullbright - override gamma every tick
        if (fullbright) {
            client.options.getGamma().setValue(100.0);
        }

        // Toggle sprint
        if (toggleSprint && client.options.forwardKey.isPressed()) {
            client.player.setSprinting(true);
        }

        // Toggle sneak - set sneaking state each tick
        if (toggleSneak) {
            client.player.setSneaking(true);
            client.player.input.sneaking = true;
        }

        // Anti-AFK - jump every 30 seconds
        if (antiAfk) {
            afkTick++;
            if (afkTick >= 600) {
                afkTick = 0;
                if (client.player.isOnGround()) client.player.jump();
            }
        }

        // Combo reset after 3.5s
        if (comboCount > 0 && System.currentTimeMillis() - lastHitTime > COMBO_RESET_MS) {
            comboCount = 0;
        }
    }

    /** Called when player lands a hit (detected by tracking attack key presses in game). */
    public static void registerHit() {
        comboCount++;
        lastHitTime = System.currentTimeMillis();
    }

    /** Called when player uses a totem of undying. */
    public static void registerTotemPop() {
        totemPops++;
    }
}
