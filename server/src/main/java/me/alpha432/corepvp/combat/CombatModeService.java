package me.alpha432.corepvp.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Switches a player between 1.8-style and modern combat.
 *
 * <p>The attack cooldown is removed by adding a keyed modifier to the attack
 * speed attribute rather than overwriting its base value, so switching back is
 * a clean removal instead of a guess at what the original number was.
 *
 * <p>Raising attack speed alone is not enough: the sweep attack survives it, so
 * {@link CombatListener} cancels sweep damage for players in a legacy kit.
 */
public final class CombatModeService {

    /** High enough that the cooldown bar is always full. */
    private static final double NO_COOLDOWN = 1024.0D;

    private final NamespacedKey modifierKey;
    private final Map<UUID, CombatMode> modes = new ConcurrentHashMap<>();

    public CombatModeService(Plugin plugin) {
        this.modifierKey = new NamespacedKey(plugin, "legacy_attack_speed");
    }

    public CombatMode modeOf(Player player) {
        return modes.getOrDefault(player.getUniqueId(), CombatMode.MODERN);
    }

    public void apply(Player player, CombatMode mode) {
        modes.put(player.getUniqueId(), mode);

        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        attackSpeed.removeModifier(modifierKey);
        if (mode.legacy()) {
            attackSpeed.addModifier(new AttributeModifier(modifierKey, NO_COOLDOWN,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    /** Back to vanilla behaviour, e.g. when returning to the hub. */
    public void reset(Player player) {
        modes.remove(player.getUniqueId());
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(modifierKey);
        }
    }

    public void forget(UUID uuid) {
        modes.remove(uuid);
    }
}
