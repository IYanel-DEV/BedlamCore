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
        assertTrue(GameRules.teamContending(true, 0));
        assertTrue(GameRules.teamContending(false, 1));
        assertFalse(GameRules.teamContending(false, 0));
        assertFalse(GameRules.shouldEndMatch(2));
        assertTrue(GameRules.shouldEndMatch(1));
        assertTrue(GameRules.shouldEndMatch(0));
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
        assertEquals(1.1, GameRules.CHEST_HOLO_Y);
        assertEquals(3.15, GameRules.GEN_HOLO_TITLE_Y);
        assertEquals(2.85, GameRules.GEN_HOLO_TIER_Y);
        assertEquals(2.25, GameRules.SHOP_HOLO_TITLE_Y);
        assertEquals(1.95, GameRules.SHOP_HOLO_SUB_Y);
        assertEquals(2.5, GameRules.GEN_STAND_Y);
        assertEquals(2.25, GameRules.LOBBY_HOLO_TITLE_Y);
        assertEquals(1.95, GameRules.LOBBY_HOLO_SUB_Y);
        assertEquals(1.65, GameRules.LOBBY_HOLO_INFO_Y);
        assertEquals(2.5, GameRules.FORGE_SHARE_RADIUS);
        assertEquals(1.2, GameRules.FORGE_STANDING_RADIUS);
        assertTrue(GameRules.forgeShareInRange(2.0, 0.5, 1.0));
        assertFalse(GameRules.forgeShareInRange(3.0, 0.0, 0.0));
        assertTrue(GameRules.forgeStandingInRange(0.5, 0.5));
        assertFalse(GameRules.forgeStandingInRange(2.0, 0.0));
        assertEquals(0.0, GameRules.forgeDiamondChance(1));
        assertEquals(GameRules.FORGE_L2_DIAMOND, GameRules.forgeDiamondChance(2));
        assertEquals(GameRules.FORGE_L3_DIAMOND, GameRules.forgeDiamondChance(3));
        assertTrue(GameRules.FORGE_L3_DIAMOND > GameRules.FORGE_L2_DIAMOND);
        assertTrue(GameRules.FORGE_L3_EMERALD > GameRules.FORGE_L2_EMERALD);
        assertTrue(GameRules.forgeBonusHits(0.02, 0.019));
        assertFalse(GameRules.forgeBonusHits(0.02, 0.02));
        assertFalse(GameRules.forgeBonusHits(0.0, 0.0));
        assertEquals(0, GameRules.toolTierAfterDeath(0));
        assertEquals(1, GameRules.toolTierAfterDeath(1));
        assertEquals(1, GameRules.toolTierAfterDeath(2));
        assertEquals(3, GameRules.toolTierAfterDeath(4));
        assertTrue(GameRules.isBridgeReplaceable("AIR"));
        assertTrue(GameRules.isBridgeReplaceable("CAVE_AIR"));
        assertFalse(GameRules.isBridgeReplaceable("WOOL"));
        assertFalse(GameRules.isBridgeReplaceable("BED_BLOCK"));
        assertEquals(20, GameRules.BRIDGE_EGG_MAX_PATH);
        assertEquals(40, GameRules.BRIDGE_EGG_MAX_TICKS);
        assertEquals(20.0, GameRules.BRIDGE_EGG_MAX_DISTANCE);
        assertEquals(0, GameRules.bridgeSideX(1.0, 0.2));
        assertEquals(1, GameRules.bridgeSideZ(1.0, 0.2));
        assertEquals(1, GameRules.bridgeSideX(0.2, 1.0));
        assertEquals(0, GameRules.bridgeSideZ(0.2, 1.0));
        assertTrue(GameRules.isGlassBlock("GLASS"));
        assertTrue(GameRules.isGlassBlock("STAINED_GLASS"));
        assertTrue(GameRules.isGlassBlock("THIN_GLASS"));
        assertTrue(GameRules.isGlassBlock("GLASS_PANE"));
        assertFalse(GameRules.isGlassBlock("WOOL"));
        assertFalse(GameRules.isGlassBlock("DIAMOND_BLOCK"));
        assertEquals(1, GameRules.levelFromXp(0));
        assertEquals(1, GameRules.levelFromXp(4999));
        assertEquals(2, GameRules.levelFromXp(5000));
        assertEquals(8, GameRules.levelFromXp(7 * 5000 + 3400));
        assertEquals(3400, GameRules.xpIntoLevel(7 * 5000 + 3400));
        assertEquals(0, GameRules.xpIntoLevel(5000));
        assertEquals("3.4k", GameRules.compactXp(3400));
        assertEquals("5k", GameRules.compactXp(5000));
        assertEquals("0", GameRules.compactXp(0));
        assertEquals("36,886", GameRules.commas(36886));
        assertEquals(7, GameRules.xpBarFilled(3400, 10));
        assertEquals(0, GameRules.xpBarFilled(0, 10));
        assertEquals(10, GameRules.xpBarFilled(5000, 10));
        assertEquals(50, GameRules.TOKENS_WIN);
        assertEquals(25, GameRules.TOKENS_BED);
        assertEquals(5, GameRules.TOKENS_KILL);
        assertEquals(10, GameRules.TOKENS_PLAY);
        assertEquals(5000, GameRules.XP_PER_LEVEL);
        assertEquals(32, GameRules.INVENTORY_TITLE_MAX);
        assertEquals("Quick Buy", GameRules.inventoryTitle("Quick Buy"));
        assertEquals(32, GameRules.inventoryTitle("12345678901234567890123456789012345").length());
        assertTrue(GameRules.isChestGuiSize(27));
        assertTrue(GameRules.isChestGuiSize(54));
        assertFalse(GameRules.isChestGuiSize(45));
        assertEquals(1, GameRules.nextToolTier(0));
        assertEquals(4, GameRules.nextToolTier(3));
        assertEquals(-1, GameRules.nextToolTier(4));
        assertEquals(1, GameRules.trapDiamondCost(0));
        assertEquals(2, GameRules.trapDiamondCost(1));
        assertEquals(3, GameRules.trapDiamondCost(2));
        assertEquals(1, GameRules.pickaxeEfficiency(1));
        assertEquals(3, GameRules.pickaxeEfficiency(4));
        assertTrue(GameRules.isPickaxe("IRON_PICKAXE"));
        assertTrue(GameRules.isAxe("DIAMOND_AXE"));
        assertFalse(GameRules.isAxe("DIAMOND_PICKAXE"));
        assertEquals(40, GameRules.TRAP_COOLDOWN_TICKS);
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
