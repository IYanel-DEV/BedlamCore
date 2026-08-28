package dev.iyanel.bedlamcore.arena;

public enum GameType {
    SOLO("Solo", 1),
    DOUBLES("Doubles", 2),
    TRIOS("3v3v3v3", 3),
    QUADS("4v4v4v4", 4);

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
        String v = value.trim().toLowerCase();
        if (v.equals("trios") || v.equals("trio") || v.equals("3v3") || v.equals("3v3v3v3")) return TRIOS;
        if (v.equals("quads") || v.equals("quad") || v.equals("4v4") || v.equals("4v4v4v4")) return QUADS;
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return SOLO; }
    }
}
