package me.gadisplay.infydamage.hook.impl;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.hook.BaseHook;
import org.bukkit.entity.Entity;

/**
 * Identifica si una entidad es un mob de MythicMobs, para el scope de daño
 * (CONTEXT.md §9.3): decidir qué fuentes de daño Mythic pasan por la fórmula
 * custom de InfyDamage y cuáles quedan excluidas.
 */
public final class MythicMobsHook extends BaseHook {

    private BukkitAPIHelper apiHelper;

    public MythicMobsHook(InfyDamage plugin) {
        super(plugin);
    }

    @Override
    public String getPluginName() {
        return "MythicMobs";
    }

    @Override
    protected void load() {
        this.apiHelper = MythicBukkit.inst().getAPIHelper();
    }

    public boolean isMythicMob(Entity entity) {
        return apiHelper.isMythicMob(entity);
    }

    /** @return el nombre interno del tipo de mob (ej. {@code "AncientGolem"}), o {@code null} si no es un mob Mythic. */
    public String getMobType(Entity entity) {
        var activeMob = apiHelper.getMythicMobInstance(entity);
        return activeMob != null ? activeMob.getMobType() : null;
    }
}
