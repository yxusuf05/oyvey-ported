package me.alpha432.network.core.rank;

import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.profile.ProfileService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads {@code ranks.yml} and turns a player's rank into a {@link PermissionAttachment}.
 * Deliberately small: one rank per player, inheritance by id, no per-player permissions.
 */
public final class RankService implements Listener {

    private final Plugin plugin;
    private final ProfileService profiles;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private String defaultRankId = "default";

    public RankService(Plugin plugin, ProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "ranks.yml");
        if (!file.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ranks.clear();
        defaultRankId = config.getString("default-rank", "default");
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection rank = section.getConfigurationSection(id);
                if (rank == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(rank.getString("icon", "PAPER"));
                ranks.put(id.toLowerCase(), new Rank(
                        id.toLowerCase(),
                        rank.getString("display-name", id),
                        rank.getString("prefix", ""),
                        rank.getString("suffix", ""),
                        rank.getString("name-color", "<gray>"),
                        rank.getInt("weight", 0),
                        rank.getDouble("price", 0.0D),
                        rank.getBoolean("purchasable", false),
                        rank.getBoolean("staff", false),
                        icon == null ? Material.PAPER : icon,
                        rank.getStringList("perks"),
                        rank.getStringList("permissions"),
                        rank.getStringList("inherits")));
            }
        }
        if (ranks.isEmpty()) {
            ranks.put(defaultRankId, Rank.fallback(defaultRankId));
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            apply(online);
        }
    }

    public Rank byId(String id) {
        if (id == null) {
            return defaultRank();
        }
        return ranks.getOrDefault(id.toLowerCase(), defaultRank());
    }

    public Rank defaultRank() {
        Rank rank = ranks.get(defaultRankId.toLowerCase());
        return rank != null ? rank : ranks.values().iterator().next();
    }

    public String defaultRankId() {
        return defaultRankId;
    }

    public Collection<Rank> all() {
        return ranks.values();
    }

    public boolean exists(String id) {
        return id != null && ranks.containsKey(id.toLowerCase());
    }

    public Rank of(Player player) {
        PlayerProfile profile = profiles.get(player);
        return profile == null ? defaultRank() : byId(profile.rankId());
    }

    public Rank of(PlayerProfile profile) {
        return profile == null ? defaultRank() : byId(profile.rankId());
    }

    /** Changes the rank of an online player and re-applies their permissions. */
    public boolean set(Player player, String rankId) {
        if (!exists(rankId)) {
            return false;
        }
        PlayerProfile profile = profiles.get(player);
        if (profile == null) {
            return false;
        }
        profile.rankId(rankId.toLowerCase());
        apply(player);
        profiles.save(profile);
        return true;
    }

    /** Ranks sold in the web shop, cheapest first. */
    public List<Rank> purchasable() {
        List<Rank> result = new ArrayList<>();
        for (Rank rank : ranks.values()) {
            if (rank.purchasable() && !rank.staff()) {
                result.add(rank);
            }
        }
        result.sort((a, b) -> Integer.compare(a.weight(), b.weight()));
        return result;
    }

    public List<Rank> staffRanks() {
        List<Rank> result = new ArrayList<>();
        for (Rank rank : ranks.values()) {
            if (rank.staff()) {
                result.add(rank);
            }
        }
        result.sort((a, b) -> Integer.compare(a.weight(), b.weight()));
        return result;
    }

    /** Rebuilds the player's permission attachment from their rank. */
    public void apply(Player player) {
        PermissionAttachment previous = attachments.remove(player.getUniqueId());
        if (previous != null) {
            player.removeAttachment(previous);
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String node : resolvePermissions(of(player))) {
            if (node.equals("*")) {
                for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
                    attachment.setPermission(permission, true);
                }
                // Tiered nodes such as network.smp.homes.15 are never registered in a
                // plugin.yml, so the wildcard has to pick them up from the rank definitions.
                for (String known : everyDeclaredPermission()) {
                    attachment.setPermission(known, true);
                }
            } else if (node.startsWith("-")) {
                attachment.setPermission(node.substring(1), false);
            } else {
                attachment.setPermission(node, true);
            }
        }
        attachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
    }

    /** Every permission node any rank mentions, used to expand the {@code *} wildcard. */
    private Set<String> everyDeclaredPermission() {
        Set<String> all = new LinkedHashSet<>();
        for (Rank rank : ranks.values()) {
            for (String node : rank.permissions()) {
                if (!node.equals("*") && !node.startsWith("-")) {
                    all.add(node);
                }
            }
        }
        return all;
    }

    /** Permissions of the rank plus everything it inherits, cycles guarded. */
    public Set<String> resolvePermissions(Rank rank) {
        Set<String> result = new LinkedHashSet<>();
        collect(rank, result, new HashSet<>());
        return result;
    }

    private void collect(Rank rank, Set<String> out, Set<String> visited) {
        if (rank == null || !visited.add(rank.id())) {
            return;
        }
        for (String parent : rank.inherits()) {
            collect(ranks.get(parent.toLowerCase()), out, visited);
        }
        out.addAll(rank.permissions());
    }

    /** Ranks ordered from highest to lowest weight. */
    public List<Rank> sortedByWeight() {
        List<Rank> sorted = new ArrayList<>(ranks.values());
        sorted.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return sorted;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = attachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            event.getPlayer().removeAttachment(attachment);
        }
    }
}
