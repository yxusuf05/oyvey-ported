package me.alpha432.oyvey.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.combat.HitboxesModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static me.alpha432.oyvey.util.traits.Util.mc;

@Mixin(Entity.class)
public class MixinEntity {
    @ModifyReturnValue(method = "getPickRadius", at = @At("RETURN"))
    private float oyvey$hitbox(float original) {
        if (OyVey.moduleManager == null) return original;
        HitboxesModule module = OyVey.moduleManager.getModuleByClass(HitboxesModule.class);
        if (module == null || !module.isEnabled()) return original;

        Entity self = (Entity) (Object) this;
        if (self != mc.player && self instanceof LivingEntity) {
            return original + module.expand.getValue();
        }
        return original;
    }
}
