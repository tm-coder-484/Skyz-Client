package net.skyz.client.util;

import java.util.*;

/**
 * Shared HUD state. All elements are defined here and read by InGameHudMixin.
 * Positions are stored as pixel coords relative to the scaled window size.
 */
public final class SkyzHudState {
    private SkyzHudState() {}

    public static class HudElementState {
        public final String name, icon, category;
        public int x, y, w, h;
        public boolean enabled;

        public HudElementState(String name, String icon, String category,
                               int x, int y, int w, int h, boolean enabled) {
            this.name=name; this.icon=icon; this.category=category;
            this.x=x; this.y=y; this.w=w; this.h=h; this.enabled=enabled;
        }
    }

    public static final List<HudElementState> ELEMENTS = new ArrayList<>();
    public static boolean initialized = false;

    // Vanilla suppression flags
    public static boolean hideVanillaHealth = false;
    public static boolean hideVanillaHunger = false;
    public static boolean hideVanillaArmor  = false;

    public static void initDefaults(int W, int H) {
        if (initialized) return;
        initialized = true;
        ELEMENTS.clear();

        // \u2500\u2500 Info \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        add("FPS Counter",       "\uD83D\uDCCA", "info",    W/2+5,   2,   90,  20, true);
        add("CPS Counter",       "\uD83D\uDDB1", "info",    W/2-95,  2,   90,  20, false);
        add("Coordinates",       "\uD83D\uDCCC", "info",    4,       4,   150, 20, false);
        add("Biome Display",     "\uD83C\uDF3F", "info",    4,       28,  130, 20, false);
        add("Speed Meter",       "\u26A1",       "info",    W-94,    4,   90,  20, false);
        add("Ping Display",      "\uD83D\uDCE1", "info",    W-94,    28,  90,  20, false);
        add("Reach Display",     "\uD83C\uDFAF", "info",    W/2-45,  H-80,90,  20, false);
        add("Memory Usage",      "\uD83D\uDDA5", "info",    4,       52,  110, 20, false);
        add("Entity Count",      "\uD83D\uDC7E", "info",    4,       76,  110, 20, false);
        add("Session Timer",     "\u23F1",       "info",    W-94,    H-24,90,  20, false);
        add("Block Info",        "\uD83E\uDDF1", "info",    W/2-60,  H-52,120, 20, false);
        add("Compass",           "\uD83E\uDDED", "info",    W/2-60,  2,   120, 14, false);

        // \u2500\u2500 Bars \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        add("Health Bar",        "\u2764",       "bars",    4,       H-48,120, 16, false);
        add("Hunger Bar",        "\uD83C\uDF56", "bars",    W-124,   H-48,120, 16, false);
        add("Saturation Bar",    "\u2728",       "bars",    W-124,   H-68,120, 10, false);
        add("Armor Bar",         "\uD83D\uDEE1", "bars",    4,       H-68,120, 10, false);
        add("Tool Durability",   "\u26CF",       "bars",    4,       H-90,120, 16, false);
        add("Helmet Durability",    "\u26D1",       "bars",    4,      H-110,120, 14, false);
        add("Chestplate Durability", "\uD83E\uDDF2", "bars",    4,      H-128,120, 14, false);
        add("Leggings Durability",   "\uD83D\uDC56", "bars",    4,      H-146,120, 14, false);
        add("Boots Durability",      "\uD83D\uDC62", "bars",    4,      H-164,120, 14, false);
        add("Attack Cooldown",   "\u2694",       "bars",    W/2-50,  H-64,100, 10, false);

        // \u2500\u2500 Combat \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        add("Enemy Info",        "\uD83D\uDC80", "combat",  W-140,   H/2, 136, 64, false);
        add("Nearby Players",    "\uD83D\uDC65", "combat",  W-120,   H/3, 116, 80, false);
        add("TNT Timer",         "\uD83D\uDCA5", "combat",  W/2-55, H/3, 110, 20, false);
        add("Combo Counter",     "\u2694",        "combat",  W/2-45, H/4,  90, 28, false);
        add("Totem Pop Counter", "\uD83C\uDF3C", "combat",  4,      H/4, 120, 20, false);

        // \u2500\u2500 Effects \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        add("Minimap",          "\uD83D\uDDFA", "effects", W-110,   4,   106,106, false);
        add("Potion Effects",    "\u2697",       "effects", W-104,   4,   100, 80, false);
        add("Custom Crosshair",  "\u271A",       "effects", W/2-10,  H/2-10,20,20,false);

        // \u2500\u2500 Input \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
        add("Keystrokes",        "\u2328",       "input",   W-82,   H-120,78,  78, false);
    }

    private static void add(String name, String icon, String cat,
                            int x, int y, int w, int h, boolean on) {
        ELEMENTS.add(new HudElementState(name, icon, cat, x, y, w, h, on));
    }

    public static HudElementState find(String name) {
        for (HudElementState e : ELEMENTS) if (e.name.equals(name)) return e;
        return null;
    }
}
