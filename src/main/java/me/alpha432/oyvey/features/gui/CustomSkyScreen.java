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
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The sky picker. Categories on the left, a scrollable grid of previews on the right and the
 * sky changing the moment something is clicked, so picking one can be done at any time without
 * ever leaving the world.
 */
public class CustomSkyScreen extends Screen {
    private static final String ALL_CATEGORIES = "All";

    private static final int MARGIN = 20;
    private static final int HEADER_HEIGHT = 46;
    private static final int FOOTER_HEIGHT = 26;
    private static final int SIDEBAR_WIDTH = 92;
    private static final int CARD_WIDTH = 118;
    private static final int CARD_HEIGHT = 88;
    private static final int CARD_GAP = 8;
    private static final int PREVIEW_HEIGHT = 56;

    private static final int PANEL_COLOR = 0xC0101014;
    private static final int CARD_COLOR = 0xC01A1A20;
    private static final int CARD_HOVER_COLOR = 0xE0262630;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF9A9AA6;

    private static CustomSkyScreen instance;

    private final List<Button> buttons = new ArrayList<>();
    private String category = ALL_CATEGORIES;
    private String search = "";
    private float scroll;
    private boolean listeningForBind;

    private CustomSkyScreen() {
        super(Component.literal("Custom Skies"));
    }

    public static CustomSkyScreen getInstance() {
        if (instance == null) instance = new CustomSkyScreen();
        return instance;
    }

    // -----------------------------------------------------------------------------------------
    // rendering
    // -----------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.buttons.clear();
        List<SkyPack> visible = getVisibleSkies();

        renderHeader(context, mouseX, mouseY);
        renderSidebar(context, mouseX, mouseY);
        renderGrid(context, mouseX, mouseY, visible);
        renderFooter(context, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics context, int mouseX, int mouseY) {
        int right = this.width - MARGIN;
        context.fill(MARGIN, MARGIN, right, MARGIN + HEADER_HEIGHT - 8, PANEL_COLOR);
        context.drawString(this.font, "Custom Skies", MARGIN + 10, MARGIN + 8, TEXT_COLOR);

        SkyPack active = SkyRegistry.getActive();
        String subtitle = active == null ? "No sky selected" : "Active: " + active.getName();
        context.drawString(this.font, subtitle, MARGIN + 10, MARGIN + 21, MUTED_COLOR);

        int searchWidth = 150;
        int searchX = right - 10 - searchWidth;
        int searchY = MARGIN + 12;
        context.fill(searchX, searchY, searchX + searchWidth, searchY + 14, 0xC0000000);
        context.renderOutline(searchX, searchY, searchWidth, 14, accent(0x80));
        String text = this.search.isEmpty() ? "Search..." : this.search;
        context.drawString(this.font, this.font.plainSubstrByWidth(text, searchWidth - 8), searchX + 4, searchY + 3,
                this.search.isEmpty() ? MUTED_COLOR : TEXT_COLOR);
    }

    private void renderSidebar(GuiGraphics context, int mouseX, int mouseY) {
        int top = MARGIN + HEADER_HEIGHT;
        int bottom = this.height - MARGIN - FOOTER_HEIGHT - 6;
        context.fill(MARGIN, top, MARGIN + SIDEBAR_WIDTH, bottom, PANEL_COLOR);

        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        categories.addAll(SkyRegistry.categories());

        int y = top + 6;
        for (String name : categories) {
            boolean selected = name.equals(this.category);
            boolean hovered = contains(mouseX, mouseY, MARGIN + 4, y, SIDEBAR_WIDTH - 8, 15);
            if (selected || hovered) {
                context.fill(MARGIN + 4, y, MARGIN + SIDEBAR_WIDTH - 4, y + 15, selected ? accent(0xB0) : 0x40FFFFFF);
            }
            context.drawString(this.font, this.font.plainSubstrByWidth(name, SIDEBAR_WIDTH - 16), MARGIN + 9, y + 4,
                    selected ? TEXT_COLOR : MUTED_COLOR);
            this.buttons.add(new Button(MARGIN + 4, y, SIDEBAR_WIDTH - 8, 15, () -> {
                this.category = name;
                this.scroll = 0.0f;
            }));
            y += 17;
        }
    }

    private void renderGrid(GuiGraphics context, int mouseX, int mouseY, List<SkyPack> skies) {
        int left = MARGIN + SIDEBAR_WIDTH + 8;
        int top = MARGIN + HEADER_HEIGHT;
        int right = this.width - MARGIN;
        int bottom = this.height - MARGIN - FOOTER_HEIGHT - 6;
        int usableWidth = right - left;
        if (usableWidth < CARD_WIDTH || bottom - top < 40) return;

        context.fill(left, top, right, bottom, PANEL_COLOR);

        int columns = Math.max(1, (usableWidth - CARD_GAP) / (CARD_WIDTH + CARD_GAP));
        int rows = (skies.size() + columns - 1) / columns;
        int contentHeight = rows * (CARD_HEIGHT + CARD_GAP) + CARD_GAP;
        int viewHeight = bottom - top;
        this.scroll = Mth.clamp(this.scroll, 0.0f, Math.max(0.0f, contentHeight - viewHeight));

        if (skies.isEmpty()) {
            String message = SkyRegistry.all().isEmpty()
                    ? "No skies found. Drop a pack into " + SkyLoader.getSkiesDirectory().getFileName()
                    : "Nothing matches this filter";
            context.drawCenteredString(this.font, message, (left + right) / 2, top + viewHeight / 2 - 4, MUTED_COLOR);
            return;
        }

        context.enableScissor(left, top, right, bottom);
        for (int index = 0; index < skies.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int x = left + CARD_GAP + column * (CARD_WIDTH + CARD_GAP);
            int y = top + CARD_GAP + row * (CARD_HEIGHT + CARD_GAP) - Math.round(this.scroll);
            if (y > bottom || y + CARD_HEIGHT < top) continue;
            renderCard(context, mouseX, mouseY, skies.get(index), x, y, top, bottom);
        }
        context.disableScissor();

        if (contentHeight > viewHeight) {
            int barHeight = Math.max(20, viewHeight * viewHeight / contentHeight);
            int barY = top + Math.round((viewHeight - barHeight) * (this.scroll / (contentHeight - viewHeight)));
            context.fill(right - 4, top, right - 1, bottom, 0x40000000);
            context.fill(right - 4, barY, right - 1, barY + barHeight, accent(0xC0));
        }
    }

    private void renderCard(GuiGraphics context, int mouseX, int mouseY, SkyPack pack, int x, int y, int clipTop, int clipBottom) {
        boolean hovered = contains(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT)
                && mouseY >= clipTop && mouseY <= clipBottom;
        boolean selected = SkyRegistry.isActive(pack);

        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, hovered ? CARD_HOVER_COLOR : CARD_COLOR);

        Identifier preview = pack.getPreview();
        if (preview != null) {
            // the north face of the sheet reads best as a thumbnail, it is what you look at
            context.blit(preview, x + 4, y + 4, x + CARD_WIDTH - 4, y + 4 + PREVIEW_HEIGHT,
                    SkyFace.NORTH.getMinU(), SkyFace.NORTH.getMaxU(), SkyFace.NORTH.getMinV(), SkyFace.NORTH.getMaxV());
        }

        context.drawString(this.font, this.font.plainSubstrByWidth(pack.getName(), CARD_WIDTH - 10),
                x + 5, y + PREVIEW_HEIGHT + 9, TEXT_COLOR);
        String note = pack.isExternal() ? pack.getCategory() + " - local" : pack.getCategory();
        context.drawString(this.font, this.font.plainSubstrByWidth(note, CARD_WIDTH - 10),
                x + 5, y + PREVIEW_HEIGHT + 21, MUTED_COLOR);

        if (selected) context.renderOutline(x, y, CARD_WIDTH, CARD_HEIGHT, accent(0xFF));

        this.buttons.add(new Button(x, Math.max(y, clipTop), CARD_WIDTH, Math.min(y + CARD_HEIGHT, clipBottom) - Math.max(y, clipTop), () -> {
            SkyRegistry.setActive(selected ? null : pack);
        }));

        if (hovered && !pack.getDescription().isEmpty()) {
            context.setTooltipForNextFrame(this.font, Component.literal(pack.getDescription()), mouseX, mouseY);
        }
    }

    private void renderFooter(GuiGraphics context, int mouseX, int mouseY) {
        int y = this.height - MARGIN - FOOTER_HEIGHT;
        int right = this.width - MARGIN;
        context.fill(MARGIN, y, right, y + FOOTER_HEIGHT, PANEL_COLOR);

        int x = MARGIN + 6;
        x = footerButton(context, mouseX, mouseY, x, y + 5, "Turn off", () -> SkyRegistry.setActive(null));
        x = footerButton(context, mouseX, mouseY, x, y + 5, "Reload", () -> {
            SkyRegistry.reload();
            this.scroll = 0.0f;
        });

        String bind = this.listeningForBind ? "press a key..." : KeyboardUtil.getKeyName(getModuleBind());
        x = footerButton(context, mouseX, mouseY, x, y + 5, "Key: " + bind, () -> this.listeningForBind = !this.listeningForBind);

        int available = right - 6 - x;
        if (available > 40) {
            String hint = this.font.plainSubstrByWidth(SkyLoader.getSkiesDirectory().toString(), available);
            context.drawString(this.font, hint, right - 6 - this.font.width(hint), y + 9, MUTED_COLOR);
        }
    }

    private int footerButton(GuiGraphics context, int mouseX, int mouseY, int x, int y, String label, Runnable action) {
        int width = this.font.width(label) + 12;
        boolean hovered = contains(mouseX, mouseY, x, y, width, 16);
        context.fill(x, y, x + width, y + 16, hovered ? accent(0xC0) : 0x50FFFFFF);
        context.drawString(this.font, label, x + 6, y + 4, TEXT_COLOR);
        this.buttons.add(new Button(x, y, width, 16, action));
        return x + width + 6;
    }

    // -----------------------------------------------------------------------------------------
    // input
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            for (Button button : this.buttons) {
                if (button.contains((int) click.x(), (int) click.y())) {
                    button.action().run();
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scroll -= (float) verticalAmount * 24.0f;
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
        String typed = input.codepointAsString();
        if (!typed.isBlank() || typed.equals(" ")) {
            this.search += typed;
            this.scroll = 0.0f;
            return true;
        }
        return super.charTyped(input);
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

    @Override // keep the world and the sky visible while picking, no blur and no dim
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    // -----------------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------------

    private List<SkyPack> getVisibleSkies() {
        List<SkyPack> visible = new ArrayList<>();
        String query = this.search.toLowerCase(Locale.ROOT);
        for (SkyPack pack : SkyRegistry.all()) {
            if (!ALL_CATEGORIES.equals(this.category) && !pack.getCategory().equals(this.category)) continue;
            if (!query.isEmpty() && !pack.getName().toLowerCase(Locale.ROOT).contains(query)
                    && !pack.getCategory().toLowerCase(Locale.ROOT).contains(query)) continue;
            visible.add(pack);
        }
        return visible;
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
        int rgb = clickGui == null ? 0x2E6FF2 : clickGui.color.getValue().getRGB() & 0xFFFFFF;
        return (alpha << 24) | rgb;
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Button(int x, int y, int width, int height, Runnable action) {
        boolean contains(int mouseX, int mouseY) {
            return this.height > 0 && mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
