package me.gadisplay.infydamage.formula;

import me.gadisplay.infydamage.config.DamageFormulaConfig;

/**
 * Matemática pura de la fórmula de daño (CONTEXT.md §6.1). No toca Bukkit/eventos
 * a propósito — la extracción de estos números desde {@code ItemStack}/{@code Player}
 * reales vive en el listener (Fase 4), para poder testear la fórmula en aislado
 * (Fase 3) sin necesitar un servidor levantado.
 */
public final class DamageFormula {

    private final DamageFormulaConfig config;

    public DamageFormula(DamageFormulaConfig config) {
        this.config = config;
    }

    /** Filo: curva vanilla (0.5 × nivel + 0.5), sin tope. */
    public double sharpnessBonus(int sharpnessLevel) {
        if (sharpnessLevel <= 0) {
            return 0.0;
        }
        return 0.5 * sharpnessLevel + 0.5;
    }

    /** DañoEquipo = atributo final de daño del arma + bonus de encantamiento (Filo o Power). */
    public double equipmentDamage(double finalAttackDamageAttribute, double enchantBonus) {
        return finalAttackDamageAttribute + enchantBonus;
    }

    /** PuntosDefensa = Armadura + (Toughness × peso) + (ΣProtección × peso). */
    public double defensePoints(double armorTotal, double toughnessTotal, int protectionLevelsSum) {
        return armorTotal
                + (toughnessTotal * config.toughnessWeight())
                + (protectionLevelsSum * config.protectionWeight());
    }

    /** DañoMitigado = DañoEquipo × (attenuation ^ PuntosDefensa) × global-modifier, con piso de seguridad. */
    public double mitigatedDamage(double equipmentDamage, double defensePoints) {
        double reduced = equipmentDamage
                * Math.pow(config.attenuationFactor(), defensePoints)
                * config.globalDamageModifier();
        return Math.max(reduced, config.minimumDamagePerHit());
    }
}
