package dev.iyanel.bedlamcore.world;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Bukkit-free world-name filter for importable maps. */
public final class ImportWorldNames {
    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList(
        "world", "world_nether", "world_the_end", "bedwarslobby"));

    private ImportWorldNames() {
    }

    public static boolean isImportCandidate(String name, Set<String> usedNames, String lobbyWorld) {
        if (name == null || name.isEmpty() || name.indexOf('.') >= 0) return false;
        if (SKIP.contains(name)) return false;
        if (name.toLowerCase(java.util.Locale.US).contains("lobby")) return false;
        if (usedNames != null && usedNames.contains(name)) return false;
        if (lobbyWorld != null && lobbyWorld.equals(name)) return false;
        return true;
    }
}