package me.alpha432.oyvey.features.gui;

import me.alpha432.oyvey.features.modules.client.ClickGuiModule;
import me.alpha432.oyvey.features.modules.render.CustomSkyModule;
import me.alpha432.oyvey.features.settings.Bind;
import me.alpha432.oyvey.features.sky.SkyLoader;
import me.alpha432.oyvey.features.sky.SkyPack;
import me.alpha432.oyvey.features.sky.SkyRegistry;
import me.alpha432.oyvey.features.sky.render.SkyFace;
import me.alpha432.oyvey.util.KeyboardUtil;
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

/**
 * The sky picker: one floating surface holding a category rail and a grid of full bleed
 * previews. The world stays visible behind it so a sky can be judged while it is picked.
 */
public class CustomSkyScreen extends Screen {
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

    private static CustomSkyScreen instance;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<String, Float> hovers = new HashMap<>();

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

    private CustomSkyScreen() {
        super(Component.literal("Custom Skies"));
    }

    public static CustomSkyScreen getInstance() {
        if (instance == null) instance = new CustomSkyScreen();
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

        List<SkyPack> visible = getVisibleSkies();
        renderRail(context, mouseX, mouseY, contentLeft, bodyTop, bodyBottom);
        renderGrid(context, mouseX, mouseY, visible, contentLeft + RAIL_WIDTH + 18, bodyTop, contentRight, bodyBottom);
        renderHeader(context, mouseX, mouseY, contentLeft, contentTop, contentRight);
        renderFooter(context, mouseX, mouseY, contentLeft, footerTop, contentRight);

        context.pose().popMatrix();
    }

    private void renderHeader(GuiGraphics context, int mouseX, int mouseY, int left, int top, int right) {
        context.pose().pushMatrix();
        context.pose().scale(1.5f, 1.5f);
        context.drawString(this.font, "Custom Skies", Math.round(left / 1.5f), Math.round(top / 1.5f), fade(TEXT), false);
        context.pose().popMatrix();

        SkyPack active = SkyRegistry.getActive();
        String subtitle = this.hoveredDescription != null ? this.hoveredDescription
                : active != null ? "Active: " + active.getName()
                : "Pick a sky, or keep the vanilla one";
        context.drawString(this.font, this.font.plainSubstrByWidth(subtitle, right - left - 230), left, top + 22, fade(MUTED), false);

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
        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        categories.addAll(SkyRegistry.categories());

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

    private void renderCard(GuiGraphics context, int mouseX, int mouseY, SkyPack pack,
                            int x, int y, int width, int height, int clipTop, int clipBottom) {
        boolean inside = contains(mouseX, mouseY, x, y, width, height) && mouseY >= clipTop && mouseY < clipBottom;
        float hover = hover("card:" + pack.getId(), inside);
        boolean selected = SkyRegistry.isActive(pack);

        int lift = Math.round(hover * 3.0f);
        y -= lift;

        roundedRect(context, x, y, width, height, 8, fade(SURFACE_RAISED));

        Identifier preview = pack.getPreview();
        if (preview != null) {
            // the north face reads like a horizon shot, which is what a thumbnail wants to be
            context.blit(preview, x, y, x + width, y + height,
                    SkyFace.NORTH.getMinU(), SkyFace.NORTH.getMaxU(), SkyFace.NORTH.getMinV(), SkyFace.NORTH.getMaxV());
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
        x = button(context, mouseX, mouseY, x, top, "Reload", false, () -> {
            SkyRegistry.reload();
            this.scroll = 0.0f;
            this.targetScroll = 0.0f;
        });
        String bind = this.listeningForBind ? "press a key" : KeyboardUtil.getKeyName(getModuleBind());
        x = button(context, mouseX, mouseY, x, top, "Key  " + bind, this.listeningForBind,
                () -> this.listeningForBind = !this.listeningForBind);

        int available = right - x - 12;
        if (available > 60) {
            String hint = this.font.plainSubstrByWidth(SkyLoader.getSkiesDirectory().toString(), available);
            context.drawString(this.font, hint, right - this.font.width(hint), top + 9, fade(0xFF5D5D68), false);
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.targetScroll -= (float) verticalAmount * 46.0f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (this.listeningForBind) {
            int key = input.key();
            setModuleBind(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE
                    ? new Bind(GLFW.GLFW_KEY_UNKNOWN) : new Bind(key));
            this.listeningForBind = false;
            return true;
        }
        if (this.search.isEmpty() && input.key() == getModuleBind().getKey()) {
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
        this.search += input.codepointAsString();
        this.targetScroll = 0.0f;
        return true;
    }

    @Override
    public void onClose() {
        this.listeningForBind = false;
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
        float current = this.hovers.getOrDefault(key, 0.0f);
        float target = hovered ? 1.0f : 0.0f;
        float next = current + (target - current) * Math.min(1.0f, this.frameSeconds * 14.0f);
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
        for (SkyPack pack : SkyRegistry.all()) {
            boolean inCategory = ALL_CATEGORIES.equals(category) || pack.getCategory().equals(category);
            boolean inSearch = query.isEmpty() || pack.getName().toLowerCase(Locale.ROOT).contains(query)
                    || pack.getCategory().toLowerCase(Locale.ROOT).contains(query);
            if (inCategory && inSearch) count++;
        }
        return count;
    }

    private Bind getModuleBind() {
        CustomSkyModule module = CustomSkyModule.getInstance();
        return module == null ? new Bind(GLFW.GLFW_KEY_UNKNOWN) : module.getBind();
    }

    private void setModuleBind(Bind bind) {
        CustomSkyModule module = CustomSkyModule.getInstance();
        if (module != null) module.bind.setValue(bind);
    }

    private static int accent(int alpha) {
        ClickGuiModule clickGui = ClickGuiModule.getInstance();
        int rgb = clickGui == null ? 0x4C6FFF : clickGui.color.getValue().getRGB() & 0xFFFFFF;
        return ARGB.color(alpha, rgb);
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Hotspot(int x, int y, int width, int height, Runnable action) {
        boolean contains(int mouseX, int mouseY) {
            return this.height > 0 && mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
