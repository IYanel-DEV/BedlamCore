package dev.iyanel.bedlamcore.game;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Hypixel-like profile hologram / GUI lore helpers (no world). */
public final class ProfileStats {
    private ProfileStats() {
    }

    /** Hologram lines above the profile NPC (viewer-personal). Achievements omitted. */
    public static String[] hologramLines(StatsStore.Record stats) {
        int level = stats == null ? 1 : Math.max(1, stats.level);
        int xp = stats == null ? 0 : Math.max(0, stats.xp);
        int into = GameRules.xpIntoLevel(xp);
        int wins = stats == null ? 0 : stats.wins;
        int streak = stats == null ? 0 : stats.winstreak;
        return new String[] {
            ChatColor.GOLD + "Your Bed Wars Profile",
            ChatColor.GRAY + "Your Level: " + ChatColor.GREEN + level + "\u2605",
            ChatColor.GRAY + "Progress: " + ChatColor.AQUA + GameRules.compactXp(into)
                + ChatColor.GRAY + "/" + ChatColor.AQUA + GameRules.compactXp(GameRules.XP_PER_LEVEL),
            ChatColor.GRAY + "Total Wins: " + ChatColor.GREEN + wins,
            ChatColor.GRAY + "Current Winstreak: " + ChatColor.GREEN + streak,
            ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK FOR STATS"
        };
    }

    public static List<String> overallLore(StatsStore.Record stats) {
        return loreBlock(stats, stats, false);
    }

    public static List<String> modeLore(String header, StatsStore.Record overall, StatsStore.ModeStats mode) {
        StatsStore.StatSlice view = mode == null ? new StatsStore.ModeStats() : mode;
        // Legacy: overall filled before per-mode buckets existed — show lifetime under Solo/Doubles.
        boolean fallback = view.games() == 0 && overall != null && overall.wins > 0;
        return loreBlock(overall, fallback ? overall : view, fallback);
    }

    /** Lore only — display name already carries the section title (no duplicate header line). */
    private static List<String> loreBlock(StatsStore.Record overall, StatsStore.StatSlice slice, boolean legacyFallback) {
        List<String> lore = new ArrayList<String>();
        if (legacyFallback) {
            lore.add(ChatColor.DARK_GRAY + "Showing lifetime totals");
            lore.add(ChatColor.DARK_GRAY + "(no per-mode games yet)");
            lore.add("");
        }
        lore.add(line("Games Played", slice.games()));
        lore.add(line("Wins", slice.wins()));
        lore.add(line("Losses", slice.losses()));
        lore.add(line("Beds Broken", slice.bedsBroken()));
        lore.add(line("Beds Lost", slice.bedsLost()));
        lore.add(line("Kills", slice.kills()));
        lore.add(line("Deaths", slice.deaths()));
        lore.add(line("Final Kills", slice.finalKills()));
        lore.add(line("Final Deaths", slice.finalDeaths()));
        lore.add(line("Final K/D", ratio(slice.finalKills(), slice.finalDeaths())));
        lore.add(line("Winstreak", overall == null ? 0 : overall.winstreak));
        lore.add("");
        lore.add(ChatColor.GRAY + "Lifetime Wins: " + ChatColor.GREEN + (overall == null ? 0 : overall.wins));
        return lore;
    }

    private static String line(String label, int value) {
        return ChatColor.GRAY + label + ": " + ChatColor.GREEN + value;
    }

    private static String line(String label, String value) {
        return ChatColor.GRAY + label + ": " + ChatColor.GREEN + value;
    }

    public static String ratio(int kills, int deaths) {
        if (deaths <= 0) return kills <= 0 ? "0.00" : String.format(Locale.US, "%.2f", (double) kills);
        return String.format(Locale.US, "%.2f", (double) kills / (double) deaths);
    }

    public static int nextWinstreak(int current, boolean won) {
        if (won) return Math.max(0, current) + 1;
        return 0;
    }
}
