package me.alpha432.corepvp.elo;

/** Standard Elo, with the exchange forced to be zero-sum. */
public final class Elo {

    public static final int DEFAULT = 1000;

    private Elo() {
    }

    /** Probability that {@code a} beats {@code b}. */
    public static double expected(int a, int b) {
        return 1.0D / (1.0D + Math.pow(10.0D, (b - a) / 400.0D));
    }

    /**
     * Points the winner gains. The loser always loses exactly this many, so
     * rating is neither created nor destroyed by a match - without that,
     * average rating drifts upward over time.
     */
    public static int gain(int winnerElo, int loserElo, int kFactor) {
        int delta = (int) Math.round(kFactor * (1.0D - expected(winnerElo, loserElo)));
        // An upset must always be worth something, and a hopeless win must
        // still cost the loser something.
        return Math.max(1, delta);
    }

    /** Clamps to a floor so a losing streak cannot push a rating below zero. */
    public static int applyLoss(int loserElo, int loss) {
        return Math.max(0, loserElo - loss);
    }

    /**
     * K-factor: new players move fast so they reach their real rating quickly,
     * established players move slowly so one bad night does not undo a season.
     */
    public static int kFactor(int matchesPlayed, int elo, int base) {
        if (matchesPlayed < 10) {
            return base * 2;
        }
        if (elo >= 2000) {
            return Math.max(1, base / 2);
        }
        return base;
    }
}
