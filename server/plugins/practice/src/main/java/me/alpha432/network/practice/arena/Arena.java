package me.alpha432.network.practice.arena;

import me.alpha432.network.core.region.Region;
import org.bukkit.Location;

import java.util.List;

/** One fighting ground. Arenas are reserved for a match and released when it ends. */
public final class Arena {

    private final String name;
    private final String displayName;
    private Location firstSpawn;
    private Location secondSpawn;
    private Region bounds;
    private List<String> kits;
    private boolean enabled;
    private boolean occupied;

    public Arena(String name, String displayName, Location firstSpawn, Location secondSpawn,
                 Region bounds, List<String> kits, boolean enabled) {
        this.name = name;
        this.displayName = displayName;
        this.firstSpawn = firstSpawn;
        this.secondSpawn = secondSpawn;
        this.bounds = bounds;
        this.kits = kits;
        this.enabled = enabled;
    }

    /** Usable when it is set up, switched on, free and it supports the kit. */
    public boolean isAvailableFor(String kitId) {
        return enabled && !occupied && isComplete() && supports(kitId);
    }

    public boolean isComplete() {
        return firstSpawn != null && secondSpawn != null;
    }

    /** An empty kit list means the arena works for every kit. */
    public boolean supports(String kitId) {
        return kits.isEmpty() || kits.contains(kitId.toLowerCase());
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }

    public Location firstSpawn() {
        return firstSpawn == null ? null : firstSpawn.clone();
    }

    public void firstSpawn(Location firstSpawn) {
        this.firstSpawn = firstSpawn == null ? null : firstSpawn.clone();
    }

    public Location secondSpawn() {
        return secondSpawn == null ? null : secondSpawn.clone();
    }

    public void secondSpawn(Location secondSpawn) {
        this.secondSpawn = secondSpawn == null ? null : secondSpawn.clone();
    }

    public Region bounds() {
        return bounds;
    }

    public void bounds(Region bounds) {
        this.bounds = bounds;
    }

    public List<String> kits() {
        return kits;
    }

    public void kits(List<String> kits) {
        this.kits = kits;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void occupied(boolean occupied) {
        this.occupied = occupied;
    }

    /** True when the location is inside the arena, or when no bounds were defined. */
    public boolean contains(Location location) {
        return bounds == null || bounds.contains(location);
    }
}
