# Skyz Client — 1.21.11 → 26.1.2 Port Status

**State:** Non-render layer 100% ported and compiling. Render layer scoped + recipe proven (`SkyzRenderHelper` done). Render rewrite of screens/HUD remains.

## Environment (one-time, already configured)
- **JDK 25** extracted to `C:\jdks\jdk25\jdk-25.0.3+9` (Foojay auto-download fails on this box — its tmp→jdks move errors). `gradle.properties` points at it via `org.gradle.java.installations.paths` + `auto-download=false`.
- Loom `1.15.5`, Gradle `9.4.0`, foojay `1.0.0`, Fabric API `0.145.4+26.1.2`, owo `0.13.0+26.1`, ModMenu `18.0.0-alpha.8`.
- Build: `.\gradlew.bat compileJava --no-daemon` (from repo root).

## DONE (compiles)
- Buildscript/metadata → 26.1.2 (new `net.fabricmc.fabric-loom` plugin, no mappings, Java 25, mixins.json `JAVA_25`).
- All vanilla renames across 54 files: `MinecraftClient`→`Minecraft`, `Text`→`Component`, `World`→`Level`, `PlayerEntity`→`Player`, world-storage (`LevelStorageSource`/`GameType`/`LevelResource`/`WorldOpenFlows`), etc.
- **26.1-specific MojMap renames** (discovered from the actual jar — these differ from NeoForge/MCP names):
  - `ResourceLocation` → **`net.minecraft.resources.Identifier`** (methods `fromNamespaceAndPath`, `parse`)
  - `Click` → **`net.minecraft.client.input.MouseButtonEvent`**; `mouseClicked(MouseButtonEvent, boolean)`
  - `CharInput` → **`net.minecraft.client.input.CharacterEvent`**; `charTyped(CharacterEvent)`
  - `OptionsScreen` → `net.minecraft.client.gui.screens.**options**.OptionsScreen`
  - `ServerAddress` → `net.minecraft.client.multiplayer.**resolver**.ServerAddress`
  - `RenderType` → `net.minecraft.client.renderer.**rendertype**.RenderType`
- Mixin targets: `GameMenuScreen`→`PauseScreen`, `MessageScreen`→`GenericMessageScreen`, `MultiplayerScreen`→`JoinMultiplayerScreen`, `ParticleManager`→`ParticleEngine`, `InGameHud`→`Gui`, `WorldRenderer`→`LevelRenderer`.
- `SkyzRenderHelper` — **ported** (param `GuiGraphics`→`GuiGraphicsExtractor`; text via `OwoUIGraphics.of(ctx).drawText`).
- ESP world-rendering **stubbed** (`SkyzEspRenderer` is a no-op; registration commented out in `SkyzClientMod`) — see "Blocked" below.

## THE 26.1 RENDERING PARADIGM (the key discovery)
26.1 replaced immediate-mode GUI rendering with a retained render-STATE system:
- `Screen` no longer has `render(GuiGraphics, mx, my, delta)`. It has `extractRenderState(GuiGraphicsExtractor, mx, my, delta)`.
- **`OwoUIGraphics extends GuiGraphicsExtractor`** and adds: `drawText(Component, float x, float y, float scale, int color)`, `drawGradientRect`, `drawRectOutline`, `drawCircle`, `drawLine`, `drawRing`, `drawPanel`.
- `GuiGraphicsExtractor` (base) keeps: `fill(x1,y1,x2,y2,col)`, `fillGradient(x1,y1,x2,y2,c1,c2)`, `blit(...)`, `enableScissor`/`disableScissor`, `pose()` → returns **`Matrix3x2fStack`** (2D, not PoseStack).
- Get an owo wrapper from a raw extractor: `OwoUIGraphics.of(guiGraphicsExtractor)`.
- owo `Surface` functional method is now `draw(OwoUIGraphics, ParentUIComponent)` (was `(GuiGraphics, comp)`).

## REMAINING WORK — render layer (~100 GuiGraphics errors)

### Per-screen transform (all 24 screens in `screen/`, both BaseUIModelScreen subclasses)
For each screen that overrides `render`/`renderBackground`:
1. `public void renderBackground(GuiGraphics ctx, int mx, int my, float d)` → `public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float d)`
2. `public void render(GuiGraphics ctx, int mx, int my, float d)` → `public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float d)`
3. `super.render(ctx, ...)` → `super.extractRenderState(ctx, ...)`
4. `SkyzRenderHelper.xxx(ctx, ...)` calls now pass `GuiGraphicsExtractor` directly — **no change needed** (helper already accepts it).
5. Direct text draws in screens: `ctx.drawCenteredString(font, s, x, y, col)` and `ctx.drawString(font, s, x, y, col)` → wrap once at top of method: `OwoUIGraphics g = OwoUIGraphics.of(ctx);` then `g.drawText(Component.literal(s), x, y, 1f, col, OwoUIGraphics.TextAnchor.CENTER)` for centered (verify TextAnchor enum name) or the left-anchored overload otherwise.
6. `parent.toast.render(ctx, width, delta)` calls → Toast must also be ported to `GuiGraphicsExtractor`/`OwoUIGraphics` (see below).
7. Screens with `mouseClicked(MouseButtonEvent, boolean)` already ported; verify `click.x()`/`click.y()` exist on `MouseButtonEvent` (likely `.x()`/`.y()` — if not, it has a position accessor; check the jar).

### Toast (`util/Toast.java`)
Param `GuiGraphics`→`GuiGraphicsExtractor`; text via `OwoUIGraphics.of(ctx).drawText(...)`. Same pattern as SkyzRenderHelper.

### SkyzHudRenderer (`render/SkyzHudRenderer.java`)
Called from `InGameHudMixin` (now `Gui`). The mixin's inject gives a `GuiGraphicsExtractor`. Convert all `GuiGraphics`→`GuiGraphicsExtractor`; text + item drawing via `OwoUIGraphics.of(ctx)` (item rendering: check `OwoUIGraphics`/`GuiGraphicsExtractor` for an item-render method — may need `renderItem`-equivalent or defer item icons).

### InGameHudMixin + InGameHudOverlayMixin
`@Mixin(Gui.class)`; inject into the new HUD render method (likely `render(GuiGraphicsExtractor, DeltaTracker)` — verify method name/signature from `Gui.class` via `javap`). Pass the extractor to `SkyzHudRenderer`.

### Surface lambdas (SkyzSurface + inline `(ctx, comp) -> ctx.fill(...)` in screens)
owo `Surface.draw(OwoUIGraphics, ParentUIComponent)`. The inline lambdas `(ctx, comp) -> ...` — `ctx` is now `OwoUIGraphics` (IS-A GuiGraphicsExtractor), so `ctx.fill(...)` and `SkyzRenderHelper.xxx(ctx, ...)` both still work. Should mostly compile unchanged once the functional type is `OwoUIGraphics`.

## BLOCKED (defer until upstream ships)
- **ESP world-rendering**: needs `fabric-rendering-v1` (`WorldRenderEvents`/`WorldRenderContext`) — NOT in Fabric API 0.145.4+26.1.2 (Fabric blog: "Renderer/Indigo modules may not be available for the initial release of 26.1"). ESP *detection* still runs; only drawing is dormant. `SkyzEspRenderer` is a documented stub; original logic in git history + `Sebbyo-mod2`'s `WorldRendererMixin`. Re-enable when the module lands, or port to a `LevelRenderer` mixin.

## Verify loop
`.\gradlew.bat compileJava --no-daemon 2>&1 | Out-File build-errors.txt -Encoding utf8` then grep `error:` / `symbol:` in `build-errors.txt`. Compiler caps at 100 errors — fix in batches and re-run.
