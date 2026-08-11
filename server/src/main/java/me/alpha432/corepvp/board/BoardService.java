package me.alpha432.corepvp.board;

import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.profile.ProfileManager;
import me.alpha432.corepvp.profile.ProfileSettings;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.state.PlayerStateService;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps every online player's sidebar up to date, picking the provider that
 * matches their current state.
 */
public final class BoardService {

    private final PlayerStateService states;
    private final ProfileManager profiles;
    private final Map<UUID, PlayerBoard> boards = new ConcurrentHashMap<>();
    private final Map<PlayerState, BoardProvider> providers = new EnumMap<>(PlayerState.class);

    private BukkitTask task;

    public BoardService(PlayerStateService states, ProfileManager profiles) {
        this.states = states;
        this.profiles = profiles;
    }

    public void register(PlayerState state, BoardProvider provider) {
        providers.put(state, provider);
    }

    public PlayerBoard board(Player player) {
        return boards.get(player.getUniqueId());
    }

    public PlayerBoard create(Player player) {
        PlayerBoard board = new PlayerBoard(player);
        boards.put(player.getUniqueId(), board);
        return board;
    }

    public void remove(Player player) {
        PlayerBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.clear();
        }
    }

    public void start(long periodTicks) {
        stop();
        task = Tasks.timer(this::tick, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = boards.get(player.getUniqueId());
            if (board == null) {
                continue;
            }

            Profile profile = profiles.get(player);
            boolean wanted = profile == null
                    || profile.settings().get(ProfileSettings.Flag.SCOREBOARD);
            board.visible(wanted);
            if (!wanted) {
                continue;
            }

            BoardProvider provider = providers.get(states.state(player));
            if (provider == null) {
                board.clear();
                continue;
            }
            board.update(provider.title(player), provider.lines(player));
        }
    }
}
