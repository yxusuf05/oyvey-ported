package me.alpha432.network.practice.bot;

import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A training opponent driven entirely by this class.
 *
 * <p>The body is a zombie with its own AI switched off; every step, jump and swing comes from
 * {@link #tick()}. That keeps the behaviour predictable and version independent — no server
 * internals are touched. Actions run through a small delay queue so a simulated ping shows up
 * as a real reaction delay instead of a cosmetic number.
 */
public final class PracticeBot {

    private final Player owner;
    private final BotSettings settings;
    private final BotStats stats = new BotStats();
    private final Deque<DelayedAction> pending = new ArrayDeque<>();

    private Zombie entity;
    private int attackCooldown;
    private int strafeDirection = 1;
    private int strafeTimer;

    public PracticeBot(Player owner, BotSettings settings) {
        this.owner = owner;
        this.settings = settings;
    }

    /** Spawns the body next to the owner and dresses it in the kit. */
    public void spawn(Location location, PracticeKit kit, String displayName) {
        entity = location.getWorld().spawn(location, Zombie.class, zombie -> {
            zombie.setAI(false);
            zombie.setSilent(true);
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setPersistent(true);
            zombie.customName(net.kyori.adventure.text.Component.text(displayName));
            zombie.setCustomNameVisible(true);
        });
        applyHealth();
        equip(kit);
    }

    private void applyHealth() {
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(settings.health());
        }
        entity.setHealth(settings.health());
    }

    /** Gives the bot the same gear the player fights with, minus anything it cannot use. */
    public void equip(PracticeKit kit) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.clear();
        if (kit != null) {
            equipment.setHelmet(clone(kit.helmet()));
            equipment.setChestplate(clone(kit.chestplate()));
            equipment.setLeggings(clone(kit.leggings()));
            equipment.setBoots(clone(kit.boots()));
            ItemStack weapon = firstWeapon(kit);
            if (weapon != null) {
                equipment.setItemInMainHand(weapon);
            }
        }
        // The bot must never drop its gear when it dies.
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
    }

    private static ItemStack firstWeapon(PracticeKit kit) {
        for (ItemStack item : kit.contents()) {
            if (item != null && item.getType().name().endsWith("_SWORD")) {
                return item.clone();
            }
        }
        for (ItemStack item : kit.contents()) {
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        return null;
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }

    /** One server tick of behaviour. Returns false when the bot should be removed. */
    public boolean tick() {
        if (entity == null || entity.isDead() || !owner.isOnline()) {
            return false;
        }
        if (!entity.getWorld().equals(owner.getWorld())) {
            return false;
        }
        runPending();

        double distance = entity.getLocation().distance(owner.getLocation());
        if (distance > 40) {
            // Lost the player; step back next to them instead of wandering off.
            entity.teleport(owner.getLocation().add(2, 0, 0));
            return true;
        }

        face(owner.getLocation());
        move(distance);

        if (attackCooldown > 0) {
            attackCooldown--;
        } else if (settings.behavior().attacks() && distance <= 3.4D) {
            schedule(this::swing);
            attackCooldown = settings.attackIntervalTicks();
        }
        return true;
    }

    private void move(double distance) {
        Vector toOwner = owner.getLocation().toVector()
                .subtract(entity.getLocation().toVector()).setY(0);
        if (toOwner.lengthSquared() < 0.0001D) {
            return;
        }
        toOwner.normalize();
        Vector sideways = new Vector(-toOwner.getZ(), 0, toOwner.getX()).multiply(strafeDirection);

        if (--strafeTimer <= 0 || ThreadLocalRandom.current().nextDouble() < settings.strafeChance()) {
            strafeDirection = -strafeDirection;
            strafeTimer = 10 + ThreadLocalRandom.current().nextInt(20);
        }

        Vector motion = switch (settings.behavior()) {
            case AGGRESSIVE, BOXING -> toOwner.clone().multiply(1.0D).add(sideways.multiply(0.6D));
            case DEFENSIVE -> distance < 4.0D
                    ? toOwner.clone().multiply(-1.0D).add(sideways.multiply(0.8D))
                    : toOwner.clone().multiply(0.4D).add(sideways.multiply(0.9D));
            case STRAFE -> sideways.multiply(1.0D)
                    .add(toOwner.clone().multiply(distance > 3.5D ? 0.5D : -0.3D));
            case SUMO -> toOwner.clone().multiply(1.2D);
        };

        motion.normalize().multiply(settings.speed());
        Vector velocity = entity.getVelocity();
        entity.setVelocity(new Vector(motion.getX(), velocity.getY(), motion.getZ()));

        // A jump now and then makes the bot harder to combo, like a real opponent.
        if (entity.isOnGround() && ThreadLocalRandom.current().nextDouble() < settings.difficulty() * 0.01D) {
            entity.setVelocity(entity.getVelocity().setY(0.42D));
        }
    }

    private void face(Location target) {
        Location location = entity.getLocation();
        double dx = target.getX() - location.getX();
        double dz = target.getZ() - location.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        entity.setRotation(yaw, 0f);
    }

    private void swing() {
        if (entity == null || entity.isDead() || !owner.isOnline()) {
            return;
        }
        entity.swingMainHand();
        if (ThreadLocalRandom.current().nextDouble() > settings.hitChance()) {
            return;
        }
        if (entity.getLocation().distance(owner.getLocation()) > 3.6D) {
            return;
        }
        if (settings.behavior() == BotBehavior.SUMO) {
            // Sumo pushes instead of hurting.
            Vector push = owner.getLocation().toVector()
                    .subtract(entity.getLocation().toVector()).setY(0.35D).normalize().multiply(0.8D);
            owner.setVelocity(owner.getVelocity().add(push));
            return;
        }
        entity.attack(owner);
        stats.taken();
    }

    /** Runs an action after the configured ping, or right away when there is none. */
    private void schedule(Runnable action) {
        int delay = settings.pingTicks();
        if (delay <= 0) {
            action.run();
            return;
        }
        pending.add(new DelayedAction(action, delay));
    }

    private void runPending() {
        pending.removeIf(DelayedAction::tickAndRun);
    }

    /** Applies the configured knockback multiplier after the player landed a hit. */
    public void onHitByOwner() {
        stats.hit();
        if (entity == null || settings.knockback() == 1.0D) {
            return;
        }
        Vector velocity = entity.getVelocity();
        entity.setVelocity(new Vector(
                velocity.getX() * settings.knockback(),
                velocity.getY() * settings.knockback(),
                velocity.getZ() * settings.knockback()));
    }

    /** Puts the bot back on full health next to the owner. */
    public void reset(PracticeKit kit) {
        if (entity == null) {
            return;
        }
        applyHealth();
        equip(kit);
        entity.teleport(owner.getLocation().add(3, 0, 0));
        entity.setFireTicks(0);
        pending.clear();
        attackCooldown = 0;
    }

    public void remove() {
        pending.clear();
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        entity = null;
    }

    public boolean isEntity(org.bukkit.entity.Entity other) {
        return entity != null && entity.equals(other);
    }

    public Zombie entity() {
        return entity;
    }

    public Player owner() {
        return owner;
    }

    public BotSettings settings() {
        return settings;
    }

    public BotStats stats() {
        return stats;
    }

    public double health() {
        return entity == null ? 0 : entity.getHealth();
    }

    public EntityType type() {
        return EntityType.ZOMBIE;
    }

    /** A queued action plus the ticks left before it fires. */
    private static final class DelayedAction {

        private final Runnable action;
        private int remaining;

        private DelayedAction(Runnable action, int remaining) {
            this.action = action;
            this.remaining = remaining;
        }

        /** @return true once it ran, so the caller can drop it from the queue. */
        private boolean tickAndRun() {
            if (--remaining > 0) {
                return false;
            }
            action.run();
            return true;
        }
    }
}
