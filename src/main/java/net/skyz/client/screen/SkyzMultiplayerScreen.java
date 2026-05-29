package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.TextureComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Skyz Multiplayer screen — owo-lib edition (Phase 2b port).
 *
 * Layout structure: see assets/skyz_client/owo_ui/multiplayer.xml.
 *
 * Server cards are built programmatically from {@link ServerList} in
 * {@link #rebuildServerList} and injected into the {@code server-list}
 * {@link FlowLayout}. Pings come from {@link ServerStatusPinger}
 * (ticked in {@link #tick}); we rebuild the card list every {@code REBUILD_TICKS}
 * ticks so latest pings show up — the server count is small, the rebuild is
 * cheap, and this keeps card state in sync without per-card label tracking.
 *
 * Direct Connect intentionally stays as a Java-rendered overlay drawn after
 * super.extractRenderState() — the modal needs single-field text input with submit-on-Enter
 * which is easier to implement with the existing screen-level char/key hooks
 * than by spinning up a second owo screen.
 */
public class SkyzMultiplayerScreen extends BaseUIModelScreen<FlowLayout> {
    /** Alias for the inherited Minecraft instance (26.1 renamed the Screen field client->minecraft). */
    private final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();


    private static final int REBUILD_TICKS = 30;   // ≈1.5 s

    /**
     * Generic {@link Screen} parent — back-button target. Originally typed
     * as {@link SkyzTitleScreen} so the toast helper was directly callable;
     * loosened to {@code Screen} so {@link net.skyz.client.mixin.MultiplayerScreenMixin}
     * can redirect from vanilla {@code JoinMultiplayerScreen} (which gets opened
     * by MC's disconnect path and has its own non-Skyz parent).
     * {@link #toastParent} is the Skyz-typed view used for toast() calls
     * when one is available.
     */
    private final Screen                  parent;
    private final SkyzTitleScreen         toastParent;
    private final Minecraft               mc;
    private final ServerList              serverList;
    private final ServerStatusPinger      pinger;

    // Owo references resolved in build().
    private FlowLayout      serverListContainer;
    private LabelComponent  countLabel;
    private TextBoxComponent searchBox;
    private ButtonComponent  filterAllBtn;
    private ButtonComponent  filterFavBtn;

    private String  searchQuery      = "";
    private String  activeFilter     = "all";
    private int     ticksSinceBuild  = 0;
    private boolean connectingDirect = false;
    private String  directAddr       = "";

    // Favicon textures live in {@link SkyzServerIconCache} which is
    // process-scoped — texture uploads survive navigating away and back.

    public SkyzMultiplayerScreen(SkyzTitleScreen parent) {
        this((Screen) parent);
    }

    /**
     * Generic-parent constructor — used by {@code MultiplayerScreenMixin}
     * when redirecting from vanilla {@code JoinMultiplayerScreen} (e.g. after
     * a server disconnect). The parent here may be any {@link Screen}; we
     * downcast to {@link SkyzTitleScreen} when available so toast()
     * notifications still work, otherwise toast() calls become no-ops.
     */
    public SkyzMultiplayerScreen(Screen parent) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "multiplayer"));
        this.parent      = parent;
        this.toastParent = parent instanceof SkyzTitleScreen sts ? sts : null;
        this.mc          = Minecraft.getInstance();
        this.serverList  = new ServerList(mc);
        this.serverList.load();
        this.pinger      = new ServerStatusPinger();
    }

    // ── Build (owo model wiring) ─────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        SkyzClientMod.LOGGER.info("[Skyz] Multiplayer built. Root has {} child(ren).",
                root.children().size());

        // Buttons.
        wire(root, "btn-back", () -> {
            pinger.removeAll();
            client.setScreen(parent);
        }, SkyzButtonRenderer.NAV_BACK);

        wire(root, "btn-add-server", this::openAddServer, SkyzButtonRenderer.DEFAULT);

        wire(root, "btn-direct-connect", () -> {
            connectingDirect = true;
            directAddr = "";
        }, SkyzButtonRenderer.DEFAULT);

        wire(root, "btn-refresh", () -> {
            pinger.removeAll();
            // Reset each ServerData's status so the card UI re-shows pending pings.
            for (int i = 0; i < serverList.size(); i++) {
                serverList.get(i).ping = -1L;
            }
            rebuildServerList();
            pingAllServers();
        }, SkyzButtonRenderer.NAV_BACK);

        // Filter pills — track active state via the renderer.
        filterAllBtn = root.childById(ButtonComponent.class, "btn-filter-all");
        filterFavBtn = root.childById(ButtonComponent.class, "btn-filter-fav");
        if (filterAllBtn != null) {
            filterAllBtn.onPress(b -> { activeFilter = "all";  refreshFilters(); rebuildServerList(); });
        }
        if (filterFavBtn != null) {
            filterFavBtn.onPress(b -> { activeFilter = "fav";  refreshFilters(); rebuildServerList(); });
        }
        refreshFilters();

        // Search box.
        searchBox = root.childById(TextBoxComponent.class, "tb-search");
        if (searchBox != null) {
            // Disable the vanilla black-rectangle backdrop so our pill surface
            // shows through.
            searchBox.setBordered(false);
            searchBox.onChanged().subscribe(value -> {
                searchQuery = value;
                rebuildServerList();
            });
        }
        // Apply Skyz pill surface to the search-box wrapper.
        FlowLayout searchWrapper = root.childById(FlowLayout.class, "search-wrapper");
        if (searchWrapper != null) searchWrapper.surface(SkyzSurface.PILL_INPUT);

        // Count label + server list container references.
        countLabel          = root.childById(LabelComponent.class, "lbl-count");
        serverListContainer = root.childById(FlowLayout.class, "server-list");

        // Skyz-themed flat scrollbar on the wrapping scroll container.
        // Translucent accent blue keeps the look consistent with the toolbar
        // pills + connect-button highlight.
        ScrollContainer<?> scroll = root.childById(ScrollContainer.class, "scroll-server-list");
        if (scroll != null) {
            scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xCC8CD2FF)));
        }

        // Diagnostic: confirm id lookups succeeded. Quick way to spot
        // typos / element-vs-attribute id mistakes in the XML.
        SkyzClientMod.LOGGER.info(
                "[Skyz] Multiplayer wirings: search={}, search-wrapper={}, count={}, "
                + "server-list={}, servers-saved={}",
                searchBox != null, searchWrapper != null, countLabel != null,
                serverListContainer != null, serverList.size());

        // Initial population + start pinging in the background.
        rebuildServerList();
        pingAllServers();
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        try {
            ButtonComponent btn = root.childById(ButtonComponent.class, id);
            if (btn == null) {
                SkyzClientMod.LOGGER.warn("[Skyz] Multiplayer: button id '{}' not found.", id);
                return;
            }
            btn.onPress(b -> action.run());
            btn.renderer(renderer);
        } catch (Exception e) {
            SkyzClientMod.LOGGER.warn("[Skyz] Multiplayer: failed to wire '{}': {}", id, e.getMessage());
        }
    }

    private void refreshFilters() {
        if (filterAllBtn != null) {
            filterAllBtn.renderer(activeFilter.equals("all")
                    ? SkyzButtonRenderer.DEFAULT
                    : SkyzButtonRenderer.NAV_BACK);
        }
        if (filterFavBtn != null) {
            filterFavBtn.renderer(activeFilter.equals("fav")
                    ? SkyzButtonRenderer.DEFAULT
                    : SkyzButtonRenderer.NAV_BACK);
        }
    }

    // ── Server cards ─────────────────────────────────────────────────────
    private void rebuildServerList() {
        if (serverListContainer == null) return;
        serverListContainer.clearChildren();

        List<ServerData> visible = getFiltered();

        if (countLabel != null) {
            countLabel.text(Component.literal(visible.size() + " server"
                    + (visible.size() == 1 ? "" : "s")));
        }

        if (visible.isEmpty()) {
            LabelComponent empty = UIComponents
                    .label(Component.literal(serverList.size() == 0
                            ? "No servers saved. MouseButtonEvent + Add Server or Direct Connect."
                            : "No servers match \"" + searchQuery + "\"."))
                    .color(io.wispforest.owo.ui.core.Color.ofArgb(SkyzColors.TEXT_MUTED))
                    .horizontalTextAlignment(HorizontalAlignment.CENTER);
            empty.horizontalSizing(Sizing.fill(100));
            empty.margins(Insets.vertical(20));
            serverListContainer.child(empty);
            return;
        }

        for (ServerData info : visible) {
            serverListContainer.child(buildCard(info));
        }
    }

    private FlowLayout buildCard(ServerData info) {
        // Card root: horizontal flow with rounded card surface.
        // FlowLayout-specific calls (.gap) and base ParentUIComponent calls
        // (.padding, .surface) don't share a fluent return type, so we use
        // statement-form rather than chaining.
        FlowLayout card = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(54));
        card.gap(10);
        card.padding(Insets.of(8, 8, 12, 12));
        card.verticalAlignment(VerticalAlignment.CENTER);
        card.surface(SkyzSurface.CARD);

        // Server favicon — ServerIconTexture.getTextureLocation() falls back to
        // vanilla's "unknown_server.png" when no favicon has been loaded.
        FaviconTexture iconTex = SkyzServerIconCache.getOrUpload(info);
        TextureComponent iconComp = UIComponents.texture(
                iconTex.textureLocation(), 0, 0, 64, 64, 64, 64);
        iconComp.horizontalSizing(Sizing.fixed(36));
        iconComp.verticalSizing(Sizing.fixed(36));
        iconComp.margins(Insets.right(2));
        card.child(iconComp);

        // Info column: name, address, motd.
        FlowLayout info_col = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        info_col.gap(2);

        String name = (info.name != null && !info.name.isEmpty()) ? info.name : info.ip;
        info_col.child(UIComponents
                .label(Component.literal(name))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(SkyzColors.TEXT_PRIMARY)));
        info_col.child(UIComponents
                .label(Component.literal(info.ip))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(0xFF4D8CD2)));

        if (info.motd != null && !info.motd.getString().isEmpty()) {
            String motdStr = info.motd.getString();
            if (motdStr.length() > 60) motdStr = motdStr.substring(0, 57) + "...";
            info_col.child(UIComponents
                    .label(Component.literal(motdStr))
                    .color(io.wispforest.owo.ui.core.Color.ofArgb(0xFF778CD2)));
        }
        card.child(info_col);

        // Ping column: ms + players (right-aligned).
        FlowLayout ping_col = UIContainers.verticalFlow(Sizing.fixed(70), Sizing.content());
        ping_col.gap(2);
        ping_col.horizontalAlignment(HorizontalAlignment.RIGHT);

        int pingMs = (int) info.ping;
        int pingCol = pingMs < 0   ? 0xFF888888
                    : pingMs < 80  ? 0xFF4CFA87
                    : pingMs < 150 ? 0xFFFACC4C
                                   : 0xFFFA6C4C;
        String pingStr = pingMs < 0 ? "..." : pingMs + " ms";
        ping_col.child(UIComponents
                .label(Component.literal(pingStr))
                .color(io.wispforest.owo.ui.core.Color.ofArgb(pingCol)));

        if (info.players != null) {
            ping_col.child(UIComponents
                    .label(Component.literal(info.players.online() + "/" + info.players.max()))
                    .color(io.wispforest.owo.ui.core.Color.ofArgb(0xFF4D8CD2)));
        }
        card.child(ping_col);

        // Action column: Connect (primary) + Edit + Delete stacked horizontally.
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        actions.gap(3);
        actions.verticalAlignment(VerticalAlignment.CENTER);

        ButtonComponent connectBtn = UIComponents
                .button(Component.literal("CONNECT"), b -> doConnect(info))
                .renderer(SkyzButtonRenderer.DEFAULT);
        connectBtn.horizontalSizing(Sizing.fixed(64));
        connectBtn.verticalSizing(Sizing.fixed(20));
        actions.child(connectBtn);

        ButtonComponent editBtn = UIComponents
                .button(Component.literal("✏"), b -> editServer(info))
                .renderer(SkyzButtonRenderer.NAV_BACK);
        editBtn.horizontalSizing(Sizing.fixed(22));
        editBtn.verticalSizing(Sizing.fixed(20));
        actions.child(editBtn);

        ButtonComponent deleteBtn = UIComponents
                .button(Component.literal("🗑"), b -> deleteServer(info))
                .renderer(SkyzButtonRenderer.QUIT);
        deleteBtn.horizontalSizing(Sizing.fixed(22));
        deleteBtn.verticalSizing(Sizing.fixed(20));
        actions.child(deleteBtn);

        card.child(actions);

        return card;
    }

    /**
     * Open the SkyzAddServerScreen pre-populated with the existing server's
     * fields (mirrors vanilla's "Edit Server" flow): we copy into a draft,
     * pass that to the editor, and on confirm copy the draft back over the
     * original so saved settings are preserved.
     */
    private void editServer(ServerData original) {
        ServerData draft = new ServerData(original.name, original.ip, ServerData.Type.OTHER);
        draft.copyFrom(original);
        client.setScreen(new SkyzAddServerScreen(
                this,
                Component.translatable("manageServer.edit.title"),
                confirmed -> {
                    if (confirmed) {
                        original.name    = draft.name;
                        original.ip      = draft.ip;
                        original.copyFrom(draft);
                        serverList.save();
                        rebuildServerList();
                        pingAllServers();
                    }
                    client.setScreen(this);
                },
                draft));
    }

    /**
     * Removes a server from the list. No confirmation dialog yet — the
     * vanilla two-step prompt could be ported later. Also evicts the
     * favicon from the global cache so the texture isn't leaked.
     */
    private void deleteServer(ServerData info) {
        serverList.remove(info);
        serverList.save();
        SkyzServerIconCache.invalidate(info.ip);
        rebuildServerList();
    }

    private List<ServerData> getFiltered() {
        String q = searchQuery.toLowerCase();
        List<ServerData> out = new ArrayList<>();
        for (int i = 0; i < serverList.size(); i++) {
            ServerData s = serverList.get(i);
            if ("fav".equals(activeFilter)) {
                // ServerData.Type.LAN / OTHER — favourites concept isn't a 1.21
                // ServerList field; placeholder behaviour: empty list under "fav".
                continue;
            }
            if (!q.isEmpty()
                    && !s.name.toLowerCase().contains(q)
                    && !s.ip.toLowerCase().contains(q)) continue;
            out.add(s);
        }
        return out;
    }

    /**
     * Kicks off pinging for every server in a background thread per server.
     * {@code pinger.add(...)} resolves DNS synchronously inside the call —
     * doing it on the render thread for several servers stalls the screen
     * 50–500 ms, which is the freeze the user noticed when first opening
     * Multiplayer. Vanilla {@code ServerSelectionList} does the
     * same trick (via server pinger thread pool); we use plain
     * daemon threads to keep it dependency-free.
     */
    private void pingAllServers() {
        for (int i = 0; i < serverList.size(); i++) {
            final ServerData info = serverList.get(i);
            Thread t = new Thread(() -> {
                try {
                    ServerAddress.parseString(info.ip);  // validate
                    pinger.pingServer(info, () -> serverList.save(), () -> {},
                            net.minecraft.server.network.EventLoopGroupHolder.remote(false));
                } catch (Exception ignored) {}
            }, "skyz-ping-" + info.ip);
            t.setDaemon(true);
            t.start();
        }
    }

    private void doConnect(ServerData info) {
        pinger.removeAll();
        ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(info.ip), info, false, null);
    }

    /**
     * Mirrors vanilla's "+ Add Server" flow: open AddServerScreen with a
     * fresh ServerData, and on confirm, append it to {@link #serverList},
     * save servers.dat, and rebuild our card list. On cancel, just return.
     */
    private void openAddServer() {
        ServerData draft = new ServerData("", "", ServerData.Type.OTHER);
        client.setScreen(new SkyzAddServerScreen(
                this,
                Component.translatable("manageServer.add.title"),
                added -> {
                    if (added) {
                        ServerData existing = serverList.unhide(draft.ip);
                        if (existing != null) {
                            existing.copyFrom(draft);
                        } else {
                            serverList.add(draft, false);
                        }
                        serverList.save();
                        rebuildServerList();
                        pingAllServers();
                    }
                    client.setScreen(this);
                },
                draft));
    }


    // ── Lifecycle ────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();
        try { pinger.tick(); } catch (Exception ignored) {}
        if (++ticksSinceBuild >= REBUILD_TICKS) {
            ticksSinceBuild = 0;
            rebuildServerList();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        pinger.removeAll();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        pinger.removeAll();
        // Favicons stay resident in SkyzServerIconCache for the life of
        // the JVM — no per-screen cleanup.
    }

    // ── Render ───────────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // No-op: render() handles the gradient background.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // 1) Gradient background — same scheme as the title screen.
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,            width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,   width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3, width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);

        // 2) Owo UI (nav, action row, toolbar, server list).
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // 3) Direct Connect modal overlay (custom Java render — see header).
        if (connectingDirect) drawDirectConnectOverlay(ctx, mouseX, mouseY);

        // 4) Toast (re-uses parent screen's toast manager).
        if (toastParent != null) toastParent.toast.render(ctx, width, delta);
    }

    private void drawDirectConnectOverlay(GuiGraphicsExtractor ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0xCC050F2A);

        int bw = 320, bh = 110, bx = (width - bw) / 2, by = (height - bh) / 2;
        SkyzRenderHelper.fillRoundedRect(ctx, bx, by, bw, bh, 12, 0xEE071830);
        SkyzRenderHelper.drawRoundedBorder(ctx, bx, by, bw, bh, 12, 0x778CD2FF);

        ctx.centeredText(font, "Direct Connect",
                width / 2, by + 14, 0xFFF0F8FF);
        ctx.text(font, "Server address:",
                bx + 14, by + 30, SkyzColors.TEXT_MUTED);

        // Input pill.
        SkyzRenderHelper.fillRoundedRect(ctx, bx + 14, by + 42, bw - 28, 22, 6, 0x55091E46);
        SkyzRenderHelper.drawRoundedBorder(ctx, bx + 14, by + 42, bw - 28, 22, 6, 0x558CD2FF);
        String addr = directAddr.isEmpty() ? "play.example.com" : directAddr + "▍";
        ctx.text(font, addr, bx + 20, by + 49,
                directAddr.isEmpty() ? 0x388CD2FF : SkyzColors.TEXT_PRIMARY);

        // Buttons.
        int btnY = by + bh - 30;
        boolean cnHov = mx >= bx + 14         && mx <= bx + 144         && my >= btnY && my <= btnY + 22;
        boolean caHov = mx >= bx + bw - 144   && mx <= bx + bw - 14     && my >= btnY && my <= btnY + 22;

        SkyzRenderHelper.fillRoundedRect(ctx, bx + 14,        btnY, 130, 22, 8,
                cnHov ? 0xCC2864AE : 0x80143C6E);
        SkyzRenderHelper.drawRoundedBorder(ctx, bx + 14,      btnY, 130, 22, 8,
                cnHov ? 0xCC8CDCFF : 0x808CDCFF);

        SkyzRenderHelper.fillRoundedRect(ctx, bx + bw - 144,  btnY, 130, 22, 8,
                caHov ? 0x66143C6E : 0x33091E46);
        SkyzRenderHelper.drawRoundedBorder(ctx, bx + bw - 144, btnY, 130, 22, 8,
                caHov ? 0x998CDCFF : 0x4D8CDCFF);

        ctx.centeredText(font, "Connect",
                bx + 79, btnY + 7, cnHov ? 0xFFFFFFFF : 0xCCDDFFFF);
        ctx.centeredText(font, "Cancel",
                bx + bw - 79, btnY + 7, caHov ? 0xFFFFFFFF : SkyzColors.TEXT_MUTED);
    }

    // ── Input ────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        // When the modal is open, intercept clicks for its buttons before owo.
        if (connectingDirect) {
            double mx = click.x(), my = click.y();
            int bw = 320, bh = 110, bx = (width - bw) / 2, by = (height - bh) / 2;
            int btnY = by + bh - 30;
            if (mx >= bx + 14 && mx <= bx + 144 && my >= btnY && my <= btnY + 22) {
                if (!directAddr.isEmpty()) {
                    connectingDirect = false;
                    doConnect(new ServerData(directAddr, directAddr,
                            ServerData.Type.OTHER));
                }
            } else if (mx >= bx + bw - 144 && mx <= bx + bw - 14
                    && my >= btnY && my <= btnY + 22) {
                connectingDirect = false;
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (connectingDirect) {
            directAddr += event.codepointAsString();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (connectingDirect) {
            if (key == 259 /* BACKSPACE */) {
                if (!directAddr.isEmpty())
                    directAddr = directAddr.substring(0, directAddr.length() - 1);
                return true;
            }
            if (key == 256 /* ESC */) { connectingDirect = false; return true; }
            if ((key == 257 || key == 335) /* ENTER / KP_ENTER */ && !directAddr.isEmpty()) {
                connectingDirect = false;
                doConnect(new ServerData(directAddr, directAddr,
                        ServerData.Type.OTHER));
                return true;
            }
            return true;  // swallow other keys while modal is open
        }
        return super.keyPressed(event);
    }
}
