package me.alpha432.corepvp.match;

/** Why a match exists. Only affects messaging, ELO and arena choice. */
public enum MatchType {

    UNRANKED(false),
    RANKED(true),
    DUEL(false),
    PARTY_SPLIT(false),
    PARTY_FFA(false);

    private final boolean ranked;

    MatchType(boolean ranked) {
        this.ranked = ranked;
    }

    public boolean ranked() {
        return ranked;
    }
}
