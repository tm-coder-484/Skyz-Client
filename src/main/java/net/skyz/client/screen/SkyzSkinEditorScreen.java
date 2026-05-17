package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.skyz.client.SkyzClientMod;
import net.skyz.client.util.SkyzColors;
import net.skyz.client.util.SkyzRenderHelper;
import net.skyz.client.util.SkyzTheme;

import java.util.function.Consumer;

/**
 * Skyz Skin Editor screen — owo-lib edition.
 *
 * <p>Wraps vanilla's per-skin-part visibility toggles ({@link
 * net.minecraft.client.option.GameOptions#setPlayerModelPart} +
 * {@link net.minecraft.client.option.GameOptions#isPlayerModelPartEnabled})
 * with a Skyz-styled two-pane layout: a live 3D preview of the player on
 * the left and the toggle column on the right.
 *
 * <p>Mojang doesn't ship an in-game skin <i>upload</i> flow — that has to
 * happen on minecraft.net — so the "Change Skin at minecraft.net" button
 * opens the user's browser to their profile page. The toggles, arm
 * selector, and any future Skyz-specific settings (cape rotation, layer
 * visibility, etc.) live alongside it.
 *
 * <p>Preview is drawn in {@link #render(DrawContext, int, int, float)}
 * because owo has no 3D-entity component. The XML reserves the left pane
 * via the {@code preview-pane} flow; we read its bounds via
 * {@link FlowLayout#x()} / {@code .y()} / {@code .width()} / {@code .height()}
 * after super.render lays out the tree.
 */
public class SkyzSkinEditorScreen extends BaseUIModelScreen<FlowLayout> {

    private final SkyzTitleScreen parent;

    private FlowLayout previewPane;
    private FlowLayout togglesPane;

    public SkyzSkinEditorScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.of("skyz_client", "skin_editor"));
        this.parent = parent;
    }

    // ─── Build ───────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        wire(root, "btn-back", () -> client.setScreen(parent), SkyzButtonRenderer.NAV_BACK);

        previewPane = root.childById(FlowLayout.class, "preview-pane");
        if (previewPane != null) previewPane.surface(SkyzSurface.CARD);

        togglesPane = root.childById(FlowLayout.class, "toggles-pane");
        if (togglesPane == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] SkinEditor: toggles-pane not found.");
            return;
        }
        togglesPane.surface(SkyzSurface.CARD);

        // Heading.
        togglesPane.child(UIComponents.label(Text.literal("👕  SKIN PARTS"))
                .color(Color.ofArgb(0xFFEEF6FF)));
        FlowLayout divider = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x338CD2FF));
        divider.margins(Insets.bottom(4));
        togglesPane.child(divider);

        // 7 PlayerModelPart toggles. Each row mirrors vanilla
        // SkinOptionsScreen's layout: label on the left, ON/OFF pill on
        // the right, click-anywhere-on-row to flip.
        addPartRow("🎩  Hat",                  PlayerModelPart.HAT);
        addPartRow("🦺  Jacket",                PlayerModelPart.JACKET);
        addPartRow("👕  Left Sleeve",            PlayerModelPart.LEFT_SLEEVE);
        addPartRow("👕  Right Sleeve",           PlayerModelPart.RIGHT_SLEEVE);
        addPartRow("👖  Left Pants Leg",         PlayerModelPart.LEFT_PANTS_LEG);
        addPartRow("👖  Right Pants Leg",        PlayerModelPart.RIGHT_PANTS_LEG);
        addPartRow("🧥  Cape",                   PlayerModelPart.CAPE);

        // Arm selector — uses GameOptions.getMainArm() (a SimpleOption<Arm>).
        FlowLayout gap = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(8));
        togglesPane.child(gap);

        togglesPane.child(UIComponents.label(Text.literal("MAIN HAND"))
                .color(Color.ofArgb(0xCC8CD2FF)));
        FlowLayout div2 = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        div2.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x338CD2FF));
        div2.margins(Insets.bottom(4));
        togglesPane.child(div2);
        togglesPane.child(buildArmRow());

        // External skin upload — minecraft.net is the only legit path.
        FlowLayout gap2 = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(10));
        togglesPane.child(gap2);

        ButtonComponent uploadBtn = UIComponents.button(
                        Text.literal("🌐  CHANGE SKIN AT MINECRAFT.NET"),
                        b -> Util.getOperatingSystem().open("https://www.minecraft.net/profile/skin"))
                .renderer(SkyzButtonRenderer.DEFAULT);
        uploadBtn.horizontalSizing(Sizing.fill(100));
        uploadBtn.verticalSizing(Sizing.fixed(22));
        togglesPane.child(uploadBtn);
    }

    private void wire(FlowLayout root, String id, Runnable action,
                      ButtonComponent.Renderer renderer) {
        ButtonComponent btn = root.childById(ButtonComponent.class, id);
        if (btn == null) {
            SkyzClientMod.LOGGER.warn("[Skyz] SkinEditor: button id '{}' not found.", id);
            return;
        }
        btn.onPress(b -> action.run());
        btn.renderer(renderer);
    }

    /**
     * One toggle row for a {@link PlayerModelPart}. Reads / writes the
     * value through vanilla's {@code GameOptions} so the change persists
     * exactly the way the vanilla skin options screen would persist it.
     */
    private void addPartRow(String label, PlayerModelPart part) {
        var options = MinecraftClient.getInstance().options;
        boolean[] state = { options.isPlayerModelPartEnabled(part) };

        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.padding(Insets.of(2, 2, 8, 8));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x1A091E46));

        row.child(UIComponents.label(Text.literal(label))
                .color(Color.ofArgb(SkyzColors.TEXT_PRIMARY))
                .horizontalSizing(Sizing.expand(100)));

        ButtonComponent pill = makePill(state[0], on -> {
            state[0] = on;
            options.setPlayerModelPart(part, on);
            options.write();
        });
        row.child(pill);
        togglesPane.child(row);
    }

    /** Left/Right arm selector — wraps {@code GameOptions.getMainArm()}. */
    private FlowLayout buildArmRow() {
        var options = MinecraftClient.getInstance().options;

        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        row.padding(Insets.of(2, 2, 8, 8));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface((ctx, comp) -> ctx.fill(comp.x(), comp.y(),
                comp.x() + comp.width(), comp.y() + comp.height(), 0x1A091E46));

        row.child(UIComponents.label(Text.literal("✋  Main Hand"))
                .color(Color.ofArgb(SkyzColors.TEXT_PRIMARY))
                .horizontalSizing(Sizing.expand(100)));

        Arm[] currentArm = { options.getMainArm().getValue() };

        ButtonComponent leftBtn = UIComponents.button(Text.literal("LEFT"), b -> {})
                .renderer(currentArm[0] == Arm.LEFT ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        leftBtn.horizontalSizing(Sizing.fixed(48));
        leftBtn.verticalSizing(Sizing.fixed(18));

        ButtonComponent rightBtn = UIComponents.button(Text.literal("RIGHT"), b -> {})
                .renderer(currentArm[0] == Arm.RIGHT ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        rightBtn.horizontalSizing(Sizing.fixed(48));
        rightBtn.verticalSizing(Sizing.fixed(18));

        leftBtn.onPress(b -> {
            currentArm[0] = Arm.LEFT;
            options.getMainArm().setValue(Arm.LEFT);
            options.write();
            leftBtn.renderer(SkyzButtonRenderer.DEFAULT);
            rightBtn.renderer(SkyzButtonRenderer.NAV_BACK);
        });
        rightBtn.onPress(b -> {
            currentArm[0] = Arm.RIGHT;
            options.getMainArm().setValue(Arm.RIGHT);
            options.write();
            leftBtn.renderer(SkyzButtonRenderer.NAV_BACK);
            rightBtn.renderer(SkyzButtonRenderer.DEFAULT);
        });
        row.child(leftBtn);
        row.child(rightBtn);
        return row;
    }

    /**
     * Same makePill as the HUD editor / settings — button whose label and
     * renderer flip on click. Returns the button so the caller can place it.
     */
    private static ButtonComponent makePill(boolean initial, Consumer<Boolean> onChange) {
        boolean[] state = { initial };
        ButtonComponent btn = UIComponents.button(
                Text.literal(state[0] ? "ON" : "OFF"), b -> {});
        btn.renderer(state[0] ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
        btn.horizontalSizing(Sizing.fixed(40));
        btn.verticalSizing(Sizing.fixed(16));
        btn.onPress(b -> {
            state[0] = !state[0];
            btn.setMessage(Text.literal(state[0] ? "ON" : "OFF"));
            btn.renderer(state[0] ? SkyzButtonRenderer.DEFAULT : SkyzButtonRenderer.NAV_BACK);
            onChange.accept(state[0]);
        });
        return btn;
    }

    // ─── Lifecycle / render ──────────────────────────────────────────────
    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // No-op — render() handles the gradient.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 1) Skyz gradient backdrop.
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,             width, height / 3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height / 3,    width, height / 3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height * 2/3,  width, height / 3, SkyzTheme.BG3, SkyzTheme.BG1);

        // 2) Owo (nav, panes).
        super.render(ctx, mouseX, mouseY, delta);

        // 3) Player preview drawn into the preview-pane bounds. Done after
        //    super.render so we can read the laid-out coordinates of the
        //    pane component.
        if (previewPane != null) drawPlayerPreview(ctx, mouseX, mouseY);
    }

    /**
     * Render the local player into the preview pane via vanilla's
     * {@link InventoryScreen#drawEntity(DrawContext, int, int, int, int, int, float, float, float, net.minecraft.entity.LivingEntity)}.
     * The pane provides x/y/w/h; we centre the entity inside it and rotate
     * it to track the mouse position so users can see all sides.
     */
    private void drawPlayerPreview(DrawContext ctx, int mouseX, int mouseY) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            // No world loaded — show "log in to view skin" hint.
            int cx = previewPane.x() + previewPane.width() / 2;
            int cy = previewPane.y() + previewPane.height() / 2;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Join a world to preview your skin",
                    cx, cy - 4, SkyzColors.TEXT_MUTED);
            return;
        }

        int x1 = previewPane.x() + 10;
        int y1 = previewPane.y() + 10;
        int x2 = previewPane.x() + previewPane.width() - 10;
        int y2 = previewPane.y() + previewPane.height() - 30;   // leave room for hint label
        int size = Math.min(x2 - x1, (y2 - y1) / 2);

        int centerX = (x1 + x2) / 2;
        int floorY  = y2;
        int scale   = Math.max(40, size);

        // Mouse-driven rotation — same feel as vanilla inventory's
        // hover-to-rotate.
        float yaw   = (centerX - mouseX) / 2.0f;
        float pitch = (floorY  - mouseY) / 8.0f;

        InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, scale, 0.0625f, mouseX, mouseY, player);
        // (The 4-arg-after-scale version takes (mouseX, mouseY, entity); the
        // 3-arg version takes (yaw, mouseX, mouseY, entity). We use vanilla's
        // form here — 1.21.11 signature is
        // drawEntity(ctx, x1, y1, x2, y2, scale, u, v, yaw, entity).)
        // Note: yaw / pitch ignored above because we pass the mouseX/Y
        // form which computes them internally.
        @SuppressWarnings("unused") float _suppress = yaw + pitch;
    }
}
