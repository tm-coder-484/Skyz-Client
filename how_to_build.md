# How to Build Skyz Client

> **Target:** Minecraft 1.21.11 · Fabric Loader 0.16.10 · Fabric API 0.140.2

---

## Prerequisites

| Tool | Required Version | Download |
|------|-----------------|---------|
| Java JDK | **21** (LTS) | [adoptium.net](https://adoptium.net/temurin/releases/?version=21) |
| Internet connection | Any | Required on first build to download Minecraft & Fabric |

> **Note:** Do **not** use Java 8, 11, or 17 — Minecraft 1.21.11 requires Java 21.
> Java 22/23/24 also work but 21 LTS is recommended.

---


## New Features

### Background Music
Place your music track in your `.minecraft` folder (next to the `mods` folder):
- **`music.wav`** — recommended (PCM, works with no extra codec)
- **`music.mp3`** — works if your JVM has an MP3 SPI codec installed

A silent placeholder `music.mp3` + `music.wav` are included in this project at the root.
Copy one of them into `.minecraft/` then replace it with your own track.

The **🔊 Music ON** button in the bottom-right of the title screen toggles playback.

### Drag-and-Drop Background Image
Drag any **PNG, JPG, or BMP** image directly onto the Skyz Client title screen.
The image is stretched to fill the screen (with a dark overlay to keep UI readable).
Your choice is saved to `skyz_client_bg.txt` and restored on next launch.
Click **[X] Clear** in the bottom-left hint text to remove it.

### Essential Mod Integration
Friends list and Cosmetics buttons now direct users to install **Essential** by EssentialGG:
- Modrinth: https://modrinth.com/mod/essential
- Website:  https://essential.gg
Essential is a client-side mod for Fabric/Forge that provides world hosting, friends, and cosmetics.

## Quick Build (Windows)

1. Open this folder in **File Explorer**
2. Double-click **`build.bat`**
3. Wait for the build to finish (5–15 min on first run, ~30s after)
4. Find the jar in **`build\libs\skyz-client-2.5.0.jar`**

That's it — `build.bat` handles everything including downloading the Gradle wrapper.

---

## Manual Build (Windows / macOS / Linux)

### Step 1 — Verify Java

```bash
java -version
# Should print: openjdk version "21.x.x" ...
```

If not, install Java 21 from [adoptium.net](https://adoptium.net/temurin/releases/?version=21) and set `JAVA_HOME`.

### Step 2 — Download the Gradle wrapper jar

The `gradle-wrapper.jar` is not included in source distributions.
Download it once and place it at `gradle/wrapper/gradle-wrapper.jar`:

**Windows (PowerShell):**
```powershell
Invoke-WebRequest `
  -Uri "https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar" `
  -OutFile "gradle\wrapper\gradle-wrapper.jar"
```

**macOS / Linux:**
```bash
curl -L \
  "https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar" \
  -o gradle/wrapper/gradle-wrapper.jar
chmod +x gradlew
```

### Step 3 — Build

**Windows:**
```bat
gradlew.bat build
```

**macOS / Linux:**
```bash
./gradlew build
```

On the **first run** this downloads:
- Gradle 8.8 (~120 MB)
- Minecraft 1.21.11 client jar
- Yarn mappings for 1.21.11
- Fabric Loader 0.16.10
- Fabric API 0.140.2+1.21.11

Subsequent builds use the local Gradle cache and complete in ~30 seconds.

### Step 4 — Find the output

```
build/libs/skyz-client-2.5.0.jar          ← install this
build/libs/skyz-client-2.5.0-sources.jar  ← source jar (optional)
```

---

## Installing the Built Mod

1. **Install Fabric Loader 0.16.10**
   Download from [fabricmc.net/use/installer](https://fabricmc.net/use/installer/) and run it for Minecraft 1.21.11.

2. **Install Fabric API**
   Download `fabric-api-0.140.2+1.21.11.jar` from [Modrinth](https://modrinth.com/mod/fabric-api) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api).

3. **Copy the mod jar**
   Put `skyz-client-2.5.0.jar` into your `.minecraft/mods/` folder.
   
   Typical locations:
   - **Windows:** `%APPDATA%\.minecraft\mods\`
   - **macOS:** `~/Library/Application Support/minecraft/mods/`
   - **Linux:** `~/.minecraft/mods/`

4. **Launch Minecraft**
   Open the Minecraft Launcher, select the **Fabric 1.21.11** profile, and click Play.
   You should see the Skyz Client title screen replace the vanilla one.

---

## Optional: Installing the Bundled Mods

The Skyz Client UI references these mods. Install them alongside Skyz Client for the full experience:

| Mod | Version | Link |
|-----|---------|------|
| Sodium | 0.8.2 | [Modrinth](https://modrinth.com/mod/sodium/version/mc1.21.11-0.8.2-fabric) |
| Iris Shaders | 1.10.4 | [Modrinth](https://modrinth.com/mod/iris/version/1.10.4+1.21.11-fabric) |
| Mod Menu | 17.0.0-β1 | [Modrinth](https://modrinth.com/mod/modmenu/version/17.0.0-beta.1) |
| Lithium | 0.14.3 | [Modrinth](https://modrinth.com/mod/lithium) |
| FerriteCore | 7.0.0 | [Modrinth](https://modrinth.com/mod/ferrite-core) |
| EntityCulling | 1.7.2 | [Modrinth](https://modrinth.com/mod/entityculling) |

---

## Project Structure

```
skyz-client-mod/
│
├── build.bat                          ← Windows one-click build
├── how_to_build.md                    ← This file
├── build.gradle                       ← Gradle build configuration
├── gradle.properties                  ← Version pins (MC, Fabric, Yarn)
├── settings.gradle                    ← Project name + plugin repos
│
├── gradle/wrapper/
│   ├── gradle-wrapper.jar             ← Download manually (see Step 2)
│   └── gradle-wrapper.properties      ← Points to Gradle 8.8
│
└── src/main/
    ├── java/net/skyz/client/
    │   ├── SkyzClientMod.java         ← Mod entrypoint (ClientModInitializer)
    │   ├── mixin/
    │   │   └── TitleScreenMixin.java  ← Intercepts vanilla TitleScreen.init()
    │   ├── screen/
    │   │   ├── SkyzTitleScreen.java   ← Main menu (logo, buttons, particles)
    │   │   ├── SkyzModsScreen.java    ← Mods browser with search + filters
    │   │   └── SkyzModConfigScreen.java ← Per-mod config with tabs
    │   └── util/
    │       ├── SkyzColors.java        ← ARGB colour palette
    │       ├── SkyzRenderHelper.java  ← Draw helpers (panels, gradients, dividers)
    │       ├── SkyzButton.java        ← Animated button widget
    │       ├── ParticleSystem.java    ← Floating mote particle system
    │       └── Toast.java             ← Slide-down toast notification
    │
    └── resources/
        ├── fabric.mod.json            ← Mod manifest (id, deps, mixins, entrypoint)
        └── skyz_client.mixins.json    ← Mixin config pointing to TitleScreenMixin
```

---

## Common Problems

### `JAVA_HOME is not set`
Set it to your JDK 21 installation:
- **Windows:** `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot`
- **macOS/Linux:** `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`

### `Could not resolve net.fabricmc:yarn:1.21.11+build.1:v2`
Your first build needs internet access to download mappings. Check your connection.
If it persists, run `gradlew.bat cleanloom` then try again.

### `Mixin injection failed` at runtime
Make sure you're running Fabric Loader **0.16.10** (not older). The mixin targets `TitleScreen.init()` using Yarn intermediary names — it should be stable across all 1.21.11 builds.

### Build succeeds but screen doesn't show
Check `latest.log` in `.minecraft/logs/` for `[Skyz Client]` lines. If the entrypoint didn't fire, confirm `fabric.mod.json` lists `"skyz_client"` as the mod ID and the mixin config is correctly referenced.

### `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`
The `gradle-wrapper.jar` is missing. Repeat Step 2 above.

---

## Development Setup (IntelliJ IDEA)

1. Open the `skyz-client-mod` folder as a Gradle project
2. Let IntelliJ import and sync — this runs `genSources` automatically
3. Run configuration: use the `Minecraft Client` run config created by Fabric Loom
4. Hot-reload: changes to screen logic take effect on next screen open without restarting MC (for most changes)

> Install the **Minecraft Development** plugin from the IntelliJ marketplace for mixin highlighting and autocompletion.

---

## Mod ID & Package

| Field | Value |
|-------|-------|
| Mod ID | `skyz_client` |
| Java package | `net.skyz.client` |
| Entrypoint class | `net.skyz.client.SkyzClientMod` |
| Mixin package | `net.skyz.client.mixin` |
| Environment | `client` only |

---

## Version Reference

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.16.10 |
| Essential (optional) | 1.3.x | [Modrinth](https://modrinth.com/mod/essential) |
| Fabric API | 0.140.2+1.21.11 |
| Yarn Mappings | 1.21.11+build.1 |
| Fabric Loom | 1.12-SNAPSHOT |
| Gradle | 8.8 |
| Java | 21 |
| Sodium (suggested) | 0.8.2 |
| Iris Shaders (suggested) | 1.10.4 |
| Mod Menu (suggested) | 17.0.0-β1 |
