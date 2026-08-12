package me.alpha432.corepvp.ffa;

import me.alpha432.corepvp.board.BoardProvider;
import me.alpha432.corepvp.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/** The sidebar while in a free-for-all arena. */
public final class FfaBoardProvider implements BoardProvider {

    private final Messages messages;
    private final FfaService ffa;

    public FfaBoardProvider(Messages messages, FfaService ffa) {
        this.messages = messages;
        this.ffa = ffa;
    }

    @Override
    public Component title(Player player) {
        return messages.render("scoreboard.ffa.title");
    }

    @Override
    public List<Component> lines(Player player) {
        FfaArena arena = ffa.arenaOf(player);
        if (arena == null) {
            return List.of();
        }
        return messages.renderList("scoreboard.ffa.lines",
                Messages.of("arena", arena.id()),
                Messages.of("kit", arena.kitId()),
                Messages.of("players", ffa.population(arena.id())),
                Messages.of("streak", ffa.killstreak(player)),
                Messages.of("ping", player.getPing()));
    }
}
