package me.alpha432.oyvey.features.modules.hud;

import me.alpha432.oyvey.event.impl.input.MouseInputEvent;
import me.alpha432.oyvey.event.impl.render.Render2DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.client.HudModule;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Deque;

public class CpsHudModule extends HudModule {
    public final Setting<Boolean> background = bool("Background", true);
    public final Setting<Boolean> rightClick = bool("RightClick", true);

    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();

    public CpsHudModule() {
        super("CPS", "Displays your clicks per second", 40, 12);
    }

    @Subscribe
    public void onMouseCps(MouseInputEvent e) {
        if (e.getAction() != 1) return;
        if (e.getButton() == 0) leftClicks.add(System.currentTimeMillis());
        else if (e.getButton() == 1) rightClicks.add(System.currentTimeMillis());
    }

    private int count(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
        return clicks.size();
    }

    @Override
    protected void render(Render2DEvent e) {
        super.render(e);

        String text = rightClick.getValue()
                ? count(leftClicks) + " | " + count(rightClicks) + " CPS"
                : count(leftClicks) + " CPS";

        float pad = 5f;
        float height = mc.font.lineHeight + 5f;
        float width = mc.font.width(text) + pad + 5f;

        float x = getX();
        float y = getY();

        GuiGraphics context = e.getContext();
        if (background.getValue()) {
            HudUtil.panel(context, x, y, width, height);
        }
        context.drawString(mc.font, text,
                (int) (x + pad), (int) (y + (height - mc.font.lineHeight) / 2f + 1), -1);

        setWidth(width);
        setHeight(height);
    }
}
