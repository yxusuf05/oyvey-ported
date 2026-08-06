package me.alpha432.network.practice.ffa;

import me.alpha432.network.core.util.Locations;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Free-for-all arenas: pick a map, respawn with the kit, chase killstreaks. */
public final class FfaService {

    /** One FFA map. */
    public record FfaArena(String id, String displayName, Material icon, String kit, Location spawn) {
    }

    private final PracticePlugin plugin;
    private final File file;
    private final Map<String, FfaArena> arenas = new LinkedHashMap<>();
    private final Map<UUID, String> players = new HashMap<>();
    private final Map<UUID, Integer> killstreaks = new HashMap<>();

    public FfaService(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ffa.yml");
        load();
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) {
            plugin.saveResource("ffa.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Material icon = Material.matchMaterial(section.getString("icon", "IRON_SWORD"));
            arenas.put(id.toLowerCase(Locale.ROOT), new FfaArena(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display-name", id),
                    icon == null ? Material.IRON_SWORD : icon,
                    section.getString("kit", "nodebuff").toLowerCase(Locale.ROOT),
                    Locations.deserialize(section.getString("spawn"))));
        }
    }

    public void setSpawn(String id, Location location) {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set("arenas." + id.toLowerCase(Locale.ROOT) + ".spawn", Locations.serialize(location));
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save ffa.yml", e);
        }
        load();
    }

    public Optional<FfaArena> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(arenas.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<FfaArena> all() {
        return arenas.values();
    }

    public List<String> names() {
        return new ArrayList<>(arenas.keySet());
    }

    public boolean isPlaying(Player player) {
        return players.containsKey(player.getUniqueId());
    }

    public String arenaOf(Player player) {
        return players.get(player.getUniqueId());
    }

    public int playerCount(String arenaId) {
        int count = 0;
        for (String id : players.values()) {
            if (id.equals(arenaId)) {
                count++;
            }
        }
        return count;
    }

    public int killstreak(Player player) {
        return killstreaks.getOrDefault(player.getUniqueId(), 0);
    }

    /** @return false when the arena has no spawn or its kit is missing. */
    public boolean join(Player player, FfaArena arena) {
        if (arena.spawn() == null) {
            plugin.messages().send(player, "ffa.no-spawn", "<arena>", arena.displayName());
            return false;
        }
        Optional<PracticeKit> kit = plugin.kits().get(arena.kit());
        if (kit.isEmpty()) {
            plugin.messages().send(player, "ffa.no-kit", "<kit>", arena.kit());
            return false;
        }
        players.put(player.getUniqueId(), arena.id());
        killstreaks.put(player.getUniqueId(), 0);
        player.teleport(arena.spawn());
        equip(player, kit.get());
        plugin.messages().send(player, "ffa.joined", "<arena>", arena.displayName());
        return true;
    }

    /** Called on death and on join; puts the kit back on the player. */
    public void equip(Player player, PracticeKit kit) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(attribute == null ? 20.0D : attribute.getValue());
        player.setMaximumNoDamageTicks(kit.damageTicks());
        player.getInventory().setStorageContents(plugin.kits().contentsFor(player, kit));
        if (kit.helmet() != null) {
            player.getInventory().setHelmet(kit.helmet().clone());
        }
        if (kit.chestplate() != null) {
            player.getInventory().setChestplate(kit.chestplate().clone());
        }
        if (kit.leggings() != null) {
            player.getInventory().setLeggings(kit.leggings().clone());
        }
        if (kit.boots() != null) {
            player.getInventory().setBoots(kit.boots().clone());
        }
        kit.effects().forEach(player::addPotionEffect);
    }

    /** Respawns the player in the same arena with a fresh kit. */
    public void respawn(Player player) {
        FfaArena arena = get(arenaOf(player)).orElse(null);
        if (arena == null) {
            leave(player, true);
            return;
        }
        killstreaks.put(player.getUniqueId(), 0);
        plugin.kits().get(arena.kit()).ifPresent(kit -> {
            player.teleport(arena.spawn());
            equip(player, kit);
        });
    }

    /** Counts a kill and announces milestone streaks. */
    public void registerKill(Player killer) {
        int streak = killstreaks.merge(killer.getUniqueId(), 1, Integer::sum);
        plugin.messages().send(killer, "ffa.kill", "<streak>", String.valueOf(streak));
        int milestone = plugin.getConfig().getInt("ffa.killstreak-announce", 5);
        if (milestone > 0 && streak % milestone == 0) {
            for (UUID uuid : players.keySet()) {
                Player online = org.bukkit.Bukkit.getPlayer(uuid);
                if (online != null) {
                    plugin.messages().send(online, "ffa.killstreak",
                            "<player>", killer.getName(), "<streak>", String.valueOf(streak));
                }
            }
        }
    }

    public void leave(Player player, boolean toHub) {
        players.remove(player.getUniqueId());
        killstreaks.remove(player.getUniqueId());
        if (toHub) {
            plugin.hub().send(player);
        }
    }

    public void leaveAll() {
        for (UUID uuid : new ArrayList<>(players.keySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                leave(player, true);
            }
        }
        players.clear();
        killstreaks.clear();
    }
}
