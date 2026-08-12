package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Enchantments;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaManager {
    private final BedlamCore plugin;
    private final Arena arena;
    private int countdownTask = -1;
    private int countdownRemaining;

    public ArenaManager(BedlamCore plugin, ArenaSettings settings) {
        this.plugin = plugin;
        this.arena = new Arena(settings);
    }

    public Arena arena() { return arena; }
    public int countdownRemaining() { return countdownRemaining; }

    public boolean join(Player player) {
        if (!player.hasPermission("bedlam.play")) return false;
        if (!arena.settings().validate().isEmpty()) {
            player.sendMessage(ChatColor.RED + "The arena is not configured yet.");
            return false;
        }
        if (arena.state() == Arena.State.RUNNING || arena.state() == Arena.State.ENDING) {
            player.sendMessage(ChatColor.RED + "That game is already running.");
            return false;
        }
        if (arena.contains(player.getUniqueId())) return true;
        if (arena.players().size() >= arena.settings().maximumPlayers()) {
            player.sendMessage(ChatColor.RED + "That game is full.");
            return false;
        }
        arena.players().put(player.getUniqueId(), null);
        prepareLobby(player);
        broadcast(ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " joined " + ChatColor.YELLOW + "(" + arena.players().size() + ")");
        if (arena.players().size() >= minimumPlayers()) beginCountdown();
        return true;
    }

    public void leave(Player player) {
        if (!arena.contains(player.getUniqueId())) return;
        TeamColor team = arena.players().remove(player.getUniqueId());
        arena.eliminated().remove(player.getUniqueId());
        clearPlayer(player);
        Location lobby = plugin.lobby().spawn();
        if (lobby != null) player.teleport(lobby);
        if (arena.state() == Arena.State.RUNNING && team != null) checkWinner();
        if (arena.state() == Arena.State.COUNTDOWN && arena.players().size() < minimumPlayers()) cancelCountdown();
    }

    public boolean forceStart() {
        if (arena.state() == Arena.State.RUNNING || arena.state() == Arena.State.ENDING || arena.players().isEmpty()) return false;
        cancelCountdown();
        startGame();
        return true;
    }

    private void beginCountdown() {
        if (arena.state() != Arena.State.WAITING) return;
        arena.state(Arena.State.COUNTDOWN);
        final int start = plugin.getConfig().getInt("countdown-seconds", 10);
        countdownRemaining = start;
        countdownTask = new BukkitRunnable() {
            private int seconds = start;

            @Override
            public void run() {
                if (arena.state() != Arena.State.COUNTDOWN) {
                    cancel();
                    return;
                }
                if (seconds == 0) {
                    countdownTask = -1;
                    cancel();
                    startGame();
                    return;
                }
                countdownRemaining = seconds;
                if (seconds <= 5 || seconds % 5 == 0) broadcast(ChatColor.YELLOW + "Game starts in " + seconds + "s");
                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();
    }

    private void cancelCountdown() {
        if (countdownTask != -1) Bukkit.getScheduler().cancelTask(countdownTask);
        countdownTask = -1;
        countdownRemaining = 0;
        if (arena.state() == Arena.State.COUNTDOWN) arena.state(Arena.State.WAITING);
        broadcast(ChatColor.RED + "Countdown cancelled: not enough players.");
    }

    private void startGame() {
        List<TeamColor> teams = arena.settings().configuredTeams();
        if (teams.size() < 2) {
            arena.state(Arena.State.WAITING);
            return;
        }
        arena.resetMatchData();
        Map<TeamColor, Integer> sizes = arena.teamSizes();
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            TeamColor team = GameRules.leastPopulated(teams, sizes);
            arena.players().put(uuid, team);
            sizes.put(team, sizes.get(team) + 1);
        }
        snapshotBeds();
        arena.state(Arena.State.RUNNING);
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) spawnPlayer(player, entry.getValue());
        }
        startGenerators();
        broadcast(ChatColor.GOLD + "Protect your bed and destroy the enemy beds!");
    }

    public void recordPlaced(Block block) {
        if (arena.state() == Arena.State.RUNNING) arena.placedBlocks().add(Locations.blockKey(block.getLocation()));
    }

    public boolean mayBreak(Player player, Block block) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return true;
        TeamColor brokenBed = bedAt(block.getLocation());
        if (brokenBed != null) {
            TeamColor playerTeam = arena.team(player.getUniqueId());
            if (brokenBed == playerTeam) {
                player.sendMessage(ChatColor.RED + "You cannot break your own bed.");
                return false;
            }
            if (!arena.bedAlive(brokenBed)) return false;
            arena.destroyBed(brokenBed);
            broadcast(ChatColor.RED + "BED DESTROYED! " + brokenBed.coloredName() + ChatColor.GRAY + " was broken by " + playerTeam.chatColor() + player.getName());
            return true;
        }
        String key = Locations.blockKey(block.getLocation());
        if (!arena.placedBlocks().remove(key)) {
            player.sendMessage(ChatColor.RED + "You can only break player-placed blocks.");
            return false;
        }
        return true;
    }

    public void handleDeath(Player player) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return;
        TeamColor team = arena.team(player.getUniqueId());
        if (!GameRules.canRespawn(arena.bedAlive(team), arena.eliminated().contains(player.getUniqueId()))) {
            arena.eliminated().add(player.getUniqueId());
            broadcast(team.chatColor() + player.getName() + ChatColor.RED + " was eliminated!");
            checkWinner();
        }
    }

    public Location respawnLocation(Player player) {
        if (!arena.contains(player.getUniqueId())) return player.getWorld().getSpawnLocation();
        if (arena.eliminated().contains(player.getUniqueId())) return arena.settings().spectator();
        return arena.settings().spectator();
    }

    public void afterRespawn(final Player player) {
        if (!arena.contains(player.getUniqueId())) return;
        if (arena.eliminated().contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatColor.RED + "FINAL KILL! You are now spectating.");
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        final int seconds = plugin.getConfig().getInt("respawn-seconds", 5);
        player.sendMessage(ChatColor.YELLOW + "Respawning in " + seconds + " seconds...");
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (arena.state() == Arena.State.RUNNING && arena.contains(player.getUniqueId()) && player.isOnline()) {
                    spawnPlayer(player, arena.team(player.getUniqueId()));
                }
            }
        }, seconds * 20L);
    }

    private void checkWinner() {
        if (arena.state() != Arena.State.RUNNING) return;
        Set<TeamColor> alive = new HashSet<TeamColor>();
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            if (!arena.eliminated().contains(entry.getKey()) && Bukkit.getPlayer(entry.getKey()) != null) alive.add(entry.getValue());
        }
        if (alive.size() > 1) return;
        final TeamColor winner = alive.isEmpty() ? null : alive.iterator().next();
        arena.state(Arena.State.ENDING);
        broadcast(winner == null ? ChatColor.GOLD + "Game over!" : ChatColor.GOLD + "VICTORY! " + winner.coloredName() + ChatColor.GOLD + " wins!");
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { reset(); }
        }, plugin.getConfig().getInt("ending-seconds", 8) * 20L);
    }

    public void reset() {
        cancelAllTasks();
        for (String key : new HashSet<String>(arena.placedBlocks())) removeBlock(key);
        for (List<BlockState> snapshots : arena.bedSnapshots().values()) {
            for (BlockState snapshot : snapshots) snapshot.update(true, false);
        }
        for (UUID uuid : new HashSet<UUID>(arena.generatedItems())) {
            Entity entity = findEntity(uuid);
            if (entity != null) entity.remove();
        }
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                clearPlayer(player);
                player.setGameMode(GameMode.ADVENTURE);
                Location lobby = plugin.lobby().spawn();
                if (lobby != null) player.teleport(lobby);
            }
        }
        arena.players().clear();
        arena.resetMatchData();
        arena.state(Arena.State.WAITING);
    }

    public void shutdown() {
        if (arena.state() != Arena.State.WAITING || !arena.players().isEmpty()) reset();
    }

    private void prepareLobby(Player player) {
        clearPlayer(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(arena.settings().spectator());
        player.getInventory().setItem(8, Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + "Leave Game"));
    }

    public void spawnPlayer(Player player, TeamColor team) {
        clearPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.teleport(arena.settings().team(team).spawn());
        ItemStack sword = Items.named(new ItemStack(Items.material("WOODEN_SWORD", "WOOD_SWORD")), ChatColor.GREEN + "Wooden Sword");
        if (arena.sharpness(team)) Enchantments.add(sword, 1, "SHARPNESS", "DAMAGE_ALL");
        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, team.wool(16));
        equipArmor(player, team);
    }

    public void equipArmor(Player player, TeamColor team) {
        PlayerInventory inventory = player.getInventory();
        boolean iron = arena.ironArmor().contains(player.getUniqueId());
        ItemStack boots = new ItemStack(Items.material(iron ? "IRON_BOOTS" : "LEATHER_BOOTS"));
        ItemStack leggings = new ItemStack(Items.material(iron ? "IRON_LEGGINGS" : "LEATHER_LEGGINGS"));
        int protection = arena.protection(team);
        if (protection > 0) {
            Enchantments.add(boots, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            Enchantments.add(leggings, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
        }
        inventory.setBoots(boots);
        inventory.setLeggings(leggings);
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    private void snapshotBeds() {
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location bed = arena.settings().team(team).bed();
            List<BlockState> snapshots = new ArrayList<BlockState>();
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                Block block = bed.clone().add(x, y, z).getBlock();
                if (block.getType().name().contains("BED")) snapshots.add(block.getState());
            }
            arena.bedSnapshots().put(team, snapshots);
        }
    }

    private TeamColor bedAt(Location location) {
        if (!location.getBlock().getType().name().contains("BED")) return null;
        for (TeamColor team : arena.settings().configuredTeams()) {
            if (Locations.near(location, arena.settings().team(team).bed(), 2.0)) return team;
        }
        return null;
    }

    private void startGenerators() {
        int iron = plugin.getConfig().getInt("generator-periods.iron", 20);
        int gold = plugin.getConfig().getInt("generator-periods.gold", 80);
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location forge = arena.settings().team(team).forge();
            generator(forge, new ItemStack(Material.IRON_INGOT), iron);
            generator(forge, new ItemStack(Material.GOLD_INGOT), gold);
        }
        for (Location location : arena.settings().diamondGenerators()) generator(location, new ItemStack(Material.DIAMOND), plugin.getConfig().getInt("generator-periods.diamond", 600));
        for (Location location : arena.settings().emeraldGenerators()) generator(location, new ItemStack(Material.EMERALD), plugin.getConfig().getInt("generator-periods.emerald", 1200));
    }

    private void generator(final Location location, final ItemStack stack, int ticks) {
        int id = new BukkitRunnable() {
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                Item item = location.getWorld().dropItemNaturally(location, stack.clone());
                arena.generatedItems().add(item.getUniqueId());
            }
        }.runTaskTimer(plugin, ticks, ticks).getTaskId();
        arena.tasks().add(id);
    }

    private void cancelAllTasks() {
        for (Integer id : arena.tasks()) Bukkit.getScheduler().cancelTask(id);
        arena.tasks().clear();
        if (countdownTask != -1) Bukkit.getScheduler().cancelTask(countdownTask);
        countdownTask = -1;
        countdownRemaining = 0;
    }

    private void broadcast(String message) {
        for (UUID uuid : arena.players().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Bedlam" + ChatColor.DARK_GRAY + "] " + message);
        }
    }

    private void removeBlock(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4 || Bukkit.getWorld(parts[0]) == null) return;
        try {
            Bukkit.getWorld(parts[0]).getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])).setType(Material.AIR);
        } catch (NumberFormatException ignored) { }
    }

    private Entity findEntity(UUID uuid) {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (entity.getUniqueId().equals(uuid)) return entity;
        return null;
    }

    private int minimumPlayers() {
        String mode = arena.settings().gameType().name().toLowerCase();
        return plugin.getConfig().getInt("modes." + mode + ".minimum-players", 2);
    }
}
