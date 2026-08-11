package me.alpha432.corepvp.arena;

import me.alpha432.corepvp.arena.rollback.RollbackJournal;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.util.Locations;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One playable arena, and the record of what a match has changed inside it. */
public final class Arena {

    private final String id;
    private String template;
    private final List<Location> spawns = new ArrayList<>();
    private Cuboid bounds;
    private Cuboid buildBounds;
    private int deathY = Integer.MIN_VALUE;
    private Set<String> allowedKits = new LinkedHashSet<>();
    private ArenaState state = ArenaState.AVAILABLE;

    private final RollbackJournal<BlockData> journal = new RollbackJournal<>();

    public Arena(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
    }

    public String id() {
        return id;
    }

    public String template() {
        return template;
    }

    public Arena template(String template) {
        this.template = template == null ? null : template.toLowerCase(Locale.ROOT);
        return this;
    }

    public List<Location> spawns() {
        return List.copyOf(spawns);
    }

    public Location spawn(int index) {
        return spawns.isEmpty() ? null : spawns.get(index % spawns.size()).clone();
    }

    public Arena spawns(List<Location> spawns) {
        this.spawns.clear();
        this.spawns.addAll(spawns);
        return this;
    }

    public Arena addSpawn(Location location) {
        spawns.add(location);
        return this;
    }

    public Cuboid bounds() {
        return bounds;
    }

    public Arena bounds(Cuboid bounds) {
        this.bounds = bounds;
        return this;
    }

    /** Where building is allowed; falls back to the arena bounds. */
    public Cuboid buildBounds() {
        return buildBounds == null ? bounds : buildBounds;
    }

    /** The configured build bounds without the fallback, for saving. */
    public Cuboid rawBuildBounds() {
        return buildBounds;
    }

    public Arena buildBounds(Cuboid buildBounds) {
        this.buildBounds = buildBounds;
        return this;
    }

    /** Below this Y a player counts as dead. Used by sumo and void arenas. */
    public int deathY() {
        return deathY == Integer.MIN_VALUE
                ? (bounds == null ? Integer.MIN_VALUE : bounds.minY() - 5)
                : deathY;
    }

    public Arena deathY(int deathY) {
        this.deathY = deathY;
        return this;
    }

    public Set<String> allowedKits() {
        return Set.copyOf(allowedKits);
    }

    public Arena allowedKits(Set<String> allowedKits) {
        this.allowedKits = new LinkedHashSet<>(allowedKits);
        return this;
    }

    /** An empty allow-list means "any kit whose arena types include our template". */
    public boolean supports(Kit kit) {
        if (!allowedKits.isEmpty()) {
            return allowedKits.contains(kit.id());
        }
        return template != null && kit.arenaTypes().contains(template);
    }

    public ArenaState state() {
        return state;
    }

    public Arena state(ArenaState state) {
        this.state = state;
        return this;
    }

    public boolean available() {
        return state == ArenaState.AVAILABLE && bounds != null && spawns.size() >= 2;
    }

    public RollbackJournal<BlockData> journal() {
        return journal;
    }

    public String serializeSpawns() {
        List<String> parts = new ArrayList<>();
        for (Location spawn : spawns) {
            parts.add(Locations.serialize(spawn));
        }
        return String.join(";", parts);
    }
}
