package dev.iyanel.bedlamcore.leaderboard;

import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.StatsStore;

import java.util.Locale;

/**
 * A rankable stat category. Each reads a {@link StatsStore.StatSlice} (the whole {@code Record} for
 * Overall, or the {@code solo}/{@code doubles} sub-record per mode) plus, for whole-account values,
 * the parent {@code Record}. All extraction is null-safe and divide-by-zero-safe.
 *
 * <p>{@code winstreak}, {@code level}, {@code xp} and {@code tokens} live on the whole account, not on
 * a per-mode slice — for the Solo/Doubles tabs they fall back to the overall value (documented, since
 * {@code ModeStats} has no xp/level/tokens/winstreak).</p>
 */
public enum LeaderboardCategory {
    WINS("wins", "Wins"),
    KILLS("kills", "Kills"),
    FINAL_KILLS("finalkills", "Final Kills"),
    BEDS("beds", "Beds Broken"),
    WINSTREAK("winstreak", "Winstreak"),
    KDR("kdr", "K/D"),
    FKDR("fkdr", "F/K/D"),
    LEVEL("level", "Level"),
    XP("xp", "XP"),
    TOKENS("tokens", "Tokens");

    private final String key;
    private final String label;

    LeaderboardCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public boolean isRatio() {
        return this == KDR || this == FKDR;
    }

    /**
     * Monotonic sort magnitude (descending). Ties break by name then UUID (see
     * {@link GameRules#compareEntries}). {@code LEVEL} sorts by XP because level derives monotonically
     * from XP, which also gives XP the "tiebreak" the spec asks for.
     */
    public double sortValue(StatsStore.Record record, StatsStore.StatSlice slice) {
        if (record == null || slice == null) return 0.0;
        switch (this) {
            case WINS: return slice.wins();
            case KILLS: return slice.kills();
            case FINAL_KILLS: return slice.finalKills();
            case BEDS: return slice.bedsBroken();
            case WINSTREAK: return record.winstreak;
            case KDR: return GameRules.ratio1(slice.kills(), slice.deaths());
            case FKDR: return GameRules.ratio1(slice.finalKills(), slice.finalDeaths());
            case LEVEL: return record.xp; // level == 1 + xp/XP_PER_LEVEL, so xp is the natural order + tiebreak
            case XP: return record.xp;
            case TOKENS: return record.tokens;
            default: return 0.0;
        }
    }

    /** Human-readable value: integer counts with thousands separators, one decimal for ratios. */
    public String formatted(StatsStore.Record record, StatsStore.StatSlice slice) {
        if (record == null || slice == null) return "0";
        switch (this) {
            case WINS: return GameRules.commas(slice.wins());
            case KILLS: return GameRules.commas(slice.kills());
            case FINAL_KILLS: return GameRules.commas(slice.finalKills());
            case BEDS: return GameRules.commas(slice.bedsBroken());
            case WINSTREAK: return GameRules.commas(record.winstreak);
            case KDR: return GameRules.formatRatio1(GameRules.ratio1(slice.kills(), slice.deaths()));
            case FKDR: return GameRules.formatRatio1(GameRules.ratio1(slice.finalKills(), slice.finalDeaths()));
            case LEVEL: return String.valueOf(Math.max(1, record.level));
            case XP: return GameRules.commas(record.xp);
            case TOKENS: return GameRules.commas(record.tokens);
            default: return "0";
        }
    }

    /** Resolve a user-supplied key (case/format-insensitive, with a few friendly aliases), or null. */
    public static LeaderboardCategory byKey(String raw) {
        if (raw == null) return null;
        String k = raw.toLowerCase(Locale.US).replace("_", "").replace("-", "").replace(" ", "").replace("/", "");
        for (LeaderboardCategory c : values()) {
            if (c.key.equals(k)) return c;
        }
        if (k.equals("finalkill") || k.equals("fk")) return FINAL_KILLS;
        if (k.equals("bed") || k.equals("bedsbroken") || k.equals("bedbroken")) return BEDS;
        if (k.equals("kd") || k.equals("kdratio")) return KDR;
        if (k.equals("fkd") || k.equals("fkdratio") || k.equals("finalkd")) return FKDR;
        if (k.equals("ws") || k.equals("streak")) return WINSTREAK;
        if (k.equals("lvl") || k.equals("star") || k.equals("stars")) return LEVEL;
        if (k.equals("token")) return TOKENS;
        return null;
    }
}
