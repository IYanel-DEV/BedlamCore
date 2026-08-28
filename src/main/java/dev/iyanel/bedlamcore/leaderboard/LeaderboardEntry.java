package dev.iyanel.bedlamcore.leaderboard;

import dev.iyanel.bedlamcore.game.StatsStore;

import java.util.UUID;

/** One ranked row: 1-based rank, player identity, sort magnitude, display value, and a stat snapshot. */
public final class LeaderboardEntry {
    private final int rank;
    private final UUID uuid;
    private final String name;
    private final double value;
    private final String formattedValue;
    private final StatsStore.Record snapshot;

    public LeaderboardEntry(int rank, UUID uuid, String name, double value, String formattedValue,
                            StatsStore.Record snapshot) {
        this.rank = rank;
        this.uuid = uuid;
        this.name = name == null ? "?" : name;
        this.value = value;
        this.formattedValue = formattedValue == null ? "0" : formattedValue;
        this.snapshot = snapshot;
    }

    public int rank() {
        return rank;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public double value() {
        return value;
    }

    public String formattedValue() {
        return formattedValue;
    }

    public StatsStore.Record snapshot() {
        return snapshot;
    }
}
