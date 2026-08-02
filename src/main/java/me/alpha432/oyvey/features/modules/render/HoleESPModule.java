package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.manager.HoleManager;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.world.phys.AABB;

import java.awt.Color;

/**
 * Renders the safe holes around you (already computed each tick by {@link HoleManager}). Full-bedrock
 * holes and one-obsidian ("unsafe") holes get their own colour so you can pick where to stand in
 * crystal PvP at a glance.
 */
public class HoleESPModule extends Module {
    public final Setting<Boolean> box = bool("Outline", true);
    public final Setting<Boolean> fill = bool("Fill", true);
    public final Setting<Integer> fillAlpha = num("FillAlpha", 40, 0, 150);
    public final Setting<Float> lineWidth = num("LineWidth", 1.2f, 0.1f, 4.0f);
    public final Setting<Float> height = num("Height", 0.15f, 0.0f, 1.0f);
    public final Setting<Color> safeColor = color("SafeColor", 0, 220, 120, 255);
    public final Setting<Color> unsafeColor = color("UnsafeColor", 255, 150, 0, 255);

    public HoleESPModule() {
        super("HoleESP", "Highlights safe holes to stand in", Category.RENDER);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        for (HoleManager.Hole hole : OyVey.holeManager.getHoles()) {
            Color base = hole.holeType() == HoleManager.HoleType.BEDROCK ? safeColor.getValue() : unsafeColor.getValue();
            AABB region = new AABB(
                    hole.pos().getX(), hole.pos().getY(), hole.pos().getZ(),
                    hole.pos().getX() + 1.0, hole.pos().getY() + height.getValue(), hole.pos().getZ() + 1.0);

            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(event.getMatrix(), region, ColorUtil.withAlpha(base, fillAlpha.getValue()));
            }
            if (box.getValue()) {
                RenderUtil.drawBox(event.getMatrix(), region, base, lineWidth.getValue());
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return String.valueOf(OyVey.holeManager.getHoles().size());
    }
}
