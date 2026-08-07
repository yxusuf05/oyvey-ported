package io.github.yxusuf05.skyloom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything the mod remembers, stored as plain json in {@code config/skyloom.json}.
 */
public final class SkyloomConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Skyloom");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Id of the picked sky, or "none". */
    public String sky = "none";

    public float brightness = 1.0f;
    public boolean rotate = true;
    public float speed = 1.0f;

    public boolean hideSun = false;
    public boolean hideMoon = false;
    public boolean hideStars = false;
    public boolean hideSunrise = false;
    public boolean hideClouds = false;
    public boolean hideWeather = false;

    public boolean overworldOnly = true;

    /** Accent colour of the picker, as 0xRRGGBB. */
    public int accent = 0x4C6FFF;

    /** Where the Browse tab fetches its list of downloadable skies from. */
    public String catalogUrl = "https://raw.githubusercontent.com/yxusuf05/skyloom-skies/main/catalog.json";

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyloom.json");
    }

    public static SkyloomConfig load() {
        Path path = path();
        if (!Files.isRegularFile(path)) return new SkyloomConfig();
        try {
            SkyloomConfig config = GSON.fromJson(Files.readString(path), SkyloomConfig.class);
            return config == null ? new SkyloomConfig() : config;
        } catch (Throwable throwable) {
            LOGGER.error("Could not read {}, starting from defaults", path, throwable);
            return new SkyloomConfig();
        }
    }

    public void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (Throwable throwable) {
            LOGGER.error("Could not write {}", path, throwable);
        }
    }
}
