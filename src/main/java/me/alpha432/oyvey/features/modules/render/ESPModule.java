package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.ColorUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/**
 * Draws boxes around living entities so you can see them through the terrain. Friends get their
 * own colour, positions are interpolated with the render delta so the boxes stay smooth at any FPS.
 */
public class ESPModule extends Module {
    public final Setting<Boolean> box = bool("Box", true);
    public final Setting<Boolean> fill = bool("Fill", true);
    public final Setting<Integer> fillAlpha = num("FillAlpha", 35, 0, 150);
    public final Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.1f, 4.0f);
    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> mobs = bool("Mobs", false);
    public final Setting<Integer> range = num("Range", 48, 4, 128);
    public final Setting<Color> color = color("Color", 255, 0, 90, 255);
    public final Setting<Color> friendColor = color("FriendColor", 0, 255, 90, 255);

    public ESPModule() {
        super("ESP", "Highlights entities through walls", Category.RENDER);
        fillAlpha.setVisibility(v -> fill.getValue());
        lineWidth.setVisibility(v -> box.getValue());
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        double r = range.getValue();
        AABB area = mc.player.getBoundingBox().inflate(r);
        float delta = event.getDelta();

        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, area, e -> e != mc.player && e.isAlive())) {
            boolean isPlayer = entity instanceof Player;
            if (isPlayer && !players.getValue()) continue;
            if (!isPlayer && !mobs.getValue()) continue;
            if (mc.player.distanceTo(entity) > r) continue;

            Color base = (entity instanceof Player player && OyVey.friendManager.isFriend(player))
                    ? friendColor.getValue()
                    : color.getValue();

            double x = Mth.lerp(delta, entity.xOld, entity.getX());
            double y = Mth.lerp(delta, entity.yOld, entity.getY());
            double z = Mth.lerp(delta, entity.zOld, entity.getZ());
            AABB bb = entity.getDimensions(entity.getPose()).makeBoundingBox(new Vec3(x, y, z));

            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(event.getMatrix(), bb, ColorUtil.withAlpha(base, fillAlpha.getValue()));
            }
            if (box.getValue()) {
                RenderUtil.drawBox(event.getMatrix(), bb, base, lineWidth.getValue());
            }
        }
    }
}
