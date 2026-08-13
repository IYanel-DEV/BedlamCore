package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Enchantments;
import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaManager {
    private final BedlamCore plugin;
    private final Arena arena;
    private final WaitingStructure waitingStructure;
    private final Map<UUID, Entity> displays = new HashMap<UUID, Entity>();
    private final Map<UUID, Location> displayPins = new HashMap<UUID, Location>();
    private final Map<UUID, Boolean> displayHolograms = new HashMap<UUID, Boolean>();
    private final Map<UUID, String> generatorKinds = new HashMap<UUID, String>();
    private final Set<UUID> respawning = new HashSet<UUID>();
    private int displayTask = -1;
    private int countdownTask = -1;
    private int countdownRemaining;
    private int gameSeconds;
    private int diamondTier = 1;
    private int emeraldTier = 1;
    private int[] bounds; // minX,minY,minZ,maxX,maxY,maxZ or null

    public ArenaManager(BedlamCore plugin, ArenaSettings settings) {
        this.plugin = plugin;
        this.arena = new Arena(settings);
        World world = plugin.worlds().load(settings);
        if (world != null) {
            settings.reattach(world); // unload+reload leaves stale World refs on Location fields
            plugin.worlds().disableAutoSave(world);
        }
        this.waitingStructure = new WaitingStructure(plugin.waitingTemplates(), settings.waitingSpawn());
        waitingStructure.build();
        bounds = computeBounds(settings);
        try {
            spawnDisplays();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("spawnDisplays failed for " + settings.id() + ": " + e.getMessage());
        }
    }

    /** Strip waiting paste / displays so Apply's saveOnce does not bake them into the pristine map. */
    public void prepareWorldSave() {
        waitingStructure.remove();
        clearDisplays();
    }

    public Arena arena() { return arena; }
    public int countdownRemaining() { return countdownRemaining; }
    public int diamondTier() { return diamondTier; }
    public int emeraldTier() { return emeraldTier; }
    public int gameSeconds() { return gameSeconds; }

    public String nextGeneratorUpgrade() {
        int next = Integer.MAX_VALUE;
        String name = "Maxed";
        int diamondTwo = upgradeAt("diamond", 2);
        int diamondThree = upgradeAt("diamond", 3);
        int emeraldTwo = upgradeAt("emerald", 2);
        int emeraldThree = upgradeAt("emerald", 3);
        if (diamondTier < 2 && diamondTwo > gameSeconds && diamondTwo < next) { next = diamondTwo; name = "Diamond II"; }
        else if (diamondTier < 3 && diamondThree > gameSeconds && diamondThree < next) { next = diamondThree; name = "Diamond III"; }
        if (emeraldTier < 2 && emeraldTwo > gameSeconds && emeraldTwo < next) { next = emeraldTwo; name = "Emerald II"; }
        else if (emeraldTier < 3 && emeraldThree > gameSeconds && emeraldThree < next) { next = emeraldThree; name = "Emerald III"; }
        if (next == Integer.MAX_VALUE) return name;
        int remaining = next - gameSeconds;
        return name + " in " + (remaining / 60) + ":" + (remaining % 60 < 10 ? "0" : "") + remaining % 60;
    }

    /** Green countdown portion for scoreboard: "Diamond II in §aM:SS". */
    public String nextGeneratorUpgradeLine() {
        String raw = nextGeneratorUpgrade();
        int idx = raw.lastIndexOf(" in ");
        if (idx < 0) return ChatColor.WHITE + raw;
        return ChatColor.WHITE + raw.substring(0, idx + 4) + ChatColor.GREEN + raw.substring(idx + 4);
    }

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
        arena.clearPlayerState(player.getUniqueId());
        respawning.remove(player.getUniqueId());
        player.setPlayerListName(null);
        sendToNetworkLobby(player);
        if (arena.state() == Arena.State.RUNNING && team != null) {
            // Disconnect / leave clears the team so a leftover bed cannot stall win detection.
            if (arena.aliveCount(team) == 0 && arena.bedAlive(team)) {
                arena.destroyBed(team);
                removeBedBlocks(arena.settings().team(team).bed());
            }
            checkWinner();
        }
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
                    for (Player online : arenaPlayers()) Sounds.countdownStart(online);
                    startGame();
                    return;
                }
                countdownRemaining = seconds;
                if (seconds <= 5 || seconds % 5 == 0) {
                    broadcast(ChatColor.YELLOW + "Game starts in " + seconds + "s");
                    for (Player online : arenaPlayers()) Sounds.countdownTick(online);
                }
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
        waitingStructure.remove();
        arena.resetMatchData();
        gameSeconds = 0;
        diamondTier = 1;
        emeraldTier = 1;
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) plugin.worlds().disableAutoSave(world);
        Map<TeamColor, Integer> sizes = arena.teamSizes();
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            TeamColor team = GameRules.leastPopulated(teams, sizes);
            arena.players().put(uuid, team);
            sizes.put(team, sizes.get(team) + 1);
        }
        snapshotBeds();
        arena.state(Arena.State.RUNNING);
        clearWildMobs();
        purgeStrayArmorStands();
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                clearEnderChest(player);
                spawnPlayer(player, entry.getValue());
            }
        }
        startGenerators();
        startMatchEffects();
        ensureTeamChests();
        refreshGeneratorLabels();
        sendStartMessage();
    }

    private void sendStartMessage() {
        for (UUID uuid : arena.players().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            title(player, ChatColor.YELLOW + "" + ChatColor.BOLD + "Bed Wars", ChatColor.YELLOW + "Protect your bed!");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "------------------------------");
            player.sendMessage(ChatColor.YELLOW + "            " + ChatColor.BOLD + "Bed Wars");
            player.sendMessage("");
            player.sendMessage(ChatColor.WHITE + "  Protect your bed and destroy the enemy beds.");
            player.sendMessage(ChatColor.WHITE + "  Collect Iron, Gold, Diamonds and Emeralds");
            player.sendMessage(ChatColor.WHITE + "  from generators to upgrade gear and traps.");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "------------------------------");
            player.sendMessage("");
        }
    }

    private void title(Player player, String title, String subtitle) {
        try {
            player.sendTitle(title, subtitle);
        } catch (Throwable ignored) {
            player.sendMessage(title);
            if (subtitle != null) player.sendMessage(subtitle);
        }
        try {
            player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class)
                .invoke(player, title, subtitle, 10, 70, 20);
        } catch (Throwable ignored) { }
    }

    public void recordPlaced(Block block) {
        if (arena.state() == Arena.State.RUNNING) arena.placedBlocks().add(Locations.blockKey(block.getLocation()));
    }

    public boolean mayPlace(Player player, Block block) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return false;
        if (isSoftSpectating(player)) {
            player.sendMessage(ChatColor.RED + "Spectators cannot build.");
            return false;
        }
        String deny = placeDenyReason(block.getLocation());
        if (deny != null) {
            player.sendMessage(ChatColor.RED + deny);
            return false;
        }
        return true;
    }

    /** null if build allowed at location (bounds / height / protected gens-shops). */
    public String placeDenyReason(Location loc) {
        Location waiting = arena.settings().waitingSpawn();
        if (waiting != null && GameRules.tooHigh(loc.getBlockY(), waiting.getBlockY())) return "You cannot build that high.";
        if (bounds != null && (loc.getBlockX() < bounds[0] || loc.getBlockY() < bounds[1] || loc.getBlockZ() < bounds[2]
            || loc.getBlockX() > bounds[3] || loc.getBlockY() > bounds[4] || loc.getBlockZ() > bounds[5])) {
            return "You cannot build outside the arena.";
        }
        if (protectedZone(loc)) return "You cannot build here.";
        return null;
    }

    /** Throw Bridge Egg: trail of team wool along flight; tracked as match blocks. */
    public void launchBridgeEgg(Player player) {
        final TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING || isSoftSpectating(player)) return;
        final org.bukkit.entity.Egg egg = player.launchProjectile(org.bukkit.entity.Egg.class);
        new BukkitRunnable() {
            private int placed;
            private Location prev;
            @Override public void run() {
                if (!egg.isValid() || egg.isDead() || placed >= GameRules.BRIDGE_EGG_MAX_BLOCKS
                    || arena.state() != Arena.State.RUNNING) {
                    cancel();
                    return;
                }
                Location here = egg.getLocation();
                if (prev == null) {
                    placeBridgeAt(here.getBlock(), team);
                    prev = here.clone();
                    return;
                }
                double dist = prev.distance(here);
                int steps = Math.max(1, (int) Math.ceil(dist));
                for (int i = 1; i <= steps && placed < GameRules.BRIDGE_EGG_MAX_BLOCKS; i++) {
                    double t = (double) i / (double) steps;
                    Location point = prev.clone().add(
                        (here.getX() - prev.getX()) * t,
                        (here.getY() - prev.getY()) * t,
                        (here.getZ() - prev.getZ()) * t);
                    placeBridgeAt(point.getBlock(), team);
                }
                prev = here.clone();
            }

            private void placeBridgeAt(Block block, TeamColor color) {
                if (placed >= GameRules.BRIDGE_EGG_MAX_BLOCKS) return;
                if (!GameRules.isBridgeReplaceable(block.getType().name())) return;
                if (placeDenyReason(block.getLocation()) != null) return;
                String key = Locations.blockKey(block.getLocation());
                if (arena.placedBlocks().contains(key) && block.getType().name().contains("WOOL")) return;
                color.placeAsBlock(block);
                arena.placedBlocks().add(key);
                placed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public boolean mayBreak(Player player, Block block) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return true;
        if (isSoftSpectating(player)) return false;
        TeamColor brokenBed = bedAt(block.getLocation());
        if (brokenBed != null) {
            TeamColor playerTeam = arena.team(player.getUniqueId());
            if (brokenBed == playerTeam) {
                player.sendMessage(ChatColor.RED + "You cannot break your own bed!");
                return false;
            }
            // Enemy beds are always breakable (solo/force-start included); empty teams do not protect beds.
            if (!arena.bedAlive(brokenBed)) return false;
            arena.destroyBed(brokenBed);
            removeBedBlocks(arena.settings().team(brokenBed).bed());
            broadcast(ChatColor.RED + "BED DESTROYED! " + brokenBed.coloredName() + ChatColor.GRAY + " was broken by " + playerTeam.chatColor() + player.getName());
            for (UUID uuid : arena.players().keySet()) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) Sounds.bedDestroyed(online);
            }
            // Solo force-start / last enemy bed: empty teams drop out when bed dies → win state.
            checkWinner();
            return true;
        }
        // Map beds outside configured points must not be spawn-protected into unbreakable props.
        if (block.getType().name().contains("BED")) {
            player.sendMessage(ChatColor.RED + "You cannot break blocks here.");
            return false;
        }
        if (protectedZone(block.getLocation())) {
            player.sendMessage(ChatColor.RED + "You cannot break blocks here.");
            return false;
        }
        String key = Locations.blockKey(block.getLocation());
        if (!arena.placedBlocks().remove(key)) {
            player.sendMessage(ChatColor.RED + "You can only break player-placed blocks.");
            return false;
        }
        return true;
    }

    private boolean protectedZone(Location loc) {
        for (Location gen : arena.settings().diamondGenerators()) if (Locations.near(loc, gen, GameRules.GEN_PROTECT)) return true;
        for (Location gen : arena.settings().emeraldGenerators()) if (Locations.near(loc, gen, GameRules.GEN_PROTECT)) return true;
        for (TeamColor team : arena.settings().configuredTeams()) {
            ArenaSettings.TeamSettings settings = arena.settings().team(team);
            if (Locations.near(loc, settings.spawn(), GameRules.SPAWN_PROTECT)) return true;
            if (Locations.near(loc, settings.forge(), GameRules.FORGE_PROTECT)) return true;
            if (Locations.near(loc, settings.itemShop(), GameRules.SHOP_PROTECT)) return true;
            if (Locations.near(loc, settings.upgradeShop(), GameRules.SHOP_PROTECT)) return true;
            if (Locations.near(loc, settings.teamChest(), GameRules.SHOP_PROTECT)) return true;
            if (Locations.near(loc, settings.enderChest(), GameRules.SHOP_PROTECT)) return true;
        }
        return false;
    }

    public void handleDeath(Player player) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return;
        player.setFallDistance(0F);
        UUID uuid = player.getUniqueId();
        arena.pickaxeTier(uuid, GameRules.toolTierAfterDeath(arena.pickaxeTier(uuid)));
        arena.axeTier(uuid, GameRules.toolTierAfterDeath(arena.axeTier(uuid)));
        TeamColor team = arena.team(uuid);
        if (!GameRules.canRespawn(arena.bedAlive(team), arena.eliminated().contains(uuid))) {
            arena.eliminated().add(uuid);
            broadcast(team.chatColor() + player.getName() + ChatColor.RED + " was eliminated!");
            checkWinner();
        } else {
            respawning.add(uuid);
        }
    }

    public boolean isRespawning(UUID uuid) { return respawning.contains(uuid); }

    public Location respawnLocation(Player player) {
        if (!arena.contains(player.getUniqueId())) return player.getWorld().getSpawnLocation();
        if (arena.eliminated().contains(player.getUniqueId())) return spectatorLocation(player);
        TeamColor team = arena.team(player.getUniqueId());
        Location spawn = team == null ? null : arena.settings().team(team).spawn();
        if (spawn != null) return spawn;
        return spectatorLocation(player);
    }

    /** Arena spectator point rebound to the live game world (final death / bed-gone). */
    private Location spectatorLocation(Player player) {
        Location spectator = arena.settings().spectator();
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (spectator != null) {
            if (world != null) spectator.setWorld(world);
            return spectator;
        }
        if (world != null) return world.getSpawnLocation();
        return player.getWorld().getSpawnLocation();
    }

    public boolean isSoftSpectating(Player player) {
        return player != null && arena.contains(player.getUniqueId())
            && (arena.eliminated().contains(player.getUniqueId()) || respawning.contains(player.getUniqueId()));
    }

    /** Hypixel-style soft spectate: adventure flight + invis; never GameMode.SPECTATOR. */
    public void applySoftSpectate(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1), true);
        try {
            player.getClass().getMethod("setCollidable", boolean.class).invoke(player, false);
        } catch (Throwable ignored) { }
        plugin.views().updateAll();
    }

    public void afterRespawn(final Player player) {
        if (!arena.contains(player.getUniqueId())) return;
        player.setFallDistance(0F);
        if (arena.eliminated().contains(player.getUniqueId())) {
            respawning.remove(player.getUniqueId());
            clearPlayer(player);
            final Location spectator = spectatorLocation(player);
            player.teleport(spectator);
            applySoftSpectate(player);
            player.getInventory().setItem(0, Items.named(new ItemStack(Material.COMPASS), ChatColor.GREEN + "Spectate", ChatColor.GRAY + "Click to watch a player"));
            player.getInventory().setItem(8, Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + "Return to Lobby", ChatColor.GRAY + "Leave this game"));
            player.sendMessage(ChatColor.RED + "FINAL KILL! You are now spectating.");
            // Belt: some clients ignore same-tick teleports after respawn.
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    if (!player.isOnline() || !arena.eliminated().contains(player.getUniqueId())) return;
                    player.teleport(spectatorLocation(player));
                    applySoftSpectate(player);
                }
            });
            return;
        }
        TeamColor team = arena.team(player.getUniqueId());
        Location spawn = team == null ? null : arena.settings().team(team).spawn();
        if (spawn != null) player.teleport(spawn);
        applySoftSpectate(player);
        final int seconds = Math.max(0, plugin.getConfig().getInt("respawn-seconds", 5));
        if (seconds <= 0) {
            respawning.remove(player.getUniqueId());
            if (team != null) spawnPlayer(player, team);
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Respawning in " + seconds + " seconds...");
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                respawning.remove(player.getUniqueId());
                if (arena.state() == Arena.State.RUNNING && arena.contains(player.getUniqueId()) && player.isOnline()
                    && !arena.eliminated().contains(player.getUniqueId())) {
                    spawnPlayer(player, arena.team(player.getUniqueId()));
                }
            }
        }, seconds * 20L);
    }

    private void checkWinner() {
        if (arena.state() != Arena.State.RUNNING) return;
        Set<TeamColor> contending = new HashSet<TeamColor>();
        for (TeamColor team : arena.settings().configuredTeams()) {
            int living = 0;
            for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
                if (entry.getValue() == team && !arena.eliminated().contains(entry.getKey()) && Bukkit.getPlayer(entry.getKey()) != null) living++;
            }
            if (GameRules.teamContending(arena.bedAlive(team), living)) contending.add(team);
        }
        if (!GameRules.shouldEndMatch(contending.size())) return;
        final TeamColor winner = contending.isEmpty() ? null : contending.iterator().next();
        arena.state(Arena.State.ENDING);
        broadcast(winner == null ? ChatColor.GOLD + "Game over!" : ChatColor.GOLD + "VICTORY! " + winner.coloredName() + ChatColor.GOLD + " wins!");
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { reset(); }
        }, plugin.getConfig().getInt("ending-seconds", 8) * 20L);
    }

    public void reset() {
        cancelAllTasks();
        for (String key : new HashSet<String>(arena.placedBlocks())) removeBlock(key);
        arena.placedBlocks().clear();
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
                player.setPlayerListName(null);
                sendToNetworkLobby(player);
            }
        }
        arena.players().clear();
        arena.resetMatchData();
        respawning.clear();
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) plugin.worlds().disableAutoSave(world);
        arena.state(Arena.State.WAITING);
        waitingStructure.build();
        refreshGeneratorLabels();
    }

    public void shutdown() {
        cancelAllTasks();
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setPlayerListName(null);
                sendToNetworkLobby(player);
            }
        }
        arena.players().clear();
        arena.resetMatchData();
        waitingStructure.remove();
        clearDisplays();
        // Never save match dirt (builds / broken beds) — discard unload only.
        plugin.worlds().unloadDiscarding(arena.settings());
    }

    public void rebuildWaitingStructure() {
        if (arena.state() != Arena.State.WAITING && arena.state() != Arena.State.COUNTDOWN) return;
        waitingStructure.remove();
        waitingStructure.build();
    }

    public boolean isBed(Block block) { return bedAt(block.getLocation()) != null; }

    public String shop(Entity entity) {
        if (!entity.hasMetadata("bedlamShop") || entity.getMetadata("bedlamShop").isEmpty()) return null;
        return entity.getMetadata("bedlamShop").get(0).asString();
    }

    public boolean isDisplay(Entity entity) {
        return entity.hasMetadata("bedlamShop") || entity.hasMetadata("bedlamGeneratorDisplay") || entity.hasMetadata("bedlamHologram");
    }

    private void prepareLobby(Player player) {
        clearPlayer(player);
        clearEnderChest(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(arena.settings().waitingSpawn());
        player.getInventory().setItem(8, Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + "Leave Game"));
    }

    public void spawnPlayer(Player player, TeamColor team) {
        respawning.remove(player.getUniqueId());
        clearPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);
        applyDragonHealth(player, team);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFallDistance(0F);
        player.teleport(arena.settings().team(team).spawn());
        player.setPlayerListName(team.chatColor() + player.getName());
        ItemStack sword = Items.unbreakable(Items.named(new ItemStack(Items.material("WOODEN_SWORD", "WOOD_SWORD")), ChatColor.GREEN + "Wooden Sword"));
        if (arena.sharpness(team)) Enchantments.add(sword, 1, "SHARPNESS", "DAMAGE_ALL");
        player.getInventory().setItem(0, sword);
        equipArmor(player, team);
        giveOwnedTools(player);
        applyHaste(player, team);
        plugin.views().updateAll();
    }

    /** Give permanent tools at current tiers (replace any existing pick/axe/shears). */
    public void giveOwnedTools(Player player) {
        UUID uuid = player.getUniqueId();
        int pick = arena.pickaxeTier(uuid);
        if (pick > 0) replaceTool(player, true, toolPickaxe(pick));
        int axe = arena.axeTier(uuid);
        if (axe > 0) replaceTool(player, false, toolAxe(axe));
        if (arena.shearsOwned(uuid)) {
            removeMatching(player, "SHEARS");
            player.getInventory().addItem(Items.unbreakable(Items.named(new ItemStack(Items.material("SHEARS")),
                ChatColor.GREEN + "Permanent Shears", ChatColor.GRAY + "Kept on respawn")));
        }
    }

    public static ItemStack toolPickaxe(int tier) {
        Material mat = pickaxeMaterial(tier);
        ItemStack item = Items.unbreakable(Items.named(new ItemStack(mat), ChatColor.GREEN + toolTierName(tier) + " Pickaxe",
            ChatColor.GRAY + "Upgradable", ChatColor.DARK_GRAY + "Loses 1 tier on death"));
        int eff = GameRules.pickaxeEfficiency(tier);
        if (eff > 0) Enchantments.add(item, eff, "DIG_SPEED", "EFFICIENCY");
        return item;
    }

    public static ItemStack toolAxe(int tier) {
        Material mat = axeMaterial(tier);
        return Items.unbreakable(Items.named(new ItemStack(mat), ChatColor.GREEN + toolTierName(tier) + " Axe",
            ChatColor.GRAY + "Upgradable", ChatColor.DARK_GRAY + "Loses 1 tier on death"));
    }

    private static String toolTierName(int tier) {
        switch (tier) {
            case 2: return "Stone";
            case 3: return "Iron";
            case 4: return "Diamond";
            default: return "Wooden";
        }
    }

    private static Material pickaxeMaterial(int tier) {
        switch (tier) {
            case 2: return Items.material("STONE_PICKAXE");
            case 3: return Items.material("IRON_PICKAXE");
            case 4: return Items.material("DIAMOND_PICKAXE");
            default: return Items.material("WOODEN_PICKAXE", "WOOD_PICKAXE");
        }
    }

    private static Material axeMaterial(int tier) {
        switch (tier) {
            case 2: return Items.material("STONE_AXE");
            case 3: return Items.material("IRON_AXE");
            case 4: return Items.material("DIAMOND_AXE");
            default: return Items.material("WOODEN_AXE", "WOOD_AXE");
        }
    }

    public void replaceTool(Player player, boolean pickaxe, ItemStack tool) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null) continue;
            String name = stack.getType().name();
            if (pickaxe ? GameRules.isPickaxe(name) : GameRules.isAxe(name)) {
                player.getInventory().setItem(i, tool);
                return;
            }
        }
        player.getInventory().addItem(tool);
    }

    private static void removeMatching(Player player, String needle) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && stack.getType().name().contains(needle)) player.getInventory().setItem(i, null);
        }
    }

    private void applyDragonHealth(Player player, TeamColor team) {
        try {
            double base = 20.0;
            double max = arena.dragonBuff(team) ? 24.0 : base;
            player.setMaxHealth(max);
        } catch (Throwable ignored) { }
    }

    public void equipArmor(Player player, TeamColor team) {
        PlayerInventory inventory = player.getInventory();
        int tier = arena.armorTier(player.getUniqueId());
        ItemStack chest;
        ItemStack helmet;
        if (tier >= 2) {
            chest = Items.unbreakable(new ItemStack(Items.material("DIAMOND_CHESTPLATE")));
            helmet = Items.unbreakable(new ItemStack(Items.material("DIAMOND_HELMET")));
        } else if (tier >= 1) {
            chest = Items.unbreakable(new ItemStack(Items.material("IRON_CHESTPLATE")));
            helmet = Items.unbreakable(new ItemStack(Items.material("IRON_HELMET")));
        } else {
            chest = Items.unbreakable(team.leather("LEATHER_CHESTPLATE", "LEATHER_CHESTPLATE"));
            helmet = Items.unbreakable(team.leather("LEATHER_HELMET", "LEATHER_HELMET"));
        }
        ItemStack boots = keepOrLeather(inventory.getBoots(), team, "LEATHER_BOOTS");
        ItemStack leggings = keepOrLeather(inventory.getLeggings(), team, "LEATHER_LEGGINGS");
        int protection = arena.protection(team);
        if (protection > 0) {
            Enchantments.add(boots, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            Enchantments.add(leggings, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            Enchantments.add(chest, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            Enchantments.add(helmet, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
        }
        if (arena.cushionedBoots(team)) Enchantments.add(boots, 4, "PROTECTION_FALL", "FEATHER_FALLING");
        inventory.setBoots(boots);
        inventory.setLeggings(leggings);
        inventory.setChestplate(chest);
        inventory.setHelmet(helmet);
    }

    private ItemStack keepOrLeather(ItemStack current, TeamColor team, String leather) {
        if (current != null) {
            String name = current.getType().name();
            if (name.contains("CHAINMAIL") || name.contains("IRON") || name.contains("DIAMOND")) {
                return Items.unbreakable(current.clone());
            }
        }
        return Items.unbreakable(team.leather(leather, leather));
    }

    public void applyHaste(Player player, TeamColor team) {
        int level = arena.hasteLevel(team);
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        if (level > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, Integer.MAX_VALUE, level - 1), true);
    }

    private void clearPlayer(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        try { player.setItemOnCursor(null); } catch (Throwable ignored) { }
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        try { player.setMaxHealth(20.0); } catch (Throwable ignored) { }
        player.setAllowFlight(false);
        player.setFlying(false);
        try {
            player.getClass().getMethod("setCollidable", boolean.class).invoke(player, true);
        } catch (Throwable ignored) { }
    }

    /** Personal ender only — never persists across matches. Team chest is separate (Arena.teamChest). */
    private void clearEnderChest(Player player) {
        if (player != null) player.getEnderChest().clear();
    }

    /** Strip match gear, teleport to network lobby, give lobby items only. */
    public void sendToNetworkLobby(Player player) {
        clearPlayer(player);
        clearEnderChest(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        Location lobby = plugin.lobby().spawn();
        if (lobby == null && !Bukkit.getWorlds().isEmpty()) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        if (lobby != null) player.teleport(lobby);
        plugin.listener().giveNavigation(player);
        plugin.views().updateAll();
        // Belt: clear again next tick in case a race re-added items during teleport.
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || plugin.games().arena(online) != null) return;
                if (hasMatchLeftovers(online)) {
                    clearPlayer(online);
                    clearEnderChest(online);
                    plugin.listener().giveNavigation(online);
                }
            }
        });
    }

    private static boolean hasMatchLeftovers(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            String name = Items.name(stack);
            if (name.equals("Bedlam Setup") || name.equals("Bedlam Menu")) continue;
            String mat = stack.getType().name();
            if (GameRules.isSword(mat) || mat.contains("WOOL") || mat.contains("INGOT") || mat.contains("DIAMOND")
                || mat.contains("EMERALD") || mat.contains("TERRACOTTA") || mat.contains("CLAY") || mat.contains("SANDSTONE")) {
                return true;
            }
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor != null) for (ItemStack piece : armor) if (piece != null && piece.getType() != Material.AIR) return true;
        return false;
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
        if (location == null || location.getWorld() == null || !location.getBlock().getType().name().contains("BED")) return null;
        World world = location.getWorld();
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location bed = arena.settings().team(team).bed();
            if (bed == null) continue;
            bed.setWorld(world);
            if (Locations.near(location, bed, 2.5)) return team;
        }
        return null;
    }

    private void removeBedBlocks(Location bed) {
        if (bed == null) return;
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) bed.setWorld(world);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            Block block = bed.clone().add(x, y, z).getBlock();
            if (block.getType().name().contains("BED")) block.setType(Material.AIR);
        }
    }

    private void startGenerators() {
        int iron = plugin.getConfig().getInt("generator-periods.iron", 20);
        int gold = plugin.getConfig().getInt("generator-periods.gold", 80);
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location forge = arena.settings().team(team).forge();
            forgeGenerator(forge, new ItemStack(Material.IRON_INGOT), "iron", iron, team);
            forgeGenerator(forge, new ItemStack(Material.GOLD_INGOT), "gold", gold, team);
        }
        for (Location location : arena.settings().diamondGenerators()) generator(location, new ItemStack(Material.DIAMOND), "diamond", 600);
        for (Location location : arena.settings().emeraldGenerators()) generator(location, new ItemStack(Material.EMERALD), "emerald", 1200);
        int id = new BukkitRunnable() {
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                gameSeconds++;
                int nextDiamond = GameRules.generatorTier(gameSeconds, upgradeAt("diamond", 2), upgradeAt("diamond", 3));
                int nextEmerald = GameRules.generatorTier(gameSeconds, upgradeAt("emerald", 2), upgradeAt("emerald", 3));
                if (nextDiamond != diamondTier) {
                    diamondTier = nextDiamond;
                    broadcast(ChatColor.AQUA + "Diamond generators upgraded to Tier " + diamondTier + "!");
                    for (Player online : arenaPlayers()) Sounds.generatorUpgrade(online);
                    refreshGeneratorLabels();
                }
                if (nextEmerald != emeraldTier) {
                    emeraldTier = nextEmerald;
                    broadcast(ChatColor.GREEN + "Emerald generators upgraded to Tier " + emeraldTier + "!");
                    for (Player online : arenaPlayers()) Sounds.generatorUpgrade(online);
                    refreshGeneratorLabels();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    private void startMatchEffects() {
        int id = new BukkitRunnable() {
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                tickTraps();
                for (TeamColor team : arena.settings().configuredTeams()) {
                    if (!arena.healPool(team)) continue;
                    Location spawn = arena.settings().team(team).spawn();
                    if (spawn == null || spawn.getWorld() == null) continue;
                    // Green heal-pool particles around the island (Hypixel-like).
                    for (int i = 0; i < 12; i++) {
                        double angle = (Math.PI * 2 * i) / 12.0;
                        Location particle = spawn.clone().add(Math.cos(angle) * 3.5, 0.4 + (i % 3) * 0.35, Math.sin(angle) * 3.5);
                        spawn.getWorld().playEffect(particle, Effect.HAPPY_VILLAGER, 0);
                    }
                    spawn.getWorld().playEffect(spawn.clone().add(0, 1.0, 0), Effect.HAPPY_VILLAGER, 0);
                }
                for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
                    if (arena.eliminated().contains(entry.getKey()) || !arena.healPool(entry.getValue())) continue;
                    Player player = Bukkit.getPlayer(entry.getKey());
                    Location spawn = arena.settings().team(entry.getValue()).spawn();
                    if (player == null || spawn == null || !Locations.near(player.getLocation(), spawn, GameRules.HEAL_POOL_RADIUS)) continue;
                    if (player.getHealth() < player.getMaxHealth()) player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1.0));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    private void tickTraps() {
        long now = System.currentTimeMillis();
        for (TeamColor team : arena.settings().configuredTeams()) {
            List<Arena.TrapType> queue = arena.traps(team);
            if (queue == null || queue.isEmpty() || !arena.trapReady(team, now)) continue;
            Location spawn = arena.settings().team(team).spawn();
            if (spawn == null) continue;
            for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
                if (entry.getValue() == team || arena.eliminated().contains(entry.getKey())) continue;
                Player enemy = Bukkit.getPlayer(entry.getKey());
                if (enemy == null || arena.eliminated().contains(enemy.getUniqueId()) || isRespawning(enemy.getUniqueId())) continue;
                if (!Locations.near(enemy.getLocation(), spawn, GameRules.TRAP_TRIGGER_RADIUS)) continue;
                Arena.TrapType trap = arena.popTrap(team);
                if (trap == null) break;
                arena.armTrapCooldown(team, now + GameRules.TRAP_COOLDOWN_TICKS * 50L);
                fireTrap(team, trap, enemy, spawn);
                break;
            }
        }
    }

    private void fireTrap(TeamColor team, Arena.TrapType trap, Player enemy, Location spawn) {
        announceTrap(team, trap.displayName() + " — " + enemy.getName());
        enemy.sendMessage(ChatColor.RED + "You triggered " + trap.displayName() + "!");
        switch (trap) {
            case BLINDNESS:
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 8 * 20, 0), true);
                break;
            case MINER_FATIGUE:
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 10 * 20, 1), true);
                break;
            case REVEAL:
                enemy.removePotionEffect(PotionEffectType.INVISIBILITY);
                enemy.sendMessage(ChatColor.YELLOW + "Your invisibility was stripped!");
                break;
            case COUNTER_OFFENSIVE:
                for (Map.Entry<UUID, TeamColor> member : arena.players().entrySet()) {
                    if (member.getValue() != team || arena.eliminated().contains(member.getKey())) continue;
                    Player ally = Bukkit.getPlayer(member.getKey());
                    if (ally == null || !Locations.near(ally.getLocation(), spawn, GameRules.TRAP_TRIGGER_RADIUS + 6)) continue;
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15 * 20, 1), true);
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 15 * 20, 1), true);
                }
                break;
            case ALARM:
            default:
                break;
        }
    }

    private void announceTrap(TeamColor team, String detail) {
        for (Map.Entry<UUID, TeamColor> member : arena.players().entrySet()) {
            if (member.getValue() != team) continue;
            Player online = Bukkit.getPlayer(member.getKey());
            if (online == null) continue;
            title(online, ChatColor.RED + "TRAP TRIGGERED!", ChatColor.WHITE + detail);
            Sounds.levelUp(online);
        }
    }

    private void ensureTeamChests() {
        for (TeamColor team : arena.settings().configuredTeams()) {
            ArenaSettings.TeamSettings settings = arena.settings().team(team);
            placeChestBlock(settings.teamChest(), Material.CHEST, false);
            placeChestBlock(settings.enderChest(), Items.material("ENDER_CHEST"), true);
            spawnChestHologram(settings.teamChest());
            spawnChestHologram(settings.enderChest());
        }
    }

    private void placeChestBlock(Location location, Material type, boolean ender) {
        if (location == null || location.getWorld() == null) return;
        Block block = location.getBlock();
        if (block.getType() != type) block.setType(type);
    }

    private void spawnChestHologram(Location location) {
        if (location == null || location.getWorld() == null) return;
        Location pin = location.getBlock().getLocation().add(0.5, GameRules.CHEST_HOLO_Y, 0.5);
        spawnHologram(pin, ChatColor.YELLOW + "" + ChatColor.BOLD + "PUNCH TO DEPOSIT");
    }

    public TeamColor teamChestAt(Location location) {
        for (TeamColor team : arena.settings().configuredTeams()) {
            if (Locations.near(location, arena.settings().team(team).teamChest(), 1.5)) return team;
        }
        return null;
    }

    public TeamColor enderChestAt(Location location) {
        for (TeamColor team : arena.settings().configuredTeams()) {
            if (Locations.near(location, arena.settings().team(team).enderChest(), 1.5)) return team;
        }
        return null;
    }

    public boolean openTeamChest(Player player, TeamColor chestTeam) {
        TeamColor playerTeam = arena.team(player.getUniqueId());
        if (playerTeam == null || chestTeam == null) return false;
        if (playerTeam != chestTeam && arena.bedAlive(chestTeam)) {
            player.sendMessage(ChatColor.RED + "You cannot open that chest while their bed is alive.");
            return false;
        }
        Inventory inventory = arena.teamChest(chestTeam);
        if (inventory != null) player.openInventory(inventory);
        return true;
    }

    public boolean openEnderChest(Player player) {
        player.openInventory(player.getEnderChest());
        return true;
    }

    public boolean fastDeposit(Player player, Inventory target, ItemStack hand) {
        if (hand == null || hand.getType() == Material.AIR) return false;
        if (!GameRules.canFastDeposit(hand.getType().name())) return false;
        ItemStack deposit = hand.clone();
        Map<Integer, ItemStack> leftover = target.addItem(deposit);
        int deposited = deposit.getAmount();
        if (!leftover.isEmpty()) {
            ItemStack remain = leftover.values().iterator().next();
            deposited -= remain.getAmount();
            player.setItemInHand(remain);
        } else {
            player.setItemInHand(null);
        }
        if (deposited <= 0) return false;
        String pretty = hand.getType().name().toLowerCase().replace('_', ' ');
        player.sendMessage(ChatColor.GREEN + "Deposited x" + deposited + " " + pretty);
        return true;
    }

    private void clearWildMobs() {
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world == null) return;
        for (Entity entity : new ArrayList<Entity>(world.getEntities())) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
            if (isDisplay(entity) || entity.hasMetadata(LobbyNpcService.META_MODE)) continue;
            entity.remove();
        }
    }

    /** World-saved setup stands lose metadata on reload — wipe any ArmorStand we are not pinning. */
    private void purgeStrayArmorStands() {
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world == null) return;
        for (Entity entity : new ArrayList<Entity>(world.getEntities())) {
            if (!(entity instanceof ArmorStand)) continue;
            // META_MODE: do not call plugin.npcs() — it is still null during ArenaManager ctor / onEnable
            if (displays.containsKey(entity.getUniqueId()) || entity.hasMetadata(LobbyNpcService.META_MODE)) continue;
            entity.remove();
        }
    }

    private void clearDisplays() {
        if (displayTask != -1) Bukkit.getScheduler().cancelTask(displayTask);
        displayTask = -1;
        for (Entity entity : displays.values()) if (entity != null) entity.remove();
        displays.clear();
        displayPins.clear();
        displayHolograms.clear();
        generatorKinds.clear();
    }

    private void spawnDisplays() {
        purgeStrayArmorStands();
        for (TeamColor team : arena.settings().configuredTeams()) {
            spawnShop(arena.settings().team(team).itemShop(), "ITEM", ChatColor.GREEN + "ITEM SHOP");
            spawnShop(arena.settings().team(team).upgradeShop(), "UPGRADE", ChatColor.AQUA + "TEAM UPGRADES");
        }
        for (Location location : arena.settings().diamondGenerators()) spawnGeneratorDisplay(location, Material.DIAMOND_BLOCK, "diamond");
        for (Location location : arena.settings().emeraldGenerators()) spawnGeneratorDisplay(location, Material.EMERALD_BLOCK, "emerald");
        if (displays.isEmpty()) return;
        displayTask = new BukkitRunnable() {
            @Override public void run() {
                for (Map.Entry<UUID, Entity> entry : new HashMap<UUID, Entity>(displays).entrySet()) {
                    Entity entity = entry.getValue();
                    Location pin = displayPins.get(entry.getKey());
                    if (entity == null || entity.isDead() || pin == null) continue;
                    entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    if (entity.hasMetadata("bedlamGeneratorDisplay")) pin.setYaw(pin.getYaw() + 3F);
                    if (entity.hasMetadata("bedlamShop")) LobbyNpcService.mute(entity);
                    if (entity.getLocation().distanceSquared(pin) > 0.0001 || entity.hasMetadata("bedlamGeneratorDisplay")) entity.teleport(pin);
                }
                updateDisplayVisibility();
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
    }

    private void spawnShop(Location location, String kind, String name) {
        if (location == null || location.getWorld() == null) return;
        Location pin = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        pin.setYaw(location.getYaw());
        pin.setPitch(0F);
        Entity villager = location.getWorld().spawnEntity(pin, EntityType.VILLAGER);
        villager.setMetadata("bedlamShop", new FixedMetadataValue(plugin, kind));
        // Holograms carry the label; hide vanilla nametag when looking at the villager.
        villager.setCustomName(" ");
        villager.setCustomNameVisible(false);
        LobbyNpcService.freeze(villager, false);
        pin(villager, pin, false);
        spawnHologram(pin.clone().add(0, GameRules.SHOP_HOLO_TITLE_Y, 0), name);
        spawnHologram(pin.clone().add(0, GameRules.SHOP_HOLO_SUB_Y, 0), ChatColor.YELLOW + "Right Click");
    }

    private void spawnGeneratorDisplay(Location location, Material block, String kind) {
        if (location == null || location.getWorld() == null) return;
        Location base = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Location standPin = base.clone().add(0, GameRules.GEN_STAND_Y, 0);
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(standPin, EntityType.ARMOR_STAND);
        // Full-size pin (not miniature); shop/chest holograms stay small via spawnHologram.
        LobbyNpcService.prepareArmorStand(stand, false);
        stand.setVisible(false);
        stand.getEquipment().setHelmet(new ItemStack(block));
        stand.setMetadata("bedlamGeneratorDisplay", new FixedMetadataValue(plugin, kind));
        pin(stand, standPin, false);
        generatorKinds.put(stand.getUniqueId(), kind);
        String label = kind.equals("diamond") ? ChatColor.AQUA + "Diamond" : ChatColor.GREEN + "Emerald";
        spawnHologram(base.clone().add(0, GameRules.GEN_HOLO_TITLE_Y, 0), label);
        spawnHologram(base.clone().add(0, GameRules.GEN_HOLO_TIER_Y, 0), ChatColor.YELLOW + "Tier " + roman(kind.equals("diamond") ? diamondTier : emeraldTier));
    }

    private void spawnHologram(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        LobbyNpcService.prepareArmorStand(stand, true);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setMetadata("bedlamHologram", new FixedMetadataValue(plugin, true));
        pin(stand, location, true);
    }

    private void refreshGeneratorLabels() {
        for (Map.Entry<UUID, String> entry : new HashMap<UUID, String>(generatorKinds).entrySet()) {
            // Labels are separate hologram stands near the block stand; rebuild is heavier — update nearby hologram names.
        }
        // Cheap path: clear and respawn generator displays only.
        List<Location> diamonds = new ArrayList<Location>(arena.settings().diamondGenerators());
        List<Location> emeralds = new ArrayList<Location>(arena.settings().emeraldGenerators());
        for (Map.Entry<UUID, Entity> entry : new HashMap<UUID, Entity>(displays).entrySet()) {
            Entity entity = entry.getValue();
            if (entity == null) continue;
            if (entity.hasMetadata("bedlamGeneratorDisplay") || (entity.hasMetadata("bedlamHologram") && nearAnyGenerator(displayPins.get(entry.getKey())))) {
                entity.remove();
                displays.remove(entry.getKey());
                displayPins.remove(entry.getKey());
                displayHolograms.remove(entry.getKey());
                generatorKinds.remove(entry.getKey());
            }
        }
        for (Location location : diamonds) spawnGeneratorDisplay(location, Material.DIAMOND_BLOCK, "diamond");
        for (Location location : emeralds) spawnGeneratorDisplay(location, Material.EMERALD_BLOCK, "emerald");
    }

    private boolean nearAnyGenerator(Location loc) {
        if (loc == null) return false;
        for (Location gen : arena.settings().diamondGenerators()) {
            if (Locations.near(loc, gen.getBlock().getLocation().add(0.5, GameRules.GEN_HOLO_TITLE_Y, 0.5), 2.0)) return true;
        }
        for (Location gen : arena.settings().emeraldGenerators()) {
            if (Locations.near(loc, gen.getBlock().getLocation().add(0.5, GameRules.GEN_HOLO_TITLE_Y, 0.5), 2.0)) return true;
        }
        return false;
    }

    private void pin(Entity entity, Location location, boolean hologram) {
        displays.put(entity.getUniqueId(), entity);
        displayPins.put(entity.getUniqueId(), location.clone());
        displayHolograms.put(entity.getUniqueId(), hologram);
    }

    private void updateDisplayVisibility() {
        double limit = GameRules.DISPLAY_VIEW * GameRules.DISPLAY_VIEW;
        for (Map.Entry<UUID, Entity> entry : displays.entrySet()) {
            Entity entity = entry.getValue();
            Location pin = displayPins.get(entry.getKey());
            if (entity == null || pin == null || pin.getWorld() == null) continue;
            boolean anyNear = false;
            for (Player player : pin.getWorld().getPlayers()) {
                boolean near = player.getLocation().distanceSquared(pin) <= limit;
                if (near && !EntityVisibility.isSpectator(player)) anyNear = true;
                EntityVisibility.apply(plugin, player, entity, near);
            }
            if (entity.hasMetadata("bedlamShop")) entity.setCustomNameVisible(false);
            else if (entity.hasMetadata("bedlamHologram")) entity.setCustomNameVisible(anyNear);
            if (entity instanceof ArmorStand && entity.hasMetadata("bedlamGeneratorDisplay")) {
                // Keep rotating block present only when a non-spectator is near.
                if (!anyNear && entity.isValid()) ((ArmorStand) entity).getEquipment().setHelmet(null);
                else if (anyNear) {
                    String kind = generatorKinds.get(entity.getUniqueId());
                    if (kind != null) ((ArmorStand) entity).getEquipment().setHelmet(new ItemStack(kind.equals("diamond") ? Material.DIAMOND_BLOCK : Material.EMERALD_BLOCK));
                }
            }
        }
    }

    private void forgeGenerator(final Location location, final ItemStack stack, final String kind, final int fallbackTicks, final TeamColor team) {
        int id = new BukkitRunnable() {
            private int waited;
            private final java.util.Random random = new java.util.Random();
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                waited++;
                int period = Math.max(1, (generatorPeriod(kind, fallbackTicks) + 19) / 20);
                int forge = arena.forgeLevel(team);
                if (forge > 0) period = Math.max(1, period - forge);
                if (waited < period) return;
                waited = 0;
                deliverForge(location, stack, team);
                if (GameRules.forgeBonusHits(GameRules.forgeDiamondChance(forge), random.nextDouble())) {
                    deliverForge(location, new ItemStack(Material.DIAMOND), team);
                }
                if (GameRules.forgeBonusHits(GameRules.forgeEmeraldChance(forge), random.nextDouble())) {
                    deliverForge(location, new ItemStack(Material.EMERALD), team);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    /** Teammates in share range each get a copy; else low ground drop. Enemies never share. */
    private void deliverForge(Location location, ItemStack stack, TeamColor team) {
        if (location == null || location.getWorld() == null || stack == null) return;
        List<Player> recipients = forgeRecipients(location, team);
        if (recipients.isEmpty()) {
            spawnForgeDrop(location, stack);
            return;
        }
        for (Player player : recipients) {
            giveForgeItem(player, stack.clone(), location);
        }
    }

    private List<Player> forgeRecipients(Location forge, TeamColor team) {
        List<Player> recipients = new ArrayList<Player>();
        double cx = forge.getBlockX() + 0.5;
        double cy = forge.getBlockY() + 0.5;
        double cz = forge.getBlockZ() + 0.5;
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            if (entry.getValue() != team || arena.eliminated().contains(entry.getKey())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.getWorld().equals(forge.getWorld())) continue;
            Location at = player.getLocation();
            if (GameRules.forgeShareInRange(at.getX() - cx, at.getY() - cy, at.getZ() - cz)) recipients.add(player);
        }
        return recipients;
    }

    private void giveForgeItem(Player player, ItemStack stack, Location forge) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack remain : leftover.values()) {
                Item drop = player.getWorld().dropItem(player.getLocation().add(0, 0.2, 0), remain);
                drop.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                drop.setPickupDelay(0);
                arena.generatedItems().add(drop.getUniqueId());
            }
        }
        double cx = forge.getBlockX() + 0.5;
        double cz = forge.getBlockZ() + 0.5;
        Location at = player.getLocation();
        if (GameRules.forgeStandingInRange(at.getX() - cx, at.getZ() - cz)) Sounds.forgeCollect(player);
        else Sounds.forgeShare(player);
    }

    private void generator(final Location location, final ItemStack stack, final String kind, final int fallbackTicks) {
        int id = new BukkitRunnable() {
            private int waited;
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                waited++;
                int seconds = Math.max(1, (generatorPeriod(kind, fallbackTicks) + 19) / 20);
                if (waited < seconds) return;
                waited = 0;
                spawnGenDrop(location, stack);
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    /** Hypixel-like: stack drops at configured gen block center with zero velocity. */
    private void spawnGenDrop(Location location, ItemStack stack) {
        spawnPinnedDrop(Locations.genDropPoint(location), stack);
    }

    private void spawnForgeDrop(Location location, ItemStack stack) {
        spawnPinnedDrop(Locations.forgeDropPoint(location), stack);
    }

    private void spawnPinnedDrop(Location at, ItemStack stack) {
        if (at == null || at.getWorld() == null || stack == null) return;
        Item item = at.getWorld().dropItem(at, stack.clone());
        item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        item.setPickupDelay(10);
        try { item.getClass().getMethod("setInvulnerable", boolean.class).invoke(item, true); } catch (Throwable ignored) { }
        arena.generatedItems().add(item.getUniqueId());
        final UUID id = item.getUniqueId();
        final Location pin = at.clone();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                Entity e = findEntity(id);
                if (e instanceof Item) {
                    e.teleport(pin);
                    e.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        });
    }

    private int generatorPeriod(String kind, int fallback) {
        int tier = kind.equals("diamond") ? diamondTier : kind.equals("emerald") ? emeraldTier : 1;
        if (tier == 1) return plugin.getConfig().getInt("generator-periods." + kind, fallback);
        int tierFallback = kind.equals("diamond") ? (tier == 2 ? 460 : 240) : (tier == 2 ? 900 : 600);
        return plugin.getConfig().getInt("generator-upgrades." + kind + ".tier-" + tier + "-period", tierFallback);
    }

    private int upgradeAt(String kind, int tier) {
        int fallback = kind.equals("diamond") ? (tier == 2 ? 360 : 720) : (tier == 2 ? 720 : 1080);
        return plugin.getConfig().getInt("generator-upgrades." + kind + ".tier-" + tier + "-seconds", fallback);
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

    private List<Player> arenaPlayers() {
        List<Player> players = new ArrayList<Player>();
        for (UUID uuid : arena.players().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) players.add(player);
        }
        return players;
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

    private static String roman(int tier) {
        return new String[] {"I", "II", "III"}[Math.max(1, Math.min(3, tier)) - 1];
    }

    private static int[] computeBounds(ArenaSettings settings) {
        List<Location> points = new ArrayList<Location>();
        if (settings.waitingSpawn() != null) points.add(settings.waitingSpawn());
        if (settings.spectator() != null) points.add(settings.spectator());
        for (TeamColor team : settings.configuredTeams()) {
            ArenaSettings.TeamSettings t = settings.team(team);
            if (t.spawn() != null) points.add(t.spawn());
            if (t.bed() != null) points.add(t.bed());
            if (t.forge() != null) points.add(t.forge());
            if (t.itemShop() != null) points.add(t.itemShop());
            if (t.upgradeShop() != null) points.add(t.upgradeShop());
            if (t.teamChest() != null) points.add(t.teamChest());
            if (t.enderChest() != null) points.add(t.enderChest());
        }
        points.addAll(settings.diamondGenerators());
        points.addAll(settings.emeraldGenerators());
        if (points.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Location loc : points) {
            if (loc == null) continue;
            minX = Math.min(minX, loc.getBlockX());
            minY = Math.min(minY, loc.getBlockY());
            minZ = Math.min(minZ, loc.getBlockZ());
            maxX = Math.max(maxX, loc.getBlockX());
            maxY = Math.max(maxY, loc.getBlockY());
            maxZ = Math.max(maxZ, loc.getBlockZ());
        }
        int pad = GameRules.ARENA_BOUND_PAD;
        return new int[] {minX - pad, Math.max(0, minY - 20), minZ - pad, maxX + pad, maxY + pad, maxZ + pad};
    }
}
