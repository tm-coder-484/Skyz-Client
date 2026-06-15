package net.sebbyo.combat.effect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One player's accumulated reward + penalty effects, as {@code effectId → amplifier}.
 * Pure logic (no Minecraft types) implementing the stacking rules:
 * <ul>
 *   <li>{@link #applyReward} — grant/stack a reward up to its cap (Speed unbounded).</li>
 *   <li>{@link #onKill} — grant a random reward AND peel one level off a random penalty.</li>
 *   <li>{@link #onDeath} — if holding rewards, drop one level of a random reward; otherwise
 *       gain one level of a random penalty.</li>
 * </ul>
 * Amplifiers are 0-based (amp 0 = level 1). {@code amp(id) == -1} means "not present".
 */
public final class EffectLedger {

    private final Map<String, Integer> amps = new HashMap<>();

    public int amp(String id) { return amps.getOrDefault(id, -1); }

    public boolean has(String id) { return amps.containsKey(id); }

    public boolean isEmpty() { return amps.isEmpty(); }

    /** Immutable snapshot {@code id → amplifier} for rendering / persistence. */
    public Map<String, Integer> view() { return Map.copyOf(amps); }

    public void applyReward(String id) {
        int cap = EffectCatalog.rewardCap(id);
        if (!amps.containsKey(id)) amps.put(id, 0);
        else amps.put(id, Math.min(cap, amps.get(id) + 1));
    }

    public void onKill(Random rng) {
        applyReward(EffectCatalog.REWARDS.get(rng.nextInt(EffectCatalog.REWARDS.size())));
        removeOnePenalty(rng);
    }

    public void onDeath(Random rng) {
        List<String> rewards = amps.keySet().stream().filter(EffectCatalog::isReward).toList();
        if (!rewards.isEmpty()) dropOneLevel(rewards.get(rng.nextInt(rewards.size())));
        else gainPenalty(rng);
    }

    private void gainPenalty(Random rng) {
        List<String> avail = EffectCatalog.PENALTIES.stream()
                .filter(id -> amp(id) < EffectCatalog.penaltyCap(id))
                .toList();
        if (avail.isEmpty()) return;
        String id = avail.get(rng.nextInt(avail.size()));
        if (!amps.containsKey(id)) amps.put(id, 0);
        else amps.put(id, amps.get(id) + 1);
    }

    private void removeOnePenalty(Random rng) {
        List<String> held = EffectCatalog.PENALTIES.stream().filter(amps::containsKey).toList();
        if (held.isEmpty()) return;
        dropOneLevel(held.get(rng.nextInt(held.size())));
    }

    private void dropOneLevel(String id) {
        int n = amps.getOrDefault(id, 0);
        if (n <= 0) amps.remove(id);
        else amps.put(id, n - 1);
    }

    // ── persistence glue (EffectStore) ──
    /** Live backing map — used by the JSON store to read/restore raw entries. */
    public Map<String, Integer> raw() { return amps; }

    public void putRaw(String id, int amp) { amps.put(id, amp); }

    // ── test helpers (package-private) ──
    int totalPenaltyCount() {
        return (int) amps.keySet().stream().filter(id -> !EffectCatalog.isReward(id)).count();
    }

    void addPenaltyForTest(String id, int amp) { amps.put(id, amp); }
}
