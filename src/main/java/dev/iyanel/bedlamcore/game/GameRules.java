package dev.iyanel.bedlamcore.game;

import java.util.List;
import java.util.Map;

public final class GameRules {
    public static final double GEN_PROTECT = 3.0;
    public static final double SPAWN_PROTECT = 4.0;
    public static final double FORGE_PROTECT = 3.0;
    public static final double SHOP_PROTECT = 2.0;
    public static final double DISPLAY_VIEW = 20.0;
    public static final int ARENA_BOUND_PAD = 40;

    private GameRules() {
    }

    public static <T> T leastPopulated(List<T> orderedTeams, Map<T, Integer> sizes) {
        T best = null;
        int bestSize = Integer.MAX_VALUE;
        for (T team : orderedTeams) {
            int size = sizes.containsKey(team) ? sizes.get(team) : 0;
            if (size < bestSize) {
                best = team;
                bestSize = size;
            }
        }
        return best;
    }

    public static boolean canRespawn(boolean bedAlive, boolean alreadyEliminated) {
        return bedAlive && !alreadyEliminated;
    }

    public static int generatorTier(int elapsedSeconds, int tierTwoSeconds, int tierThreeSeconds) {
        if (elapsedSeconds >= tierThreeSeconds) return 3;
        if (elapsedSeconds >= tierTwoSeconds) return 2;
        return 1;
    }

    /** Instant void kill when at or below waiting-spawn Y minus this drop. */
    public static double voidKillY(double waitingSpawnY) {
        return waitingSpawnY - 30.0;
    }

    public static boolean tooHigh(int blockY, int waitingSpawnY) {
        return blockY > waitingSpawnY;
    }

    public static boolean inRadius(double dx, double dy, double dz, double radius) {
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static int swordRank(String materialName) {
        if (materialName == null) return 0;
        if (materialName.contains("DIAMOND_SWORD")) return 3;
        if (materialName.contains("IRON_SWORD")) return 2;
        if (materialName.contains("STONE_SWORD")) return 1;
        if (materialName.contains("WOOD") && materialName.contains("SWORD")) return 0;
        if (materialName.endsWith("_SWORD")) return 0;
        return -1;
    }

    public static boolean isSword(String materialName) {
        return swordRank(materialName) >= 0;
    }
}
