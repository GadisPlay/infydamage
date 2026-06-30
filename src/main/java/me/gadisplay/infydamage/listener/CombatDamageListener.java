package me.gadisplay.infydamage.listener;

import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.config.DamageFormulaConfig;
import me.gadisplay.infydamage.debug.CombatDebugRecorder;
import me.gadisplay.infydamage.debug.CombatDebugSnapshot;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
 * <p><b>Resistencia y Absorción se calculan a mano, no se dejan "sin tocar" (CONTEXT.md §13):</b>
 * dejar esos dos modifiers intactos (como hacía la versión anterior) no funciona en Paper
 * moderno — el descuento real de {@code getAbsorptionAmount()} no se sincroniza de forma
 * confiable cuando ARMOR/MAGIC del mismo evento fueron reemplazados por un plugin, y los
 * corazones amarillos de la manzana dorada/encantada quedaban sin descontarse nunca.
 * {@link VanillaExtras#applyResistanceAndAbsorption} hace ese cálculo a mano (mutando
 * {@code absorptionAmount} real) y el resultado se mete en {@code MAGIC}; {@code RESISTANCE}
 * y {@code ABSORPTION} del evento se fuerzan a {@code 0} para que vanilla no los vuelva a
 * aplicar encima. <b>El evento ya NO se cancela</b> — cancelarlo (versión anterior) sí
 * resolvía la absorción, pero también anulaba TODO el pipeline de golpe vanilla (knockback,
 * sonido, parpadeo de daño), no solo la mitigación que se quería anular.</p>
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

        // Snapshot para /infydamage debug (PROBLEMS.md): se toma ANTES de mutar absorción,
        // gateado por el flag para no pagar ni estos dos getters cuando nadie está debuggeando.
        boolean debugEnabled = plugin.getDebugState().isEnabled();
        double absorptionBefore = debugEnabled ? defender.getAbsorptionAmount() : 0.0;
        double healthBefore = debugEnabled ? defender.getHealth() : 0.0;

        // Resistencia y Absorción reales del defensor, calculadas a mano (CONTEXT.md §13) y
        // metidas en MAGIC junto con el resto de la reducción custom — el evento NO se cancela,
        // así que vanilla sigue aplicando knockback/sonido/parpadeo de daño después de esto.
        double finalDamage = VanillaExtras.applyResistanceAndAbsorption(defender, mitigatedDamage);

        if (debugEnabled) {
            PotionEffect resistance = defender.getPotionEffect(PotionEffectType.RESISTANCE);
            CombatDebugRecorder.record(defender.getUniqueId(), new CombatDebugSnapshot(
                    event.getDamager().getName(), defender.getName(), baseDamage, armorTotal, toughnessTotal,
                    protectionSum, defensePoints, mitigatedDamage, absorptionBefore,
                    resistance != null ? resistance.getAmplifier() : -1, finalDamage,
                    defender.getAbsorptionAmount(), healthBefore));
        }

        event.setDamage(DamageModifier.ARMOR, 0.0);
        // Se fuerzan a 0: ya se aplicaron a mano arriba, dejarlos "sin tocar" es lo que
        // causaba que getAbsorptionAmount() nunca se descontara (ver CONTEXT.md §13).
        event.setDamage(DamageModifier.RESISTANCE, 0.0);
        event.setDamage(DamageModifier.ABSORPTION, 0.0);
        event.setDamage(DamageModifier.MAGIC, finalDamage - baseDamage);
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
