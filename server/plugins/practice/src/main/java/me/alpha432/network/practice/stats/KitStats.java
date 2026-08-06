package me.alpha432.network.practice.stats;

import java.util.UUID;

/** A player's record in one kit. */
public final class KitStats {

    private final UUID uuid;
    private final String kit;
    private int elo;
    private int wins;
    private int losses;
    private int streak;
    private int bestStreak;

    public KitStats(UUID uuid, String kit, int elo, int wins, int losses, int streak, int bestStreak) {
        this.uuid = uuid;
        this.kit = kit;
        this.elo = elo;
        this.wins = wins;
        this.losses = losses;
        this.streak = streak;
        this.bestStreak = bestStreak;
    }

    public static KitStats fresh(UUID uuid, String kit, int startingElo) {
        return new KitStats(uuid, kit, startingElo, 0, 0, 0, 0);
    }

    public void won(int eloGain) {
        wins++;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        elo = Math.max(Elo.FLOOR, elo + eloGain);
    }

    public void lost(int eloLoss) {
        losses++;
        streak = 0;
        elo = Math.max(Elo.FLOOR, elo - eloLoss);
    }

    public double winRate() {
        int played = wins + losses;
        return played == 0 ? 0.0D : (double) wins / played;
    }

    public UUID uuid() {
        return uuid;
    }

    public String kit() {
        return kit;
    }

    public int elo() {
        return elo;
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int streak() {
        return streak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public String tier() {
        return Elo.tier(elo);
    }
}
