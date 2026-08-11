package me.alpha432.corepvp.match;

/** Per-player numbers for one match, shown on the post-match screen. */
public final class MatchPlayerStats {

    private int hits;
    private int currentCombo;
    private int longestCombo;
    private int potionsThrown;
    private int potionsHit;
    private int arrowsShot;
    private int arrowsHit;
    private int crystalsPlaced;
    private int totemsPopped;
    private double damageDealt;

    public void hit() {
        hits++;
        currentCombo++;
        longestCombo = Math.max(longestCombo, currentCombo);
    }

    /** Taking a hit ends whatever combo the player was on. */
    public void tookHit() {
        currentCombo = 0;
    }

    public void potionThrown() {
        potionsThrown++;
    }

    public void potionHit() {
        potionsHit++;
    }

    public void arrowShot() {
        arrowsShot++;
    }

    public void arrowHit() {
        arrowsHit++;
    }

    public void crystalPlaced() {
        crystalsPlaced++;
    }

    public void totemPopped() {
        totemsPopped++;
    }

    public void damage(double amount) {
        damageDealt += amount;
    }

    public int hits() {
        return hits;
    }

    public int longestCombo() {
        return longestCombo;
    }

    public int potionsThrown() {
        return potionsThrown;
    }

    public int potionsHit() {
        return potionsHit;
    }

    public int arrowsShot() {
        return arrowsShot;
    }

    public int arrowsHit() {
        return arrowsHit;
    }

    public int crystalsPlaced() {
        return crystalsPlaced;
    }

    public int totemsPopped() {
        return totemsPopped;
    }

    public double damageDealt() {
        return damageDealt;
    }

    /** Percentage of thrown potions that actually landed on the thrower. */
    public int potionAccuracy() {
        return potionsThrown == 0 ? 0 : (int) Math.round(100.0D * potionsHit / potionsThrown);
    }

    public int arrowAccuracy() {
        return arrowsShot == 0 ? 0 : (int) Math.round(100.0D * arrowsHit / arrowsShot);
    }
}
