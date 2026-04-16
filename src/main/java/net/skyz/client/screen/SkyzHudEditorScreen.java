package net.skyz.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.skyz.client.util.*;

/**
 * HUD Editor \u2014 two tabs inside a collapsible sidebar:
 *   Tab 0: HUD Elements  (drag-to-place, enable/disable, scrollable)
 *   Tab 1: Built-in Mods (toggles/sliders, scrollable)
 *
 * The sidebar can be toggled open/closed with the [ ] button so the full
 * screen preview area is accessible for placing elements on the right side.
 */
public class SkyzHudEditorScreen extends Screen {

    private final SkyzTitleScreen parent;
    private java.util.List<SkyzHudState.HudElementState> elements;

    // Dragging
    private SkyzHudState.HudElementState selected = null;
    private SkyzHudState.HudElementState dragging  = null;
    private int dragOffX, dragOffY;

    // Layout
    private static final int SIDEBAR_W = 196;
    private static final int PAD       = 8;
    private boolean sidebarOpen = true;

    // Snap grid \u2014 cycle through sizes with a button
    private static final int[] GRIDS = {4, 8, 16, 32};
    private int gridIdx = 1; // default = 8
    private boolean snapEnabled = true;
    private int snapGrid() { return GRIDS[gridIdx]; }

    // Tabs
    private int activeTab  = 0;
    private int hudScroll  = 0;
    private int modsScroll = 0;

    private static final String[] TABS = {"HUD Elements", "Built-in Mods"};
    private static final int EL_ROW  = 22;
    private static final int MOD_ROW = 42;

    private static final String[][] MODS = {
        {"Toggle Sprint",    "Auto-sprint when moving forward",    "toggleSprint",    "toggle"},
        {"Toggle Sneak",     "Hold sneak without holding key",      "toggleSneak",     "toggle"},
        {"Fullbright",       "Max gamma - see in the dark",         "fullbright",      "toggle"},
        {"No Fog",           "Remove fog distance",                 "noFog",           "toggle"},
        {"No Pumpkin Blur",  "Remove pumpkin head overlay",         "noPumpkinBlur",   "toggle"},
        {"Anti-AFK",         "Micro-movement vs AFK kicks",         "antiAfk",         "toggle"},
        {"Auto GG",          "Type 'gg' after game ends",           "autoGG",          "toggle"},
        {"No Fire Overlay",  "Remove fire screen effect",           "noFireOverlay",   "toggle"},
        {"Colored Hitboxes", "Show entity hitboxes with color",     "coloredHitboxes", "toggle"},
        {"Toggle Chat",      "Toggle chat visibility",              "toggleChat",      "toggle"},
        {"FOV Changer",      "Adjust FOV 0.5\u00d7\u20132.0\u00d7", "fovMultiplier",  "slider"},
    };

    public SkyzHudEditorScreen(SkyzTitleScreen parent) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!SkyzHudState.initialized)
            SkyzHudState.initDefaults(width, height);
        elements = SkyzHudState.ELEMENTS;

        addDrawableChild(SkyzButton.of(width - 70, 4, 64, 18, "\u2190 Back",
                () -> client.setScreen(parent)));
    }

    // \u2500\u2500 Sidebar geometry \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    /** X coordinate where the sidebar starts. */
    private int sidebarX() { return sidebarOpen ? width - SIDEBAR_W - PAD : width - 26; }
    /** Width of the usable preview area (full screen when sidebar closed). */
    private int previewW()  { return sidebarOpen ? width - SIDEBAR_W - PAD : width; }

    // \u2500\u2500 Render \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Simulated game background (sky + ground)
        ctx.fill(0, 0, width, height, 0xFF1A2A3A);
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, previewW(), (int)(height*.55f), 0xFF4A7FC0, 0xFF6AA0D8);
        SkyzRenderHelper.fillGradientV(ctx, 0, (int)(height*.55f), previewW(), height, 0xFF5A7A45, 0xFF3D5A2A);
        ctx.drawCenteredTextWithShadow(textRenderer, "Drag elements \u2022 Enable in sidebar \u2022 Toggle sidebar with [\u25C4]",
                previewW()/2, height/2-4, 0x22FFFFFF);

        // Snap grid overlay while dragging
        if (dragging != null && snapEnabled) {
            int g = snapGrid();
            for (int gx=0; gx<previewW(); gx+=g) ctx.fill(gx,0,gx+1,height,0x07FFFFFF);
            for (int gy=0; gy<height; gy+=g)       ctx.fill(0,gy,previewW(),gy+1,0x07FFFFFF);
        }

        // Update dragged position
        if (dragging != null) {
            int g = snapEnabled ? snapGrid() : 1;
            int nx = Math.max(0, Math.min(previewW()-dragging.w, mouseX-dragOffX));
            int ny = Math.max(0, Math.min(height-dragging.h,     mouseY-dragOffY));
            dragging.x = Math.round((float)nx/g)*g;
            dragging.y = Math.round((float)ny/g)*g;
        }

        // HUD element previews (all, not just enabled, so you can grab disabled ones)
        for (var el : elements) drawElPreview(ctx, el, mouseX, mouseY);

        // Sidebar toggle button (always visible)
        int bx = sidebarX() - 2;
        ctx.fill(bx, height/2-14, bx+20, height/2+14, 0xCC071830);
        ctx.fill(bx, height/2-14, bx+1, height/2+14, 0x558CD2FF);
        ctx.drawCenteredTextWithShadow(textRenderer, sidebarOpen ? "\u25BA" : "\u25C4",
                bx+10, height/2-4, 0xFF8CD2FF);

        if (sidebarOpen) drawSidebar(ctx, mouseX, mouseY);

        // Bottom bar (always visible)
        ctx.fill(0, height-26, width, height, 0xCC071830);
        ctx.fill(0, height-26, width, height-25, 0x338CD2FF);
        // Snap info
        ctx.drawTextWithShadow(textRenderer,
                "Snap: "+(snapEnabled ? snapGrid()+"px" : "OFF"),
                6, height-17, snapEnabled ? 0xFF8CD2FF : SkyzColors.TEXT_MUTED);
        // Cycle grid button
        int cgx = 80;
        ctx.fill(cgx, height-24, cgx+60, height-3, 0x441864A0);
        ctx.drawCenteredTextWithShadow(textRenderer, "Grid: "+snapGrid()+"px", cgx+30, height-17, SkyzColors.TEXT_PRIMARY);
        // Snap toggle
        ctx.fill(cgx+66, height-24, cgx+116, height-3, snapEnabled?0x441864A0:0x33143254);
        ctx.drawCenteredTextWithShadow(textRenderer, snapEnabled?"Snap ON":"Snap OFF", cgx+91, height-17,
                snapEnabled?SkyzColors.TEXT_PRIMARY:SkyzColors.TEXT_MUTED);
        // Save button
        ctx.fill(width-130, height-24, width-74, height-3, 0x441864A0);
        ctx.drawCenteredTextWithShadow(textRenderer, "Save \u2713", width-102, height-17, 0xFF4CFA87);
        // Reset button
        ctx.fill(width-70, height-24, width-4, height-3, 0x44440000);
        ctx.drawCenteredTextWithShadow(textRenderer, "Reset", width-37, height-17, 0xFFFA6C4C);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawElPreview(DrawContext ctx, SkyzHudState.HudElementState el, int mx, int my) {
        boolean hov = mx>=el.x&&mx<=el.x+el.w&&my>=el.y&&my<=el.y+el.h;
        boolean sel = el==selected, drag = el==dragging;
        // Disabled elements are INVISIBLE unless hovered, selected, or dragging
        if (!el.enabled && !hov && !sel && !drag) return;
        int bg  = drag?0xCC1A5A8A:sel?0x993C8ABE:!el.enabled?0x44143C6E:0x88000000;
        int brd = drag||sel?0xFF8CD2FF:!el.enabled?0x44446688:0x448CD2FF;
        ctx.fill(el.x,el.y,el.x+el.w,el.y+el.h,bg);
        ctx.fill(el.x,el.y,el.x+el.w,el.y+1,brd);
        ctx.fill(el.x,el.y+el.h-1,el.x+el.w,el.y+el.h,brd);
        ctx.fill(el.x,el.y,el.x+1,el.y+el.h,brd);
        ctx.fill(el.x+el.w-1,el.y,el.x+el.w,el.y+el.h,brd);
        ctx.drawTextWithShadow(textRenderer, el.icon, el.x+3, el.y+(el.h-8)/2, el.enabled?0xFFFFFFFF:0x88FFFFFF);
        String lbl=el.name;
        while (textRenderer.getWidth(lbl)>el.w-18&&lbl.length()>3) lbl=lbl.substring(0,lbl.length()-3)+"..";
        ctx.drawTextWithShadow(textRenderer, lbl, el.x+16, el.y+(el.h-8)/2, el.enabled?0xCCDDFFFF:0x55DDFFFF);
    }

    // \u2500\u2500 Sidebar \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private void drawSidebar(DrawContext ctx, int mx, int my) {
        int sx = sidebarX();
        int bgCol = 0xDD000000 | (SkyzTheme.BG1 & 0x00FFFFFF);
        ctx.fill(sx, 0, sx+SIDEBAR_W+PAD, height-26, bgCol);
        ctx.fill(sx, 0, sx+1, height-26, 0x558CD2FF);

        // Tabs
        int tw = (SIDEBAR_W-2)/2;
        for (int i=0;i<TABS.length;i++){
            int tx=sx+2+i*(tw+1);
            boolean active=i==activeTab;
            ctx.fill(tx,2,tx+tw,20,active?SkyzTheme.BTN_BG:0x33091E46);
            ctx.fill(tx,active?20:19,tx+tw,20,active?0xFF8CD2FF:0x338CD2FF);
            ctx.drawCenteredTextWithShadow(textRenderer,TABS[i],tx+tw/2,8,
                    active?0xFFFFFFFF:SkyzColors.TEXT_MUTED);
        }
        ctx.fill(sx+1,20,sx+SIDEBAR_W+PAD-1,21,0x558CD2FF);

        if (activeTab==0) drawHudTab(ctx,sx,mx,my);
        else              drawModsTab(ctx,sx,mx,my);
    }

    private void drawHudTab(DrawContext ctx, int sx, int mx, int my) {
        int listTop=22, listBot=height-30;
        int listH=listBot-listTop;
        // Count total rows including category headers
        int totalRows = 0;
        String lastCat = "";
        for (var el : elements) {
            if (!el.category.equals(lastCat)) { lastCat=el.category; totalRows+=14; }
            totalRows+=EL_ROW;
        }
        totalRows += 24+60; // snap + vanilla section

        hudScroll = Math.max(0, Math.min(Math.max(0,totalRows-listH), hudScroll));
        ctx.enableScissor(sx, listTop, sx+SIDEBAR_W+PAD, listBot);

        int ey=listTop-hudScroll;
        lastCat="";
        for (var el : elements) {
            if (!el.category.equals(lastCat)) {
                lastCat=el.category;
                String hdr=switch(lastCat){case "bars"->"BARS";case "effects"->"EFFECTS";case "input"->"INPUT";case "combat"->"COMBAT";default->"INFO";};
                if (ey>=listTop-14&&ey<listBot){
                    ctx.fill(sx+2,ey+1,sx+SIDEBAR_W+PAD-2,ey+13,0x11FFFFFF);
                    ctx.drawTextWithShadow(textRenderer,hdr,sx+6,ey+3,SkyzTheme.ACCENT_DIM);
                }
                ey+=14;
            }
            if (ey>=listTop-EL_ROW&&ey<listBot) {
                boolean hov=mx>=sx+2&&mx<=sx+SIDEBAR_W+PAD-2&&my>=ey&&my<=ey+EL_ROW;
                if (hov) ctx.fill(sx+2,ey,sx+SIDEBAR_W+PAD-2,ey+EL_ROW,0x22143C6E);
                drawPill(ctx,sx+6,ey+4,el.enabled);
                ctx.drawTextWithShadow(textRenderer,el.icon,sx+40,ey+7,0xFFFFFFFF);
                ctx.drawTextWithShadow(textRenderer,el.name,sx+54,ey+7,
                        el.enabled?SkyzColors.TEXT_PRIMARY:SkyzColors.TEXT_MUTED);
            }
            ey+=EL_ROW;
        }

        // Snap toggle
        ey+=6;
        if (ey>=listTop-22&&ey<listBot) {
            ctx.fill(sx+1,ey,sx+SIDEBAR_W+PAD-1,ey+1,0x228CD2FF); ey+=6;
            ctx.drawTextWithShadow(textRenderer,"Snap to grid",sx+6,ey+5,SkyzColors.TEXT_MUTED);
            drawPill(ctx,sx+SIDEBAR_W-26,ey+2,snapEnabled); ey+=22;
        }

        // Vanilla suppress
        if (ey>=listTop-80&&ey<listBot) {
            ctx.fill(sx+1,ey,sx+SIDEBAR_W+PAD-1,ey+1,0x228CD2FF); ey+=6;
            ctx.drawTextWithShadow(textRenderer,"Replace vanilla:",sx+6,ey,0x558CD2FF); ey+=12;
            drawVTog(ctx,sx,ey,"Health", SkyzHudState.hideVanillaHealth); ey+=18;
            drawVTog(ctx,sx,ey,"Hunger", SkyzHudState.hideVanillaHunger); ey+=18;
            drawVTog(ctx,sx,ey,"Armor",  SkyzHudState.hideVanillaArmor);
        }

        ctx.disableScissor();

        // Scrollbar
        if (totalRows>listH) {
            int th=Math.max(14,listH*listH/totalRows);
            int ty=listTop+hudScroll*(listH-th)/Math.max(1,totalRows-listH);
            ctx.fill(sx+SIDEBAR_W+PAD-4,listTop,sx+SIDEBAR_W+PAD-1,listBot,0x1A8CD2FF);
            ctx.fill(sx+SIDEBAR_W+PAD-4,ty,sx+SIDEBAR_W+PAD-1,ty+th,0x558CD2FF);
        }

        // Selected info
        if (selected!=null) {
            int iy=listBot+2;
            ctx.fill(sx+2,iy,sx+SIDEBAR_W+PAD-2,iy+22,0x88071830);
            ctx.drawTextWithShadow(textRenderer,selected.name,sx+6,iy+3,0xCC8CD2FF);
            ctx.drawTextWithShadow(textRenderer,"X:"+selected.x+" Y:"+selected.y,sx+6,iy+13,SkyzColors.TEXT_MUTED);
        }
    }

    private void drawModsTab(DrawContext ctx, int sx, int mx, int my) {
        int clipTop=22, clipBot=height-30;
        int visH=clipBot-clipTop, totalH=MODS.length*MOD_ROW;
        modsScroll=Math.max(0,Math.min(Math.max(0,totalH-visH),modsScroll));
        ctx.enableScissor(sx,clipTop,sx+SIDEBAR_W+PAD,clipBot);
        int ey=clipTop-modsScroll;
        for (String[] mod : MODS) {
            if (ey+MOD_ROW<clipTop){ey+=MOD_ROW;continue;}
            if (ey>clipBot) break;
            boolean on=getToggle(mod[2]);
            boolean hov=mx>=sx+2&&mx<=sx+SIDEBAR_W+PAD-2&&my>=ey&&my<=ey+MOD_ROW-2;
            ctx.fill(sx+2,ey,sx+SIDEBAR_W+PAD-2,ey+MOD_ROW-2,hov?0x33143C6E:0x11091E46);
            ctx.drawTextWithShadow(textRenderer,mod[0],sx+8,ey+4,on?SkyzColors.TEXT_PRIMARY:SkyzColors.TEXT_MUTED);
            ctx.drawTextWithShadow(textRenderer,mod[1],sx+8,ey+15,0x448CD2FF);
            if (mod[3].equals("toggle")) {
                drawPill(ctx,sx+SIDEBAR_W+PAD-38,ey+12,on);
            } else {
                float val=SkyzClientState.fovMultiplier;
                int sX=sx+8,sW=SIDEBAR_W-50;
                ctx.fill(sX,ey+29,sX+sW,ey+32,0x338CD2FF);
                ctx.fill(sX,ey+29,sX+(int)((val-0.5f)/1.5f*sW),ey+32,0x998CD2FF);
                // Knob
                int kx=sX+(int)((val-0.5f)/1.5f*sW)-3;
                ctx.fill(kx,ey+26,kx+6,ey+35,0xFF8CD2FF);
                ctx.drawTextWithShadow(textRenderer,String.format("%.1fx",val),
                        sx+SIDEBAR_W-24,ey+26,SkyzColors.TEXT_MUTED);
            }
            ey+=MOD_ROW;
        }
        ctx.disableScissor();
        if (totalH>visH){
            int th=Math.max(14,visH*visH/totalH);
            int ty=clipTop+modsScroll*(visH-th)/Math.max(1,totalH-visH);
            ctx.fill(sx+SIDEBAR_W+PAD-4,clipTop,sx+SIDEBAR_W+PAD-1,clipBot,0x1A8CD2FF);
            ctx.fill(sx+SIDEBAR_W+PAD-4,ty,sx+SIDEBAR_W+PAD-1,ty+th,0x558CD2FF);
        }
    }

    private void drawPill(DrawContext ctx, int x, int y, boolean on) {
        ctx.fill(x,y,x+30,y+14,on?0x551864A0:0x33143254);
        ctx.fill(x,y,x+30,y+1,on?0x5564BEFF:0x2A8CD2FF);
        ctx.fill(on?x+18:x+2,y+3,(on?x+26:x+10),y+11,on?0xCC64C8FF:0x7890AACC);
    }

    private void drawVTog(DrawContext ctx, int sx, int y, String lbl, boolean on) {
        drawPill(ctx,sx+6,y,on);
        ctx.drawTextWithShadow(textRenderer,"Hide vanilla "+lbl,sx+38,y+3,
                on?SkyzColors.TEXT_PRIMARY:SkyzColors.TEXT_MUTED);
    }

    // \u2500\u2500 State \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private boolean getToggle(String f){return switch(f){
        case "toggleSprint"->SkyzClientState.toggleSprint;case "toggleSneak"->SkyzClientState.toggleSneak;
        case "fullbright"->SkyzClientState.fullbright;case "noFog"->SkyzClientState.noFog;
        case "noPumpkinBlur"->SkyzClientState.noPumpkinBlur;case "antiAfk"->SkyzClientState.antiAfk;
        case "autoGG"->SkyzClientState.autoGG;case "noFireOverlay"->SkyzClientState.noFireOverlay;
        case "coloredHitboxes"->SkyzClientState.coloredHitboxes;case "toggleChat"->SkyzClientState.toggleChat;
        default->false;};}

    private void setToggle(String f,boolean v){switch(f){
        case "toggleSprint"->SkyzClientState.toggleSprint=v;case "toggleSneak"->SkyzClientState.toggleSneak=v;
        case "fullbright"->SkyzClientState.fullbright=v;case "noFog"->SkyzClientState.noFog=v;
        case "noPumpkinBlur"->SkyzClientState.noPumpkinBlur=v;case "antiAfk"->SkyzClientState.antiAfk=v;
        case "autoGG"->SkyzClientState.autoGG=v;case "noFireOverlay"->SkyzClientState.noFireOverlay=v;
        case "coloredHitboxes"->SkyzClientState.coloredHitboxes=v;case "toggleChat"->SkyzClientState.toggleChat=v;}}

    // \u2500\u2500 Input \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx=click.x(), my=click.y();
        int sx=sidebarX();

        // Sidebar toggle
        int bx=sx-2;
        if (mx>=bx&&mx<=bx+20&&my>=height/2-14&&my<=height/2+14) {
            sidebarOpen=!sidebarOpen; return true;
        }

        // Bottom bar buttons
        if (my>=height-24&&my<=height-3) {
            int cgx=80;
            if (mx>=cgx&&mx<=cgx+60) { gridIdx=(gridIdx+1)%GRIDS.length; return true; }
            if (mx>=cgx+66&&mx<=cgx+116) { snapEnabled=!snapEnabled; return true; }
            if (mx>=width-130&&mx<=width-74) {
                SkyzConfig.save(); parent.toast("Saved!"); return true;
            }
            if (mx>=width-70&&mx<=width-4) {
                resetLayout(); return true;
            }
        }

        // Sidebar interactions
        if (sidebarOpen && mx>=sx) {
            // Tab switch
            int tw=(SIDEBAR_W-2)/2;
            for (int i=0;i<TABS.length;i++){
                int tx=sx+2+i*(tw+1);
                if (mx>=tx&&mx<=tx+tw&&my>=2&&my<=20){ activeTab=i; return true; }
            }
            if (activeTab==0) return clickHudTab(mx,my,sx);
            else              return clickModsTab(mx,my,sx);
        }

        // Preview area: select / start drag
        // Topmost (last in list) takes priority
        for (int i=elements.size()-1;i>=0;i--) {
            var el=elements.get(i);
            if (mx>=el.x&&mx<=el.x+el.w&&my>=el.y&&my<=el.y+el.h) {
                selected=el; dragging=el;
                dragOffX=(int)mx-el.x; dragOffY=(int)my-el.y;
                return true;
            }
        }
        selected=null;
        return false;
    }

    private boolean clickHudTab(double mx, double my, int sx) {
        int listTop=22, ey=listTop-hudScroll;
        String lastCat="";
        for (var el : elements) {
            if (!el.category.equals(lastCat)){ lastCat=el.category; ey+=14; }
            // Toggle pill
            if (mx>=sx+6&&mx<=sx+36&&my>=ey+4&&my<=ey+18){ el.enabled=!el.enabled; return true; }
            // Row click = select
            if (mx>=sx+2&&mx<=sx+SIDEBAR_W+PAD-2&&my>=ey&&my<=ey+EL_ROW){ selected=el; return true; }
            ey+=EL_ROW;
        }
        // Snap pill
        ey+=10;
        if (mx>=sx+SIDEBAR_W-26&&mx<=sx+SIDEBAR_W+4&&my>=ey+2&&my<=ey+16){ snapEnabled=!snapEnabled; return true; }
        // Vanilla
        ey+=28;
        if (mx>=sx+6&&mx<=sx+36){
            if (my>=ey&&my<=ey+14)    {SkyzHudState.hideVanillaHealth=!SkyzHudState.hideVanillaHealth;return true;}
            if (my>=ey+18&&my<=ey+32) {SkyzHudState.hideVanillaHunger=!SkyzHudState.hideVanillaHunger;return true;}
            if (my>=ey+36&&my<=ey+50) {SkyzHudState.hideVanillaArmor=!SkyzHudState.hideVanillaArmor;return true;}
        }
        return false;
    }

    private boolean clickModsTab(double mx, double my, int sx) {
        int clipTop=22, ey=clipTop-modsScroll;
        for (String[] mod : MODS) {
            if (ey+MOD_ROW<clipTop){ey+=MOD_ROW;continue;}
            if (mod[3].equals("toggle")){
                int tx=sx+SIDEBAR_W+PAD-38;
                if (mx>=tx&&mx<=tx+30&&my>=ey+12&&my<=ey+26){
                    setToggle(mod[2],!getToggle(mod[2]));
                    parent.toast(mod[0]+": "+(getToggle(mod[2])?"ON":"OFF")); return true;
                }
            } else {
                int sX=sx+8,sW=SIDEBAR_W-50;
                if (mx>=sX&&mx<=sX+sW&&my>=ey+26&&my<=ey+35){
                    SkyzClientState.fovMultiplier=Math.max(0.5f,Math.min(2f,0.5f+(float)((mx-sX)/sW)*1.5f));
                    return true;
                }
            }
            ey+=MOD_ROW;
        }
        return false;
    }

    private void resetLayout(){
        SkyzHudState.initialized=false; SkyzHudState.ELEMENTS.clear();
        SkyzHudState.initDefaults(width,height);
        elements=SkyzHudState.ELEMENTS; selected=null; dragging=null; hudScroll=0;
        parent.toast("Layout reset");
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (activeTab==1&&sidebarOpen) {
            double mx=click.x(), my=click.y(); int sx=sidebarX();
            if (mx>=sx){ int ey=22-modsScroll;
                for (String[] mod:MODS){if(ey+MOD_ROW<22){ey+=MOD_ROW;continue;}
                    if(mod[3].equals("slider")&&my>=ey+26&&my<=ey+35){
                        int sX=sx+8,sW=SIDEBAR_W-50;
                        SkyzClientState.fovMultiplier=Math.max(0.5f,Math.min(2f,0.5f+(float)((mx-sX)/sW)*1.5f));
                        return true;}ey+=MOD_ROW;}}}
        return super.mouseDragged(click,dx,dy);
    }

    @Override
    public boolean mouseReleased(Click click){dragging=null;return super.mouseReleased(click);}

    @Override
    public boolean mouseScrolled(double mx,double my,double h,double v){
        int sx=sidebarX();
        if (sidebarOpen&&mx>=sx){
            if (activeTab==0){
                int totalRows=elements.size()*EL_ROW+24+60;
                int listH=height-52;
                hudScroll=(int)Math.max(0,Math.min(Math.max(0,totalRows-listH),hudScroll-v*20));
            } else {
                int totalH=MODS.length*MOD_ROW;
                int visH=height-52;
                modsScroll=(int)Math.max(0,Math.min(Math.max(0,totalH-visH),modsScroll-v*20));
            }
            return true;
        }
        return false;
    }

    @Override public boolean charTyped(CharInput input){return false;}
    @Override public boolean shouldPause(){return false;}
}
