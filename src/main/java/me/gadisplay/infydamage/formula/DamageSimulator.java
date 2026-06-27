package me.gadisplay.infydamage.formula;

import me.gadisplay.infydamage.config.DamageFormulaConfig;
import me.gadisplay.infydamage.hook.impl.NexoHook;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;

/**
 * Recalcula la fórmula a mano a partir de atributos/encantamientos reales —
 * a diferencia del listener de combate (Fase 4), {@code /infydamage simulate}
 * no tiene un {@code EntityDamageByEntityEvent} real del cual leer
 * {@code DamageModifier.BASE}, así que tiene que reconstruir ese número él mismo.
 */
public final class DamageSimulator {

    private DamageSimulator() {
    }

    public static DamageSimulationResult simulate(DamageFormulaConfig config, NexoHook nexoHook, LivingEntity attacker, LivingEntity defender) {
        DamageFormula formula = new DamageFormula(config);

        double attackAttribute = EquipmentReader.resolveAttribute(attacker, Attribute.ATTACK_DAMAGE);
        int sharpnessLevel = EquipmentReader.mainHandEnchantLevel(attacker, Enchantment.SHARPNESS);
        double sharpnessBonus = formula.sharpnessBonus(sharpnessLevel);
        double equipmentDamage = formula.equipmentDamage(attackAttribute, sharpnessBonus);

        double armorTotal = EquipmentReader.resolveAttribute(defender, Attribute.ARMOR);
        double toughnessTotal = EquipmentReader.resolveAttribute(defender, Attribute.ARMOR_TOUGHNESS);
        int protectionSum = EquipmentReader.sumProtectionLevels(defender, nexoHook, config.protectionCapByItemId());

        double defensePoints = formula.defensePoints(armorTotal, toughnessTotal, protectionSum);
        double mitigatedDamage = formula.mitigatedDamage(equipmentDamage, defensePoints);
        boolean appliesFormula = equipmentDamage > config.weaponDamageThreshold();

        return new DamageSimulationResult(
                attackAttribute, sharpnessLevel, sharpnessBonus, equipmentDamage,
                armorTotal, toughnessTotal, protectionSum, defensePoints, mitigatedDamage, appliesFormula
        );
    }
}
