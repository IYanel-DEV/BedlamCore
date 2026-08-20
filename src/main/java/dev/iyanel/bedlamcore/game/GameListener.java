package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.compat.InvisArmor;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.compat.Particles;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.cosmetics.CosmeticsService;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class GameListener implements Listener {
    private static final String META_TNT_OWNER = "bedlamTntOwner";
    private static final String META_FIREBALL_OWNER = "bedlamFireballOwner";
    private final BedlamCore plugin;
    /** Match players hit by a plugin fireball this tick — cancel vanilla explosion HP. */
    private final Set<UUID> fireballNoDamage = new HashSet<UUID>();
    /** Pre-place water detection survives native sponge absorption before BlockPlaceEvent. */
    private final Map<UUID, String> wetSpongePlacements = new HashMap<UUID, String>();

    public GameListener(BedlamCore plugin) {
        this.plugin = plugin;
        registerModernBedPickupCancel();
        registerProgressCancel();
        // ponytail: 5-tick rebroadcast; per-viewer protocol lib if packet spam matters
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { InvisArmor.tick(plugin); }
        }, 5L, 5L);
        // Lobby always-day: gamerules on enable + soft refresh every 5s
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { lockLobbyDay(); }
        });
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { lockLobbyDay(); }
        }, 100L, 100L);
    }

    private void lockLobbyDay() {
        Location spawn = plugin.lobby().spawn();
        if (spawn == null || spawn.getWorld() == null) return;
        plugin.worlds().lockAlwaysDay(spawn.getWorld());
        plugin.worlds().clearWildMonsters(spawn.getWorld());
    }

    private boolean isLobbyWorld(World world) {
        Location spawn = plugin.lobby().spawn();
        return world != null && spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(world);
    }

    private boolean canLobbyBuild(Player player) {
        return GameRules.mayLobbyBuild(plugin.isAdmin(player), player.hasPermission("bedlam.lobby.build"));
    }

    /**
     * Cancel 1.8 achievements + modern advancement grants for Bedlam players (lobby / arena worlds).
     * One executor; reflects event class so 1.8 jar still compiles and modern Paper works without NMS.
     */
    @SuppressWarnings("unchecked")
    private void registerProgressCancel() {
        String[] names = {
            "org.bukkit.event.player.PlayerAchievementAwardedEvent",
            "org.bukkit.event.player.PlayerAdvancementDoneEvent",
            "com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent",
            "io.papermc.paper.event.player.PlayerAdvancementCriterionGrantEvent"
        };
        EventExecutor cancel = new EventExecutor() {
            @Override
            public void execute(Listener listener, Event event) {
                try {
                    Object playerObj = event.getClass().getMethod("getPlayer").invoke(event);
                    if (!(playerObj instanceof Player)) return;
                    if (!isBedlamManaged((Player) playerObj)) return;
                    if (event instanceof org.bukkit.event.Cancellable) {
                        ((org.bukkit.event.Cancellable) event).setCancelled(true);
                    } else {
                        try {
                            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        };
        for (String name : names) {
            try {
                Class<? extends Event> type = (Class<? extends Event>) Class.forName(name);
                Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, cancel, plugin, true);
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    private boolean isBedlamManaged(Player player) {
        if (plugin.games().arena(player) != null) return true;
        if (plugin.games().arenaInWorld(player.getWorld().getName()) != null) return true;
        Location lobby = plugin.lobby().spawn();
        return lobby != null && lobby.getWorld() != null && lobby.getWorld().equals(player.getWorld());
    }

    /** Modern API: EntityPickupItemEvent (1.12+); 1.8 uses PlayerPickupItemEvent handler below. */
    @SuppressWarnings("unchecked")
    private void registerModernBedPickupCancel() {
        try {
            final Class<? extends Event> pickupEvent = (Class<? extends Event>) Class.forName("org.bukkit.event.entity.EntityPickupItemEvent");
            Bukkit.getPluginManager().registerEvent(pickupEvent, this, EventPriority.HIGH, new EventExecutor() {
                @Override
                public void execute(Listener listener, Event event) {
                    try {
                        Entity entity = (Entity) pickupEvent.getMethod("getEntity").invoke(event);
                        if (!(entity instanceof Player)) return;
                        Player player = (Player) entity;
                        ArenaManager manager = plugin.games().arena(player);
                        if (manager == null) return;
                        if (manager.isSoftSpectating(player)) {
                            pickupEvent.getMethod("setCancelled", boolean.class).invoke(event, true);
                            return;
                        }
                        org.bukkit.entity.Item item = (org.bukkit.entity.Item) pickupEvent.getMethod("getItem").invoke(event);
                        if (item.getItemStack().getType().name().contains("BED")) {
                            pickupEvent.getMethod("setCancelled", boolean.class).invoke(event, true);
                            item.remove();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }, plugin, true);
        } catch (ClassNotFoundException ignored) {
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                Player player = event.getPlayer();
                if (plugin.getConfig().getBoolean("lobby.teleport-on-join", true) && plugin.lobby().spawn() != null) player.teleport(plugin.lobby().spawn());
                giveNavigation(player);
                plugin.views().updateAll();
                plugin.sidebars().update(player);
            }
        }, 5L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        wetSpongePlacements.remove(event.getPlayer().getUniqueId());
        InvisArmor.clear(event.getPlayer());
        plugin.gui().disconnect(event.getPlayer());
        plugin.games().leave(event.getPlayer());
        EntityVisibility.clearViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        String type = item.getType().name();
        if (!type.equals("MILK_BUCKET") && !type.equals("POTION")) return;
        final Player player = event.getPlayer();
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        final int slot = player.getInventory().getHeldItemSlot();
        final Material empty = type.equals("POTION") ? Material.GLASS_BOTTLE : Material.BUCKET;
        if (type.equals("MILK_BUCKET") && Items.name(item).equals("Magic Milk")) {
            manager.arena().grantTrapImmunity(player.getUniqueId(),
                System.currentTimeMillis() + GameRules.MAGIC_MILK_IMMUNITY_MILLIS);
            player.sendMessage(ChatColor.AQUA + "Magic Milk: traps cannot trigger on you for 30 seconds.");
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                removeContainer(player, slot, empty);
                Sounds.consumableUsed(player);
                InvisArmor.tick(plugin);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (plugin.games().arena(event.getPlayer()) == null) return;
        final Player player = event.getPlayer();
        final int slot = player.getInventory().getHeldItemSlot();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { removeContainer(player, slot, Material.BUCKET); }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Lobby: cancel trampling / physical harvest; menus+NPCs still work below.
        if (isLobbyWorld(player.getWorld()) && !canLobbyBuild(player) && event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        ItemStack item = event.getItem();
        String name = Items.name(item);
        if (plugin.waitingTemplates().isTool(item) && event.getClickedBlock() != null
            && (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (plugin.isAdmin(player)) plugin.waitingTemplates().select(player, event.getClickedBlock(), event.getAction() == Action.LEFT_CLICK_BLOCK);
            else player.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        if (name.equals("Bedlam Menu") || name.equals("Bedlam Setup")) {
            event.setCancelled(true);
            if (name.equals("Bedlam Setup")) plugin.gui().openContextSetup(player); else plugin.gui().openMain(player);
            return;
        }
        if (name.equals("Cosmetics")
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (plugin.games().arena(player) == null) plugin.gui().openCosmetics(player);
            return;
        }
        if (name.equals("Leave Game") || name.equals("Return to Lobby")) {
            event.setCancelled(true);
            plugin.games().leave(player);
            return;
        }
        if (name.equals("Play Again")) {
            event.setCancelled(true);
            plugin.games().playAgain(player);
            return;
        }
        if (name.equals("Spectate") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            plugin.gui().openSpectate(player);
            return;
        }
        GameType placer = plugin.gui().npcPlacer(item);
        if (placer != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);
            plugin.gui().placeNpc(player, placer, event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5));
            return;
        }
        if (plugin.gui().teamSetupWand(item) != null
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            plugin.gui().useTeamSetupWand(player, item);
            return;
        }
        if (plugin.gui().isDeleteStick(item)
            && (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR)) {
            // Creative cancels BlockBreak when Interact is cancelled — remove here.
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
                plugin.gui().useDeleteStick(player, item, event.getClickedBlock().getLocation());
            }
            return;
        }
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        if (manager.isSoftSpectating(player)) {
            event.setCancelled(true);
            return;
        }
        if (arena.state() == Arena.State.RUNNING && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && name.equals("Dream Defender")) {
            event.setCancelled(true);
            Location spawn = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            if (manager.spawnDreamDefender(player, spawn)) {
                takeOne(player, item);
                Sounds.deploy(spawn);
            }
            return;
        }
        if (arena.state() == Arena.State.RUNNING && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && name.equals("Pop-up Tower")) {
            event.setCancelled(true);
            Location center = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            if (manager.buildPopupTower(player, center)) takeOne(player, item);
            return;
        }
        if (arena.state() == Arena.State.RUNNING && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && item != null && item.getType().name().contains("SPONGE")) {
            Block target = event.getClickedBlock().getRelative(event.getBlockFace());
            if (hasAdjacentWater(target)) wetSpongePlacements.put(player.getUniqueId(), Locations.blockKey(target.getLocation()));
            else wetSpongePlacements.remove(player.getUniqueId());
        }
        if (arena.state() == Arena.State.RUNNING && item != null && item.getType() == Items.material("FIRE_CHARGE", "FIREBALL")
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            takeOne(player, item);
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setIsIncendiary(false);
            fireball.setYield(GameRules.FIREBALL_YIELD);
            fireball.setMetadata(META_FIREBALL_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
            return;
        }
        if (arena.state() == Arena.State.RUNNING && item != null && item.getType() == Material.EGG
            && "Bridge Egg".equals(Items.name(item))
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            takeOne(player, item);
            manager.launchBridgeEgg(player);
            return;
        }
        if (event.getClickedBlock() == null || arena.state() != Arena.State.RUNNING) return;
        // 1.8: RIGHT_CLICK_BLOCK on a bed fires interact first; cancelling it (or leaving item use
        // DEFAULT) swallows BlockPlaceEvent. Deny sleep only; force item use when holding a block.
        if (event.getClickedBlock().getType().name().contains("BED")) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack hand = player.getItemInHand();
                boolean placing = hand != null && hand.getType() != Material.AIR && hand.getType().isBlock();
                if (placing) {
                    event.setCancelled(false);
                    event.setUseInteractedBlock(Event.Result.DENY);
                    event.setUseItemInHand(Event.Result.ALLOW);
                } else {
                    event.setCancelled(true);
                }
            }
            return;
        }
        Location click = event.getClickedBlock().getLocation();
        TeamColor teamChest = manager.teamChestAt(click);
        if (teamChest != null) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                Inventory inv = arena.teamChest(teamChest);
                if (inv != null) manager.fastDeposit(player, inv, player.getItemInHand());
            } else {
                manager.openTeamChest(player, teamChest);
            }
            return;
        }
        TeamColor ender = manager.enderChestAt(click);
        if (ender != null || event.getClickedBlock().getType().name().contains("ENDER_CHEST")) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                manager.fastDeposit(player, player.getEnderChest(), player.getItemInHand());
            } else {
                manager.openEnderChest(player);
            }
            return;
        }
        // Shop/upgrade GUIs: Entity right-click only (onNpcInteract). Block clicks near NPC pins
        // must not open menus — that made floor clicks under shopkeepers open the GUI.
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        // Hologram armor stands are not NPC bodies — ignore (except personal profile lines).
        if (clicked.hasMetadata(LobbyNpcService.META_HOLO) && !plugin.npcs().isProfile(clicked)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.npcs().isCosmetics(clicked)) {
            event.setCancelled(true);
            plugin.gui().openCosmetics(event.getPlayer());
            return;
        }
        if (plugin.npcs().isProfile(clicked)) {
            event.setCancelled(true);
            UUID owner = plugin.npcs().profileOwner(clicked);
            if (owner == null || owner.equals(event.getPlayer().getUniqueId())) {
                plugin.gui().openProfileStats(event.getPlayer());
            }
            return;
        }
        GameType mode = plugin.npcs().mode(clicked);
        if (mode != null) {
            event.setCancelled(true);
            plugin.gui().openQueue(event.getPlayer(), mode);
            return;
        }
        ArenaManager manager = plugin.games().arenaInWorld(clicked.getWorld().getName());
        String shop = manager == null ? null : manager.shop(clicked);
        if (shop == null) return;
        event.setCancelled(true);
        ArenaManager playerArena = plugin.games().arena(event.getPlayer());
        if (playerArena != null && playerArena.isSoftSpectating(event.getPlayer())) return;
        if (shop.equals("ITEM")) plugin.gui().openShop(event.getPlayer());
        else plugin.gui().openUpgrades(event.getPlayer());
    }

    /**
     * The shared profile NPC body is an armor stand, which fires PlayerInteractAtEntityEvent (not the
     * PlayerInteractEntityEvent that villagers fire), so onNpcInteract never sees it. Route the profile
     * armor-stand click to the stats GUI here. Main hand only — 1.9+ fires this once per hand.
     */
    @EventHandler
    public void onNpcInteractAt(PlayerInteractAtEntityEvent event) {
        if (!plugin.npcs().isProfile(event.getRightClicked())) return;
        // getHand() only exists on 1.9+; on those versions this fires once per hand — skip the off-hand so the
        // GUI opens once. Reflection keeps it compiling against the 1.8 API (no off-hand there).
        try {
            Object hand = PlayerInteractAtEntityEvent.class.getMethod("getHand").invoke(event);
            if (hand != null && !"HAND".equals(hand.toString())) return;
        } catch (ReflectiveOperationException ignored) { }
        event.setCancelled(true);
        plugin.gui().openProfileStats(event.getPlayer());
    }

    /** Lobby: no PvP, no player→mob kills, no mob→player, no projectile combat. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLobbyCombat(EntityDamageByEntityEvent event) {
        if (!isLobbyWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (LobbyNpcService.isPluginNpc(event.getEntity())) { event.setCancelled(true); return; }
        ArenaManager displayArena = plugin.games().arenaInWorld(event.getEntity().getWorld().getName());
        if (displayArena != null && displayArena.isDisplay(event.getEntity())) { event.setCancelled(true); return; }
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) {
            if (inLobbyWorld(player) && (event.getCause() == EntityDamageEvent.DamageCause.VOID || belowWorldFloor(player))) {
                event.setCancelled(true);
                rescueLobby(player);
            }
            return;
        }
        Arena arena = manager.arena();
        if (arena.state() != Arena.State.RUNNING || arena.eliminated().contains(player.getUniqueId()) || manager.isRespawning(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // Instant void / deep-fall kill instead of slow drain.
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID || belowWorldFloor(player)) {
            event.setCancelled(true);
            player.setHealth(0);
            return;
        }
        if (fireballNoDamage.contains(player.getUniqueId())
            && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent
            && ((EntityDamageByEntityEvent) event).getDamager() instanceof Fireball
            && ((EntityDamageByEntityEvent) event).getDamager().hasMetadata(META_FIREBALL_OWNER)) {
            event.setCancelled(true);
            return;
        }
        if (atVoidKillY(manager, player.getLocation())) {
            event.setCancelled(true);
            player.setHealth(0);
        }
    }

    /** Soft-spec (respawn countdown + final): no punches / projectiles / explosion credit from them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSoftSpecAttack(EntityDamageByEntityEvent event) {
        Player attacker = combatPlayer(event.getDamager());
        if (attacker == null) return;
        ArenaManager manager = plugin.games().arena(attacker);
        if (manager != null && manager.isSoftSpectating(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeleteStickHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        ItemStack hand = player.getItemInHand();
        if (!plugin.gui().isDeleteStick(hand)) return;
        event.setCancelled(true);
        plugin.gui().useDeleteStick(player, hand, event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcHit(EntityDamageByEntityEvent event) {
        GameType mode = plugin.npcs().mode(event.getEntity());
        if (mode == null || !(event.getDamager() instanceof Player)) return;
        event.setCancelled(true);
        Player player = (Player) event.getDamager();
        if (player.isSneaking() && plugin.isAdmin(player)) plugin.gui().openNpcEditor(player, mode);
        else plugin.gui().openQueue(player, mode);
    }

    @EventHandler public void onNpcTarget(EntityTargetEvent event) {
        ArenaManager manager = plugin.games().arenaInWorld(event.getEntity().getWorld().getName());
        TeamColor defender = manager == null ? null : manager.defenderTeam(event.getEntity());
        if (defender != null && event.getTarget() instanceof Player
            && manager.arena().team(event.getTarget().getUniqueId()) == defender) {
            event.setCancelled(true);
            return;
        }
        if (plugin.npcs().mode(event.getEntity()) != null || manager != null && manager.isDisplay(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDefenderDamage(EntityDamageByEntityEvent event) {
        ArenaManager manager = plugin.games().arenaInWorld(event.getEntity().getWorld().getName());
        if (manager == null) return;
        TeamColor victimDefender = manager.defenderTeam(event.getEntity());
        Player attacker = combatPlayer(event.getDamager());
        if (victimDefender != null && attacker != null && manager.arena().team(attacker.getUniqueId()) == victimDefender) {
            event.setCancelled(true);
            return;
        }
        TeamColor attackerDefender = manager.defenderTeam(event.getDamager());
        if (attackerDefender != null && event.getEntity() instanceof Player
            && manager.arena().team(event.getEntity().getUniqueId()) == attackerDefender) event.setCancelled(true);
    }
    @EventHandler public void onNpcArmor(PlayerArmorStandManipulateEvent event) {
        ArenaManager manager = plugin.games().arenaInWorld(event.getRightClicked().getWorld().getName());
        if (plugin.npcs().mode(event.getRightClicked()) != null || event.getRightClicked().hasMetadata(LobbyNpcService.META_HOLO)
            || event.getRightClicked().hasMetadata(LobbyNpcService.META_PROFILE)
            || event.getRightClicked().hasMetadata("bedlamSetupMarker")
            || manager != null && manager.isDisplay(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler public void onBedEnter(PlayerBedEnterEvent event) {
        if (plugin.games().arena(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.Monster)) return;
        World world = event.getLocation().getWorld();
        if (world == null) return;
        ArenaManager manager = plugin.games().arenaInWorld(world.getName());
        if (manager == null && !isLobbyWorld(world)) return;
        if (LobbyNpcService.isPluginNpc(entity) || LobbyNpcService.isPet(entity)) return;
        if (manager != null && manager.defenderTeam(entity) != null) return;
        // CUSTOM = plugin NPC/pet spawn (metadata applied after event); natural/chunk/spawner cancelled.
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ArenaManager manager = plugin.games().arenaInWorld(event.getLocation().getWorld().getName());
        if (manager == null) return;
        Arena.State state = manager.arena().state();
        if (state == Arena.State.WAITING || state == Arena.State.COUNTDOWN) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        final Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        if (isBedlamTitle(title)) {
            event.setCancelled(true);
            if (plugin.gui().guiBusy(player)) return;
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getView().getTopInventory().getType() != InventoryType.CHEST) return;
            if (!GameRules.isChestGuiSize(topSize)) return;
            int raw = event.getRawSlot();
            if (raw < 0 || raw >= topSize) return;
            final ItemStack clicked = event.getCurrentItem() == null ? null : event.getCurrentItem().clone();
            final String titleCopy = title;
            final int rawCopy = raw;
            final boolean shiftLeft = event.isShiftClick() && event.isLeftClick();
            plugin.gui().beginGuiClick(player);
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    try {
                        if (player.isOnline()) plugin.gui().click(player, titleCopy, clicked, shiftLeft, rawCopy);
                    } finally {
                        plugin.gui().endGuiClick(player);
                    }
                }
            }, 1L);
            return;
        }
        if (lockMatchArmor(player, event)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (isBedlamTitle(event.getView().getTitle())) { event.setCancelled(true); return; }
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return;
        if (manager.arena().eliminated().contains(player.getUniqueId())) return;
        for (int raw : event.getRawSlots()) {
            if (raw >= 5 && raw <= 8) { event.setCancelled(true); return; }
        }
        ItemStack dragged = event.getOldCursor();
        if (dragged != null && GameRules.isArmor(dragged.getType().name())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        if (isLobbyWorld(event.getBlock().getWorld()) && !canLobbyBuild(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        ArenaManager manager = plugin.games().arena(event.getPlayer());
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return;
        if (!manager.mayPlace(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
            return;
        }
        manager.recordPlaced(event.getBlockPlaced());
        final Player player = event.getPlayer();
        // Wood Skin cosmetic: particle flourish when placing wood with a skin equipped (textures need a resource pack).
        String placedType = event.getBlockPlaced().getType().name();
        if (placedType.contains("WOOD") || placedType.contains("LOG") || placedType.contains("PLANKS")) {
            String skinId = plugin.stats().equippedCosmetic(player.getUniqueId(), CosmeticsService.CAT_WOOD_SKIN);
            CosmeticsService.Cosmetic skin = skinId == null ? null : plugin.cosmetics().get(skinId);
            if (skin != null && !skin.particles.isEmpty()) {
                Location where = event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5);
                Particles.play(null, where, 8, 0.2, skin.particles.toArray(new String[0]));
            }
        }
        if (event.getBlockPlaced().getType().name().contains("SPONGE")) {
            final Block sponge = event.getBlockPlaced();
            String expected = wetSpongePlacements.remove(player.getUniqueId());
            final boolean wet = Locations.blockKey(sponge.getLocation()).equals(expected)
                || sponge.getType().name().equals("WET_SPONGE") || hasAdjacentWater(sponge);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    int absorbed = absorbWater(sponge);
                    if ((wet || absorbed > 0) && sponge.getType().name().contains("SPONGE")) {
                        manager.forgetPlaced(sponge);
                        sponge.setType(Material.AIR);
                        Sounds.spongeAbsorb(sponge.getLocation());
                    }
                }
            });
        }
        if (event.getBlockPlaced().getType() == Material.TNT) {
            final org.bukkit.Location location = event.getBlockPlaced().getLocation().add(0.5, 0, 0.5);
            final UUID owner = player.getUniqueId();
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    event.getBlockPlaced().setType(Material.AIR);
                    TNTPrimed tnt = event.getBlockPlaced().getWorld().spawn(location, TNTPrimed.class);
                    tnt.setFuseTicks(40);
                    tnt.setMetadata(META_TNT_OWNER, new FixedMetadataValue(plugin, owner.toString()));
                    try {
                        tnt.getClass().getMethod("setSource", Entity.class).invoke(tnt, player);
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isLobbyWorld(event.getBlock().getWorld()) && !canLobbyBuild(player)) {
            event.setCancelled(true);
            return;
        }
        ItemStack hand = player.getItemInHand();
        if (plugin.gui().isDeleteStick(hand)) {
            // Interact already removes the point; only cancel the break (Creative + Survival).
            event.setCancelled(true);
            return;
        }
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        // Capture before mayBreak: enemy-bed path removes blocks, so isBed() would then be false and vanilla would drop.
        boolean bedBlock = event.getBlock().getType().name().contains("BED");
        boolean allowed = manager.mayBreak(player, event.getBlock());
        if (!allowed || bedBlock) event.setCancelled(true);
        if (!bedBlock) return;
        suppressBedDrops(event);
        clearNearbyBedItems(event.getBlock().getLocation());
        final Player breaker = player;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                clearNearbyBedItems(breaker.getLocation());
                stripBedItems(breaker);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!isLobbyWorld(event.getBlock().getWorld())) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player && canLobbyBuild((Player) entity)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.player.PlayerPickupItemEvent event) {
        ArenaManager manager = plugin.games().arena(event.getPlayer());
        if (manager == null) return;
        if (manager.isSoftSpectating(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (event.getItem().getItemStack().getType().name().contains("BED")) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    private static void suppressBedDrops(BlockBreakEvent event) {
        try { event.getClass().getMethod("setDropItems", boolean.class).invoke(event, false); } catch (Throwable ignored) { }
        try {
            Object drops = event.getClass().getMethod("getDrops").invoke(event);
            if (drops instanceof java.util.Collection) ((java.util.Collection<?>) drops).clear();
        } catch (Throwable ignored) { }
        try { event.setExpToDrop(0); } catch (Throwable ignored) { }
    }

    private static void clearNearbyBedItems(Location origin) {
        if (origin == null || origin.getWorld() == null) return;
        for (Entity entity : origin.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)) {
            if (entity.getLocation().distanceSquared(origin) > 16) continue;
            ItemStack stack = ((org.bukkit.entity.Item) entity).getItemStack();
            if (stack != null && stack.getType().name().contains("BED")) entity.remove();
        }
    }

    private static void stripBedItems(Player player) {
        if (player == null) return;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType().name().contains("BED")) {
                String name = Items.name(stack);
                // Keep lobby/spectator UI beds (named Leave / Return).
                if (name.equals("Leave Game") || name.equals("Return to Lobby") || name.equals("Play Again")) continue;
                player.getInventory().setItem(i, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        ArenaManager manager = plugin.games().arenaInWorld(event.getLocation().getWorld().getName());
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return;
        Iterator<org.bukkit.block.Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            org.bukkit.block.Block block = iterator.next();
            String key = Locations.blockKey(block.getLocation());
            if (block.getType().name().contains("BED") || !manager.arena().placedBlocks().remove(key)) iterator.remove();
        }
        if (!(event.getEntity() instanceof Fireball) || !event.getEntity().hasMetadata(META_FIREBALL_OWNER)) return;
        applyFireballBoost(manager, event.getLocation());
    }

    /** Cancel vanilla blast HP; next-tick fixed impulse for living match players only. */
    private void applyFireballBoost(ArenaManager manager, Location blast) {
        if (blast == null || blast.getWorld() == null) return;
        double r2 = GameRules.FIREBALL_RADIUS * GameRules.FIREBALL_RADIUS;
        for (UUID uuid : manager.arena().players().keySet()) {
            if (manager.arena().eliminated().contains(uuid) || manager.isRespawning(uuid)) continue;
            final Player target = Bukkit.getPlayer(uuid);
            if (target == null || manager.isSoftSpectating(target) || !target.getWorld().equals(blast.getWorld())) continue;
            if (target.getLocation().distanceSquared(blast) > r2) continue;
            fireballNoDamage.add(uuid);
            double dx = target.getLocation().getX() - blast.getX();
            double dz = target.getLocation().getZ() - blast.getZ();
            double[] xyz = new double[3];
            GameRules.fireballKnockback(dx, dz, xyz);
            final Vector boost = new Vector(xyz[0], xyz[1], xyz[2]);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    if (target.isOnline()) target.setVelocity(boost);
                }
            });
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { fireballNoDamage.clear(); }
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockY() == event.getTo().getBlockY()) return;
        Player player = event.getPlayer();
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) {
            if (inLobbyWorld(player) && (belowWorldFloor(player) || event.getTo().getY() < 0)) rescueLobby(player);
            return;
        }
        if (manager.arena().state() != Arena.State.RUNNING) return;
        if (manager.arena().eliminated().contains(player.getUniqueId()) || manager.isRespawning(player.getUniqueId())) return;
        if (atVoidKillY(manager, event.getTo()) || belowWorldFloor(player)) player.setHealth(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        ArenaManager manager = plugin.games().arena(victim);
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return;
        if (manager.isSoftSpectating(victim)) return;
        Player attacker = combatPlayer(event.getDamager());
        if (attacker == null || plugin.games().arena(attacker) != manager) return;
        if (manager.isSoftSpectating(attacker)) return;
        TeamColor vt = manager.arena().team(victim.getUniqueId());
        TeamColor at = manager.arena().team(attacker.getUniqueId());
        if (vt == null || at == null || vt == at) return;
        manager.noteCombat(victim.getUniqueId(), attacker.getUniqueId());
        if (victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            victim.removePotionEffect(PotionEffectType.INVISIBILITY);
            InvisArmor.clear(victim);
        }
    }

    private static Player combatPlayer(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof org.bukkit.entity.Projectile) {
            Object shooter = ((org.bukkit.entity.Projectile) damager).getShooter();
            if (shooter instanceof Player) return (Player) shooter;
        }
        if (damager instanceof TNTPrimed) {
            Player tagged = tntOwner(damager);
            if (tagged != null) return tagged;
            try {
                Object source = damager.getClass().getMethod("getSource").invoke(damager);
                if (source instanceof Player) return (Player) source;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static Player tntOwner(Entity tnt) {
        if (!tnt.hasMetadata(META_TNT_OWNER) || tnt.getMetadata(META_TNT_OWNER).isEmpty()) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(tnt.getMetadata(META_TNT_OWNER).get(0).asString()));
        } catch (Exception ignored) {
            return null;
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ArenaManager manager = plugin.games().arena(event.getEntity());
        if (manager == null) {
            if (!inLobbyWorld(event.getEntity())) return;
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            try { event.getClass().getMethod("setKeepLevel", boolean.class).invoke(event, Boolean.TRUE); }
            catch (Throwable ignored) { }
            event.setDeathMessage(null);
            final Player player = event.getEntity();
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    try { player.spigot().respawn(); } catch (Throwable ignored) { }
                    rescueLobby(player);
                }
            });
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        try {
            // Paper adventure: deathMessage(Component)
            event.getClass().getMethod("deathMessage", Class.forName("net.kyori.adventure.text.Component"))
                .invoke(event, new Object[]{null});
        } catch (Throwable ignored) {
        }
        Sounds.death(event.getEntity());
        InvisArmor.clear(event.getEntity());
        Player killer = event.getEntity().getKiller();
        EntityDamageEvent last = event.getEntity().getLastDamageCause();
        EntityDamageEvent.DamageCause cause = last == null ? null : last.getCause();
        Location deathAt = event.getEntity().getLocation();
        boolean voidDeath = cause == EntityDamageEvent.DamageCause.VOID
            || atVoidKillY(manager, deathAt) || belowWorldFloor(event.getEntity());
        manager.handleDeath(event.getEntity(), killer, voidDeath, cause);
        final Player player = event.getEntity();
        // Skip vanilla respawn screen (Spigot API).
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                try { player.spigot().respawn(); } catch (Throwable ignored) { }
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final ArenaManager manager = plugin.games().arena(event.getPlayer());
        if (manager == null) {
            Location spawn = plugin.lobby().spawn();
            if (spawn != null) event.setRespawnLocation(spawn);
            final Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { giveNavigation(player); }
            });
            return;
        }
        event.setRespawnLocation(manager.respawnLocation(event.getPlayer()));
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { manager.afterRespawn(player); } });
    }

    @EventHandler public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && plugin.games().arena((Player) event.getEntity()) != null) { event.setCancelled(true); ((Player) event.getEntity()).setFoodLevel(20); }
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.gui().acceptSkinInput(event.getPlayer(), event.getMessage())) event.setCancelled(true);
        else if (plugin.gui().acceptRadiusInput(event.getPlayer(), event.getMessage())) event.setCancelled(true);
        else plugin.views().formatChat(event);
    }

    @EventHandler
    public void onTeleport(final PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                plugin.views().updateAll();
                Player player = event.getPlayer();
                giveNavigation(player);
                if (!plugin.isAdmin(player) || plugin.games().arena(player) != null || plugin.gui().hasArenaDraft(player)) return;
                if (!plugin.getConfig().getBoolean("setup.auto-open-on-game-world-teleport", true) || event.getTo() == null) return;
                ArenaManager destination = plugin.games().arenaInWorld(event.getTo().getWorld().getName());
                if (destination != null) plugin.gui().beginArenaSetup(player, destination.arena().settings(), false);
            }
        });
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        String name = Items.name(stack);
        if (name.equals("Bedlam Menu") || name.equals("Bedlam Setup") || name.equals("Cosmetics")
            || name.equals("Leave Game") || name.equals("Return to Lobby") || name.equals("Play Again")
            || name.equals("Spectate") || plugin.waitingTemplates().isTool(stack) || plugin.gui().npcPlacer(stack) != null
            || plugin.gui().teamSetupWand(stack) != null || plugin.gui().isDeleteStick(stack)) {
            event.setCancelled(true);
            return;
        }
        ArenaManager manager = plugin.games().arena(event.getPlayer());
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return;
        if (GameRules.isSword(stack.getType().name())) {
            int swords = GameRules.countSwords(event.getPlayer().getInventory().getContents());
            if (!GameRules.canDropSword(swords)) event.setCancelled(true);
        }
    }

    public void giveNavigation(Player player) {
        if (plugin.games().arena(player) != null) return;
        if (!inLobbyWorld(player)) {
            // Setup / other worlds: compass only for admins (do not wipe setup tools).
            if (Items.name(player.getInventory().getItem(7)).equals("Bedlam Menu")) player.getInventory().setItem(7, null);
            if (plugin.isAdmin(player)) {
                boolean gameSetup = plugin.games().arenaInWorld(player.getWorld().getName()) != null;
                player.getInventory().setItem(8, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Bedlam Setup",
                    ChatColor.GRAY + (gameSetup ? "Open this world's game setup" : "Open lobby and world setup")));
            } else if (Items.name(player.getInventory().getItem(8)).equals("Bedlam Setup")) player.getInventory().setItem(8, null);
            return;
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        Material star = Items.material("NETHER_STAR", "GOLD_NUGGET");
        player.getInventory().setItem(4, Items.named(new ItemStack(star), ChatColor.LIGHT_PURPLE + "Cosmetics",
            ChatColor.GRAY + "Right-click to browse"));
        if (plugin.isAdmin(player)) {
            player.getInventory().setItem(8, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Bedlam Setup",
                ChatColor.GRAY + "Open lobby and world setup"));
        }
    }

    private boolean inLobbyWorld(Player player) {
        if (player == null || player.getWorld() == null || plugin.lobby() == null) return false;
        Location spawn = plugin.lobby().spawn();
        return spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(player.getWorld());
    }

    private void rescueLobby(Player player) {
        Location spawn = plugin.lobby().spawn();
        if (spawn != null) player.teleport(spawn);
        player.setFallDistance(0f);
        try { player.setHealth(player.getMaxHealth()); } catch (Throwable ignored) {
            try { player.setHealth(20.0); } catch (Throwable ignored2) { }
        }
        giveNavigation(player);
    }

    private boolean lockMatchArmor(Player player, InventoryClickEvent event) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return false;
        if (manager.arena().eliminated().contains(player.getUniqueId())) return false;
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) return true;
        ItemStack current = event.getCurrentItem();
        if (event.isShiftClick() && current != null && GameRules.isArmor(current.getType().name())) return true;
        if (event.getClick() == ClickType.NUMBER_KEY && event.getSlotType() == InventoryType.SlotType.ARMOR) return true;
        ItemStack cursor = event.getCursor();
        if (cursor != null && GameRules.isArmor(cursor.getType().name()) && event.getSlotType() == InventoryType.SlotType.ARMOR) return true;
        return false;
    }

    private static boolean isBedlamTitle(String title) {
        String clean = ChatColor.stripColor(title);
        return clean.equals("Bedlam Menu") || clean.equals("Bedlam Setup") || clean.equals("Lobby Setup") || clean.equals("Game Worlds")
            || clean.equals("Import Maps") || clean.equals("Import As")
            || clean.equals("Templates") || clean.equals("Template Mode")
            || clean.equals("World Actions") || clean.equals("Confirm World Delete") || clean.equals("Game Setup") || clean.equals("Team Setup")
            || clean.equals("NPC Editor") || clean.equals("Solo Games") || clean.equals("Doubles Games") || clean.equals("Item Shop")
            || clean.equals("Quick Buy") || clean.equals("Team Upgrades") || clean.equals("Upgrades & Traps") || clean.equals("Spectate")
            || clean.equals("Cosmetics") || clean.equals("My Cosmetics")
            || clean.equals("Kill Messages") || clean.equals("Kill Effects") || clean.equals("Win Effects")
            || clean.equals("Bed Wars Statistics")
            || clean.startsWith("Play Bed Wars ") || clean.startsWith("Map Selector ");
    }

    private static void takeOne(Player player, ItemStack item) { if (item.getAmount() <= 1) player.setItemInHand(null); else item.setAmount(item.getAmount() - 1); }

    private static void removeContainer(Player player, int slot, Material expected) {
        ItemStack result = player.getInventory().getItem(slot);
        if (result != null && result.getType() == expected) player.getInventory().setItem(slot, null);
    }

    private static boolean hasAdjacentWater(Block block) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] dir : dirs) if (isWater(block.getRelative(dir[0], dir[1], dir[2]).getType())) return true;
        return false;
    }

    /** Remove at most 64 connected water blocks up to six steps from the sponge. */
    private static int absorbWater(Block sponge) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        Queue<Block> queue = new ArrayDeque<Block>();
        Map<String, Integer> depth = new HashMap<String, Integer>();
        queue.add(sponge);
        depth.put(Locations.blockKey(sponge.getLocation()), 0);
        int removed = 0;
        while (!queue.isEmpty() && removed < 64) {
            Block current = queue.remove();
            int currentDepth = depth.get(Locations.blockKey(current.getLocation()));
            if (currentDepth >= 6) continue;
            for (int[] dir : dirs) {
                Block next = current.getRelative(dir[0], dir[1], dir[2]);
                String key = Locations.blockKey(next.getLocation());
                if (depth.containsKey(key) || !isWater(next.getType())) continue;
                depth.put(key, currentDepth + 1);
                queue.add(next);
                next.setType(Material.AIR);
                if (++removed >= 64) break;
            }
        }
        return removed;
    }

    private static boolean isWater(Material material) {
        String name = material.name();
        return name.equals("WATER") || name.equals("STATIONARY_WATER");
    }

    /** Bed-min Y − void-depth (config), else waiting spawn. */
    private boolean atVoidKillY(ArenaManager manager, Location loc) {
        if (manager == null || loc == null) return false;
        double depth = plugin.getConfig().getDouble("void-depth",
            plugin.getConfig().getDouble("void-drop-blocks", GameRules.DEFAULT_VOID_DEPTH));
        return loc.getY() <= GameRules.voidKillY(manager.arena().settings().voidReferenceY(), depth);
    }

    /** Vanilla void floor — kill instantly instead of slow VOID ticks. */
    private static boolean belowWorldFloor(Player player) {
        if (player == null || player.getWorld() == null) return false;
        int minY = 0;
        try {
            minY = (Integer) player.getWorld().getClass().getMethod("getMinHeight").invoke(player.getWorld());
        } catch (Throwable ignored) { }
        return player.getLocation().getY() < minY;
    }
}
