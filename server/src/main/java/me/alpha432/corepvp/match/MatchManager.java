package me.alpha432.corepvp.match;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.arena.Arena;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.match.snapshot.MatchSnapshot;
import me.alpha432.corepvp.match.snapshot.PlayerSnapshot;
import me.alpha432.corepvp.match.snapshot.SnapshotService;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.util.Tasks;
import me.alpha432.corepvp.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Creates, ticks and ends matches.
 *
 * <p>All matches are driven by one repeating task rather than a runnable each,
 * so the cost of running twenty fights is one scheduled job.
 */
public final class MatchManager {

    private static final NamedTextColor[] TEAM_COLORS = {
            NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN,
            NamedTextColor.YELLOW, NamedTextColor.LIGHT_PURPLE, NamedTextColor.AQUA};

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final SnapshotService snapshots;

    private final List<Match> matches = new CopyOnWriteArrayList<>();
    private final Map<UUID, Match> byPlayer = new ConcurrentHashMap<>();
    /** Who hit whom last, so void and fall deaths still credit a killer. */
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackAt = new ConcurrentHashMap<>();
    private final List<BiConsumer<Match, MatchTeam>> endHandlers = new ArrayList<>();

    private BukkitTask task;
    private int countdownSeconds;
    private int endSeconds;
    private int maxDurationMinutes;
    private long combatTagMillis;

    public MatchManager(CorePvPPlugin plugin, SnapshotService snapshots) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.snapshots = snapshots;
        reload();
    }

    public void reload() {
        countdownSeconds = plugin.configs().main().getInt("match.countdown-seconds", 5);
        endSeconds = plugin.configs().main().getInt("match.end-seconds", 3);
        maxDurationMinutes = plugin.configs().main().getInt("match.max-duration-minutes", 15);
        combatTagMillis = plugin.configs().main().getLong("match.combat-tag-seconds", 10L) * 1000L;
    }

    public void start() {
        stop();
        task = Tasks.timer(this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------
    //  Creation
    // ------------------------------------------------------------------

    /**
     * Starts a match. The arena must already have been acquired by the caller,
     * so a queue entry is never consumed when no arena is free.
     */
    public Match create(Kit kit, MatchType type, Arena arena, List<List<Player>> sides) {
        List<MatchTeam> teams = new ArrayList<>();
        for (int i = 0; i < sides.size(); i++) {
            List<UUID> members = sides.get(i).stream().map(Player::getUniqueId).toList();
            teams.add(new MatchTeam(i, members, TEAM_COLORS[i % TEAM_COLORS.length]));
        }

        Match match = new Match(snapshots.nextId(), kit, arena, type, teams, countdownSeconds);
        matches.add(match);

        for (int i = 0; i < sides.size(); i++) {
            for (Player player : sides.get(i)) {
                byPlayer.put(player.getUniqueId(), match);
                plugin.states().set(player, PlayerState.MATCH_STARTING);
                plugin.arenas().track(player, arena);
                plugin.nameTags().collidable(player, true);
                player.teleport(arena.spawn(i));
                plugin.kitApplier().apply(player, kit);
                player.showTitle(Title.title(
                        messages.render("match.start-title", Messages.of("kit", kit.displayName())),
                        messages.render("match.start-subtitle",
                                Messages.of("opponent", opponentNames(teams, i))),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(300))));
            }
        }
        return match;
    }

    private String opponentNames(List<MatchTeam> teams, int ownIndex) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            if (i != ownIndex) {
                names.add(teams.get(i).displayName());
            }
        }
        return String.join(", ", names);
    }

    // ------------------------------------------------------------------
    //  Ticking
    // ------------------------------------------------------------------

    private void tick() {
        for (Match match : matches) {
            match.tick();
            switch (match.state()) {
                case STARTING -> tickStarting(match);
                case FIGHTING -> tickFighting(match);
                case ENDING -> tickEnding(match);
                default -> {
                }
            }
        }
    }

    private void tickStarting(Match match) {
        if (match.ticks() % 20 != 0) {
            return;
        }
        int remaining = match.decrementCountdown();
        if (remaining > 0) {
            for (Player player : match.audience()) {
                player.sendMessage(messages.render("match.countdown", Messages.of("seconds", remaining)));
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.0F);
            }
            return;
        }
        match.state(MatchState.FIGHTING);
        for (Player player : match.audience()) {
            player.sendMessage(messages.render("match.begin"));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 2.0F);
        }
        for (Player player : match.participants()) {
            plugin.states().set(player, PlayerState.MATCH_FIGHTING);
            plugin.kitApplier().apply(player, match.kit());
        }
    }

    private void tickFighting(Match match) {
        // Sumo and void arenas are decided by falling, not by damage.
        int deathY = match.arena().deathY();
        if (deathY != Integer.MIN_VALUE && match.ticks() % 4 == 0) {
            for (MatchTeam team : match.teams()) {
                for (Player player : team.alivePlayers()) {
                    if (player.getLocation().getY() < deathY) {
                        handleDeath(player, resolveKiller(player));
                    }
                }
            }
        }

        if (maxDurationMinutes > 0 && match.durationMillis() > maxDurationMinutes * 60_000L) {
            match.broadcast(messages.render("match.time-limit"));
            end(match, null);
        }
    }

    private void tickEnding(Match match) {
        if (match.ticks() >= endSeconds * 20L) {
            finish(match);
        }
    }

    // ------------------------------------------------------------------
    //  Deaths and the end of a match
    // ------------------------------------------------------------------

    public void handleDeath(Player player, Player killer) {
        Match match = byPlayer.get(player.getUniqueId());
        if (match == null || match.state() != MatchState.FIGHTING) {
            return;
        }
        MatchTeam team = match.teamOf(player.getUniqueId());
        if (team == null || !team.isAlive(player.getUniqueId())) {
            return;
        }

        team.kill(player.getUniqueId());
        // Capture before the state change: moving the player to spectator
        // clears their inventory, and that inventory is the whole point of the
        // post-match screen.
        match.recordSnapshot(player.getUniqueId(),
                PlayerSnapshot.capture(player, match.stats(player.getUniqueId()), false));

        Component message = killer == null
                ? messages.render("match.death", Messages.of("player", player.getName()))
                : messages.render("match.killed",
                Messages.of("player", player.getName()),
                Messages.of("killer", killer.getName()),
                Messages.of("health", String.format("%.1f", killer.getHealth() / 2.0D)));
        match.broadcast(message);

        plugin.states().set(player, PlayerState.MATCH_DEAD);
        player.teleport(match.arena().spawn(team.index()));

        if (killer != null) {
            match.stats(killer.getUniqueId());
            plugin.profiles().require(killer).globalKills(plugin.profiles().require(killer).globalKills() + 1);
            plugin.profiles().require(killer).kit(match.kit().id())
                    .kills(plugin.profiles().require(killer).kit(match.kit().id()).kills() + 1);
        }
        var profile = plugin.profiles().require(player);
        profile.globalDeaths(profile.globalDeaths() + 1);
        profile.kit(match.kit().id()).deaths(profile.kit(match.kit().id()).deaths() + 1);

        List<MatchTeam> alive = match.aliveTeams();
        if (alive.size() <= 1) {
            end(match, alive.isEmpty() ? null : alive.get(0));
        }
    }

    /** The last player to hit this one, if it was recent enough to count. */
    public Player resolveKiller(Player victim) {
        UUID attacker = lastAttacker.get(victim.getUniqueId());
        Long at = lastAttackAt.get(victim.getUniqueId());
        if (attacker == null || at == null || System.currentTimeMillis() - at > combatTagMillis) {
            return null;
        }
        Player player = plugin.getServer().getPlayer(attacker);
        return player != null && player.isOnline() ? player : null;
    }

    public void tagAttacker(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttackAt.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    public void end(Match match, MatchTeam winner) {
        if (match.state() == MatchState.ENDING || match.state() == MatchState.ENDED) {
            return;
        }
        match.winner(winner);
        match.state(MatchState.ENDING);

        MatchSnapshot snapshot = snapshots.capture(match);

        Component result = winner == null
                ? messages.render("match.draw")
                : messages.render("match.result",
                Messages.of("winner", winner.displayName()),
                Messages.of("loser", loserNames(match, winner)),
                Messages.of("duration", TimeUtil.clock(match.durationMillis())));
        match.broadcast(result);
        match.broadcast(messages.render("match.inventories",
                Messages.of("players", inventoryLinks(snapshot))));

        for (BiConsumer<Match, MatchTeam> handler : endHandlers) {
            handler.accept(match, winner);
        }
    }

    /**
     * Registered by whatever needs to react to a finished match - ELO, party
     * bookkeeping, rematch offers - so the match engine does not have to know
     * about any of them.
     */
    public void onEnd(BiConsumer<Match, MatchTeam> handler) {
        endHandlers.add(handler);
    }

    /**
     * One clickable name per player, each running /inv. Adventure's callback
     * click events are not in the bundled version, so these point at a real
     * command - which also works as a fallback players can type.
     */
    private Component inventoryLinks(MatchSnapshot snapshot) {
        Component links = Component.empty();
        boolean first = true;
        for (String name : snapshot.names()) {
            if (!first) {
                links = links.append(messages.parse("<dark_gray>, "));
            }
            links = links.append(Component.text(name)
                    .color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/inv " + snapshot.id() + " " + name))
                    .hoverEvent(HoverEvent.showText(
                            messages.render("match.inventory-hover", Messages.of("player", name)))));
            first = false;
        }
        return links;
    }

    private String loserNames(Match match, MatchTeam winner) {
        List<String> names = new ArrayList<>();
        for (MatchTeam team : match.teams()) {
            if (team != winner) {
                names.add(team.displayName());
            }
        }
        return String.join(", ", names);
    }

    private void finish(Match match) {
        match.state(MatchState.ENDED);
        matches.remove(match);

        for (Player player : match.participants()) {
            byPlayer.remove(player.getUniqueId());
            lastAttacker.remove(player.getUniqueId());
            lastAttackAt.remove(player.getUniqueId());
            plugin.arenas().untrack(player);
            plugin.combat().reset(player);
            plugin.lobby().sendToLobby(player);
        }
        for (UUID uuid : match.spectators()) {
            Player spectator = plugin.getServer().getPlayer(uuid);
            if (spectator != null && spectator.isOnline()) {
                byPlayer.remove(uuid);
                plugin.lobby().sendToLobby(spectator);
            }
        }
        plugin.arenas().release(match.arena());
    }

    /** Ends everything immediately, for shutdown. */
    public void endAllBlocking() {
        for (Match match : new ArrayList<>(matches)) {
            match.state(MatchState.ENDED);
            for (Player player : match.participants()) {
                plugin.arenas().untrack(player);
                plugin.combat().reset(player);
                plugin.states().set(player, PlayerState.LOBBY);
            }
        }
        matches.clear();
        byPlayer.clear();
    }

    // ------------------------------------------------------------------
    //  Spectating and lookups
    // ------------------------------------------------------------------

    public boolean spectate(Player spectator, Player target) {
        Match match = byPlayer.get(target.getUniqueId());
        if (match == null || match.state() == MatchState.ENDED) {
            return false;
        }
        match.addSpectator(spectator.getUniqueId());
        byPlayer.put(spectator.getUniqueId(), match);
        plugin.states().set(spectator, PlayerState.SPECTATING);
        spectator.teleport(target.getLocation());
        match.broadcast(messages.render("match.spectator-joined",
                Messages.of("player", spectator.getName())));
        return true;
    }

    public void stopSpectating(Player spectator) {
        Match match = byPlayer.get(spectator.getUniqueId());
        if (match == null || match.contains(spectator.getUniqueId())) {
            return;
        }
        match.removeSpectator(spectator.getUniqueId());
        byPlayer.remove(spectator.getUniqueId());
        plugin.lobby().sendToLobby(spectator);
    }

    /** Cleans up when someone disconnects mid-match. */
    public void handleQuit(Player player) {
        Match match = byPlayer.remove(player.getUniqueId());
        if (match == null) {
            return;
        }
        lastAttacker.remove(player.getUniqueId());
        lastAttackAt.remove(player.getUniqueId());
        plugin.arenas().untrack(player);

        if (!match.contains(player.getUniqueId())) {
            match.removeSpectator(player.getUniqueId());
            return;
        }
        MatchTeam team = match.teamOf(player.getUniqueId());
        if (team != null && team.isAlive(player.getUniqueId())) {
            team.kill(player.getUniqueId());
            match.broadcast(messages.render("match.disconnected", Messages.of("player", player.getName())));
            List<MatchTeam> alive = match.aliveTeams();
            if (alive.size() <= 1 && match.state() == MatchState.FIGHTING) {
                end(match, alive.isEmpty() ? null : alive.get(0));
            }
        }
    }

    public Match matchOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public Match matchOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public List<Match> matches() {
        return List.copyOf(matches);
    }

    /** Players currently in a running match, for the lobby sidebar. */
    public int fightingCount() {
        int count = 0;
        for (Match match : matches) {
            if (match.state() == MatchState.FIGHTING) {
                for (MatchTeam team : match.teams()) {
                    count += team.size();
                }
            }
        }
        return count;
    }

    public Map<String, Integer> countsByKit() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Match match : matches) {
            counts.merge(match.kit().id(), 1, Integer::sum);
        }
        return counts;
    }
}
