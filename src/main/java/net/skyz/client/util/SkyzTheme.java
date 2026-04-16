package net.skyz.client.util;

/**
 * Mutable theme singleton for Skyz Client.
 * All screens read background and accent colors from here.
 * Call SkyzTheme.apply(index) to switch themes; the change takes effect
 * immediately on the next render frame.
 */
public final class SkyzTheme {

    private SkyzTheme() {}

    public static final String[][] THEMES = {
        // name,      bg1,         bg2,         bg3,         accent1,     accent2
        {"Sky Blue",  "0xFF050F2A","0xFF0A1F50","0xFF0D2860","0xFF4AB8F0","0xFF8CD2FF"},
        {"Midnight",  "0xFF05050F","0xFF0A0520","0xFF15083A","0xFF8A64FF","0xFFB09AFF"},
        {"Aurora",    "0xFF051510","0xFF0A2A20","0xFF0D3A2A","0xFF4AF0B8","0xFF7AECD0"},
        {"Crimson",   "0xFF1A0308","0xFF380510","0xFF55081A","0xFFFF6060","0xFFFF9090"},
        {"Amber",     "0xFF190D02","0xFF301A05","0xFF4A2808","0xFFFFB030","0xFFFFD070"},
        {"Sakura",    "0xFF1A0518","0xFF320A30","0xFF4A1048","0xFFFF80C0","0xFFFFB0D8"},
    };

    private static int current = 0;

    // Live color values \u2014 read by all screens
    public static int BG1       = 0xFF050F2A;
    public static int BG2       = 0xFF0A1F50;
    public static int BG3       = 0xFF0D2860;
    public static int ACCENT1   = 0xFF4AB8F0;
    public static int ACCENT2   = 0xFF8CD2FF;
    // Derived
    public static int CARD_BG     = 0x72081C41;
    public static int CARD_BORDER = 0x248CD2FF;
    public static int BTN_BG      = 0x66143C6E;
    public static int BTN_BORDER  = 0x598CD2FF;
    public static int DIVIDER     = 0x1A8CD2FF;
    public static int ACCENT_DIM  = 0x4D8CD2FF;
    public static int ACCENT_MUTED= 0x338CD2FF;
    public static int GLOW        = 0x6664C8FF;

    public static int getCurrent() { return current; }

    public static void apply(int index) {
        if (index < 0 || index >= THEMES.length) return;
        current = index;
        String[] t = THEMES[index];

        BG1     = parseHex(t[1]);
        BG2     = parseHex(t[2]);
        BG3     = parseHex(t[3]);
        ACCENT1 = parseHex(t[4]);
        ACCENT2 = parseHex(t[5]);

        // Rebuild derived colors using ACCENT2 as hue
        int aR = (ACCENT2 >> 16) & 0xFF, aG = (ACCENT2 >> 8) & 0xFF, aB = ACCENT2 & 0xFF;
        CARD_BG      = blendBg(BG1, BG2, 0x72);
        CARD_BORDER  = withAlpha(ACCENT2, 0x24);
        BTN_BG       = withAlpha(mixColor(BG2, ACCENT2, 0.3f), 0x66);
        BTN_BORDER   = withAlpha(ACCENT2, 0x59);
        DIVIDER      = withAlpha(ACCENT2, 0x1A);
        ACCENT_DIM   = withAlpha(ACCENT2, 0x4D);
        ACCENT_MUTED = withAlpha(ACCENT2, 0x33);
        GLOW         = withAlpha(ACCENT1, 0x66);

        // Sync SkyzColors static fields so any screen that imported those directly still works
        SkyzColors.BG_DARK  = BG1;
        SkyzColors.BG_MID   = BG2;
        SkyzColors.BG_LIGHT = BG3;
    }

    private static int parseHex(String s) {
        return (int) Long.parseLong(s.substring(2), 16);
    }

    private static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    private static int blendBg(int a, int b, int alpha) {
        int r = ((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) / 2;
        int g = ((a >>  8 & 0xFF) + (b >>  8 & 0xFF)) / 2;
        int bl= ((a       & 0xFF) + (b        & 0xFF)) / 2;
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int mixColor(int a, int b, float t) {
        int aR=(a>>16&0xFF), aG=(a>>8&0xFF), aB=(a&0xFF);
        int bR=(b>>16&0xFF), bG=(b>>8&0xFF), bB=(b&0xFF);
        int r=(int)(aR+t*(bR-aR)), g=(int)(aG+t*(bG-aG)), bl=(int)(aB+t*(bB-aB));
        return 0xFF000000 | (r<<16) | (g<<8) | bl;
    }
}
