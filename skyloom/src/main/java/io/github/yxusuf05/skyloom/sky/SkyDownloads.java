package io.github.yxusuf05.skyloom.sky;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads a sky from the catalog straight into the skies folder. The archive is stored as it
 * came down and read by the normal loader, so nothing is ever unpacked onto the disk.
 */
public final class SkyDownloads {
    static final String USER_AGENT = "Skyloom";
    private static final Logger LOGGER = LoggerFactory.getLogger("Skyloom");
    private static final long MAX_DOWNLOAD_BYTES = 2L * 1024L * 1024L * 1024L;

    private static final Map<String, Download> DOWNLOADS = new ConcurrentHashMap<>();
    private static HttpClient client;

    private SkyDownloads() {
    }

    static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    /** Small images only, the catalog previews are thumbnails and nothing else is expected. */
    static byte[] fetchImage(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalStateException("previews have to be https");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = http().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IllegalStateException("server answered " + response.statusCode());
        byte[] body = response.body();
        if (body.length > 8 * 1024 * 1024) throw new IllegalStateException("preview is too large");
        return body;
    }

    public static Path fileFor(String id) {
        return SkyLoader.getSkiesDirectory().resolve(safeName(id) + ".zip");
    }

    public static boolean isInstalled(String id) {
        return Files.isRegularFile(fileFor(id));
    }

    public static Download getDownload(String id) {
        return DOWNLOADS.get(id);
    }

    public static void install(SkyCatalog.Entry entry) {
        if (DOWNLOADS.containsKey(entry.id())) return;
        Download download = new Download(entry.size());
        DOWNLOADS.put(entry.id(), download);
        SkyTextures.submit(() -> run(entry, download));
    }

    public static boolean remove(String id) {
        try {
            boolean deleted = Files.deleteIfExists(fileFor(id));
            if (deleted) SkyRegistry.reload();
            return deleted;
        } catch (Throwable throwable) {
            LOGGER.error("Could not remove {}", id, throwable);
            return false;
        }
    }

    private static void run(SkyCatalog.Entry entry, Download download) {
        Path target = fileFor(entry.id());
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try {
            URI uri = URI.create(entry.downloadUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalStateException("downloads have to be https");

            Files.createDirectories(target.getParent());
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(30))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) throw new IllegalStateException("server answered " + response.statusCode());

            long expected = response.headers().firstValueAsLong("content-length").orElse(entry.size());
            download.total = expected > 0 ? expected : entry.size();

            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[1 << 16];
                long written = 0;
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    written += read;
                    download.done = written;
                    if (written > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("download exceeded the size limit");
                }
            }

            if (!looksLikeZip(temporary)) throw new IllegalStateException("the download is not a zip archive");

            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            download.finished = true;
            SkyRegistry.requestReload();
            LOGGER.info("Installed sky {}", entry.id());
        } catch (Throwable throwable) {
            LOGGER.error("Could not download {}", entry.id(), throwable);
            download.error = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            try {
                Files.deleteIfExists(temporary);
            } catch (Throwable ignored) {
                // nothing useful to do about a leftover part file
            }
        } finally {
            download.done = Math.max(download.done, 0);
            if (download.error == null) DOWNLOADS.remove(entry.id());
        }
    }

    /** Guards against a html error page being saved as if it were a sky. */
    private static boolean looksLikeZip(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] magic = in.readNBytes(2);
            return magic.length == 2 && magic[0] == 'P' && magic[1] == 'K';
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static String safeName(String id) {
        StringBuilder builder = new StringBuilder(id.length());
        for (char character : id.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                    || character == '-' ? character : '-');
        }
        String name = builder.toString();
        return name.isBlank() ? "sky" : name;
    }

    public static final class Download {
        volatile long done;
        volatile long total;
        volatile boolean finished;
        volatile String error;

        Download(long total) {
            this.total = total;
        }

        public float progress() {
            if (this.finished) return 1.0f;
            if (this.total <= 0) return -1.0f; // unknown length, the bar shows as indeterminate
            return Math.min(1.0f, (float) this.done / (float) this.total);
        }

        public long done() {
            return this.done;
        }

        public String error() {
            return this.error;
        }

        public void dismiss() {
            this.error = null;
        }
    }
}
