package me.alpha432.oyvey.features.sky;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.render.CustomSkyModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every sky that is available to the picker. Loading happens lazily on the render
 * thread the first time somebody asks for a sky, because textures cannot be uploaded
 * before the game window exists.
 */
public final class SkyRegistry {
    public static final String NONE = "none";

    private static final Map<String, SkyPack> PACKS = new LinkedHashMap<>();
    private static boolean loaded;

    private SkyRegistry() {
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        SkyLoader.load(PACKS);
    }

    public static void reload() {
        SkyLoader.unload();
        PACKS.clear();
        loaded = false;
        ensureLoaded();
    }

    public static Collection<SkyPack> all() {
        ensureLoaded();
        return PACKS.values();
    }

    public static List<String> categories() {
        List<String> categories = new ArrayList<>();
        for (SkyPack pack : all()) {
            if (!categories.contains(pack.getCategory())) categories.add(pack.getCategory());
        }
        categories.sort(String::compareToIgnoreCase);
        return categories;
    }

    public static SkyPack byId(String id) {
        if (id == null || id.isBlank() || NONE.equalsIgnoreCase(id)) return null;
        ensureLoaded();
        return PACKS.get(id);
    }

    /**
     * @return the sky that should be rendered right now, or null when custom skies are off
     */
    public static SkyPack getActive() {
        CustomSkyModule module = getModule();
        if (module == null) return null;
        return byId(module.sky.getValue());
    }

    public static void setActive(SkyPack pack) {
        CustomSkyModule module = getModule();
        if (module == null) return;
        module.sky.setValue(pack == null ? NONE : pack.getId());
    }

    public static boolean isActive(SkyPack pack) {
        CustomSkyModule module = getModule();
        if (module == null) return false;
        String id = pack == null ? NONE : pack.getId();
        return id.equals(module.sky.getValue());
    }

    private static CustomSkyModule getModule() {
        if (OyVey.moduleManager == null) return null;
        return OyVey.moduleManager.getModuleByClass(CustomSkyModule.class);
    }
}
