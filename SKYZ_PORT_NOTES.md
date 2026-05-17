# Skyz Client owo-lib Port — Working Notes

Read this file at the start of any new session to recover context after `/compact`.

## Project

- **Mod**: Skyz Client v3.0.2, a Minecraft Fabric mod styling main menu / HUD / screens
- **MC**: 1.21.11, Yarn `1.21.11+build.1`, Java 21
- **Working dir**: `D:\tmaco0\My Documents\Downloads\Sebbyo-mod2`
- **Visual reference**: `skyz-client.html` (1799 lines) — the Feather-style design system being ported. `Skyz HUD Template.html` is the HUD reference.
- **User preference**: don't change functional features, only aesthetics. Match the HTML reference.

## Current build setup (gradle.properties)

```
loader_version=0.17.3       # bump from 0.16.10 was required by fabric-api 0.140.2
fabric_version=0.140.2+1.21.11
loom_version=1.15.3
mod_version=3.0.2
owo_version=0.13.0+1.21.11
```

`build.gradle` force-pins `fabric-api` and `fabric-loader` so Loom 1.15.3 doesn't transitively upgrade them (a newer fabric-api uses a MixinExtras EXPRESSION injection point that the bundled MixinExtras 0.4.1 in the loader doesn't support — caused log spam from the fluid renderer mixin).

## Phase progress

- ✅ **Phase 1** — kitchen sink validation (`SkyzKitchenSinkScreen`, K keybind in dev). Working.
- ✅ **Phase 2a** — `SkyzTitleScreen` ported. owo handles button column, Java still draws gradient bg / particles / SKYZ logo / splash / HUD strip / mute toggle / drop-zone / toast.
- ✅ **Phase 2b** — `SkyzMultiplayerScreen` ported. Server cards built programmatically. Per-card CONNECT, ✏ EDIT, 🗑 DELETE buttons. Toolbar 🔄 REFRESH. Search box + ALL/FAVOURITES filter pills. Direct Connect modal kept as Java overlay for single-field input. Async pinging (background threads per server) — fixes the open-page lag.
- ✅ **Phase 2b** — `SkyzAddServerScreen` ported (custom Skyz-themed replacement for vanilla `AddServerScreen`). Same constructor signature so it's a drop-in.
- ✅ **Phase 2b** — `SkyzModsScreen` ported. Uses owo for nav/toolbar/list. Cards built programmatically. ModMenu hand-off button + reflection-based config screen lookup preserved.
- ✅ **Phase 2c** — `SkyzHudEditorScreen` ported. Chrome (back + bottom toolbar) is owo XML, sidebar (tabs, scroll, rows) is built programmatically and absolutely positioned over the root. Drag-and-drop preview kept in `render()` BEFORE `super.render()` so owo overlays it. Sidebar collapse rebuilds the sidebar component tree from scratch (cheap).
- ✅ **Phase 2c** — In-game HUD extracted out of `InGameHudMixin` into a dedicated `net.skyz.client.render.SkyzHudRenderer` (~700 lines). Mixin shrunk to ~70 lines (just the `@Inject` hook + `tickAutoTotem`). Each HUD element now uses the polished `SkyzRenderHelper.drawSkyzHudPanel` (gradient body, top sheen, accent stripe, soft border) and `drawSkyzBar` (rounded gradient bars). Color-coded accents per element (green/yellow/red for FPS/ping/health/etc). ESP / hack rendering NOT touched — the user explicitly said don't change without asking.
- ✅ **Phase 2c** — `SkyzSingleplayerScreen` ported. Mirrors the Multiplayer pattern (search + ALL/SURVIVAL/CREATIVE/HARDCORE filters, scroll-wrapped list, per-card Play/Edit/Delete). Edit hooks vanilla `EditWorldScreen.create(...)` for proper rename/backup/reset-icon. Delete shows a Skyz-styled confirm overlay then walks the level dir bottom-up via `Files.walk + deleteIfExists`. `[+ Create World]` and `[🌍 Open Vanilla Selector]` both go to `SelectWorldScreen`.
- ✅ **Phase 2c** — `SkyzSettingsScreen` ported. Minimal XML (nav bar + scroll wrapper); all sections built programmatically — Theme grid (6 themes, custom Surface paints gradient + accent), Interface (5 toggles + 3 sliders), Minimap & ESP (4 toggles + zoom slider), Client Info (7 version rows w/ green/red dot). Theme cards use `mouseDown().subscribe(...)` for click-to-apply (cards aren't buttons). Toggle pills follow the HUD-editor `makePill` pattern. Sliders use owo's `SliderComponent` with int-range conversion at the boundary.
- ✅ **Phase 2c** — `SkyzShadersScreen` ported + freeze fix. Two-pane layout (260-px scroll list + flex info panel). Shader pack load + apply both run on dedicated **daemon threads**, then hop back to the render thread via `MinecraftClient.execute(...)` before mutating owo state. The original used `CompletableFuture.runAsync` (FJP common pool), which deadlocked against the render thread on Iris class loading and produced a 200-700 ms freeze on screen open. Iris reflection paths (`getIrisConfig`, `setShaderPackName`, `Iris.reload`, `ShaderPackScreen` ctor) preserved; per-pack Apply button surfaces errors via toast.
- ✅ **Phase 2c** — `SkyzFriendsScreen` ported + Essential opener fix. **The bug:** the original used `cls.getDeclaredConstructors()[0].newInstance()` to open Essential's social screen — `[0]` picks an arbitrary constructor, and on Essential's Kotlin classes that's typically a synthetic Kotlin-DSL constructor needing args, so `newInstance()` threw `IllegalArgumentException` and the code reported "social screen not found". **The fix:** explicit `getConstructor()` (no-arg), iterate three known class-name candidates (`SocialMenu` modern, `FriendsScreen` legacy, `SocialMenuScreen` older), then fall back to `EssentialAPI.getInstance().getGuiUtil().openSocialMenu()` for newer versions. Errors now surface inline in a red call-out box on the friends card AND via toast — never silent.
- ✅ **Phase 2d** — Vanilla MC screen re-skins (mixin-redirect pattern, `@Inject HEAD cancellable=true`):
    - `SkyzPauseMenuScreen` — replaces `GameMenuScreen`. 8 buttons: Resume / Achievements / Statistics / Feedback / Bug Report / Options / Open to LAN / Save and Quit (or Disconnect). Mixin gates on `showMenu == true` so the world-load no-UI case is left to vanilla. `shouldPause() = true` matches vanilla.
    - `SkyzDeathScreen` — replaces `DeathScreen`. Cause-of-death + score + Respawn (with vanilla's 20-tick cooldown) / Title Screen. Hardcore detection swaps title to "GAME OVER" and disables Respawn. `shouldCloseOnEsc() = false`.
    - `SkyzDisconnectedScreen` — replaces `DisconnectedScreen`. Carries `parent`, title, and `info.reason()` from the vanilla `DisconnectionInfo` record so server kick messages show through verbatim.
    - `SkyzOptionsScreen` — replaces top-level `OptionsScreen` (routing only). Buttons open vanilla `SkinOptionsScreen`/`SoundOptionsScreen`/`VideoOptionsScreen`/`ControlsOptionsScreen`/`LanguageOptionsScreen`/`ChatOptionsScreen`/`PackScreen`/`AccessibilityOptionsScreen`/`TelemetryInfoScreen`/`CreditsAndAttributionScreen` — each leaf stays vanilla because they're tied to `SimpleOption<T>.asWidget` machinery that's risky to recreate. `VideoOptionsScreen` constructor takes `(parent, mc, options)` not `(parent, options)` in 1.21.11 — gotcha worth noting.
    - **Realms case** intentionally not handled — `MinecraftClient` in 1.21.11 doesn't expose an `isConnectedToRealms()` helper, and we don't have a Realms re-skin. Quit-to-title from a Realm goes back to the title screen rather than `RealmsMainScreen`. Acceptable trade-off — Realm users can navigate to Realms from title.
    - **`mc.disconnect()` (no-arg) doesn't exist in 1.21.11**. The signature is `disconnect(Screen, boolean, boolean)` (pending screen + saving + transferring flags). Use that or `disconnectWithProgressScreen()` for save-and-quit.
- ⏳ **Next** — `SkyzModConfigScreen` (the fallback used when a mod has no ModMenu config screen).

## Key files

### Screens (`src/main/java/net/skyz/client/screen/`)

- `SkyzTitleScreen.java` — Phase 2a. `BaseUIModelScreen<FlowLayout>`.
- `SkyzMultiplayerScreen.java` — Phase 2b.
- `SkyzAddServerScreen.java` — Skyz-styled add/edit-server form.
- `SkyzModsScreen.java` — Phase 2b.
- `SkyzKitchenSinkScreen.java` — dev validation screen.
- `SkyzButtonRenderer.java` — `ButtonComponent.Renderer` impls: DEFAULT (blue glass), QUIT (red), NAV_BACK (subtle).
- `SkyzSurface.java` — owo `Surface` impls: CARD (rounded panel), PILL_INPUT (input backdrop).
- `SkyzServerIconCache.java` — process-lifetime favicon `WorldIcon` cache (shared across screen instances).
- `SkyzHudEditorScreen.java` — Phase 2c. `BaseUIModelScreen<FlowLayout>`. Sidebar built programmatically with `Positioning.absolute(...)`.
- `SkyzSingleplayerScreen.java` — Phase 2c. Mirrors `SkyzMultiplayerScreen` structure. Delete uses Java-rendered confirm overlay (same pattern as multiplayer's direct-connect modal).
- `SkyzSettingsScreen.java` — Phase 2c. Sections built programmatically inside an owo `<scroll>`. Theme cards use `mouseDown` events (not buttons) so the surface can paint the theme gradient.
- `SkyzShadersScreen.java` — Phase 2c. Daemon-threaded load + apply with render-thread hop-back. Two-pane: 260-px scroll list + flex info panel (rebuilt on selection/load). Iris integration is reflective (no compile-time dep); guard with `HAS_IRIS = FabricLoader.isModLoaded("essential")` before any reflection.
- `SkyzFriendsScreen.java` — Phase 2c. Single centred card. Tries 3 Essential class-name candidates with explicit no-arg constructor, falls back to `EssentialAPI.getGuiUtil().openSocialMenu()`, surfaces errors inline + toast. Doesn't auto-open Essential during init() — user clicks the OPEN button explicitly.
- `SkyzModConfigScreen.java` — NOT YET PORTED (still extends vanilla `Screen` and uses `SkyzButton`). It's the fallback shown when a mod doesn't have a ModMenu config screen.

### Render (`src/main/java/net/skyz/client/render/`)

- `SkyzHudRenderer.java` — Phase 2c. All in-game HUD element rendering (FPS, ping, health bars, minimap, compass, …). Owns the minimap's `NativeImageBackedTexture` + column-sample cache. Called from `InGameHudMixin#skyz$render`.

### XML models (`src/main/resources/assets/skyz_client/owo_ui/`)

- `title.xml`, `multiplayer.xml`, `add_server.xml`, `mods.xml`, `kitchen_sink.xml` — landed.
- `_templates/*.xml` — exists from earlier attempt with cross-file template refs but those proved unreliable in owo 0.13. **Use inline templates / programmatic component construction.** The `_templates/` files are dead code; ignore them.

### Utilities (`src/main/java/net/skyz/client/util/`)

- `SkyzRenderHelper.java` — `fillRoundedRect` (gradient version: `fillRoundedRectGradient`), `drawRoundedBorder`, `fillCircle`, `drawTextSpaced*`, `fillGradientV/H`. Used for both Java rendering and inside owo `Surface` lambdas.
- `SkyzColors.java` — `lerp(int,int,float)`, `TEXT_PRIMARY`, `TEXT_MUTED`, `CARD_BG`, `CARD_BORDER`, `withAlpha(int,int)`.
- `SkyzTheme.java` — `BG1/BG2/BG3`, `ACCENT1/ACCENT2`.
- `ParticleSystem.java`, `Toast.java`, `SkyzAudioManager.java`, `SkyzBackgroundManager.java`, `SkyzHudState.java` — keep as-is, called from screens.
- `SkyzButton.java`, `SkyzCircleButton.java` — vanilla widget classes still used by un-ported screens. Don't delete yet.

### Mixins (`src/main/java/net/skyz/client/mixin/`)

- `TitleScreenMixin.java` — replaces vanilla TitleScreen with `SkyzTitleScreen` via `@Inject(method="init", at=HEAD, cancellable=true)`.
- Other mixins (`InGameHudMixin`, `FogRendererMixin`, etc.) wire feature toggles, NOT relevant to the screen port.

## owo-lib API gotchas (learned the hard way)

1. **`id` is an XML ATTRIBUTE only.** `<flow-layout id="x">`, NOT `<flow-layout><id>x</id></flow-layout>`. The latter is silently ignored — `BaseUIComponent.parseProperties` only reads `element.getAttribute("id")`. This is the bug that made the first Multiplayer port look completely broken.

2. **`<spacer/>` expands BOTH axes** (`Sizing.expand(100)` for both). In a horizontal flow it expands vertically too — making the whole row grow to fill the parent's height and pushing siblings off-screen. **Use a manually-sized flow-layout** for the spacer pattern:
   ```xml
   <flow-layout direction="horizontal">
       <sizing>
           <horizontal method="expand">100</horizontal>
           <vertical method="content"/>
       </sizing>
   </flow-layout>
   ```

3. **`fill(50)` does not account for gaps.** Two children at `fill(50)` with `gap=3` overflow the parent by 3 px. Use `fill(49)` for 2-col rows.

4. **Fluent builder return-types are lossy.** `padding()`, `surface()`, `verticalAlignment()` etc. return `ParentUIComponent` — losing the `FlowLayout` type. Subsequent `.gap()` (FlowLayout-only) won't compile. **Use statement form** when you need both:
   ```java
   FlowLayout f = UIContainers.horizontalFlow(...);
   f.gap(10);
   f.padding(...);
   f.surface(...);
   f.verticalAlignment(...);
   ```
   Same for `LabelComponent`: call `horizontalTextAlignment()` BEFORE `horizontalSizing()` (which returns lossy `UIComponent`).

5. **`<text-box>` renders an opaque vanilla black backdrop by default.** Call `searchBox.setDrawsBackground(false)` and wrap it in a `<flow-layout id="..-wrapper">` whose `surface` you then set to `SkyzSurface.PILL_INPUT` in build().

6. **Renderer only draws BACKGROUND.** `ButtonComponent.Renderer.draw(...)` is responsible for chrome only — owo draws the text on top via `ButtonComponent.draw()` after the renderer.

7. **Custom Surfaces aren't XML-accessible.** Apply via Java in `build()`: `flow.surface(SkyzSurface.CARD)`.

8. **Owo's debug inspector toggles on LEFT-SHIFT** in dev environment. Press shift again to dismiss. `Owo.DEBUG` is the env-controlled flag.

9. **Hex colors must be `#AARRGGBB` (alpha first)** in owo XML, not `0xAARRGGBB`. Helper: ARGB-style integers in Java code work fine.

10. **`<scroll>` container** wraps a SINGLE child, not via `<children>`. `WrappingParentUIComponent.parseProperties` finds it via `UIParsing.allChildrenOfType(element, ELEMENT_NODE).get(0)` — that returns ALL element children in document order, including property elements like `<sizing>`. So **the wrapped child MUST come BEFORE any property elements** in the XML, otherwise `<sizing>` would be picked as `.get(0)` and parseComponent would throw "Unknown component type: sizing". Property elements (sizing/padding/etc.) are still applied — they're keyed by tag name in the children map, so document order doesn't affect them. Used successfully in `multiplayer.xml`:
    ```xml
    <scroll direction="vertical" id="scroll-server-list">
        <flow-layout id="server-list" direction="vertical">...</flow-layout>
        <sizing>
            <horizontal method="fill">100</horizontal>
            <vertical method="fill">100</vertical>
        </sizing>
        <scrollbar-thiccness>4</scrollbar-thiccness>
        <scroll-step>22</scroll-step>
    </scroll>
    ```
    Style the scrollbar from Java: `scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xCC8CD2FF)));`. Scrollbar is overlaid on top of content (not laid out next to it), so add `padding right=6` (or similar) to the wrapped flow-layout to keep card content out from under the scrollbar.

## Patterns established (reuse for HUD editor + remaining screens)

### Screen skeleton

```java
public class SkyzXxxScreen extends BaseUIModelScreen<FlowLayout> {
    private final SkyzTitleScreen parent;

    public SkyzXxxScreen(SkyzTitleScreen parent) {
        super(FlowLayout.class, Identifier.of("skyz_client", "xxx"));
        this.parent = parent;
    }

    @Override protected void build(FlowLayout root) {
        // wire(root, "btn-back", () -> client.setScreen(parent), SkyzButtonRenderer.NAV_BACK);
        // root.childById(FlowLayout.class, "search-wrapper").surface(SkyzSurface.PILL_INPUT);
        // textBox.setDrawsBackground(false);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) { /* no-op */ }

    @Override public void render(DrawContext ctx, int mx, int my, float d) {
        // Skyz gradient bg
        SkyzRenderHelper.fillGradientV(ctx, 0, 0,            width, height/3, SkyzTheme.BG1, SkyzTheme.BG2);
        SkyzRenderHelper.fillGradientV(ctx, 0, height/3,     width, height/3, SkyzTheme.BG2, SkyzTheme.BG3);
        SkyzRenderHelper.fillGradientV(ctx, 0, height*2/3,   width, height/3, SkyzTheme.BG3, SkyzTheme.BG1);
        super.render(ctx, mx, my, d);
        if (parent != null) parent.toast.render(ctx, width, d);
    }
}
```

### Card construction (Multiplayer/Mods pattern)

```java
FlowLayout card = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(N));
card.gap(10);
card.padding(Insets.of(8, 8, 12, 12));
card.verticalAlignment(VerticalAlignment.CENTER);
card.surface(SkyzSurface.CARD);
card.child(...);
```

### Async work in build()

Wrap DNS / disk I/O in daemon threads. See `SkyzMultiplayerScreen.pingAllServers()`.

## HUD Editor port plan (PRIORITY for next session)

### What it does

- Two tabs: HUD Elements (drag-to-place enabled toggles) and Built-in Mods (toggle/slider rows)
- Sidebar (collapsible via `[◄]` button) at right with the tabs + scroll
- Preview area (left side) shows simulated game background; user drags HUD elements freely, snap grid optional
- Selected element gets highlighted
- Snap grid cycles through 4/8/16/32 px

### Strategy

1. **Preview area stays in `render()`** — drag-and-drop is fundamentally incompatible with owo's component-tree layout. Just keep the existing draw code from `SkyzHudEditorScreen.render()` (the lines that draw the fake sky/ground gradient, snap-grid overlay when dragging, HUD element rectangles).

2. **Sidebar via owo XML** — `assets/skyz_client/owo_ui/hud_editor.xml`:
   - Root: `<stack-layout>` (so we can overlay the sidebar on top of nothing — preview is a `render()` thing, not in the model)
   - Sidebar: positioned absolutely, right side, vertical flow, fixed width 196.
   - Tab strip at top: 2 owo buttons; active tab uses `SkyzButtonRenderer.DEFAULT`, inactive uses `NAV_BACK`.
   - Below tabs: a `<flow-layout id="tab-content">` whose children we swap when the user clicks a tab.

3. **HUD Elements tab** — vertical list of mini-cards, one per `SkyzHudState.HudElementState`. Each card: enable toggle (owo `<small-checkbox>`) + label + drag handle. Click anywhere on the card to select/start drag (handle in `mouseClicked` overrides).

4. **Built-in Mods tab** — same vertical list pattern as Multiplayer/Mods cards. Each row is built from the `MODS` array. Three row types:
   - `HEADER` / `WARN` — just a styled label (different bg color for warn)
   - `toggle` — label + `<small-checkbox>` wired to `SkyzClientState.<field>`
   - `slider` — label + owo `<slider>`. For `fovMultiplier` (0.5–2.0) and `autoclickerCps` (1–20).

5. **Sidebar collapse** — `[◄]` button that toggles a `boolean sidebarOpen` and switches the sidebar's `horizontalSizing` between `Sizing.fixed(196)` and `Sizing.fixed(26)`. Or just hide the inner content and keep an icon-strip.

6. **Drag-and-drop** — preserved in `mouseClicked` / `mouseDragged` / `mouseReleased`. owo's `super.mouseClicked` runs first (sidebar buttons get their click); if it returns false, fall through to preview drag code. The HUD element being dragged needs its position updated in `SkyzHudState`, then redraw on next frame (no list rebuild needed since elements are drawn in `render()`).

### Files to write

- `assets/skyz_client/owo_ui/hud_editor.xml` — sidebar layout
- `SkyzHudEditorScreen.java` — full rewrite as `BaseUIModelScreen<...>`. Existing 601 lines are mostly the layout math + per-mod row drawing (which owo replaces) and the drag/drop input (which we keep). Probably ~400 lines after port.
- May also need to handle the `<small-checkbox>` and `<slider>` components — owo provides them; their callbacks update `SkyzClientState` fields directly.

### Reflection on the slider state

`SkyzClientState` has fields like `toggleSprint` (boolean) and `fovMultiplier` (float). The existing `SkyzHudEditorScreen` reads/writes via java reflection on the field name. Keep that pattern — wire the owo checkbox/slider's onChange to set the field via `Field.set()`.

## User preferences observed

- Wants premium / non-pixelated feel. Cap`CORNER_R = 9` on buttons (true pill) was approved.
- Wants emoji icons in button labels (🌍 ◈ 🧩 ⚙ ✦ 🧍 ✨ 🎛 🌅 👥 ⊗ etc).
- Prefers fewer / cleaner screen elements (removed BG-drag hint text).
- Doesn't want to add unrequested features.
- Will paste screenshots and describe issues directly. Read carefully — "right side cut off" meant 2-col `fill(50)` overflow, not a different problem.
- Recommends keeping diagnostic LOGGER.info lines in build() so wirings can be verified from console.

## Build / iterate workflow

```bash
.\gradlew.bat compileJava   # ~5s incremental, ~1.5min cold
.\gradlew.bat build         # also bundles XML resources
.\gradlew.bat runClient     # launch dev MC
```

Owo hot-reloads XML on screen-reopen in dev. Java changes require rebuild + relaunch.

## Don't do these (failed paths)

- Don't try cross-file owo template references (`skyz_client:_templates/...:name`). Doesn't work in 0.13.
- Don't try to make MC anti-alias rounded curves. No MSAA in MC's GUI pipeline. Pixelated edges are just the medium.
- Don't replace vanilla AddServerScreen via mixin — wrote `SkyzAddServerScreen` instead, same constructor.
- Don't sleep / poll on background work — use Daemon threads + let owo rebuild on next frame.
