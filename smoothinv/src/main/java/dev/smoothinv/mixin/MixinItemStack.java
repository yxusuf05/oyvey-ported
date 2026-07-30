package dev.smoothinv.mixin;

import dev.smoothinv.SmoothInvConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vanilla rebuilds the full tooltip line list every single frame while an
 * item is hovered. For component-heavy stacks (shulker boxes, enchanted
 * gear) that means thousands of short-lived allocations per second, which
 * on low-memory setups drives the GC pauses felt as inventory stutter.
 * Caching the result for a few hundred milliseconds removes almost all of
 * that work without any visible difference.
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @Unique
    private List<Component> smoothinv$cachedLines;
    @Unique
    private TooltipFlag smoothinv$cachedFlag;
    @Unique
    private long smoothinv$cacheExpiryNanos;

    @Inject(method = "getTooltipLines", at = @At("HEAD"), cancellable = true)
    private void smoothinv$serveCachedTooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
                                              CallbackInfoReturnable<List<Component>> cir) {
        if (!SmoothInvConfig.get().cacheTooltips) return;
        if (smoothinv$cachedLines != null
                && System.nanoTime() < smoothinv$cacheExpiryNanos
                && Objects.equals(flag, smoothinv$cachedFlag)) {
            // Copy so callers that append their own lines can't pollute the cache.
            cir.setReturnValue(new ArrayList<>(smoothinv$cachedLines));
        }
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void smoothinv$storeTooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
                                        CallbackInfoReturnable<List<Component>> cir) {
        if (!SmoothInvConfig.get().cacheTooltips) return;
        smoothinv$cachedLines = List.copyOf(cir.getReturnValue());
        smoothinv$cachedFlag = flag;
        smoothinv$cacheExpiryNanos = System.nanoTime()
                + SmoothInvConfig.get().tooltipCacheMs * 1_000_000L;
    }
}
