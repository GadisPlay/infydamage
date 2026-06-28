package me.gadisplay.infydamage.formula;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Resistencia y Absorción reales del defensor, aplicadas a mano sobre el resultado
 * de {@link DamageFormula} (CONTEXT.md §6.2/§13). Reemplaza la confianza en
 * {@code DamageModifier.RESISTANCE}/{@code ABSORPTION} del evento de Bukkit: esa API
 * está deprecada por Paper ("responsible for a large number of implementation
 * problems") y no sincroniza de forma confiable el descuento real de
 * {@code LivingEntity#getAbsorptionAmount()} cuando otro modifier del mismo evento
 * (ARMOR/MAGIC) fue reemplazado por un plugin — los corazones amarillos quedaban
 * sin descontarse nunca, sin importar el daño recibido.
 */
public final class VanillaExtras {

    private VanillaExtras() {
    }

    /** Fórmula vanilla de Resistencia: 1 − 0.2 × (amplifier + 1), clamp a 0. */
    public static double resistanceFactor(int amplifier) {
        return Math.max(0.0, 1.0 - 0.2 * (amplifier + 1));
    }

    /** Descuenta de {@code absorptionAmount} lo que se pueda (clamp a 0) y devuelve el remanente. */
    public static double applyAbsorptionPure(double absorptionAmount, double damage) {
        double absorbed = Math.min(Math.max(absorptionAmount, 0.0), damage);
        return damage - absorbed;
    }

    /** Aplica Resistencia y Absorción reales de {@code defender}, mutando su absorptionAmount. */
    public static double applyResistanceAndAbsorption(LivingEntity defender, double damage) {
        PotionEffect resistance = defender.getPotionEffect(PotionEffectType.RESISTANCE);
        double afterResistance = resistance != null
                ? damage * resistanceFactor(resistance.getAmplifier())
                : damage;

        double absorption = defender.getAbsorptionAmount();
        double remaining = applyAbsorptionPure(absorption, afterResistance);
        if (absorption > 0.0) {
            defender.setAbsorptionAmount(Math.max(0.0, absorption - (afterResistance - remaining)));
        }
        return remaining;
    }
}
