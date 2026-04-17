package net.skyz.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.skyz.client.util.SkyzClientState;
import net.skyz.client.util.SkyzHudState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    // Session timer
    private static long sessionStart = 0;

    // Minimap: GPU texture built once, re-uploaded only when player moves a block
    private static net.minecraft.client.texture.NativeImageBackedTexture minimapTex = null;
    private static net.minecraft.util.Identifier minimapId = null;
    private static int minimapCachedX = Integer.MIN_VALUE;
    private static int minimapCachedZ = Integer.MIN_VALUE;
    private static int minimapTexSize = 0;
    private static final int MINIMAP_RANGE = 48; // world blocks shown each side

    @Inject(method = "render", at = @At("TAIL"))
    private void skyz$render(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int W = ctx.getScaledWindowWidth();
        int H = ctx.getScaledWindowHeight();
        SkyzHudState.initDefaults(W, H);

        if (sessionStart == 0) sessionStart = System.currentTimeMillis();
        net.skyz.client.util.SkyzClientState.updateCps();

        for (SkyzHudState.HudElementState el : SkyzHudState.ELEMENTS) {
            if (!el.enabled) continue;
            renderElement(ctx, client, el, W, H);
        }
    }

    private void renderElement(DrawContext ctx, MinecraftClient client,
                               SkyzHudState.HudElementState el, int W, int H) {
        // Keystrokes and custom crosshair have special rendering
        switch (el.name) {
            case "Keystrokes"      -> { renderKeystrokes(ctx, client, el); return; }
            case "Potion Effects"  -> { renderPotions(ctx, client, el); return; }
            case "Custom Crosshair"-> { renderCrosshair(ctx, el, W, H); return; }
            case "Enemy Info"      -> { renderEnemyInfo(ctx, client, el); return; }
            case "Nearby Players"  -> { renderNearbyPlayers(ctx, client, el); return; }
            case "TNT Timer"       -> { renderTntTimer(ctx, client, el); return; }
            case "Compass"         -> { renderCompass(ctx, client, el, W); return; }
            case "Minimap"         -> { renderMinimap(ctx, client, el); return; }
        }

        // Standard pill background
        ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0xAA000000);
        ctx.fill(el.x, el.y, el.x+el.w, el.y+1, 0x668CD2FF);
        int ty = el.y + (el.h-8)/2;
        var tr = client.textRenderer;

        switch (el.name) {
            case "FPS Counter" -> {
                int f = client.getCurrentFps();
                ctx.drawTextWithShadow(tr, f+" FPS", el.x+4, ty,
                        f>=60 ? 0xFF4CFA87 : f>=30 ? 0xFFFACC4C : 0xFFFA6C4C);
            }
            case "CPS Counter" -> {
                ctx.drawTextWithShadow(tr, SkyzClientState.cps+" CPS", el.x+4, ty, 0xCCFFFFFF);
            }
            case "Coordinates" -> {
                ctx.drawTextWithShadow(tr, String.format("%.0f / %.0f / %.0f",
                        client.player.getX(), client.player.getY(), client.player.getZ()),
                        el.x+4, ty, 0xCCFFFFFF);
            }
            case "Biome Display" -> {
                String biome = getBiomeName(client);
                ctx.drawTextWithShadow(tr, "\uD83C\uDF3F "+biome, el.x+4, ty, 0xCCB0F0B0);
            }
            case "Speed Meter" -> {
                double dx = client.player.getX() - client.player.lastRenderX;
                double dz = client.player.getZ() - client.player.lastRenderZ;
                double spd = Math.sqrt(dx*dx+dz*dz)*20;
                ctx.drawTextWithShadow(tr, String.format("\u26A1 %.1f b/s", spd), el.x+4, ty,
                        spd>5.6 ? 0xFF4CFA87 : spd>4.3 ? 0xFFFACC4C : 0xFFFFFFFF);
            }
            case "Ping Display" -> {
                int ping = -1;
                if (client.isInSingleplayer()) {
                    ping = 0; // local
                } else {
                    try {
                        var net = client.getNetworkHandler();
                        if (net != null) {
                            // Try by game profile name first (more reliable than UUID in some versions)
                            String name = client.player.getName().getString();
                            for (var entry : net.getPlayerList()) {
                                if (entry.getProfile().name().equals(name)) {
                                    ping = entry.getLatency();
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                String pingStr = client.isInSingleplayer() ? "LAN"
                               : ping < 0 ? "???" : ping + "ms";
                int pingCol = client.isInSingleplayer() ? 0xFF4CFA87
                            : ping < 0 ? 0xFF888888
                            : ping < 80 ? 0xFF4CFA87 : ping < 150 ? 0xFFFACC4C : 0xFFFA6C4C;
                ctx.drawTextWithShadow(tr, "\uD83D\uDCE1 " + pingStr, el.x+4, ty, pingCol);
            }
            case "Reach Display" -> {
                // Show reach distance, turns green when targeting entity
                boolean targeting = client.crosshairTarget != null &&
                        client.crosshairTarget.getType() == HitResult.Type.ENTITY;
                String reachStr = "\uD83C\uDFAF 3.0m";
                ctx.drawTextWithShadow(tr, reachStr, el.x+4, ty, targeting ? 0xFF4CFA87 : 0xFFFFFFFF);
            }
            case "Memory Usage" -> {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory()-rt.freeMemory())/1048576;
                long max  = rt.maxMemory()/1048576;
                int pct = (int)(used*100/max);
                drawBar(ctx, el, pct, 0xCC2A7ACA, 0xFFAADDFF, "\uD83D\uDDA5 "+used+"MB", tr);
            }
            case "Entity Count" -> {
                int count = 0;
                for (Entity e : client.world.getEntities()) count++;
                ctx.drawTextWithShadow(tr, "\uD83D\uDC7E "+count+" entities", el.x+4, ty, 0xCCFFDDAA);
            }
            case "Session Timer" -> {
                long secs = (System.currentTimeMillis()-sessionStart)/1000;
                ctx.drawTextWithShadow(tr, String.format("\u23F1 %d:%02d:%02d",
                        secs/3600, (secs%3600)/60, secs%60), el.x+4, ty, 0xCCDDDDFF);
            }
            case "Block Info" -> {
                String info = "Air";
                if (client.crosshairTarget != null &&
                        client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                    var bhr = (net.minecraft.util.hit.BlockHitResult) client.crosshairTarget;
                    var state = client.world.getBlockState(bhr.getBlockPos());
                    if (!state.isAir()) {
                        String raw = net.minecraft.registry.Registries.BLOCK
                                .getId(state.getBlock()).getPath().replace('_',' ');
                        info = raw.substring(0,1).toUpperCase()+raw.substring(1);
                    }
                }
                ctx.drawTextWithShadow(tr, "\uD83E\uDDF1 "+info, el.x+4, ty, 0xCCFFFFDD);
            }
            case "Health Bar" -> {
                float hp = client.player.getHealth(), maxHp = client.player.getMaxHealth();
                drawBar(ctx, el, (int)(hp/maxHp*100), 0xCCE03030, 0xFFFF8888,
                        "\u2764 "+(int)hp+"/"+(int)maxHp, tr);
            }
            case "Hunger Bar" -> {
                int food = client.player.getHungerManager().getFoodLevel();
                drawBar(ctx, el, food*5, 0xCCE08030, 0xFFFFBB66, "\uD83C\uDF56 "+food+"/20", tr);
            }
            case "Saturation Bar" -> {
                float sat = client.player.getHungerManager().getSaturationLevel();
                drawBar(ctx, el, (int)(sat/20*100), 0xCCFFD700, 0xFFFFE866,
                        "\u2728 "+String.format("%.1f",sat), tr);
            }
            case "Armor Bar" -> {
                int armor = client.player.getArmor();
                drawBar(ctx, el, armor*5, 0xCCAAAAAA, 0xFFCCCCCC, "\uD83D\uDEE1 "+armor+"/20", tr);
            }
            case "Tool Durability" -> {
                ItemStack tool = client.player.getMainHandStack();
                if (!tool.isEmpty() && tool.isDamageable()) {
                    int dur = tool.getMaxDamage()-tool.getDamage(), max = tool.getMaxDamage();
                    int pct = max>0 ? dur*100/max : 0;
                    drawBar(ctx, el, pct, 0xCC3A9A3A, 0xFF80FF80,
                            "\u26CF "+dur+"/"+max, tr);
                } else {
                    ctx.drawTextWithShadow(tr, "\u26CF No tool", el.x+4, ty, 0x448CD2FF);
                }
            }
            case "Helmet Durability"     -> renderArmorSlot(ctx, client, el, net.minecraft.entity.EquipmentSlot.HEAD,  "\u26D1");
            case "Chestplate Durability" -> renderArmorSlot(ctx, client, el, net.minecraft.entity.EquipmentSlot.CHEST, "\uD83E\uDDF2");
            case "Leggings Durability"   -> renderArmorSlot(ctx, client, el, net.minecraft.entity.EquipmentSlot.LEGS,  "\uD83D\uDC56");
            case "Boots Durability"      -> renderArmorSlot(ctx, client, el, net.minecraft.entity.EquipmentSlot.FEET,  "\uD83D\uDC62");
            case "Attack Cooldown" -> {
                float cooldown = client.player.getAttackCooldownProgress(0f);
                int pct = (int)(cooldown*100);
                ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0x55000000);
                ctx.fill(el.x, el.y, el.x+(int)(el.w*cooldown), el.y+el.h,
                        pct>=100 ? 0xCCFFAA00 : 0xCC884400);
            }
            case "Combo Counter" -> {
                int combo = net.skyz.client.util.SkyzClientState.comboCount;
                if (combo > 0) {
                    // Big centered combo display
                    ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0xAA000000);
                    int col = combo>=10?0xFFFF4444:combo>=5?0xFFFFAA00:0xFFFFFFFF;
                    String comboStr = combo + " combo";
                    ctx.drawCenteredTextWithShadow(tr, comboStr, el.x+el.w/2, el.y+(el.h-8)/2, col);
                }
            }
            case "Totem Pop Counter" -> {
                int pops = net.skyz.client.util.SkyzClientState.totemPops;
                ctx.drawTextWithShadow(tr, "\uD83C\uDF3C Totems: " + pops, el.x+4, ty, 0xFF88FF88);
            }
            default -> ctx.drawTextWithShadow(tr, el.icon+" "+el.name, el.x+4, ty, 0x88FFFFFF);
        }
    }

    private void drawBar(DrawContext ctx, SkyzHudState.HudElementState el,
                         int pct, int barColor, int textColor, String label,
                         net.minecraft.client.font.TextRenderer tr) {
        int bx = el.x+4, by = el.y+el.h-4, bw = el.w-8;
        ctx.fill(bx, by, bx+bw, by+3, 0x55333333);
        ctx.fill(bx, by, bx+Math.max(0, bw*pct/100), by+3, barColor);
        ctx.drawTextWithShadow(tr, label, el.x+4, el.y+2, textColor);
    }

    private void renderTntTimer(DrawContext ctx, MinecraftClient client, SkyzHudState.HudElementState el) {
        List<TntEntity> tnts = client.world.getEntitiesByClass(TntEntity.class,
                client.player.getBoundingBox().expand(100), e -> true);
        if (tnts.isEmpty()) return;

        // Find closest lit TNT
        TntEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (TntEntity t : tnts) {
            double d = t.squaredDistanceTo(client.player);
            if (d < minDist) { minDist = d; closest = t; }
        }

        ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0xCC440000);
        ctx.fill(el.x, el.y, el.x+el.w, el.y+1, 0xAAFF4400);
        var tr = client.textRenderer;

        // getFuse() returns remaining fuse ticks
        if (closest != null) {
            int fuseTicks = closest.getFuse();
            float fuseSecs = fuseTicks / 20f;
            String timeStr = String.format("%.1fs", fuseSecs);
            ctx.drawTextWithShadow(tr, "\uD83D\uDCA5 " + tnts.size() + " TNT \u00B7 " + timeStr,
                    el.x+4, el.y+(el.h-8)/2, fuseSecs < 1f ? 0xFFFF4444 : 0xFFFF9944);
        } else {
            ctx.drawTextWithShadow(tr, "\uD83D\uDCA5 "+tnts.size()+" TNT",
                    el.x+4, el.y+(el.h-8)/2, 0xFFFFAA44);
        }
    }

    private void renderEnemyInfo(DrawContext ctx, MinecraftClient client, SkyzHudState.HudElementState el) {
        if (!(client.crosshairTarget instanceof EntityHitResult ehr)) return;
        Entity target = ehr.getEntity();
        if (!(target instanceof LivingEntity living)) return;

        ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0xAA000000);
        ctx.fill(el.x, el.y, el.x+el.w, el.y+1, 0x88FF4444);
        var tr = client.textRenderer;
        int y = el.y+4;

        String name = living.getDisplayName().getString();
        ctx.drawTextWithShadow(tr, "\uD83D\uDC80 "+name, el.x+4, y, 0xFFFF8888); y+=12;

        float hp = living.getHealth(), maxHp = living.getMaxHealth();
        int bw = el.w-8;
        ctx.fill(el.x+4, y, el.x+4+bw, y+6, 0x55333333);
        ctx.fill(el.x+4, y, el.x+4+(int)(bw*hp/maxHp), y+6, 0xCCE03030);
        ctx.drawTextWithShadow(tr, String.format("%.0f/%.0f HP",hp,maxHp), el.x+4, y+8, 0xFFFF9999); y+=20;

        int armor = living.getArmor();
        ctx.drawTextWithShadow(tr, "\uD83D\uDEE1 "+armor+" armor", el.x+4, y, 0xFFCCCCCC); y+=12;

        if (living instanceof PlayerEntity p) {
            ItemStack held = p.getMainHandStack();
            if (!held.isEmpty()) {
                String itemName = held.getName().getString();
                ctx.drawTextWithShadow(tr, "\u2694 "+itemName, el.x+4, y, 0xFFFFDD88);
            }
        }
    }

    private void renderNearbyPlayers(DrawContext ctx, MinecraftClient client, SkyzHudState.HudElementState el) {
        ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0xAA000000);
        ctx.fill(el.x, el.y, el.x+el.w, el.y+1, 0x888CD2FF);
        var tr = client.textRenderer;
        int y = el.y+4;
        ctx.drawTextWithShadow(tr, "\uD83D\uDC65 Nearby", el.x+4, y, 0xFF8CD2FF); y+=12;

        int count = 0;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            double dist = p.distanceTo(client.player);
            if (dist > 64) continue;
            String line = p.getName().getString()+" "+Math.round(dist)+"m";
            ctx.drawTextWithShadow(tr, line, el.x+4, y, 0xCCFFFFFF);
            y += 10;
            if (++count >= 6) break;
        }
        if (count == 0)
            ctx.drawTextWithShadow(tr, "None nearby", el.x+4, y, 0x448CD2FF);
    }

    private void renderCompass(DrawContext ctx, MinecraftClient client, SkyzHudState.HudElementState el, int W) {
        ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0xAA000000);
        ctx.fill(el.x, el.y, el.x+el.w, el.y+1, 0x668CD2FF);
        var tr = client.textRenderer;
        // Normalize yaw: MC yaw 0=south, -90=east, 90=west, 180/-180=north
        float yaw = ((client.player.getYaw() % 360) + 360) % 360; // 0-360, 0=south
        // Cardinal direction label
        String dir;
        if (yaw < 22.5f || yaw >= 337.5f) dir = "S";
        else if (yaw < 67.5f)  dir = "SW";
        else if (yaw < 112.5f) dir = "W";
        else if (yaw < 157.5f) dir = "NW";
        else if (yaw < 202.5f) dir = "N";
        else if (yaw < 247.5f) dir = "NE";
        else if (yaw < 292.5f) dir = "E";
        else                   dir = "SE";
        // Scrolling compass strip \u2014 render chars N, NE, E, SE, S, SW, W, NW spaced out
        String strip = "N  NE  E  SE  S  SW  W  NW  N  NE  E";
        int stripW = tr.getWidth(strip);
        // offset so current direction centers in the element
        int offset = (int)(yaw / 360f * (stripW / 2)) % (stripW / 2);
        int cx = el.x + el.w/2;
        ctx.enableScissor(el.x+1, el.y, el.x+el.w-1, el.y+el.h);
        ctx.drawTextWithShadow(tr, strip, cx - offset - stripW/4, el.y+3, 0xCCFFFFFF);
        ctx.disableScissor();
        // Center tick
        ctx.fill(cx-1, el.y, cx+1, el.y+el.h, 0xCCFF4444);
        // Direction label at right edge
        ctx.drawTextWithShadow(tr, dir, el.x+el.w-tr.getWidth(dir)-4, el.y+3, 0xFF8CD2FF);
    }

    private void renderPotions(DrawContext ctx, MinecraftClient client, SkyzHudState.HudElementState el) {
        var effects = client.player.getStatusEffects();
        if (effects.isEmpty()) return;
        int iy = el.y;
        for (var effect : effects) {
            ctx.fill(el.x, iy, el.x+el.w, iy+18, 0xAA000000);
            ctx.fill(el.x, iy, el.x+el.w, iy+1, 0x668CD2FF);
            String name = "?";
            var id = net.minecraft.registry.Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
            if (id != null) { name = id.getPath().replace('_',' '); name = name.substring(0,1).toUpperCase()+name.substring(1); }
            int dur = effect.getDuration()/20;
            ctx.drawTextWithShadow(client.textRenderer,
                    name+" "+(dur>60?dur/60+"m":dur+"s"), el.x+4, iy+5, 0xCCFFFFFF);
            iy += 20;
            if (iy > el.y+el.h) break;
        }
    }

    private void renderCrosshair(DrawContext ctx, SkyzHudState.HudElementState el, int W, int H) {
        int cx = el.x+el.w/2, cy = el.y+el.h/2, size = 6;
        ctx.fill(cx-size, cy-1, cx+size, cy+1, 0xCCFFFFFF);
        ctx.fill(cx-1, cy-size, cx+1, cy+size, 0xCCFFFFFF);
    }

    private void renderKeystrokes(DrawContext ctx, MinecraftClient client, SkyzHudState.HudElementState el) {
        var opts = client.options;
        int kw=22, kh=18, gap=2;
        drawKey(ctx, client, "W",  el.x+kw+gap,      el.y+2,           kw, kh, opts.forwardKey.isPressed());
        drawKey(ctx, client, "A",  el.x+2,            el.y+kh+gap+2,    kw, kh, opts.leftKey.isPressed());
        drawKey(ctx, client, "S",  el.x+kw+gap,       el.y+kh+gap+2,    kw, kh, opts.backKey.isPressed());
        drawKey(ctx, client, "D",  el.x+(kw+gap)*2,   el.y+kh+gap+2,    kw, kh, opts.rightKey.isPressed());
        drawKey(ctx, client, "",   el.x+2,             el.y+(kh+gap)*2+2,kw*3+gap*2, kh, opts.jumpKey.isPressed());
    }

    private void drawKey(DrawContext ctx, MinecraftClient client, String label,
                         int x, int y, int w, int h, boolean pressed) {
        ctx.fill(x,y,x+w,y+h, pressed?0xCC2A5A8A:0x88151515);
        ctx.fill(x,y,x+w,y+1, pressed?0x884AB8F0:0x44888888);
        ctx.fill(x,y+h-1,x+w,y+h, pressed?0x444AB8F0:0x22888888);
        if (!label.isBlank())
            ctx.drawCenteredTextWithShadow(client.textRenderer, label,
                    x+w/2, y+(h-8)/2, pressed?0xFFFFFFFF:0x88AAAAAA);
    }

    private void renderArmorSlot(DrawContext ctx, MinecraftClient client,
                                  net.skyz.client.util.SkyzHudState.HudElementState el,
                                  net.minecraft.entity.EquipmentSlot slot, String icon) {
        net.minecraft.item.ItemStack s = client.player.getEquippedStack(slot);
        var tr = client.textRenderer;
        ctx.fill(el.x, el.y, el.x+el.w, el.y+el.h, 0xAA000000);
        ctx.fill(el.x, el.y, el.x+el.w, el.y+1, 0x668CD2FF);
        int ty = el.y + (el.h-8)/2;
        if (s.isEmpty()) {
            ctx.drawTextWithShadow(tr, icon+" None", el.x+4, ty, 0x338CD2FF);
        } else if (s.isDamageable()) {
            int dur = s.getMaxDamage()-s.getDamage(), max = s.getMaxDamage();
            int pct = max>0?dur*100/max:0;
            int col = pct>60?0xFF4CFA87:pct>30?0xFFFACC4C:0xFFFA6C4C;
            drawBar(ctx, el, pct, col&0x00FFFFFF|0xCC000000, col, icon+" "+dur+"/"+max, tr);
        } else {
            ctx.drawTextWithShadow(tr, icon+" "+s.getName().getString(), el.x+4, ty, 0xCCFFFFFF);
        }
    }

    private void renderMinimap(DrawContext ctx, MinecraftClient client, net.skyz.client.util.SkyzHudState.HudElementState el) {
        int mx = el.x, my = el.y, mw = el.w, mh = el.h;
        int texSize = Math.min(mw - 2, mh - 2);
        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();

        // Rebuild texture only when player moves to a new block, or first render
        if (minimapTex == null || minimapTexSize != texSize
                || minimapCachedX != playerX || minimapCachedZ != playerZ) {
            minimapCachedX = playerX;
            minimapCachedZ = playerZ;
            minimapTexSize = texSize;

            // Build/rebuild the NativeImage
            net.minecraft.client.texture.NativeImage img =
                    new net.minecraft.client.texture.NativeImage(texSize, texSize, false);

            for (int px = 0; px < texSize; px++) {
                for (int pz = 0; pz < texSize; pz++) {
                    int bx = (px - texSize/2) * MINIMAP_RANGE / (texSize/2);
                    int bz = (pz - texSize/2) * MINIMAP_RANGE / (texSize/2);
                    int worldX = playerX + bx;
                    int worldZ = playerZ + bz;
                    int argb = 0xFF333333;
                    try {
                        if (client.world.getChunk(worldX >> 4, worldZ >> 4,
                                net.minecraft.world.chunk.ChunkStatus.FULL, false) != null) {
                            int topY = client.world.getTopY(
                                    net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                    worldX, worldZ) - 1;
                            if (topY >= client.world.getBottomY()) {
                                var state = client.world.getBlockState(
                                        new net.minecraft.util.math.BlockPos(worldX, topY, worldZ));
                                int rgb = getBlockMapColor(state);
                                int shade = Math.max(60, Math.min(255, 80 + topY + 64));
                                int r = (rgb >> 16 & 0xFF) * shade / 255;
                                int g = (rgb >>  8 & 0xFF) * shade / 255;
                                int b = (rgb       & 0xFF) * shade / 255;
                                argb = 0xFF000000 | (r << 16) | (g << 8) | b;
                            } else {
                                argb = 0xFF111111;
                            }
                        } else {
                            argb = 0xFF222222;
                        }
                    } catch (Exception ignored) {}
                    // NativeImage uses ABGR format
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >>  8) & 0xFF;
                    int b = (argb)       & 0xFF;
                    img.setColor(px, pz, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }

            // Upload to GPU
            if (minimapTex == null) {
                minimapTex = new net.minecraft.client.texture.NativeImageBackedTexture(img);
                minimapId = net.minecraft.client.MinecraftClient.getInstance()
                        .getTextureManager()
                        .registerDynamicTexture("skyz_minimap", minimapTex);
            } else {
                minimapTex.setImage(img);
                minimapTex.upload();
            }
        }

        // Single drawTexture call \u2014 one GPU draw instead of 10000 fill calls
        if (minimapId != null) {
            // drawTexture(id, x, y, u, v, w, h, texW, texH)
            ctx.drawTexture(minimapId, mx+1, my+1, 0, 0, texSize, texSize, texSize, texSize);
        }

        // Border
        ctx.fill(mx, my, mx+mw, my+1, 0xFF8CD2FF);
        ctx.fill(mx, my+mh-1, mx+mw, my+mh, 0xFF8CD2FF);
        ctx.fill(mx, my, mx+1, my+mh, 0xFF8CD2FF);
        ctx.fill(mx+mw-1, my, mx+mw, my+mh, 0xFF8CD2FF);

        int cx = mx + mw/2, cy = my + mh/2;

        // Entities
        try {
            for (net.minecraft.entity.Entity e : client.world.getEntities()) {
                if (e == client.player) continue;
                double ex = e.getX() - playerX;
                double ez = e.getZ() - playerZ;
                if (Math.abs(ex) > MINIMAP_RANGE || Math.abs(ez) > MINIMAP_RANGE) continue;
                int dotX = cx + (int)(ex * (texSize/2) / MINIMAP_RANGE);
                int dotY = cy + (int)(ez * (texSize/2) / MINIMAP_RANGE);
                int col = e instanceof net.minecraft.entity.player.PlayerEntity ? 0xFFFFFF44
                        : e instanceof net.minecraft.entity.mob.HostileEntity   ? 0xFFFF4444
                        : 0xFF44FF44;
                ctx.fill(dotX-1, dotY-1, dotX+2, dotY+2, col);
            }
        } catch (Exception ignored) {}

        // Player dot + direction arrow
        // MC yaw: 0=south(+Z down), 90=west, 180=north, 270=east
        float yawRad = (float) Math.toRadians(client.player.getYaw());
        int ax = cx + (int)(Math.sin(yawRad) * 6);
        int ay = cy + (int)(Math.cos(yawRad) * 6);
        ctx.fill(cx-2, cy-2, cx+2, cy+2, 0xFFFFFFFF);
        ctx.fill(ax-1, ay-1, ax+1, ay+1, 0xFFFF2222);

        // N label
        ctx.drawTextWithShadow(client.textRenderer, "N",
                cx - client.textRenderer.getWidth("N")/2, my+2, 0xFFFFFFFF);
    }

    private int getBlockMapColor(net.minecraft.block.BlockState state) {
        try {
            net.minecraft.block.MapColor mc = state.getMapColor(null, net.minecraft.util.math.BlockPos.ORIGIN);
            if (mc != null && mc != net.minecraft.block.MapColor.CLEAR) return mc.color;
        } catch (Exception ignored) {}
        String id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
        if (id.contains("grass") || id.contains("lawn"))  return 0x59AE30;
        if (id.contains("water"))                         return 0x3F76E4;
        if (id.contains("sand") || id.contains("gravel")) return 0xD9CC9A;
        if (id.contains("stone") || id.contains("cobble"))return 0x888888;
        if (id.contains("wood")  || id.contains("log"))   return 0x8B4513;
        if (id.contains("leaf")  || id.contains("leaves"))return 0x2D7A12;
        if (id.contains("snow")  || id.contains("ice"))   return 0xDDEEFF;
        if (id.contains("lava"))                          return 0xFF6600;
        if (id.contains("dirt"))                          return 0x8B5E3C;
        if (id.contains("ore"))                           return 0x555555;
        return 0x777777;
    }

    private String getBiomeName

    private String getBiomeName(MinecraftClient client) {
        try {
            Optional<RegistryKey<Biome>> key = client.world
                    .getBiome(client.player.getBlockPos()).getKey();
            if (key.isPresent()) {
                String raw = key.get().getValue().getPath();
                return raw.substring(0,1).toUpperCase()+raw.substring(1).replace('_',' ');
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }
}