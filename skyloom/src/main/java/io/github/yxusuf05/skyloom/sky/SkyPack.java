package io.github.yxusuf05.skyloom.sky;

import io.github.yxusuf05.skyloom.sky.render.SkyFace;

import java.util.List;

/**
 * One entry of the sky picker: a name, a category and the layers that get drawn for it.
 */
public final class SkyPack {
    public static final String DEFAULT_CATEGORY = "Misc";

    private final String id;
    private final String name;
    private final String category;
    private final String description;
    private final List<SkyLayer> layers;
    private final SkyTexture thumbnail;
    private final boolean external;

    public SkyPack(String id, String name, String category, String description,
                   List<SkyLayer> layers, SkyTexture thumbnail, boolean external) {
        this.id = id;
        this.name = name;
        this.category = category == null || category.isBlank() ? DEFAULT_CATEGORY : category;
        this.description = description == null ? "" : description;
        this.layers = List.copyOf(layers);
        this.thumbnail = thumbnail;
        this.external = external;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getCategory() {
        return this.category;
    }

    public String getDescription() {
        return this.description;
    }

    public List<SkyLayer> getLayers() {
        return this.layers;
    }

    /**
     * @return true for skies loaded from the users skies folder instead of the mod jar
     */
    public boolean isExternal() {
        return this.external;
    }

    /**
     * The picker thumbnail. External packs get a small pre cut crop so the grid never has to
     * touch a full sheet, packs from the jar are small enough to sample in place.
     */
    public Preview getPreview() {
        if (this.thumbnail != null) {
            return new Preview(this.thumbnail, 0.0f, 1.0f, 0.0f, 1.0f);
        }
        if (this.layers.isEmpty()) return null;
        return new Preview(this.layers.getFirst().getTexture(),
                SkyFace.NORTH.getMinU(), SkyFace.NORTH.getMaxU(), SkyFace.NORTH.getMinV(), SkyFace.NORTH.getMaxV());
    }

    /**
     * Drops the video memory of every layer. The picker thumbnail stays.
     */
    public void release() {
        for (SkyLayer layer : this.layers) {
            layer.getTexture().release();
        }
    }

    public record Preview(SkyTexture texture, float minU, float maxU, float minV, float maxV) {
    }
}
