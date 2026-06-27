package me.gadisplay.infydamage.config;

import me.gadisplay.infydamage.InfyDamage;

/**
 * Carga y recarga {@code config.yml} en caliente. {@code reload()} es lo único
 * que necesita el comando {@code /infydamage reload} (CONTEXT.md §5.4) — no requiere
 * reiniciar el servidor ni recompilar.
 */
public final class ConfigManager {

    private final InfyDamage plugin;
    private DamageFormulaConfig formulaConfig;

    public ConfigManager(InfyDamage plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.formulaConfig = DamageFormulaConfig.fromConfig(plugin.getConfig());
    }

    public void reload() {
        plugin.reloadConfig();
        this.formulaConfig = DamageFormulaConfig.fromConfig(plugin.getConfig());
    }

    public DamageFormulaConfig getFormulaConfig() {
        return formulaConfig;
    }
}
