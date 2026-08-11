package me.alpha432.corepvp.lobby;

import me.alpha432.corepvp.board.BoardService;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.rank.NameTagService;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.state.PlayerStateService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryHolder;

/** Hub protection, hub item clicks, and join/quit wiring. */
public final class LobbyListener implements Listener {

    private final LobbyService lobby;
    private final PlayerStateService states;
    private final BoardService boards;
    private final NameTagService nameTags;
    private final Messages messages;

    public LobbyListener(LobbyService lobby, PlayerStateService states, BoardService boards,
                         NameTagService nameTags, Messages messages) {
        this.lobby = lobby;
        this.states = states;
        this.boards = boards;
        this.nameTags = nameTags;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        boards.create(player);
        nameTags.populate(player);
        nameTags.update(player);

        lobby.sendToLobby(player);

        event.joinMessage(messages.render("lobby.join", Messages.of("player", player.getName())));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        nameTags.remove(player);
        boards.remove(player);
        states.forget(player.getUniqueId());
        event.quitMessage(messages.render("lobby.quit", Messages.of("player", player.getName())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedState(event.getPlayer()) && !event.getPlayer().hasPermission("corepvp.build")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protectedState(event.getPlayer()) && !event.getPlayer().hasPermission("corepvp.build")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && protectedState(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && protectedState(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (protectedState(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && protectedState(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (protectedState(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Stops hub items from being dragged around the player's own inventory. */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !protectedState(player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof me.alpha432.corepvp.menu.Menu) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!protectedState(player)) {
            return;
        }
        HubItem item = lobby.itemOf(event.getItem());
        if (item == null) {
            return;
        }
        event.setCancelled(true);
        lobby.click(player, item);
    }

    /** Falling out of a void lobby puts the player back at spawn instead of killing them. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() > -32.0D) {
            return;
        }
        Player player = event.getPlayer();
        if (!protectedState(player)) {
            return;
        }
        if (lobby.spawn() != null) {
            player.teleport(lobby.spawn());
        }
    }

    private boolean protectedState(Player player) {
        PlayerState state = states.state(player);
        return state == PlayerState.LOBBY || state == PlayerState.QUEUE;
    }
}
