package dev.iyanel.bedlamcore.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;

/** Small crash-safe replacements for plugin YAML and pristine world directories. */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void writeUtf8(Path target, String value) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Target has no parent: " + target);
        Files.createDirectories(parent);
        Path temporary = parent.resolve(target.getFileName() + ".tmp");
        Files.write(temporary, value.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        moveReplacing(temporary, target);
    }

    public static void replaceDirectoryFromCopy(Path source, Path target, Set<String> excludedNames) throws IOException {
        if (!Files.isDirectory(source)) throw new IOException("Source is not a directory: " + source);
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Target has no parent: " + target);
        Files.createDirectories(parent);
        Path temporary = parent.resolve(target.getFileName() + ".tmp");
        Path backup = parent.resolve(target.getFileName() + ".bak");
        deleteTree(temporary);
        deleteTree(backup);
        copyDirectory(source, temporary, excludedNames == null ? Collections.<String>emptySet() : excludedNames);

        boolean backedUp = false;
        try {
            if (Files.exists(target)) {
                moveReplacing(target, backup);
                backedUp = true;
            }
            moveReplacing(temporary, target);
            if (backedUp) deleteTree(backup);
        } catch (IOException failure) {
            deleteTree(temporary);
            if (backedUp && !Files.exists(target) && Files.exists(backup)) {
                try { moveReplacing(backup, target); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }

    public static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyDirectory(final Path source, final Path target, final Set<String> excludedNames) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (excludedNames.contains(file.getFileName().toString())) return FileVisitResult.CONTINUE;
                Path copy = target.resolve(source.relativize(file).toString());
                Files.createDirectories(copy.getParent());
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
