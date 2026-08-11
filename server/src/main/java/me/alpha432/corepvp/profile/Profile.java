package me.alpha432.corepvp.profile;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything persisted about a player. Lives in memory while they are online
 * and is written back when it changes.
 */
public final class Profile {

    private final UUID uuid;
    private String name;
    private String rankId = "default";
    private long firstSeen;
    private long lastSeen;
    private int globalKills;
    private int globalDeaths;

    private final ProfileSettings settings = new ProfileSettings();
    private final Map<String, KitStats> kitStats = new ConcurrentHashMap<>();

    private volatile boolean dirty;
    /** True until the row has been read from storage at least once. */
    private volatile boolean fresh = true;

    public Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        long now = System.currentTimeMillis();
        this.firstSeen = now;
        this.lastSeen = now;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (!name.equals(this.name)) {
            this.name = name;
            markDirty();
        }
    }

    public String rankId() {
        return rankId;
    }

    public void rankId(String rankId) {
        this.rankId = rankId;
        markDirty();
    }

    public long firstSeen() {
        return firstSeen;
    }

    public void firstSeen(long firstSeen) {
        this.firstSeen = firstSeen;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        markDirty();
    }

    public int globalKills() {
        return globalKills;
    }

    public void globalKills(int globalKills) {
        this.globalKills = globalKills;
        markDirty();
    }

    public int globalDeaths() {
        return globalDeaths;
    }

    public void globalDeaths(int globalDeaths) {
        this.globalDeaths = globalDeaths;
        markDirty();
    }

    public ProfileSettings settings() {
        return settings;
    }

    /** Stats for a kit, created on first access. */
    public KitStats kit(String kitId) {
        return kitStats.computeIfAbsent(kitId.toLowerCase(java.util.Locale.ROOT), KitStats::new);
    }

    public Collection<KitStats> allKitStats() {
        return kitStats.values();
    }

    public boolean fresh() {
        return fresh;
    }

    public void fresh(boolean fresh) {
        this.fresh = fresh;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
