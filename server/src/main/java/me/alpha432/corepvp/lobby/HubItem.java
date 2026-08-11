package me.alpha432.corepvp.lobby;

import org.bukkit.Material;

import java.util.Locale;

/** The fixed hotbar of the hub. */
public enum HubItem {

    UNRANKED_QUEUE(0, Material.IRON_SWORD),
    RANKED_QUEUE(1, Material.DIAMOND_SWORD),
    FFA(2, Material.END_CRYSTAL),
    SURVIVAL(3, Material.GRASS_BLOCK),
    KIT_EDITOR(4, Material.BOOK),
    LEADERBOARD(6, Material.PAPER),
    PARTY(7, Material.NAME_TAG),
    SETTINGS(8, Material.COMPARATOR);

    private final int slot;
    private final Material material;

    HubItem(int slot, Material material) {
        this.slot = slot;
        this.material = material;
    }

    public int slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String nameKey() {
        return "lobby.items." + key() + ".name";
    }

    public String loreKey() {
        return "lobby.items." + key() + ".lore";
    }
}
