package me.alpha432.corepvp.crystal;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.match.Match;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tops up consumables for kits that ask for it.
 *
 * <p>Crystal fights burn through crystals, obsidian and gapples faster than
 * anyone wants to manage, and running out mid-fight decides matches on
 * inventory management rather than play. Kits opt in with the infiniteItems
 * flag; the rest are untouched.
 */
public final class RefillService {

    private final CorePvPPlugin plugin;
    private final Map<Material, Integer> targets = new LinkedHashMap<>();
    private BukkitTask task;

    public RefillService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        targets.clear();
        ConfigurationSection section = plugin.configs().main()
                .getConfigurationSection("crystal.refill");
        if (section == null) {
            targets.put(Material.END_CRYSTAL, 64);
            targets.put(Material.OBSIDIAN, 64);
            targets.put(Material.ENCHANTED_GOLDEN_APPLE, 16);
            targets.put(Material.EXPERIENCE_BOTTLE, 64);
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.getLogger().warning("Unknown refill material '" + key + "' - skipping.");
                continue;
            }
            targets.put(material, Math.max(1, section.getInt(key)));
        }
    }

    public void start() {
        stop();
        long period = Math.max(10L, plugin.configs().main().getLong("crystal.refill-interval-ticks", 20L));
        task = Tasks.timer(this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (targets.isEmpty()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Match match = plugin.matches().matchOf(player);
            if (match == null || !match.contains(player.getUniqueId())) {
                continue;
            }
            Kit kit = match.kit();
            if (!kit.flags().infiniteItems()) {
                continue;
            }
            refill(player, kit);
        }
    }

    /** Only tops up what the kit actually contains - never hands out new items. */
    public void refill(Player player, Kit kit) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;

        for (Map.Entry<Material, Integer> target : targets.entrySet()) {
            Material material = target.getKey();
            if (!kitContains(kit, material)) {
                continue;
            }
            int have = count(inventory, material);
            if (have >= target.getValue()) {
                continue;
            }
            inventory.addItem(new ItemStack(material, target.getValue() - have));
            changed = true;
        }

        if (changed) {
            player.updateInventory();
        }
    }

    private boolean kitContains(Kit kit, Material material) {
        for (ItemStack item : kit.contents()) {
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
