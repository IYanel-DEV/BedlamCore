package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
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

    /** Tab list + nametag colors from match teams. Invis players: nametag NEVER (armor hide is InvisArmor). */
    @SuppressWarnings("deprecation")
    private void applyTeamColors(Scoreboard board, Player viewer) {
        ArenaManager manager = plugin.games().arena(viewer);
        if (manager == null || manager.arena().state() == Arena.State.WAITING || manager.arena().state() == Arena.State.COUNTDOWN) {
            viewer.setPlayerListName(null);
            return;
        }
        Arena arena = manager.arena();
        for (TeamColor color : arena.settings().configuredTeams()) {
            Team team = styleTeam(board.registerNewTeam(color.name()), color);
            Team invis = styleTeam(board.registerNewTeam("I" + color.name()), color);
            invis.setNameTagVisibility(NameTagVisibility.NEVER);
        }
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            Player member = Bukkit.getPlayer(entry.getKey());
            if (member == null || entry.getValue() == null) continue;
            // Soft-specs are hidePlayer'd; keep their tab/team colors for other soft-specs.
            boolean hideTag = member.hasPotionEffect(PotionEffectType.INVISIBILITY) && !manager.isSoftSpectating(member);
            Team team = board.getTeam((hideTag ? "I" : "") + entry.getValue().name());
            if (team != null) team.addEntry(member.getName());
            member.setPlayerListName(entry.getValue().chatColor() + member.getName());
        }
    }

    private static Team styleTeam(Team team, TeamColor color) {
        team.setPrefix(color.chatColor().toString());
        try {
            team.getClass().getMethod("setColor", ChatColor.class).invoke(team, color.chatColor());
        } catch (Throwable ignored) { }
        return team;
    }

    private List<String> lines(Player player) {
        List<String> lines = new ArrayList<String>();
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) {
            StatsStore.Record stats = plugin.stats().get(player.getUniqueId());
            int into = GameRules.xpIntoLevel(stats.xp);
            String lobbyId = trim(plugin.getConfig().getString("scoreboard.lobby-id", "L1"), 6);
            lines.add(ChatColor.GRAY + date() + "  " + ChatColor.DARK_GRAY + lobbyId);
            lines.add(" ");
            lines.add(ChatColor.WHITE + "Level: " + ChatColor.GRAY + stats.level + "*");
            lines.add(" ");
            lines.add(ChatColor.WHITE + "Progress: " + ChatColor.AQUA + GameRules.compactXp(into) + "/" + GameRules.compactXp(GameRules.XP_PER_LEVEL));
            lines.add(xpBar(into));
            lines.add(" ");
            lines.add(ChatColor.WHITE + "Tokens: " + ChatColor.GREEN + GameRules.commas(stats.tokens));
            lines.add(" ");
            lines.add(ChatColor.WHITE + "Total Kills: " + ChatColor.GREEN + GameRules.commas(stats.kills));
            lines.add(ChatColor.WHITE + "Total Wins: " + ChatColor.GREEN + GameRules.commas(stats.wins));
        } else {
            Arena arena = manager.arena();
            String instance = trim(arena.settings().id(), 10);
            lines.add(ChatColor.GRAY + date() + " " + ChatColor.DARK_GRAY + instance);
            lines.add(" ");
            if (arena.state() == Arena.State.WAITING || arena.state() == Arena.State.COUNTDOWN) {
                lines.add(ChatColor.WHITE + "Mode: " + ChatColor.GREEN + arena.settings().gameType().displayName());
                lines.add(ChatColor.WHITE + "Map: " + ChatColor.GREEN + trim(arena.settings().id(), 12));
                lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + arena.players().size() + "/" + arena.settings().maximumPlayers());
                lines.add(arena.state() == Arena.State.COUNTDOWN
                    ? ChatColor.WHITE + "Starting in " + ChatColor.GREEN + manager.countdownRemaining() + "s"
                    : ChatColor.YELLOW + "Waiting...");
            } else {
                lines.add(manager.nextGeneratorUpgradeLine());
                lines.add(" ");
                TeamColor you = arena.team(player.getUniqueId());
                for (TeamColor color : arena.settings().configuredTeams()) {
                    String marker = arena.bedAlive(color)
                        ? ChatColor.GREEN + "✓"
                        : ChatColor.GREEN + String.valueOf(arena.aliveCount(color));
                    String youTag = color == you ? ChatColor.GRAY + " YOU" : "";
                    lines.add(color.chatColor() + "" + color.displayName().charAt(0) + " " + ChatColor.WHITE + color.displayName()
                        + ChatColor.GRAY + ": " + marker + youTag);
                }
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

    private static String xpBar(int xpInto) {
        int filled = GameRules.xpBarFilled(xpInto, GameRules.XP_BAR_SLOTS);
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GRAY).append('[').append(ChatColor.AQUA);
        for (int i = 0; i < filled; i++) bar.append('\u2588');
        bar.append(ChatColor.GRAY);
        for (int i = filled; i < GameRules.XP_BAR_SLOTS; i++) bar.append('\u2591');
        bar.append(ChatColor.GRAY).append(']');
        return bar.toString();
    }

    private static String unique(String line, int salt) {
        String value = trim(line, 38);
        return value + ChatColor.values()[salt % ChatColor.values().length];
    }

    private static String trim(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit); }
    private static String colors(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
}
