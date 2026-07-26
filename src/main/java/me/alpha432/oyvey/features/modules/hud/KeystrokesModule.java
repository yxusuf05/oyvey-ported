package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.input.MouseInputEvent;
import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.client.HudModule;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.AnimationUtil;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lunar/Feather-style keystrokes overlay. Each key smoothly fades toward the client accent
 * color when held and back to the panel color when released, driven by a frame-rate
 * independent animator so the motion looks identical at any FPS.
 */
public class KeystrokesModule extends HudModule {
    private static final Color IDLE_BG = new Color(20, 20, 26, 160);
    private static final Color IDLE_TEXT = new Color(175, 175, 185);
    private static final Color PRESSED_TEXT = new Color(255, 255, 255);

    public final Setting<Boolean> mouseButtons = bool("MouseButtons", true);
    public final Setting<Boolean> spaceBar = bool("SpaceBar", true);
    public final Setting<Boolean> showCps = bool("ShowCps", true);
    public final Setting<Float> speed = num("AnimSpeed", 14f, 3f, 30f);

    private final AnimationUtil.Animation animW = new AnimationUtil.Animation(0f, 14f);
    private final AnimationUtil.Animation animA = new AnimationUtil.Animation(0f, 14f);
    private final AnimationUtil.Animation animS = new AnimationUtil.Animation(0f, 14f);
    private final AnimationUtil.Animation animD = new AnimationUtil.Animation(0f, 14f);
    private final AnimationUtil.Animation animLmb = new AnimationUtil.Animation(0f, 14f);
    private final AnimationUtil.Animation animRmb = new AnimationUtil.Animation(0f, 14f);
    private final AnimationUtil.Animation animSpace = new AnimationUtil.Animation(0f, 14f);

    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();

    private static final float KEY = 20f;
    private static final float GAP = 2f;

    public KeystrokesModule() {
        super("Keystrokes", "Animated WASD + mouse key display", KEY * 3 + GAP * 2, KEY * 3 + GAP * 3 + 12f);
    }

    @Subscribe
    public void onMouseKeystrokes(MouseInputEvent e) {
        if (e.getAction() != 1) return;
        if (e.getButton() == 0) leftClicks.add(System.currentTimeMillis());
        else if (e.getButton() == 1) rightClicks.add(System.currentTimeMillis());
    }

    private int cps(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
        return clicks.size();
    }

    private boolean down(KeyMapping mapping) {
        return mapping != null && mapping.isDown();
    }

    private void key(GuiGraphics context, AnimationUtil.Animation anim, boolean pressed,
                     float x, float y, float w, float h, String label) {
        anim.setSpeed(speed.getValue());
        anim.setTarget(pressed ? 1f : 0f);
        float progress = AnimationUtil.easeOutCubic(anim.update());

        Color accent = ColorUtil.withAlpha(OyVey.colorManager.getColor(), 210);
        Color bg = ColorUtil.interpolate(IDLE_BG, accent, progress);
        Color textColor = ColorUtil.interpolate(IDLE_TEXT, PRESSED_TEXT, progress);

        RenderUtil.rect(context, x, y, x + w, y + h, bg.getRGB());

        if (label != null && !label.isEmpty()) {
            float textX = x + (w - mc.font.width(label)) / 2f;
            float textY = y + (h - mc.font.lineHeight) / 2f + 1f;
            context.drawString(mc.font, label, (int) textX, (int) textY, textColor.getRGB());
        }
    }

    @Override
    protected void render(Render2DEvent e) {
        super.render(e);
        if (mc.options == null) return;

        float x = getX();
        float y = getY();
        GuiGraphics context = e.getContext();

        String lmbLabel = showCps.getValue() ? String.valueOf(cps(leftClicks)) : "L";
        String rmbLabel = showCps.getValue() ? String.valueOf(cps(rightClicks)) : "R";

        // Row 0: W (centered)
        key(context, animW, down(mc.options.keyUp), x + KEY + GAP, y, KEY, KEY, "W");
        // Row 1: A S D
        float row1 = y + KEY + GAP;
        key(context, animA, down(mc.options.keyLeft), x, row1, KEY, KEY, "A");
        key(context, animS, down(mc.options.keyDown), x + KEY + GAP, row1, KEY, KEY, "S");
        key(context, animD, down(mc.options.keyRight), x + (KEY + GAP) * 2, row1, KEY, KEY, "D");

        float totalWidth = KEY * 3 + GAP * 2;
        float bottom = row1 + KEY + GAP;

        // Row 2: mouse buttons
        if (mouseButtons.getValue()) {
            float mouseW = (totalWidth - GAP) / 2f;
            key(context, animLmb, down(mc.options.keyAttack), x, bottom, mouseW, KEY, lmbLabel);
            key(context, animRmb, down(mc.options.keyUse), x + mouseW + GAP, bottom, mouseW, KEY, rmbLabel);
            bottom += KEY + GAP;
        }

        // Row 3: space bar
        if (spaceBar.getValue()) {
            key(context, animSpace, down(mc.options.keyJump), x, bottom, totalWidth, 12f, "____");
            bottom += 12f;
        }

        setWidth(totalWidth);
        setHeight(bottom - y);
    }
}
