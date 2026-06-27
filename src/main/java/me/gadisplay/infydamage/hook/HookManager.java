package me.gadisplay.infydamage.hook;

import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.hook.impl.MythicMobsHook;
import me.gadisplay.infydamage.hook.impl.NexoHook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra y expone los hooks de soft-dependency. {@code MythicCrucibleHook} todavía
 * no existe — bloqueado por el repo Maven roto de Lumine (IMPLEMENTATION_PLAN.md Fase 0/2).
 * {@code THEnchantmentsHook} no hace falta: confirmado que no aporta nada que la API
 * estándar de Bukkit no dé ya (CONTEXT.md, historial de decisiones).
 */
public final class HookManager {

    private final List<BaseHook> hooks = new ArrayList<>();
    private final Map<Class<? extends BaseHook>, BaseHook> index = new HashMap<>();

    public HookManager(InfyDamage plugin) {
        register(new NexoHook(plugin));
        register(new MythicMobsHook(plugin));
    }

    private void register(BaseHook hook) {
        hooks.add(hook);
        index.put(hook.getClass(), hook);
    }

    public void loadAll() {
        hooks.forEach(BaseHook::tryLoad);
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseHook> T getHook(Class<T> type) {
        BaseHook hook = index.get(type);
        return (hook != null && hook.isEnabled()) ? (T) hook : null;
    }
}
