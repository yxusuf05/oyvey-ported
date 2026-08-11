package me.alpha432.corepvp.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public record MenuClick(Player player, Menu menu, int slot, ClickType type) {

    public boolean isLeft() {
        return type == ClickType.LEFT || type == ClickType.SHIFT_LEFT;
    }

    public boolean isRight() {
        return type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;
    }
}
