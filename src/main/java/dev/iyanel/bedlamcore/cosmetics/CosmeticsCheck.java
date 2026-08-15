package dev.iyanel.bedlamcore.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dependency-free smoke check for cosmetic kill-line formatting + category keys. */
public final class CosmeticsCheck {
    private CosmeticsCheck() {
    }

    public static void run() {
        assertEquals(CosmeticsService.CAT_KILL_MESSAGE, CosmeticsService.normalizeCategory("kill-message"));
        assertEquals(CosmeticsService.CAT_KILL_EFFECT, CosmeticsService.normalizeCategory("Kill Effects"));
        assertEquals(CosmeticsService.CAT_WIN_EFFECT, CosmeticsService.normalizeCategory("WIN_EFFECT"));
        assertEquals(null, CosmeticsService.normalizeCategory("hats"));
        assertEquals("Kill Messages", CosmeticsService.categoryDisplay(CosmeticsService.CAT_KILL_MESSAGE));
        assertEquals("Kill Effects", CosmeticsService.categoryDisplay(CosmeticsService.CAT_KILL_EFFECT));
        assertEquals("Win Effects", CosmeticsService.categoryDisplay(CosmeticsService.CAT_WIN_EFFECT));

        String msg = CosmeticsService.formatKillMessage(
            "%killer% &7crushed %victim%&7.",
            "\u00A7cVictim",
            "\u00A79Killer",
            true);
        assertTrue(msg.contains("\u00A79Killer"));
        assertTrue(msg.contains("\u00A7cVictim"));
        assertTrue(msg.contains("crushed"));
        assertTrue(msg.contains("FINAL KILL!"));

        String plain = CosmeticsService.formatKillMessage(
            "{killer} rekt {victim}",
            "\u00A7cBob",
            "\u00A79Ann",
            false);
        assertEquals("\u00A79Ann rekt \u00A7cBob", plain);

        Map<String, String> templates = new LinkedHashMap<String, String>();
        templates.put("kill", "%victim% &7was struck down by %killer%&7.");
        templates.put("void_kill", "%victim% &7was melted by %killer%&7.");
        templates.put("shot", "%victim% &7was turned to ash by %killer%&7.");
        CosmeticsService.Cosmetic pack = new CosmeticsService.Cosmetic(
            "km_fire", CosmeticsService.CAT_KILL_MESSAGE, "Fire", 100, templates, "", java.util.Collections.<String>emptyList());
        assertEquals("%victim% &7was melted by %killer%&7.", pack.templateFor("void_kill"));
        assertEquals("%victim% &7was struck down by %killer%&7.", pack.templateFor("kill"));
        assertEquals("%victim% &7was struck down by %killer%&7.", pack.templateFor("fall")); // fallback to kill
        assertEquals("%victim% &7was struck down by %killer%&7.", pack.message());

        String fireLine = CosmeticsService.formatKillMessage(pack.templateFor("void_kill"), "\u00A7cBob", "\u00A76Ann", false);
        assertTrue(fireLine.contains("melted"));
        assertTrue(fireLine.contains("\u00A76Ann"));

        String[] kmIds = CosmeticsService.defaultKillMessagePackIds();
        assertEquals(29, kmIds.length);
        assertContains(kmIds, "km_fire");
        assertContains(kmIds, "km_western");
        assertContains(kmIds, "km_honourable");
        assertContains(kmIds, "km_oldman");
        assertContains(kmIds, "km_crushed");

        String[] winIds = CosmeticsService.defaultWinEffectIds();
        assertTrue(winIds.length >= 12);
        assertTrue(winIds.length <= 24);
        assertContains(winIds, "we_firework");
        assertContains(winIds, "we_cold_snap");
        assertContains(winIds, "we_burning_soul");
        assertContains(winIds, "we_notes");
        assertContains(winIds, "we_blood");
        assertContains(winIds, "we_cookie");
        assertContains(winIds, "we_campfire");
        assertContains(winIds, "we_glyphs");
        assertContains(winIds, "we_snowball");
        assertContains(winIds, "we_tornado");
        assertContains(winIds, "we_meteor");
        assertContains(winIds, "we_anvil");
        assertContains(winIds, "we_dragon");
        assertContains(winIds, "we_rainbow");
        assertEquals(19, winIds.length);

        assertEquals("addPassenger", CosmeticsService.passengerMountMethod(true));
        assertEquals("setPassenger", CosmeticsService.passengerMountMethod(false));
        assertEquals(3, CosmeticsService.rainbowSheepColorIndex(10, 1, 16));
        assertEquals(0, CosmeticsService.rainbowSheepColorIndex(0, 0, 16));
        assertTrue(CosmeticsService.needsWinDragonRemount(true, false));
        assertTrue(!CosmeticsService.needsWinDragonRemount(true, true));
        assertTrue(!CosmeticsService.needsWinDragonRemount(false, false));
        assertTrue(CosmeticsService.winDragonFlightSpeed() >= 1.0);
        assertTrue(CosmeticsService.winDragonFireballYield() >= 1.5f);
        assertTrue(CosmeticsService.isWinDragonBreakable(org.bukkit.Material.STONE));
        assertTrue(!CosmeticsService.isWinDragonBreakable(org.bukkit.Material.AIR));
        assertTrue(!CosmeticsService.isWinDragonBreakable(org.bukkit.Material.BEDROCK));
        assertEquals("Unlocked: 3/29 (10%)", CosmeticsService.unlockProgress(3, 29));
        assertEquals("Unlocked: 0/0 (0%)", CosmeticsService.unlockProgress(0, 0));
        assertEquals("Currently Selected: NONE", CosmeticsService.selectedLabel(null));
        assertEquals("Currently Selected: Fire", CosmeticsService.selectedLabel("Fire"));
        assertTrue(!CosmeticsService.unlockProgress(1, 2).toLowerCase().contains("hypixel"));
        assertTrue(!CosmeticsService.selectedLabel("Kill Messages").toLowerCase().contains("hypixel"));

        assertTrue(dev.iyanel.bedlamcore.world.GameWorlds.resetRestoresPristine());
        assertTrue(dev.iyanel.bedlamcore.world.GameWorlds.unloadArenaWithoutSave());
        assertTrue(dev.iyanel.bedlamcore.lobby.LobbyNpcService.needsCosmeticsRespawn(false, true));
        assertTrue(dev.iyanel.bedlamcore.lobby.LobbyNpcService.needsCosmeticsRespawn(true, false));
        assertTrue(!dev.iyanel.bedlamcore.lobby.LobbyNpcService.needsCosmeticsRespawn(true, true));
        assertTrue(dev.iyanel.bedlamcore.lobby.LobbyNpcService.HOLO_SCRUB_RADIUS >= 2.0);
        assertTrue(dev.iyanel.bedlamcore.lobby.LobbyNpcService.profileHologramLineCount() == 6);
        assertTrue(dev.iyanel.bedlamcore.lobby.LobbyNpcService.PROFILE_HOLO_TOP > 2.0);
    }

    private static void assertContains(String[] haystack, String needle) {
        for (String s : haystack) {
            if (needle.equals(s)) return;
        }
        throw new AssertionError("Missing id: " + needle);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }
}
