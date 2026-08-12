package me.alpha432.corepvp.ffa;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.arena.Cuboid;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.config.YamlFile;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.util.Cooldown;
import me.alpha432.corepvp.util.Locations;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Permanent open arenas: join, fight, respawn, repeat. */
public final class FfaService {

    private static final String FILE = "ffa.yml";

    private final CorePvPPlugin plugin;
    private final Messages messages;

    private final Map<String, FfaArena> arenas = new LinkedHashMap<>();
    private final Map<UUID, String> players = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killstreaks = new ConcurrentHashMap<>();
    private final Cooldown combatTag = new Cooldown();

    public FfaService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    // ------------------------------------------------------------------
    //  Configuration
    // ------------------------------------------------------------------

    public void load() {
        arenas.clear();
        YamlFile file = plugin.configs().file(FILE);
        ConfigurationSection root = file.get().getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Location spawn = Locations.deserialize(section.getString("spawn"));
            if (spawn == null) {
                plugin.getLogger().warning("FFA arena '" + id + "' has no usable spawn - skipping.");
                continue;
            }
            arenas.put(id.toLowerCase(Locale.ROOT), new FfaArena(id,
                    section.getString("kit", "crystal"),
                    spawn,
                    section.getDouble("safe-radius", 6.0D),
                    Cuboid.deserialize(section.getString("bounds")),
                    section.getInt("death-y", spawn.getBlockY() - 20)));
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " FFA arena(s).");
    }

    public void save() {
        YamlFile file = plugin.configs().file(FILE);
        file.get().set("arenas", null);
        ConfigurationSection root = file.get().createSection("arenas");
        for (FfaArena arena : arenas.values()) {
            ConfigurationSection section = root.createSection(arena.id());
            section.set("kit", arena.kitId());
            section.set("spawn", Locations.serialize(arena.spawn()));
            section.set("safe-radius", arena.safeRadius());
            section.set("bounds", arena.bounds() == null ? null : arena.bounds().serialize());
            section.set("death-y", arena.deathY());
        }
        file.save();
    }

    public void register(FfaArena arena) {
        arenas.put(arena.id(), arena);
        save();
    }

    public boolean remove(String id) {
        boolean removed = arenas.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public FfaArena byId(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public List<FfaArena> all() {
        return List.copyOf(arenas.values());
    }

    public int population(String id) {
        int count = 0;
        for (String arena : players.values()) {
            if (arena.equalsIgnoreCase(id)) {
                count++;
            }
        }
        return count;
    }

    public int total() {
        return players.size();
    }

    // ------------------------------------------------------------------
    //  Playing
    // ------------------------------------------------------------------

    public boolean join(Player player, FfaArena arena) {
        if (plugin.matches().matchOf(player) != null) {
            messages.send(player, "ffa.in-match");
            return false;
        }
        Kit kit = plugin.kits().byId(arena.kitId());
        if (kit == null) {
            messages.send(player, "ffa.kit-missing", Messages.of("kit", arena.kitId()));
            return false;
        }
        if (plugin.queues().inQueue(player)) {
            plugin.queues().leave(player);
        }

        players.put(player.getUniqueId(), arena.id());
        killstreaks.put(player.getUniqueId(), 0);
        plugin.states().set(player, PlayerState.FFA);
        player.teleport(arena.spawn());
        plugin.kitApplier().apply(player, kit);
        plugin.nameTags().collidable(player, true);

        messages.send(player, "ffa.joined", Messages.of("arena", arena.id()));
        return true;
    }

    public void leave(Player player) {
        if (players.remove(player.getUniqueId()) == null) {
            return;
        }
        killstreaks.remove(player.getUniqueId());
        combatTag.reset(player.getUniqueId());
        plugin.combat().reset(player);
        plugin.lobby().sendToLobby(player);
        messages.send(player, "ffa.left");
    }

    public FfaArena arenaOf(Player player) {
        return byId(players.get(player.getUniqueId()));
    }

    public boolean isPlaying(Player player) {
        return players.containsKey(player.getUniqueId());
    }

    public int killstreak(Player player) {
        return killstreaks.getOrDefault(player.getUniqueId(), 0);
    }

    public void tag(Player player) {
        combatTag.set(player.getUniqueId(),
                plugin.configs().main().getLong("ffa.combat-tag-seconds", 15L) * 1000L);
    }

    public boolean tagged(Player player) {
        return !combatTag.isReady(player.getUniqueId());
    }

    /** Death in FFA: respawn with a fresh kit, no dropped items, streaks updated. */
    public void handleDeath(Player victim, Player killer) {
        FfaArena arena = arenaOf(victim);
        if (arena == null) {
            return;
        }

        int lostStreak = killstreaks.getOrDefault(victim.getUniqueId(), 0);
        killstreaks.put(victim.getUniqueId(), 0);
        combatTag.reset(victim.getUniqueId());

        Profile victimProfile = plugin.profiles().require(victim);
        victimProfile.globalDeaths(victimProfile.globalDeaths() + 1);

        if (killer != null && !killer.equals(victim)) {
            int streak = killstreaks.merge(killer.getUniqueId(), 1, Integer::sum);
            Profile killerProfile = plugin.profiles().require(killer);
            killerProfile.globalKills(killerProfile.globalKills() + 1);

            broadcast(arena, messages.render("ffa.killed",
                    Messages.of("victim", victim.getName()),
                    Messages.of("killer", killer.getName()),
                    Messages.of("health", String.format("%.1f", killer.getHealth() / 2.0D))));

            announceMilestone(arena, killer, streak);
            if (lostStreak >= milestoneFloor()) {
                broadcast(arena, messages.render("ffa.streak-ended",
                        Messages.of("victim", victim.getName()),
                        Messages.of("killer", killer.getName()),
                        Messages.of("streak", lostStreak)));
            }
            // Healing the killer is what keeps a good player alive in a crowd.
            Kit kit = plugin.kits().byId(arena.kitId());
            if (kit != null && plugin.configs().main().getBoolean("ffa.refill-on-kill", true)) {
                plugin.kitApplier().apply(killer, kit);
            }
        } else {
            broadcast(arena, messages.render("ffa.died", Messages.of("victim", victim.getName())));
        }

        respawn(victim, arena);
    }

    public void respawn(Player player, FfaArena arena) {
        Kit kit = plugin.kits().byId(arena.kitId());
        player.teleport(arena.spawn());
        if (kit != null) {
            plugin.kitApplier().apply(player, kit);
        }
    }

    private int milestoneFloor() {
        List<Integer> milestones = milestones();
        return milestones.isEmpty() ? Integer.MAX_VALUE : milestones.get(0);
    }

    private List<Integer> milestones() {
        List<Integer> configured = plugin.configs().main().getIntegerList("ffa.killstreak-milestones");
        return configured.isEmpty() ? List.of(5, 10, 15, 25, 50) : configured;
    }

    private void announceMilestone(FfaArena arena, Player killer, int streak) {
        if (!milestones().contains(streak)) {
            return;
        }
        broadcast(arena, messages.render("ffa.streak",
                Messages.of("player", killer.getName()),
                Messages.of("streak", streak)));
    }

    private void broadcast(FfaArena arena, net.kyori.adventure.text.Component message) {
        for (Player player : playersIn(arena)) {
            player.sendMessage(message);
        }
    }

    public List<Player> playersIn(FfaArena arena) {
        List<Player> list = new ArrayList<>();
        players.forEach((uuid, id) -> {
            if (!id.equalsIgnoreCase(arena.id())) {
                return;
            }
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                list.add(player);
            }
        });
        return list;
    }

    /**
     * Logging out while tagged counts as a death, so combat logging is not a
     * free escape from a losing fight.
     */
    public void handleQuit(Player player) {
        FfaArena arena = arenaOf(player);
        if (arena != null && tagged(player)
                && plugin.configs().main().getBoolean("ffa.punish-combat-log", true)) {
            Profile profile = plugin.profiles().require(player);
            profile.globalDeaths(profile.globalDeaths() + 1);
            broadcast(arena, messages.render("ffa.combat-logged", Messages.of("player", player.getName())));
        }
        players.remove(player.getUniqueId());
        killstreaks.remove(player.getUniqueId());
        combatTag.reset(player.getUniqueId());
    }
}
