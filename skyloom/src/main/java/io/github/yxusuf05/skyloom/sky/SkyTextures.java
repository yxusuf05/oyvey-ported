package io.github.yxusuf05.skyloom.sky;

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
    private static ExecutorService network;

    private SkyTextures() {
    }

    /** Image decoding. One thread on purpose, a large sheet is memory hungry. */
    static void submit(Runnable task) {
        getExecutor().execute(task);
    }

    /**
     * Catalog and downloads. Separate from decoding, otherwise refreshing the list waits behind
     * however many thumbnails happen to be queued.
     */
    static synchronized void submitNetwork(Runnable task) {
        if (network == null) {
            network = Executors.newFixedThreadPool(3, task2 -> {
                Thread thread = new Thread(task2, "Skyloom network");
                thread.setDaemon(true);
                return thread;
            });
        }
        network.execute(task);
    }

    /**
     * Deliberately a sibling of the skies folder rather than a child: anything inside that folder
     * is treated as a sky, and cached thumbnails are not skies.
     */
    public static Path getThumbnailCache() {
        return SkyLoader.getSkiesDirectory().resolveSibling("cache");
    }

    private static synchronized ExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Skyloom texture loader");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        }
        return executor;
    }
}
