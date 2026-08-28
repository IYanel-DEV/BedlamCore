package dev.iyanel.bedlamcore.leaderboard;

import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds and caches Hypixel-style rankings from {@link StatsStore}, entirely on the main thread but
 * throttled: at most one full recompute every {@code refresh-seconds}, and only when a stat write has
 * marked the cache stale. Read APIs return cached, immutable snapshots so a busy lobby never thrashes.
 */
public final class LeaderboardService {
    /** Overall (null) + the two per-mode boards. */
    public static final GameType[] MODES = {null, GameType.SOLO, GameType.DOUBLES, GameType.TRIOS, GameType.QUADS};
    private static final long CHECK_TICKS = 20L; // re-evaluate the throttle roughly once a second

    private final JavaPlugin plugin;
    private final StatsStore stats;

    private final Map<String, List<LeaderboardEntry>> topByKey = new HashMap<String, List<LeaderboardEntry>>();
    private final Map<String, Map<UUID, Integer>> rankByKey = new HashMap<String, Map<UUID, Integer>>();

    private volatile boolean dirty = true;
    private long lastRefresh;
    private BukkitTask task;

    public LeaderboardService(JavaPlugin plugin, StatsStore stats) {
        this.plugin = plugin;
        this.stats = stats;
        stats.setChangeListener(new Runnable() {
            @Override public void run() { dirty = true; }
        });
        refresh();
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(); }
        }, CHECK_TICKS, CHECK_TICKS);
    }

    public void shutdown() {
        if (task != null) { task.cancel(); task = null; }
    }

    /** Force a recompute now (used by reload). */
    public void reload() {
        dirty = true;
        refresh();
    }

    public static String modeLabel(GameType mode) {
        return mode == null ? "Overall" : mode.displayName();
    }

    public int topN() {
        return Math.max(1, Math.min(50, GameRules.LEADERBOARD_TOP_N));
    }

    /** Cached top rows for a board (never null; empty when disabled or nobody qualifies). */
    public List<LeaderboardEntry> ranking(LeaderboardCategory category, GameType mode, LeaderboardWindow window) {
        if (category == null || window != LeaderboardWindow.ALL_TIME) return Collections.emptyList();
        List<LeaderboardEntry> list = topByKey.get(key(category, mode));
        return list == null ? Collections.<LeaderboardEntry>emptyList() : list;
    }

    /** 1-based rank of a player over the full filtered ordering, or -1 if unranked. */
    public int rankOf(UUID uuid, LeaderboardCategory category, GameType mode) {
        if (uuid == null || category == null) return -1;
        Map<UUID, Integer> ranks = rankByKey.get(key(category, mode));
        if (ranks == null) return -1;
        Integer rank = ranks.get(uuid);
        return rank == null ? -1 : rank;
    }

    /**
     * Hologram board lines top-to-bottom: title, category subtitle, then up to {@link #topN()} rows
     * ('&'-translated, per the configured rank colours). Used by the lobby board; diffed by the caller.
     */
    public List<String> boardLines(LeaderboardCategory category, GameType mode) {
        List<String> out = new ArrayList<String>();
        out.add(ChatColor.translateAlternateColorCodes('&', GameRules.LEADERBOARD_NPC_TITLE));
        String header = category == null ? "Wins" : category.label();
        out.add(ChatColor.GRAY + header + (mode == null ? "" : " " + ChatColor.DARK_GRAY + "(" + modeLabel(mode) + ")"));
        List<LeaderboardEntry> rows = ranking(category, mode, LeaderboardWindow.ALL_TIME);
        if (rows.isEmpty()) {
            out.add(ChatColor.GRAY + "No ranked players yet");
        } else {
            String valueColor = ChatColor.translateAlternateColorCodes('&', GameRules.LEADERBOARD_FMT_VALUE);
            for (LeaderboardEntry entry : rows) {
                String rankColor = ChatColor.translateAlternateColorCodes('&', GameRules.rankColor(entry.rank()));
                out.add(rankColor + "#" + entry.rank() + " " + entry.name() + "  " + valueColor + entry.formattedValue());
            }
        }
        // Footer: Hypixel-style "Click ..." line (config subtitle) so the board reads as clickable.
        out.add(ChatColor.translateAlternateColorCodes('&', GameRules.LEADERBOARD_NPC_SUBTITLE));
        return out;
    }

    // ------------------------------------------------------------------ recompute

    private void tick() {
        if (!GameRules.LEADERBOARD_ENABLED) return;
        long now = System.currentTimeMillis();
        if (dirty && now - lastRefresh >= GameRules.LEADERBOARD_REFRESH_SECONDS * 1000L) {
            refresh();
        }
    }

    private void refresh() {
        lastRefresh = System.currentTimeMillis();
        dirty = false;
        topByKey.clear();
        rankByKey.clear();
        if (!GameRules.LEADERBOARD_ENABLED) return;

        Map<UUID, StatsStore.Record> src = stats.snapshot();
        if (src.isEmpty()) return;
        int minGames = GameRules.LEADERBOARD_MIN_GAMES;
        int top = topN();
        Map<UUID, String> names = new HashMap<UUID, String>();

        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            for (GameType mode : MODES) {
                List<LeaderboardEntry> all = new ArrayList<LeaderboardEntry>();
                for (Map.Entry<UUID, StatsStore.Record> e : src.entrySet()) {
                    StatsStore.Record record = e.getValue();
                    StatsStore.StatSlice slice = slice(record, mode);
                    if (!GameRules.qualifies(slice.games(), minGames)) continue;
                    UUID uuid = e.getKey();
                    String name = names.get(uuid);
                    if (name == null) { name = resolveName(uuid); names.put(uuid, name); }
                    all.add(new LeaderboardEntry(0, uuid, name,
                        category.sortValue(record, slice), category.formatted(record, slice), record));
                }
                Collections.sort(all, ENTRY_ORDER);
                Map<UUID, Integer> ranks = new HashMap<UUID, Integer>();
                List<LeaderboardEntry> topRows = new ArrayList<LeaderboardEntry>();
                for (int i = 0; i < all.size(); i++) {
                    LeaderboardEntry base = all.get(i);
                    int rank = i + 1;
                    ranks.put(base.uuid(), rank);
                    if (i < top) {
                        topRows.add(new LeaderboardEntry(rank, base.uuid(), base.name(),
                            base.value(), base.formattedValue(), base.snapshot()));
                    }
                }
                String key = key(category, mode);
                topByKey.put(key, Collections.unmodifiableList(topRows));
                rankByKey.put(key, ranks);
            }
        }
    }

    private static final Comparator<LeaderboardEntry> ENTRY_ORDER = new Comparator<LeaderboardEntry>() {
        @Override public int compare(LeaderboardEntry a, LeaderboardEntry b) {
            return GameRules.compareEntries(a.value(), a.name(), a.uuid().toString(),
                b.value(), b.name(), b.uuid().toString());
        }
    };

    private static StatsStore.StatSlice slice(StatsStore.Record record, GameType mode) {
        if (mode == null) return record;
        return record.mode(mode);
    }

    private static String key(LeaderboardCategory category, GameType mode) {
        return category.name() + '|' + (mode == null ? "ALL" : mode.name());
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline == null ? null : offline.getName();
            if (name != null && !name.isEmpty()) return name;
        } catch (Throwable ignored) { }
        String s = uuid.toString();
        return s.substring(0, Math.min(8, s.length()));
    }
}
