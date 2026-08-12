package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.arena.GameType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class GameRulesCheck {
    private GameRulesCheck() {
    }

    public static void main(String[] args) {
        Map<String, Integer> sizes = new HashMap<String, Integer>();
        sizes.put("red", 2);
        sizes.put("blue", 1);
        sizes.put("green", 1);
        assertEquals("blue", GameRules.leastPopulated(Arrays.asList("red", "blue", "green"), sizes));
        assertTrue(GameRules.canRespawn(true, false));
        assertFalse(GameRules.canRespawn(false, false));
        assertFalse(GameRules.canRespawn(true, true));
        assertEquals(1, GameRules.generatorTier(359, 360, 720));
        assertEquals(2, GameRules.generatorTier(360, 360, 720));
        assertEquals(3, GameRules.generatorTier(720, 360, 720));
        assertEquals(1, GameType.SOLO.teamSize());
        assertEquals(2, GameType.DOUBLES.teamSize());
        assertEquals(70.0, GameRules.voidKillY(100.0));
        assertTrue(GameRules.tooHigh(101, 100));
        assertFalse(GameRules.tooHigh(100, 100));
        assertTrue(GameRules.isSword("WOOD_SWORD"));
        assertTrue(GameRules.isSword("IRON_SWORD"));
        assertEquals(2, GameRules.swordRank("IRON_SWORD"));
        assertEquals(3, GameRules.swordRank("DIAMOND_SWORD"));
        assertTrue(GameRules.swordRank("IRON_SWORD") > GameRules.swordRank("WOODEN_SWORD"));
        assertTrue(GameRules.isArmor("IRON_CHESTPLATE"));
        assertTrue(GameRules.isArmor("LEATHER_BOOTS"));
        assertFalse(GameRules.isArmor("IRON_SWORD"));
        assertFalse(GameRules.canDropSword(1));
        assertTrue(GameRules.canDropSword(2));
        assertEquals(8.0, GameRules.HEAL_POOL_RADIUS);
        assertTrue(GameRules.canFastDeposit("IRON_INGOT"));
        assertTrue(GameRules.canFastDeposit("WOOL"));
        assertFalse(GameRules.canFastDeposit("IRON_SWORD"));
        assertFalse(GameRules.canFastDeposit("DIAMOND_PICKAXE"));
        assertFalse(GameRules.canFastDeposit("IRON_BOOTS"));
        assertEquals(3, GameRules.TRAP_QUEUE_MAX);
        System.out.println("BedlamCore game rules: PASS");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }
}
