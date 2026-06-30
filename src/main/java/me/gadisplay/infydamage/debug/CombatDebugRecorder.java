package me.gadisplay.infydamage.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Puente entre {@code CombatDamageListener} (prioridad HIGH, escribe el snapshot antes
 * de tocar el evento) y {@code CombatDebugListener} (prioridad MONITOR, lo consume una
 * vez que el evento ya resolvió del todo, vida y absorción reales incluidas).
 *
 * <p>Un {@code HashMap} simple alcanza: Bukkit procesa cada
 * {@code EntityDamageByEntityEvent} de punta a punta en el hilo principal antes de
 * pasar al siguiente, así que no hay carrera entre el write (HIGH) y el read (MONITOR)
 * del mismo golpe.</p>
 */
public final class CombatDebugRecorder {

    private static final Map<UUID, CombatDebugSnapshot> PENDING = new HashMap<>();

    private CombatDebugRecorder() {
    }

    public static void record(UUID defenderId, CombatDebugSnapshot snapshot) {
        PENDING.put(defenderId, snapshot);
    }

    public static CombatDebugSnapshot consume(UUID defenderId) {
        return PENDING.remove(defenderId);
    }
}
