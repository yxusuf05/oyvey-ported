package io.github.yxusuf05.skyloom.sky;

import io.github.yxusuf05.skyloom.Skyloom;

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
    private static SkyPack resident;

    private SkyRegistry() {
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        SkyLoader.load(PACKS);
    }

    public static void reload() {
        for (SkyPack pack : PACKS.values()) pack.release();
        resident = null;
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
    /**
     * Also keeps the video memory honest: only the sky being rendered holds onto its sheets,
     * so a collection of a hundred packs costs no more than a single one.
     */
    public static SkyPack getActive() {
        SkyPack pack = byId(Skyloom.config().sky);
        if (pack != resident) {
            if (resident != null) resident.release();
            resident = pack;
        }
        return pack;
    }

    public static void setActive(SkyPack pack) {
        Skyloom.config().sky = pack == null ? NONE : pack.getId();
        Skyloom.save();
    }

    public static boolean isActive(SkyPack pack) {
        return (pack == null ? NONE : pack.getId()).equals(Skyloom.config().sky);
    }
}
