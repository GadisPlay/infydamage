package me.gadisplay.infydamage.debug;

/** Desglose de un golpe que pasó por la fórmula, tomado por {@code CombatDamageListener} antes de tocar el evento. */
public record CombatDebugSnapshot(
        String attackerName,
        String defenderName,
        double baseDamage,
        double armorTotal,
        double toughnessTotal,
        int protectionSum,
        double defensePoints,
        double mitigatedDamage,
        double absorptionBefore,
        int resistanceAmplifier,
        double finalDamage,
        double absorptionAfter,
        double healthBefore
) {
}
