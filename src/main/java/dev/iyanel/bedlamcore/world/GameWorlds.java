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
        if (folder.exists() && !deleteRecursively(folder)) {
            operator.sendMessage(ChatColor.RED + "Could not completely delete " + name + ". Check server file permissions.");
            return false;
        }
        return true;
    }

    public World load(ArenaSettings settings) {
        World world = Bukkit.getWorld(settings.worldName());
        if (world != null) return world;
        WorldCreator creator = new WorldCreator(settings.worldName());
        if (managedName(settings.worldName())) creator.generator(oneBlockGenerator());
        return creator.createWorld();
    }

    private static boolean deleteRecursively(File file) {
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
}
