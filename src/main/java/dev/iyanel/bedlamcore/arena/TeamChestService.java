package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Team/ender chests + punch-deposit. Owned by ArenaManager. */
final class TeamChestService {
    private final ArenaManager manager;
    private final ArenaDisplayService displays;

    TeamChestService(ArenaManager manager, ArenaDisplayService displays) {
        this.manager = manager;
        this.displays = displays;
    }

    void ensureTeamChests() {
        Arena arena = manager.arena();
        for (TeamColor team : arena.settings().configuredTeams()) {
            ArenaSettings.TeamSettings settings = arena.settings().team(team);
            placeChestBlock(settings.teamChest(), Material.CHEST, false);
            placeChestBlock(settings.enderChest(), Items.material("ENDER_CHEST"), true);
            spawnChestHologram(settings.teamChest());
            spawnChestHologram(settings.enderChest());
        }
    }

    TeamColor teamChestAt(Location location) {
        for (TeamColor team : manager.arena().settings().configuredTeams()) {
            if (Locations.near(location, manager.arena().settings().team(team).teamChest(), 1.5)) return team;
        }
        return null;
    }

    TeamColor enderChestAt(Location location) {
        for (TeamColor team : manager.arena().settings().configuredTeams()) {
            if (Locations.near(location, manager.arena().settings().team(team).enderChest(), 1.5)) return team;
        }
        return null;
    }

    boolean openTeamChest(Player player, TeamColor chestTeam) {
        Arena arena = manager.arena();
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

    boolean openEnderChest(Player player) {
        player.openInventory(player.getEnderChest());
        return true;
    }

    boolean fastDeposit(Player player, Inventory target, ItemStack hand) {
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

    private void placeChestBlock(Location location, Material type, boolean ender) {
        if (location == null || location.getWorld() == null) return;
        Block block = location.getBlock();
        if (block.getType() != type) block.setType(type);
    }

    private void spawnChestHologram(Location location) {
        if (location == null || location.getWorld() == null) return;
        Location pin = location.getBlock().getLocation().add(0.5, GameRules.CHEST_HOLO_Y, 0.5);
        displays.spawnHologram(pin, ChatColor.YELLOW + "" + ChatColor.BOLD + "PUNCH TO DEPOSIT");
    }
}
