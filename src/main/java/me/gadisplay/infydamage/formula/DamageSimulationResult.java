package me.gadisplay.infydamage.formula;

/** Desglose paso a paso de una simulación de golpe (IMPLEMENTATION_PLAN.md Fase 6). */
public record DamageSimulationResult(
        double attackAttribute,
        int sharpnessLevel,
        double sharpnessBonus,
        double equipmentDamage,
        double armorTotal,
        double toughnessTotal,
        int protectionSum,
        double defensePoints,
        double mitigatedDamage,
        boolean appliesFormula
) {
}
