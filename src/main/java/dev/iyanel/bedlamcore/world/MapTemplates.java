package dev.iyanel.bedlamcore.world;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.util.AtomicFiles;
import org.bukkit.Bukkit;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bundled map templates (JAR resources → plugin data → server world container).
 * ponytail: one hardcoded template for now; catalog grows when more zips ship.
 * <p>
 * {@code bedwars-e2560} is a 1.8-era anvil world. Paper 1.13+ converts regions on first
 * load; we strip {@code session.lock}/{@code entities}/{@code poi} before createWorld so
 * conversion does not freeze the main thread. Dual {@code .mca}/{@code .mcr} in the zip is fine.
 */
public final class MapTemplates {
    public static final String BEDWARS_E2560 = "bedwars-e2560";
    /** Chained: a 4-island bundled template, playable only as Trios (4x3) or Quads (4x4). */
    public static final String CHAINED = "chained";
    private static final List<String> IDS = Collections.unmodifiableList(Arrays.asList(BEDWARS_E2560, CHAINED));
    private static final Set<String> SKIP_COPY = new HashSet<String>(Arrays.asList(
        "arena.yml", "session.lock", "uid.dat", ".DS_Store"));

    private final BedlamCore plugin;

    public MapTemplates(BedlamCore plugin) {
        this.plugin = plugin;
    }

    public List<String> list() {
        return IDS;
    }

    /**
     * Modes a template may materialize as. {@code chained} is a 4-team island map → Trios/Quads only;
     * {@code bedwars-e2560} (and any other id) stays Solo/Doubles. Enforced hard by {@link #materialize}
     * and used by the setup GUI to render only the valid buttons.
     */
    public EnumSet<GameType> allowedModes(String id) {
        if (CHAINED.equals(id)) return EnumSet.of(GameType.TRIOS, GameType.QUADS);
        return EnumSet.of(GameType.SOLO, GameType.DOUBLES);
    }

    /** Extract jar zip to {@code plugins/BedlamCore/templates/<id>/} if missing {@code arena.yml}. */
    public Path ensureExtracted(String id) throws IOException {
        if (!IDS.contains(id)) throw new IOException("Unknown template: " + id);
        Path dir = plugin.getDataFolder().toPath().resolve("templates").resolve(id);
        if (Files.isRegularFile(dir.resolve("arena.yml")) && Files.isRegularFile(dir.resolve("level.dat"))) {
            return dir;
        }
        Files.createDirectories(dir.getParent());
        String resource = "templates/" + id + ".zip";
        InputStream raw = plugin.getResource(resource);
        if (raw == null) throw new IOException("Missing jar resource " + resource);
        Path temporary = dir.resolveSibling(id + ".extracting");
        try {
            AtomicFiles.deleteTree(temporary);
            Files.createDirectories(temporary);
            unzip(raw, temporary);
            if (!Files.isRegularFile(temporary.resolve("arena.yml"))) {
                throw new IOException("Template zip missing arena.yml: " + id);
            }
            AtomicFiles.replaceDirectoryFromCopy(temporary, dir, Collections.<String>emptySet());
        } finally {
            try { raw.close(); } catch (IOException ignored) { }
            AtomicFiles.deleteTree(temporary);
        }
        return dir;
    }

    /**
     * Resolve world name, copy template world if needed, load preset with Solo/Doubles.
     * Prefer {@code bedwars-e2560}; on arena collision use {@code bedwars-e2560_solo|/doubles}.
     */
    public ArenaSettings materialize(String templateId, GameType type) throws IOException {
        if (type == null) type = GameType.SOLO;
        EnumSet<GameType> allowed = allowedModes(templateId);
        if (!allowed.contains(type)) {
            throw new IOException(templateId + " cannot be set up as " + type.displayName()
                + "; allowed modes: " + allowed);
        }
        Path templateDir = ensureExtracted(templateId);
        String worldName = resolveWorldName(templateId, type);
        if (plugin.games().byId(worldName) != null || plugin.games().arenaInWorld(worldName) != null) {
            throw new IOException(worldName + " already has an arena configured.");
        }
        // Not a live arena → clear any stale classic/dimension leftovers so the fresh template copy wins.
        // On 26.2 a lingering managed dimension would otherwise shadow the copy and null out the setup.
        GameWorlds.purgeWorldFolders(worldName);
        Path dest = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.isDirectory(dest) || !Files.isRegularFile(dest.resolve("level.dat"))) {
            AtomicFiles.replaceDirectoryFromCopy(templateDir, dest, SKIP_COPY);
        }
        GameWorlds.prepareCopiedWorldFolder(dest.toFile());
        return plugin.arenas().readTemplatePreset(
            templateDir.resolve("arena.yml").toFile(), worldName, type, worldName);
    }

    private String resolveWorldName(String templateId, GameType type) {
        if (plugin.games().byId(templateId) == null && plugin.games().arenaInWorld(templateId) == null) {
            return templateId;
        }
        return templateId + "_" + type.name().toLowerCase();
    }

    private static void unzip(InputStream raw, Path target) throws IOException {
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.isEmpty() || name.contains("..")) {
                    zip.closeEntry();
                    continue;
                }
                Path out = target.resolve(name).normalize();
                if (!out.startsWith(target)) {
                    zip.closeEntry();
                    continue;
                }
                if (entry.isDirectory() || name.endsWith("/")) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
    }
}
