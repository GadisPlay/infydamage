package me.gadisplay.infydamage.config;

import java.util.Set;

/**
 * Decide si un mob de MythicMobs específico pasa por la fórmula custom de
 * InfyDamage o se deja en manos de la mitigación vanilla (CONTEXT.md §9.3/§12).
 */
public enum MythicMobsScopeMode {
    /** La fórmula custom aplica igual, sea o no un mob de MythicMobs. */
    NONE,
    /** Solo los tipos listados en {@code mob-types} pasan por la fórmula; el resto, vanilla. */
    WHITELIST,
    /** Los tipos listados en {@code mob-types} se quedan en vanilla; el resto pasa por la fórmula. */
    BLACKLIST;

    public boolean allows(String mobType, Set<String> configuredTypes) {
        return switch (this) {
            case NONE -> true;
            case WHITELIST -> configuredTypes.contains(mobType);
            case BLACKLIST -> !configuredTypes.contains(mobType);
        };
    }
}
