package me.alpha432.corepvp.board;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/** Supplies the sidebar contents for one {@link me.alpha432.corepvp.state.PlayerState}. */
public interface BoardProvider {

    Component title(Player player);

    List<Component> lines(Player player);
}
