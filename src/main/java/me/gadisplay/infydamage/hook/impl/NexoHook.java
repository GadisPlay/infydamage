package me.gadisplay.infydamage.hook.impl;

import com.nexomc.nexo.api.NexoItems;
import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.hook.BaseHook;
import org.bukkit.inventory.ItemStack;

/**
 * Solo lectura del ID de ítem Nexo de un {@link ItemStack} — hace falta únicamente
 * para los overrides por {@code nexo:item_id} de la Fase 7 (CONTEXT.md §3.1/§12).
 * El daño/armadura/protección del día a día NO depende de este hook: se leen
 * directo de los atributos del ItemStack vía API estándar de Bukkit (CONTEXT.md §9.1).
 */
public final class NexoHook extends BaseHook {

    public NexoHook(InfyDamage plugin) {
        super(plugin);
    }

    @Override
    public String getPluginName() {
        return "Nexo";
    }

    @Override
    protected void load() {
        // NexoItems es un objeto Kotlin con métodos estáticos — no hay nada que
        // cachear. Esta llamada de prueba solo confirma que la clase resuelve en runtime.
        NexoItems.itemNames();
    }

    /** @return el ID Nexo del ítem (ej. {@code "void_helmet"}), o {@code null} si no es un ítem Nexo. */
    public String resolveItemId(ItemStack item) {
        return NexoItems.idFromItem(item);
    }
}
