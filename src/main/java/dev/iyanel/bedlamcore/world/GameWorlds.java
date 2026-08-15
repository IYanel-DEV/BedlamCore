package dev.iyanel.bedlamcore.world;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.AtomicFiles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import dev.iyanel.bedlamcore.lobby.LobbyNpcService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class GameWorlds {
    private static final Set<String> TRANSIENT_WORLD_FILES = new HashSet<String>(Arrays.asList("session.lock", "uid.dat"));
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
        disableAutoSave(world);
        return new ArenaSettings(name, type, name);
    }

    /** World folders on disk with level.dat that are not yet registered as arenas. */
    public List<String> listImportable() {
        Set<String> used = new HashSet<String>();
        for (ArenaManager manager : plugin.games().arenas()) {
            used.add(manager.arena().settings().worldName());
            used.add(manager.arena().settings().id());
        }
        String lobbyWorld = plugin.lobby().spawn() != null && plugin.lobby().spawn().getWorld() != null
            ? plugin.lobby().spawn().getWorld().getName() : null;
        File container = Bukkit.getWorldContainer();
        File[] children = container == null ? null : container.listFiles();
        List<String> result = new ArrayList<String>();
        if (children == null) return result;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String name = child.getName();
            if (!ImportWorldNames.isImportCandidate(name, used, lobbyWorld)) continue;
            if (!new File(child, "level.dat").isFile()) continue;
            result.add(name);
        }
        Collections.sort(result);
        return result;
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
        String name = settings.worldName();
        World world = Bukkit.getWorld(name);
        if (world != null) {
            // Crash/reload path: never keep in-memory match dirt; discard without save first.
            for (Item item : new java.util.ArrayList<Item>(world.getEntitiesByClass(Item.class))) item.remove();
            LocationFallback teleport = new LocationFallback(plugin);
            for (Player player : new java.util.ArrayList<Player>(world.getPlayers())) teleport.toLobby(player);
            world.setAutoSave(false);
            Bukkit.unloadWorld(world, false);
        }
        File pristine = pristineDir(name);
        if (pristine.isDirectory()) {
            restorePristine(name);
        } else {
            plugin.getLogger().warning("No pristine snapshot for " + name
                + " — Apply the arena once to create plugins/BedlamCore/pristine/" + name
                + ". Loading disk world without restore (match builds may linger after crashes).");
        }
        WorldCreator creator = new WorldCreator(name);
        if (managedName(name)) creator.generator(oneBlockGenerator());
        world = creator.createWorld();
        if (world != null) {
            disableAutoSave(world);
            for (Item item : new java.util.ArrayList<Item>(world.getEntitiesByClass(Item.class))) item.remove();
        }
        return world;
    }

    public void disableAutoSave(World world) {
        if (world == null) return;
        world.setAutoSave(false);
        // Arena worlds: no natural animal/monster spawns (shopkeepers spawned by plugin still allowed).
        world.setSpawnFlags(false, false);
        lockAlwaysDay(world);
        clearWildMonsters(world);
    }

    /** Day + clear weather; cycles off (lobby and arena worlds). Monsters never. */
    public void lockAlwaysDay(World world) {
        if (world == null) return;
        world.setTime(GameRules.ALWAYS_DAY_TIME);
        world.setStorm(false);
        world.setThundering(false);
        // Keep animals as-is; never allow natural monsters in Bedlam worlds.
        world.setSpawnFlags(world.getAllowAnimals(), false);
        try {
            world.setGameRuleValue("doDaylightCycle", "false");
            world.setGameRuleValue("doWeatherCycle", "false");
        } catch (Throwable ignored) { }
    }

    /** Remove hostile mobs that are not plugin NPCs / pets. */
    public void clearWildMonsters(World world) {
        if (world == null) return;
        for (org.bukkit.entity.Entity entity : new ArrayList<org.bukkit.entity.Entity>(world.getEntities())) {
            if (!(entity instanceof Monster)) continue;
            if (LobbyNpcService.isPluginNpc(entity) || LobbyNpcService.isPet(entity)) continue;
            entity.remove();
        }
    }

    /** Setup Apply only: flush pristine map, then snapshot for crash-safe reloads. */
    public void saveOnce(World world) {
        if (world == null) return;
        // Never bake win-dragon grief into the pristine snapshot.
        if (plugin.cosmetics() != null && plugin.cosmetics().worldHasWinDragonGrief(world)) {
            plugin.getLogger().warning("Refusing pristine snapshot for " + world.getName()
                + " — win dragon grief is active (wait for match reset).");
            return;
        }
        world.setAutoSave(false);
        world.save();
        world.setAutoSave(false);
        snapshotPristine(world.getName());
    }

    /** Crash/stop policy: arena worlds unload without saving so grief never hits disk. */
    public static boolean unloadArenaWithoutSave() {
        return true;
    }

    /** Next-round policy: match reset restores from pristine before WAITING. */
    public static boolean resetRestoresPristine() {
        return true;
    }

    /** Unload without writing match dirt, restore pristine files, then load. */
    public void reloadDiscarding(ArenaSettings settings) {
        unloadDiscarding(settings);
        load(settings);
    }

    public void unloadDiscarding(ArenaSettings settings) {
        World world = Bukkit.getWorld(settings.worldName());
        if (world == null) return;
        LocationFallback teleport = new LocationFallback(plugin);
        for (Player player : new java.util.ArrayList<Player>(world.getPlayers())) teleport.toLobby(player);
        for (Item item : world.getEntitiesByClass(Item.class)) item.remove();
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
            AtomicFiles.replaceDirectoryFromCopy(src.toPath(), dst.toPath(), TRANSIENT_WORLD_FILES);
            plugin.getLogger().info("Saved pristine snapshot for " + worldName);
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
            AtomicFiles.replaceDirectoryFromCopy(src.toPath(), dst.toPath(), TRANSIENT_WORLD_FILES);
            plugin.getLogger().info("Restored pristine world " + worldName);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not restore pristine " + worldName + ": " + e.getMessage());
        }
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