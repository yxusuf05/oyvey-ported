package me.blockoutlines.gui;

import com.mojang.blaze3d.platform.InputConstants;
import me.blockoutlines.BlockOutlines;
import me.blockoutlines.config.BlockOutlinesConfig;
import me.blockoutlines.config.BoxShape;
import me.blockoutlines.config.RenderMode;
import me.blockoutlines.config.TargetMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Main settings screen, reachable from Mod Menu or from the configurable menu key. */
public class ConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 22;

    private final @Nullable Screen parent;
    private OptionList list;
    private double scrollAmount;

    /** Which keybind is currently waiting for a key press, {@code null} when none. */
    private @Nullable Binding listening;
    private @Nullable Button toggleKeyButton;
    private @Nullable Button menuKeyButton;

    private enum Binding {
        TOGGLE,
        MENU
    }

    public ConfigScreen(@Nullable Screen parent) {
        super(Component.literal("Block Outlines"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BlockOutlinesConfig config = BlockOutlines.config();

        this.list = new OptionList(minecraft, width, height - 64, 32, ROW_HEIGHT);
        addRenderableWidget(list);

        // ---------------------------------------------------------------- general
        list.addHeader(Component.literal("General").withStyle(ChatFormatting.YELLOW));
        list.addFull(toggle("Mod enabled", config.enabled, value -> config.enabled = value));

        toggleKeyButton = Button.builder(keyLabel("Toggle key", config.toggleKey),
                button -> startListening(Binding.TOGGLE)).build();
        menuKeyButton = Button.builder(keyLabel("Menu key", config.menuKey),
                button -> startListening(Binding.MENU)).build();
        list.addPair(toggleKeyButton, menuKeyButton);
        list.addFull(toggle("Chat message on toggle", config.toggleMessage, value -> config.toggleMessage = value));

        // ------------------------------------------------------------ target block
        list.addHeader(Component.literal("Block you are looking at").withStyle(ChatFormatting.YELLOW));
        list.addFull(CycleButton.builder((TargetMode mode) -> Component.literal(mode.displayName()), config.targetMode)
                .withValues(TargetMode.values())
                .create(0, 0, 200, 20, Component.literal("Outline"), (button, value) -> {
                    config.targetMode = value;
                    BlockOutlines.configChanged();
                }));
        list.addWithSwatch(colorButton("Outline colour", () -> config.targetColor, value -> config.targetColor = value),
                () -> config.targetColor);
        list.addFull(new NumberSlider(200, 20, "Line width", "px", config.targetLineWidth, 0.5, 10.0, 0.1, false,
                value -> {
                    config.targetLineWidth = (float) value;
                    BlockOutlines.configChanged();
                }));
        list.addPair(cycle("Shape", BoxShape.values(), config.targetShape, BoxShape::displayName,
                        value -> config.targetShape = value),
                new NumberSlider(200, 20, "Box size", "", config.targetExpand, -0.4, 0.5, 0.01, false, value -> {
                    config.targetExpand = (float) value;
                    BlockOutlines.configChanged();
                }));
        list.addPair(toggle("Fill", config.targetFill, value -> config.targetFill = value),
                toggle("Through walls", config.targetThroughWalls, value -> config.targetThroughWalls = value));
        list.addWithSwatch(colorButton("Fill colour", () -> config.targetFillColor, value -> config.targetFillColor = value),
                () -> config.targetFillColor);
        list.addFull(toggle("Rainbow", config.targetRainbow, value -> config.targetRainbow = value));

        // ----------------------------------------------------------- block search
        list.addHeader(Component.literal("Block search (highlight chosen blocks)").withStyle(ChatFormatting.YELLOW));
        list.addPair(toggle("Search enabled", config.searchEnabled, value -> config.searchEnabled = value),
                Button.builder(Component.literal("Blocks... (" + config.blocks.size() + ")"), button -> {
                    saveScroll();
                    minecraft.setScreen(new BlockListScreen(this));
                }).build());
        list.addFull(cycle("Draw mode", RenderMode.values(), config.searchMode, RenderMode::displayName,
                value -> config.searchMode = value));
        list.addWithSwatch(colorButton("Default colour", () -> config.searchColor, value -> config.searchColor = value),
                () -> config.searchColor);
        list.addPair(new NumberSlider(200, 20, "Line width", "px", config.searchLineWidth, 0.5, 10.0, 0.1, false,
                        value -> {
                            config.searchLineWidth = (float) value;
                            BlockOutlines.configChanged();
                        }),
                new NumberSlider(200, 20, "Fill opacity", "", config.searchFillAlpha, 0, 255, 1, true, value -> {
                    config.searchFillAlpha = (int) value;
                    BlockOutlines.configChanged();
                }));
        list.addPair(new NumberSlider(200, 20, "Range", " blocks", config.searchRange, 8, 256, 4, true, value -> {
                    config.searchRange = (int) value;
                    BlockOutlines.configChanged();
                }),
                new NumberSlider(200, 20, "Max blocks", "", config.searchMaxBlocks, 100, 20000, 100, true, value -> {
                    config.searchMaxBlocks = (int) value;
                    BlockOutlines.configChanged();
                }));
        list.addPair(new NumberSlider(200, 20, "Scan interval", " ms", config.searchScanInterval, 50, 5000, 50, true,
                        value -> {
                            config.searchScanInterval = (int) value;
                            BlockOutlines.configChanged();
                        }),
                new NumberSlider(200, 20, "Box size", "", config.searchExpand, -0.4, 0.5, 0.01, false, value -> {
                    config.searchExpand = (float) value;
                    BlockOutlines.configChanged();
                }));
        list.addPair(cycle("Shape", BoxShape.values(), config.searchShape, BoxShape::displayName,
                        value -> config.searchShape = value),
                toggle("Through walls", config.searchThroughWalls, value -> config.searchThroughWalls = value));
        list.addPair(toggle("Only exposed blocks", config.searchOnlyExposed, value -> config.searchOnlyExposed = value),
                toggle("Merge touching blocks", config.searchMergeTouching, value -> config.searchMergeTouching = value));
        list.addPair(toggle("Tracers", config.searchTracers, value -> config.searchTracers = value),
                new NumberSlider(200, 20, "Tracer width", "px", config.searchTracerWidth, 0.5, 10.0, 0.1, false,
                        value -> {
                            config.searchTracerWidth = (float) value;
                            BlockOutlines.configChanged();
                        }));
        list.addWithSwatch(colorButton("Tracer colour", () -> config.searchTracerColor,
                value -> config.searchTracerColor = value), () -> config.searchTracerColor);
        list.addFull(toggle("Rainbow", config.searchRainbow, value -> config.searchRainbow = value));

        // ----------------------------------------------------------------- shared
        list.addHeader(Component.literal("Shared").withStyle(ChatFormatting.YELLOW));
        list.addPair(new NumberSlider(200, 20, "Rainbow speed", "/min", config.rainbowSpeed, 1, 120, 1, true, value -> {
                    config.rainbowSpeed = (float) value;
                    BlockOutlines.configChanged();
                }),
                toggle("Fade with distance", config.distanceFade, value -> config.distanceFade = value));
        list.addFull(Button.builder(Component.literal("Reset all settings"), button -> {
            BlockOutlinesConfig fresh = new BlockOutlinesConfig();
            fresh.blocks = config.blocks;
            copyInto(fresh);
            BlockOutlines.configChanged();
            rebuild();
        }).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20)
                .build());

        list.setScrollAmount(scrollAmount);
    }

    /** Copies every setting of {@code source} into the live config instance. */
    private static void copyInto(BlockOutlinesConfig source) {
        BlockOutlinesConfig config = BlockOutlines.config();
        config.enabled = source.enabled;
        config.toggleKey = source.toggleKey;
        config.menuKey = source.menuKey;
        config.toggleMessage = source.toggleMessage;
        config.targetMode = source.targetMode;
        config.targetColor = source.targetColor;
        config.targetLineWidth = source.targetLineWidth;
        config.targetFill = source.targetFill;
        config.targetFillColor = source.targetFillColor;
        config.targetThroughWalls = source.targetThroughWalls;
        config.targetShape = source.targetShape;
        config.targetExpand = source.targetExpand;
        config.targetRainbow = source.targetRainbow;
        config.searchEnabled = source.searchEnabled;
        config.searchMode = source.searchMode;
        config.searchColor = source.searchColor;
        config.searchLineWidth = source.searchLineWidth;
        config.searchFillAlpha = source.searchFillAlpha;
        config.searchRange = source.searchRange;
        config.searchMaxBlocks = source.searchMaxBlocks;
        config.searchThroughWalls = source.searchThroughWalls;
        config.searchOnlyExposed = source.searchOnlyExposed;
        config.searchMergeTouching = source.searchMergeTouching;
        config.searchTracers = source.searchTracers;
        config.searchTracerColor = source.searchTracerColor;
        config.searchTracerWidth = source.searchTracerWidth;
        config.searchRainbow = source.searchRainbow;
        config.searchScanInterval = source.searchScanInterval;
        config.searchExpand = source.searchExpand;
        config.searchShape = source.searchShape;
        config.rainbowSpeed = source.rainbowSpeed;
        config.distanceFade = source.distanceFade;
    }

    private void saveScroll() {
        scrollAmount = list == null ? 0.0 : list.scrollAmount();
    }

    private void rebuild() {
        saveScroll();
        rebuildWidgets();
    }

    // ------------------------------------------------------------- widget helpers
    private AbstractWidget toggle(String label, boolean value, java.util.function.Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(value)
                .create(0, 0, 200, 20, Component.literal(label), (button, newValue) -> {
                    setter.accept(newValue);
                    BlockOutlines.configChanged();
                });
    }

    private <T> AbstractWidget cycle(String label, T[] values, T current,
                                     java.util.function.Function<T, String> naming,
                                     java.util.function.Consumer<T> setter) {
        return CycleButton.builder((T value) -> Component.literal(naming.apply(value)), current)
                .withValues(values)
                .create(0, 0, 200, 20, Component.literal(label), (button, newValue) -> {
                    setter.accept(newValue);
                    BlockOutlines.configChanged();
                });
    }

    private Button colorButton(String label, IntSupplier getter, IntConsumer setter) {
        return Button.builder(Component.literal(label), button -> {
            saveScroll();
            minecraft.setScreen(new ColorPickerScreen(this, label, getter.getAsInt(), color -> {
                setter.accept(color);
                BlockOutlines.configChanged();
            }));
        }).build();
    }

    private void startListening(Binding binding) {
        listening = binding;
        updateKeyLabels();
    }

    private void updateKeyLabels() {
        BlockOutlinesConfig config = BlockOutlines.config();
        if (toggleKeyButton != null) {
            toggleKeyButton.setMessage(listening == Binding.TOGGLE
                    ? Component.literal("Toggle key: press a key...").withStyle(ChatFormatting.YELLOW)
                    : keyLabel("Toggle key", config.toggleKey));
        }
        if (menuKeyButton != null) {
            menuKeyButton.setMessage(listening == Binding.MENU
                    ? Component.literal("Menu key: press a key...").withStyle(ChatFormatting.YELLOW)
                    : keyLabel("Menu key", config.menuKey));
        }
    }

    private static Component keyLabel(String label, int key) {
        String name = key == -1
                ? "none"
                : InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        return Component.literal(label + ": " + name);
    }

    // -------------------------------------------------------------------- screen
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (listening != null) {
            int key = keyEvent.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : keyEvent.key();
            BlockOutlinesConfig config = BlockOutlines.config();
            if (listening == Binding.TOGGLE) {
                config.toggleKey = key;
            } else {
                config.menuKey = key;
            }
            listening = null;
            BlockOutlines.configChanged();
            updateKeyLabels();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        BlockOutlines.config().save();
        minecraft.setScreen(parent);
    }
}
