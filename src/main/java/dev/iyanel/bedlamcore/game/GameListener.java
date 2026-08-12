package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.gui.GuiController;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public final class GameListener implements Listener {
    private final BedlamCore plugin;

    public GameListener(BedlamCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { giveNavigation(event.getPlayer()); }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.arenaManager().leave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String name = Items.name(item);
        if (name.equals("Bedlam Menu") || name.equals("Bedlam Setup")) {
            event.setCancelled(true);
            if (name.equals("Bedlam Setup")) plugin.gui().openSetup(player); else plugin.gui().openMain(player);
            return;
        }
        if (name.equals("Leave Game")) {
            event.setCancelled(true);
            plugin.arenaManager().leave(player);
            giveNavigation(player);
            return;
        }
        Arena arena = plugin.arenaManager().arena();
        if (arena.state() == Arena.State.RUNNING && arena.contains(player.getUniqueId()) && item != null
            && item.getType() == Items.material("FIRE_CHARGE", "FIREBALL")
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            takeOne(player, item);
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setIsIncendiary(false);
            fireball.setYield(2F);
            return;
        }
        if (event.getClickedBlock() == null || arena.state() != Arena.State.RUNNING || !arena.contains(player.getUniqueId())) return;
        for (TeamColor team : arena.settings().configuredTeams()) {
            if (Locations.near(event.getClickedBlock().getLocation(), arena.settings().team(team).itemShop(), 2.0)) {
                event.setCancelled(true);
                plugin.gui().openShop(player);
                return;
            }
            if (Locations.near(event.getClickedBlock().getLocation(), arena.settings().team(team).upgradeShop(), 2.0)) {
                event.setCancelled(true);
                plugin.gui().openUpgrades(player);
                return;
            }
        }
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
        Arena arena = plugin.arenaManager().arena();
        if (!arena.contains(event.getPlayer().getUniqueId()) || arena.state() != Arena.State.RUNNING) return;
        plugin.arenaManager().recordPlaced(event.getBlockPlaced());
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
        if (!plugin.arenaManager().mayBreak(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Arena arena = plugin.arenaManager().arena();
        if (arena.state() != Arena.State.RUNNING) return;
        Iterator<org.bukkit.block.Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            org.bukkit.block.Block block = iterator.next();
            String key = Locations.blockKey(block.getLocation());
            if (block.getType().name().contains("BED") || !arena.placedBlocks().remove(key)) iterator.remove();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.arenaManager().arena().contains(event.getEntity().getUniqueId())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        plugin.arenaManager().handleDeath(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.arenaManager().arena().contains(event.getPlayer().getUniqueId())) return;
        event.setRespawnLocation(plugin.arenaManager().respawnLocation(event.getPlayer()));
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { plugin.arenaManager().afterRespawn(player); }
        });
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Arena arena = plugin.arenaManager().arena();
        if (!arena.contains(player.getUniqueId())) return;
        if (arena.state() != Arena.State.RUNNING || arena.eliminated().contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && plugin.arenaManager().arena().contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        String name = Items.name(event.getItemDrop().getItemStack());
        if (name.equals("Bedlam Menu") || name.equals("Bedlam Setup") || name.equals("Leave Game")) event.setCancelled(true);
    }

    public void giveNavigation(Player player) {
        Arena arena = plugin.arenaManager().arena();
        if (arena.contains(player.getUniqueId())) return;
        player.getInventory().setItem(7, Items.named(new ItemStack(Material.NETHER_STAR), ChatColor.RED + "Bedlam Menu"));
        if (player.hasPermission("bedlam.admin")) {
            player.getInventory().setItem(8, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Bedlam Setup"));
        }
    }

    private static boolean isBedlamTitle(String title) {
        String clean = ChatColor.stripColor(title);
        return clean.equals("Bedlam Menu") || clean.equals("Arena Setup") || clean.startsWith("Setup: ")
            || clean.equals("Item Shop") || clean.equals("Team Upgrades");
    }

    private static void takeOne(Player player, ItemStack item) {
        if (item.getAmount() <= 1) player.setItemInHand(null);
        else item.setAmount(item.getAmount() - 1);
    }
}
