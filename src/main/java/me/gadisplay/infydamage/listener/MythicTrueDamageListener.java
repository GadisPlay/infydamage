package me.gadisplay.infydamage.listener;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicDamageEvent;
import me.gadisplay.infydamage.InfyDamage;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marca a un defensor como "true damage" cuando una skill de MythicMobs (mob o
 * jugador casteando una skill) usa {@code ignorearmor} (CONTEXT.md §6.1.3/§9.3).
 *
 * <p>{@link MythicDamageEvent} es un evento custom de MythicMobs, distinto de
 * {@link org.bukkit.event.entity.EntityDamageByEntityEvent}, que se dispara antes en
 * el mismo tick. Acá solo se anota la marca; quien la consume y decide saltarse la
 * fórmula es {@code CombatDamageListener} a través de {@link #consumeTrueDamage(UUID)}.</p>
 *
 * <p>Se usa un {@code Set<UUID>} (no un solo valor) porque varios defensores pueden
 * recibir daño Mythic dentro del mismo tick (ej. una skill de área). El cleanup
 * programado un tick después es una red de seguridad: si por lo que sea el
 * {@code EntityDamageByEntityEvent} correlacionado nunca llega a dispararse, la marca
 * no se queda pegada para siempre en el set.</p>
 */
public final class MythicTrueDamageListener implements Listener {

    private final InfyDamage plugin;
    private final Set<UUID> pendingTrueDamage = ConcurrentHashMap.newKeySet();

    public MythicTrueDamageListener(InfyDamage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicDamage(MythicDamageEvent event) {
        if (!Boolean.TRUE.equals(event.getDamageMetadata().getIgnoresArmor())) {
            return;
        }

        Entity target = BukkitAdapter.adapt(event.getTarget());
        UUID targetId = target.getUniqueId();
        pendingTrueDamage.add(targetId);

        plugin.getServer().getScheduler().runTask(plugin, () -> pendingTrueDamage.remove(targetId));
    }

    /** Chequea y consume la marca: {@code true} si este defensor tiene un golpe pendiente de true damage. */
    public boolean consumeTrueDamage(UUID defenderId) {
        return pendingTrueDamage.remove(defenderId);
    }
}
