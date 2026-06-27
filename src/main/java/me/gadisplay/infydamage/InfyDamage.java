package me.gadisplay.infydamage;

import me.gadisplay.infydamage.command.InfyDamageCommand;
import me.gadisplay.infydamage.config.ConfigManager;
import me.gadisplay.infydamage.hook.HookManager;
import me.gadisplay.infydamage.hook.impl.MythicMobsHook;
import me.gadisplay.infydamage.listener.CombatDamageListener;
import me.gadisplay.infydamage.listener.MythicTrueDamageListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfyDamage extends JavaPlugin {

    private static InfyDamage instance;

    private ConfigManager configManager;
    private HookManager hookManager;

    @Override
    public void onEnable() {
        instance = this;

        // Orden de carga (CONTEXT.md §11.2):
        // 1. ConfigManager    (Fase 1)
        // 2. HookManager      (Fase 2)
        // 3. Listener de daño (Fase 4)
        // 4. Comandos         (Fase 5)
        this.configManager = new ConfigManager(this);
        this.hookManager = new HookManager(this);
        this.hookManager.loadAll();

        // MythicTrueDamageListener solo se registra si MythicMobs está presente: requiere
        // la clase MythicDamageEvent del plugin de Mythic, así que registrarlo sin esa
        // dependencia cargada tiraría un NoClassDefFoundError apenas Bukkit intente
        // resolver la firma del método @EventHandler.
        MythicTrueDamageListener mythicTrueDamageListener = null;
        if (hookManager.getHook(MythicMobsHook.class) != null) {
            mythicTrueDamageListener = new MythicTrueDamageListener(this);
            getServer().getPluginManager().registerEvents(mythicTrueDamageListener, this);
        }
        getServer().getPluginManager().registerEvents(new CombatDamageListener(this, mythicTrueDamageListener), this);
        new InfyDamageCommand(this).register(this, "infydamage");

        getLogger().info("InfyDamage habilitado.");
    }

    @Override
    public void onDisable() {
        getLogger().info("InfyDamage deshabilitado.");
    }

    public static InfyDamage getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }
}
