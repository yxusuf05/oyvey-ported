package me.alpha432.network.smp;

import me.alpha432.network.core.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Where the SMP spawn is. Also keeps the world spawn in sync so the lobby selector lands right. */
public final class SpawnService {

    private final Plugin plugin;
    private final File file;
    private Location spawn;

    public SpawnService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        spawn = Locations.deserialize(YamlConfiguration.loadConfiguration(file).getString("spawn"));
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawn", Locations.serialize(spawn));
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save spawn.yml", e);
        }
    }

    public String worldName() {
        return plugin.getConfig().getString("world", "smp");
    }

    public boolean isSmp(World world) {
        if (world == null) {
            return false;
        }
        return plugin.getConfig().getStringList("worlds").contains(world.getName())
                || world.getName().equalsIgnoreCase(worldName());
    }

    public boolean isSmp(Player player) {
        return isSmp(player.getWorld());
    }

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
            // The lobby selector teleports to the world spawn, so both must point at the same spot.
            world.setSpawnLocation(location);
        }
        save();
    }
}
