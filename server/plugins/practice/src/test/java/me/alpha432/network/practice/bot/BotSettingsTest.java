package me.alpha432.network.practice.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotSettingsTest {

    @Test
    void difficultyIsClampedToTheAllowedRange() {
        BotSettings settings = new BotSettings("nodebuff");

        settings.difficulty(0);
        assertEquals(BotSettings.MIN_DIFFICULTY, settings.difficulty());

        settings.difficulty(99);
        assertEquals(BotSettings.MAX_DIFFICULTY, settings.difficulty());
    }

    @Test
    void aHarderBotSwingsFasterAndHitsMoreOften() {
        BotSettings easy = new BotSettings("nodebuff");
        easy.difficulty(1);
        BotSettings hard = new BotSettings("nodebuff");
        hard.difficulty(5);

        assertTrue(hard.attackIntervalTicks() < easy.attackIntervalTicks());
        assertTrue(hard.hitChance() > easy.hitChance());
        assertTrue(hard.speed() > easy.speed());
    }

    @Test
    void hitChanceStaysAProbability() {
        for (int difficulty = BotSettings.MIN_DIFFICULTY; difficulty <= BotSettings.MAX_DIFFICULTY; difficulty++) {
            BotSettings settings = new BotSettings("nodebuff");
            settings.difficulty(difficulty);

            assertTrue(settings.hitChance() > 0.0D && settings.hitChance() <= 1.0D,
                    "hit chance out of range at difficulty " + difficulty);
            assertTrue(settings.attackIntervalTicks() > 0);
        }
    }

    @Test
    void pingTurnsIntoWholeTicks() {
        BotSettings settings = new BotSettings("nodebuff");

        settings.pingMillis(100);
        assertEquals(2, settings.pingTicks());

        settings.pingMillis(40);
        assertEquals(0, settings.pingTicks(), "under one tick there is nothing to delay");

        settings.pingMillis(-50);
        assertEquals(0, settings.pingMillis());
    }

    @Test
    void healthAndKnockbackAreClamped() {
        BotSettings settings = new BotSettings("nodebuff");

        settings.health(-5);
        assertEquals(1.0D, settings.health());

        settings.health(9999);
        assertEquals(200.0D, settings.health());

        settings.knockback(-1);
        assertEquals(0.0D, settings.knockback());

        settings.knockback(99);
        assertEquals(5.0D, settings.knockback());
    }

    @Test
    void onlyTheFightingModesAttack() {
        assertTrue(BotBehavior.AGGRESSIVE.attacks());
        assertTrue(BotBehavior.DEFENSIVE.attacks());
        assertTrue(BotBehavior.BOXING.attacks());
        assertTrue(!BotBehavior.STRAFE.attacks());
        assertTrue(!BotBehavior.SUMO.attacks());
        assertEquals(BotBehavior.SUMO, BotBehavior.byName("sumo"));
        assertEquals(null, BotBehavior.byName("nope"));
    }
}
