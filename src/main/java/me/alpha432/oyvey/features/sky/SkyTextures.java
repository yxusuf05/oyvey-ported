package me.alpha432.oyvey.features.sky;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single background thread every sky texture decodes on, plus where thumbnails are cached.
 * One thread on purpose: decoding a large sheet is memory hungry and doing several at once
 * buys nothing.
 */
public final class SkyTextures {
    private static ExecutorService executor;

    private SkyTextures() {
    }

    static void submit(Runnable task) {
        getExecutor().execute(task);
    }

    public static Path getThumbnailCache() {
        return SkyLoader.getSkiesDirectory().resolve(".thumbnails");
    }

    private static synchronized ExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "OyVey Sky Loader");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        }
        return executor;
    }
}
