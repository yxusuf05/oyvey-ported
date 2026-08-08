package io.github.yxusuf05.skyloom.sky;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.yxusuf05.skyloom.Skyloom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The list of skies offered in the Browse tab, fetched from a json file the mod author hosts.
 * Nothing is downloaded until the player asks for a specific sky.
 */
public final class SkyCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Skyloom");
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;

    private static volatile State state = State.IDLE;
    private static volatile String error = "";
    private static volatile List<Entry> entries = List.of();

    private SkyCatalog() {
    }

    public static State getState() {
        return state;
    }

    public static String getError() {
        return error;
    }

    public static List<Entry> getEntries() {
        return entries;
    }

    /** Fetches once, then only again when the player asks for it. */
    public static void ensureLoaded() {
        if (state == State.IDLE) refresh();
    }

    public static void refresh() {
        if (state == State.LOADING) return;
        state = State.LOADING;
        error = "";
        SkyTextures.submitNetwork(SkyCatalog::fetch);
    }

    private static void fetch() {
        String url = Skyloom.config().catalogUrl;
        try {
            if (url == null || url.isBlank()) throw new IllegalStateException("no catalog url configured");
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalStateException("the catalog url has to be https");
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", SkyDownloads.USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = SkyDownloads.http().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("server answered " + response.statusCode());
            if (response.body().length() > MAX_CATALOG_BYTES) throw new IllegalStateException("catalog is too large");

            // editors and copy paste like to leave a byte order mark in front of the first brace
            String body = response.body().replace("\uFEFF", "").trim();
            if (body.isEmpty()) throw new IllegalStateException("the catalog file is empty");

            entries = parse(body);
            state = State.READY;
            LOGGER.info("Catalog listed {} skies", entries.size());
        } catch (Throwable throwable) {
            LOGGER.error("Could not read the sky catalog from {}", url, throwable);
            error = describe(throwable);
            entries = List.of();
            state = State.FAILED;
        }
    }

    private static List<Entry> parse(String body) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (Throwable throwable) {
            throw new IllegalStateException("the catalog is not valid json");
        }
        if (!parsed.isJsonObject()) throw new IllegalStateException("the catalog is not valid json");
        JsonObject root = parsed.getAsJsonObject();
        List<Entry> found = new ArrayList<>();
        if (!root.has("skies") || !root.get("skies").isJsonArray()) {
            throw new IllegalStateException("the catalog has no \"skies\" list");
        }
        for (JsonElement element : root.getAsJsonArray("skies")) {
            try {
                JsonObject object = element.getAsJsonObject();
                String id = object.get("id").getAsString().trim();
                String download = object.get("download").getAsString().trim();
                if (id.isEmpty() || !download.toLowerCase().startsWith("https://")) continue;
                found.add(new Entry(
                        id,
                        object.has("name") ? object.get("name").getAsString() : id,
                        object.has("category") ? object.get("category").getAsString() : "Downloads",
                        object.has("description") ? object.get("description").getAsString() : "",
                        object.has("thumbnail") ? object.get("thumbnail").getAsString().trim() : null,
                        download,
                        object.has("size") ? object.get("size").getAsLong() : 0L
                ));
            } catch (Throwable throwable) {
                LOGGER.warn("Skipping a malformed catalog entry", throwable);
            }
        }
        return List.copyOf(found);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public record Entry(String id, String name, String category, String description,
                        String thumbnailUrl, String downloadUrl, long size) {
    }

    public enum State {
        IDLE,
        LOADING,
        READY,
        FAILED
    }
}
