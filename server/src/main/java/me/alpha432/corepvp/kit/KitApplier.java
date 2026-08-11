package me.alpha432.corepvp.kit;

import me.alpha432.corepvp.combat.CombatModeService;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Hands a kit to a player, including the combat rules it is played under. */
public final class KitApplier {

    private final CombatModeService combat;

    public KitApplier(CombatModeService combat) {
        this.combat = combat;
    }

    public void apply(Player player, Kit kit) {
        apply(player, kit, null);
    }

    /**
     * @param hotbarOrder the player's saved layout for this kit, or null for
     *                    the kit's own order
     */
    public void apply(Player player, Kit kit, ItemStack[] hotbarOrder) {
        player.getInventory().clear();

        ItemStack[] contents = kit.contents();
        if (hotbarOrder != null) {
            System.arraycopy(hotbarOrder, 0, contents, 0, Math.min(9, hotbarOrder.length));
        }
        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(kit.armor());
        player.getInventory().setItemInOffHand(kit.offHand());
        player.getInventory().setHeldItemSlot(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : kit.effects()) {
            player.addPotionEffect(effect);
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0D : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(0);
        player.setNoDamageTicks(0);

        combat.apply(player, kit.combatMode());
        player.updateInventory();
    }
}
