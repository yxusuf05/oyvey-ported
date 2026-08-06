package me.alpha432.oyvey.features.sky;

import com.mojang.blaze3d.platform.NativeImage;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A sky texture that only reaches the graphics card once something actually needs it.
 * <p>
 * Sky sheets are big - a 7680x5120 sheet is 157 MB of video memory - so a pack worth of them
 * is only worth holding while that pack is the one being rendered. Decoding happens on a
 * background thread and the upload on the render thread, so nothing ever stalls a frame.
 */
public final class SkyTexture {
    private static final Logger LOGGER = LoggerFactory.getLogger("OyVeySkies");
    private static final int THUMBNAIL_SIZE = 256;

    private final Identifier id;
    private final Path packPath;
    private final String entry;
    private final boolean thumbnail;

    private volatile State state = State.IDLE;
    private volatile NativeImage decoded;

    private SkyTexture(Identifier id, Path packPath, String entry, boolean thumbnail) {
        this.id = id;
        this.packPath = packPath;
        this.entry = entry;
        this.thumbnail = thumbnail;
    }

    /**
     * A texture that lives in a resource pack, the vanilla texture manager already handles those.
     */
    public static SkyTexture ofResource(Identifier id) {
        return new SkyTexture(id, null, null, false);
    }

    public static SkyTexture ofSheet(Identifier id, Path packPath, String entry) {
        return new SkyTexture(id, packPath, entry, false);
    }

    /**
     * A small crop of the sheets north face, kept around permanently for the picker grid.
     */
    public static SkyTexture ofThumbnail(Identifier id, Path packPath, String entry) {
        return new SkyTexture(id, packPath, entry, true);
    }

    public Identifier getId() {
        return this.id;
    }

    /**
     * @return the same image as a picker thumbnail, or null when it comes from a resource pack
     */
    public SkyTexture toThumbnail() {
        if (this.packPath == null) return null;
        return new SkyTexture(this.id.withSuffix("-thumb"), this.packPath, this.entry, true);
    }

    /**
     * Must be called on the render thread.
     *
     * @return the identifier once the texture is bindable, or null while it is still loading
     */
    public Identifier resolve() {
        if (this.packPath == null) return this.id;
        switch (this.state) {
            case READY -> {
                return this.id;
            }
            case DECODED -> {
                upload();
                return this.state == State.READY ? this.id : null;
            }
            case IDLE -> {
                this.state = State.LOADING;
                SkyTextures.submit(this::decode);
            }
            default -> {
            }
        }
        return null;
    }

    public boolean isReady() {
        return this.packPath == null || this.state == State.READY;
    }

    /**
     * Hands the video memory back. The texture reloads by itself the next time it is resolved.
     */
    public void release() {
        if (this.packPath == null) return;
        if (this.state == State.READY) Util.mc.getTextureManager().release(this.id);
        NativeImage pending = this.decoded;
        this.decoded = null;
        if (pending != null) pending.close();
        if (this.state != State.LOADING) this.state = State.IDLE;
    }

    private void upload() {
        NativeImage image = this.decoded;
        this.decoded = null;
        if (image == null) {
            this.state = State.IDLE;
            return;
        }
        try {
            Util.mc.getTextureManager().register(this.id, new DynamicTexture(this.id::toString, image));
            this.state = State.READY;
        } catch (Throwable throwable) {
            LOGGER.error("Failed to upload sky texture {}", this.id, throwable);
            image.close();
            this.state = State.FAILED;
        }
    }

    private void decode() {
        try {
            NativeImage image = this.thumbnail ? decodeThumbnail() : decodeSheet();
            if (image == null) {
                this.state = State.FAILED;
                return;
            }
            this.decoded = image;
            this.state = State.DECODED;
        } catch (Throwable throwable) {
            LOGGER.error("Failed to read sky texture {} from {}", this.entry, this.packPath, throwable);
            this.state = State.FAILED;
        }
    }

    private NativeImage decodeSheet() throws Exception {
        byte[] data = SkyLoader.readEntry(this.packPath, this.entry);
        return data == null ? null : NativeImage.read(new ByteArrayInputStream(data));
    }

    /**
     * Thumbnails survive on disk, so browsing a large collection only pays the decode once ever.
     */
    private NativeImage decodeThumbnail() throws Exception {
        Path cache = SkyTextures.getThumbnailCache().resolve(this.id.getPath().replace('/', '_') + ".png");
        if (Files.isRegularFile(cache)) {
            return NativeImage.read(new ByteArrayInputStream(Files.readAllBytes(cache)));
        }

        byte[] data = SkyLoader.readEntry(this.packPath, this.entry);
        if (data == null) return null;

        NativeImage thumb = new NativeImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, false);
        try (NativeImage full = NativeImage.read(new ByteArrayInputStream(data))) {
            int faceWidth = full.getWidth() / 3;
            int faceHeight = full.getHeight() / 2;
            // the north face sits in the bottom right cell of the sheet
            full.resizeSubRectTo(2 * faceWidth, faceHeight, faceWidth, faceHeight, thumb);
        }

        try {
            Files.createDirectories(cache.getParent());
            thumb.writeToFile(cache);
        } catch (Throwable throwable) {
            LOGGER.warn("Could not cache the thumbnail for {}", this.id, throwable);
        }
        return thumb;
    }

    private enum State {
        IDLE,
        LOADING,
        DECODED,
        READY,
        FAILED
    }
}
