package me.alpha432.oyvey.util;

import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side explosion damage prediction.
 *
 * <p>Mirrors the vanilla pipeline as closely as the client allows: the raw amount comes from
 * {@code ExplosionDamageCalculator#getEntityDamageAmount}, then difficulty scaling (players only,
 * applied in {@code Player#hurtServer}), armour absorption, resistance and finally enchantment
 * protection — in that order.
 *
 * <p>Protection has to be summed by hand because {@code EnchantmentHelper#getDamageProtection}
 * needs a {@code ServerLevel}, which a client never has. Protection counts 1 point per level and
 * Blast Protection 2, matching the vanilla enchantment definitions for explosion damage.
 */
public final class DamageUtil implements Util {
    /** Explosion power of an end crystal. */
    public static final float CRYSTAL_POWER = 6.0f;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private DamageUtil() {
        throw new AssertionError("Can't create an instance of utility class");
    }

    /** Damage an end crystal exploding at {@code center} would deal to {@code target}. */
    public static float crystalDamage(Vec3 center, LivingEntity target) {
        return explosionDamage(center, target, CRYSTAL_POWER);
    }

    /**
     * Damage an explosion of the given power at {@code center} would deal to {@code target},
     * after all vanilla reductions. Returns 0 when the target is out of the blast radius.
     */
    public static float explosionDamage(Vec3 center, LivingEntity target, float power) {
        if (target == null || mc.level == null) return 0.0f;

        float radius = power * 2.0f;
        double distance = Math.sqrt(target.distanceToSqr(center)) / radius;
        if (distance > 1.0) return 0.0f;

        float seen = ServerExplosion.getSeenPercent(center, target);
        double exposure = (1.0 - distance) * seen;
        float raw = (float) ((exposure * exposure + exposure) / 2.0 * 7.0 * radius + 1.0);

        return reduce(target, raw);
    }

    /**
     * Cheap upper bound on {@link #explosionDamage} that assumes full exposure and no reductions.
     * Used to discard hopeless candidate positions before paying for the ray-traced exposure.
     */
    public static float maxPossibleDamage(Vec3 center, LivingEntity target, float power) {
        float radius = power * 2.0f;
        double distance = Math.sqrt(target.distanceToSqr(center)) / radius;
        if (distance > 1.0) return 0.0f;
        double exposure = 1.0 - distance;
        return (float) ((exposure * exposure + exposure) / 2.0 * 7.0 * radius + 1.0);
    }

    /** Health that actually has to be chewed through, absorption (golden apples) included. */
    public static float effectiveHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private static float reduce(LivingEntity entity, float damage) {
        if (entity instanceof Player) {
            Difficulty difficulty = mc.level.getDifficulty();
            if (difficulty == Difficulty.PEACEFUL) return 0.0f;
            if (difficulty == Difficulty.EASY) {
                damage = Math.min(damage / 2.0f + 1.0f, damage);
            } else if (difficulty == Difficulty.HARD) {
                damage = damage * 3.0f / 2.0f;
            }
        }

        DamageSource source = mc.level.damageSources().explosion(null, null);
        damage = CombatRules.getDamageAfterAbsorb(entity, damage, source,
                entity.getArmorValue(), (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

        if (entity.hasEffect(MobEffects.RESISTANCE)) {
            int strength = (entity.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
            damage = Math.max(damage * (25 - strength) / 25.0f, 0.0f);
        }

        damage = CombatRules.getDamageAfterMagicAbsorb(damage, protection(entity));
        return Math.max(damage, 0.0f);
    }

    private static float protection(LivingEntity entity) {
        float points = 0.0f;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            points += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            points += EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack) * 2.0f;
        }
        return Math.min(points, 20.0f);
    }
}
