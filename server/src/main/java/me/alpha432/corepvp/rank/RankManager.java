package me.alpha432.corepvp.rank;

import me.alpha432.corepvp.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranks loaded from ranks.yml.
 *
 * <p>A player's rank is the highest-priority one whose permission they hold, so
 * this works with LuckPerms, any other permission plugin, or plain
 * {@code permissions.yml} without needing an integration.
 */
public final class RankManager {

    private static final Rank FALLBACK =
            new Rank("default", "Player", "<gray>", "<gray>", 0, null);

    private final ConfigManager configs;
    private final Map<String, Rank> byId = new LinkedHashMap<>();
    private List<Rank> byPriority = List.of();
    private Rank defaultRank = FALLBACK;

    public RankManager(ConfigManager configs) {
        this.configs = configs;
        reload();
    }

    public void reload() {
        byId.clear();

        ConfigurationSection root = configs.get("ranks.yml").getConfigurationSection("ranks");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                String permission = section.getString("permission", "");
                byId.put(key, new Rank(
                        key,
                        section.getString("display", id),
                        section.getString("prefix", ""),
                        section.getString("name-color", "<gray>"),
                        section.getInt("priority", 0),
                        permission.isBlank() ? null : permission));
            }
        }

        if (byId.isEmpty()) {
            byId.put(FALLBACK.id(), FALLBACK);
        }

        List<Rank> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparingInt(Rank::priority).reversed());
        byPriority = List.copyOf(sorted);

        String defaultId = configs.get("ranks.yml").getString("default-rank", "default");
        defaultRank = byId.getOrDefault(defaultId.toLowerCase(Locale.ROOT), sorted.get(sorted.size() - 1));
    }

    public Rank byId(String id) {
        return id == null ? defaultRank : byId.getOrDefault(id.toLowerCase(Locale.ROOT), defaultRank);
    }

    public Rank defaultRank() {
        return defaultRank;
    }

    public List<Rank> ranks() {
        return byPriority;
    }

    /** The highest-priority rank this player has permission for. */
    public Rank of(Player player) {
        for (Rank rank : byPriority) {
            if (rank.permission() != null && player.hasPermission(rank.permission())) {
                return rank;
            }
        }
        return defaultRank;
    }
}
