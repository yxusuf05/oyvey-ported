package me.alpha432.network.practice.bot;

/** Live training numbers of one bot session. */
public final class BotStats {

    private final long startedAt = System.currentTimeMillis();
    private int hits;
    private int swings;
    private int combo;
    private int bestCombo;
    private int hitsTaken;
    private int botCombo;

    /** The player landed a hit on the bot. */
    public void hit() {
        hits++;
        combo++;
        botCombo = 0;
        bestCombo = Math.max(bestCombo, combo);
    }

    /** The player swung, whether or not it connected. */
    public void swing() {
        swings++;
    }

    /** The bot landed a hit, which breaks the player's combo. */
    public void taken() {
        hitsTaken++;
        botCombo++;
        combo = 0;
    }

    public int hits() {
        return hits;
    }

    public int swings() {
        return swings;
    }

    public int combo() {
        return combo;
    }

    public int bestCombo() {
        return bestCombo;
    }

    public int hitsTaken() {
        return hitsTaken;
    }

    public int botCombo() {
        return botCombo;
    }

    public long durationSeconds() {
        return Math.max(1L, (System.currentTimeMillis() - startedAt) / 1000L);
    }

    /** Share of swings that connected, as a percentage. */
    public double accuracy() {
        return swings == 0 ? 0.0D : (double) hits / swings * 100.0D;
    }

    public double hitsPerSecond() {
        return (double) hits / durationSeconds();
    }

    public String durationText() {
        long seconds = durationSeconds();
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
