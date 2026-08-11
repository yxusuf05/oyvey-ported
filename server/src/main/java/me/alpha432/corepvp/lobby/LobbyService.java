package me.alpha432.corepvp.lobby;

import me.alpha432.corepvp.config.ConfigManager;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.rank.NameTagService;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.state.PlayerStateService;
import me.alpha432.corepvp.util.ItemBuilder;
import me.alpha432.corepvp.util.Locations;
import me.alpha432.corepvp.world.WorldService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** The hub: spawn point, hotbar items and the way back from every other mode. */
public final class LobbyService {

    private final Plugin plugin;
    private final ConfigManager configs;
    private final Messages messages;
    private final PlayerStateService states;
    private final WorldService worlds;
    private final NameTagService nameTags;
    private final NamespacedKey itemKey;

    private final Map<HubItem, Consumer<Player>> actions = new EnumMap<>(HubItem.class);
    private Location spawn;

    public LobbyService(Plugin plugin, ConfigManager configs, Messages messages,
                        PlayerStateService states, WorldService worlds, NameTagService nameTags) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.states = states;
        this.worlds = worlds;
        this.nameTags = nameTags;
        this.itemKey = new NamespacedKey(plugin, "hub_item");
        reload();
    }

    public void reload() {
        spawn = Locations.deserialize(configs.main().getString("lobby.spawn"));
    }

    public NamespacedKey itemKey() {
        return itemKey;
    }

    /** Falls back to the lobby world's own spawn until an admin sets one. */
    public Location spawn() {
        if (spawn != null) {
            return spawn;
        }
        return worlds.lobby() == null ? null : worlds.lobby().getSpawnLocation();
    }

    public void setSpawn(Location location) {
        this.spawn = location;
        configs.main().set("lobby.spawn", Locations.serialize(location));
        configs.file("config.yml").save();
    }

    /** Registered by later phases so a hub item opens their menu. */
    public void setAction(HubItem item, Consumer<Player> action) {
        actions.put(item, action);
    }

    /**
     * The single way back to the hub from anywhere. Resets the player through
     * {@link PlayerStateService}, so nothing from the previous mode survives.
     */
    public void sendToLobby(Player player) {
        states.set(player, PlayerState.LOBBY);
        Location target = spawn();
        if (target != null) {
            player.teleport(target);
        }
        giveHubItems(player);
        nameTags.collidable(player, false);
    }

    public void giveHubItems(Player player) {
        player.getInventory().clear();
        for (HubItem item : HubItem.values()) {
            if (!actions.containsKey(item)) {
                // Nothing has claimed this slot yet, so showing it would only
                // hand the player a button that does nothing.
                continue;
            }
            player.getInventory().setItem(item.slot(), build(item));
        }
        player.getInventory().setHeldItemSlot(0);
        player.updateInventory();
    }

    private ItemStack build(HubItem item) {
        List<Component> lore = messages.renderList(item.loreKey());
        return ItemBuilder.of(item.material())
                .name(messages.render(item.nameKey()))
                .loreComponents(lore)
                .meta(meta -> meta.getPersistentDataContainer()
                        .set(itemKey, PersistentDataType.STRING, item.name()))
                .build();
    }

    /** Resolves a clicked item back to its hub item, or null. */
    public HubItem itemOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return HubItem.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void click(Player player, HubItem item) {
        Consumer<Player> action = actions.get(item);
        if (action != null) {
            action.accept(player);
        }
    }
}
