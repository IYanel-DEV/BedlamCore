package dev.iyanel.bedlamcore.command;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.GameType;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BedlamCommand implements CommandExecutor {
    private final BedlamCore plugin;

    public BedlamCommand(BedlamCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "menu" : args[0].toLowerCase();
        if (action.equals("reload") || action.equals("forcestart") || action.equals("start")) {
            if (!sender.hasPermission("bedlam.admin")) { sender.sendMessage(ChatColor.RED + "You do not have permission."); return true; }
            if (action.equals("reload")) { plugin.reloadBedlam(); sender.sendMessage(ChatColor.GREEN + "BedlamCore reloaded."); return true; }
            if (!(sender instanceof Player)) { sender.sendMessage("A player must stand in the arena to force-start it."); return true; }
            ArenaManager manager = plugin.games().arena((Player) sender);
            if (manager == null || !manager.forceStart()) sender.sendMessage(ChatColor.RED + "Join a waiting game first. One player is enough for admin testing.");
            return true;
        }
        if (!(sender instanceof Player)) { sender.sendMessage("Players only. Console may use /bedlam reload."); return true; }
        Player player = (Player) sender;
        if (action.equals("menu")) plugin.gui().openMain(player);
        else if (action.equals("solo")) plugin.games().quickJoin(player, GameType.SOLO);
        else if (action.equals("doubles") || action.equals("duals")) plugin.games().quickJoin(player, GameType.DOUBLES);
        else if (action.equals("leave")) plugin.games().leave(player);
        else player.sendMessage(ChatColor.YELLOW + "/bedlam [menu|solo|doubles|leave|forcestart|reload]");
        return true;
    }
}
