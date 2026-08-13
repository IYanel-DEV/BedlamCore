package dev.iyanel.bedlamcore.world;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;

public final class GameWorlds {
    private final BedlamCore plugin;

    public GameWorlds(BedlamCore plugin) { this.plugin = plugin; }

    public ArenaSettings create(GameType type) {
        String stem = "bedlam_" + type.name().toLowerCase() + "_";
        int number = 1;
        while (Bukkit.getWorld(stem + number) != null || new File(Bukkit.getWorldContainer(), stem + number).exists()) number++;
        String name = stem + number;
        World world = new WorldCreator(name).generator(oneBlockGenerator()).generateStructures(false).createWorld();
        if (world == null) throw new IllegalStateException("Could not create world " + name);
        world.getBlockAt(0, 64, 0).setType(Material.STONE);
        world.setSpawnLocation(0, 65, 0);
        world.setAutoSave(true);
        world.save();
        world.setAutoSave(false);
        return new ArenaSettings(name, type, name);
    }

    public boolean delete(ArenaSettings settings, Player operator) {
        String name = settings.worldName();
        if (!managedName(name)) {
            operator.sendMessage(ChatColor.RED + "Only BedlamCore-created worlds can be deleted.");
            return false;
        }
        World world = Bukkit.getWorld(name);
        if (world != null) {
            for (Player player : world.getPlayers()) {
                if (plugin.lobby().spawn() != null) player.teleport(plugin.lobby().spawn());
                else player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            if (!Bukkit.unloadWorld(world, false)) {
                operator.sendMessage(ChatColor.RED + "Could not unload " + name + ".");
                return false;
            }
        }
        File folder = new File(Bukkit.getWorldContainer(), name);
        try {
            if (!folder.getCanonicalFile().getParentFile().equals(Bukkit.getWorldContainer().getCanonicalFile())) return false;
        } catch (IOException exception) {
            operator.sendMessage(ChatColor.RED + "Could not verify the world folder.");
            return false;
        }
        deleteRecursively(pristineDir(name));
        if (folder.exists() && !deleteRecursively(folder)) {
            operator.sendMessage(ChatColor.RED + "Could not completely delete " + name + ". Check server file permissions.");
            return false;
        }
        return true;
    }

    public World load(ArenaSettings settings) {
        World world = Bukkit.getWorld(settings.worldName());
        if (world != null) {
            disableAutoSave(world);
            return world;
        }
        // Disk may still hold match dirt from a prior crash/save — restore Apply snapshot first.
        restorePristine(settings.worldName());
        WorldCreator creator = new WorldCreator(settings.worldName());
        if (managedName(settings.worldName())) creator.generator(oneBlockGenerator());
        world = creator.createWorld();
        if (world != null) disableAutoSave(world);
        return world;
    }

    public void disableAutoSave(World world) {
        if (world == null) return;
        world.setAutoSave(false);
        // Arena worlds: no natural animal/monster spawns (shopkeepers spawned by plugin still allowed).
        world.setSpawnFlags(false, false);
    }

    /** Setup Apply only: flush pristine map, then snapshot for crash-safe reloads. */
    public void saveOnce(World world) {
        if (world == null) return;
        world.save();
        world.setAutoSave(false);
        snapshotPristine(world.getName());
    }

    /** Unload without writing match dirt, restore pristine files, then load. */
    public void reloadDiscarding(ArenaSettings settings) {
        unloadDiscarding(settings);
        restorePristine(settings.worldName());
        load(settings);
    }

    public void unloadDiscarding(ArenaSettings settings) {
        World world = Bukkit.getWorld(settings.worldName());
        if (world == null) return;
        LocationFallback teleport = new LocationFallback(plugin);
        for (Player player : new java.util.ArrayList<Player>(world.getPlayers())) teleport.toLobby(player);
        for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) item.remove();
        world.setAutoSave(false);
        Bukkit.unloadWorld(world, false);
    }

    private File pristineDir(String worldName) {
        return new File(plugin.getDataFolder(), "pristine" + File.separator + worldName);
    }

    private void snapshotPristine(String worldName) {
        File src = new File(Bukkit.getWorldContainer(), worldName);
        if (!src.isDirectory()) return;
        File dst = pristineDir(worldName);
        try {
            deleteRecursively(dst);
            copyWorldFiles(src.toPath(), dst.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not snapshot pristine " + worldName + ": " + e.getMessage());
        }
    }

    private void restorePristine(String worldName) {
        if (Bukkit.getWorld(worldName) != null) return;
        File src = pristineDir(worldName);
        if (!src.isDirectory()) return;
        File dst = new File(Bukkit.getWorldContainer(), worldName);
        try {
            deleteRecursively(dst);
            copyWorldFiles(src.toPath(), dst.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not restore pristine " + worldName + ": " + e.getMessage());
        }
    }

    private static void copyWorldFiles(final Path from, final Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (name.equals("session.lock") || name.equals("uid.dat")) return FileVisitResult.CONTINUE;
                Path target = to.resolve(from.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) if (!deleteRecursively(child)) return false;
        return file.delete();
    }

    public static boolean managedName(String name) {
        return name != null && name.matches("bedlam_(solo|doubles)_[1-9][0-9]*");
    }

    public static ChunkGenerator oneBlockGenerator() { return new OneBlockGenerator(); }

    @SuppressWarnings("deprecation")
    private static final class OneBlockGenerator extends ChunkGenerator {
        @Override
        public byte[][] generateBlockSections(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomes) {
            return new byte[16][];
        }
    }

    private static final class LocationFallback {
        private final BedlamCore plugin;
        private LocationFallback(BedlamCore plugin) { this.plugin = plugin; }
        private void toLobby(Player player) {
            if (plugin.lobby().spawn() != null) player.teleport(plugin.lobby().spawn());
            else if (!Bukkit.getWorlds().isEmpty()) player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }
}
