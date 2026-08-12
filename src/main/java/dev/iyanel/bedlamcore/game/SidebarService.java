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
import java.util.Map;
import java.util.UUID;

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
        objective.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "BED WARS");
        List<String> lines = lines(player);
        int score = lines.size();
        for (String line : lines) objective.getScore(unique(line, score)).setScore(score--);
        applyTeamColors(board, player);
        player.setScoreboard(board);
    }

    /** Tab list + nametag colors from match teams. */
    @SuppressWarnings("deprecation")
    private void applyTeamColors(Scoreboard board, Player viewer) {
        ArenaManager manager = plugin.games().arena(viewer);
        if (manager == null || manager.arena().state() == Arena.State.WAITING || manager.arena().state() == Arena.State.COUNTDOWN) {
            viewer.setPlayerListName(null);
            return;
        }
        Arena arena = manager.arena();
        for (TeamColor color : arena.settings().configuredTeams()) {
            org.bukkit.scoreboard.Team team = board.registerNewTeam(color.name());
            team.setPrefix(color.chatColor().toString());
            try {
                team.getClass().getMethod("setColor", ChatColor.class).invoke(team, color.chatColor());
            } catch (Throwable ignored) { }
        }
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            Player member = Bukkit.getPlayer(entry.getKey());
            if (member == null || entry.getValue() == null) continue;
            org.bukkit.scoreboard.Team team = board.getTeam(entry.getValue().name());
            if (team != null) team.addEntry(member.getName());
            member.setPlayerListName(entry.getValue().chatColor() + member.getName());
        }
    }

    private List<String> lines(Player player) {
        List<String> lines = new ArrayList<String>();
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) {
            lines.add(ChatColor.GRAY + date());
            lines.add(" ");
            lines.add(ChatColor.WHITE + "Lobby");
            lines.add(ChatColor.GRAY + "Online: " + ChatColor.GREEN + Bukkit.getOnlinePlayers().size());
            lines.add(ChatColor.AQUA + "Solo " + ChatColor.WHITE + plugin.games().waiting(GameType.SOLO));
            lines.add(ChatColor.GOLD + "Doubles " + ChatColor.WHITE + plugin.games().waiting(GameType.DOUBLES));
        } else {
            Arena arena = manager.arena();
            lines.add(ChatColor.GRAY + date());
            lines.add(" ");
            if (arena.state() == Arena.State.WAITING || arena.state() == Arena.State.COUNTDOWN) {
                lines.add(ChatColor.WHITE + "Mode: " + ChatColor.GREEN + arena.settings().gameType().displayName());
                lines.add(ChatColor.WHITE + "Map: " + ChatColor.GREEN + trim(arena.settings().id(), 12));
                lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + arena.players().size() + "/" + arena.settings().maximumPlayers());
                lines.add(arena.state() == Arena.State.COUNTDOWN
                    ? ChatColor.WHITE + "Starting in " + ChatColor.GREEN + manager.countdownRemaining() + "s"
                    : ChatColor.YELLOW + "Waiting...");
            } else {
                lines.add(ChatColor.WHITE + formatTime(manager.gameSeconds()));
                lines.add(" ");
                for (TeamColor color : arena.settings().configuredTeams()) {
                    String bed = arena.bedAlive(color) ? ChatColor.GREEN + "+" : ChatColor.RED + "X";
                    int alive = arena.aliveCount(color);
                    String you = color == arena.team(player.getUniqueId()) ? ChatColor.GRAY + " YOU" : "";
                    lines.add(bed + " " + color.chatColor() + color.displayName().charAt(0) + " " + ChatColor.GREEN + alive + you);
                }
                lines.add(" ");
                lines.add(ChatColor.WHITE + manager.nextGeneratorUpgrade());
                lines.add(ChatColor.AQUA + "Mode: " + ChatColor.GRAY + arena.settings().gameType().displayName());
                lines.add(ChatColor.AQUA + "Map: " + ChatColor.GRAY + trim(arena.settings().id(), 12));
            }
        }
        lines.add(" ");
        lines.add(colors(plugin.getConfig().getString("scoreboard.footer", "&eplay.bedlam")));
        return lines;
    }

    private static String date() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return String.format("%02d/%02d/%02d", cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.YEAR) % 100);
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }

    private static String unique(String line, int salt) {
        String value = trim(line, 38);
        return value + ChatColor.values()[salt % ChatColor.values().length];
    }

    private static String trim(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit); }
    private static String colors(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
}
