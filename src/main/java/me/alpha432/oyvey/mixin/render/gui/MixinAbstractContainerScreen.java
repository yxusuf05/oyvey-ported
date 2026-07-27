package me.alpha432.oyvey.mixin.render.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the (protected) container origin so the "legit" totem mover can compute the real-pixel
 * position of a slot and park the OS cursor over it.
 */
@Mixin(AbstractContainerScreen.class)
public interface MixinAbstractContainerScreen {
    @Accessor("leftPos")
    int oyvey$getLeftPos();

    @Accessor("topPos")
    int oyvey$getTopPos();
}
