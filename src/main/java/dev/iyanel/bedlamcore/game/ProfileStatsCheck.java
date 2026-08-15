package dev.iyanel.bedlamcore.game;

import java.util.List;

/** Dependency-free smoke check for profile hologram / winstreak / K-D helpers. */
public final class ProfileStatsCheck {
    private ProfileStatsCheck() {
    }

    public static void run() {
        StatsStore.Record empty = new StatsStore.Record();
        String[] lines = ProfileStats.hologramLines(empty);
        assertEquals(6, lines.length);
        assertTrue(strip(lines[0]).contains("Bed Wars Profile"));
        assertTrue(strip(lines[1]).contains("Your Level:"));
        assertTrue(strip(lines[2]).contains("Progress:"));
        assertTrue(strip(lines[3]).contains("Total Wins:"));
        assertTrue(strip(lines[4]).contains("Current Winstreak:"));
        assertTrue(strip(lines[5]).toUpperCase().contains("CLICK FOR STATS"));
        for (String line : lines) {
            assertTrue(!strip(line).toLowerCase().contains("achievement"));
        }

        StatsStore.Record filled = new StatsStore.Record();
        filled.xp = GameRules.XP_PER_LEVEL + 3400;
        filled.level = GameRules.levelFromXp(filled.xp);
        filled.wins = 12;
        filled.winstreak = 3;
        filled.kills = 10;
        filled.deaths = 4;
        filled.finalKills = 5;
        filled.finalDeaths = 2;
        filled.games = 20;
        filled.losses = 8;
        filled.beds = 7;
        filled.bedsLost = 3;
        String[] hot = ProfileStats.hologramLines(filled);
        assertTrue(strip(hot[1]).contains("2\u2605") || strip(hot[1]).contains("2★") || strip(hot[1]).contains("2"));
        assertTrue(strip(hot[3]).contains("12"));
        assertTrue(strip(hot[4]).contains("3"));

        assertEquals(1, ProfileStats.nextWinstreak(0, true));
        assertEquals(4, ProfileStats.nextWinstreak(3, true));
        assertEquals(0, ProfileStats.nextWinstreak(9, false));
        assertEquals("2.50", ProfileStats.ratio(5, 2));
        assertEquals("0.00", ProfileStats.ratio(0, 0));
        assertEquals("7.00", ProfileStats.ratio(7, 0));

        assertTrue(ProfileStats.overallLore(filled).size() >= 10);
        List<String> soloEmpty = ProfileStats.modeLore("Solo", filled, filled.solo);
        assertTrue(!strip(soloEmpty.get(0)).equals("Solo Statistics"));
        assertTrue(strip(soloEmpty.get(0)).contains("lifetime") || strip(soloEmpty.get(0)).contains("Games Played"));
        // Zero mode games + overall wins → show overall numbers under Solo/Doubles.
        boolean sawWins = false;
        for (String line : soloEmpty) {
            if (strip(line).contains("Wins:") && strip(line).contains("12")) sawWins = true;
        }
        assertTrue(sawWins);
        filled.solo.games = 5;
        filled.solo.wins = 2;
        List<String> soloTracked = ProfileStats.modeLore("Solo", filled, filled.solo);
        assertTrue(strip(soloTracked.get(0)).contains("Games Played"));
        assertTrue(strip(soloTracked.get(0)).contains("5") || strip(soloTracked.get(1)).contains("5")
            || strip(soloTracked.toString()).contains("Games Played: 5"));
        assertEquals(6, ProfileStats.hologramLines(null).length);
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("\u00A7.", "");
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
