package net.sebbyo.combat.combat;

import java.util.UUID;

/** Transient per-player combat state: ticks left in combat + who last damaged them. */
public final class CombatState {
    public int remainingTicks;
    public UUID lastAttacker;
}
