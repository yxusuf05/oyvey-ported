package me.alpha432.corepvp.state;

/**
 * Where a player currently is. Every listener checks this first, and
 * {@link PlayerStateService} is the only thing allowed to change it.
 */
public enum PlayerState {

    /** In the hub, with the hub hotbar. */
    LOBBY,
    /** In the hub but waiting for a match. */
    QUEUE,
    /** Match countdown; frozen in place. */
    MATCH_STARTING,
    /** Match running. */
    MATCH_FIGHTING,
    /** Dead in a running match, waiting for it to finish. */
    MATCH_DEAD,
    /** Watching someone else's match. */
    SPECTATING,
    /** In a free-for-all arena. */
    FFA,
    /** Rearranging a kit in the editor. */
    EDITING_KIT,
    /** In the survival world. */
    SURVIVAL,
    /** Staff mode: vanished, moderation tools in hand. */
    STAFF;

    public boolean inMatch() {
        return this == MATCH_STARTING || this == MATCH_FIGHTING || this == MATCH_DEAD;
    }

    /** True while the player can take and deal damage. */
    public boolean fighting() {
        return this == MATCH_FIGHTING || this == FFA || this == SURVIVAL;
    }

    public boolean inLobby() {
        return this == LOBBY || this == QUEUE;
    }
}
