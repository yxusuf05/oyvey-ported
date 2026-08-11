package me.alpha432.corepvp.profile;

/** Per-kit record for one player. Mutable on purpose; profiles are cached. */
public final class KitStats {

    public static final int DEFAULT_ELO = 1000;

    private final String kit;
    private int elo = DEFAULT_ELO;
    private int wins;
    private int losses;
    private int kills;
    private int deaths;

    public KitStats(String kit) {
        this.kit = kit;
    }

    public String kit() {
        return kit;
    }

    public int elo() {
        return elo;
    }

    public void elo(int elo) {
        this.elo = Math.max(0, elo);
    }

    public int wins() {
        return wins;
    }

    public void wins(int wins) {
        this.wins = wins;
    }

    public int losses() {
        return losses;
    }

    public void losses(int losses) {
        this.losses = losses;
    }

    public int kills() {
        return kills;
    }

    public void kills(int kills) {
        this.kills = kills;
    }

    public int deaths() {
        return deaths;
    }

    public void deaths(int deaths) {
        this.deaths = deaths;
    }

    public int played() {
        return wins + losses;
    }

    public double winRate() {
        int played = played();
        return played == 0 ? 0.0D : (double) wins / played;
    }
}
