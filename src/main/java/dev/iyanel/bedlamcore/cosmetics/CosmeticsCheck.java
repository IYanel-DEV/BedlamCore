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

        assertEquals(CosmeticsService.CAT_WOOD_SKIN, CosmeticsService.normalizeCategory("Wood Skins"));
        assertEquals(CosmeticsService.CAT_FINAL_KILL_EFFECT, CosmeticsService.normalizeCategory("Final Kill Effects"));
        assertEquals(CosmeticsService.CAT_PRESTIGE, CosmeticsService.normalizeCategory("Prestige Customizer"));
        assertEquals(CosmeticsService.CAT_PRESTIGE, CosmeticsService.normalizeCategory("prestige"));
        assertEquals("Wood Skins", CosmeticsService.categoryDisplay(CosmeticsService.CAT_WOOD_SKIN));
        assertEquals("Final Kill Effects", CosmeticsService.categoryDisplay(CosmeticsService.CAT_FINAL_KILL_EFFECT));
        assertEquals("Prestige Customizer", CosmeticsService.categoryDisplay(CosmeticsService.CAT_PRESTIGE));

        String[] wsIds = CosmeticsService.defaultWoodSkinIds();
        assertEquals(9, wsIds.length);
        assertContains(wsIds, "ws_cherry");
        assertContains(wsIds, "ws_warped");
        String[] fkeIds = CosmeticsService.defaultFinalKillEffectIds();
        assertEquals(8, fkeIds.length);
        assertContains(fkeIds, "fke_soul_rip");
        assertContains(fkeIds, "fke_nova");
        String[] prIds = CosmeticsService.defaultPrestigeIds();
        assertEquals(8, prIds.length);
        assertContains(prIds, "pr_none");
        assertContains(prIds, "pr_hypixel");

        assertEquals(CosmeticsService.CAT_PROJECTILE_TRAIL, CosmeticsService.normalizeCategory("Projectile Trails"));
        assertEquals(CosmeticsService.CAT_BED_DESTROY, CosmeticsService.normalizeCategory("Bed Destroys"));
        assertEquals("Projectile Trails", CosmeticsService.categoryDisplay(CosmeticsService.CAT_PROJECTILE_TRAIL));
        assertEquals("Bed Destroys", CosmeticsService.categoryDisplay(CosmeticsService.CAT_BED_DESTROY));
        String[] ptIds = CosmeticsService.defaultProjectileTrailIds();
        assertEquals(10, ptIds.length);
        assertContains(ptIds, "pt_flame");
        assertContains(ptIds, "pt_firework");
        String[] bdIds = CosmeticsService.defaultBedDestroyIds();
        assertEquals(10, bdIds.length);
        assertContains(bdIds, "bd_explosion");
        assertContains(bdIds, "bd_glyph");

        assertEquals("addPassenger", WinEffectController.passengerMountMethod(true));
        assertEquals("setPassenger", WinEffectController.passengerMountMethod(false));
        assertEquals(3, WinEffectController.rainbowSheepColorIndex(10, 1, 16));
        assertEquals(0, WinEffectController.rainbowSheepColorIndex(0, 0, 16));
        assertTrue(WinEffectController.needsWinDragonRemount(true, false));
        assertTrue(!WinEffectController.needsWinDragonRemount(true, true));
        assertTrue(!WinEffectController.needsWinDragonRemount(false, false));
        assertTrue(WinEffectController.winDragonFlightSpeed() >= 1.0);
        assertTrue(WinEffectController.winDragonFireballYield() >= 1.5f);
        assertTrue(WinEffectController.isWinDragonBreakable(org.bukkit.Material.STONE));
        assertTrue(!WinEffectController.isWinDragonBreakable(org.bukkit.Material.AIR));
        assertTrue(!WinEffectController.isWinDragonBreakable(org.bukkit.Material.BEDROCK));
        // Dragon facing derives from displacement (never a hardcoded guess): +Z motion faces the model offset,
        // and opposite deltas face 180 apart — so head-first flight holds on every version.
        assertTrue(Math.abs(WinEffectController.dragonBodyYaw(0, 1) - 180f) < 0.001f);
        assertTrue(Math.abs(WinEffectController.dragonBodyYaw(1, 0) - 90f) < 0.001f);
        assertTrue(WinEffectController.clampDragonPitch(100, 1) <= 40.0f);
        assertTrue(WinEffectController.clampDragonPitch(-100, 1) >= -40.0f);
        assertTrue(WinEffectController.clampDragonPitch(0, 0) == 0f);
        // Shortest-arc yaw lerp wraps across ±180 (170 → -170 eases toward 180, not back through 0).
        assertTrue(Math.abs(WinEffectController.lerpYawShortest(170f, -170f, 0.5f) - 180f) < 0.001f);
        assertTrue(Math.abs(WinEffectController.lerpYawShortest(0f, 90f, 0.5f) - 45f) < 0.001f);
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
        assertTrue(dev.iyanel.bedlamcore.lobby.LobbyNpcService.PROFILE_ENSURE_INTERVAL >= 20);
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
