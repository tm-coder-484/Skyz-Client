package net.skyz.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.skyz.client.util.SkyzClientState;
import net.skyz.client.util.SkyzHudState;
import net.skyz.client.util.SkyzMinimapState;
import net.skyz.client.util.SkyzRenderHelper;

import java.util.List;
import java.util.Optional;

/**
 * Per-element renderer for the Skyz in-game HUD.
 *
 * <p>This used to live inside {@code InGameHudMixin}. It was extracted
 * (Phase 2c) to:
 * <ul>
 *   <li>keep the mixin small and focused on the {@code @Inject} hook,</li>
 *   <li>let the renderer be unit-styled / tweaked without recompiling the
 *       whole mixin class,</li>
 *   <li>polish the panel/bar visuals to match the rest of the owo-ported
 *       screens (deeper gradients, top accent stripes, color-coded values,
 *       primary/secondary typography hierarchy).</li>
 * </ul>
 *
 * <p>The minimap's column-sample cache and {@link net.minecraft.client.renderer.texture.DynamicTexture}
 * came along with the move — those were the heaviest part of the mixin and
 * are entirely scoped to HUD rendering. ESP / hack renderers, autoTotem,
 * and the {@code @Inject} hook itself stay in {@code InGameHudMixin}.
 *
 * <p>Each element gets a "Skyz panel" via
 * {@link SkyzRenderHelper#drawSkyzHudPanel} which is a gradient body with a
 * top sheen, soft border and 1-px accent stripe — the colour of the stripe
 * is element-specific (green/yellow/red for status, Skyz blue for neutral).
 * Bars use {@link SkyzRenderHelper#drawSkyzBar} for rounded gradient fills.
 */
public final class SkyzHudRenderer {

    private SkyzHudRenderer() {}

    // ─── Skyz palette references ─────────────────────────────────────────
    // Subtle blue accent stripe — used on most "neutral" panels.
    private static final int ACCENT_BLUE   = 0xFF8CD2FF;
    private static final int ACCENT_GREEN  = 0xFF4CFA87;
    private static final int ACCENT_YELLOW = 0xFFFACC4C;
    private static final int ACCENT_RED    = 0xFFFA6C4C;
    private static final int ACCENT_TNT    = 0xFFFF6644;

    private static final int TEXT_PRIMARY  = 0xFFEAF4FF;
    private static final int TEXT_SECONDARY= 0xCCB0D8FF;
    private static final int TEXT_MUTED    = 0x8C8CD2FF;

    // ─── Minimap state (moved from the mixin) ────────────────────────────
    private static com.mojang.blaze3d.platform.NativeImage minimapImage = null;
    private static net.minecraft.client.renderer.texture.DynamicTexture minimapTexture = null;
    private static final net.minecraft.resources.Identifier MINIMAP_TEX_ID =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("skyz_client", "minimap_dynamic");
    private static int     minimapTexW   = -1;
    private static int     minimapTexH   = -1;
    private static boolean minimapTexDirty = true;

    private static int[]   colSamples    = null;
    private static int     colsSide      = -1;     // 2*range+1
    private static int     colsRange     = -1;
    private static int     colsOriginX   = Integer.MIN_VALUE;
    private static int     colsOriginZ   = Integer.MIN_VALUE;
    private static int     colsCachedY   = Integer.MIN_VALUE;
    private static boolean colsCaveMode  = false;
    private static boolean colsShowLeaves= false;
    private static int     colsCursor    = 0;

    private static final int UNCOMPUTED = 0x00000000;
    private static final int COLUMN_BUDGET_PER_FRAME = 1024;

    // ─── Entry point ─────────────────────────────────────────────────────
    /**
     * Render every enabled HUD element. The mixin's @Inject calls this once
     * per frame after the vanilla HUD has finished.
     *
     * @param sessionStartMs time the player entered this session, used by the
     *                       Session Timer element
     */
    public static void render(GuiGraphicsExtractor ctx, Minecraft client,
                              int W, int H, long sessionStartMs) {
        for (SkyzHudState.HudElementState el : SkyzHudState.ELEMENTS) {
            if (!el.enabled) continue;
            renderElement(ctx, client, el, W, H, sessionStartMs);
        }
    }

    // ─── Element dispatcher ──────────────────────────────────────────────
    private static void renderElement(GuiGraphicsExtractor ctx, Minecraft client,
                                      SkyzHudState.HudElementState el,
                                      int W, int H, long sessionStartMs) {
        // Special-cased elements with bespoke rendering.
        switch (el.name) {
            case "Keystrokes"       -> { renderKeystrokes(ctx, client, el);   return; }
            case "Potion Effects"   -> { renderPotions(ctx, client, el);      return; }
            case "Custom Crosshair" -> { renderCrosshair(ctx, el);             return; }
            case "Enemy Info"       -> { renderEnemyInfo(ctx, client, el);     return; }
            case "Nearby Players"   -> { renderNearbyPlayers(ctx, client, el); return; }
            case "TNT Timer"        -> { renderTntTimer(ctx, client, el);      return; }
            case "Compass"          -> { renderCompass(ctx, client, el);       return; }
            case "Minimap"          -> { renderMinimap(ctx, client, el);       return; }
        }

        // Standard text/bar panels — share the same panel style + typography.
        Font tr = client.font;
        switch (el.name) {
            case "FPS Counter"      -> renderFps(ctx, client, el, tr);
            case "CPS Counter"      -> renderCps(ctx, el, tr);
            case "Coordinates"      -> renderCoordinates(ctx, client, el, tr);
            case "Biome Display"    -> renderBiome(ctx, client, el, tr);
            case "Speed Meter"      -> renderSpeed(ctx, client, el, tr);
            case "Ping Display"     -> renderPing(ctx, client, el, tr);
            case "Reach Display"    -> renderReach(ctx, client, el, tr);
            case "Memory Usage"     -> renderMemory(ctx, el, tr);
            case "Entity Count"     -> renderEntityCount(ctx, client, el, tr);
            case "Session Timer"    -> renderSessionTimer(ctx, el, tr, sessionStartMs);
            case "Block Info"       -> renderBlockInfo(ctx, client, el, tr);
            case "Health Bar"       -> renderHealthBar(ctx, client, el, tr);
            case "Hunger Bar"       -> renderHungerBar(ctx, client, el, tr);
            case "Saturation Bar"   -> renderSaturationBar(ctx, client, el, tr);
            case "Armor Bar"        -> renderArmorBar(ctx, client, el, tr);
            case "Tool Durability"  -> renderToolDurability(ctx, client, el, tr);
            case "Helmet Durability"     -> renderArmorSlot(ctx, client, el, net.minecraft.world.entity.EquipmentSlot.HEAD,  "⛑", tr);
            case "Chestplate Durability" -> renderArmorSlot(ctx, client, el, net.minecraft.world.entity.EquipmentSlot.CHEST, "🧲", tr);
            case "Leggings Durability"   -> renderArmorSlot(ctx, client, el, net.minecraft.world.entity.EquipmentSlot.LEGS,  "👖", tr);
            case "Boots Durability"      -> renderArmorSlot(ctx, client, el, net.minecraft.world.entity.EquipmentSlot.FEET,  "👢", tr);
            case "Attack Cooldown"  -> renderAttackCooldown(ctx, client, el);
            case "Combo Counter"    -> renderComboCounter(ctx, el, tr);
            case "Totem Pop Counter"-> renderTotemPops(ctx, el, tr);
            default -> {
                // Fallback: bare panel + "icon name" — matches old behaviour
                SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_BLUE);
                int ty = el.y + (el.h - 8) / 2;
                ctx.text(tr, el.icon + " " + el.name, el.x + 4, ty, TEXT_MUTED, true);
            }
        }
    }

    // ─── Standard text panels ────────────────────────────────────────────
    /**
     * Two-tone text element: primary value left-aligned in {@code valueColor},
     * secondary suffix in muted blue. Used by FPS / CPS / Speed / Ping and
     * other "single number + label" elements.
     */
    private static void drawValuePanel(GuiGraphicsExtractor ctx, SkyzHudState.HudElementState el,
                                       Font tr,
                                       String value, int valueColor,
                                       String suffix,
                                       int accent) {
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, accent);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, value, el.x + 6, ty, valueColor, true);
        if (suffix != null) {
            int valW = tr.width(value);
            ctx.text(tr, suffix, el.x + 6 + valW + 4, ty, TEXT_MUTED, true);
        }
    }

    private static void renderFps(GuiGraphicsExtractor ctx, Minecraft client,
                                   SkyzHudState.HudElementState el, Font tr) {
        int f = client.getFps();
        int col = f >= 60 ? ACCENT_GREEN : f >= 30 ? ACCENT_YELLOW : ACCENT_RED;
        drawValuePanel(ctx, el, tr, String.valueOf(f), col, "FPS", col);
    }

    private static void renderCps(GuiGraphicsExtractor ctx, SkyzHudState.HudElementState el,
                                   Font tr) {
        drawValuePanel(ctx, el, tr, String.valueOf(SkyzClientState.cps),
                TEXT_PRIMARY, "CPS", ACCENT_BLUE);
    }

    private static void renderCoordinates(GuiGraphicsExtractor ctx, Minecraft client,
                                           SkyzHudState.HudElementState el, Font tr) {
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_BLUE);
        var p = client.player;
        if (p == null) return;
        int ty = el.y + (el.h - 8) / 2;
        // "X" labels muted, numeric values bright.
        int x = el.x + 6;
        x = drawLabelValue(ctx, tr, "X", String.format("%.0f", p.getX()), x, ty);
        x = drawLabelValue(ctx, tr, "Y", String.format("%.0f", p.getY()), x + 4, ty);
            drawLabelValue(ctx, tr, "Z", String.format("%.0f", p.getZ()), x + 4, ty);
    }

    private static int drawLabelValue(GuiGraphicsExtractor ctx, Font tr,
                                       String label, String value, int x, int y) {
        ctx.text(tr, label, x, y, TEXT_MUTED, true);
        int lw = tr.width(label) + 2;
        ctx.text(tr, value, x + lw, y, TEXT_PRIMARY, true);
        return x + lw + tr.width(value);
    }

    private static void renderBiome(GuiGraphicsExtractor ctx, Minecraft client,
                                     SkyzHudState.HudElementState el, Font tr) {
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_GREEN);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "🌿", el.x + 6, ty, ACCENT_GREEN, true);
        ctx.text(tr, getBiomeName(client), el.x + 22, ty, TEXT_PRIMARY, true);
    }

    private static void renderSpeed(GuiGraphicsExtractor ctx, Minecraft client,
                                     SkyzHudState.HudElementState el, Font tr) {
        var p = client.player;
        if (p == null) return;
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        double spd = Math.sqrt(dx * dx + dz * dz) * 20;
        int col = spd > 5.6 ? ACCENT_GREEN : spd > 4.3 ? ACCENT_YELLOW : TEXT_PRIMARY;
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, col);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "⚡", el.x + 6, ty, ACCENT_YELLOW, true);
        ctx.text(tr, String.format("%.1f", spd), el.x + 18, ty, col, true);
        ctx.text(tr, "b/s", el.x + 18 + tr.width(String.format("%.1f", spd)) + 4,
                ty, TEXT_MUTED, true);
    }

    private static void renderPing(GuiGraphicsExtractor ctx, Minecraft client,
                                    SkyzHudState.HudElementState el, Font tr) {
        int ping = -1;
        boolean lan = client.isLocalServer();
        if (lan) {
            ping = 0;
        } else {
            try {
                var net = client.getConnection();
                if (net != null && client.player != null) {
                    String name = client.player.getName().getString();
                    for (var entry : net.getOnlinePlayers()) {
                        if (entry.getProfile().name().equals(name)) {
                            ping = entry.getLatency();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        String pingStr = lan ? "LAN" : ping < 0 ? "???" : String.valueOf(ping);
        int col = lan ? ACCENT_GREEN
                : ping < 0 ? 0xFF888888
                : ping < 80 ? ACCENT_GREEN
                : ping < 150 ? ACCENT_YELLOW
                : ACCENT_RED;
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, col);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "📡", el.x + 6, ty, ACCENT_BLUE, true);
        ctx.text(tr, pingStr, el.x + 22, ty, col, true);
        if (!lan && ping >= 0) {
            int valW = tr.width(pingStr);
            ctx.text(tr, "ms", el.x + 22 + valW + 3, ty, TEXT_MUTED, true);
        }
    }

    private static void renderReach(GuiGraphicsExtractor ctx, Minecraft client,
                                     SkyzHudState.HudElementState el, Font tr) {
        boolean targeting = client.hitResult != null
                && client.hitResult.getType() == HitResult.Type.ENTITY;
        int col = targeting ? ACCENT_GREEN : TEXT_PRIMARY;
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, col);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "🎯", el.x + 6, ty, ACCENT_BLUE, true);
        ctx.text(tr, "3.0", el.x + 22, ty, col, true);
        ctx.text(tr, "m", el.x + 22 + tr.width("3.0") + 3, ty, TEXT_MUTED, true);
    }

    private static void renderMemory(GuiGraphicsExtractor ctx, SkyzHudState.HudElementState el,
                                      Font tr) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        long max  = rt.maxMemory() / 1048576;
        int pct = max > 0 ? (int) (used * 100 / max) : 0;
        int col = pct < 70 ? ACCENT_GREEN : pct < 85 ? ACCENT_YELLOW : ACCENT_RED;
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, col);
        // Top label
        ctx.text(tr, "🖥 " + used + " MB", el.x + 4, el.y + 2, TEXT_PRIMARY, true);
        // Bar at the bottom
        SkyzRenderHelper.drawSkyzBar(ctx, el.x + 4, el.y + el.h - 5, el.w - 8, 3,
                pct, col, (col & 0x00FFFFFF) | 0x99000000);
    }

    private static void renderEntityCount(GuiGraphicsExtractor ctx, Minecraft client,
                                            SkyzHudState.HudElementState el, Font tr) {
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_BLUE);
        int count = 0;
        if (client.level != null) {
            for (Entity ignored : client.level.entitiesForRendering()) count++;
        }
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "👾", el.x + 6, ty, ACCENT_YELLOW, true);
        ctx.text(tr, String.valueOf(count), el.x + 22, ty, TEXT_PRIMARY, true);
        ctx.text(tr, "entities", el.x + 22 + tr.width(String.valueOf(count)) + 4,
                ty, TEXT_MUTED, true);
    }

    private static void renderSessionTimer(GuiGraphicsExtractor ctx, SkyzHudState.HudElementState el,
                                            Font tr, long sessionStartMs) {
        long secs = (System.currentTimeMillis() - sessionStartMs) / 1000;
        String t = String.format("%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_BLUE);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "⏱", el.x + 6, ty, ACCENT_BLUE, true);
        ctx.text(tr, t, el.x + 18, ty, TEXT_PRIMARY, true);
    }

    private static void renderBlockInfo(GuiGraphicsExtractor ctx, Minecraft client,
                                         SkyzHudState.HudElementState el, Font tr) {
        String info = "Air";
        if (client.hitResult != null
                && client.hitResult.getType() == HitResult.Type.BLOCK
                && client.level != null) {
            var bhr = (net.minecraft.world.phys.BlockHitResult) client.hitResult;
            var state = client.level.getBlockState(bhr.getBlockPos());
            if (!state.isAir()) {
                String raw = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace('_', ' ');
                info = raw.isEmpty() ? "?" : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            }
        }
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_BLUE);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "🧱", el.x + 6, ty, ACCENT_YELLOW, true);
        ctx.text(tr, info, el.x + 22, ty, TEXT_PRIMARY, true);
    }

    // ─── Bars ────────────────────────────────────────────────────────────
    /**
     * Renders an icon-and-value header line at the top of {@code el} and a
     * {@link SkyzRenderHelper#drawSkyzBar} along the bottom — used by
     * Health / Hunger / Saturation / Armor / Tool / Memory.
     */
    /**
     * Renders an icon-and-value header line at the top of {@code el} and a
     * progress bar along the bottom — used by Health / Hunger / Saturation
     * / Armor / Tool / Memory.
     *
     * <p>For panels with {@code el.h < 18} the icon row + bar can't fit
     * without overlap (icon is ~10px tall starting at y+3, bar wants
     * y+h-5..y+h-2). In that case we skip the bar and render the icon
     * vertically centred — matches how the Skyz value-panel elements
     * (FPS, CPS, etc.) look. Older configs that saved h=14 for armor
     * durability were rendering as a black smear because of this overlap;
     * this graceful-degrade fixes the "I enabled Helmet Durability and
     * nothing shows" report without forcing a Reset.
     */
    private static void drawIconBarPanel(GuiGraphicsExtractor ctx, Font tr,
                                          SkyzHudState.HudElementState el,
                                          String icon, int iconColor,
                                          String label, int labelColor,
                                          int pct, int barTop, int barBot,
                                          int accent) {
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, accent);

        if (el.h < 18) {
            // Compact: single-line icon + label, no bar. Vertically centred.
            int ty = el.y + (el.h - 8) / 2;
            ctx.text(tr, icon, el.x + 4, ty, iconColor, true);
            ctx.text(tr, label, el.x + 16, ty, labelColor, true);
            return;
        }

        ctx.text(tr, icon, el.x + 4, el.y + 3, iconColor, true);
        ctx.text(tr, label, el.x + 16, el.y + 3, labelColor, true);
        // Bar pinned to bottom edge with a 4px inset from sides.
        int by = el.y + el.h - 5;
        SkyzRenderHelper.drawSkyzBar(ctx, el.x + 4, by, el.w - 8, 3, pct, barTop, barBot);
    }

    private static void renderHealthBar(GuiGraphicsExtractor ctx, Minecraft client,
                                         SkyzHudState.HudElementState el, Font tr) {
        var p = client.player;
        if (p == null) return;
        float hp = p.getHealth(), maxHp = p.getMaxHealth();
        int pct = maxHp > 0 ? (int) (hp / maxHp * 100) : 0;
        drawIconBarPanel(ctx, tr, el,
                "❤", 0xFFFF6464,
                (int) hp + "/" + (int) maxHp, TEXT_PRIMARY,
                pct, 0xFFE03030, 0xFF8C1818, ACCENT_RED);
    }

    private static void renderHungerBar(GuiGraphicsExtractor ctx, Minecraft client,
                                         SkyzHudState.HudElementState el, Font tr) {
        var p = client.player;
        if (p == null) return;
        int food = p.getFoodData().getFoodLevel();
        drawIconBarPanel(ctx, tr, el,
                "🍖", 0xFFFFAA66,
                food + "/20", TEXT_PRIMARY,
                food * 5, 0xFFE08030, 0xFF8C4818, ACCENT_YELLOW);
    }

    private static void renderSaturationBar(GuiGraphicsExtractor ctx, Minecraft client,
                                             SkyzHudState.HudElementState el, Font tr) {
        var p = client.player;
        if (p == null) return;
        float sat = p.getFoodData().getSaturationLevel();
        drawIconBarPanel(ctx, tr, el,
                "✨", 0xFFFFE866,
                String.format("%.1f", sat), TEXT_PRIMARY,
                (int) (sat / 20 * 100), 0xFFFFD700, 0xFF8C7400, ACCENT_YELLOW);
    }

    private static void renderArmorBar(GuiGraphicsExtractor ctx, Minecraft client,
                                        SkyzHudState.HudElementState el, Font tr) {
        var p = client.player;
        if (p == null) return;
        int armor = p.getArmorValue();
        drawIconBarPanel(ctx, tr, el,
                "🛡", 0xFFCCCCCC,
                armor + "/20", TEXT_PRIMARY,
                armor * 5, 0xFFCCCCCC, 0xFF666666, 0xFFAAAAAA);
    }

    private static void renderToolDurability(GuiGraphicsExtractor ctx, Minecraft client,
                                              SkyzHudState.HudElementState el, Font tr) {
        var p = client.player;
        if (p == null) return;
        ItemStack tool = p.getMainHandItem();
        if (tool.isEmpty() || !tool.isDamageableItem()) {
            SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, 0xFF555555);
            int ty = el.y + (el.h - 8) / 2;
            ctx.text(tr, "⛏ No tool", el.x + 6, ty, TEXT_MUTED, true);
            return;
        }
        int dur = tool.getMaxDamage() - tool.getDamageValue(), max = tool.getMaxDamage();
        int pct = max > 0 ? dur * 100 / max : 0;
        int col = pct > 60 ? ACCENT_GREEN : pct > 30 ? ACCENT_YELLOW : ACCENT_RED;
        drawIconBarPanel(ctx, tr, el,
                "⛏", col,
                dur + "/" + max, TEXT_PRIMARY,
                pct, col, (col & 0x00FFFFFF) | 0x99000000, col);
    }

    private static void renderArmorSlot(GuiGraphicsExtractor ctx, Minecraft client,
                                         SkyzHudState.HudElementState el,
                                         net.minecraft.world.entity.EquipmentSlot slot,
                                         String icon, Font tr) {
        var p = client.player;
        if (p == null) return;
        ItemStack s = p.getItemBySlot(slot);
        if (s.isEmpty()) {
            SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, 0xFF555555);
            int ty = el.y + (el.h - 8) / 2;
            ctx.text(tr, icon + " None", el.x + 6, ty, TEXT_MUTED, true);
            return;
        }
        if (s.isDamageableItem()) {
            int dur = s.getMaxDamage() - s.getDamageValue(), max = s.getMaxDamage();
            int pct = max > 0 ? dur * 100 / max : 0;
            int col = pct > 60 ? ACCENT_GREEN : pct > 30 ? ACCENT_YELLOW : ACCENT_RED;
            drawIconBarPanel(ctx, tr, el,
                    icon, col,
                    dur + "/" + max, TEXT_PRIMARY,
                    pct, col, (col & 0x00FFFFFF) | 0x99000000, col);
        } else {
            SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_BLUE);
            int ty = el.y + (el.h - 8) / 2;
            ctx.text(tr, icon + " " + s.getHoverName().getString(),
                    el.x + 6, ty, TEXT_PRIMARY, true);
        }
    }

    private static void renderAttackCooldown(GuiGraphicsExtractor ctx, Minecraft client,
                                              SkyzHudState.HudElementState el) {
        var p = client.player;
        if (p == null) return;
        float cooldown = p.getAttackStrengthScale(0f);
        int pct = (int) (cooldown * 100);
        SkyzRenderHelper.drawSkyzBar(ctx, el.x, el.y, el.w, el.h, pct,
                pct >= 100 ? 0xFFFFAA00 : 0xFF884400,
                pct >= 100 ? 0xFFCC7800 : 0xFF552200);
    }

    private static void renderComboCounter(GuiGraphicsExtractor ctx, SkyzHudState.HudElementState el,
                                            Font tr) {
        int combo = SkyzClientState.comboCount;
        if (combo <= 0) return;
        int col = combo >= 10 ? ACCENT_RED : combo >= 5 ? ACCENT_YELLOW : 0xFFFFFFFF;
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, col);
        String big = String.valueOf(combo);
        // Big number centered, "combo" muted underneath if there's vertical room.
        if (el.h >= 22) {
            ctx.centeredText(tr, big, el.x + el.w / 2, el.y + 4, col);
            ctx.centeredText(tr, "combo", el.x + el.w / 2, el.y + el.h - 11, TEXT_MUTED);
        } else {
            ctx.centeredText(tr, big + " combo",
                    el.x + el.w / 2, el.y + (el.h - 8) / 2, col);
        }
    }

    private static void renderTotemPops(GuiGraphicsExtractor ctx, SkyzHudState.HudElementState el,
                                         Font tr) {
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_GREEN);
        int ty = el.y + (el.h - 8) / 2;
        ctx.text(tr, "🌼", el.x + 6, ty, ACCENT_YELLOW, true);
        ctx.text(tr, "Totems", el.x + 22, ty, TEXT_PRIMARY, true);
        String n = String.valueOf(SkyzClientState.totemPops);
        ctx.text(tr, n, el.x + el.w - 4 - tr.width(n), ty, ACCENT_GREEN, true);
    }

    // ─── TNT timer ───────────────────────────────────────────────────────
    private static void renderTntTimer(GuiGraphicsExtractor ctx, Minecraft client,
                                        SkyzHudState.HudElementState el) {
        if (client.level == null || client.player == null) return;
        List<PrimedTnt> tnts = client.level.getEntitiesOfClass(PrimedTnt.class,
                client.player.getBoundingBox().inflate(100), e -> true);
        if (tnts.isEmpty()) return;

        PrimedTnt closest = null;
        double minDist = Double.MAX_VALUE;
        for (PrimedTnt t : tnts) {
            double d = t.distanceToSqr(client.player);
            if (d < minDist) { minDist = d; closest = t; }
        }

        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_TNT);
        Font tr = client.font;
        int ty = el.y + (el.h - 8) / 2;

        ctx.text(tr, "💥", el.x + 6, ty, ACCENT_TNT, true);
        if (closest != null) {
            float fuse = closest.getFuse() / 20f;
            String label = tnts.size() + " TNT · " + String.format("%.1fs", fuse);
            int col = fuse < 1f ? 0xFFFF4444 : 0xFFFF9944;
            ctx.text(tr, label, el.x + 22, ty, col, true);
        } else {
            ctx.text(tr, tnts.size() + " TNT", el.x + 22, ty, ACCENT_YELLOW, true);
        }
    }

    // ─── Enemy info ──────────────────────────────────────────────────────
    private static void renderEnemyInfo(GuiGraphicsExtractor ctx, Minecraft client,
                                         SkyzHudState.HudElementState el) {
        if (!(client.hitResult instanceof EntityHitResult ehr)) return;
        Entity target = ehr.getEntity();
        if (!(target instanceof LivingEntity living)) return;

        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_RED);
        Font tr = client.font;
        int y = el.y + 4;

        ctx.text(tr, "💀 " + living.getDisplayName().getString(),
                el.x + 6, y, 0xFFFF8888, true); y += 12;

        float hp = living.getHealth(), maxHp = living.getMaxHealth();
        int pct = maxHp > 0 ? (int) (hp / maxHp * 100) : 0;
        SkyzRenderHelper.drawSkyzBar(ctx, el.x + 6, y, el.w - 12, 6, pct,
                0xFFE03030, 0xFF8C1818);
        ctx.text(tr, String.format("%.0f / %.0f HP", hp, maxHp),
                el.x + 6, y + 8, 0xFFFFAAAA, true); y += 22;

        int armor = living.getArmorValue();
        ctx.text(tr, "🛡 " + armor + " armor", el.x + 6, y, 0xFFCCCCCC, true); y += 12;

        if (living instanceof Player pl) {
            ItemStack held = pl.getMainHandItem();
            if (!held.isEmpty()) {
                ctx.text(tr, "⚔ " + held.getHoverName().getString(),
                        el.x + 6, y, 0xFFFFDD88, true);
            }
        }
    }

    // ─── Nearby players ──────────────────────────────────────────────────
    private static void renderNearbyPlayers(GuiGraphicsExtractor ctx, Minecraft client,
                                              SkyzHudState.HudElementState el) {
        if (client.level == null || client.player == null) return;
        Font tr = client.font;

        /*
         * First pass: collect EVERY player within range. We need the full
         * count for the header label ("Nearby (12)") even when only a
         * subset fits on the panel — earlier the loop broke at 6, which
         * made the header lie ("Nearby (6)" with 12 actually around).
         *
         * After the pass, sort by distance so the closest players are
         * shown first when we cap the display list, and the rest are
         * surfaced as a "+N more" footer line.
         */
        java.util.List<Player> nearby = new java.util.ArrayList<>();
        float nearbyMaxDist = Math.max(4, Math.min(32,
                net.skyz.client.util.SkyzClientState.espRange)) * 16f;
        for (Player p : client.level.players()) {
            if (p == client.player) continue;
            if (p.distanceTo(client.player) > nearbyMaxDist) continue;
            nearby.add(p);
        }
        nearby.sort((a, b) -> Float.compare(
                a.distanceTo(client.player),
                b.distanceTo(client.player)));

        int totalCount = nearby.size();

        // Empty state: render a single-line compact panel ("👥 None nearby")
        // at fixed 18-tall regardless of the configured el.h. The HUD editor
        // still uses el.w × el.h for hit detection so the user can drag it
        // freely.
        if (totalCount == 0) {
            int compactH = 18;
            SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, compactH, ACCENT_BLUE);
            int ty = el.y + (compactH - 8) / 2;
            ctx.text(tr, "👥 None nearby", el.x + 6, ty, TEXT_MUTED, true);
            return;
        }

        // Decide how many name rows actually fit. We want at least 1 row,
        // and never more than `MAX_DISPLAYED` so a 200-player crowd doesn't
        // push the panel halfway down the screen. The header line eats 12px,
        // each name row is 10px, plus 4px top padding + 2px bottom slack +
        // 10px footer if we hide some.
        final int MAX_DISPLAYED = 8;
        int rowsFitInPanel = Math.max(1, (el.h - 4 - 12 - 2) / 10);
        int displayCount   = Math.min(totalCount, Math.min(MAX_DISPLAYED, rowsFitInPanel));
        boolean hasMore    = totalCount > displayCount;

        // Populated: full-size panel with header + name rows + optional
        // "+N more" footer. Sized to actual content so the panel doesn't
        // claim more vertical space than it needs.
        int footerH  = hasMore ? 10 : 0;
        int neededH  = 4 + 12 + displayCount * 10 + footerH + 2;
        int drawH    = Math.min(el.h, neededH);
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, drawH, ACCENT_BLUE);

        int y = el.y + 4;
        ctx.text(tr, "👥 Nearby (" + totalCount + ")",
                el.x + 6, y, ACCENT_BLUE, true); y += 12;
        for (int i = 0; i < displayCount; i++) {
            Player p = nearby.get(i);
            String name = p.getName().getString();
            String distStr = Math.round(p.distanceTo(client.player)) + "m";
            ctx.text(tr, name, el.x + 6, y, TEXT_PRIMARY, true);
            ctx.text(tr, distStr,
                    el.x + el.w - 4 - tr.width(distStr), y, TEXT_MUTED, true);
            y += 10;
        }
        if (hasMore) {
            int hidden = totalCount - displayCount;
            ctx.text(tr, "+ " + hidden + " more",
                    el.x + 6, y, TEXT_MUTED, true);
        }
    }

    // ─── Compass ─────────────────────────────────────────────────────────
    private static void renderCompass(GuiGraphicsExtractor ctx, Minecraft client,
                                       SkyzHudState.HudElementState el) {
        SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, el.y, el.w, el.h, ACCENT_BLUE);
        var p = client.player;
        if (p == null) return;
        Font tr = client.font;

        float yaw = ((p.getYRot() % 360) + 360) % 360;   // 0..360, 0 = south
        String dir;
        if (yaw < 22.5f || yaw >= 337.5f) dir = "S";
        else if (yaw < 67.5f)  dir = "SW";
        else if (yaw < 112.5f) dir = "W";
        else if (yaw < 157.5f) dir = "NW";
        else if (yaw < 202.5f) dir = "N";
        else if (yaw < 247.5f) dir = "NE";
        else if (yaw < 292.5f) dir = "E";
        else                   dir = "SE";

        String strip = "N  NE  E  SE  S  SW  W  NW  N  NE  E";
        int stripW = tr.width(strip);
        int stripHalf = stripW / 2;
        int offset = stripHalf > 0 ? (int) (yaw / 360f * stripHalf) % stripHalf : 0;
        int cx = el.x + el.w / 2;

        ctx.enableScissor(el.x + 1, el.y + 1, el.x + el.w - 1, el.y + el.h - 1);
        ctx.text(tr, strip, cx - offset - stripHalf / 2,
                el.y + (el.h - 8) / 2, TEXT_PRIMARY, true);
        ctx.disableScissor();

        // Centre tick.
        ctx.fill(cx - 1, el.y + 1, cx + 1, el.y + el.h - 1, 0xCCFF4444);

        // Direction badge in top-right.
        int dirW = tr.width(dir) + 6;
        ctx.fill(el.x + el.w - dirW - 2, el.y + 2,
                el.x + el.w - 2, el.y + 11, 0x55091E46);
        ctx.text(tr, dir, el.x + el.w - dirW + 1, el.y + 3, ACCENT_BLUE, true);
    }

    // ─── Potion effects ──────────────────────────────────────────────────
    private static void renderPotions(GuiGraphicsExtractor ctx, Minecraft client,
                                        SkyzHudState.HudElementState el) {
        if (client.player == null) return;
        var effects = client.player.getActiveEffects();
        if (effects.isEmpty()) return;
        Font tr = client.font;

        int iy = el.y;
        for (var effect : effects) {
            if (iy + 18 > el.y + el.h) break;
            // Beneficial = green accent, harmful = red.
            boolean harmful = effect.getEffect().value().getCategory()
                    == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
            int accent = harmful ? ACCENT_RED : ACCENT_GREEN;
            SkyzRenderHelper.drawSkyzHudPanel(ctx, el.x, iy, el.w, 18, accent);
            String name = "?";
            var id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null) {
                String raw = id.getPath().replace('_', ' ');
                name = raw.isEmpty() ? "?" : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            }
            int dur = effect.getDuration() / 20;
            String durStr = dur > 60 ? (dur / 60) + "m" : dur + "s";
            ctx.text(tr, name, el.x + 6, iy + 5, TEXT_PRIMARY, true);
            ctx.text(tr, durStr,
                    el.x + el.w - 4 - tr.width(durStr), iy + 5, accent, true);
            iy += 20;
        }
    }

    // ─── Custom crosshair ────────────────────────────────────────────────
    private static void renderCrosshair(GuiGraphicsExtractor ctx, SkyzHudState.HudElementState el) {
        int cx = el.x + el.w / 2, cy = el.y + el.h / 2, size = 6;
        // Soft drop-shadow then a 1-px white cross.
        ctx.fill(cx - size, cy, cx + size + 1, cy + 1, 0x99000000);
        ctx.fill(cx, cy - size, cx + 1, cy + size + 1, 0x99000000);
        ctx.fill(cx - size, cy - 1, cx + size + 1, cy + 1, 0xFFFFFFFF);
        ctx.fill(cx - 1, cy - size, cx + 1, cy + size + 1, 0xFFFFFFFF);
    }

    // ─── Keystrokes ──────────────────────────────────────────────────────
    private static void renderKeystrokes(GuiGraphicsExtractor ctx, Minecraft client,
                                          SkyzHudState.HudElementState el) {
        var opts = client.options;
        int kw = 22, kh = 18, gap = 2;
        drawKey(ctx, client, "W",  el.x + kw + gap,        el.y + 2,            kw, kh, opts.keyUp.isDown());
        drawKey(ctx, client, "A",  el.x + 2,                el.y + kh + gap + 2, kw, kh, opts.keyLeft.isDown());
        drawKey(ctx, client, "S",  el.x + kw + gap,         el.y + kh + gap + 2, kw, kh, opts.keyDown.isDown());
        drawKey(ctx, client, "D",  el.x + (kw + gap) * 2,   el.y + kh + gap + 2, kw, kh, opts.keyRight.isDown());
        drawKey(ctx, client, "",   el.x + 2,                el.y + (kh + gap) * 2 + 2,
                kw * 3 + gap * 2, kh, opts.keyJump.isDown());
    }

    private static void drawKey(GuiGraphicsExtractor ctx, Minecraft client, String label,
                                 int x, int y, int w, int h, boolean pressed) {
        int r = h < 10 ? 2 : 3;
        if (pressed) {
            // Pressed: glowing blue with brighter top sheen.
            SkyzRenderHelper.fillRoundedRectGradient(ctx, x, y, w, h, r,
                    0xCC4AB8F0, 0xCC1864A0);
            SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, r, 0xFF8CDCFF);
            // Outer halo
            SkyzRenderHelper.drawRoundedBorder(ctx, x - 1, y - 1, w + 2, h + 2,
                    r + 1, 0x668CDCFF);
        } else {
            SkyzRenderHelper.fillRoundedRectGradient(ctx, x, y, w, h, r,
                    0xC8132C5A, 0xCC061533);
            SkyzRenderHelper.drawRoundedBorder(ctx, x, y, w, h, r, 0x558CD2FF);
        }
        if (!label.isBlank()) {
            ctx.centeredText(client.font, label,
                    x + w / 2, y + (h - 8) / 2, pressed ? 0xFFFFFFFF : 0xCCB0D8FF);
        }
    }

    // ─── Minimap ─────────────────────────────────────────────────────────
    /**
     * Optimized minimap renderer — owns a {@link net.minecraft.client.renderer.texture.DynamicTexture}
     * and a (2*range+1)² column-sample cache. The cache is scrolled in-place
     * when the player walks; only newly-revealed columns need a getBlockState
     * call. Sampling is time-budgeted to {@link #COLUMN_BUDGET_PER_FRAME} per
     * frame so a full rebuild spreads across a few frames instead of stalling
     * one frame for ~16 k state lookups.
     *
     * <p>This is a verbatim move from the old InGameHudMixin — the perf work
     * here was non-trivial and we don't change it for this port.
     */
    private static void renderMinimap(GuiGraphicsExtractor ctx, Minecraft client,
                                       SkyzHudState.HudElementState el) {
        if (client.level == null || client.player == null) return;
        int mx = el.x, my = el.y, mw = el.w, mh = el.h;
        int mapX = mx + 1, mapY = my + 1, mapW = mw - 2, mapH = mh - 2;
        if (mapW <= 0 || mapH <= 0) return;
        int cx = mx + mw / 2, cy = my + mh / 2;

        int playerX = (int) Math.floor(client.player.getX());
        int playerZ = (int) Math.floor(client.player.getZ());
        int playerY = (int) Math.floor(client.player.getY());
        int range   = Math.max(8, Math.min(128, SkyzClientState.minimapZoom));
        int side    = 2 * range + 1;

        boolean caveMode   = SkyzClientState.minimapCaveMode;
        boolean showLeaves = SkyzClientState.minimapShowLeaves;
        boolean showEnts   = SkyzClientState.minimapShowEntities;

        boolean playerUnderground = false;
        if (caveMode) {
            try {
                net.minecraft.world.level.levelgen.Heightmap.Types hm = showLeaves
                        ? net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING
                        : net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
                int playerSurfaceY = client.level.getHeight(hm, playerX, playerZ) - 1;
                playerUnderground = playerY < playerSurfaceY - 1;
            } catch (Exception ignored) {}
        }

        int newOriginX = playerX - range;
        int newOriginZ = playerZ - range;
        boolean settingsChanged = (range != colsRange
                || caveMode != colsCaveMode
                || showLeaves != colsShowLeaves);
        boolean caveYChanged   = caveMode && playerUnderground && playerY != colsCachedY;
        boolean explicitDirty  = SkyzMinimapState.dirty;

        if (colSamples == null || colsSide != side || settingsChanged) {
            colSamples = new int[side * side];
            colsSide = side;
            colsRange = range;
            colsCaveMode  = caveMode;
            colsShowLeaves= showLeaves;
            colsOriginX = newOriginX;
            colsOriginZ = newOriginZ;
            colsCachedY = playerY;
            colsCursor  = 0;
        } else {
            int dx = newOriginX - colsOriginX;
            int dz = newOriginZ - colsOriginZ;
            if (dx != 0 || dz != 0) {
                scrollColumnCache(dx, dz, side);
                colsOriginX = newOriginX;
                colsOriginZ = newOriginZ;
                colsCursor = 0;
            }
            if (explicitDirty || caveYChanged) {
                java.util.Arrays.fill(colSamples, UNCOMPUTED);
                colsCursor = 0;
                colsCachedY = playerY;
            }
        }
        if (explicitDirty) SkyzMinimapState.dirty = false;

        int total = side * side;
        int budget = COLUMN_BUDGET_PER_FRAME;
        int scanned = 0;
        boolean columnsChanged = false;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        while (budget > 0 && scanned < total) {
            int idx = colsCursor;
            if (colSamples[idx] == UNCOMPUTED) {
                int relX = idx % side;
                int relZ = idx / side;
                int worldX = colsOriginX + relX;
                int worldZ = colsOriginZ + relZ;
                colSamples[idx] = sampleColumn(client, worldX, worldZ, playerY,
                        playerUnderground, caveMode, showLeaves, mp);
                columnsChanged = true;
                budget--;
            }
            colsCursor = (colsCursor + 1) % total;
            scanned++;
        }

        boolean sizeChanged = (minimapImage == null || minimapTexW != mapW || minimapTexH != mapH);
        if (sizeChanged) {
            if (minimapTexture != null) {
                try { minimapTexture.close(); } catch (Exception ignored) {}
                minimapTexture = null;
                minimapImage = null;
            }
            minimapImage = new com.mojang.blaze3d.platform.NativeImage(mapW, mapH, false);
            // TODO(port): DynamicTexture constructor signature in 26.1 may have
            // changed — original yarn called NativeImageBackedTexture(supplier, image).
            // Mojang's DynamicTexture(String, NativeImage) is the closest match.
            minimapTexture = new net.minecraft.client.renderer.texture.DynamicTexture(
                    () -> "skyz_minimap", minimapImage);
            client.getTextureManager().register(MINIMAP_TEX_ID, minimapTexture);
            minimapTexW = mapW;
            minimapTexH = mapH;
            minimapTexDirty = true;
        }

        if (columnsChanged || sizeChanged || settingsChanged
                || explicitDirty || caveYChanged) {
            for (int pz = 0; pz < mapH; pz++) {
                int relZ = (pz * side) / mapH;
                if (relZ >= side) relZ = side - 1;
                int rowBase = relZ * side;
                for (int px = 0; px < mapW; px++) {
                    int relX = (px * side) / mapW;
                    if (relX >= side) relX = side - 1;
                    int sample = colSamples[rowBase + relX];
                    int argb = (sample == UNCOMPUTED) ? 0xFF1A1A1A : sample;
                    minimapImage.setPixelABGR(px, pz, argb);
                }
            }
            minimapTexDirty = true;
        }

        if (minimapTexDirty && minimapTexture != null) {
            minimapTexture.upload();
            minimapTexDirty = false;
        }

        if (minimapTexture != null) {
            // TODO(port): GuiGraphicsExtractor texture-blit signature in 26.1 — original
            // yarn used ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y,
            // u, v, w, h, texW, texH). Mojang's equivalent is `ctx.blit(...)`
            // with a RenderType argument. Using the closest available overload.
            ctx.blit(MINIMAP_TEX_ID, mapX, mapY, mapW, mapH, 0f, 1f, 0f, 1f);
        }

        // Polished frame: inner ring + soft outer halo.
        SkyzRenderHelper.drawRoundedBorder(ctx, mx, my, mw, mh, 5, 0xFF8CD2FF);
        SkyzRenderHelper.drawRoundedBorder(ctx, mx - 1, my - 1, mw + 2, mh + 2, 6, 0x558CD2FF);
        SkyzRenderHelper.drawRoundedBorder(ctx, mx - 2, my - 2, mw + 4, mh + 4, 7, 0x22558CFF);

        // Entity dots (cached above to avoid per-frame allocation).
        if (showEnts) try {
            for (Entity e : client.level.entitiesForRendering()) {
                if (e == client.player) continue;
                double ex = e.getX() - playerX;
                double ez = e.getZ() - playerZ;
                if (Math.abs(ex) > range || Math.abs(ez) > range) continue;
                int dotX = cx + (int) Math.round(ex * mapW / (double) (range * 2));
                int dotY = cy + (int) Math.round(ez * mapH / (double) (range * 2));
                int col  = e instanceof Player ? 0xFFFFFF44
                         : e instanceof net.minecraft.world.entity.monster.Monster ? 0xFFFF4444
                         : 0xFF44FF44;
                SkyzRenderHelper.fillCircle(ctx, dotX, dotY, 2, col);
            }
        } catch (Exception ignored) {}

        // Player dot — cyan halo, white core, red direction tip.
        float yawRad = (float) Math.toRadians(client.player.getYRot());
        int arrowR = 7;
        int ax = cx - (int) (Math.sin(yawRad) * arrowR);
        int ay = cy + (int) (Math.cos(yawRad) * arrowR);
        SkyzRenderHelper.fillCircle(ctx, cx, cy, 4, 0xCC4AB8F0);
        SkyzRenderHelper.fillCircle(ctx, cx, cy, 2, 0xFFFFFFFF);
        SkyzRenderHelper.fillCircle(ctx, ax, ay, 2, 0xFFFF2222);
        int wx = (int) (Math.cos(yawRad) * 3);
        int wy = (int) (Math.sin(yawRad) * 3);
        SkyzRenderHelper.fillCircle(ctx, cx + wx, cy + wy, 1, 0xCCFF6666);
        SkyzRenderHelper.fillCircle(ctx, cx - wx, cy - wy, 1, 0xCCFF6666);

        // "N" label.
        ctx.text(client.font, "N",
                cx - client.font.width("N") / 2, my + 2, 0xFFFFFFFF, true);
    }

    private static void scrollColumnCache(int dx, int dz, int side) {
        if (Math.abs(dx) >= side || Math.abs(dz) >= side) {
            java.util.Arrays.fill(colSamples, UNCOMPUTED);
            return;
        }
        int[] tmp = new int[side * side];
        for (int z = 0; z < side; z++) {
            int oldZ = z + dz;
            if (oldZ < 0 || oldZ >= side) continue;
            for (int x = 0; x < side; x++) {
                int oldX = x + dx;
                if (oldX < 0 || oldX >= side) continue;
                tmp[z * side + x] = colSamples[oldZ * side + oldX];
            }
        }
        System.arraycopy(tmp, 0, colSamples, 0, side * side);
    }

    private static int sampleColumn(Minecraft client, int worldX, int worldZ, int playerY,
                                     boolean playerUnderground, boolean caveMode, boolean showLeaves,
                                     BlockPos.MutableBlockPos mp) {
        try {
            if (client.level.getChunk(worldX >> 4, worldZ >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) {
                return 0xFF222222;
            }
            net.minecraft.world.level.levelgen.Heightmap.Types hm = showLeaves
                    ? net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING
                    : net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
            int surfaceY = client.level.getHeight(hm, worldX, worldZ) - 1;

            int scanY;
            if (caveMode && playerUnderground) {
                scanY = playerY + 1;
                while (scanY > client.level.getMinY()) {
                    mp.set(worldX, scanY, worldZ);
                    if (!client.level.getBlockState(mp).isAir()) break;
                    scanY--;
                }
            } else {
                scanY = surfaceY;
            }
            if (scanY < client.level.getMinY()) return 0xFF111111;

            mp.set(worldX, scanY, worldZ);
            int rgb = getBlockMapColor(client.level.getBlockState(mp));
            int shade = Math.max(50, Math.min(255, 80 + scanY));
            int r = (rgb >> 16 & 0xFF) * shade / 255;
            int g = (rgb >>  8 & 0xFF) * shade / 255;
            int b = (rgb       & 0xFF) * shade / 255;
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            return argb == UNCOMPUTED ? 0xFF010101 : argb;
        } catch (Exception e) {
            return 0xFF333333;
        }
    }

    private static int getBlockMapColor(net.minecraft.world.level.block.state.BlockState state) {
        try {
            net.minecraft.world.level.material.MapColor mc = state.getMapColor(null, BlockPos.ZERO);
            if (mc != null && mc != net.minecraft.world.level.material.MapColor.NONE) return mc.col;
        } catch (Exception ignored) {}
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.contains("grass") || id.contains("lawn"))   return 0x59AE30;
        if (id.contains("water"))                          return 0x3F76E4;
        if (id.contains("sand") || id.contains("gravel"))  return 0xD9CC9A;
        if (id.contains("stone") || id.contains("cobble")) return 0x888888;
        if (id.contains("wood")  || id.contains("log"))    return 0x8B4513;
        if (id.contains("leaf")  || id.contains("leaves")) return 0x2D7A12;
        if (id.contains("snow")  || id.contains("ice"))    return 0xDDEEFF;
        if (id.contains("lava"))                           return 0xFF6600;
        if (id.contains("dirt"))                           return 0x8B5E3C;
        if (id.contains("ore"))                            return 0x555555;
        return 0x777777;
    }

    private static String getBiomeName(Minecraft client) {
        try {
            if (client.level == null || client.player == null) return "Unknown";
            Optional<ResourceKey<Biome>> key = client.level
                    .getBiome(client.player.blockPosition()).unwrapKey();
            if (key.isPresent()) {
                String raw = key.get().identifier().getPath();
                return raw.isEmpty() ? "Unknown"
                        : Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace('_', ' ');
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }
}
