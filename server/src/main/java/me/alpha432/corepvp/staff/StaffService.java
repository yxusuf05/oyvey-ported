package me.alpha432.corepvp.staff;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Staff mode: vanish, a small toolbar, and freezing players. */
public final class StaffService {

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final Set<UUID> inStaffMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public StaffService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    public boolean inStaffMode(Player player) {
        return inStaffMode.contains(player.getUniqueId());
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    public void toggleStaffMode(Player player) {
        if (inStaffMode.remove(player.getUniqueId())) {
            setVanished(player, false);
            plugin.lobby().sendToLobby(player);
            messages.send(player, "staff.mode-off");
            return;
        }
        inStaffMode.add(player.getUniqueId());
        plugin.states().set(player, PlayerState.STAFF);
        setVanished(player, true);
        giveTools(player);
        messages.send(player, "staff.mode-on");
    }

    private void giveTools(Player player) {
        player.getInventory().setItem(0, ItemBuilder.of(Material.COMPASS)
                .name(messages.render("staff.tool-teleport")).build());
        player.getInventory().setItem(1, ItemBuilder.of(Material.PACKED_ICE)
                .name(messages.render("staff.tool-freeze")).build());
        player.getInventory().setItem(8, ItemBuilder.of(Material.BARRIER)
                .name(messages.render("staff.tool-exit")).build());
        player.updateInventory();
    }

    public void setVanished(Player player, boolean vanish) {
        if (vanish) {
            vanished.add(player.getUniqueId());
        } else {
            vanished.remove(player.getUniqueId());
        }

        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            // Staff can still see each other while vanished.
            if (vanish && !other.hasPermission("corepvp.staff")) {
                other.hidePlayer(plugin, player);
            } else {
                other.showPlayer(plugin, player);
            }
        }
        // Invisibility keeps particles and held items from giving vanish away.
        if (vanish) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    public void toggleFreeze(Player staff, Player target) {
        if (frozen.remove(target.getUniqueId())) {
            messages.send(target, "staff.unfrozen");
            messages.send(staff, "staff.unfroze", Messages.of("player", target.getName()));
            return;
        }
        frozen.add(target.getUniqueId());
        messages.send(target, "staff.frozen");
        messages.send(staff, "staff.froze", Messages.of("player", target.getName()));
    }

    /** New arrivals must not see anyone who is currently vanished. */
    public void hideVanishedFrom(Player viewer) {
        if (viewer.hasPermission("corepvp.staff")) {
            return;
        }
        for (UUID uuid : vanished) {
            Player hidden = plugin.getServer().getPlayer(uuid);
            if (hidden != null && hidden.isOnline()) {
                viewer.hidePlayer(plugin, hidden);
            }
        }
    }

    public void forget(UUID uuid) {
        inStaffMode.remove(uuid);
        vanished.remove(uuid);
        frozen.remove(uuid);
    }
}
