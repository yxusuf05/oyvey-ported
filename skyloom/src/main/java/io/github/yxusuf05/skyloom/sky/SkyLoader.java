package io.github.yxusuf05.skyloom.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds the sky list from two places:
 * <ul>
 *     <li>skies shipped inside the mod jar, listed in {@code assets/skyloom/skies/index.json}</li>
 *     <li>skies the user dropped into {@code .minecraft/skyloom/skies}, either as a folder or a
 *     zip, in this mods own {@code sky.json} format or in the OptiFine / MCPatcher format</li>
 * </ul>
 */
public final class SkyLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Skyloom");
    private static final String BUILTIN_INDEX = "/assets/skyloom/skies/index.json";
    private static final String PACK_MANIFEST = "sky.json";

    private static final Pattern OTHER_WORLD = Pattern.compile("/world-?(?!0/)\\d+/");

    private SkyLoader() {
    }

    public static Path getSkiesDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("skyloom").resolve("skies");
    }

    static void load(Map<String, SkyPack> out) {
        loadBuiltin(out);
        loadExternal(out);
        LOGGER.info("Loaded {} custom skies", out.size());
    }

    static void unload() {
    }

    /**
     * Reads one file out of a pack, reopening the folder or zip each time. Called from the
     * texture loading thread, long after the pack itself was scanned.
     */
    static byte[] readEntry(Path packPath, String entry) throws IOException {
        boolean zip = Files.isRegularFile(packPath);
        try (PackSource source = zip ? new ZipSource(packPath) : new DirectorySource(packPath)) {
            return source.read(entry);
        }
    }

    // -----------------------------------------------------------------------------------------
    // skies shipped with the mod
    // -----------------------------------------------------------------------------------------

    private static void loadBuiltin(Map<String, SkyPack> out) {
        try (InputStream stream = SkyLoader.class.getResourceAsStream(BUILTIN_INDEX)) {
            if (stream == null) return;
            JsonObject index = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!index.has("skies")) return;
            for (JsonElement element : index.getAsJsonArray("skies")) {
                try {
                    JsonObject object = element.getAsJsonObject();
                    String id = object.get("id").getAsString();
                    SkyPack pack = readManifest(object, id, false, path -> SkyTexture.ofResource(Identifier.parse(path)));
                    if (pack != null) out.put(pack.getId(), pack);
                } catch (Throwable throwable) {
                    LOGGER.error("Failed to read a built in sky", throwable);
                }
            }
        } catch (Throwable throwable) {
            LOGGER.error("Failed to read {}", BUILTIN_INDEX, throwable);
        }
    }

    // -----------------------------------------------------------------------------------------
    // skies from .minecraft/skyloom/skies
    // -----------------------------------------------------------------------------------------

    private static void loadExternal(Map<String, SkyPack> out) {
        Path directory = getSkiesDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            LOGGER.error("Failed to create {}", directory, exception);
            return;
        }

        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> children = Files.list(directory)) {
            children.forEach(candidates::add);
        } catch (IOException exception) {
            LOGGER.error("Failed to list {}", directory, exception);
            return;
        }
        candidates.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));

        for (Path candidate : candidates) {
            boolean zip = Files.isRegularFile(candidate) && candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
            if (!Files.isDirectory(candidate) && !zip) continue;

            try (PackSource source = zip ? new ZipSource(candidate) : new DirectorySource(candidate)) {
                SkyPack pack = readExternal(source, uniqueId(out, packId(stripExtension(candidate.getFileName().toString()))));
                if (pack != null && !pack.getLayers().isEmpty()) out.put(pack.getId(), pack);
            } catch (Throwable throwable) {
                LOGGER.error("Failed to load sky pack {}", candidate.getFileName(), throwable);
            }
        }
    }

    private static SkyPack readExternal(PackSource source, String id) throws IOException {
        String manifest = source.find(PACK_MANIFEST);
        if (manifest != null) {
            byte[] data = source.read(manifest);
            JsonObject object = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            return readManifest(object, id, true, path -> sheetTexture(source, id, resolveSibling(manifest, path)));
        }
        return readOptiFine(source, id);
    }

    /**
     * Reads the shared sky.json / index.json shape. {@code textures} turns a texture reference
     * from the manifest into something the texture manager can bind.
     */
    private static SkyPack readManifest(JsonObject object, String id, boolean external, TextureResolver textures) {
        String name = string(object, "name", id);
        String category = string(object, "category", SkyPack.DEFAULT_CATEGORY);
        String description = string(object, "description", "");

        List<SkyLayer> layers = new ArrayList<>();
        if (object.has("layers")) {
            for (JsonElement element : object.getAsJsonArray("layers")) {
                SkyLayer layer = readLayer(element.getAsJsonObject(), textures);
                if (layer != null) layers.add(layer);
            }
        } else if (object.has("texture")) {
            SkyLayer layer = readLayer(object, textures);
            if (layer != null) layers.add(layer);
        }

        if (layers.isEmpty()) {
            LOGGER.warn("Sky {} has no usable layers", id);
            return null;
        }
        return new SkyPack(id, name, category, description, layers, thumbnailOf(layers), external);
    }

    private static SkyLayer readLayer(JsonObject object, TextureResolver textures) {
        if (!object.has("texture")) return null;
        SkyTexture texture = textures.resolve(object.get("texture").getAsString());
        if (texture == null) return null;

        SkyLayer.Builder builder = SkyLayer.builder(texture)
                .blend(BlendMode.byName(string(object, "blend", null), BlendMode.REPLACE))
                .rotate(bool(object, "rotate", true))
                .speed(number(object, "speed", 1.0f));

        if (object.has("axis")) {
            JsonArray axis = object.getAsJsonArray("axis");
            if (axis.size() == 3) {
                builder.axis(new Vector3f(axis.get(0).getAsFloat(), axis.get(1).getAsFloat(), axis.get(2).getAsFloat()));
            }
        }

        Fade fade = readFade(string(object, "startFadeIn", null), string(object, "endFadeIn", null),
                string(object, "startFadeOut", null), string(object, "endFadeOut", null));
        builder.fade(fade);

        if (object.has("weather")) {
            Set<WeatherCondition> weather = EnumSet.noneOf(WeatherCondition.class);
            for (JsonElement element : object.getAsJsonArray("weather")) {
                WeatherCondition condition = WeatherCondition.byName(element.getAsString());
                if (condition != null) weather.add(condition);
            }
            builder.weather(weather);
        }
        return builder.build();
    }

    // -----------------------------------------------------------------------------------------
    // OptiFine / MCPatcher packs
    // -----------------------------------------------------------------------------------------

    private static SkyPack readOptiFine(PackSource source, String id) throws IOException {
        List<String> properties = new ArrayList<>();
        for (String entry : source.entries()) {
            String lower = entry.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".properties")) continue;
            if (!lower.contains("sky/") && !fileName(lower).startsWith("sky")) continue;
            if (OTHER_WORLD.matcher(lower).find()) continue; // world1 is the End, vanilla keeps its own sky there
            properties.add(entry);
        }
        properties.sort(Comparator.comparingInt(SkyLoader::layerNumber).thenComparing(entry -> entry));

        List<SkyLayer> layers = new ArrayList<>();
        for (String entry : properties) {
            SkyLayer layer = readOptiFineLayer(source, id, entry);
            if (layer != null) layers.add(layer);
        }

        if (layers.isEmpty()) {
            // a bare folder holding nothing but a skybox sheet is still a perfectly good sky
            for (String entry : source.entries()) {
                if (!entry.toLowerCase(Locale.ROOT).endsWith(".png") || entry.contains("/")) continue;
                SkyTexture texture = sheetTexture(source, id, entry);
                if (texture != null) layers.add(SkyLayer.builder(texture).blend(BlendMode.REPLACE).build());
            }
        }

        if (layers.isEmpty()) return null;
        return new SkyPack(id, prettify(id), "OptiFine", "Loaded from " + source.name(), layers, thumbnailOf(layers), true);
    }

    private static SkyLayer readOptiFineLayer(PackSource source, String id, String entry) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = new ByteArrayInputStream(source.read(entry))) {
            properties.load(stream);
        }

        String sourcePath = properties.getProperty("source", "./" + stripExtension(fileName(entry)) + ".png");
        String resolved = resolveOptiFineSource(source, entry, sourcePath);
        if (resolved == null) {
            LOGGER.warn("Sky layer {} points at a missing texture: {}", entry, sourcePath);
            return null;
        }

        SkyTexture texture = sheetTexture(source, id, resolved);
        if (texture == null) return null;

        SkyLayer.Builder builder = SkyLayer.builder(texture)
                .blend(BlendMode.byName(properties.getProperty("blend"), BlendMode.ADD))
                .rotate(!"false".equalsIgnoreCase(properties.getProperty("rotate", "true")))
                .speed(parseFloat(properties.getProperty("speed"), 1.0f))
                .fade(readFade(properties.getProperty("startFadeIn"), properties.getProperty("endFadeIn"),
                        properties.getProperty("startFadeOut"), properties.getProperty("endFadeOut")));

        String axis = properties.getProperty("axis");
        if (axis != null) {
            String[] parts = axis.trim().split("\\s+");
            if (parts.length == 3) {
                builder.axis(new Vector3f(parseFloat(parts[0], 0.0f), parseFloat(parts[1], 0.0f), parseFloat(parts[2], 1.0f)));
            }
        }

        String weather = properties.getProperty("weather");
        if (weather != null) {
            Set<WeatherCondition> conditions = EnumSet.noneOf(WeatherCondition.class);
            for (String part : weather.trim().split("\\s+")) {
                WeatherCondition condition = WeatherCondition.byName(part);
                if (condition != null) conditions.add(condition);
            }
            builder.weather(conditions);
        }
        return builder.build();
    }

    /**
     * OptiFine allows {@code ./relative}, {@code ~/} for the optifine folder and plain pack paths.
     */
    private static String resolveOptiFineSource(PackSource source, String propertiesEntry, String value) {
        String path = value.trim();
        List<String> attempts = new ArrayList<>();
        if (path.startsWith("./")) {
            attempts.add(resolveSibling(propertiesEntry, path.substring(2)));
        } else if (path.startsWith("~/")) {
            attempts.add("assets/minecraft/optifine/" + path.substring(2));
            attempts.add("assets/minecraft/mcpatcher/" + path.substring(2));
        } else {
            attempts.add(path);
            attempts.add("assets/minecraft/" + path);
            attempts.add(resolveSibling(propertiesEntry, path));
        }
        for (String attempt : attempts) {
            String found = source.find(attempt);
            if (found != null) return found;
        }
        return null;
    }

    private static int layerNumber(String entry) {
        String name = stripExtension(fileName(entry));
        int index = name.length();
        while (index > 0 && Character.isDigit(name.charAt(index - 1))) index--;
        if (index == name.length()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(name.substring(index));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    // -----------------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------------

    private static Fade readFade(String startFadeIn, String endFadeIn, String startFadeOut, String endFadeOut) {
        int in0 = Fade.parseTime(startFadeIn);
        int in1 = Fade.parseTime(endFadeIn);
        int out1 = Fade.parseTime(endFadeOut);
        if (in0 < 0 || in1 < 0 || out1 < 0) return Fade.ALWAYS;

        int out0 = Fade.parseTime(startFadeOut);
        if (out0 < 0) {
            // OptiFine leaves startFadeOut optional, the fade out then mirrors the fade in
            out0 = out1 - Math.floorMod(in1 - in0, Fade.DAY_LENGTH);
        }
        return Fade.of(in0, in1, out0, out1);
    }

    private static SkyTexture sheetTexture(PackSource source, String packId, String entry) {
        if (entry == null || source.find(entry) == null) return null;
        return SkyTexture.ofSheet(textureId(packId, entry), source.path(), entry);
    }

    private static Identifier textureId(String packId, String entry) {
        return Identifier.fromNamespaceAndPath("skyloom", "skies/" + packId + "/" + sanitise(entry));
    }

    private static String resolveSibling(String entry, String relative) {
        int slash = entry.lastIndexOf('/');
        return slash < 0 ? relative : entry.substring(0, slash + 1) + relative;
    }

    private static String fileName(String entry) {
        int slash = entry.lastIndexOf('/');
        return slash < 0 ? entry : entry.substring(slash + 1);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /**
     * Identifiers only accept a small character set, everything else becomes an underscore.
     */
    private static String sanitise(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                    || character == '_' || character == '.' || character == '-' || character == '/' ? character : '_');
        }
        return builder.toString();
    }

    /**
     * Pack ids end up in the config file, where underscores are reserved, so they use dashes.
     */
    private static String packId(String value) {
        String id = sanitise(value).replace('_', '-').replace('/', '-').replace('.', '-');
        return id.isBlank() ? "pack" : id;
    }

    private static String uniqueId(Map<String, SkyPack> out, String id) {
        String unique = id;
        int suffix = 2;
        while (out.containsKey(unique)) unique = id + "-" + suffix++;
        return unique;
    }

    private static String prettify(String id) {
        String replaced = id.replace('_', ' ').replace('-', ' ').trim();
        if (replaced.isEmpty()) return id;
        return Character.toUpperCase(replaced.charAt(0)) + replaced.substring(1);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
    }

    private static float number(JsonObject object, String key, float fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsFloat() : fallback;
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null) return fallback;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * @return a small crop of the first layer for the picker grid, null for packs in the jar
     */
    private static SkyTexture thumbnailOf(List<SkyLayer> layers) {
        return layers.isEmpty() ? null : layers.getFirst().getTexture().toThumbnail();
    }

    @FunctionalInterface
    private interface TextureResolver {
        SkyTexture resolve(String path);
    }

    // -----------------------------------------------------------------------------------------
    // reading folders and zips through the same interface
    // -----------------------------------------------------------------------------------------

    private interface PackSource extends Closeable {
        String name();

        Path path();

        List<String> entries();

        byte[] read(String entry) throws IOException;

        /**
         * @return the real entry name for a case insensitive lookup, or null when it is missing
         */
        default String find(String entry) {
            String wanted = entry.replace('\\', '/');
            while (wanted.startsWith("/")) wanted = wanted.substring(1);
            for (String candidate : entries()) {
                if (candidate.equalsIgnoreCase(wanted)) return candidate;
            }
            return null;
        }
    }

    private static final class DirectorySource implements PackSource {
        private final Path root;
        private final List<String> entries = new ArrayList<>();

        private DirectorySource(Path root) throws IOException {
            this.root = root;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .forEach(path -> entries.add(root.relativize(path).toString().replace('\\', '/')));
            }
        }

        @Override
        public String name() {
            return this.root.getFileName().toString();
        }

        @Override
        public Path path() {
            return this.root;
        }

        @Override
        public List<String> entries() {
            return this.entries;
        }

        @Override
        public byte[] read(String entry) throws IOException {
            Path path = this.root.resolve(entry).normalize();
            if (!path.startsWith(this.root) || !Files.isRegularFile(path)) return null;
            return Files.readAllBytes(path);
        }

        @Override
        public void close() {
        }
    }

    private static final class ZipSource implements PackSource {
        private final Path path;
        private final ZipFile zip;
        private final List<String> entries = new ArrayList<>();

        private ZipSource(Path path) throws IOException {
            this.path = path;
            this.zip = new ZipFile(path.toFile());
            this.zip.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> entries.add(entry.getName().replace('\\', '/')));
        }

        @Override
        public String name() {
            return this.path.getFileName().toString();
        }

        @Override
        public Path path() {
            return this.path;
        }

        @Override
        public List<String> entries() {
            return this.entries;
        }

        @Override
        public byte[] read(String entry) throws IOException {
            ZipEntry zipEntry = this.zip.getEntry(entry);
            if (zipEntry == null) return null;
            try (InputStream stream = this.zip.getInputStream(zipEntry)) {
                return stream.readAllBytes();
            }
        }

        @Override
        public void close() throws IOException {
            this.zip.close();
        }
    }
}
