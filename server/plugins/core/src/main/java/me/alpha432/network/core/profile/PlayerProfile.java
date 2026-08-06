package me.alpha432.network.core.profile;

import java.util.UUID;

/** Everything the network stores about a player that is not feature specific. */
public final class PlayerProfile {

    private final UUID uuid;
    private volatile String name;
    private volatile long firstJoin;
    private volatile long lastSeen;
    private volatile long playTime;
    private volatile double balance;
    private volatile String rankId;
    private volatile boolean dirty;

    public PlayerProfile(UUID uuid, String name, long firstJoin, long lastSeen,
                         double balance, String rankId, long playTime) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
        this.balance = balance;
        this.rankId = rankId;
        this.playTime = playTime;
    }

    public static PlayerProfile fresh(UUID uuid, String name, double startingBalance, String defaultRank) {
        long now = System.currentTimeMillis();
        PlayerProfile profile = new PlayerProfile(uuid, name, now, now, startingBalance, defaultRank, 0L);
        profile.dirty = true;
        return profile;
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
            this.dirty = true;
        }
    }

    public long firstJoin() {
        return firstJoin;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        this.dirty = true;
    }

    public long playTime() {
        return playTime;
    }

    public void addPlayTime(long millis) {
        this.playTime += millis;
        this.dirty = true;
    }

    public double balance() {
        return balance;
    }

    public void balance(double balance) {
        this.balance = Math.max(0.0D, balance);
        this.dirty = true;
    }

    public String rankId() {
        return rankId;
    }

    public void rankId(String rankId) {
        this.rankId = rankId;
        this.dirty = true;
    }

    /** True when the profile changed since it was last written to the database. */
    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        this.dirty = false;
    }

    /** Whether this is the player's very first session. */
    public boolean isNew() {
        return firstJoin == lastSeen;
    }
}
