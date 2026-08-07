package me.alpha432.network.smp.crate;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.Menu;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The opening animation: a row of rewards scrolls past a marker, slows down and stops on the
 * reward that was already rolled. The outcome is decided before the animation starts, so
 * closing the inventory early cannot change or duplicate it.
 */
public final class CrateOpenMenu extends Menu {

    private static final int ROW_START = 9;
    private static final int ROW_LENGTH = 9;
    private static final int MARKER_SLOT = ROW_START + 4;

    private final SmpPlugin plugin;
    private final Player viewer;
    private final Crate crate;
    private final CrateReward reward;
    private final List<CrateReward> strip = new ArrayList<>();

    private BukkitTask task;
    private boolean finished;

    public CrateOpenMenu(SmpPlugin plugin, Player viewer, Crate crate, CrateReward reward) {
        super(plugin.messages().get("crate.open-title", "<crate>", crate.displayName()), 3);
        this.plugin = plugin;
        this.viewer = viewer;
        this.crate = crate;
        this.reward = reward;
        buildStrip();
        decorate();
    }

    /** Builds the strip of icons that scrolls past, ending on the actual reward. */
    private void buildStrip() {
        List<CrateReward> pool = new ArrayList<>(crate.rewards());
        if (pool.isEmpty()) {
            pool.add(reward);
        }
        int length = plugin.getConfig().getInt("crates.animation-length", 28);
        for (int i = 0; i < length; i++) {
            strip.add(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
        }
        // The winning icon has to sit where the marker will stop.
        Collections.reverse(strip);
        strip.set(0, reward);
    }

    private void decorate() {
        ItemStack marker = ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE).name("<yellow>▼").build();
        display(MARKER_SLOT - 9, marker);
        display(MARKER_SLOT + 9, marker);
        fill(filler());
    }

    @Override
    public void open(Player player) {
        super.open(player);
        start();
    }

    private void start() {
        int[] position = {strip.size() - 1};
        int[] delay = {1};
        int[] sinceStep = {0};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (finished) {
                return;
            }
            sinceStep[0]++;
            if (sinceStep[0] < delay[0]) {
                return;
            }
            sinceStep[0] = 0;

            for (int i = 0; i < ROW_LENGTH; i++) {
                int index = position[0] - 4 + i;
                CrateReward icon = index >= 0 && index < strip.size() ? strip.get(index) : null;
                set(ROW_START + i, icon == null
                        ? MenuItem.display(filler())
                        : MenuItem.display(ItemBuilder.of(icon.display().clone())
                                .name(icon.displayName()).hideAttributes().build()));
            }
            Sounds.play(viewer, plugin.getConfig().getString("crates.tick-sound", ""), 0.4f, 1.2f);

            if (position[0] <= 0) {
                finish();
                return;
            }
            position[0]--;
            // Slow down towards the end so the last few icons are readable.
            if (position[0] < 8) {
                delay[0] = Math.min(8, delay[0] + 1);
            }
        }, 5L, 1L);
    }

    private void finish() {
        finished = true;
        if (task != null) {
            task.cancel();
        }
        plugin.crates().give(viewer, reward);
        plugin.messages().send(viewer, "crate.won",
                "<crate>", crate.displayName(), "<reward>", reward.displayName());
        Sounds.play(viewer, plugin.getConfig().getString("crates.win-sound", ""), 1f, 1.2f);

        if (reward.broadcast()) {
            Bukkit.broadcast(plugin.messages().get("crate.broadcast",
                    "<player>", viewer.getName(),
                    "<crate>", crate.displayName(),
                    "<reward>", reward.displayName()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.getOpenInventory().getTopInventory().equals(getInventory())) {
                viewer.closeInventory();
            }
        }, plugin.getConfig().getLong("crates.close-delay-ticks", 40L));
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        // Closing early must not cost the player their reward.
        if (!finished) {
            finish();
        } else if (task != null) {
            task.cancel();
        }
    }
}
