package dev.iyanel.bedlamcore.command;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.game.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class BedlamCommand implements CommandExecutor {
    private final BedlamCore plugin;

    public BedlamCommand(BedlamCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("leave")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
            Player player = (Player) sender;
            plugin.games().leave(player);
            if (plugin.games().arena(player) == null) plugin.listener().giveNavigation(player);
            return true;
        }
        String action = args.length == 0 ? "menu" : args[0].toLowerCase();
        if (action.equals("token") || action.equals("xp")) {
            return economyAdd(sender, action, args);
        }
        if (action.equals("reload") || action.equals("forcestart") || action.equals("start") || action.equals("spawnbuild")) {
            if (!plugin.isAdmin(sender)) { sender.sendMessage(ChatColor.RED + "You do not have permission."); return true; }
            if (action.equals("reload")) { plugin.reloadBedlam(); sender.sendMessage(ChatColor.GREEN + "BedlamCore reloaded."); return true; }
            if (action.equals("spawnbuild")) {
                if (!(sender instanceof Player)) { sender.sendMessage("A player must select the waiting building."); return true; }
                plugin.waitingTemplates().giveTool((Player) sender);
                return true;
            }
            if (!(sender instanceof Player)) { sender.sendMessage("A player must stand in the arena to force-start it."); return true; }
            ArenaManager manager = plugin.games().arena((Player) sender);
            if (manager == null || !manager.forceStart()) sender.sendMessage(ChatColor.RED + "Join a waiting game first. One player is enough for admin testing.");
            return true;
        }
        if (!(sender instanceof Player)) { sender.sendMessage("Players only. Console may use /bedlam reload|token|xp."); return true; }
        Player player = (Player) sender;
        if (action.equals("menu")) plugin.gui().openMain(player);
        else if (action.equals("solo")) plugin.games().quickJoin(player, GameType.SOLO);
        else if (action.equals("doubles") || action.equals("duals")) plugin.games().quickJoin(player, GameType.DOUBLES);
        else if (action.equals("leave")) plugin.games().leave(player);
        else player.sendMessage(ChatColor.YELLOW + "/bedlam [menu|solo|doubles|leave|spawnbuild|forcestart|reload|token|xp]");
        return true;
    }

    /** /bc token add <player> <amount> | /bc xp add <player> <amount> */
    private boolean economyAdd(CommandSender sender, String kind, String[] args) {
        String perm = kind.equals("token") ? "bedlam.token.add" : "bedlam.xp.add";
        if (!sender.hasPermission(perm) && !plugin.isAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("add")) {
            sender.sendMessage(ChatColor.YELLOW + "/bc " + kind + " add <player> <amount>");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Bad number: " + args[3]);
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be a positive number.");
            return true;
        }
        ResolvedPlayer target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
            return true;
        }
        StatsStore stats = plugin.stats();
        if (kind.equals("token")) {
            stats.apply(target.uuid, amount, 0, 0, 0, 0, 0);
            StatsStore.Record record = stats.get(target.uuid);
            sender.sendMessage(ChatColor.GREEN + "Added " + amount + " tokens to " + target.name
                    + ChatColor.GRAY + " (now " + record.tokens + ")");
            if (target.online != null) {
                target.online.sendMessage(ChatColor.GOLD + "+" + amount + " Tokens "
                        + ChatColor.GRAY + "(admin)");
            }
        } else {
            stats.apply(target.uuid, 0, amount, 0, 0, 0, 0);
            StatsStore.Record record = stats.get(target.uuid);
            sender.sendMessage(ChatColor.GREEN + "Added " + amount + " XP to " + target.name
                    + ChatColor.GRAY + " (level " + record.level + ", " + record.xp + " XP)");
            if (target.online != null) {
                target.online.sendMessage(ChatColor.AQUA + "+" + amount + " XP "
                        + ChatColor.GRAY + "(admin)");
            }
        }
        return true;
    }

    private static ResolvedPlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return new ResolvedPlayer(online.getUniqueId(), online.getName(), online);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.getUniqueId() == null) return null;
        if (!offline.hasPlayedBefore() && !offline.isOnline()) return null;
        String display = offline.getName() != null ? offline.getName() : name;
        return new ResolvedPlayer(offline.getUniqueId(), display, null);
    }

    private static final class ResolvedPlayer {
        final UUID uuid;
        final String name;
        final Player online;

        ResolvedPlayer(UUID uuid, String name, Player online) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
        }
    }
}
