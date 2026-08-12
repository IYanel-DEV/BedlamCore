package dev.iyanel.bedlamcore.game;

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
