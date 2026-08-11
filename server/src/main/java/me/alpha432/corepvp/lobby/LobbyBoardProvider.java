package me.alpha432.corepvp.lobby;

import me.alpha432.corepvp.board.BoardProvider;
import me.alpha432.corepvp.config.ConfigManager;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.rank.RankManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * The hub sidebar. Every line comes from messages.yml, so the layout can be
 * changed without touching code.
 */
public final class LobbyBoardProvider implements BoardProvider {

    private final Messages messages;
    private final ConfigManager configs;
    private final RankManager ranks;

    // Filled in by the phases that own those numbers; zero until then.
    private IntSupplier inFights = () -> 0;
    private IntSupplier inQueue = () -> 0;

    public LobbyBoardProvider(Messages messages, ConfigManager configs, RankManager ranks) {
        this.messages = messages;
        this.configs = configs;
        this.ranks = ranks;
    }

    public void inFights(IntSupplier supplier) {
        this.inFights = supplier;
    }

    public void inQueue(IntSupplier supplier) {
        this.inQueue = supplier;
    }

    @Override
    public Component title(Player player) {
        return messages.render("scoreboard.lobby.title");
    }

    @Override
    public List<Component> lines(Player player) {
        return messages.renderList("scoreboard.lobby.lines",
                Messages.of("online", Bukkit.getOnlinePlayers().size()),
                Messages.of("max", Bukkit.getMaxPlayers()),
                Messages.of("fights", inFights.getAsInt()),
                Messages.of("queued", inQueue.getAsInt()),
                Messages.of("rank", ranks.of(player).display()),
                Messages.of("ping", player.getPing()),
                Messages.of("server", configs.main().getString("server.name", "CorePvP")));
    }
}
