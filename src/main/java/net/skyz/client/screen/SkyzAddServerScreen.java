package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

/**
 * Skyz-styled Add Server / Edit Server screen — drop-in replacement for
 * {@link net.minecraft.client.gui.screens.multiplayer.AddServerScreen} with the
 * same constructor signature so existing call-sites in
 * {@link SkyzMultiplayerScreen#openAddServer} swap over with one import.
 *
 * Behavioural parity with vanilla AddServerScreen:
 *   - Two text fields: server name (defaulting to the saved name), address.
 *   - A cycle button for {@link ServerData.ResourcePackPolicy}.
 *   - Done writes name+address (and the policy was already written via the
 *     cycle handler), then calls the callback with {@code true}.
 *   - Cancel calls the callback with {@code false}.
 *   - Done is disabled until {@link ServerAddress#isValid} returns true.
 *   - ESC returns to parent without invoking the callback (matches vanilla).
 *
 * Visual style: centred card on the Skyz gradient backdrop, pill-styled text
 * inputs, glass buttons.
 */
public class SkyzAddServerScreen extends BaseUIModelScreen<FlowLayout> {
    /** Alias for the inherited Minecraft instance (26.1 renamed the Screen field client->minecraft). */
    private final net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();


    private final Screen          parent;
    private final BooleanConsumer callback;
    private final ServerData      server;

    private TextBoxComponent  nameField;
    private TextBoxComponent  addrField;
    private ButtonComponent   resourcePackBtn;
    private ButtonComponent   doneBtn;
    private LabelComponent    titleLbl;

    public SkyzAddServerScreen(Screen parent, Component title,
                               BooleanConsumer callback, ServerData server) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "add_server"));
        this.parent   = parent;
        this.callback = callback;
        this.server   = server;
        // title is propagated to the title label in build().
        this.title$ = title;
    }

    /** Stash the title text so build() can apply it to the label. */
    private Component title$;

    @Override
    protected void build(FlowLayout root) {
        // Card surface.
        FlowLayout card = root.childById(FlowLayout.class, "card");
        if (card != null) card.surface(SkyzSurface.CARD);

        // Title.
        titleLbl = root.childById(LabelComponent.class, "lbl-title");
        if (titleLbl != null && title$ != null) {
            titleLbl.text(Component.literal(title$.getString().toUpperCase()));
        }

        // Name + address pill surfaces.
        FlowLayout nameWrap = root.childById(FlowLayout.class, "name-wrapper");
        FlowLayout addrWrap = root.childById(FlowLayout.class, "addr-wrapper");
        if (nameWrap != null) nameWrap.surface(SkyzSurface.PILL_INPUT);
        if (addrWrap != null) addrWrap.surface(SkyzSurface.PILL_INPUT);

        // Text fields — disable vanilla black backdrop so the pill shows.
        nameField = root.childById(TextBoxComponent.class, "tb-name");
        addrField = root.childById(TextBoxComponent.class, "tb-addr");
        if (nameField != null) {
            nameField.setBordered(false);
            nameField.setMaxLength(32);
            nameField.text(server.name == null ? "" : server.name);
            nameField.onChanged().subscribe(v -> updateDoneEnabled());
        }
        if (addrField != null) {
            addrField.setBordered(false);
            addrField.setMaxLength(128);
            addrField.text(server.ip == null ? "" : server.ip);
            addrField.onChanged().subscribe(v -> updateDoneEnabled());
        }

        // Resource pack policy cycle button.
        resourcePackBtn = root.childById(ButtonComponent.class, "btn-rp");
        if (resourcePackBtn != null) {
            resourcePackBtn.renderer(SkyzButtonRenderer.NAV_BACK);
            updateRpLabel();
            resourcePackBtn.onPress(b -> {
                ServerData.ServerPackStatus[] all = ServerData.ServerPackStatus.values();
                int next = (server.getResourcePackStatus().ordinal() + 1) % all.length;
                server.setResourcePackStatus(all[next]);
                updateRpLabel();
            });
        }

        // Cancel / Done buttons.
        ButtonComponent cancelBtn = root.childById(ButtonComponent.class, "btn-cancel");
        if (cancelBtn != null) {
            cancelBtn.renderer(SkyzButtonRenderer.NAV_BACK);
            cancelBtn.onPress(b -> callback.accept(false));
        }
        doneBtn = root.childById(ButtonComponent.class, "btn-done");
        if (doneBtn != null) {
            doneBtn.renderer(SkyzButtonRenderer.DEFAULT);
            doneBtn.onPress(b -> addAndClose());
        }
        updateDoneEnabled();

        SkyzClientMod.LOGGER.info(
                "[Skyz] AddServer wirings: name={}, addr={}, rp={}, done={}",
                nameField != null, addrField != null,
                resourcePackBtn != null, doneBtn != null);
    }

    private void updateRpLabel() {
        if (resourcePackBtn == null) return;
        ServerData.ServerPackStatus p = server.getResourcePackStatus();
        resourcePackBtn.setMessage(Component.literal(
                "Resource Packs: " + p.name()));
    }

    private void updateDoneEnabled() {
        if (doneBtn == null || addrField == null) return;
        doneBtn.active(ServerAddress.isValidAddress(addrField.getValue()));
    }

    private void addAndClose() {
        String n = nameField == null ? "" : nameField.getValue();
        String a = addrField == null ? "" : addrField.getValue();
        server.name = n.isEmpty() ? Component.translatable("selectServer.defaultName").getString() : n;
        server.ip   = a;
        callback.accept(true);
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // No-op: render() draws the gradient itself.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,            width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,   width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3, width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
