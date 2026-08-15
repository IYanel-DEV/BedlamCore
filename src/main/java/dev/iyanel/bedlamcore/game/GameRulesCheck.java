package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.cosmetics.CosmeticsCheck;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.util.PersistenceCheck;
import dev.iyanel.bedlamcore.world.ImportWorldsCheck;
import dev.iyanel.bedlamcore.world.MapTemplatesCheck;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class GameRulesCheck {
    private GameRulesCheck() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, Integer> sizes = new HashMap<String, Integer>();
        sizes.put("red", 2);
        sizes.put("blue", 1);
        sizes.put("green", 1);
        assertEquals("blue", GameRules.leastPopulated(Arrays.asList("red", "blue", "green"), sizes));
        assertTrue(GameRules.canRespawn(true, false));
        assertFalse(GameRules.canRespawn(false, false));
        assertFalse(GameRules.canRespawn(true, true));
        // Contending: living, or bed+occupied; empty never-occupied bed does not block.
        assertFalse(GameRules.teamContending(true, 0, false));
        assertTrue(GameRules.teamContending(true, 0, true));
        assertTrue(GameRules.teamContending(false, 1, false));
        assertTrue(GameRules.teamContending(false, 1, true));
        assertFalse(GameRules.teamContending(false, 0, true));
        assertFalse(GameRules.teamContending(false, 0, false));
        assertFalse(GameRules.shouldEndMatch(2));
        assertTrue(GameRules.shouldEndMatch(1));
        assertTrue(GameRules.shouldEndMatch(0));
        assertEquals(1, GameRules.generatorTier(359, 360, 720));
        assertEquals(2, GameRules.generatorTier(360, 360, 720));
        assertEquals(3, GameRules.generatorTier(720, 360, 720));
        assertEquals(1, GameType.SOLO.teamSize());
        assertEquals(2, GameType.DOUBLES.teamSize());
        assertEquals(85.0, GameRules.voidKillY(100.0));
        assertEquals(70.0, GameRules.voidKillY(100.0, 30.0));
        // Border: GUI radius → WB diameter 2r; mayPlace XZ = center ± radius
        assertEquals(160, ArenaSettings.worldBorderDiameter(80));
        int[] box = ArenaSettings.borderBox(0, 64, 0, 100, 80, 0, 80);
        assertEquals(-30, box[0]); // cx=50 → 50-80
        assertEquals(130, box[3]);
        assertEquals((box[0] + box[3]) / 2, 50);
        assertEquals(box[3] - box[0], ArenaSettings.worldBorderDiameter(80));
        // bedwars-e2560: waiting Y=202, bed Y=100 — raw waiting-only Y excludes bed; expand like buildBorderBounds
        int[] highLobby = ArenaSettings.borderBox(0, 202, 0, 0, 202, 0, 80);
        assertEquals(138, highLobby[1]); // 202-64
        assertTrue(100 < highLobby[1]); // bed would be denied without expand
        highLobby[1] = Math.min(highLobby[1], 100 - 64);
        highLobby[4] = Math.max(highLobby[4], 100 + 128);
        assertEquals(36, highLobby[1]);
        assertTrue(100 >= highLobby[1] && 100 <= highLobby[4]);
        assertTrue(23 >= highLobby[0] && 23 <= highLobby[3] && 50 >= highLobby[2] && 50 <= highLobby[5]);
        assertTrue(GameRules.tooHigh(101, 100));
        assertFalse(GameRules.tooHigh(100, 100));
        assertTrue(GameRules.tooLow(92, 100)); // bedY−8
        assertFalse(GameRules.tooLow(93, 100)); // bedY−7 ok
        assertEquals(7, GameRules.BUILD_FLOOR_BELOW);
        assertTrue(GameRules.protectPlus(0, 0, 0));
        assertTrue(GameRules.protectPlus(1, 0, 0));
        assertTrue(GameRules.protectPlus(0, 0, -1));
        assertFalse(GameRules.protectPlus(1, 0, 1)); // diagonal
        assertFalse(GameRules.protectPlus(2, 0, 0));
        assertFalse(GameRules.protectPlus(0, 1, 0)); // not same Y
        assertEquals(1, GameRules.PROTECT_PLUS);
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
        assertEquals(GameRules.LOBBY_NPC_HOLO_TOP, GameRules.LOBBY_HOLO_TITLE_Y);
        assertEquals(GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 1), GameRules.LOBBY_HOLO_SUB_Y);
        assertEquals(GameRules.labelY(GameRules.LOBBY_NPC_HOLO_TOP, 2), GameRules.LOBBY_HOLO_INFO_Y);
        assertTrue(GameRules.LOBBY_HOLO_INFO_Y > GameRules.NPC_HOLO_TOP); // queue stack clears body; cosmetics keep NPC_HOLO_TOP
        float[] steep = LobbyNpcService.lookYawPitch(new org.bukkit.util.Vector(0, 1.62, 0), new org.bukkit.util.Vector(0, 10, 2));
        assertTrue(Math.abs(steep[1]) <= 30.01f); // pitch clamp
        float[] level = LobbyNpcService.lookYawPitch(new org.bukkit.util.Vector(0, 1.62, 0), new org.bukkit.util.Vector(0, 1.62, 5));
        assertTrue(Math.abs(level[1]) < 1f);
        assertEquals(8.0, LobbyNpcService.LOOK_RANGE);
        assertTrue(LobbyNpcService.inLookRange(8.0 * 8.0));
        assertFalse(LobbyNpcService.inLookRange(8.0 * 8.0 + 0.01));
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
        assertEquals(0, GameRules.bridgeEggEndDip(1, 20));
        assertEquals(0, GameRules.bridgeEggEndDip(12, 20));
        assertEquals(1, GameRules.bridgeEggEndDip(13, 20));
        assertEquals(1, GameRules.bridgeEggEndDip(14, 20));
        assertEquals(2, GameRules.bridgeEggEndDip(15, 20));
        assertEquals(4, GameRules.bridgeEggEndDip(20, 20));
        assertEquals(0, GameRules.bridgeEggEndDip(0, 20));
        assertEquals(0, GameRules.bridgeEggEndDip(10, 0));
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
        assertTrue(GameRules.isChestGuiSize(45));
        assertTrue(GameRules.isChestGuiSize(54));
        assertFalse(GameRules.isChestGuiSize(36));
        assertEquals(1, GameRules.nextToolTier(0));
        assertEquals(4, GameRules.nextToolTier(3));
        assertEquals(-1, GameRules.nextToolTier(4));
        assertEquals(1, GameRules.trapDiamondCost(0));
        assertEquals(2, GameRules.trapDiamondCost(1));
        assertEquals(4, GameRules.trapDiamondCost(2));
        assertEquals(1, GameRules.pickaxeEfficiency(1));
        assertEquals(3, GameRules.pickaxeEfficiency(4));
        assertTrue(GameRules.isPickaxe("IRON_PICKAXE"));
        assertTrue(GameRules.isAxe("DIAMOND_AXE"));
        assertFalse(GameRules.isAxe("DIAMOND_PICKAXE"));
        assertEquals(40, GameRules.TRAP_COOLDOWN_TICKS);
        assertEquals(900, GameRules.POTION_SPEED_TICKS);
        assertEquals(900, GameRules.POTION_JUMP_TICKS);
        assertEquals(600, GameRules.POTION_INVIS_TICKS);
        assertEquals(1, GameRules.POTION_SPEED_AMPLIFIER);
        assertEquals(4, GameRules.POTION_JUMP_AMPLIFIER);
        assertEquals(2, GameRules.POTION_INVIS_COST_EMERALD);
        assertEquals(1, GameRules.POTION_SPEED_COST_EMERALD);
        assertEquals(1, GameRules.LEGACY_EQUIP_BOOTS);
        assertEquals(4, GameRules.LEGACY_EQUIP_HELMET);
        assertEquals("\u00A7cVictim\u00A77 was killed by \u00A79Killer\u00A77.",
            GameRules.killMessage("\u00A7cVictim", "\u00A79Killer", "kill", false));
        assertEquals("\u00A7cVictim\u00A77 was shot by \u00A79Killer\u00A77. \u00A7c\u00A7lFINAL KILL!",
            GameRules.killMessage("\u00A7cVictim", "\u00A79Killer", "shot", true));
        assertEquals("\u00A7cVictim\u00A77 fell into the void.",
            GameRules.killMessage("\u00A7cVictim", null, "void", false));
        assertEquals("\u00A7cVictim\u00A77 was knocked into the void by \u00A79Killer\u00A77.",
            GameRules.killMessage("\u00A7cVictim", "\u00A79Killer", "void_kill", false));
        assertEquals("\u00A7cVictim\u00A77 was knocked off an edge by \u00A79Killer\u00A77.",
            GameRules.killMessage("\u00A7cVictim", "\u00A79Killer", "fall", false));
        assertEquals("\u00A7cVictim\u00A77 was bombed by \u00A79Killer\u00A77.",
            GameRules.killMessage("\u00A7cVictim", "\u00A79Killer", "explosion", false));
        assertEquals("\u00A7cVictim\u00A77 was burned by \u00A79Killer\u00A77.",
            GameRules.killMessage("\u00A7cVictim", "\u00A79Killer", "fire", false));
        assertEquals("void_kill", GameRules.deathMode(true, true, null));
        assertEquals("void", GameRules.deathMode(true, false, null));
        assertEquals("shot", GameRules.deathMode(false, true, "PROJECTILE"));
        assertEquals("fall", GameRules.deathMode(false, true, "FALL"));
        assertEquals("explosion", GameRules.deathMode(false, true, "ENTITY_EXPLOSION"));
        assertEquals("fire", GameRules.deathMode(false, true, "FIRE_TICK"));
        assertEquals("kill", GameRules.deathMode(false, true, "ENTITY_ATTACK"));
        assertEquals("die", GameRules.deathMode(false, false, null));
        assertEquals("\u00A7f\u00A7lBED DESTRUCTION > \u00A7cRed\u00A7f\u00A7l's Bed\u00A77 > Destroyed by \u00A79Bob",
            GameRules.bedBreakMessage("\u00A7cRed", "\u00A79Bob"));
        assertTrue(GameRules.isMatchOre("IRON_INGOT"));
        assertTrue(GameRules.isMatchOre("EMERALD"));
        assertFalse(GameRules.isMatchOre("WOOL"));
        int[] totals = new int[] {64, 8, 2, 2};
        int[] shares = GameRules.killLootShares(totals);
        assertEquals(64, shares[GameRules.RES_IRON]);
        assertEquals(4, shares[GameRules.RES_GOLD]);
        assertEquals(1, shares[GameRules.RES_DIAMOND]);
        assertEquals(1, shares[GameRules.RES_EMERALD]);
        assertEquals("\u00A7a+64 Iron! \u00A76+4 Gold! \u00A7b+1 Diamond! \u00A72+1 Emerald!",
            GameRules.killLootKillerMessage(shares));
        assertEquals("\u00A7cBob killed you and stole your resources!", GameRules.killLootVictimMessage("Bob"));
        // Team setup field order (GUI / nextMissing); complete when nextMissing is null.
        assertEquals("spawn", GameRules.teamSetupNextMissing(false, false, false, false, false, false, false));
        assertEquals("bed", GameRules.teamSetupNextMissing(true, false, false, false, false, false, false));
        assertEquals("forge", GameRules.teamSetupNextMissing(true, true, false, false, false, false, false));
        assertEquals("item shop", GameRules.teamSetupNextMissing(true, true, true, false, false, false, false));
        assertEquals("upgrade shop", GameRules.teamSetupNextMissing(true, true, true, true, false, false, false));
        assertEquals("team chest", GameRules.teamSetupNextMissing(true, true, true, true, true, false, false));
        assertEquals("ender chest", GameRules.teamSetupNextMissing(true, true, true, true, true, true, false));
        assertTrue(GameRules.teamSetupNextMissing(true, true, true, true, true, true, true) == null);
        // Team wool sits in the hotbar slot before Bedlam Setup (usually 7 when setup is 8).
        assertEquals(7, GameRules.slotBeforeSetup(8));
        assertEquals(3, GameRules.slotBeforeSetup(4));
        assertEquals(0, GameRules.slotBeforeSetup(0));
        // Delete stick: two slots before compass (usually 6).
        assertEquals(6, GameRules.deleteStickSlot(8));
        assertEquals(2, GameRules.deleteStickSlot(4));
        assertEquals(0, GameRules.deleteStickSlot(0));
        assertTrue(GameRules.setupPointNear(10, 64, 10, 10, 64, 10));
        assertTrue(GameRules.setupPointNear(10, 64, 10, 11, 65, 9));
        assertTrue(GameRules.setupPointNear(10, 64, 10, 12, 64, 10)); // dist 2
        assertFalse(GameRules.setupPointNear(10, 64, 10, 13, 64, 10));
        assertTrue(GameRules.mayLobbyBuild(true, false));
        assertTrue(GameRules.mayLobbyBuild(false, true));
        assertFalse(GameRules.mayLobbyBuild(false, false));
        PersistenceCheck.run();
        ImportWorldsCheck.run();
        MapTemplatesCheck.run();
        CosmeticsCheck.run();
        ProfileStatsCheck.run();
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
