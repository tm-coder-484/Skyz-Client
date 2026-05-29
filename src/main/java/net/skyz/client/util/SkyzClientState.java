package net.skyz.client.util;

import net.minecraft.client.Minecraft;

/**
 * Central state for all built-in mod toggles.
 * tick() is called every game tick from SkyzClientMod's ClientTickEvents.
 */
public final class SkyzClientState {
    private SkyzClientState() {}

    // CPS tracking - ring buffer
    public static int cps = 0;
    private static final long[] CPS_CLICKS = new long[20];
    private static int cpsHead = 0;

    public static void recordClick() {
        CPS_CLICKS[cpsHead % 20] = System.currentTimeMillis();
        cpsHead++;
    }

    public static void updateCps() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long t : CPS_CLICKS) if (t > 0 && now - t < 1000) count++;
        cps = count;
    }

    // QoL Toggles
    public static boolean toggleSprint    = false;
    public static boolean toggleSneak     = false;
    public static boolean toggleChat      = true;
    public static boolean fullbright      = false;
    public static boolean noFog           = false;
    public static boolean noPumpkinBlur   = false;
    public static boolean antiAfk         = false;
    public static boolean autoGG          = false;

    // Visual toggles
    public static float   fovMultiplier   = 1.0f;
    public static boolean coloredHitboxes = false;
    public static boolean noFireOverlay   = false;

    // ── Hacks ────────────────────────────────────────────────────────────────
    public static boolean spawnerAlert      = false; // ping when a spawner is nearby
    public static boolean storageEsp        = false;
    public static boolean playerEsp         = false;
    public static boolean itemEsp           = false; // item hitboxes through walls
    public static boolean blockEsp          = false; // configurable in settings
    public static boolean autoTotem         = false;
    public static boolean trajectories      = false; // bow/pearl paths
    public static boolean mobEsp            = false;
    public static boolean oreHighlighter    = false;
    public static boolean autoclicker       = false;
    public static int     autoclickerCps    = 12;    // target CPS
    public static boolean dynamicFps        = false; // limit FPS when tabbed/AFK
    public static boolean particleLimiter   = false;
    public static boolean chatTimestamps    = true;
    public static long    sessionStartMs    = System.currentTimeMillis();

    // Minimap settings (persisted via SkyzConfig)
    public static boolean minimapShowLeaves  = false; // false = use MOTION_BLOCKING_NO_LEAVES
    public static boolean minimapCaveMode    = true;  // true = show cave below player
    public static boolean minimapShowEntities= true;
    public static int     minimapZoom        = 32;    // range in blocks each side

    // Anti-AFK state
    private static int  afkTick     = 0;
    private static boolean afkDir   = false;

    // Autoclicker state
    private static int  autoclickerTick = 0;

    // Dynamic FPS state — remembers original cap so we can restore it
    private static int  savedMaxFps = -1;

    // Auto-GG state — fires once per game-end detection, then re-arms when player rejoins
    private static long lastAutoGgMs = 0;

    // Combo counter
    public static int  comboCount   = 0;
    public static long lastHitTime  = 0;
    private static final long COMBO_RESET_MS = 3500;

    // Totem pop counter
    public static int totemPops = 0;

    // Spawner alert — track already-pinged positions so we only ping once per
    // spawner per session. Cleared when player changes worlds (different
    // dimension or null world). Scan cadence: every 20 ticks (1 second) so
    // the block iteration cost is amortised across a tick budget.
    private static final java.util.Set<net.minecraft.core.BlockPos> SPAWNER_PINGED =
            new java.util.HashSet<>();
    private static int spawnerScanTick = 0;
    private static net.minecraft.world.level.Level spawnerLastWorld = null;

    // ── Block ESP ──────────────────────────────────────────────────────────
    /**
     * One toggleable category of blocks rendered with a single colour. Each
     * group can match multiple blocks (e.g. ore + deepslate variant) so a
     * single toggle catches both. Color is 0xRRGGBB (alpha is added at draw
     * time so the user can pick by hue without worrying about transparency).
     */
    public static final class BlockEspGroup {
        public final String name;
        public final int color;
        public final net.minecraft.world.level.block.Block[] blocks;
        public final boolean isBlockEntity; // auto-detected at construction
        public boolean enabled;
        public BlockEspGroup(String name, int color, net.minecraft.world.level.block.Block... blocks) {
            this.name = name; this.color = color; this.blocks = blocks;
            // Auto-detect: if the first block is EntityBlock, use BE path.
            // All blocks in a group should be the same kind in practice (we group by category).
            this.isBlockEntity = blocks.length > 0
                    && blocks[0] instanceof net.minecraft.world.level.block.EntityBlock;
        }
    }

    public record BlockEspMatch(net.minecraft.core.BlockPos pos, int color) {}

    /** Built-in groups. Order = render order (later draws on top). */
    public static final java.util.List<BlockEspGroup> BLOCK_ESP_GROUPS = new java.util.ArrayList<>();
    static {
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Diamond Ore",   0x33EAFF,
                net.minecraft.world.level.block.Blocks.DIAMOND_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Ancient Debris", 0xA040FF,
                net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Emerald Ore",   0x2EFF74,
                net.minecraft.world.level.block.Blocks.EMERALD_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Gold Ore",      0xFFD737,
                net.minecraft.world.level.block.Blocks.GOLD_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE,
                net.minecraft.world.level.block.Blocks.NETHER_GOLD_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Lapis Ore",     0x3F58FF,
                net.minecraft.world.level.block.Blocks.LAPIS_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_LAPIS_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Iron Ore",      0xD9B89D,
                net.minecraft.world.level.block.Blocks.IRON_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Coal Ore",      0x888888,
                net.minecraft.world.level.block.Blocks.COAL_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_COAL_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Redstone Ore",  0xFF3F3F,
                net.minecraft.world.level.block.Blocks.REDSTONE_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_REDSTONE_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Copper Ore",    0xE07A3F,
                net.minecraft.world.level.block.Blocks.COPPER_ORE, net.minecraft.world.level.block.Blocks.DEEPSLATE_COPPER_ORE));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Spawner",       0xFF4444,
                net.minecraft.world.level.block.Blocks.SPAWNER));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Trial Spawner", 0xFF9933,
                net.minecraft.world.level.block.Blocks.TRIAL_SPAWNER));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Vault",         0x9933FF,
                net.minecraft.world.level.block.Blocks.VAULT));
        // ── Added: storage / interactive block-entity groups ────────────
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Chest",         0xFFAA00,
                net.minecraft.world.level.block.Blocks.CHEST, net.minecraft.world.level.block.Blocks.TRAPPED_CHEST));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Barrel",        0xCC8833,
                net.minecraft.world.level.block.Blocks.BARREL));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Shulker Box",   0xAA44FF,
                net.minecraft.world.level.block.Blocks.SHULKER_BOX));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Ender Chest",   0x00CC88,
                net.minecraft.world.level.block.Blocks.ENDER_CHEST));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Hopper",        0x666666,
                net.minecraft.world.level.block.Blocks.HOPPER));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Furnace",       0xFF8844,
                net.minecraft.world.level.block.Blocks.FURNACE, net.minecraft.world.level.block.Blocks.BLAST_FURNACE,
                net.minecraft.world.level.block.Blocks.SMOKER));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Beacon",        0x88FFFF,
                net.minecraft.world.level.block.Blocks.BEACON));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Conduit",       0x44CCFF,
                net.minecraft.world.level.block.Blocks.CONDUIT));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Beehive",       0xFFCC44,
                net.minecraft.world.level.block.Blocks.BEEHIVE, net.minecraft.world.level.block.Blocks.BEE_NEST));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("End Portal Frame", 0xAA88FF,
                net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Sculk Catalyst", 0x0088AA,
                net.minecraft.world.level.block.Blocks.SCULK_CATALYST, net.minecraft.world.level.block.Blocks.SCULK_SHRIEKER));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Mob Heads",     0xFFEEDD,
                net.minecraft.world.level.block.Blocks.SKELETON_SKULL,
                net.minecraft.world.level.block.Blocks.WITHER_SKELETON_SKULL,
                net.minecraft.world.level.block.Blocks.PLAYER_HEAD,
                net.minecraft.world.level.block.Blocks.ZOMBIE_HEAD,
                net.minecraft.world.level.block.Blocks.CREEPER_HEAD,
                net.minecraft.world.level.block.Blocks.DRAGON_HEAD,
                net.minecraft.world.level.block.Blocks.PIGLIN_HEAD));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("Crafter",       0xAA7755,
                net.minecraft.world.level.block.Blocks.CRAFTER));
        BLOCK_ESP_GROUPS.add(new BlockEspGroup("TNT",           0xFF4422,
                net.minecraft.world.level.block.Blocks.TNT));
    }

    /** Read by the world renderer each frame. Replaced atomically by the scan thread. */
    public static volatile java.util.List<BlockEspMatch> blockEspMatches = java.util.Collections.emptyList();

    private static int blockEspScanTick = 0;
    private static net.minecraft.world.level.Level blockEspLastWorld = null;

    // ── Global ESP range ──────────────────────────────────────────────────
    /** Global ESP scan range in chunks. UI-clamped to [4, 32]. */
    public static int espRange = 16;
    private static volatile boolean espScanRunning = false;
    private static Thread espScanThread;

    /**
     * Called every game tick from SkyzClientMod.
     */
    public static void tick(Minecraft client) {
        if (client.player == null) return;

        // Fullbright: set gamma to maximum each tick
        if (fullbright) {
            try {
                // In 1.21.11, OptionInstance<Double> stores value in field named "value"
                // setValue() is clamped to [0,1], so we write the backing field directly
                var gammaOpt = client.options.gamma();
                gammaOpt.set(1.0); // set to max vanilla first
                // Now force the backing "value" field past the clamp
                for (var f : gammaOpt.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        Object current = f.get(gammaOpt);
                        if (current instanceof Double) {
                            f.set(gammaOpt, 100.0);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        // Toggle sprint
        if (toggleSprint && client.options.keyUp.isDown()) {
            client.player.setSprinting(true);
        }

        // Toggle sneak - hold sneak state persistently when toggle is ON
        // The user disables this from the Built-in Mods tab in the HUD editor.
        // TODO(port): the yarn version also overwrote player.input.playerInput
        // with a new PlayerInput record to force the sneak key down. In Mojang
        // 26.1 the equivalent is `Input` with a `keyPresses` field, but
        // setShiftKeyDown(true) is sufficient to keep the sneak state on for
        // movement; the keypress override is dropped per port notes.
        if (toggleSneak) {
            client.player.setShiftKeyDown(true);
        }

        // Anti-AFK - jump every 30 seconds
        if (antiAfk) {
            afkTick++;
            if (afkTick >= 600) {
                afkTick = 0;
                if (client.player.onGround()) client.player.jumpFromGround();
            }
        }

        // Combo reset after 3.5s
        if (comboCount > 0 && System.currentTimeMillis() - lastHitTime > COMBO_RESET_MS) {
            comboCount = 0;
        }

        // Autoclicker — fires left-MouseButtonEvent attack at the configured CPS while
        // the user is holding attack and looking at a mob. Mob-only gate
        // keeps it from spamming blocks (which would also break them).
        if (autoclicker && client.options.keyAttack.isDown()
                && client.crosshairPickEntity != null
                && client.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity
                && client.gameMode != null) {
            int periodTicks = Math.max(1, 20 / Math.max(1, autoclickerCps));
            if (++autoclickerTick >= periodTicks) {
                autoclickerTick = 0;
                client.gameMode.attack(client.player, client.crosshairPickEntity);
                client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                recordClick();
                registerHit();
            }
        } else {
            autoclickerTick = 0;
        }

        // Spawner alert — once per second, scan a small cube around the player
        // for SPAWNER / TRIAL_SPAWNER blocks. Each detected block pings once
        // (sound + chat line) and is added to a session set so we don't spam.
        if (spawnerAlert) tickSpawnerAlert(client);

        // Dynamic FPS — drop to 30 fps when window unfocused; restore on refocus
        applyDynamicFps(client);
    }

    /**
     * Scan loaded chunks around the player for spawners, trial spawners, and
     * ominous vaults. We iterate each chunk's {@code blockEntities} map
     * rather than walking individual block positions — every target block
     * here has a block entity, so a chunk-walk is ~100× cheaper than a cubic
     * block scan and lets us push the radius way out without a perf hit.
     *
     * <p>Concretely: an 8-chunk (128-block) radius walks ~289 chunks, each
     * with maybe 5–20 block entities, so ~3,000–6,000 entries per second.
     * Compare to a 128-block cubic block-state scan: 257³ = ~17M lookups/sec.
     *
     * <p>De-duped via {@link #SPAWNER_PINGED} so each block only pings on
     * first detection. Switching world or dimension clears the set —
     * re-entering an old chunk re-pings, which is fine because the chunk
     * has been freshly re-loaded client-side.
     */
    private static void tickSpawnerAlert(Minecraft client) {
        net.minecraft.client.multiplayer.ClientLevel world = client.level;
        if (world == null || client.player == null) return;

        // Reset ping memory on world change (dimension hop, server switch, etc.).
        if (spawnerLastWorld != world) {
            SPAWNER_PINGED.clear();
            spawnerLastWorld = world;
        }

        // Once per second.
        if (++spawnerScanTick < 20) return;
        spawnerScanTick = 0;

        // Driven by the global ESP range slider (chunks). Cheap because we
        // walk block-entity maps, not block states.
        final int CHUNK_R = Math.max(4, Math.min(32, espRange));
        net.minecraft.world.level.ChunkPos pCp = client.player.chunkPosition();

        for (int dx = -CHUNK_R; dx <= CHUNK_R; dx++) {
            for (int dz = -CHUNK_R; dz <= CHUNK_R; dz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk =
                        world.getChunk(pCp.x + dx, pCp.z + dz);
                if (chunk == null) continue;
                java.util.Map<net.minecraft.core.BlockPos,
                        net.minecraft.world.level.block.entity.BlockEntity> bes = chunk.getBlockEntities();
                if (bes.isEmpty()) continue;
                for (java.util.Map.Entry<net.minecraft.core.BlockPos,
                        net.minecraft.world.level.block.entity.BlockEntity> e : bes.entrySet()) {
                    net.minecraft.world.level.block.entity.BlockEntity be = e.getValue();
                    net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
                    net.minecraft.world.level.block.Block block = state.getBlock();

                    String type = classifyAlertBlock(block, state);
                    if (type == null) continue;

                    net.minecraft.core.BlockPos pos = e.getKey();
                    if (SPAWNER_PINGED.add(pos)) {
                        pingSpawner(client, pos, type);
                    }
                }
            }
        }
    }

    /**
     * Returns a human-readable label if the block is one we want to ping for,
     * or null otherwise. Ominous vaults are gated on the {@code ominous}
     * BooleanProperty — regular vaults (non-ominous, found in normal trial
     * chambers) are skipped because they're far less interesting.
     */
    private static String classifyAlertBlock(net.minecraft.world.level.block.Block block,
                                             net.minecraft.world.level.block.state.BlockState state) {
        if (block == net.minecraft.world.level.block.Blocks.SPAWNER)        return "Spawner";
        if (block == net.minecraft.world.level.block.Blocks.TRIAL_SPAWNER)  return "Trial Spawner";
        if (block == net.minecraft.world.level.block.Blocks.VAULT) {
            // Read the ominous flag via property lookup so we don't compile-
            // bind the VaultBlock.OMINOUS field — keeps us forward-compatible
            // if Mojang renames the constant.
            for (net.minecraft.world.level.block.state.properties.Property<?> p : state.getProperties()) {
                if (p.getName().equals("ominous")
                        && p instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bp) {
                    if (state.getValue(bp)) return "Ominous Vault";
                    return null;
                }
            }
        }
        return null;
    }

    private static void pingSpawner(Minecraft client,
                                    net.minecraft.core.BlockPos pos,
                                    String typeLabel) {
        // High-pitched note-block pling — short, audible, easy to miss-and-find
        // again, and doesn't stack badly if multiple spawners trigger.
        try {
            client.player.playSound(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                    1.0f, 2.0f);
        } catch (Throwable ignored) {}

        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                "[Skyz] " + typeLabel + " detected at "
                        + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .copy().withStyle(net.minecraft.ChatFormatting.AQUA);
        client.player.displayClientMessage(msg, false);
    }

    /**
     * When dynamicFps is on, drop the max FPS to 30 while the window is
     * unfocused and restore the original cap on refocus. Toggling the feature
     * off also restores the saved value.
     */
    private static void applyDynamicFps(Minecraft client) {
        var maxFpsOpt = client.options.framerateLimit();
        if (!dynamicFps) {
            if (savedMaxFps != -1) {
                try { maxFpsOpt.set(savedMaxFps); } catch (Exception ignored) {}
                savedMaxFps = -1;
            }
            return;
        }
        boolean focused = client.isWindowActive();
        int current = maxFpsOpt.get();
        if (!focused && current > 30) {
            if (savedMaxFps == -1) savedMaxFps = current;
            try { maxFpsOpt.set(30); } catch (Exception ignored) {}
        } else if (focused && savedMaxFps != -1) {
            try { maxFpsOpt.set(savedMaxFps); } catch (Exception ignored) {}
            savedMaxFps = -1;
        }
    }

    /**
     * Called from a chat-receive listener. If the message looks like a
     * win/loss notification and autoGG is on, queue "gg" once per 30 s window.
     */
    public static void onChatMessage(Minecraft client, String text) {
        if (!autoGG || client.player == null || client.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoGgMs < 30_000) return;
        String t = text.toLowerCase();
        if (t.contains("won the game") || t.contains("victory") || t.contains("winner")
                || t.contains(" wins") || t.contains("game over")) {
            lastAutoGgMs = now;
            client.getConnection().sendChat("gg");
        }
    }

    /** Called when player lands a hit (detected by tracking attack key presses in game). */
    public static void registerHit() {
        comboCount++;
        lastHitTime = System.currentTimeMillis();
    }

    /** Called when player uses a totem of undying. */
    public static void registerTotemPop() {
        totemPops++;
    }

    /**
     * Background ESP scan thread. Runs a block ESP rescan every ~1.5 s on a
     * daemon thread so the main render thread isn't blocked. Reads chunk
     * sections directly — ClientLevel chunks are immutable-ish after load
     * (unload is the main hazard), and exceptions are swallowed so a torn
     * read during chunk unload just produces stale results until next pass.
     */
    public static synchronized void startEspScanThread() {
        if (espScanRunning) return;
        espScanRunning = true;
        espScanThread = new Thread(() -> {
            while (espScanRunning) {
                try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
                try { doBlockEspScan(); } catch (Throwable ignored) {}
            }
        }, "skyz-esp-scan");
        espScanThread.setDaemon(true);
        espScanThread.start();
    }

    private static void doBlockEspScan() {
        if (!blockEsp) { blockEspMatches = java.util.Collections.emptyList(); return; }
        Minecraft client = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel world = client.level;
        if (world == null || client.player == null) {
            blockEspMatches = java.util.Collections.emptyList();
            return;
        }
        // Snapshot enabled groups, split by detection path.
        java.util.List<BlockEspGroup> beGroups = new java.util.ArrayList<>();
        java.util.List<BlockEspGroup> bsGroups = new java.util.ArrayList<>();
        for (BlockEspGroup g : BLOCK_ESP_GROUPS) {
            if (!g.enabled) continue;
            (g.isBlockEntity ? beGroups : bsGroups).add(g);
        }
        if (beGroups.isEmpty() && bsGroups.isEmpty()) {
            blockEspMatches = java.util.Collections.emptyList();
            return;
        }
        java.util.List<BlockEspMatch> out = new java.util.ArrayList<>(256);
        net.minecraft.world.level.ChunkPos pCp;
        try { pCp = client.player.chunkPosition(); }
        catch (Throwable t) { return; }
        int R = Math.max(4, Math.min(32, espRange));

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk;
                try {
                    chunk = world.getChunkSource().getChunk(pCp.x + dx, pCp.z + dz, false);
                } catch (Throwable t) { continue; }
                if (chunk == null) continue;

                // Block-entity path — fast, just walk the BE map.
                if (!beGroups.isEmpty()) {
                    try {
                        for (java.util.Map.Entry<net.minecraft.core.BlockPos,
                                net.minecraft.world.level.block.entity.BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                            net.minecraft.world.level.block.Block block = e.getValue().getBlockState().getBlock();
                            for (BlockEspGroup g : beGroups) {
                                boolean matched = false;
                                for (net.minecraft.world.level.block.Block target : g.blocks) {
                                    if (block == target) {
                                        out.add(new BlockEspMatch(e.getKey(), g.color));
                                        matched = true;
                                        break;
                                    }
                                }
                                if (matched) break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // Block-state path — palette pre-check per section, then iterate.
                if (!bsGroups.isEmpty()) {
                    try {
                        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                        int bottomY = chunk.getMinBuildHeight();
                        int baseChunkX = (pCp.x + dx) << 4;
                        int baseChunkZ = (pCp.z + dz) << 4;
                        for (int si = 0; si < sections.length; si++) {
                            net.minecraft.world.level.chunk.LevelChunkSection sec = sections[si];
                            if (sec == null || sec.hasOnlyAir()) continue;
                            // Cheap palette pre-check: skip whole section if no target block present.
                            boolean hasTarget = false;
                            for (BlockEspGroup g : bsGroups) {
                                for (net.minecraft.world.level.block.Block tb : g.blocks) {
                                    if (sec.maybeHas(s -> s.is(tb))) { hasTarget = true; break; }
                                }
                                if (hasTarget) break;
                            }
                            if (!hasTarget) continue;
                            int baseY = bottomY + si * 16;
                            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                                net.minecraft.world.level.block.state.BlockState s = sec.getBlockState(x, y, z);
                                net.minecraft.world.level.block.Block b = s.getBlock();
                                for (BlockEspGroup g : bsGroups) {
                                    boolean matched = false;
                                    for (net.minecraft.world.level.block.Block target : g.blocks) {
                                        if (b == target) {
                                            out.add(new BlockEspMatch(
                                                    new net.minecraft.core.BlockPos(baseChunkX + x, baseY + y, baseChunkZ + z),
                                                    g.color));
                                            matched = true;
                                            break;
                                        }
                                    }
                                    if (matched) break;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
        // Cap to prevent insane render cost — keep nearest 2000.
        if (out.size() > 2000) {
            net.minecraft.world.phys.Vec3 pp = new net.minecraft.world.phys.Vec3(
                    client.player.getX(), client.player.getY(), client.player.getZ());
            out.sort((a, b) -> Double.compare(
                    a.pos().distToCenterSqr(pp), b.pos().distToCenterSqr(pp)));
            out = new java.util.ArrayList<>(out.subList(0, 2000));
        }
        blockEspMatches = out;
    }
}
