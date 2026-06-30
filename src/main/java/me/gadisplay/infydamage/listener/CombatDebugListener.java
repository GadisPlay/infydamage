package me.gadisplay.infydamage.listener;

import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.debug.CombatDebugRecorder;
import me.gadisplay.infydamage.debug.CombatDebugSnapshot;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Solo activo con {@code /infydamage debug} encendido (PROBLEMS.md — testing manual
 * in-game de Resistencia/Absorción). Corre en {@code MONITOR}, después de
 * {@code CombatDamageListener} (HIGH) y de que vanilla terminó de aplicar el daño real,
 * para comparar el estado post-golpe (vida, {@code getFinalDamage()}) contra el
 * snapshot que {@code CombatDamageListener} tomó antes de tocar el evento.
 */
public final class CombatDebugListener implements Listener {

    private final InfyDamage plugin;

    public CombatDebugListener(InfyDamage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getDebugState().isEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity defender)) {
            return;
        }

        CombatDebugSnapshot snapshot = CombatDebugRecorder.consume(defender.getUniqueId());
        if (snapshot == null) {
            return;
        }

        plugin.getLogger().info(String.format(
                "[debug-combat] %s -> %s | BASE=%.2f defensa(armor=%.2f, toughness=%.2f, prot=%d)=%.2f"
                        + " | mitigado(formula)=%.2f | resistencia(amplifier)=%d | absorcion %.2f -> %.2f"
                        + " | finalDamage(formula+resist+absorb)=%.2f | event.getFinalDamage()=%.2f cancelled=%s"
                        + " | vida %.2f -> %.2f",
                snapshot.attackerName(), snapshot.defenderName(),
                snapshot.baseDamage(), snapshot.armorTotal(), snapshot.toughnessTotal(), snapshot.protectionSum(),
                snapshot.defensePoints(), snapshot.mitigatedDamage(), snapshot.resistanceAmplifier(),
                snapshot.absorptionBefore(), snapshot.absorptionAfter(), snapshot.finalDamage(),
                event.getFinalDamage(), event.isCancelled(),
                snapshot.healthBefore(), defender.getHealth()));
    }
}
