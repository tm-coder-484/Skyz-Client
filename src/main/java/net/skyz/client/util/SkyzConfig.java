package net.skyz.client.util;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.nio.file.*;

/**
 * Persists HUD element positions/enabled flags and built-in mod toggle states
 * to .minecraft/config/skyz_client.json.
 * Called from SkyzHudEditorScreen Save button and loaded at startup via SkyzClientMod.
 */
public final class SkyzConfig {
    private SkyzConfig() {}

    private static final String FILE_NAME = "skyz_client.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path getConfigPath() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config").resolve(FILE_NAME);
    }

    /** Saves current HUD layout and toggle states to disk. */
    public static void save() {
        try {
            JsonObject root = new JsonObject();

            // HUD elements
            JsonArray elements = new JsonArray();
            for (SkyzHudState.HudElementState el : SkyzHudState.ELEMENTS) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name",    el.name);
                obj.addProperty("x",       el.x);
                obj.addProperty("y",       el.y);
                obj.addProperty("w",       el.w);
                obj.addProperty("h",       el.h);
                obj.addProperty("enabled", el.enabled);
                elements.add(obj);
            }
            root.add("elements", elements);

            // Vanilla suppression
            root.addProperty("hideVanillaHealth", SkyzHudState.hideVanillaHealth);
            root.addProperty("hideVanillaHunger", SkyzHudState.hideVanillaHunger);
            root.addProperty("hideVanillaArmor",  SkyzHudState.hideVanillaArmor);

            // Built-in mods
            root.addProperty("toggleSprint",    SkyzClientState.toggleSprint);
            root.addProperty("toggleSneak",     SkyzClientState.toggleSneak);
            root.addProperty("fullbright",      SkyzClientState.fullbright);
            root.addProperty("noFog",           SkyzClientState.noFog);
            root.addProperty("noPumpkinBlur",   SkyzClientState.noPumpkinBlur);
            root.addProperty("antiAfk",         SkyzClientState.antiAfk);
            root.addProperty("autoGG",          SkyzClientState.autoGG);
            root.addProperty("noFireOverlay",   SkyzClientState.noFireOverlay);
            root.addProperty("coloredHitboxes", SkyzClientState.coloredHitboxes);
            root.addProperty("toggleChat",      SkyzClientState.toggleChat);
            root.addProperty("fovMultiplier",   SkyzClientState.fovMultiplier);

            // Theme
            root.addProperty("theme", SkyzTheme.getCurrent());

            Path cfg = getConfigPath();
            Files.createDirectories(cfg.getParent());
            Files.writeString(cfg, GSON.toJson(root));
        } catch (Exception e) {
            net.skyz.client.SkyzClientMod.LOGGER.warn("[Skyz] Could not save config: {}", e.getMessage());
        }
    }

    /** Loads saved config. Returns false if no config exists yet. */
    public static boolean load() {
        Path cfg = getConfigPath();
        if (!Files.exists(cfg)) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(cfg)).getAsJsonObject();

            // HUD elements - match by name
            if (root.has("elements")) {
                for (JsonElement el : root.getAsJsonArray("elements")) {
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    SkyzHudState.HudElementState state = SkyzHudState.find(name);
                    if (state != null) {
                        state.x       = obj.get("x").getAsInt();
                        state.y       = obj.get("y").getAsInt();
                        state.w       = obj.get("w").getAsInt();
                        state.h       = obj.get("h").getAsInt();
                        state.enabled = obj.get("enabled").getAsBoolean();
                    }
                }
            }

            if (root.has("hideVanillaHealth")) SkyzHudState.hideVanillaHealth = root.get("hideVanillaHealth").getAsBoolean();
            if (root.has("hideVanillaHunger")) SkyzHudState.hideVanillaHunger = root.get("hideVanillaHunger").getAsBoolean();
            if (root.has("hideVanillaArmor"))  SkyzHudState.hideVanillaArmor  = root.get("hideVanillaArmor").getAsBoolean();

            if (root.has("toggleSprint"))    SkyzClientState.toggleSprint    = root.get("toggleSprint").getAsBoolean();
            if (root.has("toggleSneak"))     SkyzClientState.toggleSneak     = root.get("toggleSneak").getAsBoolean();
            if (root.has("fullbright"))      SkyzClientState.fullbright      = root.get("fullbright").getAsBoolean();
            if (root.has("noFog"))           SkyzClientState.noFog           = root.get("noFog").getAsBoolean();
            if (root.has("noPumpkinBlur"))   SkyzClientState.noPumpkinBlur   = root.get("noPumpkinBlur").getAsBoolean();
            if (root.has("antiAfk"))         SkyzClientState.antiAfk         = root.get("antiAfk").getAsBoolean();
            if (root.has("autoGG"))          SkyzClientState.autoGG          = root.get("autoGG").getAsBoolean();
            if (root.has("noFireOverlay"))   SkyzClientState.noFireOverlay   = root.get("noFireOverlay").getAsBoolean();
            if (root.has("coloredHitboxes")) SkyzClientState.coloredHitboxes = root.get("coloredHitboxes").getAsBoolean();
            if (root.has("toggleChat"))      SkyzClientState.toggleChat      = root.get("toggleChat").getAsBoolean();
            if (root.has("fovMultiplier"))   SkyzClientState.fovMultiplier   = root.get("fovMultiplier").getAsFloat();
            if (root.has("theme"))           SkyzTheme.apply(root.get("theme").getAsInt());

            return true;
        } catch (Exception e) {
            net.skyz.client.SkyzClientMod.LOGGER.warn("[Skyz] Could not load config: {}", e.getMessage());
            return false;
        }
    }
}
