package me.alpha432.corepvp.match;

public enum MatchState {

    /** Countdown; players are frozen and cannot damage each other. */
    STARTING,
    /** Live. */
    FIGHTING,
    /** Decided; the result is on screen and players are about to be sent back. */
    ENDING,
    /** Finished and removed from the manager. */
    ENDED
}
