package dev.iyanel.bedlamcore.leaderboard;

/**
 * Ranking time window. Only {@link #ALL_TIME} is implemented today; {@link #WEEKLY}/{@link #MONTHLY}
 * are scaffolded so the display/command code is window-ready.
 *
 * <p>A later sprint would accumulate per-window tallies on top of the {@code lastSeen} epoch already
 * written by {@code StatsStore}: on each stat write, roll the delta into a bucket keyed by the current
 * ISO week / month, and reset a player's bucket the first time they play in a new period. The ranking
 * API ({@code LeaderboardService.ranking(category, mode, window)}) already threads the window through,
 * so only the aggregation source would change — not the display layer.</p>
 */
public enum LeaderboardWindow {
    ALL_TIME("All Time"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    private final String label;

    LeaderboardWindow(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
