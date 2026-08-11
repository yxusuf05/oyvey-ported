package me.alpha432.corepvp.arena;

public enum ArenaState {

    /** Free to be handed to a match. */
    AVAILABLE,
    /** A match is running in it. */
    IN_USE,
    /** The match ended and blocks are being put back. */
    RESETTING,
    /** Taken out of rotation by an admin. */
    DISABLED
}
