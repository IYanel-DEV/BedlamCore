package dev.iyanel.bedlamcore.game;

// REGISTER in BedlamCore: new ChestSoundListener(plugin)

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.UUID;

public final class ChestSoundListener implements Listener {
    private final BedlamCore plugin;
    private UUID depositPlayer;
    private int depositBefore;
    private UUID lastSoundPlayer;
    private String lastSoundKind;
    private long lastSoundAt;

    public ChestSoundListener(BedlamCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!running(player) || !matchChest(player, event.getInventory()) || !once(player, "open")) return;
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            Sounds.play(player, "ENDERCHEST_OPEN", "BLOCK_ENDER_CHEST_OPEN", "CHEST_OPEN", "BLOCK_CHEST_OPEN");
        } else {
            Sounds.play(player, "CHEST_OPEN", "BLOCK_CHEST_OPEN");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!running(player) || !matchChest(player, event.getInventory()) || !once(player, "close")) return;
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            Sounds.play(player, "ENDERCHEST_CLOSE", "BLOCK_ENDER_CHEST_CLOSE", "CHEST_CLOSE", "BLOCK_CHEST_CLOSE");
        } else {
            Sounds.play(player, "CHEST_CLOSE", "BLOCK_CHEST_CLOSE");
        }
    }

    /** Snapshot hand before GameListener HIGH consumes a successful fast-deposit. */
    @EventHandler(priority = EventPriority.LOW)
    public void markDeposit(PlayerInteractEvent event) {
        depositPlayer = null;
        if (!depositAttempt(event)) return;
        ItemStack hand = event.getPlayer().getItemInHand();
        if (hand == null) return;
        depositPlayer = event.getPlayer().getUniqueId();
        depositBefore = hand.getAmount();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDeposit(PlayerInteractEvent event) {
        if (depositPlayer == null || !depositPlayer.equals(event.getPlayer().getUniqueId())) return;
        depositPlayer = null;
        ItemStack hand = event.getPlayer().getItemInHand();
        int after = hand == null || hand.getType() == Material.AIR ? 0 : hand.getAmount();
        if (after >= depositBefore) return;
        if (!once(event.getPlayer(), "deposit")) return;
        Sounds.play(event.getPlayer(), "CHICKEN_EGG_POP", "ENTITY_CHICKEN_EGG", "CLICK", "UI_BUTTON_CLICK");
    }

    // ponytail: 1.8 right-click / double-chest can fire the same open twice in one tick
    private boolean once(Player player, String kind) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        if (id.equals(lastSoundPlayer) && kind.equals(lastSoundKind) && now - lastSoundAt < 50L) return false;
        lastSoundPlayer = id;
        lastSoundKind = kind;
        lastSoundAt = now;
        return true;
    }

    private boolean running(Player player) {
        ArenaManager manager = plugin.games().arena(player);
        return manager != null && manager.arena().state() == Arena.State.RUNNING && !manager.isSoftSpectating(player);
    }

    private boolean matchChest(Player player, Inventory inventory) {
        if (inventory == null) return false;
        if (inventory.getType() == InventoryType.ENDER_CHEST) return true;
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return false;
        for (TeamColor team : manager.arena().settings().configuredTeams()) {
            if (inventory == manager.arena().teamChest(team)) return true;
        }
        return false;
    }

    /** Same path as GameListener.onInteract + ArenaManager.fastDeposit, without changing either. */
    private boolean depositAttempt(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) return false;
        Player player = event.getPlayer();
        if (!running(player)) return false;
        ItemStack hand = player.getItemInHand();
        if (hand == null || !GameRules.canFastDeposit(hand.getType().name())) return false;
        ArenaManager manager = plugin.games().arena(player);
        Arena arena = manager.arena();
        TeamColor teamChest = manager.teamChestAt(event.getClickedBlock().getLocation());
        if (teamChest != null) return arena.teamChest(teamChest) != null;
        return manager.enderChestAt(event.getClickedBlock().getLocation()) != null
            || event.getClickedBlock().getType().name().contains("ENDER_CHEST");
    }
}
