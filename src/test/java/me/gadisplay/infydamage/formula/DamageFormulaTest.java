package me.gadisplay.infydamage.formula;

import me.gadisplay.infydamage.config.DamageFormulaConfig;
import me.gadisplay.infydamage.config.MythicMobsScopeMode;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Casos de regresión de la fórmula pura (CONTEXT.md §6.1/§7.3). Los pesos default
 * usados acá son los mismos de {@code config.yml} — si se recalibran en Fase 8,
 * estos números esperados hay que actualizarlos junto con el documento.
 */
class DamageFormulaTest {

    private static final double DELTA = 0.001;

    private static DamageFormulaConfig defaultConfig() {
        return new DamageFormulaConfig(
                0.45,   // global-damage-modifier
                0.965,  // attenuation-factor
                1.2,    // protection-weight
                1.0,    // toughness-weight
                0.5,    // minimum-damage-per-hit
                true,   // sharpness.use-vanilla-formula
                Set.of(DamageCause.ENTITY_ATTACK),
                1.0,                           // scope.weapon-damage-threshold
                Map.of(),                     // overrides.protection-cap-by-item-id
                MythicMobsScopeMode.NONE,      // mythicmobs-scope.mode
                Set.of()                       // mythicmobs-scope.mob-types
        );
    }

    @Test
    void sharpnessUsesVanillaCurveWithoutCap() {
        DamageFormula formula = new DamageFormula(defaultConfig());

        assertEquals(0.0, formula.sharpnessBonus(0), DELTA);
        assertEquals(3.0, formula.sharpnessBonus(5), DELTA);   // Filo V
        assertEquals(4.0, formula.sharpnessBonus(7), DELTA);   // Filo VII
        assertEquals(5.5, formula.sharpnessBonus(10), DELTA);  // Filo X — vanilla no tiene tope
        assertEquals(25.5, formula.sharpnessBonus(50), DELTA); // THEnchantments permite hasta 50
    }

    @Test
    void equipmentDamageAddsAttributeAndEnchantBonus() {
        DamageFormula formula = new DamageFormula(defaultConfig());

        // Espada del End: ~14 pts Nexo + Filo VII (4.0) ≈ 18 pts (CONTEXT.md §7.3)
        double bonus = formula.sharpnessBonus(7);
        assertEquals(18.0, formula.equipmentDamage(14.0, bonus), DELTA);
    }

    @Test
    void defensePointsSumArmorToughnessAndWeightedProtection() {
        DamageFormula formula = new DamageFormula(defaultConfig());

        // Set del End: 28 armadura, 20 toughness (5 x 4 piezas), Protección IX x4 = 36 niveles
        double points = formula.defensePoints(28.0, 20.0, 36);
        assertEquals(91.2, points, DELTA);
    }

    @Test
    void endgameVsEndgameHitsTheSafetyFloor() {
        // Regresión de CONTEXT.md §7.3 (recalculado tras sumar Toughness a la fórmula):
        // con los pesos default, este combo pisa minimum-damage-per-hit en vez de
        // aterrizar en el rango objetivo de §4. Confirma que el piso de seguridad
        // funciona — la recalibración de attenuation/toughness-weight es Fase 8, no esto.
        DamageFormula formula = new DamageFormula(defaultConfig());

        double equipmentDamage = 18.0;
        double defensePoints = 91.2;

        assertEquals(0.5, formula.mitigatedDamage(equipmentDamage, defensePoints), DELTA);
    }

    @Test
    void unarmoredTargetTakesFullEquipmentDamageScaledByGlobalModifier() {
        DamageFormula formula = new DamageFormula(defaultConfig());

        double equipmentDamage = 18.0;
        double defensePoints = formula.defensePoints(0.0, 0.0, 0);

        // attenuation^0 = 1 → solo se aplica el global-damage-modifier
        assertEquals(8.1, formula.mitigatedDamage(equipmentDamage, defensePoints), DELTA);
    }
}
