package dev.iyanel.bedlamcore.world;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.AtomicFiles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
        if (name == null || name.isEmpty()) {
            operator.sendMessage(ChatColor.RED + "No world name to delete.");
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
        if (isReservedWorldName(name)) {
            operator.sendMessage(ChatColor.RED + "Refusing to delete reserved world " + name + ".");
            return false;
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
        // Paper 26+ keeps the live world at world/dimensions/minecraft/<name>. Deleting only the classic
        // top-level folder leaves that stale build on disk; recreating the same template then reloads the
        // OLD build (clearPaperDimensionMigrationConflict keeps the dimension) and its blocks no longer
        // match the fresh arena.yml → "setups missing". Remove the migrated dimension folder too.
        File dimension = new File(Bukkit.getWorldContainer(), "world" + File.separator + "dimensions"
            + File.separator + "minecraft" + File.separator + name);
        if (dimension.exists() && !deleteRecursively(dimension)) {
            operator.sendMessage(ChatColor.RED + "Could not delete the migrated world data for " + name
                + " (world/dimensions/minecraft/" + name + "). Check server file permissions.");
            return false;
        }
        return true;
    }

    public World load(ArenaSettings settings) {
        String name = settings.worldName();
        World world = Bukkit.getWorld(name);
        if (world != null) {
            // Crash/reload path: never keep in-memory match dirt; discard without save first.
            clearLooseItemsNearSpawn(world);
            LocationFallback teleport = new LocationFallback(plugin);
            for (Player player : new java.util.ArrayList<Player>(world.getPlayers())) teleport.toLobby(player);
            world.setAutoSave(false);
            Bukkit.unloadWorld(world, false);
        }
        // Paper 26+ keeps this world under world/dimensions/minecraft/<name> and manages its files.
        // Copying a snapshot over that live folder fights Paper's storage and corrupts region headers,
        // so on managed servers we let Paper reload the on-disk world (Apply saved the setup there).
        if (!usesManagedDimensions()) {
            File pristine = pristineDir(name);
            if (pristine.isDirectory()) {
                restorePristine(name);
            } else {
                plugin.getLogger().warning("No pristine snapshot for " + name
                    + " — Apply the arena once to create plugins/BedlamCore/pristine/" + name
                    + ". Loading disk world without restore (match builds may linger after crashes).");
            }
        }
        prepareCopiedWorldFolder(new File(Bukkit.getWorldContainer(), name));
        WorldCreator creator = new WorldCreator(name);
        if (managedName(name)) creator.generator(oneBlockGenerator());
        world = creator.createWorld();
        if (world != null) {
            disableAutoSave(world);
            clearLooseItemsNearSpawn(world);
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

    /** Remove hostile mobs near spawn that are not plugin NPCs / pets.
     * Nearby only — full world.getEntities freezes Paper 26.x on large/converted maps. */
    public void clearWildMonsters(World world) {
        if (world == null) return;
        Location spawn = world.getSpawnLocation();
        if (spawn == null) return;
        // ponytail: 96-block box; far leftovers despawn with spawn flags off. Full scan if maps ship dungeon mobs far from spawn.
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(spawn, 96, 96, 96)) {
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
        clearLooseItemsNearSpawn(world);
        world.setAutoSave(false);
        Bukkit.unloadWorld(world, false);
    }

    /**
     * After copying a 1.8 (or any) template: drop locks and modern entity/poi trees so first
     * {@code createWorld} lets Paper convert anvil without a sync entity-wipe storm.
     * Region may ship dual {@code .mca}/{@code .mcr}; Paper upgrades on load.
     * Paper 26.x migrates {@code <world>/region} → {@code world/dimensions/minecraft/<world>/region};
     * a partial migrate leaves both and {@code createWorld} throws "Refusing to overwrite".
     */
    public static void prepareCopiedWorldFolder(File folder) {
        if (folder == null || !folder.isDirectory()) return;
        new File(folder, "session.lock").delete();
        new File(folder, "uid.dat").delete();
        deleteRecursively(new File(folder, "entities"));
        deleteRecursively(new File(folder, "poi"));
        clearPaperDimensionMigrationConflict(folder);
    }

    /**
     * Paper 26+ stores each custom world at {@code world/dimensions/minecraft/<name>/}. The classic
     * top-level {@code <container>/<name>} folder is only the initial import/template source; once Paper
     * migrates it, that dimension folder holds every setup edit and {@code world.save()}. If BOTH still
     * carry region files, {@code createWorld} refuses to overwrite — and re-importing the stale top-level
     * would wipe the player's setup. So we keep the migrated dimension (live data) and retire the stale
     * top-level source. No-op on 1.8.8 (craft package {@code v1_8*}) and when either side lacks region files.
     */
    static void clearPaperDimensionMigrationConflict(File classicFolder) {
        if (classicFolder == null || isLegacy18CraftPackage()) return;
        String name = classicFolder.getName();
        if (isReservedWorldName(name)) return;
        File classicRegion = new File(classicFolder, "region");
        if (!hasRegionFiles(classicRegion)) return;
        File container = classicFolder.getParentFile();
        if (container == null) return;
        File dimRoot = new File(container, "world" + File.separator + "dimensions"
            + File.separator + "minecraft" + File.separator + name);
        // Only retire the stale source once the migrated dimension is proven to hold this world's region
        // data — never delete the sole copy.
        if (!hasRegionFiles(new File(dimRoot, "region"))) return;
        deleteRecursively(classicFolder);
    }

    /** Never let world-folder surgery touch the primary level or vanilla dimensions. */
    private static boolean isReservedWorldName(String name) {
        if (name == null || name.isEmpty()) return true;
        return name.equalsIgnoreCase("world")
            || name.equalsIgnoreCase("world_nether")
            || name.equalsIgnoreCase("world_the_end")
            || name.equalsIgnoreCase("overworld")
            || name.equalsIgnoreCase("the_nether")
            || name.equalsIgnoreCase("the_end");
    }

    /**
     * On-disk folder that actually holds a world's live data: the Paper 26+ migrated dimension
     * ({@code world/dimensions/minecraft/<name>}) when it carries region files, otherwise the legacy
     * top-level {@code <container>/<name>} folder used on 1.8.x and before first migration.
     */
    static File liveWorldFolder(String name) {
        File dimension = new File(Bukkit.getWorldContainer(), "world" + File.separator + "dimensions"
            + File.separator + "minecraft" + File.separator + name);
        if (hasRegionFiles(new File(dimension, "region"))) return dimension;
        return new File(Bukkit.getWorldContainer(), name);
    }

    /**
     * True when a world's live data exists under the Paper 26+ managed dimension
     * ({@code world/dimensions/minecraft/<name>}). After a match+save the classic top-level folder is
     * retired (see {@link #clearPaperDimensionMigrationConflict}), so this is the only on-disk proof the
     * world still exists — {@code ArenaRepository.loadExistingWorld} must consult it or every team
     * Location decodes to null on restart ("setup missing").
     */
    /**
     * Remove any stale on-disk copies (classic top-level {@code <container>/<name>} and the Paper 26+
     * managed dimension {@code world/dimensions/minecraft/<name>}) of a world that is not a live arena.
     * Used before materializing a fresh template so a leftover dimension can't shadow the fresh copy —
     * on 26.2 {@code clearPaperDimensionMigrationConflict} would otherwise delete the fresh classic copy
     * in favour of the stale dimension, and the arena would load with null coords ("setup missing").
     */
    public static void purgeWorldFolders(String name) {
        if (name == null || name.isEmpty() || isReservedWorldName(name)) return;
        if (Bukkit.getWorld(name) != null) return; // never touch a loaded world's files
        deleteRecursively(new File(Bukkit.getWorldContainer(), name));
        deleteRecursively(new File(Bukkit.getWorldContainer(), "world" + File.separator + "dimensions"
            + File.separator + "minecraft" + File.separator + name));
    }

    public static boolean managedDimensionExists(String name) {
        if (name == null || name.isEmpty() || isReservedWorldName(name)) return false;
        File dimension = new File(Bukkit.getWorldContainer(), "world" + File.separator + "dimensions"
            + File.separator + "minecraft" + File.separator + name);
        return hasRegionFiles(new File(dimension, "region")) || new File(dimension, "level.dat").isFile();
    }

    private static boolean hasRegionFiles(File regionDir) {
        if (regionDir == null || !regionDir.isDirectory()) return false;
        File[] files = regionDir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".mca") || name.endsWith(".mcr")) return true;
        }
        return false;
    }

    private static Boolean managedDimensions;

    /**
     * True on Paper 26+ where every world lives under {@code world/dimensions/minecraft/<name>} and the
     * server owns those files. The plugin's copy-based pristine snapshot/restore is unsafe there (it
     * races Paper's chunk storage and corrupts region headers), so those servers persist worlds natively:
     * Apply writes the setup with {@code world.save()}, matches run with autosave off, and a crash/stop
     * unload-without-save discards match dirt while the last saved (setup) state stays on disk.
     */
    static boolean usesManagedDimensions() {
        if (managedDimensions != null) return managedDimensions;
        if (isLegacy18CraftPackage()) return managedDimensions = Boolean.FALSE;
        try {
            File dims = new File(Bukkit.getWorldContainer(),
                "world" + File.separator + "dimensions" + File.separator + "minecraft");
            managedDimensions = dims.isDirectory();
        } catch (Throwable ignored) {
            managedDimensions = Boolean.FALSE;
        }
        return managedDimensions;
    }

    /** True only when running on Spigot/Paper 1.8.x (craft package {@code v1_8*}). */
    private static boolean isLegacy18CraftPackage() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            int dot = pkg.lastIndexOf('.');
            String ver = dot < 0 ? pkg : pkg.substring(dot + 1);
            return ver.startsWith("v1_8");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Match dirt near spawn only — avoid world-wide entity iteration on Paper 26.x. */
    private static void clearLooseItemsNearSpawn(World world) {
        if (world == null) return;
        Location spawn = world.getSpawnLocation();
        if (spawn == null) return;
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(spawn, 128, 128, 128)) {
            if (entity instanceof Item) entity.remove();
        }
    }

    private File pristineDir(String worldName) {
        return new File(plugin.getDataFolder(), "pristine" + File.separator + worldName);
    }

    private void snapshotPristine(String worldName) {
        // Managed-dimension servers persist natively (see usesManagedDimensions); no folder copy.
        if (usesManagedDimensions()) return;
        File src = liveWorldFolder(worldName);
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
        // Managed-dimension servers persist natively; copying over the live folder corrupts it.
        if (usesManagedDimensions()) return;
        if (Bukkit.getWorld(worldName) != null) return;
        File src = pristineDir(worldName);
        if (!src.isDirectory()) return;
        File dst = liveWorldFolder(worldName);
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