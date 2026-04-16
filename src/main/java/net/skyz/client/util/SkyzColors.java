package net.skyz.client.util;

/**
 * Central colour palette for Skyz Client, matching the HTML/CSS design.
 * All colours are packed ARGB integers as expected by Minecraft's DrawContext.
 */
public final class SkyzColors {

    private SkyzColors() {}

    // Primary brand colours
    public static final int SKY_BLUE       = 0xFF4AB8F0;
    public static final int SKY_LIGHT      = 0xFF88D4F8;
    public static final int SKY_GLOW       = 0x6664BEFF; // semi-transparent glow

    // Panel / background
    public static final int PANEL_BG       = 0x590A1E3C; // rgba(10,30,60,0.35)
    public static final int PANEL_BORDER   = 0x388CD2FF; // rgba(140,210,255,0.22)
    public static final int CARD_BG        = 0x72081C41; // rgba(8,28,65,0.45)
    public static final int CARD_BORDER    = 0x248CD2FF; // rgba(140,210,255,0.14)

    // Button colours
    public static final int BTN_BG         = 0x66143C6E; // rgba(20,60,110,0.40)
    public static final int BTN_HOVER      = 0x8C2864AA; // rgba(40,100,170,0.55)
    public static final int BTN_BORDER     = 0x598CD2FF; // rgba(140,210,255,0.35)

    // Text
    public static final int TEXT_PRIMARY   = 0xF2DCF0FF; // rgba(220,240,255,0.95)
    public static final int TEXT_MUTED     = 0x8CA0D2FF; // rgba(160,210,255,0.55)
    public static final int TEXT_WHITE     = 0xFFFFFFFF;

    // Background gradient stops (used for fill rectangles)
    public static int BG_DARK        = 0xFF050F2A;  // mutable \u2014 set by SkyzTheme
    public static int BG_MID         = 0xFF0A1F50;
    public static int BG_LIGHT       = 0xFF0D2860;

    // Status colours
    public static final int STATUS_GREEN   = 0xFF4CFA87;
    public static final int STATUS_YELLOW  = 0xFFFACC4C;
    public static final int STATUS_RED     = 0xFFFA6C4C;
    public static final int STATUS_BLUE    = 0xFF50D2FF;

    // Danger / quit button
    public static final int DANGER_BORDER  = 0x2EFF8C8C;
    public static final int DANGER_TEXT    = 0xBFFFB4B4;
    public static final int DANGER_HOVER   = 0x73781E28;

    /**
     * Linearly interpolates between two packed ARGB colours.
     * @param a  start colour
     * @param b  end colour
     * @param t  blend factor 0.0\u20131.0
     */
    public static int lerp(int a, int b, float t) {
        int aA = (a >> 24) & 0xFF, aR = (a >> 16) & 0xFF,
            aG = (a >>  8) & 0xFF, aB =  a        & 0xFF;
        int bA = (b >> 24) & 0xFF, bR = (b >> 16) & 0xFF,
            bG = (b >>  8) & 0xFF, bB =  b        & 0xFF;
        int rA = (int)(aA + (bA - aA) * t);
        int rR = (int)(aR + (bR - aR) * t);
        int rG = (int)(aG + (bG - aG) * t);
        int rB = (int)(aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }

    /** Returns the colour with alpha replaced. */
    public static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
