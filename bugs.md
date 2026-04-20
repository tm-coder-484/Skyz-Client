# Bug Report — Skyz-Client

## Critical (could crash the client)

### Bug 1 — Index out of bounds in shader selection
**File**: `src/main/java/net/skyz/client/screen/SkyzShadersScreen.java:274`

`packs.get(selectedPack)` is called without checking if `selectedPack` is still a valid index after `loadShaderPacks()` rebuilds the list. If the new list is shorter than the previous one, this throws `IndexOutOfBoundsException`.

---

### Bug 2 — `parseHex` crashes on short input
**File**: `src/main/java/net/skyz/client/util/SkyzTheme.java:72`

`s.substring(2)` throws `StringIndexOutOfBoundsException` if `s` is fewer than 2 characters. No length validation is performed before the substring call.

---

## Logic Errors

### Bug 3 — Off-by-one in shader pack count display
**File**: `src/main/java/net/skyz/client/screen/SkyzShadersScreen.java:211`

Displays `packs.size() - 1` as the pack count, but "No Shaders (Vanilla)" is already included in the list. The displayed count is one less than the actual total number of entries.

---

### Bug 4 — Backwards path truncation logic
**File**: `src/main/java/net/skyz/client/screen/SkyzShadersScreen.java:298`

```java
path = "..." + path.substring(Math.min(path.length(), path.length() - (w - 20) / 5));
```

`Math.min` always selects the subtracted value (which is smaller), so the truncation calculation is incorrect and paths are not displayed as intended.

---

## Null / Empty String Risks

### Bug 5 — Empty biome name substring
**File**: `src/main/java/net/skyz/client/mixin/InGameHudMixin.java:535`

`raw.substring(0, 1)` is called without first checking that `raw` is non-empty. An empty biome path string would throw `StringIndexOutOfBoundsException`.

---

### Bug 6 — Empty item/armor name substring
**File**: `src/main/java/net/skyz/client/mixin/InGameHudMixin.java:357`

Same pattern as Bug 5 — `name.substring(0, 1)` is called without a length guard on `name`.

---

## Division by Zero

### Bug 7 — Compass offset modulo
**File**: `src/main/java/net/skyz/client/mixin/InGameHudMixin.java:337`

```java
int offset = (int)(yaw / 360f * (stripW / 2)) % (stripW / 2);
```

If `stripW` is 0, the modulo operation throws `ArithmeticException`.

---

### Bug 8 — FOV slider width
**File**: `src/main/java/net/skyz/client/screen/SkyzHudEditorScreen.java:420`

```java
0.5f + (float)((mx - sX) / sW) * 1.5f
```

If `sW` is 0, this throws `ArithmeticException`. No guard exists to ensure the slider has a non-zero width before this calculation.

---

## Minor / Precision

### Bug 9 — Minimap dot positioning precision loss
**File**: `src/main/java/net/skyz/client/mixin/InGameHudMixin.java:489-490`

```java
int dotX = cx + (int)(ex * mapW / (MINIMAP_RANGE * 2));
int dotY = cy + (int)(ez * mapH / (MINIMAP_RANGE * 2));
```

The integer cast truncates rather than rounds, causing entity dots to be slightly mispositioned. Using `Math.round()` would be more accurate.

---

### Bug 10 — `ServerInfo` constructor mismatch
**File**: `src/main/java/net/skyz/client/screen/SkyzMultiplayerScreen.java:243`

The `ServerInfo` constructor call may be missing required parameters for the 1.21.x API, which could cause a compile error or runtime failure depending on the exact API version.
