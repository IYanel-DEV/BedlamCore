package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
    private final WaitingStructure waitingStructure;
    private final ForgeGeneratorService generators;
    private final SoftSpectateService softSpectate;
    private final ArenaDisplayService displays;
    private final TeamChestService chests;
    private final BridgeEggLauncher bridgeEggs;
    private final MatchEffectsService effects;
    private final PlayerLoadoutService loadout;
    private final MatchRewardsService rewards;
    private final DreamDefenderService defenders;
    private int countdownTask = -1;
    private int countdownRemaining;
    private int[] bounds; // minX,minY,minZ,maxX,maxY,maxZ or null
    /** Last enemy who damaged this player (void / fall credit when getKiller is null). */
    private final Map<UUID, UUID> lastDamager = new java.util.HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastDamageAt = new java.util.HashMap<UUID, Long>();
    private final java.util.Random deathRandom = new java.util.Random();
    /** Foot block + facing from last ensureBeds (exact 2-cell footprint). */
    private final Map<TeamColor, Location> bedFeet = new java.util.EnumMap<TeamColor, Location>(TeamColor.class);
    private final Map<TeamColor, Integer> bedFaces = new java.util.EnumMap<TeamColor, Integer>(TeamColor.class);

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
        hideWorldBorder();
        settings.warnBedsOutsideBorder(plugin.getLogger());
        this.generators = new ForgeGeneratorService(this);
        this.softSpectate = new SoftSpectateService(this);
        this.displays = new ArenaDisplayService(this);
        this.chests = new TeamChestService(this, displays);
        this.bridgeEggs = new BridgeEggLauncher(this);
        this.effects = new MatchEffectsService(this);
        this.loadout = new PlayerLoadoutService(this);
        this.rewards = new MatchRewardsService(this);
        this.defenders = new DreamDefenderService(this);
        try {
            displays.spawnAll();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("spawnDisplays failed for " + settings.id() + ": " + e.getMessage());
        }
    }

    BedlamCore plugin() { return plugin; }

    /** Strip waiting paste / displays so Apply's saveOnce does not bake them into the pristine map. */
    public void prepareWorldSave() {
        waitingStructure.remove();
        displays.clear();
    }

    public Arena arena() { return arena; }
    public int countdownRemaining() { return countdownRemaining; }
    public int diamondTier() { return generators.diamondTier(); }
    public int emeraldTier() { return generators.emeraldTier(); }
    public int gameSeconds() { return generators.gameSeconds(); }

    public String nextGeneratorUpgrade() { return generators.nextGeneratorUpgrade(); }

    /** Green countdown portion for scoreboard: "Diamond II in §aM:SS". */
    public String nextGeneratorUpgradeLine() { return generators.nextGeneratorUpgradeLine(); }

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
        World joinWorld = Bukkit.getWorld(arena.settings().worldName());
        if (joinWorld != null) plugin.worlds().lockAlwaysDay(joinWorld);
        loadout.prepareLobby(player);
        announce(ChatColor.YELLOW + player.getName() + " has joined (" + arena.players().size() + "/" + arena.settings().maximumPlayers() + ")!");
        if (arena.players().size() >= minimumPlayers()) beginCountdown();
        return true;
    }

    public void leave(Player player) {
        if (!arena.contains(player.getUniqueId())) return;
        boolean announceQuit = arena.state() == Arena.State.WAITING || arena.state() == Arena.State.COUNTDOWN;
        TeamColor team = arena.players().remove(player.getUniqueId());
        arena.eliminated().remove(player.getUniqueId());
        arena.clearPlayerState(player.getUniqueId());
        softSpectate.clear(player.getUniqueId());
        lastDamager.remove(player.getUniqueId());
        lastDamageAt.remove(player.getUniqueId());
        player.setPlayerListName(null);
        sendToNetworkLobby(player);
        if (announceQuit) announce(ChatColor.YELLOW + player.getName() + " has quit!");
        if (arena.state() == Arena.State.RUNNING && team != null) {
            rewards.creditPlay(player.getUniqueId());
            rewards.sendRewardsSummary(player);
            // Disconnect / leave: clear bed so a ghost island cannot stall; empty never-occupied teams already ignored.
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
                    announce(ChatColor.YELLOW + GameRules.countdownMessage(seconds));
                    for (Player online : arenaPlayers()) {
                        Sounds.countdownTick(online);
                        if (seconds <= 5) {
                            ChatColor color = seconds >= 5 ? ChatColor.GREEN : seconds >= 3 ? ChatColor.GOLD : ChatColor.RED;
                            title(online, color + "" + ChatColor.BOLD + seconds, ChatColor.YELLOW + "Prepare to fight!");
                        }
                    }
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
        rewards.clear();
        defenders.clear();
        generators.resetForMatch();
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) {
            plugin.worlds().disableAutoSave(world);
            plugin.worlds().lockAlwaysDay(world); // force day on WAITING→RUNNING even if night during wait
        }
        Map<TeamColor, Integer> sizes = arena.teamSizes();
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            TeamColor team = GameRules.leastPopulated(teams, sizes);
            arena.players().put(uuid, team);
            arena.markOccupied(team);
            sizes.put(team, sizes.get(team) + 1);
        }
        ensureBeds();
        arena.state(Arena.State.RUNNING);
        clearArenaItems();
        displays.clearWildMobs();
        displays.purgeStrayArmorStands();
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                loadout.clearEnderChest(player);
                spawnPlayer(player, entry.getValue());
            }
        }
        generators.start();
        effects.start();
        chests.ensureTeamChests();
        refreshGeneratorLabels();
        sendStartMessage();
        // ponytail: no checkWinner here — solo force-start must not auto-win with one occupied team
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

    void title(Player player, String title, String subtitle) {
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

    public void forgetPlaced(Block block) {
        if (block != null) arena.placedBlocks().remove(Locations.blockKey(block.getLocation()));
    }

    public boolean mayPlace(Player player, Block block) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return false;
        if (isSoftSpectating(player)) {
            player.sendMessage(ChatColor.RED + "Spectators cannot build.");
            return false;
        }
        String deny = placeDenyReason(block.getLocation(), player.getUniqueId());
        if (deny != null) {
            player.sendMessage(ChatColor.RED + deny);
            return false;
        }
        return true;
    }

    /** null if build allowed (bounds / height / floor / protected gens-shops). */
    public String placeDenyReason(Location loc) {
        return placeDenyReason(loc, null);
    }

    public String placeDenyReason(Location loc, UUID playerId) {
        Location waiting = arena.settings().waitingSpawn();
        if (waiting != null && GameRules.tooHigh(loc.getBlockY(), waiting.getBlockY())) return "You cannot build that high.";
        if (GameRules.tooLow(loc.getBlockY(), buildFloorReferenceY(playerId))) return "You cannot build that low.";
        if (bounds != null && (loc.getBlockX() < bounds[0] || loc.getBlockY() < bounds[1] || loc.getBlockZ() < bounds[2]
            || loc.getBlockX() > bounds[3] || loc.getBlockY() > bounds[4] || loc.getBlockZ() > bounds[5])) {
            return "You cannot build outside the build border.";
        }
        if (aliveBedCell(loc) != null) return "You cannot build on a bed.";
        if (protectedZone(loc) && !adjacentToBed(loc)) return "You cannot build here.";
        return null;
    }

    /** Team bed Y, else team spawn Y; without player: min configured bed/spawn Y. */
    private int buildFloorReferenceY(UUID playerId) {
        if (playerId != null) {
            TeamColor team = arena.team(playerId);
            if (team != null) {
                ArenaSettings.TeamSettings t = arena.settings().team(team);
                if (t.bed() != null) return t.bed().getBlockY();
                if (t.spawn() != null) return t.spawn().getBlockY();
            }
        }
        Integer min = null;
        for (TeamColor color : arena.settings().configuredTeams()) {
            ArenaSettings.TeamSettings t = arena.settings().team(color);
            if (t.bed() != null) min = min == null ? t.bed().getBlockY() : Math.min(min, t.bed().getBlockY());
            else if (t.spawn() != null) min = min == null ? t.spawn().getBlockY() : Math.min(min, t.spawn().getBlockY());
        }
        if (min != null) return min;
        return (int) Math.floor(arena.settings().voidReferenceY());
    }

    /** Throw Bridge Egg: 3-wide team wool one block under the egg each tick (not on ProjectileHit). */
    public void launchBridgeEgg(Player player) { bridgeEggs.launch(player); }

    public boolean spawnDreamDefender(Player player, Location location) { return defenders.spawn(player, location); }

    public TeamColor defenderTeam(Entity entity) { return defenders.team(entity); }

    /** Compact 5x5 Hypixel-style team-wool tower with doorway, roof, and battlements. */
    public boolean buildPopupTower(Player player, Location center) {
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING || center == null || center.getWorld() == null) return false;
        int doorX = 0, doorZ = 0;
        double dx = player.getLocation().getX() - center.getX();
        double dz = player.getLocation().getZ() - center.getZ();
        if (Math.abs(dx) > Math.abs(dz)) doorX = dx >= 0 ? 2 : -2;
        else doorZ = dz >= 0 ? 2 : -2;
        List<Block> blocks = new ArrayList<Block>();
        List<Block> ladders = new ArrayList<Block>();
        int ladderX = doorX == 0 ? 0 : -Integer.signum(doorX);
        int ladderZ = doorZ == 0 ? 0 : -Integer.signum(doorZ);
        for (int y = 0; y <= 3; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
            if (x == doorX && z == doorZ && y <= 1) continue;
            blocks.add(center.clone().add(x, y, z).getBlock());
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (x != ladderX || z != ladderZ) blocks.add(center.clone().add(x, 4, z).getBlock());
        }
        for (int y = 0; y <= 4; y++) ladders.add(center.clone().add(ladderX, y, ladderZ).getBlock());
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if ((Math.abs(x) == 2 || Math.abs(z) == 2) && ((x + z) & 1) == 0) blocks.add(center.clone().add(x, 5, z).getBlock());
        }
        UUID playerId = player.getUniqueId();
        for (Block block : blocks) {
            if (block.getType() != Material.AIR || placeDenyReason(block.getLocation(), playerId) != null) {
                player.sendMessage(ChatColor.RED + "There is not enough clear space for a Pop-up Tower here.");
                return false;
            }
        }
        for (Block block : ladders) {
            if (block.getType() != Material.AIR || placeDenyReason(block.getLocation(), playerId) != null) {
                player.sendMessage(ChatColor.RED + "There is not enough clear space for a Pop-up Tower here.");
                return false;
            }
        }
        for (Block block : blocks) {
            team.placeAsBlock(block);
            recordPlaced(block);
        }
        byte ladderData = doorX > 0 ? (byte) 5 : doorX < 0 ? (byte) 4 : doorZ > 0 ? (byte) 3 : (byte) 2;
        for (Block block : ladders) {
            block.setType(Material.LADDER);
            block.setData(ladderData);
            recordPlaced(block);
        }
        Sounds.deploy(center);
        return true;
    }

    /** Wool on/against either bed half must not be blocked by nearby forge/shop spheres. */
    private boolean adjacentToBed(Location loc) {
        Block block = loc.getBlock();
        int[][] dirs = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = 0; i < dirs.length; i++) {
            if (block.getRelative(dirs[i][0], dirs[i][1], dirs[i][2]).getType().name().contains("BED")) return true;
        }
        return false;
    }

    public boolean mayBreak(Player player, Block block) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return true;
        if (isSoftSpectating(player)) return false;
        TeamColor brokenBed = bedAt(block.getLocation());
        if (brokenBed == null && block.getType().name().contains("BED")) brokenBed = aliveBedCell(block.getLocation());
        if (brokenBed != null) {
            TeamColor playerTeam = arena.team(player.getUniqueId());
            if (brokenBed == playerTeam) {
                player.sendMessage(ChatColor.RED + "You cannot break your own bed!");
                return false;
            }
            // Enemy beds are always breakable (solo/force-start included); empty teams do not protect beds.
            if (!arena.bedAlive(brokenBed)) {
                removeBedBlocks(arena.settings().team(brokenBed).bed());
                clearBedFootprint(brokenBed);
                return true;
            }
            arena.destroyBed(brokenBed);
            removeBedBlocks(arena.settings().team(brokenBed).bed());
            clearBedFootprint(brokenBed);
            announce(GameRules.bedBreakMessage(brokenBed.coloredName(), playerTeam.chatColor() + player.getName()));
            GameType mode = arena.settings().gameType();
            for (UUID uuid : arena.players().keySet()) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) Sounds.bedDestroyed(online);
                if (brokenBed.equals(arena.players().get(uuid))) plugin.stats().addBedLost(uuid, mode);
            }
            rewards.grant(player.getUniqueId(), GameRules.TOKENS_BED, GameRules.XP_BED, 0, 1, 0, 0, "Bed");
            // Solo force-start / last enemy bed: never-occupied empty teams ignored via teamContending.
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
        for (Location gen : arena.settings().diamondGenerators()) if (protectPlusAt(loc, gen)) return true;
        for (Location gen : arena.settings().emeraldGenerators()) if (protectPlusAt(loc, gen)) return true;
        for (TeamColor team : arena.settings().configuredTeams()) {
            ArenaSettings.TeamSettings settings = arena.settings().team(team);
            // No spawn bubble: Hypixel-like bed defense needs builds on/beside beds (spawn is usually next to bed).
            if (protectPlusAt(loc, settings.forge())) return true;
            if (protectPlusAt(loc, settings.itemShop())) return true;
            if (protectPlusAt(loc, settings.upgradeShop())) return true;
            if (protectPlusAt(loc, settings.teamChest())) return true;
            if (protectPlusAt(loc, settings.enderChest())) return true;
        }
        return false;
    }

    private static boolean protectPlusAt(Location loc, Location anchor) {
        if (loc == null || anchor == null || loc.getWorld() == null || !loc.getWorld().equals(anchor.getWorld())) return false;
        return GameRules.protectPlus(
            loc.getBlockX() - anchor.getBlockX(),
            loc.getBlockY() - anchor.getBlockY(),
            loc.getBlockZ() - anchor.getBlockZ());
    }

    /** Record last hostile damager for void / fall kill credit when getKiller() is null. */
    public void noteCombat(UUID victim, UUID attacker) {
        if (victim == null || attacker == null || victim.equals(attacker)) return;
        lastDamager.put(victim, attacker);
        lastDamageAt.put(victim, System.currentTimeMillis());
    }

    /**
     * @param killer Bukkit getKiller(), may be null (void); lastDamager fills the gap
     * @param voidDeath true when VOID cause or below void-kill Y
     * @param cause last damage cause (projectile / fall / explosion / fire / …)
     */
    public void handleDeath(Player player, Player killer, boolean voidDeath, EntityDamageEvent.DamageCause cause) {
        if (arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return;
        player.setFallDistance(0F);
        UUID uuid = player.getUniqueId();
        arena.pickaxeTier(uuid, GameRules.toolTierAfterDeath(arena.pickaxeTier(uuid)));
        arena.axeTier(uuid, GameRules.toolTierAfterDeath(arena.axeTier(uuid)));
        TeamColor team = arena.team(uuid);
        boolean finalKill = !GameRules.canRespawn(arena.bedAlive(team), arena.eliminated().contains(uuid));
        if (finalKill) arena.eliminated().add(uuid);
        else softSpectate.markRespawning(uuid);

        Player credited = resolveKiller(player, killer, team);
        handleDeathResources(player, credited, finalKill);
        String victimColored = (team == null ? ChatColor.GRAY : team.chatColor()) + player.getName();
        String killerColored = null;
        if (credited != null) {
            TeamColor killerTeam = arena.team(credited.getUniqueId());
            killerColored = (killerTeam == null ? ChatColor.GRAY : killerTeam.chatColor()) + credited.getName();
        }
        String mode = GameRules.deathMode(voidDeath, credited != null, cause == null ? null : cause.name());
        String custom = credited == null ? null : plugin.cosmetics().killMessage(credited, victimColored, killerColored, mode, finalKill);
        announce(custom != null ? custom : GameRules.killMessage(victimColored, killerColored, mode, finalKill));

        GameType gameType = arena.settings().gameType();
        plugin.stats().addDeath(uuid, gameType, finalKill);
        if (credited != null) {
            plugin.cosmetics().playKillEffect(credited, player.getLocation());
            Sounds.kill(credited);
            Sounds.levelUp(credited);
            if (finalKill) {
                rewards.grant(credited.getUniqueId(), GameRules.TOKENS_FINAL_KILL, GameRules.XP_FINAL_KILL, 1, 0, 0, 0, "Final Kill");
                plugin.stats().addFinalKill(credited.getUniqueId(), gameType);
            } else {
                rewards.grant(credited.getUniqueId(), GameRules.TOKENS_KILL, GameRules.XP_KILL, 1, 0, 0, 0, "Kill");
            }
        }
        lastDamager.remove(uuid);
        lastDamageAt.remove(uuid);
        if (finalKill) checkWinner();
    }

    /** Kill loot transfer, or 50/50 forge-drop for bed-up deaths with no killer. */
    private void handleDeathResources(Player victim, Player credited, boolean finalKill) {
        int[] totals = GameRules.countMatchOres(victim.getInventory().getContents());
        stripMatchOres(victim);
        if (!GameRules.hasMatchOres(totals)) return;
        if (credited != null) {
            int[] shares = GameRules.killLootShares(totals);
            giveMatchOres(credited, shares);
            String lootMsg = GameRules.killLootKillerMessage(shares);
            if (lootMsg != null) credited.sendMessage(lootMsg);
            victim.sendMessage(GameRules.killLootVictimMessage(credited.getName()));
            return;
        }
        if (finalKill) return;
        if (deathRandom.nextBoolean()) {
            generators.dropMatchOresAtForge(arena.team(victim.getUniqueId()), totals);
            victim.sendMessage(ChatColor.GREEN + "Your resources were returned to your forge!");
        } else {
            victim.sendMessage(ChatColor.RED + "You lost your resources!");
        }
    }

    private static void stripMatchOres(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && GameRules.isMatchOre(stack.getType().name())) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private void giveMatchOres(Player player, int[] counts) {
        if (!GameRules.hasMatchOres(counts)) return;
        giveMatchOre(player, Material.IRON_INGOT, counts[GameRules.RES_IRON]);
        giveMatchOre(player, Material.GOLD_INGOT, counts[GameRules.RES_GOLD]);
        giveMatchOre(player, Material.DIAMOND, counts[GameRules.RES_DIAMOND]);
        giveMatchOre(player, Material.EMERALD, counts[GameRules.RES_EMERALD]);
    }

    private void giveMatchOre(Player player, Material material, int count) {
        if (count <= 0) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, count));
        for (ItemStack stack : leftover.values()) {
            Item drop = player.getWorld().dropItem(player.getLocation().add(0, 0.2, 0), stack);
            drop.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            drop.setPickupDelay(0);
            arena.generatedItems().add(drop.getUniqueId());
        }
    }

    private Player resolveKiller(Player victim, Player killer, TeamColor victimTeam) {
        Player candidate = killer;
        if (candidate == null) {
            UUID id = lastDamager.get(victim.getUniqueId());
            Long hit = lastDamageAt.get(victim.getUniqueId());
            if (id != null && hit != null && GameRules.combatCreditValid(hit, System.currentTimeMillis())) candidate = Bukkit.getPlayer(id);
        }
        if (candidate == null || !candidate.isOnline()) return null;
        if (!arena.contains(candidate.getUniqueId()) || candidate.getUniqueId().equals(victim.getUniqueId())) return null;
        TeamColor killerTeam = arena.team(candidate.getUniqueId());
        if (killerTeam == null || killerTeam == victimTeam) return null;
        return candidate;
    }

    public boolean isRespawning(UUID uuid) { return softSpectate.isRespawning(uuid); }

    public Location respawnLocation(Player player) { return softSpectate.respawnLocation(player); }

    public boolean isSoftSpectating(Player player) { return softSpectate.isSoftSpectating(player); }

    /** Hypixel-style soft spectate: adventure flight + invis; never GameMode.SPECTATOR. */
    public void applySoftSpectate(Player player) { softSpectate.applySoftSpectate(player); }

    public void afterRespawn(Player player) { softSpectate.afterRespawn(player); }

    private void checkWinner() {
        if (arena.state() != Arena.State.RUNNING) return;
        Set<TeamColor> contending = new HashSet<TeamColor>();
        for (TeamColor team : arena.settings().configuredTeams()) {
            int living = 0;
            for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
                if (entry.getValue() == team && !arena.eliminated().contains(entry.getKey()) && Bukkit.getPlayer(entry.getKey()) != null) living++;
            }
            if (GameRules.teamContending(arena.bedAlive(team), living, arena.wasOccupiedThisMatch(team))) contending.add(team);
        }
        if (!GameRules.shouldEndMatch(contending.size())) return;
        final TeamColor winner = contending.isEmpty() ? null : contending.iterator().next();
        arena.state(Arena.State.ENDING);
        clearArenaItems();
        broadcast(winner == null ? ChatColor.GOLD + "Game over!" : ChatColor.GOLD + "VICTORY! " + winner.coloredName() + ChatColor.GOLD + " wins!");
        rewards.settleMatch(winner);
        if (winner != null) for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && entry.getValue() == winner && !arena.eliminated().contains(entry.getKey())) {
                loadout.prepareWinner(player);
                plugin.cosmetics().playWinEffect(player);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { reset(); }
        }, plugin.getConfig().getInt("ending-seconds", 8) * 20L);
    }

    public void reset() {
        cancelAllTasks();
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) plugin.cosmetics().clearWorldEffects(world);
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setPlayerListName(null);
                sendToNetworkLobby(player);
            }
        }
        arena.players().clear();
        arena.resetMatchData();
        rewards.clear();
        defenders.clear();
        softSpectate.clearAll();
        lastDamager.clear();
        lastDamageAt.clear();
        waitingStructure.remove();
        displays.clear();
        arena.placedBlocks().clear();
        // Win-dragon grief + match dirt: unload without save → restore pristine → reload before WAITING.
        plugin.worlds().reloadDiscarding(arena.settings());
        world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) {
            arena.settings().reattach(world);
            plugin.worlds().disableAutoSave(world);
        }
        // settings.reattach alone is not enough — WaitingStructure caches its own Location.
        waitingStructure.reattach(arena.settings().waitingSpawn());
        ensureBeds();
        arena.state(Arena.State.WAITING);
        waitingStructure.build();
        try {
            displays.spawnAll();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("spawnDisplays failed after reset for " + arena.settings().id() + ": " + e.getMessage());
        }
        refreshGeneratorLabels();
    }

    /** All Item entities (gen drops + player dumps). UUID tracking alone misses re-drops. */
    private void clearArenaItems() {
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) {
            for (Item item : new ArrayList<Item>(world.getEntitiesByClass(Item.class))) {
                item.remove();
            }
        }
        arena.generatedItems().clear();
    }

    public void shutdown() {
        cancelAllTasks();
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) plugin.cosmetics().clearWorldEffects(world);
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setPlayerListName(null);
                sendToNetworkLobby(player);
            }
        }
        arena.players().clear();
        arena.resetMatchData();
        rewards.clear();
        defenders.clear();
        lastDamager.clear();
        lastDamageAt.clear();
        waitingStructure.remove();
        displays.clear();
        // Never write win-dragon grief / match dirt — unload without save (disk pristine stays clean).
        plugin.worlds().unloadDiscarding(arena.settings());
    }

    public void rebuildWaitingStructure() {
        if (arena.state() != Arena.State.WAITING && arena.state() != Arena.State.COUNTDOWN) return;
        waitingStructure.remove();
        waitingStructure.reattach(arena.settings().waitingSpawn());
        waitingStructure.build();
    }

    public boolean isBed(Block block) { return bedAt(block.getLocation()) != null; }

    public String shop(Entity entity) { return displays.shop(entity); }

    public boolean isDisplay(Entity entity) { return displays.isDisplay(entity); }

    public void spawnPlayer(Player player, TeamColor team) {
        softSpectate.clear(player.getUniqueId());
        loadout.spawnPlayer(player, team);
    }

    public void giveOwnedTools(Player player) { loadout.giveOwnedTools(player); }

    public static ItemStack toolPickaxe(int tier) { return PlayerLoadoutService.toolPickaxe(tier); }

    public static ItemStack toolAxe(int tier) { return PlayerLoadoutService.toolAxe(tier); }

    public void replaceTool(Player player, boolean pickaxe, ItemStack tool) { loadout.replaceTool(player, pickaxe, tool); }

    public void equipArmor(Player player, TeamColor team) { loadout.equipArmor(player, team); }

    public void applyHaste(Player player, TeamColor team) { loadout.applyHaste(player, team); }

    void clearPlayer(Player player) { loadout.clearPlayer(player); }

    /** Strip match gear, teleport to network lobby, give lobby items only. */
    public void sendToNetworkLobby(Player player) {
        // Match/leave always ends setup context for this player — leftover arenaDraft made lobby compass open Game Setup.
        plugin.gui().clearArenaDraft(player);
        loadout.clearPlayer(player);
        loadout.clearEnderChest(player);
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
                    loadout.clearPlayer(online);
                    loadout.clearEnderChest(online);
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

    /** Place both bed halves for every team (1.8 BED_BLOCK data + modern colored beds). */
    private void ensureBeds() {
        bedFeet.clear();
        bedFaces.clear();
        arena.bedSnapshots().clear();
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location configured = arena.settings().team(team).bed();
            Location spawn = arena.settings().team(team).spawn();
            if (configured == null || configured.getWorld() == null || spawn == null) continue;
            World world = Bukkit.getWorld(arena.settings().worldName());
            if (world != null) configured.setWorld(world);
            int facing = resolveBedFacing(configured, spawn);
            Location foot = resolveBedFoot(configured, facing);
            removeBedBlocks(configured);
            removeBedBlocks(foot);
            Block footBlock = foot.getBlock();
            Block headBlock = footBlock.getRelative(GameRules.bedHeadDx(facing), 0, GameRules.bedHeadDz(facing));
            placeBedPair(footBlock, headBlock, facing, team.bedMaterial());
            bedFeet.put(team, foot.getBlock().getLocation());
            bedFaces.put(team, Integer.valueOf(facing));
            List<BlockState> snapshots = new ArrayList<BlockState>();
            if (footBlock.getType().name().contains("BED")) snapshots.add(footBlock.getState());
            if (headBlock.getType().name().contains("BED")) snapshots.add(headBlock.getState());
            arena.bedSnapshots().put(team, snapshots);
        }
    }

    private int resolveBedFacing(Location configured, Location spawn) {
        Block at = configured.getBlock();
        if (at.getType().name().contains("BED")) {
            int fromBlock = legacyBedFacing(at);
            if (fromBlock >= 0) return fromBlock;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < dirs.length; i++) {
            Block neighbor = at.getRelative(dirs[i][0], 0, dirs[i][1]);
            if (!neighbor.getType().name().contains("BED")) continue;
            int fromNeighbor = legacyBedFacing(neighbor);
            if (fromNeighbor >= 0) return fromNeighbor;
        }
        return GameRules.bedFacing(configured.getX(), configured.getZ(), spawn.getX(), spawn.getZ());
    }

    /** If configured point is the head half, return the foot; else configured. */
    private Location resolveBedFoot(Location configured, int facing) {
        Block at = configured.getBlock();
        if (at.getType().name().contains("BED") && legacyBedIsHead(at)) {
            return configured.clone().add(-GameRules.bedHeadDx(facing), 0, -GameRules.bedHeadDz(facing));
        }
        Block towardHead = at.getRelative(GameRules.bedHeadDx(facing), 0, GameRules.bedHeadDz(facing));
        Block towardFoot = at.getRelative(-GameRules.bedHeadDx(facing), 0, -GameRules.bedHeadDz(facing));
        if (!at.getType().name().contains("BED") && towardFoot.getType().name().contains("BED")
            && towardHead.getType() == Material.AIR) {
            return towardFoot.getLocation();
        }
        return configured.getBlock().getLocation();
    }

    @SuppressWarnings("deprecation")
    private static int legacyBedFacing(Block block) {
        try {
            if (block.getType().name().equals("BED_BLOCK") || block.getType().name().equals("BED")) {
                return block.getData() & 0x3;
            }
        } catch (Throwable ignored) { }
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            Object face = data.getClass().getMethod("getFacing").invoke(data);
            String name = face.toString();
            if ("SOUTH".equals(name)) return 0;
            if ("WEST".equals(name)) return 1;
            if ("NORTH".equals(name)) return 2;
            if ("EAST".equals(name)) return 3;
        } catch (Throwable ignored) { }
        return -1;
    }

    @SuppressWarnings("deprecation")
    private static boolean legacyBedIsHead(Block block) {
        try {
            if (block.getType().name().equals("BED_BLOCK") || block.getType().name().equals("BED")) {
                return (block.getData() & 0x8) != 0;
            }
        } catch (Throwable ignored) { }
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            Object part = data.getClass().getMethod("getPart").invoke(data);
            return part != null && "HEAD".equals(part.toString());
        } catch (Throwable ignored) { }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static void placeBedPair(Block foot, Block head, int facing, Material material) {
        if (placeModernBed(foot, head, facing, material)) return;
        Material legacy = Items.material("BED_BLOCK", "BED");
        foot.setType(legacy);
        foot.setData((byte) facing);
        head.setType(legacy);
        head.setData((byte) (facing | 0x8));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean placeModernBed(Block foot, Block head, int facing, Material material) {
        if (material == null || !material.name().endsWith("_BED")) return false;
        try {
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Class<?> bedClass = Class.forName("org.bukkit.block.data.type.Bed");
            Class<?> partClass = Class.forName("org.bukkit.block.data.type.Bed$Part");
            Class<?> faceClass = Class.forName("org.bukkit.block.BlockFace");
            String faceName = facing == 0 ? "SOUTH" : facing == 1 ? "WEST" : facing == 2 ? "NORTH" : "EAST";
            Object face = Enum.valueOf((Class<Enum>) faceClass, faceName);
            Object footPart = Enum.valueOf((Class<Enum>) partClass, "FOOT");
            Object headPart = Enum.valueOf((Class<Enum>) partClass, "HEAD");
            try {
                foot.getClass().getMethod("setType", Material.class, boolean.class).invoke(foot, material, Boolean.FALSE);
                head.getClass().getMethod("setType", Material.class, boolean.class).invoke(head, material, Boolean.FALSE);
            } catch (Throwable ignored) {
                foot.setType(material);
                head.setType(material);
            }
            Object footData = foot.getClass().getMethod("getBlockData").invoke(foot);
            Object headData = head.getClass().getMethod("getBlockData").invoke(head);
            if (!bedClass.isInstance(footData) || !bedClass.isInstance(headData)) return false;
            bedClass.getMethod("setPart", partClass).invoke(footData, footPart);
            bedClass.getMethod("setFacing", faceClass).invoke(footData, face);
            bedClass.getMethod("setPart", partClass).invoke(headData, headPart);
            bedClass.getMethod("setFacing", faceClass).invoke(headData, face);
            foot.getClass().getMethod("setBlockData", blockDataClass).invoke(foot, footData);
            head.getClass().getMethod("setBlockData", blockDataClass).invoke(head, headData);
            return foot.getType().name().contains("BED") && head.getType().name().contains("BED");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private TeamColor bedAt(Location location) {
        if (location == null || location.getWorld() == null || !location.getBlock().getType().name().contains("BED")) return null;
        TeamColor footprint = aliveBedCell(location);
        if (footprint != null) return footprint;
        World world = location.getWorld();
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location bed = arena.settings().team(team).bed();
            if (bed == null) continue;
            bed.setWorld(world);
            if (Locations.near(location, bed, 2.5)) return team;
        }
        return null;
    }

    /** Exact foot/head cell for an alive bed (blocks place-on-ghost-half). */
    private TeamColor aliveBedCell(Location location) {
        if (location == null) return null;
        for (TeamColor team : arena.settings().configuredTeams()) {
            if (!arena.bedAlive(team)) continue;
            Location foot = bedFeet.get(team);
            Integer facing = bedFaces.get(team);
            if (foot == null || facing == null) {
                Location configured = arena.settings().team(team).bed();
                Location spawn = arena.settings().team(team).spawn();
                if (configured == null || spawn == null) continue;
                facing = Integer.valueOf(GameRules.bedFacing(configured.getX(), configured.getZ(), spawn.getX(), spawn.getZ()));
                foot = configured.getBlock().getLocation();
            }
            if (sameBlock(location, foot)) return team;
            Location head = foot.clone().add(GameRules.bedHeadDx(facing.intValue()), 0, GameRules.bedHeadDz(facing.intValue()));
            if (sameBlock(location, head)) return team;
        }
        return null;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private void clearBedFootprint(TeamColor team) {
        bedFeet.remove(team);
        bedFaces.remove(team);
    }

    private void removeBedBlocks(Location bed) {
        if (bed == null) return;
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (world != null) bed.setWorld(world);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            Block block = bed.clone().add(x, y, z).getBlock();
            if (block.getType().name().contains("BED")) block.setType(Material.AIR);
        }
        for (Map.Entry<TeamColor, Integer> entry : bedFaces.entrySet()) {
            Location stored = bedFeet.get(entry.getKey());
            if (stored == null || !Locations.near(bed, stored, 2.5)) continue;
            if (stored.getBlock().getType().name().contains("BED")) stored.getBlock().setType(Material.AIR);
            Block head = stored.getBlock().getRelative(GameRules.bedHeadDx(entry.getValue()), 0, GameRules.bedHeadDz(entry.getValue()));
            if (head.getType().name().contains("BED")) head.setType(Material.AIR);
        }
    }

    public TeamColor teamChestAt(Location location) { return chests.teamChestAt(location); }

    public TeamColor enderChestAt(Location location) { return chests.enderChestAt(location); }

    public boolean openTeamChest(Player player, TeamColor chestTeam) { return chests.openTeamChest(player, chestTeam); }

    public boolean openEnderChest(Player player) { return chests.openEnderChest(player); }

    public boolean fastDeposit(Player player, Inventory target, ItemStack hand) { return chests.fastDeposit(player, target, hand); }

    void refreshGeneratorLabels() { displays.refreshGeneratorLabels(); }

    private void cancelAllTasks() {
        for (Integer id : arena.tasks()) Bukkit.getScheduler().cancelTask(id);
        arena.tasks().clear();
        if (countdownTask != -1) Bukkit.getScheduler().cancelTask(countdownTask);
        countdownTask = -1;
        countdownRemaining = 0;
    }

    void broadcast(String message) {
        for (UUID uuid : arena.players().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Bedlam" + ChatColor.DARK_GRAY + "] " + message);
        }
    }

    /** Match chat without [Bedlam] prefix (Hypixel kill / bed lines). */
    private void announce(String message) {
        for (UUID uuid : arena.players().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(message);
        }
    }

    List<Player> arenaPlayers() {
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

    /** Match + waiting: no visible WorldBorder (build edge is mayPlace AABB only). */
    public void hideWorldBorder() {
        ArenaSettings.hideWorldBorder(Bukkit.getWorld(arena.settings().worldName()));
    }

    private int minimumPlayers() {
        String mode = arena.settings().gameType().name().toLowerCase();
        return plugin.getConfig().getInt("modes." + mode + ".minimum-players", 2);
    }

    private static int[] computeBounds(ArenaSettings settings) {
        int[] border = settings.buildBorderBounds();
        if (border != null) return border;
        // ponytail: padded fallback if waiting/spectator unset; border normally from radius AABB
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
