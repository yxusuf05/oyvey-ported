package me.alpha432.network.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A menu whose content spills over several pages. The bottom row holds the navigation, the
 * rows above it hold the content.
 */
public class PagedMenu extends Menu {

    private final List<MenuItem> content = new ArrayList<>();
    private final int contentSlots;
    private int page;

    public PagedMenu(Component title, int rows) {
        super(title, rows);
        this.contentSlots = (rows() - 1) * 9;
    }

    public PagedMenu content(List<MenuItem> items) {
        content.clear();
        content.addAll(items);
        page = Math.min(page, Math.max(0, pageCount() - 1));
        return this;
    }

    public int pageCount() {
        return Math.max(1, (int) Math.ceil(content.size() / (double) contentSlots));
    }

    public int page() {
        return page;
    }

    /** Rebuilds the visible page. Call after {@link #content(List)}. */
    public void render() {
        clear();
        int from = page * contentSlots;
        for (int i = 0; i < contentSlots; i++) {
            int index = from + i;
            if (index >= content.size()) {
                break;
            }
            set(i, content.get(index));
        }

        int navRow = (rows() - 1) * 9;
        if (page > 0) {
            set(navRow, ItemBuilder.of(Material.ARROW)
                    .name("<yellow>« Seite " + page)
                    .build(), event -> {
                page--;
                render();
            });
        }
        if (page < pageCount() - 1) {
            set(navRow + 8, ItemBuilder.of(Material.ARROW)
                    .name("<yellow>Seite " + (page + 2) + " »")
                    .build(), event -> {
                page++;
                render();
            });
        }
        set(navRow + 4, MenuItem.display(ItemBuilder.of(Material.PAPER)
                .name("<gray>Seite <white>" + (page + 1) + "<gray>/<white>" + pageCount())
                .build()));
        fill(filler());
    }

    @Override
    public void open(Player player) {
        render();
        super.open(player);
    }
}
