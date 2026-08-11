package me.alpha432.corepvp.kit;

import me.alpha432.corepvp.combat.CombatMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A loadout plus the rules it is played under. */
public final class Kit {

    /** Player inventories are 36 slots: hotbar plus three rows. */
    public static final int CONTENT_SIZE = 36;
    public static final int ARMOR_SIZE = 4;

    private final String id;
    private String displayName;
    private ItemStack icon;
    private int menuSlot;
    private boolean enabled = true;

    private ItemStack[] contents = new ItemStack[CONTENT_SIZE];
    private ItemStack[] armor = new ItemStack[ARMOR_SIZE];
    private ItemStack offHand;
    private List<PotionEffect> effects = new ArrayList<>();

    private KitFlags flags = KitFlags.defaults();
    private CombatMode combatMode = CombatMode.LEGACY_1_8;
    /** Arena templates this kit can be played on, e.g. {@code sumo}, {@code crystal}. */
    private Set<String> arenaTypes = new LinkedHashSet<>(List.of("flat"));

    public Kit(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = id;
        this.icon = new ItemStack(Material.IRON_SWORD);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Kit displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public ItemStack icon() {
        return icon.clone();
    }

    public Kit icon(ItemStack icon) {
        this.icon = icon.clone();
        return this;
    }

    public int menuSlot() {
        return menuSlot;
    }

    public Kit menuSlot(int menuSlot) {
        this.menuSlot = menuSlot;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public Kit enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /** A defensive copy: handing out the live array would let callers mutate the kit. */
    public ItemStack[] contents() {
        return copy(contents, CONTENT_SIZE);
    }

    public Kit contents(ItemStack[] contents) {
        this.contents = copy(contents, CONTENT_SIZE);
        return this;
    }

    public ItemStack[] armor() {
        return copy(armor, ARMOR_SIZE);
    }

    public Kit armor(ItemStack[] armor) {
        this.armor = copy(armor, ARMOR_SIZE);
        return this;
    }

    public ItemStack offHand() {
        return offHand == null ? null : offHand.clone();
    }

    public Kit offHand(ItemStack offHand) {
        this.offHand = offHand == null ? null : offHand.clone();
        return this;
    }

    public List<PotionEffect> effects() {
        return List.copyOf(effects);
    }

    public Kit effects(List<PotionEffect> effects) {
        this.effects = new ArrayList<>(effects);
        return this;
    }

    public KitFlags flags() {
        return flags;
    }

    public Kit flags(KitFlags flags) {
        this.flags = flags;
        return this;
    }

    public CombatMode combatMode() {
        return combatMode;
    }

    public Kit combatMode(CombatMode combatMode) {
        this.combatMode = combatMode;
        return this;
    }

    public Set<String> arenaTypes() {
        return Set.copyOf(arenaTypes);
    }

    public Kit arenaTypes(Set<String> arenaTypes) {
        this.arenaTypes = new LinkedHashSet<>(arenaTypes);
        return this;
    }

    public Kit arenaTypes(String... arenaTypes) {
        return arenaTypes(new LinkedHashSet<>(List.of(arenaTypes)));
    }

    private static ItemStack[] copy(ItemStack[] source, int size) {
        ItemStack[] target = new ItemStack[size];
        if (source != null) {
            for (int i = 0; i < Math.min(size, source.length); i++) {
                target[i] = source[i] == null ? null : source[i].clone();
            }
        }
        return target;
    }
}
