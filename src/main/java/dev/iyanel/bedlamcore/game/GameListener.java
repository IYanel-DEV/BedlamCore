package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public final class GameListener implements Listener {
    private final BedlamCore plugin;

    public GameListener(BedlamCore plugin) { this.plugin = plugin; }

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
        plugin.gui().disconnect(event.getPlayer());
        plugin.games().leave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
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
        if (name.equals("Leave Game")) {
            event.setCancelled(true);
            plugin.games().leave(player);
            giveNavigation(player);
            return;
        }
        GameType placer = plugin.gui().npcPlacer(item);
        if (placer != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);
            plugin.gui().placeNpc(player, placer, event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5));
            return;
        }
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        if (arena.state() == Arena.State.RUNNING && item != null && item.getType() == Items.material("FIRE_CHARGE", "FIREBALL")
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            takeOne(player, item);
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setIsIncendiary(false);
            fireball.setYield(2F);
            return;
        }
        if (event.getClickedBlock() == null || arena.state() != Arena.State.RUNNING) return;
        if (event.getClickedBlock().getType().name().contains("BED")) { event.setCancelled(true); return; }
        for (TeamColor team : arena.settings().configuredTeams()) {
            if (Locations.near(event.getClickedBlock().getLocation(), arena.settings().team(team).itemShop(), 2.0)) { event.setCancelled(true); plugin.gui().openShop(player); return; }
            if (Locations.near(event.getClickedBlock().getLocation(), arena.settings().team(team).upgradeShop(), 2.0)) { event.setCancelled(true); plugin.gui().openUpgrades(player); return; }
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        GameType mode = plugin.npcs().mode(event.getRightClicked());
        if (mode != null) {
            event.setCancelled(true);
            plugin.gui().openQueue(event.getPlayer(), mode);
            return;
        }
        ArenaManager manager = plugin.games().arenaInWorld(event.getRightClicked().getWorld().getName());
        String shop = manager == null ? null : manager.shop(event.getRightClicked());
        if (shop == null) return;
        event.setCancelled(true);
        if (shop.equals("ITEM")) plugin.gui().openShop(event.getPlayer());
        else plugin.gui().openUpgrades(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.npcs().mode(event.getEntity()) != null) { event.setCancelled(true); return; }
        if (event.getEntity().hasMetadata(LobbyNpcService.META_HOLO)) { event.setCancelled(true); return; }
        ArenaManager displayArena = plugin.games().arenaInWorld(event.getEntity().getWorld().getName());
        if (displayArena != null && displayArena.isDisplay(event.getEntity())) { event.setCancelled(true); return; }
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        if (arena.state() != Arena.State.RUNNING || arena.eliminated().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // Faster void / deep-fall kill instead of slow drain.
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setDamage(1000);
            return;
        }
        Location waiting = arena.settings().waitingSpawn();
        if (waiting != null && player.getLocation().getY() <= GameRules.voidKillY(waiting.getY())) {
            event.setDamage(1000);
        }
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
        if (plugin.npcs().mode(event.getEntity()) != null || manager != null && manager.isDisplay(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler public void onNpcArmor(PlayerArmorStandManipulateEvent event) {
        ArenaManager manager = plugin.games().arenaInWorld(event.getRightClicked().getWorld().getName());
        if (plugin.npcs().mode(event.getRightClicked()) != null || event.getRightClicked().hasMetadata(LobbyNpcService.META_HOLO)
            || manager != null && manager.isDisplay(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler public void onBedEnter(PlayerBedEnterEvent event) {
        if (plugin.games().arena(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (!isBedlamTitle(title)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        plugin.gui().click((Player) event.getWhoClicked(), title, event.getCurrentItem());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        ArenaManager manager = plugin.games().arena(event.getPlayer());
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return;
        if (!manager.mayPlace(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
            return;
        }
        manager.recordPlaced(event.getBlockPlaced());
        if (event.getBlockPlaced().getType() == Material.TNT) {
            final org.bukkit.Location location = event.getBlockPlaced().getLocation().add(0.5, 0, 0.5);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    event.getBlockPlaced().setType(Material.AIR);
                    TNTPrimed tnt = event.getBlockPlaced().getWorld().spawn(location, TNTPrimed.class);
                    tnt.setFuseTicks(40);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ArenaManager manager = plugin.games().arena(event.getPlayer());
        if (manager == null) return;
        boolean bed = manager.isBed(event.getBlock());
        if (!manager.mayBreak(event.getPlayer(), event.getBlock()) || bed) event.setCancelled(true);
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
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockY() == event.getTo().getBlockY()) return;
        Player player = event.getPlayer();
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null || manager.arena().state() != Arena.State.RUNNING) return;
        if (manager.arena().eliminated().contains(player.getUniqueId())) return;
        Location waiting = manager.arena().settings().waitingSpawn();
        if (waiting != null && event.getTo().getY() <= GameRules.voidKillY(waiting.getY())) player.setHealth(0);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ArenaManager manager = plugin.games().arena(event.getEntity());
        if (manager == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        manager.handleDeath(event.getEntity());
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
        if (manager == null) return;
        event.setRespawnLocation(manager.respawnLocation(event.getPlayer()));
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { manager.afterRespawn(player); } });
    }

    @EventHandler public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && plugin.games().arena((Player) event.getEntity()) != null) { event.setCancelled(true); ((Player) event.getEntity()).setFoodLevel(20); }
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.gui().acceptSkinInput(event.getPlayer(), event.getMessage())) event.setCancelled(true);
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
        String name = Items.name(event.getItemDrop().getItemStack());
        if (name.equals("Bedlam Menu") || name.equals("Bedlam Setup") || name.equals("Leave Game") || plugin.waitingTemplates().isTool(event.getItemDrop().getItemStack()) || plugin.gui().npcPlacer(event.getItemDrop().getItemStack()) != null) event.setCancelled(true);
    }

    public void giveNavigation(Player player) {
        if (plugin.games().arena(player) != null) return;
        if (Items.name(player.getInventory().getItem(7)).equals("Bedlam Menu")) player.getInventory().setItem(7, null);
        if (plugin.isAdmin(player)) {
            boolean gameSetup = plugin.games().arenaInWorld(player.getWorld().getName()) != null || plugin.gui().hasArenaDraft(player);
            player.getInventory().setItem(8, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Bedlam Setup",
                ChatColor.GRAY + (gameSetup ? "Open this world's game setup" : "Open lobby and world setup")));
        } else if (Items.name(player.getInventory().getItem(8)).equals("Bedlam Setup")) player.getInventory().setItem(8, null);
    }

    private static boolean isBedlamTitle(String title) {
        String clean = ChatColor.stripColor(title);
        return clean.equals("Bedlam Menu") || clean.equals("Bedlam Setup") || clean.equals("Lobby Setup") || clean.equals("Game Worlds")
            || clean.equals("World Actions") || clean.equals("Confirm World Delete") || clean.equals("Game Setup") || clean.equals("Team Setup")
            || clean.equals("NPC Editor") || clean.equals("Solo Games") || clean.equals("Doubles Games") || clean.equals("Item Shop") || clean.equals("Team Upgrades");
    }

    private static void takeOne(Player player, ItemStack item) { if (item.getAmount() <= 1) player.setItemInHand(null); else item.setAmount(item.getAmount() - 1); }
}
