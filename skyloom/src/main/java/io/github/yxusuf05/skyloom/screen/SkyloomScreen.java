package io.github.yxusuf05.skyloom.screen;

import io.github.yxusuf05.skyloom.Skyloom;
import io.github.yxusuf05.skyloom.SkyloomConfig;
import io.github.yxusuf05.skyloom.sky.SkyCatalog;
import io.github.yxusuf05.skyloom.sky.SkyDownloads;
import io.github.yxusuf05.skyloom.sky.SkyPack;
import io.github.yxusuf05.skyloom.sky.SkyTexture;
import io.github.yxusuf05.skyloom.sky.SkyRegistry;
import io.github.yxusuf05.skyloom.sky.render.SkyFace;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The sky picker: one floating surface holding a category rail and a grid of full bleed
 * previews. The world stays visible behind it so a sky can be judged while it is picked.
 */
public class SkyloomScreen extends Screen {
    private static final String ALL_CATEGORIES = "All";

    private static final int SURFACE = 0xF00D0D11;
    private static final int SURFACE_RAISED = 0xFF17171E;
    private static final int BORDER = 0x1AFFFFFF;
    private static final int TEXT = 0xFFF3F3F6;
    private static final int MUTED = 0xFF83838F;
    private static final int SCRIM = 0x66000000;

    /** The layout is authored for this size and shrinks as a whole below it, never reflows. */
    private static final int DESIGN_WIDTH = 640;
    private static final int DESIGN_HEIGHT = 400;

    private static final int RADIUS = 10;
    private static final int PADDING = 22;
    private static final int RAIL_WIDTH = 128;
    private static final int MIN_CARD_WIDTH = 148;
    private static final int CARD_GAP = 14;
    private static final float APPEAR_TIME = 0.16f;

    private static SkyloomScreen instance;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<String, Float> hovers = new HashMap<>();
    private final Map<String, SkyTexture> previews = new HashMap<>();

    private final Map<String, SliderTrack> sliders = new HashMap<>();

    private Tab tab = Tab.SKIES;
    private String draggingSlider;
    private String category = ALL_CATEGORIES;
    private String search = "";
    private String hoveredDescription;
    private float scroll;
    private float targetScroll;
    private float uiScale = 1.0f;
    private float appear;
    private float frameSeconds;
    private long lastFrame;
    private boolean listeningForBind;

    private SkyloomScreen() {
        super(Component.literal("Skyloom"));
    }

    public static SkyloomScreen getInstance() {
        if (instance == null) instance = new SkyloomScreen();
        return instance;
    }

    @Override
    protected void init() {
        this.appear = 0.0f;
        this.lastFrame = 0L;
    }

    // -----------------------------------------------------------------------------------------
    // rendering
    // -----------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        advanceAnimations();
        this.hotspots.clear();
        this.hoveredDescription = null;

        this.uiScale = Math.min(1.0f, Math.min(this.width / (float) DESIGN_WIDTH, this.height / (float) DESIGN_HEIGHT));
        int viewWidth = Math.round(this.width / this.uiScale);
        int viewHeight = Math.round(this.height / this.uiScale);
        mouseX = Math.round(mouseX / this.uiScale);
        mouseY = Math.round(mouseY / this.uiScale);

        context.pose().pushMatrix();
        context.pose().scale(this.uiScale, this.uiScale);

        int width = Math.min(1020, viewWidth - 56);
        int height = Math.min(620, viewHeight - 56);
        int left = (viewWidth - width) / 2;
        int top = (viewHeight - height) / 2 + Math.round((1.0f - this.appear) * 14.0f);

        context.fill(0, 0, viewWidth, viewHeight, fade(SCRIM));
        for (int i = 7; i > 0; i--) {
            roundedRect(context, left - i, top - i + 2, width + i * 2, height + i * 2, RADIUS + i, fade(0x09000000));
        }
        roundedRect(context, left, top, width, height, RADIUS, fade(SURFACE));
        roundedOutline(context, left, top, width, height, RADIUS, fade(BORDER));

        int contentLeft = left + PADDING;
        int contentRight = left + width - PADDING;
        int contentTop = top + PADDING;
        int contentBottom = top + height - PADDING;

        int footerTop = contentBottom - 26;
        int bodyTop = contentTop + 54;
        int bodyBottom = footerTop - 16;

        int paneLeft = contentLeft + RAIL_WIDTH + 18;
        renderRail(context, mouseX, mouseY, contentLeft, bodyTop, bodyBottom);
        switch (this.tab) {
            case SKIES -> renderGrid(context, mouseX, mouseY, getVisibleSkies(), paneLeft, bodyTop, contentRight, bodyBottom);
            case BROWSE -> renderBrowse(context, mouseX, mouseY, paneLeft, bodyTop, contentRight, bodyBottom);
            case SETTINGS -> renderSettings(context, mouseX, mouseY, paneLeft, bodyTop, contentRight, bodyBottom);
        }
        renderHeader(context, mouseX, mouseY, contentLeft, contentTop, contentRight);
        renderFooter(context, mouseX, mouseY, contentLeft, footerTop, contentRight);

        context.pose().popMatrix();
    }

    private void renderHeader(GuiGraphics context, int mouseX, int mouseY, int left, int top, int right) {
        context.pose().pushMatrix();
        context.pose().scale(1.5f, 1.5f);
        context.drawString(this.font, "Skyloom", Math.round(left / 1.5f), Math.round(top / 1.5f), fade(TEXT), false);
        context.pose().popMatrix();

        SkyPack active = SkyRegistry.getActive();
        String subtitle = this.hoveredDescription != null ? this.hoveredDescription
                : this.tab == Tab.SETTINGS ? "Trim the sky down to what you want to see"
                : this.tab == Tab.BROWSE ? "Pick something new, it downloads straight into your library"
                : active != null ? "Active: " + active.getName()
                : "Pick a sky, or keep the vanilla one";
        context.drawString(this.font, this.font.plainSubstrByWidth(subtitle, right - left - 230), left, top + 22, fade(MUTED), false);

        if (this.tab == Tab.SETTINGS) return;

        int searchWidth = Math.min(210, (right - left) / 3);
        int searchX = right - searchWidth;
        boolean typing = !this.search.isEmpty();
        roundedRect(context, searchX, top - 2, searchWidth, 26, 13, fade(SURFACE_RAISED));
        roundedOutline(context, searchX, top - 2, searchWidth, 26, 13, fade(typing ? accent(0xFF) : BORDER));
        String text = typing ? this.search : "Type to search";
        context.drawString(this.font, this.font.plainSubstrByWidth(text, searchWidth - 24), searchX + 12, top + 6,
                fade(typing ? TEXT : MUTED), false);
    }

    private void renderRail(GuiGraphics context, int mouseX, int mouseY, int left, int top, int bottom) {
        top = renderTabs(context, mouseX, mouseY, left, top) + 14;
        if (this.tab == Tab.SETTINGS) return;

        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        if (this.tab == Tab.BROWSE) {
            for (SkyCatalog.Entry entry : SkyCatalog.getEntries()) {
                if (!categories.contains(entry.category())) categories.add(entry.category());
            }
        } else {
            categories.addAll(SkyRegistry.categories());
        }

        int y = top;
        for (String name : categories) {
            if (y + 28 > bottom) break;
            boolean selected = name.equals(this.category);
            float hover = hover("rail:" + name, contains(mouseX, mouseY, left, y, RAIL_WIDTH, 28));

            if (selected) {
                roundedRect(context, left, y, RAIL_WIDTH, 28, 8, fade(accent(0xE0)));
            } else if (hover > 0.01f) {
                roundedRect(context, left, y, RAIL_WIDTH, 28, 8, fade(ARGB.color(Math.round(hover * 22.0f), 0xFFFFFF)));
            }

            int count = countIn(name);
            context.drawString(this.font, this.font.plainSubstrByWidth(name, RAIL_WIDTH - 34), left + 12, y + 10,
                    fade(selected ? 0xFFFFFFFF : mix(MUTED, TEXT, hover)), false);
            context.drawString(this.font, String.valueOf(count), left + RAIL_WIDTH - 12 - this.font.width(String.valueOf(count)),
                    y + 10, fade(selected ? 0xB0FFFFFF : 0xFF5D5D68), false);

            this.hotspots.add(new Hotspot(left, y, RAIL_WIDTH, 28, () -> {
                this.category = name;
                this.targetScroll = 0.0f;
                this.scroll = 0.0f;
            }));
            y += 32;
        }
    }

    /**
     * The tab rail. The selected pill slides between entries instead of blinking over, which is
     * what sells it as a switch rather than a set of buttons.
     *
     * @return the bottom edge of the rail
     */
    private int renderTabs(GuiGraphics context, int mouseX, int mouseY, int left, int top) {
        Tab[] tabs = Tab.values();
        int height = 26;
        int step = height + 4;

        float slide = slide("tab", this.tab.ordinal());
        roundedRect(context, left, top + Math.round(slide * step), RAIL_WIDTH, height, 8, fade(accent(0xE6)));

        for (Tab value : tabs) {
            int y = top + value.ordinal() * step;
            boolean selected = this.tab == value;
            float hover = hover("tab:" + value, contains(mouseX, mouseY, left, y, RAIL_WIDTH, height));
            if (!selected && hover > 0.01f) {
                roundedRect(context, left, y, RAIL_WIDTH, height, 8, fade(ARGB.color(Math.round(hover * 18.0f), 0xFFFFFF)));
            }
            context.drawString(this.font, value.label, left + 12, y + 9,
                    fade(selected ? 0xFFFFFFFF : mix(MUTED, TEXT, hover)), false);

            // downloads land in My skies, so say so while any are running
            int running = value == Tab.SKIES ? SkyDownloads.getActiveCount() : 0;
            if (running > 0) {
                String badge = running + " v";
                int badgeWidth = this.font.width(badge) + 10;
                int badgeX = left + RAIL_WIDTH - badgeWidth - 8;
                roundedRect(context, badgeX, y + 6, badgeWidth, 14, 7, fade(accent(selected ? 0x80 : 0xFF)));
                context.drawString(this.font, badge, badgeX + 5, y + 9, fade(0xFFFFFFFF), false);
            }
            this.hotspots.add(new Hotspot(left, y, RAIL_WIDTH, height, () -> {
                this.tab = value;
                this.scroll = 0.0f;
                this.targetScroll = 0.0f;
                if (value == Tab.BROWSE) SkyCatalog.ensureLoaded();
            }));
        }
        return top + tabs.length * step - 4;
    }

    private void renderSettings(GuiGraphics context, int mouseX, int mouseY, int left, int top, int right, int bottom) {
        SkyloomConfig config = Skyloom.config();
        this.sliders.clear();
        int width = right - left;
        int viewHeight = bottom - top;

        context.enableScissor(left, top, right + 12, bottom);
        int y = top - Math.round(this.scroll);

        y = section(context, left, y, "Celestial");
        y = toggle(context, mouseX, mouseY, left, y, width, "Sun", "The vanilla sun disc",
                () -> config.hideSun, value -> config.hideSun = value, true);
        y = toggle(context, mouseX, mouseY, left, y, width, "Moon", "The moon and its phases",
                () -> config.hideMoon, value -> config.hideMoon = value, true);
        y = toggle(context, mouseX, mouseY, left, y, width, "Stars", "The vanilla star field at night",
                () -> config.hideStars, value -> config.hideStars = value, true);
        y = toggle(context, mouseX, mouseY, left, y, width, "Sunrise glow", "The orange band at dawn and dusk",
                () -> config.hideSunrise, value -> config.hideSunrise = value, true);

        y = section(context, left, y + 10, "Atmosphere");
        y = toggle(context, mouseX, mouseY, left, y, width, "Clouds", "Every cloud layer",
                () -> config.hideClouds, value -> config.hideClouds = value, true);
        y = toggle(context, mouseX, mouseY, left, y, width, "Rain and snow", "Falling weather, the sound stays",
                () -> config.hideWeather, value -> config.hideWeather = value, true);

        y = section(context, left, y + 10, "Skybox");
        y = slider(context, mouseX, mouseY, left, y, width, "Brightness", "Dims the picked sky",
                () -> config.brightness, value -> config.brightness = value, 0.0f, 1.0f, "brightness", "%.0f%%", 100.0f);
        y = toggle(context, mouseX, mouseY, left, y, width, "Turn with the day", "Let the sky follow the sun",
                () -> config.rotate, value -> config.rotate = value, false);
        if (config.rotate) {
            y = slider(context, mouseX, mouseY, left, y, width, "Turn speed", "Multiplier on that rotation",
                    () -> config.speed, value -> config.speed = value, 0.0f, 5.0f, "speed", "%.1fx", 1.0f);
        }
        y = toggle(context, mouseX, mouseY, left, y, width, "Overworld only", "Keep custom skies out of other dimensions",
                () -> config.overworldOnly, value -> config.overworldOnly = value, false);

        context.disableScissor();

        int contentHeight = y + Math.round(this.scroll) - top;
        float maxScroll = Math.max(0.0f, contentHeight - viewHeight);
        this.targetScroll = Mth.clamp(this.targetScroll, 0.0f, maxScroll);
        this.scroll = Mth.clamp(this.scroll, 0.0f, maxScroll);
        if (maxScroll > 0.0f) {
            int barHeight = Math.max(28, Math.round(viewHeight * (viewHeight / (float) contentHeight)));
            int barY = top + Math.round((viewHeight - barHeight) * (this.scroll / maxScroll));
            roundedRect(context, right + 8, top, 3, viewHeight, 2, fade(0x14FFFFFF));
            roundedRect(context, right + 8, barY, 3, barHeight, 2, fade(accent(0xC0)));
        }
    }

    private int section(GuiGraphics context, int left, int y, String title) {
        context.pose().pushMatrix();
        context.pose().scale(0.8f, 0.8f);
        context.drawString(this.font, title.toUpperCase(Locale.ROOT), Math.round(left / 0.8f), Math.round((y + 4) / 0.8f),
                fade(0xFF6E6E7A), false);
        context.pose().popMatrix();
        return y + 18;
    }

    /**
     * @param inverted true for "hide x" settings, so the switch reads as "x is shown"
     */
    private int toggle(GuiGraphics context, int mouseX, int mouseY, int left, int y, int width,
                       String label, String description,
                       BooleanSupplier getter, Consumer<Boolean> setter, boolean inverted) {
        int height = 36;
        boolean on = inverted != getter.getAsBoolean();
        float hover = hover("row:" + label, contains(mouseX, mouseY, left, y, width, height));
        if (hover > 0.01f) {
            roundedRect(context, left - 6, y, width + 12, height, 8, fade(ARGB.color(Math.round(hover * 16.0f), 0xFFFFFF)));
        }

        context.drawString(this.font, label, left, y + 8, fade(TEXT), false);
        context.pose().pushMatrix();
        context.pose().scale(0.8f, 0.8f);
        context.drawString(this.font, description, Math.round(left / 0.8f), Math.round((y + 21) / 0.8f), fade(0xFF6E6E7A), false);
        context.pose().popMatrix();

        int trackWidth = 32;
        int trackHeight = 18;
        int trackX = left + width - trackWidth;
        int trackY = y + (height - trackHeight) / 2;
        float progress = slide("switch:" + label, on ? 1.0f : 0.0f);

        roundedRect(context, trackX, trackY, trackWidth, trackHeight, 9,
                fade(mix(0x33FFFFFF, accent(0xFF), progress)));
        int knob = trackHeight - 4;
        int knobX = trackX + 2 + Math.round(progress * (trackWidth - knob - 4));
        roundedRect(context, knobX, trackY + 2, knob, knob, knob / 2, fade(0xFFFFFFFF));

        this.hotspots.add(new Hotspot(left - 6, y, width + 12, height, () -> {
            setter.accept(!getter.getAsBoolean());
            Skyloom.save();
        }));
        return y + height;
    }

    private int slider(GuiGraphics context, int mouseX, int mouseY, int left, int y, int width,
                       String label, String description, Supplier<Float> getter, Consumer<Float> setter,
                       float min, float max, String key, String format, float displayScale) {
        int height = 36;
        float hover = hover("row:" + label, contains(mouseX, mouseY, left, y, width, height));
        if (hover > 0.01f) {
            roundedRect(context, left - 6, y, width + 12, height, 8, fade(ARGB.color(Math.round(hover * 16.0f), 0xFFFFFF)));
        }

        context.drawString(this.font, label, left, y + 8, fade(TEXT), false);
        context.pose().pushMatrix();
        context.pose().scale(0.8f, 0.8f);
        context.drawString(this.font, description, Math.round(left / 0.8f), Math.round((y + 21) / 0.8f), fade(0xFF6E6E7A), false);
        context.pose().popMatrix();

        int trackWidth = Math.min(96, Math.max(48, width / 3));
        int trackX = left + width - trackWidth;
        int trackY = y + height / 2 - 1;
        float progress = max <= min ? 0.0f : Mth.clamp((getter.get() - min) / (max - min), 0.0f, 1.0f);

        roundedRect(context, trackX, trackY, trackWidth, 4, 2, fade(0x33FFFFFF));
        roundedRect(context, trackX, trackY, Math.max(4, Math.round(trackWidth * progress)), 4, 2, fade(accent(0xFF)));
        int knobX = trackX + Math.round((trackWidth - 10) * progress);
        roundedRect(context, knobX, trackY - 3, 10, 10, 5, fade(0xFFFFFFFF));

        String value = String.format(Locale.ROOT, format, getter.get() * displayScale);
        context.pose().pushMatrix();
        context.pose().scale(0.8f, 0.8f);
        context.drawString(this.font, value, Math.round((trackX - 8 - this.font.width(value) * 0.8f) / 0.8f),
                Math.round((y + 13) / 0.8f), fade(MUTED), false);
        context.pose().popMatrix();

        this.sliders.put(key, new SliderTrack(trackX, trackWidth, min, max, setter));
        this.hotspots.add(new Hotspot(trackX - 6, y, trackWidth + 12, height, () -> {
            this.draggingSlider = key;
            applySlider(key, mouseX);
        }));
        return y + height;
    }

    private void applySlider(String key, int mouseX) {
        SliderTrack track = this.sliders.get(key);
        if (track == null) return;
        float progress = Mth.clamp((mouseX - track.x()) / (float) track.width(), 0.0f, 1.0f);
        track.setter().accept(track.min() + (track.max() - track.min()) * progress);
    }

    private void renderGrid(GuiGraphics context, int mouseX, int mouseY, List<SkyPack> skies,
                            int left, int top, int right, int bottom) {
        int available = right - left;
        int viewHeight = bottom - top;
        if (available < MIN_CARD_WIDTH || viewHeight < 60) return;

        if (skies.isEmpty()) {
            String message = SkyRegistry.all().isEmpty()
                    ? "No skies yet - drop a pack into the folder below and hit Reload"
                    : "Nothing matches that search";
            context.drawCenteredString(this.font, message, (left + right) / 2, top + viewHeight / 2 - 4, fade(MUTED));
            return;
        }

        int columns = Math.max(1, (available + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP));
        int cardWidth = (available - (columns - 1) * CARD_GAP) / columns;
        int cardHeight = Math.round(cardWidth * 0.66f);
        int rows = (skies.size() + columns - 1) / columns;
        int contentHeight = rows * (cardHeight + CARD_GAP) - CARD_GAP;
        float maxScroll = Math.max(0.0f, contentHeight - viewHeight);
        this.targetScroll = Mth.clamp(this.targetScroll, 0.0f, maxScroll);
        this.scroll = Mth.clamp(this.scroll, 0.0f, maxScroll);

        context.enableScissor(left, top, right, bottom);
        for (int index = 0; index < skies.size(); index++) {
            int x = left + (index % columns) * (cardWidth + CARD_GAP);
            int y = top + (index / columns) * (cardHeight + CARD_GAP) - Math.round(this.scroll);
            if (y > bottom || y + cardHeight < top) continue;
            renderCard(context, mouseX, mouseY, skies.get(index), x, y, cardWidth, cardHeight, top, bottom);
        }
        context.disableScissor();

        if (maxScroll > 0.0f) {
            int barHeight = Math.max(28, Math.round(viewHeight * (viewHeight / (float) contentHeight)));
            int barY = top + Math.round((viewHeight - barHeight) * (this.scroll / maxScroll));
            roundedRect(context, right + 8, top, 3, viewHeight, 2, fade(0x14FFFFFF));
            roundedRect(context, right + 8, barY, 3, barHeight, 2, fade(accent(0xC0)));
        }
    }

    private void renderBrowse(GuiGraphics context, int mouseX, int mouseY, int left, int top, int right, int bottom) {
        SkyCatalog.ensureLoaded();
        int viewHeight = bottom - top;
        int available = right - left;
        if (available < MIN_CARD_WIDTH || viewHeight < 60) return;

        SkyCatalog.State state = SkyCatalog.getState();
        if (state != SkyCatalog.State.READY) {
            String message = switch (state) {
                case LOADING -> "Loading the list" + ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
                case FAILED -> "Could not reach the sky list: " + SkyCatalog.getError();
                default -> "";
            };
            context.drawCenteredString(this.font, this.font.plainSubstrByWidth(message, available),
                    (left + right) / 2, top + viewHeight / 2 - 12, fade(MUTED));
            if (state == SkyCatalog.State.FAILED) {
                int width = this.font.width("Try again") + 24;
                button(context, mouseX, mouseY, (left + right) / 2 - width / 2, top + viewHeight / 2 + 4,
                        "Try again", false, SkyCatalog::refresh);
            }
            return;
        }

        List<SkyCatalog.Entry> entries = new ArrayList<>();
        String query = this.search.toLowerCase(Locale.ROOT).trim();
        for (SkyCatalog.Entry entry : SkyCatalog.getEntries()) {
            if (!ALL_CATEGORIES.equals(this.category) && !entry.category().equals(this.category)) continue;
            if (!query.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(query)
                    && !entry.category().toLowerCase(Locale.ROOT).contains(query)) continue;
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            context.drawCenteredString(this.font, "Nothing here", (left + right) / 2, top + viewHeight / 2 - 4, fade(MUTED));
            return;
        }

        int columns = Math.max(1, (available + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP));
        int cardWidth = (available - (columns - 1) * CARD_GAP) / columns;
        int cardHeight = Math.round(cardWidth * 0.66f);
        int rows = (entries.size() + columns - 1) / columns;
        int contentHeight = rows * (cardHeight + CARD_GAP) - CARD_GAP;
        float maxScroll = Math.max(0.0f, contentHeight - viewHeight);
        this.targetScroll = Mth.clamp(this.targetScroll, 0.0f, maxScroll);
        this.scroll = Mth.clamp(this.scroll, 0.0f, maxScroll);

        context.enableScissor(left, top, right, bottom);
        for (int index = 0; index < entries.size(); index++) {
            int x = left + (index % columns) * (cardWidth + CARD_GAP);
            int y = top + (index / columns) * (cardHeight + CARD_GAP) - Math.round(this.scroll);
            if (y > bottom || y + cardHeight < top) continue;
            renderCatalogCard(context, mouseX, mouseY, entries.get(index), x, y, cardWidth, cardHeight, top, bottom);
        }
        context.disableScissor();

        if (maxScroll > 0.0f) {
            int barHeight = Math.max(28, Math.round(viewHeight * (viewHeight / (float) contentHeight)));
            int barY = top + Math.round((viewHeight - barHeight) * (this.scroll / maxScroll));
            roundedRect(context, right + 8, top, 3, viewHeight, 2, fade(0x14FFFFFF));
            roundedRect(context, right + 8, barY, 3, barHeight, 2, fade(accent(0xC0)));
        }
    }

    private void renderCatalogCard(GuiGraphics context, int mouseX, int mouseY, SkyCatalog.Entry entry,
                                   int x, int y, int width, int height, int clipTop, int clipBottom) {
        boolean inside = contains(mouseX, mouseY, x, y, width, height) && mouseY >= clipTop && mouseY < clipBottom;
        float hover = hover("catalog:" + entry.id(), inside);
        y -= Math.round(hover * 3.0f);

        roundedRect(context, x, y, width, height, 8, fade(SURFACE_RAISED));

        Identifier preview = entry.thumbnailUrl() == null ? null : this.previews
                .computeIfAbsent(entry.id(), key -> SkyTexture.ofUrl(
                        Identifier.fromNamespaceAndPath("skyloom", "catalog/" + key.toLowerCase(Locale.ROOT)),
                        entry.thumbnailUrl()))
                .resolve();
        if (preview != null) {
            context.blit(preview, x, y, x + width, y + height, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            float pulse = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 1400L) / 1400.0f * Mth.TWO_PI);
            context.fill(x, y, x + width, y + height, fade(ARGB.color(Math.round(8.0f + pulse * 12.0f), 0xFFFFFF)));
        }

        context.fillGradient(x, y + height - 46, x + width, y + height, 0x00000000, fade(0xE6000000));
        if (hover > 0.01f) context.fill(x, y, x + width, y + height, ARGB.color(Math.round(hover * 26.0f), 0xFFFFFF));
        cornerMask(context, x, y, width, height, 8, fade(SURFACE));

        context.drawString(this.font, this.font.plainSubstrByWidth(entry.name(), width - 20),
                x + 10, y + height - 30, fade(0xFFFFFFFF), false);
        context.pose().pushMatrix();
        context.pose().scale(0.8f, 0.8f);
        String note = entry.size() > 0 ? entry.category() + " . " + readableSize(entry.size()) : entry.category();
        context.drawString(this.font, this.font.plainSubstrByWidth(note, Math.round((width - 20) / 0.8f)),
                Math.round((x + 10) / 0.8f), Math.round((y + height - 17) / 0.8f), fade(0xFFA8A8B4), false);
        context.pose().popMatrix();

        renderCatalogAction(context, mouseX, mouseY, entry, x, y, width, height, clipTop, clipBottom);
        if (inside && !entry.description().isEmpty()) this.hoveredDescription = entry.description();

        int hotTop = Math.max(y, clipTop);
        int hotHeight = Math.min(y + height, clipBottom) - hotTop;
        this.hotspots.add(new Hotspot(x, hotTop, width, hotHeight, () -> {
            if (SkyDownloads.getDownload(entry.id()) != null) return;   // already running
            if (SkyDownloads.isInstalled(entry.id())) return;           // the badge removes it
            SkyDownloads.install(entry);
        }));
    }

    private void renderCatalogAction(GuiGraphics context, int mouseX, int mouseY, SkyCatalog.Entry entry,
                                     int x, int y, int width, int height, int clipTop, int clipBottom) {
        SkyDownloads.Download download = SkyDownloads.getDownload(entry.id());
        boolean installed = SkyDownloads.isInstalled(entry.id());

        int badgeHeight = 16;
        int badgeY = y + 8;
        boolean visible = badgeY >= clipTop && badgeY + badgeHeight <= clipBottom;

        if (download != null && download.error() == null) {
            // the whole card becomes the progress indicator, a badge sized one is too easy to miss
            context.fill(x, y, x + width, y + height, fade(0xB0000000));

            float progress = download.progress();
            int barWidth = width - 32;
            int barX = x + 16;
            int barY = y + height / 2 + 4;
            roundedRect(context, barX, barY, barWidth, 6, 3, fade(0x40FFFFFF));
            if (progress < 0.0f) {
                // no length from the server, so a sliding piece instead of a filling bar
                float slide = (System.currentTimeMillis() % 1200L) / 1200.0f;
                int pieceWidth = Math.max(20, barWidth / 4);
                int pieceX = barX + Math.round((barWidth - pieceWidth) * (0.5f - 0.5f * Mth.cos(slide * Mth.TWO_PI)));
                roundedRect(context, pieceX, barY, pieceWidth, 6, 3, fade(accent(0xFF)));
            } else {
                roundedRect(context, barX, barY, Math.max(6, Math.round(barWidth * progress)), 6, 3, fade(accent(0xFF)));
            }

            String label = progress < 0.0f
                    ? readableSize(download.done()) + " loaded"
                    : Math.round(progress * 100.0f) + "%  of  " + readableSize(entry.size());
            context.drawCenteredString(this.font, label, x + width / 2, y + height / 2 - 12, fade(0xFFFFFFFF));
            String hint = "lands in My skies when it is done";
            context.pose().pushMatrix();
            context.pose().scale(0.8f, 0.8f);
            context.drawCenteredString(this.font, hint,
                    Math.round((x + width / 2) / 0.8f), Math.round((barY + 14) / 0.8f), fade(0xFFA8A8B4));
            context.pose().popMatrix();
            return;
        }

        String label = download != null ? "Failed" : installed ? "Installed" : "Get";
        int badgeWidth = this.font.width(label) + 16;
        int badgeX = x + width - badgeWidth - 8;
        boolean hovered = visible && contains(mouseX, mouseY, badgeX, badgeY, badgeWidth, badgeHeight);
        if (hovered && installed) {
            label = "Remove";
            badgeWidth = this.font.width(label) + 16;
            badgeX = x + width - badgeWidth - 8;
        }

        int background = download != null ? 0xFFB4453C : installed ? (hovered ? 0xFFB4453C : 0x66000000) : accent(0xFF);
        roundedRect(context, badgeX, badgeY, badgeWidth, badgeHeight, 8, fade(background));
        context.drawString(this.font, label, badgeX + 8, badgeY + 4, fade(0xFFFFFFFF), false);

        if (!visible) return;
        String action = label;
        this.hotspots.add(new Hotspot(badgeX, badgeY, badgeWidth, badgeHeight, () -> {
            if (download != null) {
                download.dismiss();
            } else if (action.equals("Remove")) {
                SkyDownloads.remove(entry.id());
            } else if (!installed) {
                SkyDownloads.install(entry);
            }
        }));
    }

    private static String readableSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
        if (bytes >= 1024L * 1024L) return Math.round(bytes / 1024.0 / 1024.0) + " MB";
        return Math.max(1L, bytes / 1024L) + " KB";
    }

    private void renderCard(GuiGraphics context, int mouseX, int mouseY, SkyPack pack,
                            int x, int y, int width, int height, int clipTop, int clipBottom) {
        boolean inside = contains(mouseX, mouseY, x, y, width, height) && mouseY >= clipTop && mouseY < clipBottom;
        float hover = hover("card:" + pack.getId(), inside);
        boolean selected = SkyRegistry.isActive(pack);

        int lift = Math.round(hover * 3.0f);
        y -= lift;

        roundedRect(context, x, y, width, height, 8, fade(SURFACE_RAISED));

        // the north face reads like a horizon shot, which is what a thumbnail wants to be
        SkyPack.Preview preview = pack.getPreview();
        Identifier previewTexture = preview == null ? null : preview.texture().resolve();
        if (previewTexture != null) {
            context.blit(previewTexture, x, y, x + width, y + height,
                    preview.minU(), preview.maxU(), preview.minV(), preview.maxV());
        } else {
            // still decoding in the background, a pulse reads better than an empty hole
            float pulse = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 1400L) / 1400.0f * Mth.TWO_PI);
            context.fill(x, y, x + width, y + height, fade(ARGB.color(Math.round(8.0f + pulse * 12.0f), 0xFFFFFF)));
        }

        int scrimTop = y + height - 46;
        context.fillGradient(x, scrimTop, x + width, y + height, 0x00000000, fade(0xE6000000));
        if (hover > 0.01f) {
            context.fill(x, y, x + width, y + height, ARGB.color(Math.round(hover * 26.0f), 0xFFFFFF));
        }
        cornerMask(context, x, y, width, height, 8, fade(SURFACE));

        context.drawString(this.font, this.font.plainSubstrByWidth(pack.getName(), width - 20),
                x + 10, y + height - 30, fade(0xFFFFFFFF), false);

        context.pose().pushMatrix();
        context.pose().scale(0.8f, 0.8f);
        String note = pack.isExternal() ? pack.getCategory() + " . local" : pack.getCategory();
        context.drawString(this.font, this.font.plainSubstrByWidth(note, Math.round((width - 20) / 0.8f)),
                Math.round((x + 10) / 0.8f), Math.round((y + height - 17) / 0.8f), fade(0xFFA8A8B4), false);
        context.pose().popMatrix();

        if (selected) {
            roundedOutline(context, x, y, width, height, 8, fade(accent(0xFF)));
            int badge = this.font.width("Active") + 16;
            roundedRect(context, x + width - badge - 8, y + 8, badge, 16, 8, fade(accent(0xFF)));
            context.drawString(this.font, "Active", x + width - badge, y + 12, fade(0xFFFFFFFF), false);
        }

        if (inside) this.hoveredDescription = pack.getDescription().isEmpty() ? null : pack.getDescription();

        int hotTop = Math.max(y, clipTop);
        this.hotspots.add(new Hotspot(x, hotTop, width, Math.min(y + height, clipBottom) - hotTop,
                () -> SkyRegistry.setActive(selected ? null : pack)));
    }

    private void renderFooter(GuiGraphics context, int mouseX, int mouseY, int left, int top, int right) {
        context.fill(left, top - 16, right, top - 15, fade(BORDER));

        int x = left;
        x = button(context, mouseX, mouseY, x, top, "Turn off", false, () -> SkyRegistry.setActive(null));
        x = button(context, mouseX, mouseY, x, top, "Rescan", false, () -> {
            SkyRegistry.reload();
            this.scroll = 0.0f;
            this.targetScroll = 0.0f;
        });
        if (this.tab == Tab.BROWSE) {
            x = button(context, mouseX, mouseY, x, top, "Refresh list", false, SkyCatalog::refresh);
        }
        String bind = this.listeningForBind ? "press a key" : bindName();
        button(context, mouseX, mouseY, x, top, "Key  " + bind, this.listeningForBind,
                () -> this.listeningForBind = !this.listeningForBind);
    }

    private int button(GuiGraphics context, int mouseX, int mouseY, int x, int y, String label, boolean active, Runnable action) {
        int width = this.font.width(label) + 24;
        float hover = hover("button:" + label, contains(mouseX, mouseY, x, y, width, 26));
        int background = active ? accent(0xFF) : ARGB.color(Math.round(18.0f + hover * 22.0f), 0xFFFFFF);
        roundedRect(context, x, y, width, 26, 8, fade(background));
        if (!active) roundedOutline(context, x, y, width, 26, 8, fade(BORDER));
        context.drawString(this.font, label, x + 12, y + 9, fade(active ? 0xFFFFFFFF : mix(MUTED, TEXT, hover)), false);
        this.hotspots.add(new Hotspot(x, y, width, 26, action));
        return x + width + 8;
    }

    // -----------------------------------------------------------------------------------------
    // input
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int mouseX = Math.round((float) click.x() / this.uiScale);
            int mouseY = Math.round((float) click.y() / this.uiScale);
            for (int i = this.hotspots.size() - 1; i >= 0; i--) {
                Hotspot hotspot = this.hotspots.get(i);
                if (!hotspot.contains(mouseX, mouseY)) continue;
                hotspot.action().run();
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (this.draggingSlider != null) {
            applySlider(this.draggingSlider, Math.round((float) click.x() / this.uiScale));
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (this.draggingSlider != null) Skyloom.save();
        this.draggingSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.targetScroll -= (float) verticalAmount * 46.0f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (this.listeningForBind) {
            int key = input.key();
            setBind(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE
                    ? GLFW.GLFW_KEY_UNKNOWN : key);
            this.listeningForBind = false;
            return true;
        }
        if (this.search.isEmpty() && Skyloom.openKey().matches(input)) {
            this.onClose();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && !this.search.isEmpty()) {
            this.search = "";
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (this.listeningForBind) return true;
        if (this.tab == Tab.SETTINGS) return true;
        this.search += input.codepointAsString();
        this.targetScroll = 0.0f;
        return true;
    }

    @Override
    public void onClose() {
        this.listeningForBind = false;
        Skyloom.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override // the world and the sky stay visible, no vanilla blur and no dim
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    // -----------------------------------------------------------------------------------------
    // animation
    // -----------------------------------------------------------------------------------------

    private void advanceAnimations() {
        long now = System.nanoTime();
        float seconds = this.lastFrame == 0L ? 0.0f : Math.min(0.1f, (now - this.lastFrame) / 1.0E9f);
        this.lastFrame = now;

        this.appear = Math.min(1.0f, this.appear + (APPEAR_TIME <= 0.0f ? 1.0f : seconds / APPEAR_TIME));
        this.scroll += (this.targetScroll - this.scroll) * Math.min(1.0f, seconds * 16.0f);
        this.frameSeconds = seconds;
    }

    private float hover(String key, boolean hovered) {
        return ease(key, hovered ? 1.0f : 0.0f, 14.0f);
    }

    /**
     * Slower than a hover so the travel of a switch knob or a tab pill is actually readable.
     */
    private float slide(String key, float target) {
        return ease(key, target, 11.0f);
    }

    private float ease(String key, float target, float speed) {
        float current = this.hovers.getOrDefault(key, target);
        float next = current + (target - current) * Math.min(1.0f, this.frameSeconds * speed);
        if (Math.abs(target - next) < 0.001f) next = target;
        this.hovers.put(key, next);
        return next;
    }

    /**
     * Scales a colours alpha by the open animation so the whole surface fades in as one.
     */
    private int fade(int color) {
        int alpha = Math.round(ARGB.alpha(color) * easeOut(this.appear));
        return ARGB.color(alpha, color & 0xFFFFFF);
    }

    private static float easeOut(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - (1.0f - clamped) * (1.0f - clamped);
    }

    private static int mix(int from, int to, float progress) {
        return ARGB.srgbLerp(Mth.clamp(progress, 0.0f, 1.0f), from, to);
    }

    // -----------------------------------------------------------------------------------------
    // shapes
    // -----------------------------------------------------------------------------------------

    private static void roundedRect(GuiGraphics context, int x, int y, int width, int height, int radius, int color) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        context.fill(x + r, y, x + width - r, y + height, color);
        context.fill(x, y + r, x + r, y + height - r, color);
        context.fill(x + width - r, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            context.fill(x + inset, y + i, x + r, y + i + 1, color);
            context.fill(x + width - r, y + i, x + width - inset, y + i + 1, color);
            context.fill(x + inset, y + height - i - 1, x + r, y + height - i, color);
            context.fill(x + width - r, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    private static void roundedOutline(GuiGraphics context, int x, int y, int width, int height, int radius, int color) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        context.fill(x + r, y, x + width - r, y + 1, color);
        context.fill(x + r, y + height - 1, x + width - r, y + height, color);
        context.fill(x, y + r, x + 1, y + height - r, color);
        context.fill(x + width - 1, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            context.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            context.fill(x + width - inset - 1, y + i, x + width - inset, y + i + 1, color);
            context.fill(x + inset, y + height - i - 1, x + inset + 1, y + height - i, color);
            context.fill(x + width - inset - 1, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /**
     * Paints over the four square corners of an already drawn rectangle so it reads as rounded.
     */
    private static void cornerMask(GuiGraphics context, int x, int y, int width, int height, int radius, int color) {
        int r = Math.min(radius, Math.min(width, height) / 2);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            if (inset <= 0) continue;
            context.fill(x, y + i, x + inset, y + i + 1, color);
            context.fill(x + width - inset, y + i, x + width, y + i + 1, color);
            context.fill(x, y + height - i - 1, x + inset, y + height - i, color);
            context.fill(x + width - inset, y + height - i - 1, x + width, y + height - i, color);
        }
    }

    private static int cornerInset(int radius, int row) {
        double offset = radius - row - 0.5;
        return radius - (int) Math.round(Math.sqrt(Math.max(0.0, radius * radius - offset * offset)));
    }

    // -----------------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------------

    private List<SkyPack> getVisibleSkies() {
        List<SkyPack> visible = new ArrayList<>();
        String query = this.search.toLowerCase(Locale.ROOT).trim();
        for (SkyPack pack : SkyRegistry.all()) {
            if (!matches(pack, query)) continue;
            visible.add(pack);
        }
        return visible;
    }

    private boolean matches(SkyPack pack, String query) {
        if (!ALL_CATEGORIES.equals(this.category) && !pack.getCategory().equals(this.category)) return false;
        if (query.isEmpty()) return true;
        return pack.getName().toLowerCase(Locale.ROOT).contains(query)
                || pack.getCategory().toLowerCase(Locale.ROOT).contains(query);
    }

    private int countIn(String category) {
        int count = 0;
        String query = this.search.toLowerCase(Locale.ROOT).trim();
        if (this.tab == Tab.BROWSE) {
            for (SkyCatalog.Entry entry : SkyCatalog.getEntries()) {
                if (matches(entry.name(), entry.category(), category, query)) count++;
            }
            return count;
        }
        for (SkyPack pack : SkyRegistry.all()) {
            if (matches(pack.getName(), pack.getCategory(), category, query)) count++;
        }
        return count;
    }

    private static boolean matches(String name, String ownCategory, String category, String query) {
        if (!ALL_CATEGORIES.equals(category) && !ownCategory.equals(category)) return false;
        return query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)
                || ownCategory.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String bindName() {
        return Skyloom.openKey().getTranslatedKeyMessage().getString();
    }

    private static void setBind(int key) {
        Skyloom.openKey().setKey(key == GLFW.GLFW_KEY_UNKNOWN
                ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(key));
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    private static int accent(int alpha) {
        return ARGB.color(alpha, Skyloom.config().accent & 0xFFFFFF);
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum Tab {
        SKIES("My skies"),
        BROWSE("Browse"),
        SETTINGS("Settings");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record SliderTrack(int x, int width, float min, float max, Consumer<Float> setter) {
    }

    private record Hotspot(int x, int y, int width, int height, Runnable action) {
        boolean contains(int mouseX, int mouseY) {
            return this.height > 0 && mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
