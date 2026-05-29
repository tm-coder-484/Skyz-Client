# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Skyz Client** (v4.3.0) is a Minecraft 1.21.11 **Fabric client-side mod** that replaces the vanilla title screen, pause menu, death screen, and other UI elements with a polished custom design system. It also provides an in-game HUD editor, cosmetics/friends integrations, and QoL gameplay toggles (sprint, fullbright, ESP, auto-GG, anti-AFK, etc.).

Key dependencies: **Java 21**, Fabric Loader 0.17.3, Fabric API 0.140.2, **owo-lib 0.13.0** (Wispforest XML UI framework), Yarn mappings.

## Build Commands

```bash
# Quick build (Windows)
build.bat

# Compile only — fastest iteration (~5s incremental)
.\gradlew.bat compileJava

# Full build — produces installable JAR
.\gradlew.bat build
# Output: build/libs/skyz-client-4.3.0.jar

# Launch Minecraft dev client
.\gradlew.bat runClient
```

First build downloads Gradle 8.8 + Minecraft client JAR + dependencies (~5–15 min). Subsequent builds are ~30 seconds.

**Dev iteration loop:**
1. Edit XML → reopen screen in-game (owo hot-reloads XML; no rebuild needed)
2. Edit Java → `.\gradlew.bat compileJava` → restart Minecraft
3. Press **K** in-game to open the kitchen-sink design validation screen

## Architecture

### Entry Point
`net.skyz.client.SkyzClientMod` implements `ClientModInitializer`. It registers keybindings, initializes state/config, and hooks `ClientTickEvents.START_CLIENT_TICK` for per-tick logic (CPS tracking, totem detection, auto-GG, etc.).

### Mixin Hooks
`src/main/java/net/skyz/client/mixin/` — 14 classes hook into vanilla Minecraft screens:
- `TitleScreenMixin` → replaces vanilla TitleScreen with `SkyzTitleScreen`
- `InGameHudMixin` → hooks HUD rendering
- `GameMenuScreenMixin` → replaces pause menu
- Similar patterns for death, disconnect, options, etc.

### Screen Layer
`screen/` — 24 screen classes. Modern screens extend `BaseUIModelScreen<FlowLayout>` (owo-lib):
- UI structure declared in `assets/skyz_client/owo_ui/*.xml`
- Java `build(FlowLayout root)` wires button clicks using `wire("id", runnable)`
- Surfaces (card styling, pill inputs) applied in Java: `flow.surface(SkyzSurface.CARD)`

### State & Config
| Class | Purpose |
|---|---|
| `SkyzClientState` | Central boolean/int/float toggles for QoL features + CPS tracking |
| `SkyzHudState` | HUD element positions, visibility, drag-drop layout state |
| `SkyzConfig` | JSON persistence to `.minecraft/` — loaded at mod init |

### Rendering
- **Low-level primitives** (`rounded rects`, gradients, dividers, particles) → `SkyzRenderHelper` via `DrawContext`
- **High-level layout** (buttons, flows, text) → owo-lib components
- **Hybrid pattern:** title screen draws background + particles + logo using `DrawContext` in `render()` *before* `super.render()`, so owo buttons layer on top
- All HUD elements (FPS counter, health bar, minimap overlay, etc.) → `SkyzHudRenderer`

### Async Pattern
Multiplayer server pinging and shader pack loading run on daemon threads. Results hop back to the render thread via `MinecraftClient.execute(Runnable)` to prevent UI freeze.

### Reflection-Based Integrations
- **Essential** (cosmetics/friends): no compile-time dep; 3 class-name candidates tried with fallback
- **Iris shaders**: guarded by `FabricLoader.isModLoaded("iris")`

## owo-lib Gotchas

These are hard-learned; avoid repeating these mistakes:

- `id` is an **XML attribute only** — using it as a child element silently fails
- `<spacer/>` expands on **both axes** — use explicit flow layouts instead
- `fill(50)` doesn't account for gaps — use `49` to avoid overflow
- Fluent builder return types are **lossy** — prefer statement form when chaining multiple properties
- Hex colors in XML: **`#AARRGGBB`** (alpha first, not last)
- Custom `Surface` implementations must be applied in **Java**, not XML

## UI Design Reference

`skyz-client.html` (Feather design system, 1800 lines) is the **visual source of truth** — consult it for colors, spacing, and component aesthetics before implementing any UI. `Skyz HUD Template.html` is the HUD layout reference.

`assets/skyz_client/owo_ui/_templates/` — legacy templates, marked for cleanup, not used.

## In-Progress Work

See `SKYZ_PORT_NOTES.md` for detailed session notes. Current status:
- Phases 1–2d complete (screen porting, mixin hookup, HUD renderer)
- Phase 2e next: `SkyzModConfigScreen` port to owo-lib, HUD Editor improvements

**Important:** only make aesthetic/structural changes unless explicitly asked to add or change functionality.
