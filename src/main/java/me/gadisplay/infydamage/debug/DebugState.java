package me.gadisplay.infydamage.debug;

/**
 * Toggle de runtime para el log de consola de {@code CombatDebugListener} (PROBLEMS.md
 * — testing manual in-game de Resistencia/Absorción). Apagado por default: cero costo
 * en el hot path de combate cuando nadie está debuggeando.
 */
public final class DebugState {

    private volatile boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
