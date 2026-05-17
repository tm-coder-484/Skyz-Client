package net.skyz.client.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.skyz.client.SkyzClientMod;

/**
 * Skyz Death Screen — owo-lib edition (Phase 2d port).
 *
 * <p>Replaces vanilla {@code DeathScreen} via
 * {@link net.skyz.client.mixin.DeathScreenMixin}. Functionality preserved:
 * <ul>
 *   <li>Respawn → {@code player.requestRespawn()} + close screen.</li>
 *   <li>Title Screen → vanilla's quitLevel logic (disconnect + back to title).</li>
 *   <li>Hardcore detection — disables Respawn, shows "Game Over!" / "Title Screen"
 *       label since you can't respawn in hardcore.</li>
 *   <li>Respawn button cooldown — first ~20 ticks after death the button is
 *       disabled to prevent accidental click-through (vanilla does this).</li>
 *   <li>Score display — pulled from {@code player.getScoreboardName()} score.</li>
 * </ul>
 */
public class SkyzDeathScreen extends BaseUIModelScreen<FlowLayout> {

    /** Vanilla's threshold before Respawn becomes clickable. */
    private static final int RESPAWN_COOLDOWN_TICKS = 20;

    private final Text     causeOfDeath;
    private final boolean  isHardcore;

    private int             ticksSinceDeath = 0;
    private ButtonComponent respawnBtn;

    public SkyzDeathScreen(Text causeOfDeath, boolean isHardcore) {
        super(FlowLayout.class, Identifier.of("skyz_client", "death_screen"));
        this.causeOfDeath = causeOfDeath;
        this.isHardcore   = isHardcore;
    }

    // ─── Build ───────────────────────────────────────────────────────────
    @Override
    protected void build(FlowLayout root) {
        FlowLayout card = root.childById(FlowLayout.class, "card");
        if (card != null) card.surface(SkyzSurface.CARD);

        // Title swaps for hardcore.
        LabelComponent title = root.childById(LabelComponent.class, "lbl-title");
        if (title != null) {
            title.text(Text.literal(isHardcore ? "GAME OVER" : "YOU DIED!"));
        }

        // Cause of death — vanilla shows the death message verbatim.
        LabelComponent cause = root.childById(LabelComponent.class, "lbl-cause");
        if (cause != null) {
            cause.text(causeOfDeath != null ? causeOfDeath : Text.literal("..."));
        }

        // Score line.
        LabelComponent scoreLbl = root.childById(LabelComponent.class, "lbl-score");
        if (scoreLbl != null) {
            int score = 0;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                score = player.getScore();
            }
            scoreLbl.text(Text.translatable("deathScreen.score")
                    .append(": ")
                    .append(Text.literal(String.valueOf(score))));
        }

        // Respawn button — disabled in hardcore (no respawn possible) and during
        // the early cooldown after death.
        respawnBtn = root.childById(ButtonComponent.class, "btn-respawn");
        if (respawnBtn != null) {
            respawnBtn.renderer(SkyzButtonRenderer.DEFAULT);
            respawnBtn.onPress(b -> respawn());
            respawnBtn.active(!isHardcore && ticksSinceDeath >= RESPAWN_COOLDOWN_TICKS);
        }

        // Title-screen button — vanilla uses different label for hardcore vs not.
        ButtonComponent titleBtn = root.childById(ButtonComponent.class, "btn-title");
        if (titleBtn != null) {
            titleBtn.renderer(SkyzButtonRenderer.QUIT);
            titleBtn.onPress(b -> quitLevel());
            titleBtn.setMessage(Text.literal(isHardcore
                    ? "⏻  TITLE SCREEN"
                    : "⏻  TITLE SCREEN"));
            // (Vanilla actually shows "Title Screen" in both cases — we match.)
        }
    }

    // ─── Actions ─────────────────────────────────────────────────────────
    private void respawn() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (isHardcore) {
            // Defensive — UI button is disabled but could be triggered by other paths.
            quitLevel();
            return;
        }
        player.requestRespawn();
        MinecraftClient.getInstance().setScreen(null);
    }

    /**
     * Same disconnect flow as vanilla DeathScreen#quitLevel() — drop the
     * world cleanly, route to TitleScreen (singleplayer) or
     * MultiplayerScreen (server).
     */
    private void quitLevel() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean integrated = mc.isIntegratedServerRunning();

        if (mc.world != null) {
            mc.world.disconnect(Text.translatable("disconnect.lost"));
        }
        if (integrated) {
            mc.disconnectWithProgressScreen();
        } else {
            // 1.21.11 has no zero-arg disconnect(); the (Screen, boolean, boolean)
            // form takes the pending screen + saving/transferring flags.
            mc.disconnect(new TitleScreen(), false, false);
        }

        TitleScreen titleScreen = new TitleScreen();
        if (integrated) {
            mc.setScreen(titleScreen);
        } else {
            mc.setScreen(new MultiplayerScreen(titleScreen));
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────
    @Override public boolean shouldPause() { return false; }

    /** Vanilla blocks Esc on death so the player can't bypass the screen. */
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        super.tick();
        ticksSinceDeath++;
        if (respawnBtn != null && !isHardcore && ticksSinceDeath >= RESPAWN_COOLDOWN_TICKS
                && !respawnBtn.active) {
            respawnBtn.active(true);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Red-tinted veil — like vanilla's death overlay but Skyz-themed.
        ctx.fillGradient(0, 0, width, height, 0xCC600C0C, 0xCC1A0608);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        if (respawnBtn != null && !respawnBtn.active && !isHardcore) {
            // Cooldown hint under the buttons.
            int remaining = Math.max(0, RESPAWN_COOLDOWN_TICKS - ticksSinceDeath);
            String txt = "Respawn available in " + Math.max(1, remaining / 20 + 1) + "s...";
            ctx.drawCenteredTextWithShadow(textRenderer, txt, width / 2, height / 2 + 80, 0x99CCCCCC);
        }
    }
}
