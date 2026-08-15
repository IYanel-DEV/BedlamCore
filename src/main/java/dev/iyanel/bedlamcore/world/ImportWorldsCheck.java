package dev.iyanel.bedlamcore.world;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Dependency-free smoke check for importable-world name filtering. */
public final class ImportWorldsCheck {
    private ImportWorldsCheck() {
    }

    public static void run() {
        Set<String> used = new HashSet<String>();
        used.add("bedwars-taken");
        assertTrue(ImportWorldNames.isImportCandidate("bedwars-e2560", used, "world"));
        assertFalse(ImportWorldNames.isImportCandidate("world", used, null));
        assertFalse(ImportWorldNames.isImportCandidate("bedwars-taken", used, null));
        assertFalse(ImportWorldNames.isImportCandidate("lobby", used, "lobby"));
        assertFalse(ImportWorldNames.isImportCandidate("weird.name", Collections.<String>emptySet(), null));
        assertFalse(ImportWorldNames.isImportCandidate("", used, null));
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }
}