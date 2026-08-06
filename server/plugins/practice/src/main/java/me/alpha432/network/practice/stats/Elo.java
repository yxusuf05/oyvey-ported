package me.alpha432.network.practice.stats;

/**
 * Standard Elo rating. The winner takes what the loser gives up, so the sum of all ratings in a
 * kit stays constant apart from the floor.
 */
public final class Elo {

    /** Ratings never drop below this, so a losing streak cannot bury a player forever. */
    public static final int FLOOR = 0;

    private Elo() {
    }

    /** Probability that {@code rating} beats {@code opponent}, between 0 and 1. */
    public static double expected(int rating, int opponent) {
        return 1.0D / (1.0D + Math.pow(10.0D, (opponent - rating) / 400.0D));
    }

    /**
     * How many points the winner gains. Always at least 1, so a heavy favourite still moves.
     *
     * @param kFactor how strongly one match counts, 32 is the usual value
     */
    public static int gain(int winnerRating, int loserRating, int kFactor) {
        int delta = (int) Math.round(kFactor * (1.0D - expected(winnerRating, loserRating)));
        return Math.max(1, delta);
    }

    /**
     * How many points the loser drops. Mirrors {@link #gain} but never pushes the rating below
     * {@link #FLOOR}, so the actual loss can be smaller near the floor.
     */
    public static int loss(int winnerRating, int loserRating, int kFactor) {
        int delta = gain(winnerRating, loserRating, kFactor);
        return Math.min(delta, Math.max(0, loserRating - FLOOR));
    }

    /** Tier name for a rating, used as the automatic practice title. */
    public static String tier(int rating) {
        if (rating >= 2000) {
            return "champion";
        }
        if (rating >= 1600) {
            return "diamant";
        }
        if (rating >= 1300) {
            return "gold";
        }
        if (rating >= 1100) {
            return "silber";
        }
        return "bronze";
    }
}
