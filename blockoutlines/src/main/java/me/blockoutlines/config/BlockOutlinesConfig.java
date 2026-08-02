package me.blockoutlines.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every user facing setting of the mod. Serialised as-is to {@code config/blockoutlines.json},
 * so field names double as the config keys.
 */
public class BlockOutlinesConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("blockoutlines");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("blockoutlines.json");

    // ---------------------------------------------------------------- general
    /** Master switch, toggled by {@link #toggleKey}. */
    public boolean enabled = true;
    /** GLFW key code that toggles the mod, {@code -1} when unbound. */
    public int toggleKey = -1;
    /** GLFW key code that opens this config screen, {@code -1} when unbound. */
    public int menuKey = -1;
    /** Print a chat message whenever the mod is toggled with the keybind. */
    public boolean toggleMessage = true;

    // ------------------------------------------------------- targeted block
    /** How the block the crosshair points at is drawn. */
    public TargetMode targetMode = TargetMode.CUSTOM;
    /** ARGB colour of the outline around the targeted block. */
    public int targetColor = 0xFFFF3B30;
    /** Outline thickness in pixels. */
    public float targetLineWidth = 2.0f;
    /** Also draw a translucent box inside the outline. */
    public boolean targetFill = false;
    /** ARGB colour of that translucent box. */
    public int targetFillColor = 0x40FF3B30;
    /** Draw the outline even when the block is behind other blocks. */
    public boolean targetThroughWalls = false;
    /** Trace the real collision shape (stairs, slabs, ...) or always a full cube. */
    public BoxShape targetShape = BoxShape.BLOCK_SHAPE;
    /** Grows (positive) or shrinks (negative) the box, in blocks. */
    public float targetExpand = 0.002f;
    /** Cycle the outline colour through the rainbow instead of using {@link #targetColor}. */
    public boolean targetRainbow = false;

    // ---------------------------------------------------------- block search
    /** Highlight the blocks listed in {@link #blocks} anywhere around the player. */
    public boolean searchEnabled = false;
    /** Outline, fill or both. */
    public RenderMode searchMode = RenderMode.BOTH;
    /** ARGB colour used for blocks that have no colour of their own. */
    public int searchColor = 0xFF34C759;
    /** Outline thickness in pixels. */
    public float searchLineWidth = 1.5f;
    /** Alpha (0-255) of the fill, the RGB part comes from the block colour. */
    public int searchFillAlpha = 60;
    /** Search radius in blocks around the player. */
    public int searchRange = 64;
    /** Upper bound on highlighted blocks, protects the framerate in ore-heavy areas. */
    public int searchMaxBlocks = 3000;
    /** Draw highlights through terrain. */
    public boolean searchThroughWalls = true;
    /** Skip blocks that are completely enclosed by other solid blocks. */
    public boolean searchOnlyExposed = false;
    /** Merge adjacent blocks of the same kind into a single box. */
    public boolean searchMergeTouching = false;
    /** Draw a line from the crosshair to every highlighted block. */
    public boolean searchTracers = false;
    /** ARGB colour of those lines. */
    public int searchTracerColor = 0x9934C759;
    /** Tracer thickness in pixels. */
    public float searchTracerWidth = 1.0f;
    /** Cycle every highlight through the rainbow, ignoring the configured colours. */
    public boolean searchRainbow = false;
    /** Milliseconds between two scans of the loaded chunks. */
    public int searchScanInterval = 500;
    /** Grows (positive) or shrinks (negative) the boxes, in blocks. */
    public float searchExpand = 0.002f;
    /** Trace the real collision shape or always a full cube. */
    public BoxShape searchShape = BoxShape.FULL_CUBE;

    // ---------------------------------------------------------------- shared
    /** Full rainbow cycles per minute. */
    public float rainbowSpeed = 20.0f;
    /** Fade highlights out towards the edge of the search range. */
    public boolean distanceFade = false;

    /** Tracked blocks: registry id -> ARGB colour, {@code -1} meaning "use {@link #searchColor}". */
    public Map<String, Integer> blocks = new LinkedHashMap<>();

    // ------------------------------------------------------------------ i/o
    public static BlockOutlinesConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                BlockOutlinesConfig config = GSON.fromJson(reader, BlockOutlinesConfig.class);
                if (config != null) {
                    config.sanitise();
                    return config;
                }
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("[BlockOutlines] Could not read {}, falling back to defaults", PATH, e);
            }
        }

        BlockOutlinesConfig config = new BlockOutlinesConfig();
        config.addDefaultBlocks();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[BlockOutlines] Could not write {}", PATH, e);
        }
    }

    /** Repairs values that a hand-edited config file could have left in an unusable state. */
    private void sanitise() {
        if (blocks == null) {
            blocks = new LinkedHashMap<>();
        }
        if (targetMode == null) {
            targetMode = TargetMode.CUSTOM;
        }
        if (searchMode == null) {
            searchMode = RenderMode.BOTH;
        }
        if (targetShape == null) {
            targetShape = BoxShape.BLOCK_SHAPE;
        }
        if (searchShape == null) {
            searchShape = BoxShape.FULL_CUBE;
        }
        targetLineWidth = clamp(targetLineWidth, 0.5f, 10.0f);
        searchLineWidth = clamp(searchLineWidth, 0.5f, 10.0f);
        searchTracerWidth = clamp(searchTracerWidth, 0.5f, 10.0f);
        targetExpand = clamp(targetExpand, -0.4f, 0.5f);
        searchExpand = clamp(searchExpand, -0.4f, 0.5f);
        searchFillAlpha = (int) clamp(searchFillAlpha, 0, 255);
        searchRange = (int) clamp(searchRange, 8, 256);
        searchMaxBlocks = (int) clamp(searchMaxBlocks, 100, 20000);
        searchScanInterval = (int) clamp(searchScanInterval, 50, 5000);
        rainbowSpeed = clamp(rainbowSpeed, 1.0f, 120.0f);
    }

    private void addDefaultBlocks() {
        blocks.put("minecraft:diamond_ore", 0xFF00E5FF);
        blocks.put("minecraft:deepslate_diamond_ore", 0xFF00E5FF);
        blocks.put("minecraft:ancient_debris", 0xFFB05A2E);
        blocks.put("minecraft:chest", 0xFFFFB000);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
