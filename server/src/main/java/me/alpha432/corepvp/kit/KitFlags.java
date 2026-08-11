package me.alpha432.corepvp.kit;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The rules that make one kit play differently from another.
 *
 * @param build          blocks may be placed and broken (BuildUHC, crystal)
 * @param hunger         the food bar drains
 * @param naturalRegen   health regenerates on its own
 * @param sumo           no damage; you lose by leaving the platform
 * @param boxing         no death; first to the hit target wins
 * @param showHealth     health is shown under the name tag
 * @param rankedEnabled  the kit can be queued ranked
 * @param editable       players may save their own hotbar layouts
 * @param infiniteItems  consumables are refilled while fighting (FFA, crystal)
 */
public record KitFlags(boolean build, boolean hunger, boolean naturalRegen, boolean sumo,
                       boolean boxing, boolean showHealth, boolean rankedEnabled,
                       boolean editable, boolean infiniteItems) {

    public static KitFlags defaults() {
        return new KitFlags(false, false, false, false, false, true, true, true, false);
    }

    public KitFlags withBuild(boolean value) {
        return new KitFlags(value, hunger, naturalRegen, sumo, boxing, showHealth, rankedEnabled, editable, infiniteItems);
    }

    public KitFlags withHunger(boolean value) {
        return new KitFlags(build, value, naturalRegen, sumo, boxing, showHealth, rankedEnabled, editable, infiniteItems);
    }

    public KitFlags withNaturalRegen(boolean value) {
        return new KitFlags(build, hunger, value, sumo, boxing, showHealth, rankedEnabled, editable, infiniteItems);
    }

    public KitFlags withSumo(boolean value) {
        return new KitFlags(build, hunger, naturalRegen, value, boxing, showHealth, rankedEnabled, editable, infiniteItems);
    }

    public KitFlags withBoxing(boolean value) {
        return new KitFlags(build, hunger, naturalRegen, sumo, value, showHealth, rankedEnabled, editable, infiniteItems);
    }

    public KitFlags withRanked(boolean value) {
        return new KitFlags(build, hunger, naturalRegen, sumo, boxing, showHealth, value, editable, infiniteItems);
    }

    public KitFlags withEditable(boolean value) {
        return new KitFlags(build, hunger, naturalRegen, sumo, boxing, showHealth, rankedEnabled, value, infiniteItems);
    }

    public KitFlags withInfiniteItems(boolean value) {
        return new KitFlags(build, hunger, naturalRegen, sumo, boxing, showHealth, rankedEnabled, editable, value);
    }

    public void save(ConfigurationSection section) {
        section.set("build", build);
        section.set("hunger", hunger);
        section.set("natural-regen", naturalRegen);
        section.set("sumo", sumo);
        section.set("boxing", boxing);
        section.set("show-health", showHealth);
        section.set("ranked-enabled", rankedEnabled);
        section.set("editable", editable);
        section.set("infinite-items", infiniteItems);
    }

    public static KitFlags load(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        KitFlags defaults = defaults();
        return new KitFlags(
                section.getBoolean("build", defaults.build()),
                section.getBoolean("hunger", defaults.hunger()),
                section.getBoolean("natural-regen", defaults.naturalRegen()),
                section.getBoolean("sumo", defaults.sumo()),
                section.getBoolean("boxing", defaults.boxing()),
                section.getBoolean("show-health", defaults.showHealth()),
                section.getBoolean("ranked-enabled", defaults.rankedEnabled()),
                section.getBoolean("editable", defaults.editable()),
                section.getBoolean("infinite-items", defaults.infiniteItems()));
    }
}
