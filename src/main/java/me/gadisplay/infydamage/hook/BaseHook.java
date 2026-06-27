package me.gadisplay.infydamage.hook;

import me.gadisplay.infydamage.InfyDamage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Soft-dependency: el hook solo queda habilitado si el plugin externo está
 * instalado y encendido. Si no está, o si {@link #load()} falla, el hook
 * queda deshabilitado sin romper el arranque de InfyDamage (CONTEXT.md §11.2).
 */
public abstract class BaseHook {

    protected final InfyDamage plugin;
    private boolean enabled = false;

    protected BaseHook(InfyDamage plugin) {
        this.plugin = plugin;
    }

    public abstract String getPluginName();

    protected abstract void load() throws Exception;

    public final boolean tryLoad() {
        if (!isPluginPresent()) {
            enabled = false;
            return false;
        }
        try {
            load();
            enabled = true;
            plugin.getLogger().info("[Hook] " + getPluginName() + " conectado correctamente.");
        } catch (Exception e) {
            enabled = false;
            plugin.getLogger().warning("[Hook] " + getPluginName() + " falló al conectar: " + e.getMessage());
        }
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean isPluginPresent() {
        Plugin target = Bukkit.getPluginManager().getPlugin(getPluginName());
        return target != null && target.isEnabled();
    }
}
