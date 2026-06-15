package net.sebbyo.combat.effect;

import java.util.List;
import java.util.Map;

/**
 * The reward / penalty effect sets and their per-effect caps. Pure data — no
 * Minecraft imports — so the stacking rules stay unit-testable. Caps are stored as
 * <b>max amplifier</b> (level − 1): cap 0 = level 1, cap 3 = level 4.
 */
public final class EffectCatalog {

    private EffectCatalog() {}

    public static final String SPEED = "minecraft:speed";

    /** Reward id → max amplifier. Speed has no entry → unbounded. */
    public static final Map<String, Integer> REWARD_CAPS = Map.ofEntries(
            Map.entry("minecraft:health_boost", 3),
            Map.entry("minecraft:absorption", 1),
            Map.entry("minecraft:regeneration", 0),
            Map.entry("minecraft:saturation", 1),
            Map.entry("minecraft:conduit_power", 1),
            Map.entry("minecraft:haste", 1),
            Map.entry("minecraft:resistance", 1),
            Map.entry("minecraft:fire_resistance", 1),
            Map.entry("minecraft:invisibility", 1),
            Map.entry("minecraft:hero_of_the_village", 1));

    public static final List<String> REWARDS = List.of(
            SPEED, "minecraft:saturation", "minecraft:conduit_power", "minecraft:haste",
            "minecraft:regeneration", "minecraft:resistance", "minecraft:fire_resistance",
            "minecraft:health_boost", "minecraft:absorption", "minecraft:invisibility",
            "minecraft:hero_of_the_village");

    /** Penalty id → max amplifier. */
    public static final Map<String, Integer> PENALTY_CAPS = Map.ofEntries(
            Map.entry("minecraft:blindness", 0),
            Map.entry("minecraft:nausea", 0),
            Map.entry("minecraft:darkness", 0),
            Map.entry("minecraft:bad_omen", 0),
            Map.entry("minecraft:glowing", 0),
            Map.entry("minecraft:jump_boost", 1),
            Map.entry("minecraft:slow_falling", 1),
            Map.entry("minecraft:hunger", 1),
            Map.entry("minecraft:slowness", 1),
            Map.entry("minecraft:mining_fatigue", 1),
            Map.entry("minecraft:weakness", 1));

    public static final List<String> PENALTIES = List.of(
            "minecraft:blindness", "minecraft:nausea", "minecraft:darkness", "minecraft:bad_omen",
            "minecraft:glowing", "minecraft:jump_boost", "minecraft:slow_falling", "minecraft:hunger",
            "minecraft:slowness", "minecraft:mining_fatigue", "minecraft:weakness");

    public static boolean isReward(String id) { return REWARDS.contains(id); }

    public static int rewardCap(String id) { return REWARD_CAPS.getOrDefault(id, Integer.MAX_VALUE); }

    public static int penaltyCap(String id) { return PENALTY_CAPS.getOrDefault(id, 0); }
}
