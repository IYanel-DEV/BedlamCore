package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.arena.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public final class SidebarService {
    private final BedlamCore plugin;

    public SidebarService(BedlamCore plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override public void run() { updateAll(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    @SuppressWarnings("deprecation")
    public void update(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("bedlam", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "BEDLAM");
        List<String> lines = lines(player);
        int score = lines.size();
        for (String line : lines) objective.getScore(unique(line, score)).setScore(score--);
        player.setScoreboard(board);
    }

    private List<String> lines(Player player) {
        List<String> lines = new ArrayList<String>();
        lines.add(ChatColor.DARK_GRAY + "────────────");
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) {
            lines.add(ChatColor.WHITE + "Lobby");
            lines.add(ChatColor.GRAY + "Online: " + ChatColor.GREEN + Bukkit.getOnlinePlayers().size());
            lines.add(ChatColor.AQUA + "Solo: " + ChatColor.WHITE + plugin.games().waiting(GameType.SOLO));
            lines.add(ChatColor.GOLD + "Doubles: " + ChatColor.WHITE + plugin.games().waiting(GameType.DOUBLES));
            lines.add(ChatColor.GRAY + trim(player.getWorld().getName(), 13));
        } else {
            Arena arena = manager.arena();
            lines.add(ChatColor.WHITE + arena.settings().gameType().displayName());
            lines.add(ChatColor.GRAY + trim(arena.settings().worldName(), 13));
            if (arena.state() == Arena.State.WAITING || arena.state() == Arena.State.COUNTDOWN) {
                lines.add(ChatColor.YELLOW + state(manager));
                lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + arena.players().size() + "/" + arena.settings().maximumPlayers());
            } else {
                TeamColor team = arena.team(player.getUniqueId());
                lines.add(ChatColor.WHITE + "Team: " + (team == null ? ChatColor.GRAY + "Spectator" : team.coloredName()));
                for (TeamColor color : arena.settings().configuredTeams()) {
                    lines.add(color.chatColor() + color.displayName() + ": " + (arena.bedAlive(color) ? ChatColor.GREEN + "BED" : ChatColor.RED + "FINAL"));
                }
            }
        }
        lines.add(ChatColor.DARK_GRAY + "─────────── ");
        lines.add(colors(plugin.getConfig().getString("scoreboard.footer", "&fplay.bedlam")));
        return lines;
    }

    private static String state(ArenaManager manager) {
        return manager.arena().state() == Arena.State.COUNTDOWN ? "Starts: " + manager.countdownRemaining() + "s" : "Waiting...";
    }

    private static String unique(String line, int salt) {
        String value = trim(line, 38);
        return value + ChatColor.values()[salt % ChatColor.values().length];
    }

    private static String trim(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit); }
    private static String colors(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
}
