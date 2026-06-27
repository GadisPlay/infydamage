package me.gadisplay.infydamage.listener;

import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.config.DamageFormulaConfig;
import me.gadisplay.infydamage.formula.DamageFormula;
import me.gadisplay.infydamage.formula.EquipmentReader;
import me.gadisplay.infydamage.hook.impl.MythicMobsHook;
import me.gadisplay.infydamage.hook.impl.NexoHook;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

/**
 * Reemplaza la mitigación de armadura/protección vanilla por la fórmula custom
 * (CONTEXT.md §5/§6).
 *
 * <p>A propósito NO se toca {@code DamageModifier.BASE}: vanilla ya lo calcula bien
 * — ahí adentro ya están el atributo de daño de Nexo y el bonus real de Filo (sin cap,
 * vanilla no lo limita) y los extras de §6.2 que tienen que quedar fuera de la fórmula
 * (Fuerza/Debilidad, críticos). Si reemplazáramos BASE con un cálculo propio, esos
 * extras se perderían en silencio. El bug real de CONTEXT.md §2 está únicamente en
 * ARMOR y MAGIC — los dos modifiers que vanilla capa a 20 puntos — así que son los
 * únicos dos que se reemplazan.</p>
 *
 * <p>Solo se usan setters de modifier explícitos ({@code setDamage(DamageModifier, double)}),
 * nunca el genérico de un argumento — ver la nota técnica de CONTEXT.md §5.3 sobre por
 * qué ese segundo setter puede revivir la mitigación vanilla que se quiere anular.</p>
 *
 * <p><b>El atacante siempre tiene que ser un jugador</b> (CONTEXT.md §6.1.3): un Zombie,
 * un Warden o un mob de MythicMobs pegando NO pasan por esta fórmula — su daño es 100%
 * vanilla/Mythic, sin que InfyDamage lo toque. Cuando el atacante SÍ es un jugador pero
 * el defensor es un mob, la fórmula sigue aplicando del lado de la defensa (CONTEXT.md §6.1).</p>
 */
public final class CombatDamageListener implements Listener {

    private final InfyDamage plugin;

    // Cacheados una sola vez: los hooks no cambian después de HookManager#loadAll()
    // en el arranque, así que no tiene sentido pagar un HashMap.get() por cada golpe.
    private final MythicMobsHook mythicMobsHook;
    private final NexoHook nexoHook;
    private final MythicTrueDamageListener mythicTrueDamageListener;

    public CombatDamageListener(InfyDamage plugin, MythicTrueDamageListener mythicTrueDamageListener) {
        this.plugin = plugin;
        this.mythicMobsHook = plugin.getHookManager().getHook(MythicMobsHook.class);
        this.nexoHook = plugin.getHookManager().getHook(NexoHook.class);
        this.mythicTrueDamageListener = mythicTrueDamageListener;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        DamageFormulaConfig config = plugin.getConfigManager().getFormulaConfig();
        if (!config.scopeApplyTo().contains(event.getCause())) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity defender)) {
            return;
        }

        if (!(event.getDamager() instanceof Player)) {
            // Zombie, Warden, mob de MythicMobs, etc. — su daño es 100% vanilla/Mythic.
            // InfyDamage solo entra en juego cuando quien pega es un jugador.
            return;
        }

        if (mythicTrueDamageListener != null && mythicTrueDamageListener.consumeTrueDamage(defender.getUniqueId())) {
            // MythicMobs marcó este golpe puntual como "ignoresArmor" (true damage) — aunque
            // el atacante sea un jugador, este golpe específico se deja 100% en sus manos.
            return;
        }

        double baseDamage = event.getDamage(DamageModifier.BASE);
        if (baseDamage <= config.weaponDamageThreshold()) {
            // Puño vacío, manzana, bloque: no es un golpe de un arma que se calibre a
            // propósito. Vanilla se encarga entero — no se toca ARMOR/MAGIC y
            // minimum-damage-per-hit no sube artificialmente este golpe.
            return;
        }

        if (mythicMobsHook != null && isExcludedMythicDefender(mythicMobsHook, config, defender)) {
            return;
        }

        DamageFormula formula = new DamageFormula(config);

        double armorTotal = EquipmentReader.resolveAttribute(defender, Attribute.ARMOR);
        double toughnessTotal = EquipmentReader.resolveAttribute(defender, Attribute.ARMOR_TOUGHNESS);
        int protectionSum = EquipmentReader.sumProtectionLevels(defender, nexoHook, config.protectionCapByItemId());

        double defensePoints = formula.defensePoints(armorTotal, toughnessTotal, protectionSum);
        double mitigatedDamage = formula.mitigatedDamage(baseDamage, defensePoints);

        // ARMOR a 0 y toda la reducción custom metida en MAGIC: BASE + MAGIC = mitigatedDamage.
        // RESISTANCE, ABSORPTION, BLOCKING, HARD_HAT siguen sin tocarse — vanilla los aplica
        // después, tal como pide CONTEXT.md §6.2.
        event.setDamage(DamageModifier.ARMOR, 0.0);
        event.setDamage(DamageModifier.MAGIC, mitigatedDamage - baseDamage);
    }

    /**
     * Whitelist/blacklist de mobs MythicMobs como DEFENSOR (CONTEXT.md §9.3/§12,
     * IMPLEMENTATION_PLAN.md Fase 7). El atacante en este punto ya está garantizado
     * como {@code Player} — no hace falta volver a chequear si el atacante es un mob.
     */
    private boolean isExcludedMythicDefender(MythicMobsHook hook, DamageFormulaConfig config, LivingEntity defender) {
        String defenderType = hook.isMythicMob(defender) ? hook.getMobType(defender) : null;
        return defenderType != null && !config.mythicMobsScopeMode().allows(defenderType, config.mythicMobsTypes());
    }
}
