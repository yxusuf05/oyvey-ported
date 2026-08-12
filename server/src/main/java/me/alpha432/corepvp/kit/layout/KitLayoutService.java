package me.alpha432.corepvp.kit.layout;

import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.kit.KitSerializer;
import me.alpha432.corepvp.storage.Database;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Loads and stores per-player kit layouts. */
public final class KitLayoutService {

    /** Enough choice to matter, few enough to fit one menu row. */
    public static final int MAX_LAYOUTS = 4;

    private static final String SELECT =
            "SELECT kit, slot, label, contents FROM corepvp_kit_layouts WHERE uuid = ?";
    private static final String UPDATE =
            "UPDATE corepvp_kit_layouts SET label = ?, contents = ? WHERE uuid = ? AND kit = ? AND slot = ?";
    private static final String INSERT =
            "INSERT INTO corepvp_kit_layouts (uuid, kit, slot, label, contents) VALUES (?, ?, ?, ?, ?)";
    private static final String DELETE =
            "DELETE FROM corepvp_kit_layouts WHERE uuid = ? AND kit = ? AND slot = ?";

    private final Plugin plugin;
    private final Database database;
    /** uuid -> kit id -> slot -> layout */
    private final Map<UUID, Map<String, Map<Integer, KitLayout>>> cache = new ConcurrentHashMap<>();
    /** uuid -> kit id -> selected slot */
    private final Map<UUID, Map<String, Integer>> selected = new ConcurrentHashMap<>();

    public KitLayoutService(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    /** Called off the main thread while the player is still logging in. */
    public void load(Connection connection, UUID uuid) throws SQLException {
        Map<String, Map<Integer, KitLayout>> byKit = new ConcurrentHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String kit = result.getString("kit").toLowerCase(Locale.ROOT);
                    int slot = result.getInt("slot");
                    KitLayout layout = new KitLayout(slot, result.getString("label"),
                            KitSerializer.decode(result.getString("contents")));
                    byKit.computeIfAbsent(kit, key -> new ConcurrentHashMap<>()).put(slot, layout);
                }
            }
        }
        cache.put(uuid, byKit);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
        selected.remove(uuid);
    }

    public Map<Integer, KitLayout> layouts(UUID uuid, String kitId) {
        Map<String, Map<Integer, KitLayout>> byKit = cache.get(uuid);
        if (byKit == null) {
            return Map.of();
        }
        return byKit.getOrDefault(kitId.toLowerCase(Locale.ROOT), Map.of());
    }

    public KitLayout layout(UUID uuid, String kitId, int slot) {
        return layouts(uuid, kitId).get(slot);
    }

    public int selectedSlot(UUID uuid, String kitId) {
        Map<String, Integer> byKit = selected.get(uuid);
        return byKit == null ? 0 : byKit.getOrDefault(kitId.toLowerCase(Locale.ROOT), 0);
    }

    public void select(UUID uuid, String kitId, int slot) {
        selected.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>())
                .put(kitId.toLowerCase(Locale.ROOT), slot);
    }

    /**
     * The hotbar a player should get for this kit, or null to use the kit's own
     * order. Stale layouts are dropped rather than applied.
     */
    public org.bukkit.inventory.ItemStack[] hotbarFor(Player player, Kit kit) {
        KitLayout layout = layout(player.getUniqueId(), kit.id(),
                selectedSlot(player.getUniqueId(), kit.id()));
        if (layout == null || layout.isEmpty() || !layout.matches(kit)) {
            return null;
        }
        return layout.hotbar();
    }

    public void save(UUID uuid, String kitId, KitLayout layout) {
        String key = kitId.toLowerCase(Locale.ROOT);
        cache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                .put(layout.slot(), layout);

        String encoded = KitSerializer.encode(layout.hotbar());
        database.execute(connection -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(UPDATE)) {
                statement.setString(1, layout.label());
                statement.setString(2, encoded);
                statement.setString(3, uuid.toString());
                statement.setString(4, key);
                statement.setInt(5, layout.slot());
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, key);
                    statement.setInt(3, layout.slot());
                    statement.setString(4, layout.label());
                    statement.setString(5, encoded);
                    statement.executeUpdate();
                }
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, "Could not save a kit layout", throwable);
            return null;
        });
    }

    public void reset(UUID uuid, String kitId, int slot) {
        String key = kitId.toLowerCase(Locale.ROOT);
        Map<String, Map<Integer, KitLayout>> byKit = cache.get(uuid);
        if (byKit != null && byKit.containsKey(key)) {
            byKit.get(key).remove(slot);
        }
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, key);
                statement.setInt(3, slot);
                statement.executeUpdate();
            }
        });
    }

    public Map<Integer, KitLayout> orderedLayouts(UUID uuid, String kitId) {
        Map<Integer, KitLayout> layouts = new LinkedHashMap<>();
        for (int slot = 0; slot < MAX_LAYOUTS; slot++) {
            KitLayout layout = layout(uuid, kitId, slot);
            if (layout != null) {
                layouts.put(slot, layout);
            }
        }
        return layouts;
    }
}
