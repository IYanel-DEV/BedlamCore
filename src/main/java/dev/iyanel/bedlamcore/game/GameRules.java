package dev.iyanel.bedlamcore.game;

import java.util.List;
import java.util.Map;

public final class GameRules {
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
}
