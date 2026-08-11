package me.alpha432.corepvp.world;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Creates and configures the worlds the server runs on.
 *
 * <p>Void worlds are made with vanilla's flat generator and an empty layer
 * list, which avoids shipping a custom {@code ChunkGenerator} and behaves
 * identically across Minecraft versions.
 */
public final class WorldService {

    /** Flat generator settings that produce nothing but void. */
    public static final String VOID_PRESET = "{\"layers\":[],\"biome\":\"minecraft:the_void\"}";

    private final Plugin plugin;

    private World lobby;
    private World arenas;
    private World ffa;
    private World survival;

    public WorldService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection config) {
        String lobbyName = config.getString("lobby", "world");
        String arenaName = config.getString("arenas", "world_arenas");
        String ffaName = config.getString("ffa", "world_ffa");
        String survivalName = config.getString("survival", "world_survival");

        lobby = voidWorld(lobbyName);
        arenas = voidWorld(arenaName);
        ffa = voidWorld(ffaName);

        if (config.getBoolean("create-survival", true)) {
            survival = survivalWorld(survivalName);
        }

        applyLobbyRules(lobby);
        applyArenaRules(arenas);
        applyArenaRules(ffa);
        if (survival != null) {
            applySurvivalRules(survival);
        }
    }

    private World voidWorld(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        plugin.getLogger().info("Creating void world '" + name + "'");
        World world = new WorldCreator(name)
                .type(WorldType.FLAT)
                .generatorSettings(VOID_PRESET)
                .generateStructures(false)
                .environment(World.Environment.NORMAL)
                .createWorld();
        if (world == null) {
            plugin.getLogger().severe("Failed to create world '" + name + "'");
        }
        return world;
    }

    private World survivalWorld(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        plugin.getLogger().info("Creating survival world '" + name + "'");
        return new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .createWorld();
    }

    private void applyCommon(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
    }

    private void applyLobbyRules(World world) {
        if (world == null) {
            return;
        }
        applyCommon(world);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setDifficulty(Difficulty.PEACEFUL);
    }

    private void applyArenaRules(World world) {
        if (world == null) {
            return;
        }
        applyCommon(world);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        // Crystal PvP is built on explosions breaking obsidian, so griefing has
        // to stay on here. The rollback journal puts the arena back afterwards.
        world.setGameRule(GameRules.MOB_GRIEFING, true);
        // Never PEACEFUL: it forces instant health regeneration, which silently
        // breaks every no-regeneration kit.
        world.setDifficulty(Difficulty.NORMAL);
        world.setAutoSave(false);
    }

    private void applySurvivalRules(World world) {
        applyCommon(world);
        world.setGameRule(GameRules.ADVANCE_TIME, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, true);
        world.setGameRule(GameRules.MOB_GRIEFING, true);
        world.setDifficulty(Difficulty.NORMAL);
    }

    public World lobby() {
        return lobby;
    }

    public World arenas() {
        return arenas;
    }

    public World ffa() {
        return ffa;
    }

    public World survival() {
        return survival;
    }
}
