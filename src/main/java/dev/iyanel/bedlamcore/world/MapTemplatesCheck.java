package dev.iyanel.bedlamcore.world;

import java.util.Arrays;

/** Dependency-free smoke check for template world-name remapping. */
public final class MapTemplatesCheck {
    private MapTemplatesCheck() {
    }

    public static void run() {
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
