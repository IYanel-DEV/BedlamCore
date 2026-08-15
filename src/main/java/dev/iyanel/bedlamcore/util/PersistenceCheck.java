package dev.iyanel.bedlamcore.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/** Dependency-free smoke check for atomic file and directory replacement. */
public final class PersistenceCheck {
    private PersistenceCheck() {
    }

    public static void run() throws Exception {
        Path root = Files.createTempDirectory("bedlam-persistence-check-");
        try {
            Path yaml = root.resolve("stats.yml");
            AtomicFiles.writeUtf8(yaml, "tokens: 1\n");
            AtomicFiles.writeUtf8(yaml, "tokens: 2\n");
            assertEquals("tokens: 2\n", new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8));

            Path source = root.resolve("source");
            Files.createDirectories(source.resolve("region"));
            Files.write(source.resolve("level.dat"), new byte[] {1});
            Files.write(source.resolve("session.lock"), new byte[] {2});
            Files.write(source.resolve("region/r.0.0.mca"), new byte[] {3});
            Path target = root.resolve("target");
            Files.createDirectories(target);
            Files.write(target.resolve("old.dat"), new byte[] {4});
            AtomicFiles.replaceDirectoryFromCopy(source, target, Collections.singleton("session.lock"));
            assertTrue(Files.isRegularFile(target.resolve("level.dat")));
            assertTrue(Files.isRegularFile(target.resolve("region/r.0.0.mca")));
            assertFalse(Files.exists(target.resolve("session.lock")));
            assertFalse(Files.exists(target.resolve("old.dat")));
        } finally {
            AtomicFiles.deleteTree(root);
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }
}
