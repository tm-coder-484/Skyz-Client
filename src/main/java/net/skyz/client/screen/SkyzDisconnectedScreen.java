package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.skyz.client.util.AutoReconnectManager;

/**
 * Skyz Disconnected Screen — owo-lib edition (Phase 2d port).
 *
 * <p>Replaces vanilla {@code DisconnectedScreen} via
 * {@link net.skyz.client.mixin.DisconnectedScreenMixin}. Carries through
 * the original {@code parent} screen, title, and reason text (server kick
 * messages, connection-lost reasons, etc.) so error visibility is
 * preserved 1:1 with vanilla.
 *
 * <p>Single button — "Back to Title" — routes back to the {@code parent}
 * supplied by whoever constructed the disconnect screen, matching vanilla's
 * behaviour (vanilla typically passes a TitleScreen or JoinMultiplayerScreen
 * here so the user lands somewhere sensible).
 */
public class SkyzDisconnectedScreen extends BaseUIModelScreen<FlowLayout> {

    private final Screen    parent;
    private final Component title;
    private final Component reason;

    private LabelComponent  countdownLbl;
    private ButtonComponent cancelBtn;

    public SkyzDisconnectedScreen(Screen parent, Component title, Component reason) {
        super(FlowLayout.class, Identifier.fromNamespaceAndPath("skyz_client", "disconnected"));
        this.parent = parent;
        this.title  = title  != null ? title  : Component.literal("Disconnected");
        this.reason = reason != null ? reason : Component.literal("Connection lost.");
    }

    @Override
    protected void build(FlowLayout root) {
        FlowLayout card = root.childById(FlowLayout.class, "card");
        if (card != null) card.surface(SkyzSurface.CARD);

        LabelComponent titleLbl = root.childById(LabelComponent.class, "lbl-title");
        if (titleLbl != null) titleLbl.text(title);

        LabelComponent reasonLbl = root.childById(LabelComponent.class, "lbl-reason");
        if (reasonLbl != null) reasonLbl.text(reason);

        ButtonComponent backBtn = root.childById(ButtonComponent.class, "btn-back");
        if (backBtn != null) {
            backBtn.renderer(SkyzButtonRenderer.DEFAULT);
            backBtn.onPress(b -> {
                AutoReconnectManager.INSTANCE.cancel();
                Minecraft.getInstance().setScreen(parent);
            });
        }

        countdownLbl = root.childById(LabelComponent.class, "lbl-countdown");

        cancelBtn = root.childById(ButtonComponent.class, "btn-cancel");
        if (cancelBtn != null) {
            cancelBtn.renderer(SkyzButtonRenderer.DEFAULT);
            cancelBtn.onPress(b -> AutoReconnectManager.INSTANCE.cancel());
        }

        AutoReconnectManager.INSTANCE.start();
    }

    @Override
    public void tick() {
        super.tick();
        AutoReconnectManager mgr = AutoReconnectManager.INSTANCE;
        if (countdownLbl != null) {
            countdownLbl.text(switch (mgr.getState()) {
                case COUNTING_DOWN -> Component.literal(
                    "Reconnecting in " + mgr.getSecondsRemaining() + "s… (attempt "
                    + mgr.getAttemptNumber() + "/" + mgr.getMaxAttempts() + ")")
                    .withStyle(net.minecraft.ChatFormatting.BLUE);
                case FAILED -> Component.literal("Failed to reconnect.").withStyle(net.minecraft.ChatFormatting.RED);
                default     -> Component.literal("");
            });
        }
        if (cancelBtn != null) {
            cancelBtn.active(mgr.getState() == AutoReconnectManager.State.COUNTING_DOWN);
        }
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xCC1A0608, 0xCC050F2A);
    }
}
