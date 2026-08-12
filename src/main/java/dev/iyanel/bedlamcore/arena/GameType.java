package dev.iyanel.bedlamcore.arena;

public enum GameType {
    SOLO("Solo", 1),
    DOUBLES("Doubles", 2);

    private final String displayName;
    private final int teamSize;

    GameType(String displayName, int teamSize) {
        this.displayName = displayName;
        this.teamSize = teamSize;
    }

    public String displayName() { return displayName; }
    public int teamSize() { return teamSize; }

    public static GameType parse(String value) {
        if (value == null) return SOLO;
        try { return valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException ignored) { return SOLO; }
    }
}
