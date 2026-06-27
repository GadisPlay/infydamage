package me.gadisplay.infydamage.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot inmutable de los valores de la fórmula de daño leídos de {@code config.yml}.
 * Reconstruido por completo en cada {@code /infydamage reload} (CONTEXT.md §5.4) —
 * nunca se mutan campos individuales, se reemplaza la instancia entera.
 */
public record DamageFormulaConfig(
        double globalDamageModifier,
        double attenuationFactor,
        double protectionWeight,
        double toughnessWeight,
        double minimumDamagePerHit,
        boolean sharpnessUseVanillaFormula,
        Set<DamageCause> scopeApplyTo,
        double weaponDamageThreshold,
        Map<String, Integer> protectionCapByItemId,
        MythicMobsScopeMode mythicMobsScopeMode,
        Set<String> mythicMobsTypes
) {

    public static DamageFormulaConfig fromConfig(FileConfiguration config) {
        Set<DamageCause> applyTo = EnumSet.noneOf(DamageCause.class);
        for (String name : config.getStringList("scope.apply-to")) {
            applyTo.add(DamageCause.valueOf(name));
        }

        Map<String, Integer> protectionCapByItemId = new HashMap<>();
        ConfigurationSection capsSection = config.getConfigurationSection("overrides.protection-cap-by-item-id");
        if (capsSection != null) {
            for (String itemId : capsSection.getKeys(false)) {
                protectionCapByItemId.put(itemId, capsSection.getInt(itemId));
            }
        }

        MythicMobsScopeMode mythicMobsScopeMode = MythicMobsScopeMode.valueOf(
                config.getString("mythicmobs-scope.mode", "NONE").toUpperCase());
        Set<String> mythicMobsTypes = new HashSet<>(config.getStringList("mythicmobs-scope.mob-types"));

        return new DamageFormulaConfig(
                config.getDouble("combat-pacing.global-damage-modifier"),
                config.getDouble("formulas.attenuation-factor"),
                config.getDouble("formulas.protection-weight"),
                config.getDouble("formulas.toughness-weight"),
                config.getDouble("formulas.minimum-damage-per-hit"),
                config.getBoolean("sharpness.use-vanilla-formula"),
                applyTo,
                config.getDouble("scope.weapon-damage-threshold"),
                protectionCapByItemId,
                mythicMobsScopeMode,
                mythicMobsTypes
        );
    }
}
