package dev.iyanel.bedlamcore.command;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.party.Party;
import dev.iyanel.bedlamcore.party.PartyService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Executor for {@code /party} (alias {@code /p}) and {@code /pc}. Null-safe; never throws on bad input. */
public final class PartyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList(
        "create", "invite", "accept", "deny", "kick", "promote", "leave",
        "disband", "list", "open", "close", "warp", "chat", "help");

    private final BedlamCore plugin;

    public PartyCommand(BedlamCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
        Player player = (Player) sender;
        PartyService service = plugin.partyService();
        if (service == null || !service.enabled()) { player.sendMessage(ChatColor.RED + "The party system is disabled."); return true; }
        if (!player.hasPermission("bedlam.party.use")) { player.sendMessage(ChatColor.RED + "You do not have permission."); return true; }

        // /pc [message] — toggle party chat with no args, else send.
        if (command.getName().equalsIgnoreCase("pc")) {
            if (!player.hasPermission("bedlam.party.chat")) { player.sendMessage(ChatColor.RED + "You do not have permission."); return true; }
            if (args.length == 0) { service.toggleChat(player); return true; }
            service.sendPartyChat(player, join(args, 0));
            return true;
        }

        String action = args.length == 0 ? "help" : args[0].toLowerCase();
        if (action.equals("create")) service.create(player);
        else if (action.equals("invite") || action.equals("add")) {
            if (args.length < 2) { player.sendMessage(ChatColor.YELLOW + "Usage: /party invite <player>"); return true; }
            service.invite(plugin.getServer().getPlayerExact(args[1]), player);
        } else if (action.equals("accept") || action.equals("join")) {
            service.accept(player, args.length >= 2 ? args[1] : null);
        } else if (action.equals("deny") || action.equals("decline")) {
            service.decline(player, args.length >= 2 ? args[1] : null);
        } else if (action.equals("kick") || action.equals("remove")) {
            if (args.length < 2) { player.sendMessage(ChatColor.YELLOW + "Usage: /party kick <player>"); return true; }
            service.kick(plugin.getServer().getPlayerExact(args[1]), player.getUniqueId());
        } else if (action.equals("promote")) {
            if (args.length < 2) { player.sendMessage(ChatColor.YELLOW + "Usage: /party promote <player>"); return true; }
            service.promote(plugin.getServer().getPlayerExact(args[1]), player.getUniqueId());
        } else if (action.equals("leave")) service.leave(player);
        else if (action.equals("disband")) service.disbandCommand(player);
        else if (action.equals("list") || action.equals("info")) service.list(player);
        else if (action.equals("open")) service.setOpen(player, true);
        else if (action.equals("close")) service.setOpen(player, false);
        else if (action.equals("warp")) service.warp(player);
        else if (action.equals("chat")) {
            if (!player.hasPermission("bedlam.party.chat")) { player.sendMessage(ChatColor.RED + "You do not have permission."); return true; }
            if (args.length >= 2) service.sendPartyChat(player, join(args, 1));
            else service.toggleChat(player);
        } else help(player);
        return true;
    }

    private void help(Player player) {
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.STRIKETHROUGH + "----------------------------");
        player.sendMessage(ChatColor.YELLOW + "Party commands:");
        player.sendMessage(ChatColor.GREEN + "/party create" + ChatColor.GRAY + " - start a party");
        player.sendMessage(ChatColor.GREEN + "/party invite <player>" + ChatColor.GRAY + " - invite a player");
        player.sendMessage(ChatColor.GREEN + "/party accept|deny [player]" + ChatColor.GRAY + " - respond to an invite");
        player.sendMessage(ChatColor.GREEN + "/party kick|promote <player>" + ChatColor.GRAY + " - manage members");
        player.sendMessage(ChatColor.GREEN + "/party list" + ChatColor.GRAY + " - show members");
        player.sendMessage(ChatColor.GREEN + "/party open|close" + ChatColor.GRAY + " - toggle open joins");
        player.sendMessage(ChatColor.GREEN + "/party warp" + ChatColor.GRAY + " - bring members to you (lobby)");
        player.sendMessage(ChatColor.GREEN + "/party leave|disband" + ChatColor.GRAY + " - leave or disband");
        player.sendMessage(ChatColor.GREEN + "/pc <message>" + ChatColor.GRAY + " - party chat");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.STRIKETHROUGH + "----------------------------");
    }

    private static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) { if (sb.length() > 0) sb.append(' '); sb.append(args[i]); }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (command.getName().equalsIgnoreCase("pc")) return out;
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (String sub : SUBS) if (sub.startsWith(p)) out.add(sub);
            return out;
        }
        if (args.length == 2) {
            String a = args[0].toLowerCase();
            if (a.equals("invite") || a.equals("kick") || a.equals("promote") || a.equals("accept") || a.equals("deny")) {
                String p = args[1].toLowerCase();
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (online.getName().toLowerCase().startsWith(p)) out.add(online.getName());
                }
            }
        }
        return out;
    }
}
