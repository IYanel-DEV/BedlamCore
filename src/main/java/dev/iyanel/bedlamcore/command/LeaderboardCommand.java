package dev.iyanel.bedlamcore.command;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardCategory;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardEntry;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardService;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardWindow;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Read-only {@code /leaderboard [category] [solo|doubles] [page]} (aliases /lb, /top). Never errors on bad args. */
public final class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private static final List<String> CATEGORY_KEYS = Arrays.asList(
        "wins", "kills", "finalkills", "beds", "winstreak", "kdr", "fkdr", "level", "xp", "tokens");
    private static final List<String> MODE_KEYS = Arrays.asList("overall", "solo", "doubles");

    private final BedlamCore plugin;

    public LeaderboardCommand(BedlamCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!GameRules.LEADERBOARD_ENABLED || plugin.leaderboards() == null) {
            sender.sendMessage(ChatColor.RED + "Leaderboards are disabled.");
            return true;
        }
        if (!sender.hasPermission("bedlam.leaderboard")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        LeaderboardCategory category = LeaderboardCategory.WINS;
        GameType mode = null; // Overall
        int page = 1;

        for (String arg : args) {
            if (arg == null || arg.isEmpty()) continue;
            LeaderboardCategory parsed = LeaderboardCategory.byKey(arg);
            if (parsed != null) { category = parsed; continue; }
            GameType parsedMode = parseMode(arg);
            if (parsedMode != null || arg.equalsIgnoreCase("overall") || arg.equalsIgnoreCase("all")) {
                mode = parsedMode; // null for overall/all
                continue;
            }
            Integer parsedPage = parsePage(arg);
            if (parsedPage != null) { page = parsedPage; continue; }
            // Unknown token → show usage, never an error.
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label
                + " [wins|kills|finalkills|beds|winstreak|kdr|fkdr|level|xp|tokens] [solo|doubles] [page]");
            return true;
        }

        LeaderboardService service = plugin.leaderboards();
        List<LeaderboardEntry> rows = service.ranking(category, mode, LeaderboardWindow.ALL_TIME);
        int max = Math.max(1, Math.min(GameRules.LEADERBOARD_COMMAND_MAX_LINES, service.topN()));
        int pages = Math.max(1, (int) Math.ceil(rows.size() / (double) max));
        page = Math.max(1, Math.min(page, pages));

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Bed Wars Leaderboard "
            + ChatColor.GRAY + "- " + ChatColor.YELLOW + category.label()
            + ChatColor.GRAY + " (" + LeaderboardService.modeLabel(mode) + ")"
            + (pages > 1 ? ChatColor.GRAY + " [" + page + "/" + pages + "]" : ""));

        if (rows.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No ranked players yet.");
            return true;
        }
        int start = (page - 1) * max;
        for (int i = start; i < rows.size() && i < start + max; i++) {
            LeaderboardEntry entry = rows.get(i);
            String rankColor = ChatColor.translateAlternateColorCodes('&', GameRules.rankColor(entry.rank()));
            String valueColor = ChatColor.translateAlternateColorCodes('&', GameRules.LEADERBOARD_FMT_VALUE);
            sender.sendMessage(rankColor + "#" + entry.rank() + " " + ChatColor.WHITE + entry.name()
                + ChatColor.GRAY + " - " + valueColor + entry.formattedValue());
        }

        if (sender instanceof Player) {
            int myRank = service.rankOf(((Player) sender).getUniqueId(), category, mode);
            sender.sendMessage(myRank > 0
                ? ChatColor.GREEN + "Your Rank: " + ChatColor.AQUA + "#" + myRank
                : ChatColor.GRAY + "You are not ranked yet.");
        }
        return true;
    }

    private static GameType parseMode(String arg) {
        String m = arg.toLowerCase(Locale.US);
        if (m.equals("solo")) return GameType.SOLO;
        if (m.equals("doubles") || m.equals("double") || m.equals("duo")) return GameType.DOUBLES;
        if (m.equals("trios") || m.equals("trio") || m.equals("3v3") || m.equals("3v3v3v3")) return GameType.TRIOS;
        if (m.equals("quads") || m.equals("quad") || m.equals("4v4") || m.equals("4v4v4v4")) return GameType.QUADS;
        return null;
    }

    private static Integer parsePage(String arg) {
        try {
            int v = Integer.parseInt(arg.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.US);
            for (String key : CATEGORY_KEYS) if (key.startsWith(prefix)) out.add(key);
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.US);
            for (String key : MODE_KEYS) if (key.startsWith(prefix)) out.add(key);
        }
        return out;
    }
}
