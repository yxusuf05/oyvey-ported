package me.alpha432.corepvp.state;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single place a player's inventory, gamemode, health and flight are
 * changed.
 *
 * <p>Every mode in this plugin hands the player over here on entry and exit.
 * That is what stops the classic bugs of this genre: keeping kit items after a
 * match, taking a lobby compass into survival, or respawning in creative.
 * Nothing outside this class may call {@code setGameMode}, {@code getInventory().clear()}
 * or {@code setAllowFlight} on a player.
 */
public final class PlayerStateService {

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    /** Inventories parked while the player is somewhere else (survival, staff mode). */
    private final Map<UUID, Map<PlayerState, StoredInventory>> parked = new ConcurrentHashMap<>();

    public PlayerState state(Player player) {
        return states.getOrDefault(player.getUniqueId(), PlayerState.LOBBY);
    }

    public boolean is(Player player, PlayerState state) {
        return state(player) == state;
    }

    /**
     * Moves a player into a new state: the old state's inventory is parked if it
     * is one we restore later, the player is fully reset, and the new state's
     * baseline is applied. Callers fill in the details (kit items, hub items)
     * afterwards.
     */
    public void set(Player player, PlayerState next) {
        PlayerState previous = state(player);
        if (shouldPark(previous)) {
            park(player, previous);
        }

        states.put(player.getUniqueId(), next);
        reset(player);

        switch (next) {
            case LOBBY, QUEUE, EDITING_KIT -> {
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true);
                player.setFlying(false);
            }
            case MATCH_STARTING, MATCH_FIGHTING, FFA -> player.setGameMode(GameMode.SURVIVAL);
            case MATCH_DEAD, SPECTATING -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.setAllowFlight(true);
            }
            case SURVIVAL -> {
                player.setGameMode(GameMode.SURVIVAL);
                restore(player, PlayerState.SURVIVAL);
            }
            case STAFF -> {
                player.setGameMode(GameMode.CREATIVE);
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        }
    }

    /** Wipes everything a previous mode could have left behind. */
    public void reset(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D);
        }
        player.setHealth(Math.min(20.0D, maxHealth == null ? 20.0D : maxHealth.getValue()));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);

        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setFallDistance(0.0F);
        player.setVelocity(player.getVelocity().zero());
        player.setGliding(false);
        player.setSprinting(false);
        player.setLevel(0);
        player.setExp(0.0F);
        // Without this a player teleported out of a fight keeps their damage
        // immunity into the next one.
        player.setNoDamageTicks(0);
        player.setArrowsInBody(0);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.closeInventory();
    }

    private boolean shouldPark(PlayerState state) {
        // Only survival has an inventory worth keeping; everything else is
        // handed out by the server and can be thrown away.
        return state == PlayerState.SURVIVAL;
    }

    private void park(Player player, PlayerState state) {
        parked.computeIfAbsent(player.getUniqueId(), uuid -> new EnumMap<>(PlayerState.class))
                .put(state, StoredInventory.capture(player));
    }

    private void restore(Player player, PlayerState state) {
        Map<PlayerState, StoredInventory> byState = parked.get(player.getUniqueId());
        if (byState == null) {
            return;
        }
        StoredInventory stored = byState.remove(state);
        if (stored != null) {
            stored.applyTo(player);
        }
    }

    /** Saved contents for a state the player will come back to. */
    public StoredInventory parkedInventory(UUID uuid, PlayerState state) {
        Map<PlayerState, StoredInventory> byState = parked.get(uuid);
        return byState == null ? null : byState.get(state);
    }

    public void putParked(UUID uuid, PlayerState state, StoredInventory inventory) {
        parked.computeIfAbsent(uuid, key -> new EnumMap<>(PlayerState.class)).put(state, inventory);
    }

    public void forget(UUID uuid) {
        states.remove(uuid);
        parked.remove(uuid);
    }

    public Map<UUID, PlayerState> snapshot() {
        return new HashMap<>(states);
    }

    /** A player's inventory, armour and vitals, detached from the player. */
    public record StoredInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offHand,
                                  double health, int food, float saturation, int level, float exp) {

        public static StoredInventory capture(Player player) {
            return new StoredInventory(
                    player.getInventory().getContents().clone(),
                    player.getInventory().getArmorContents().clone(),
                    player.getInventory().getItemInOffHand().clone(),
                    player.getHealth(),
                    player.getFoodLevel(),
                    player.getSaturation(),
                    player.getLevel(),
                    player.getExp());
        }

        public void applyTo(Player player) {
            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offHand);
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double cap = maxHealth == null ? 20.0D : maxHealth.getValue();
            player.setHealth(Math.max(1.0D, Math.min(cap, health)));
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.setLevel(level);
            player.setExp(exp);
        }
    }
}
