package me.gadisplay.infydamage.formula;

import me.gadisplay.infydamage.hook.impl.NexoHook;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Lectura de atributos/encantamientos reales de un {@link LivingEntity}. Comparte
 * el listener de combate (Fase 4) y el comando {@code /infydamage simulate}
 * (Fase 6) para no duplicar la misma lógica de lectura en los dos lugares.
 */
public final class EquipmentReader {

    private EquipmentReader() {
    }

    public static double resolveAttribute(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null ? instance.getValue() : 0.0;
    }

    /**
     * Suma los niveles de Protección de la armadura equipada. Si {@code nexoHook} no es
     * {@code null} y una pieza tiene un ID Nexo con cap configurado en
     * {@code protectionCapByItemId} (CONTEXT.md §3.1, casos híbridos), el nivel real de
     * esa pieza se recorta al cap antes de sumar.
     */
    public static int sumProtectionLevels(LivingEntity entity, NexoHook nexoHook, Map<String, Integer> protectionCapByItemId) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return 0;
        }

        // Sin overrides configurados, no tiene sentido pagar una llamada a la API de
        // Nexo (NexoItems.idFromItem) por cada pieza de armadura en cada golpe — el
        // resultado nunca puede importar si el mapa de caps está vacío.
        boolean checkOverrides = nexoHook != null && !protectionCapByItemId.isEmpty();

        int sum = 0;
        for (ItemStack piece : equipment.getArmorContents()) {
            if (piece == null) {
                continue;
            }

            int level = piece.getEnchantmentLevel(Enchantment.PROTECTION);
            if (checkOverrides) {
                String itemId = nexoHook.resolveItemId(piece);
                Integer cap = itemId != null ? protectionCapByItemId.get(itemId) : null;
                if (cap != null) {
                    level = Math.min(level, cap);
                }
            }
            sum += level;
        }
        return sum;
    }

    public static int mainHandEnchantLevel(LivingEntity entity, Enchantment enchantment) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return 0;
        }
        return equipment.getItemInMainHand().getEnchantmentLevel(enchantment);
    }
}
