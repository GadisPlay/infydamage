package me.gadisplay.infydamage.listener;

import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.config.DamageFormulaConfig;
import me.gadisplay.infydamage.formula.DamageFormula;
import me.gadisplay.infydamage.formula.EquipmentReader;
import me.gadisplay.infydamage.formula.VanillaExtras;
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
 * ARMOR y MAGIC — los dos modifiers que vanilla capa a 20 puntos.</p>
 *
 * <p><b>El resultado final NO se aplica vía {@code DamageModifier} (CONTEXT.md §13):</b>
 * esa API está deprecada por Paper y no sincroniza de forma confiable el descuento real
 * de Resistencia/Absorción cuando otro modifier del mismo evento (ARMOR/MAGIC) fue
 * reemplazado — los corazones amarillos de la manzana dorada/encantada quedaban sin
 * descontarse nunca. En vez de eso, el evento se cancela y el daño final se aplica a
 * mano con {@link VanillaExtras#applyResistanceAndAbsorption} + {@code setHealth},
 * replicando Resistencia y Absorción reales del defensor.</p>
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

        // Resistencia y Absorción reales del defensor, replicadas a mano (CONTEXT.md §13) —
        // el evento se cancela en vez de seguir usando DamageModifier para el resultado final.
        double finalDamage = VanillaExtras.applyResistanceAndAbsorption(defender, mitigatedDamage);

        event.setCancelled(true);

        if (finalDamage > 0.0) {
            // Invulnerability ticks estándar de jugador — se pierde al cancelar el evento.
            defender.setNoDamageTicks(20);
            defender.setHealth(Math.max(0.0, defender.getHealth() - finalDamage));
            // NOTA (CONTEXT.md §13): cancelar el evento aborta el pipeline de daño de NMS
            // completo, no solo los modifiers de Bukkit — esto puede perder la atribución
            // de muerte ("X fue asesinado por Y") y el knockback/sonido de golpe. No hay
            // API pública de reemplazo (Entity#setLastDamageCause quedó deprecada "for
            // removal, internal use only" sin alternativa). Pendiente de confirmar en
            // testing manual in-game antes de cerrar el bug — ver IMPLEMENTATION_PLAN.md.
        }
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
