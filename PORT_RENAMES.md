# Skyz Client 1.21.11 → 26.1 Yarn-to-Mojang Rename Reference

This is the authoritative rename map for the port. Use it for find/replace and
verify against the [NeoForged 26.1 primer](https://docs.neoforged.net/primer/docs/26.1/)
when a name isn't listed here.

## Core packages — same `net.minecraft.*` root but different leaves

| Yarn (1.21.11) | Mojang (26.1) | Notes |
|---|---|---|
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` | |
| `net.minecraft.client.gui.DrawContext` | `net.minecraft.client.gui.GuiGraphics` | All `DrawContext` parameters become `GuiGraphics` |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.ResourceLocation` | |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` | |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` | |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` | |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` | |
| `net.minecraft.client.world.ClientWorld` | `net.minecraft.client.multiplayer.ClientLevel` | |
| `net.minecraft.world.chunk.ChunkSection` | `net.minecraft.world.level.chunk.LevelChunkSection` | |
| `net.minecraft.world.chunk.WorldChunk` | `net.minecraft.world.level.chunk.LevelChunk` | |
| `net.minecraft.entity.player.PlayerEntity` | `net.minecraft.world.entity.player.Player` | |
| `net.minecraft.client.network.ClientPlayerEntity` | `net.minecraft.client.player.LocalPlayer` | |
| `net.minecraft.entity.LivingEntity` | `net.minecraft.world.entity.LivingEntity` | |
| `net.minecraft.entity.Entity` | `net.minecraft.world.entity.Entity` | |
| `net.minecraft.entity.mob.MobEntity` | `net.minecraft.world.entity.Mob` | |
| `net.minecraft.entity.mob.HostileEntity` | `net.minecraft.world.entity.monster.Monster` | |
| `net.minecraft.entity.mob.PhantomEntity` | `net.minecraft.world.entity.monster.Phantom` | |
| `net.minecraft.entity.TntEntity` | `net.minecraft.world.entity.item.PrimedTnt` | |
| `net.minecraft.entity.EquipmentSlot` | `net.minecraft.world.entity.EquipmentSlot` | |
| `net.minecraft.util.Hand` | `net.minecraft.world.InteractionHand` | |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` | |
| `net.minecraft.util.math.ChunkPos` | `net.minecraft.world.level.ChunkPos` | |
| `net.minecraft.block.Block` | `net.minecraft.world.level.block.Block` | |
| `net.minecraft.block.Blocks` | `net.minecraft.world.level.block.Blocks` | |
| `net.minecraft.block.BlockState` | `net.minecraft.world.level.block.state.BlockState` | |
| `net.minecraft.block.BlockEntityProvider` | `net.minecraft.world.level.block.EntityBlock` | |
| `net.minecraft.block.entity.BlockEntity` | `net.minecraft.world.level.block.entity.BlockEntity` | |
| `net.minecraft.block.entity.MobSpawnerBlockEntity` | `net.minecraft.world.level.block.entity.SpawnerBlockEntity` | |
| `net.minecraft.block.entity.*` (Chest/Barrel/Hopper/Shulker/Furnace/Dispenser/TrappedChest/Vault/TrialSpawner) | `net.minecraft.world.level.block.entity.*` | Same simple names |
| `net.minecraft.item.Items` | `net.minecraft.world.item.Items` | |
| `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` | But may need `ItemStackTemplate` if created at static init! |
| `net.minecraft.client.option.KeyBinding` | `net.minecraft.client.KeyMapping` | |
| `net.minecraft.client.option.GameOptions` | `net.minecraft.client.Options` | |
| `net.minecraft.client.option.SimpleOption` | `net.minecraft.client.OptionInstance` | |
| `net.minecraft.client.util.InputUtil` | `com.mojang.blaze3d.platform.InputConstants` | |
| `net.minecraft.client.font.TextRenderer` | `net.minecraft.client.gui.Font` | |
| `net.minecraft.registry.Registries` | `net.minecraft.core.registries.BuiltInRegistries` | |
| `net.minecraft.registry.RegistryKey` | `net.minecraft.resources.ResourceKey` | |
| `net.minecraft.client.network.ServerInfo` | `net.minecraft.client.multiplayer.ServerData` | |
| `net.minecraft.entity.effect.StatusEffectCategory` | `net.minecraft.world.effect.MobEffectCategory` | |
| `net.minecraft.util.hit.HitResult` | `net.minecraft.world.phys.HitResult` | |
| `net.minecraft.util.hit.EntityHitResult` | `net.minecraft.world.phys.EntityHitResult` | |
| `net.minecraft.world.biome.Biome` | `net.minecraft.world.level.biome.Biome` | |
| `net.minecraft.sound.SoundEvents` | `net.minecraft.sounds.SoundEvents` | |
| `net.minecraft.sound.SoundEvent` | `net.minecraft.sounds.SoundEvent` | |
| `net.minecraft.SharedConstants` | `net.minecraft.SharedConstants` | (same package) |
| `net.minecraft.util.Util` | `net.minecraft.Util` | |
| `net.minecraft.state.property.Property` | `net.minecraft.world.level.block.state.properties.Property` | |
| `net.minecraft.state.property.BooleanProperty` | `net.minecraft.world.level.block.state.properties.BooleanProperty` | |

## Screen classes (`net.minecraft.client.gui.screen.*`)

These move under `net.minecraft.client.gui.screens.*` (note the `screens` plural).

| Yarn | Mojang | Renamed? |
|---|---|---|
| `Screen` | `Screen` | same name |
| `TitleScreen` | `TitleScreen` | same |
| `multiplayer.MultiplayerScreen` | `multiplayer.JoinMultiplayerScreen` | **renamed** |
| `GameMenuScreen` | `PauseScreen` | **renamed** |
| `MessageScreen` | `GenericMessageScreen` | **renamed** |
| `OptionsScreen` | `OptionsScreen` | same |
| `DeathScreen` | `DeathScreen` | same |
| `DisconnectedScreen` | `DisconnectedScreen` | same |
| `ProgressScreen` | `ProgressScreen` | same |
| `ConnectScreen` | `ConnectScreen` | same |
| `OpenToLanScreen` | `ShareToLanScreen` | **renamed** |
| `advancement.AdvancementsScreen` | `advancements.AdvancementsScreen` | (pkg `advancements`) |
| `StatsScreen` | `StatsScreen` | same |
| `option.VideoOptionsScreen` | `options.VideoSettingsScreen` | **renamed** |
| `option.ControlsOptionsScreen` | `options.controls.ControlsScreen` | **renamed** |
| `option.LanguageOptionsScreen` | `options.LanguageSelectScreen` | **renamed** |
| `option.SoundOptionsScreen` | `options.SoundOptionsScreen` | same |
| `option.ChatOptionsScreen` | `options.ChatOptionsScreen` | same |
| `option.SkinOptionsScreen` | `options.SkinCustomizationScreen` | **renamed** |
| `option.AccessibilityOptionsScreen` | `options.AccessibilityOptionsScreen` | same |
| `option.TelemetryInfoScreen` | `options.TelemetryInfoScreen` | same |
| `option.CreditsAndAttributionScreen` | `options.CreditsAndAttributionScreen` | same |
| `pack.PackScreen` | `packs.PackSelectionScreen` | **renamed** |

## Method renames on common types

| Yarn | Mojang |
|---|---|
| `Text.literal(s)` | `Component.literal(s)` |
| `Text.translatable(k)` | `Component.translatable(k)` |
| `Identifier.of(ns, path)` | `ResourceLocation.fromNamespaceAndPath(ns, path)` |
| `Identifier.of(str)` | `ResourceLocation.parse(str)` |
| `screen.close()` (override) | `screen.onClose()` (override) |
| `MinecraftClient.getInstance().setScreen(s)` | `Minecraft.getInstance().setScreen(s)` (same) |
| `DrawContext.drawText(font, text, x, y, color, shadow)` | `GuiGraphics.drawString(font, text, x, y, color, shadow)` |
| `DrawContext.fill(x1, y1, x2, y2, color)` | `GuiGraphics.fill(...)` (same) |
| `DrawContext.fillGradient(...)` | `GuiGraphics.fillGradient(...)` (same) |
| `player.getEquippedStack(slot)` | `player.getItemBySlot(slot)` |
| `player.getStatHandler()` | `player.getStats()` |
| `player.networkHandler` (field) | `player.connection` |
| `connection.getAdvancementHandler()` | `connection.getAdvancements()` |
| `connection.sendChatMessage(s)` | `connection.sendChat(s)` |
| `player.isSneaking()` | `player.isShiftKeyDown()` |
| `player.setSneaking(b)` | `player.setShiftKeyDown(b)` |
| `player.isOnGround()` | `player.onGround()` |
| `player.jump()` | `player.jumpFromGround()` |
| `player.swingHand(hand)` | `player.swing(hand)` |
| `Hand.MAIN_HAND` | `InteractionHand.MAIN_HAND` |
| `options.getFov()` | `options.fov()` |
| `options.attackKey` | `options.keyAttack` |
| `options.useKey` | `options.keyUse` |
| `options.forwardKey` | `options.keyUp` |
| `options.getGamma()` | `options.gamma()` |
| `options.getMaxFps()` | `options.framerateLimit()` |
| `options.write()` | `options.save()` |
| `Util.getOperatingSystem().open(url)` | `Util.getPlatform().openUri(url)` |
| `world.getChunkManager()` | `level.getChunkSource()` |
| `client.world` | `client.level` |
| `player.getChunkPos()` | `player.chunkPosition()` |
| `player.getBlockPos()` | `player.blockPosition()` |
| `player.getX()/getY()/getZ()` | same |
| `chunk.getSectionArray()` | `chunk.getSections()` |
| `chunk.getBottomY()` | `chunk.getMinBuildHeight()` |
| `chunk.getBlockEntities()` | `chunk.getBlockEntities()` (same) |
| `sec.hasAny(predicate)` | `sec.maybeHas(predicate)` |
| `sec.getBlockState(x,y,z)` | `sec.getBlockState(x,y,z)` (same) |
| `sec.isEmpty()` | `sec.hasOnlyAir()` |
| `Items.TOTEM_OF_UNDYING` | `Items.TOTEM_OF_UNDYING` (same) |
| `SoundEvents.X` | `SoundEvents.X` (same) |
| `evt.value()` (RegistryEntry<SoundEvent>) | `evt.value()` (same) |

## Fabric API renames

| Yarn-era | Mojang-era 26.1 |
|---|---|
| `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` | `net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper` (or class same name, depends; verify) |
| `KeyBindingHelper.registerKeyBinding(kb)` | `KeyMappingHelper.registerKeyBinding(km)` |
| `KeyBinding.Category.MISC` | `KeyMapping.CATEGORY_MISC` (now a String constant) |
| `HudRenderCallback` | **REMOVED** — use `HudElementRegistry` for new HUD elements; for replacing vanilla, mixin into `Gui` |
| `ClientTickEvents` | `ClientTickEvents` (likely same) |
| `ClientReceiveMessageEvents` | `ClientReceiveMessageEvents` (likely same) |
| `ClientPlayConnectionEvents` | `ClientPlayConnectionEvents` (likely same) |
| `ModMenuApi.createModsScreen(parent)` | same |

## Rendering — the big one (used in WorldRendererMixin)

26.1 reworked the render pipeline heavily. The exact signature changes for
`WorldRenderer.render(...)` need verification — refer to the source via
`mcsrc.dev` or fetch the latest WorldRenderer source.

Probable changes:
- `WorldRenderer` → `LevelRenderer`
- `RenderTickCounter` → `DeltaTracker`
- `RenderLayer` → `RenderType`
- `RenderLayers.LINES` → `RenderType.LINES`
- `BufferBuilder.endNullable()` → `BufferBuilder.buildOrThrow()` / `build()`
- `BuiltBuffer` → `MeshData`
- `Tessellator` (with double-s) → `Tesselator` (single s in Mojang!)
- `VertexConsumer.lineWidth()` may have been removed in 26.x rendering refactor

If the WorldRenderer render() injection breaks fundamentally, consider
switching to `WorldRenderEvents.LAST` (Fabric API event) instead — it
provides a stable hook over the render-pipeline turmoil.

## Mixin descriptor strategy

Since MC 26.1 is unobfuscated, mixin method references use Mojang
names directly (no obf-mapped intermediary). Method names in our `@Inject`s:
- `init` — likely still `init` for screens
- `render` — same on most screens
- `tick` — same

Class targets (the only ones that need updating in `@Mixin(...)`):
- `@Mixin(GameMenuScreen.class)` → `@Mixin(PauseScreen.class)`
- `@Mixin(MessageScreen.class)` → `@Mixin(GenericMessageScreen.class)`
- `@Mixin(MultiplayerScreen.class)` → `@Mixin(JoinMultiplayerScreen.class)`
- `@Mixin(WorldRenderer.class)` → `@Mixin(LevelRenderer.class)` (likely)
- All others same simple name (TitleScreen, OptionsScreen, etc.)
