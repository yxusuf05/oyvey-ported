package me.alpha432.network.staff.punishment;

/** The kinds of punishment the staff can hand out. */
public enum PunishmentType {

    /** Removes the player from the server once. */
    KICK(false),
    /** Blocks the player from joining. */
    BAN(true),
    /** Blocks the player from chatting. */
    MUTE(true),
    /** A recorded warning without further effect. */
    WARN(false);

    private final boolean lasting;

    PunishmentType(boolean lasting) {
        this.lasting = lasting;
    }

    /** True when the punishment stays active until it expires or is lifted. */
    public boolean isLasting() {
        return lasting;
    }
}
