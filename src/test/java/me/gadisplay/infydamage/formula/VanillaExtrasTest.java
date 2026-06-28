package me.gadisplay.infydamage.formula;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Casos de regresión de la matemática pura de Resistencia/Absorción (CONTEXT.md §13,
 * bug de manzana dorada/encantada ignorada por la fórmula). La integración real con
 * {@code LivingEntity} (getPotionEffect/getAbsorptionAmount/setAbsorptionAmount) queda
 * para testing manual in-game, igual que el resto del listener de combate.
 */
class VanillaExtrasTest {

    private static final double DELTA = 0.001;

    @Test
    void resistanceFactorAppliesVanillaTwentyPercentPerLevel() {
        assertEquals(0.8, VanillaExtras.resistanceFactor(0), DELTA); // Resistencia I
        assertEquals(0.6, VanillaExtras.resistanceFactor(1), DELTA); // Resistencia II
        assertEquals(0.4, VanillaExtras.resistanceFactor(2), DELTA); // Resistencia III
        assertEquals(0.2, VanillaExtras.resistanceFactor(3), DELTA); // Resistencia IV
    }

    @Test
    void resistanceFactorClampsToZeroPastLevelFive() {
        assertEquals(0.0, VanillaExtras.resistanceFactor(4), DELTA); // Resistencia V
        assertEquals(0.0, VanillaExtras.resistanceFactor(10), DELTA);
    }

    @Test
    void absorptionFullyAbsorbsDamageWhenPoolIsLarger() {
        assertEquals(0.0, VanillaExtras.applyAbsorptionPure(8.0, 2.0), DELTA);
    }

    @Test
    void absorptionPartiallyAbsorbsDamageWhenPoolIsSmaller() {
        assertEquals(3.0, VanillaExtras.applyAbsorptionPure(2.0, 5.0), DELTA);
    }

    @Test
    void absorptionExactlyMatchingDamageLeavesNothingForHealth() {
        assertEquals(0.0, VanillaExtras.applyAbsorptionPure(4.0, 4.0), DELTA);
    }

    @Test
    void noAbsorptionPoolLeavesDamageUntouched() {
        assertEquals(5.0, VanillaExtras.applyAbsorptionPure(0.0, 5.0), DELTA);
    }
}
