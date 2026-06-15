package net.sebbyo.combat.effect;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EffectLedgerTest {

    private final EffectLedger ledger = new EffectLedger();

    @Test
    void speedStacksUnbounded() {
        for (int i = 0; i < 5; i++) ledger.applyReward("minecraft:speed");
        assertEquals(4, ledger.amp("minecraft:speed")); // 5 kills -> Speed V (amp 4)
    }

    @Test
    void healthBoostCapsAtLevel4() {
        for (int i = 0; i < 9; i++) ledger.applyReward("minecraft:health_boost");
        assertEquals(3, ledger.amp("minecraft:health_boost"));
    }

    @Test
    void absorptionCapsAtLevel2() {
        for (int i = 0; i < 9; i++) ledger.applyReward("minecraft:absorption");
        assertEquals(1, ledger.amp("minecraft:absorption"));
    }

    @Test
    void regenerationCapsAtLevel1() {
        for (int i = 0; i < 9; i++) ledger.applyReward("minecraft:regeneration");
        assertEquals(0, ledger.amp("minecraft:regeneration"));
    }

    @Test
    void otherRewardsCapAtLevel2() {
        for (int i = 0; i < 5; i++) ledger.applyReward("minecraft:haste");
        assertEquals(1, ledger.amp("minecraft:haste"));
    }

    @Test
    void deathWithRewardsDropsOneLevel() {
        ledger.applyReward("minecraft:speed");
        ledger.applyReward("minecraft:speed");
        ledger.applyReward("minecraft:speed"); // amp 2 (Speed III)
        ledger.onDeath(new Random(1));
        assertEquals(1, ledger.amp("minecraft:speed")); // -> Speed II
    }

    @Test
    void deathRemovesLevelOneRewardEntirely() {
        ledger.applyReward("minecraft:haste"); // amp 0
        ledger.onDeath(new Random(1));
        assertFalse(ledger.has("minecraft:haste"));
    }

    @Test
    void deathWithNoRewardsGrantsAPenalty() {
        ledger.onDeath(new Random(1));
        assertEquals(1, ledger.totalPenaltyCount());
    }

    @Test
    void penaltiesAccumulateWhileBuffless() {
        ledger.onDeath(new Random(1));
        ledger.onDeath(new Random(2));
        int totalPenaltyLevels = ledger.view().entrySet().stream()
                .filter(e -> !EffectCatalog.isReward(e.getKey()))
                .mapToInt(e -> e.getValue() + 1)
                .sum();
        assertEquals(2, totalPenaltyLevels);
    }

    @Test
    void killRemovesOnePenaltyLevel() {
        ledger.addPenaltyForTest("minecraft:slowness", 1); // Slowness II
        ledger.onKill(new Random(1)); // grants a reward AND peels one penalty level
        assertEquals(0, ledger.amp("minecraft:slowness")); // -> Slowness I
    }
}
