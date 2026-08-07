package me.alpha432.network.practice.bot;

/**
 * Everything {@code /bot} can change. Kept mutable so a running bot picks up a change right
 * away instead of having to be respawned.
 */
public final class BotSettings {

    /** 1 is a slow beginner, 5 reacts almost instantly and rarely misses. */
    public static final int MIN_DIFFICULTY = 1;
    public static final int MAX_DIFFICULTY = 5;

    private int difficulty = 3;
    private String kitId;
    private double health = 20.0D;
    private double knockback = 1.0D;
    private BotBehavior behavior = BotBehavior.AGGRESSIVE;
    private int pingMillis;

    public BotSettings(String kitId) {
        this.kitId = kitId;
    }

    public int difficulty() {
        return difficulty;
    }

    public void difficulty(int difficulty) {
        this.difficulty = Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, difficulty));
    }

    public String kitId() {
        return kitId;
    }

    public void kitId(String kitId) {
        this.kitId = kitId;
    }

    public double health() {
        return health;
    }

    public void health(double health) {
        this.health = Math.max(1.0D, Math.min(200.0D, health));
    }

    public double knockback() {
        return knockback;
    }

    public void knockback(double knockback) {
        this.knockback = Math.max(0.0D, Math.min(5.0D, knockback));
    }

    public BotBehavior behavior() {
        return behavior;
    }

    public void behavior(BotBehavior behavior) {
        this.behavior = behavior;
    }

    public int pingMillis() {
        return pingMillis;
    }

    public void pingMillis(int pingMillis) {
        this.pingMillis = Math.max(0, Math.min(1000, pingMillis));
    }

    /** Simulated latency expressed in ticks, which is the resolution the bot acts on. */
    public int pingTicks() {
        return pingMillis / 50;
    }

    /** Ticks between two attacks. Higher difficulty swings faster. */
    public int attackIntervalTicks() {
        return 22 - difficulty * 2;
    }

    /** How often the bot lands a swing at all, from 55 % up to 95 %. */
    public double hitChance() {
        return 0.45D + difficulty * 0.10D;
    }

    /** Movement speed factor; a harder bot keeps up with a strafing player. */
    public double speed() {
        return 0.16D + difficulty * 0.03D;
    }

    /** How erratic the strafing is. Lower difficulty means more predictable movement. */
    public double strafeChance() {
        return 0.05D + difficulty * 0.03D;
    }
}
