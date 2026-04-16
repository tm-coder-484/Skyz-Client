package net.skyz.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import net.skyz.client.util.*;
import net.skyz.client.util.SkyzTheme;

import java.util.*;

public class SkyzMultiplayerScreen extends Screen {

    private final SkyzTitleScreen            parent;
    private final MinecraftClient            mc;
    private final ServerList                 serverList;
    private final MultiplayerServerListPinger pinger;

    private String  searchQuery      = "";
    private String  activeFilter     = "all";
    private int     scrollOffset     = 0;
    private boolean connectingDirect = false;
    private String  directAddr       = "";

    private static final int NAV_H     = 30;
    private static final int TOOLBAR_H = 28;
    private static final int CARD_H    = 64;
    private static final int CARD_GAP  = 6;
    private static final int PAD       = 12;

    public SkyzMultiplayerScreen(SkyzTitleScreen parent) {
        super(Text.literal("Multiplayer"));
        this.parent     = parent;
        this.mc         = MinecraftClient.getInstance();
        this.serverList = new ServerList(mc);
        this.serverList.loadFile();
        this.pinger     = new MultiplayerServerListPinger();
    }

    @Override
    protected void init() {
        addDrawableChild(SkyzButton.of(width - 82, 7, 74, 18, "\u2190 Back",
                () -> {
                    pinger.cancel();
                    client.setScreen(parent);
                }));
        addDrawableChild(SkyzButton.of(PAD, NAV_H + 4, 100, 20, "+ Add Server",
                () -> client.setScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(this))));
        addDrawableChild(SkyzButton.of(PAD + 108, NAV_H + 4, 120, 20, "\u26A1 Direct Connect",
                () -> { connectingDirect = true; directAddr = ""; }));

        // Ping all servers asynchronously
        pingServers();
    }

    private void pingServers() {
        for (int i = 0; i < serverList.size(); i++) {
            ServerInfo info = serverList.get(i);
            try {
                ServerAddress addr = ServerAddress.parse(info.address);
                pinger.add(info, () -> serverList.saveFile(), () -> {}, NetworkingBackend.remote(false));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void tick() {
        super.tick();
        try { pinger.tick(); } catch (Exception ignored) {}
    }

    @Override
    public void removed() {
        pinger.cancel();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0, width, height, SkyzTheme.BG1, SkyzTheme.BG2);
        drawNavBar(ctx);
        drawToolbar(ctx, mouseX, mouseY);
        drawServerList(ctx, mouseX, mouseY);
        if (connectingDirect) drawDirectConnectOverlay(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
        parent.toast.render(ctx, width, delta);
    }

    private void drawNavBar(DrawContext ctx) {
        ctx.drawTextWithShadow(textRenderer, "SKYZ", 10, 10, 0xFFF0F8FF);
        int x = 10 + textRenderer.getWidth("SKYZ") + 6;
        ctx.drawTextWithShadow(textRenderer, "/", x, 10, 0x4D8CD2FF);
        x += textRenderer.getWidth("/") + 6;
        ctx.drawTextWithShadow(textRenderer, "MULTIPLAYER", x, 10, 0x888CD2FF);
        SkyzRenderHelper.drawDivider(ctx, 0, NAV_H, width);
    }

    private void drawToolbar(DrawContext ctx, int mx, int my) {
        int ty = NAV_H + 4;
        int sX = PAD + 238, sW = Math.min(200, width - sX - PAD - 80);
        SkyzRenderHelper.fillPanel(ctx, sX, ty, sW, 20, 0x55091E46, 0x338CD2FF);
        String q = searchQuery.isEmpty() ? "Search servers..." : searchQuery;
        ctx.drawTextWithShadow(textRenderer, q, sX + 6, ty + 6,
                searchQuery.isEmpty() ? 0x388CD2FF : SkyzColors.TEXT_PRIMARY);

        int fx = sX + sW + 8;
        for (String[] f : new String[][]{{"all","All"},{"fav","Favourites"}}) {
            boolean active = activeFilter.equals(f[0]);
            int fw = textRenderer.getWidth(f[1]) + 14;
            SkyzRenderHelper.fillPanel(ctx, fx, ty, fw, 20,
                    active ? 0x553C6E90 : 0x33091E46, active ? 0x998CD2FF : 0x338CD2FF);
            ctx.drawTextWithShadow(textRenderer, f[1], fx + 7, ty + 6,
                    active ? 0xFFFFFFFF : SkyzColors.TEXT_MUTED);
            fx += fw + 4;
        }

        List<ServerInfo> vis = getFiltered();
        String cnt = vis.size() + " server" + (vis.size() == 1 ? "" : "s");
        ctx.drawTextWithShadow(textRenderer, cnt,
                width - PAD - textRenderer.getWidth(cnt), ty + 6, 0x4D8CD2FF);
    }

    private void drawServerList(DrawContext ctx, int mx, int my) {
        List<ServerInfo> servers = getFiltered();
        int listTop = NAV_H + TOOLBAR_H + 8;
        int listH   = height - listTop - 8;
        int cardW   = width - PAD * 2;
        int y       = listTop - scrollOffset;

        if (servers.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "No servers saved. Click \u201C+ Add Server\u201D or Direct Connect.",
                    width / 2, listTop + listH / 2, SkyzColors.TEXT_MUTED);
            return;
        }

        ctx.enableScissor(0, listTop, width, height - 8);
        for (ServerInfo s : servers) {
            if (y + CARD_H >= listTop && y <= listTop + listH)
                drawCard(ctx, s, PAD, y, cardW, CARD_H, mx, my);
            y += CARD_H + CARD_GAP;
        }
        ctx.disableScissor();

        int totalH = servers.size() * (CARD_H + CARD_GAP);
        if (totalH > listH) {
            SkyzRenderHelper.fillRect(ctx, width - 4, listTop, 3, listH, 0x1A8CD2FF);
            int th  = Math.max(20, (int)((float) listH / totalH * listH));
            int ty2 = listTop + (int)((float) scrollOffset / Math.max(1, totalH - listH) * (listH - th));
            SkyzRenderHelper.fillRect(ctx, width - 4, ty2, 3, th, 0x558CD2FF);
        }
    }

    private void drawCard(DrawContext ctx, ServerInfo s,
                          int x, int y, int w, int h, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        SkyzRenderHelper.fillPanel(ctx, x, y, w, h,
                hov ? 0x800C2A5A : SkyzColors.CARD_BG, hov ? 0x478CD2FF : SkyzColors.CARD_BORDER);

        ctx.drawTextWithShadow(textRenderer, "\uD83D\uDDA5", x + 12, y + (h - 8) / 2, 0xFFFFFFFF);

        int tx = x + 30;
        String name = (s.name != null && !s.name.isEmpty()) ? s.name : s.address;
        ctx.drawTextWithShadow(textRenderer, name, tx, y + 8, SkyzColors.TEXT_PRIMARY);
        ctx.drawTextWithShadow(textRenderer, s.address, tx, y + 20, 0x4D8CD2FF);

        if (s.label != null && !s.label.getString().isEmpty()) {
            String motd = s.label.getString();
            int maxW = w - 165;
            while (textRenderer.getWidth(motd) > maxW && motd.length() > 4)
                motd = motd.substring(0, motd.length() - 4) + "...";
            ctx.drawTextWithShadow(textRenderer, motd, tx, y + 33, 0x778CD2FF);
        }

        // Ping - s.ping is long; -1 = not yet pinged
        int pingMs  = (int) s.ping;
        int pingCol = pingMs < 0   ? 0xFF888888
                    : pingMs < 80  ? 0xFF4CFA87
                    : pingMs < 150 ? 0xFFFACC4C : 0xFFFA6C4C;
        String pingStr = pingMs < 0 ? "..." : pingMs + "ms";
        int pingX = x + w - 95;
        SkyzRenderHelper.fillRect(ctx, pingX + 5, y + 12, 8, 8, pingCol);
        ctx.drawTextWithShadow(textRenderer, pingStr, pingX + 16, y + 13, pingCol);

        if (s.playerCountLabel != null && !s.playerCountLabel.getString().isEmpty())
            ctx.drawTextWithShadow(textRenderer, s.playerCountLabel.getString(), pingX, y + 26, 0x4D8CD2FF);

        // Connect button
        int bx = x + w - 62, by = y + (h - 18) / 2;
        boolean bHov = mx >= bx && mx <= bx + 54 && my >= by && my <= by + 18;
        SkyzRenderHelper.fillPanel(ctx, bx, by, 54, 18,
                bHov ? 0x661E6EC8 : 0x441864A0, 0x558CD2FF);
        ctx.drawCenteredTextWithShadow(textRenderer, "Connect", bx + 27, by + 5,
                bHov ? 0xFFFFFFFF : 0xCCDDFFFF);
    }

    private void drawDirectConnectOverlay(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0xCC050F2A);
        int bw = 320, bh = 110, bx = (width - bw) / 2, by = (height - bh) / 2;
        SkyzRenderHelper.fillPanel(ctx, bx, by, bw, bh, 0xEE071830, 0x778CD2FF);
        ctx.drawCenteredTextWithShadow(textRenderer, "Direct Connect", width / 2, by + 14, 0xFFF0F8FF);
        ctx.drawTextWithShadow(textRenderer, "Server address:", bx + 14, by + 30, SkyzColors.TEXT_MUTED);

        SkyzRenderHelper.fillPanel(ctx, bx + 14, by + 42, bw - 28, 22, 0x55091E46, 0x558CD2FF);
        String addr = directAddr.isEmpty() ? "play.example.com" : directAddr + "\u258D";
        ctx.drawTextWithShadow(textRenderer, addr, bx + 20, by + 49,
                directAddr.isEmpty() ? 0x388CD2FF : SkyzColors.TEXT_PRIMARY);

        int btnY = by + bh - 30;
        boolean cnHov = mx >= bx+14 && mx <= bx+144 && my >= btnY && my <= btnY+22;
        boolean caHov = mx >= bx+bw-144 && mx <= bx+bw-14 && my >= btnY && my <= btnY+22;
        SkyzRenderHelper.fillPanel(ctx, bx+14,     btnY, 130, 22, cnHov?0x661E6EC8:0x551864A0, 0x558CD2FF);
        SkyzRenderHelper.fillPanel(ctx, bx+bw-144, btnY, 130, 22, caHov?0x66143C6E:0x33091E46, 0x338CD2FF);
        ctx.drawCenteredTextWithShadow(textRenderer, "Connect", bx+79,    btnY+7, cnHov?0xFFFFFFFF:0xCCDDFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "Cancel",  bx+bw-79, btnY+7, caHov?0xFFFFFFFF:SkyzColors.TEXT_MUTED);
    }

    // Connects to a server \u2014 passing THIS as parent so disconnect returns here
    private void doConnect(ServerInfo info) {
        pinger.cancel();
        ConnectScreen.connect(this, mc, ServerAddress.parse(info.address), info, false, null);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        if (connectingDirect) {
            int bw = 320, bh = 110, bx = (width-bw)/2, by = (height-bh)/2;
            int btnY = by + bh - 30;
            if (mx >= bx+14 && mx <= bx+144 && my >= btnY && my <= btnY+22) {
                if (!directAddr.isEmpty()) {
                    connectingDirect = false;
                    doConnect(new ServerInfo(directAddr, directAddr, ServerInfo.ServerType.OTHER));
                }
            } else if (mx >= bx+bw-144 && my >= btnY && my <= btnY+22) {
                connectingDirect = false;
            }
            return true;
        }

        // Filters
        int ty = NAV_H + 4, sX = PAD + 238;
        int sW = Math.min(200, width - sX - PAD - 80);
        int fx = sX + sW + 8;
        for (String[] f : new String[][]{{"all","All"},{"fav","Favourites"}}) {
            int fw = textRenderer.getWidth(f[1]) + 14;
            if (mx >= fx && mx <= fx+fw && my >= ty && my <= ty+20) {
                activeFilter = f[0]; scrollOffset = 0; return true;
            }
            fx += fw + 4;
        }

        // Cards
        List<ServerInfo> servers = getFiltered();
        int listTop = NAV_H + TOOLBAR_H + 8;
        int cardW   = width - PAD * 2;
        int y       = listTop - scrollOffset;
        for (ServerInfo s : servers) {
            int bx = PAD + cardW - 62, by = y + (CARD_H - 18) / 2;
            if (mx >= bx && mx <= bx+54 && my >= by && my <= by+18) {
                doConnect(s); return true;
            }
            y += CARD_H + CARD_GAP;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int listH  = height - (NAV_H + TOOLBAR_H + 8) - 8;
        int totalH = getFiltered().size() * (CARD_H + CARD_GAP);
        scrollOffset = (int) Math.max(0, Math.min(Math.max(0, totalH-listH), scrollOffset - v*16));
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        String s = input.asString();
        if (connectingDirect) { if (!s.isEmpty()) directAddr += s; }
        else if (!s.isEmpty()) { searchQuery += s; scrollOffset = 0; }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.getKeycode();
        if (key == 259) { // BACKSPACE
            if (connectingDirect) { if (!directAddr.isEmpty()) directAddr = directAddr.substring(0, directAddr.length()-1); }
            else if (!searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length()-1); scrollOffset = 0; }
            return true;
        }
        if (key == 256 && connectingDirect) { connectingDirect = false; return true; }
        if ((key == 257 || key == 335) && connectingDirect && !directAddr.isEmpty()) {
            connectingDirect = false;
            doConnect(new ServerInfo(directAddr, directAddr, ServerInfo.ServerType.OTHER));
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }

    private List<ServerInfo> getFiltered() {
        String q = searchQuery.toLowerCase();
        List<ServerInfo> out = new ArrayList<>();
        for (int i = 0; i < serverList.size(); i++) {
            ServerInfo s = serverList.get(i);
            if (!q.isEmpty() && !s.name.toLowerCase().contains(q) && !s.address.toLowerCase().contains(q)) continue;
            out.add(s);
        }
        return out;
    }
}
