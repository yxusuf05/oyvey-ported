package me.alpha432.oyvey.features.sky;

import net.minecraft.resources.Identifier;

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
    private final boolean external;

    public SkyPack(String id, String name, String category, String description, List<SkyLayer> layers, boolean external) {
        this.id = id;
        this.name = name;
        this.category = category == null || category.isBlank() ? DEFAULT_CATEGORY : category;
        this.description = description == null ? "" : description;
        this.layers = List.copyOf(layers);
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
     * @return the texture the picker shows as a thumbnail
     */
    public Identifier getPreview() {
        return this.layers.isEmpty() ? null : this.layers.getFirst().getTexture();
    }
}
