package dev.iyanel.bedlamcore.command;

import dev.iyanel.bedlamcore.BedlamCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BedlamCommand implements CommandExecutor {
    private final BedlamCore plugin;

    public BedlamCommand(BedlamCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "menu" : args[0].toLowerCase();
        if (action.equals("reload") || action.equals("start")) {
            if (!sender.hasPermission("bedlam.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            if (action.equals("reload")) {
                plugin.reloadBedlam();
                sender.sendMessage(ChatColor.GREEN + "BedlamCore reloaded.");
            } else if (!plugin.arenaManager().forceStart()) {
                sender.sendMessage(ChatColor.RED + "At least two queued players are required.");
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only. Console may use /bedlam start or reload.");
            return true;
        }
        Player player = (Player) sender;
        if (action.equals("menu")) plugin.gui().openMain(player);
        else if (action.equals("join")) plugin.arenaManager().join(player);
        else if (action.equals("leave")) plugin.arenaManager().leave(player);
        else player.sendMessage(ChatColor.YELLOW + "/bedlam [menu|join|leave|start|reload]");
        return true;
    }
}
