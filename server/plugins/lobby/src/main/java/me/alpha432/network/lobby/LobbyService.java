package me.alpha432.network.lobby;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Knows where the lobby spawn is and sends players there. */
public final class LobbyService {

    private final LobbyPlugin plugin;
    private final File file;
    private Location spawn;

    public LobbyService(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lobby.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        spawn = Locations.deserialize(config.getString("spawn"));
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawn", Locations.serialize(spawn));
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save lobby.yml", e);
        }
    }

    /** The configured spawn, or the lobby world's own spawn as a fallback. */
    public Location spawn() {
        if (spawn != null) {
            return spawn.clone();
        }
        World world = Bukkit.getWorld(worldName());
        return world == null ? null : world.getSpawnLocation();
    }

    public void spawn(Location location) {
        this.spawn = location.clone();
        World world = location.getWorld();
        if (world != null) {
            world.setSpawnLocation(location);
        }
        save();
    }

    public String worldName() {
        return plugin.getConfig().getString("world", "lobby");
    }

    public boolean isLobby(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName());
    }

    public boolean isLobby(Player player) {
        return isLobby(player.getWorld());
    }

    /** Teleports the player to the lobby; falls back to a message when the world is missing. */
    public void send(Player player) {
        Location target = spawn();
        if (target == null) {
            plugin.messages().send(player, "lobby.missing-world", "<world>", worldName());
            return;
        }
        Core.teleports().teleport(player, target, false);
    }
}
