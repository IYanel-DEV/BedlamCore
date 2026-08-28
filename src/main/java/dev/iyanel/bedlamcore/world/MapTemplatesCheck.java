package dev.iyanel.bedlamcore.world;

import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.util.AtomicFiles;

import java.util.Arrays;
import java.util.EnumSet;

/** Dependency-free smoke check for template world-name remapping. */
public final class MapTemplatesCheck {
    private MapTemplatesCheck() {
    }

    public static void run() throws Exception {
        String yaml = ""
            + "arena:\n"
            + "  mode: SOLO\n"
            + "  world: bedwars-e2560\n"
            + "  waiting-spawn: bedwars-e2560,1.0,2.0,3.0,0.0,0.0\n";
        String remapped = remapWorld(yaml, "bedwars-e2560", "bedwars-e2560_solo");
        assertTrue(remapped.contains("world: bedwars-e2560_solo"));
        assertTrue(remapped.contains("bedwars-e2560_solo,1.0,2.0,3.0"));
        assertFalse(remapped.contains("bedwars-e2560,"));
        assertEquals("bedwars-e2560", preferredWorld("bedwars-e2560", false));
        assertEquals("bedwars-e2560_doubles", preferredWorld("bedwars-e2560", true));
        assertTrue(Arrays.asList("bedwars-e2560").contains(MapTemplates.BEDWARS_E2560));
        // chained template: bundled id, Trios/Quads only, and world-name remap works like e2560.
        MapTemplates templates = new MapTemplates(null);
        assertTrue(templates.list().contains(MapTemplates.CHAINED));
        assertTrue(templates.allowedModes(MapTemplates.CHAINED).equals(EnumSet.of(GameType.TRIOS, GameType.QUADS)));
        assertTrue(templates.allowedModes(MapTemplates.BEDWARS_E2560).equals(EnumSet.of(GameType.SOLO, GameType.DOUBLES)));
        String chainedYaml = ""
            + "arena:\n"
            + "  mode: TRIOS\n"
            + "  world: chained\n"
            + "  waiting-spawn: chained,2.6,114.1,0.4,271.7,90.0\n";
        String chainedRemap = remapWorld(chainedYaml, "chained", "chained_trios");
        assertTrue(chainedRemap.contains("world: chained_trios"));
        assertTrue(chainedRemap.contains("chained_trios,2.6,114.1,0.4"));
        // prepareCopiedWorldFolder: drop locks + modern entity trees before Paper converts 1.8 anvil.
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("bedlam-tmpl");
        try {
            java.nio.file.Files.write(tmp.resolve("session.lock"), new byte[] {1});
            java.nio.file.Files.write(tmp.resolve("uid.dat"), new byte[] {2});
            java.nio.file.Files.createDirectory(tmp.resolve("entities"));
            java.nio.file.Files.createDirectory(tmp.resolve("poi"));
            GameWorlds.prepareCopiedWorldFolder(tmp.toFile());
            assertFalse(java.nio.file.Files.exists(tmp.resolve("session.lock")));
            assertFalse(java.nio.file.Files.exists(tmp.resolve("uid.dat")));
            assertFalse(java.nio.file.Files.exists(tmp.resolve("entities")));
            assertFalse(java.nio.file.Files.exists(tmp.resolve("poi")));
        } finally {
            AtomicFiles.deleteTree(tmp);
        }
        // Paper 26 conflict: classic region + world/dimensions/minecraft/<name>/region both present.
        // The migrated dimension holds live setup edits, so keep it and retire the stale classic source;
        // re-importing the classic folder would wipe the player's arena setup on every restart.
        java.nio.file.Path container = java.nio.file.Files.createTempDirectory("bedlam-dim");
        try {
            java.nio.file.Path classic = container.resolve("bedwars-e2560");
            java.nio.file.Files.createDirectories(classic.resolve("region"));
            java.nio.file.Files.write(classic.resolve("region").resolve("r.0.0.mca"), new byte[] {1});
            java.nio.file.Path dim = container.resolve("world").resolve("dimensions")
                .resolve("minecraft").resolve("bedwars-e2560");
            java.nio.file.Files.createDirectories(dim.resolve("region"));
            java.nio.file.Files.write(dim.resolve("region").resolve("r.0.0.mca"), new byte[] {2});
            GameWorlds.clearPaperDimensionMigrationConflict(classic.toFile());
            assertTrue(java.nio.file.Files.exists(dim.resolve("region").resolve("r.0.0.mca")));
            assertFalse(java.nio.file.Files.exists(classic));
        } finally {
            AtomicFiles.deleteTree(container);
        }
        // Reserved worlds (primary level + vanilla dimensions) are never touched by folder surgery.
        java.nio.file.Path guard = java.nio.file.Files.createTempDirectory("bedlam-guard");
        try {
            java.nio.file.Path world = guard.resolve("world");
            java.nio.file.Files.createDirectories(world.resolve("region"));
            java.nio.file.Files.write(world.resolve("region").resolve("r.0.0.mca"), new byte[] {1});
            java.nio.file.Path dim = guard.resolve("world").resolve("dimensions")
                .resolve("minecraft").resolve("world");
            java.nio.file.Files.createDirectories(dim.resolve("region"));
            java.nio.file.Files.write(dim.resolve("region").resolve("r.0.0.mca"), new byte[] {2});
            GameWorlds.clearPaperDimensionMigrationConflict(world.toFile());
            assertTrue(java.nio.file.Files.exists(world.resolve("region").resolve("r.0.0.mca")));
        } finally {
            AtomicFiles.deleteTree(guard);
        }
    }

    /** Shared remap used by ArenaRepository; kept here so coreCheck can assert without Bukkit. */
    public static String remapWorld(String yaml, String fromWorld, String toWorld) {
        if (fromWorld == null || toWorld == null || fromWorld.equals(toWorld)) return yaml;
        String out = yaml.replace(fromWorld + ",", toWorld + ",");
        out = out.replace("world: " + fromWorld, "world: " + toWorld);
        return out;
    }

    public static String preferredWorld(String templateId, boolean arenaTaken) {
        return arenaTaken ? templateId + "_doubles" : templateId;
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
