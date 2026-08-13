package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Iterator;

public final class NetworkViewService {
    private final BedlamCore plugin;

    public NetworkViewService(BedlamCore plugin) { this.plugin = plugin; }

    @SuppressWarnings("deprecation")
    public void updateAll() {
        boolean isolate = plugin.getConfig().getBoolean("isolation.enabled", true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                // Soft spectators stay invisible to living players (Hypixel-style).
                if (softSpectating(target) && !softSpectating(viewer)) {
                    viewer.hidePlayer(target);
                    continue;
                }
                if (!isolate || sameChannel(viewer, target)) viewer.showPlayer(target);
                else viewer.hidePlayer(target);
            }
        }
    }

    private boolean softSpectating(Player player) {
        ArenaManager manager = plugin.games().arena(player);
        return manager != null && manager.isSoftSpectating(player);
    }

    public void formatChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (plugin.getConfig().getBoolean("isolation.chat", true)) {
            Iterator<Player> recipients = event.getRecipients().iterator();
            while (recipients.hasNext()) if (!sameChannel(sender, recipients.next())) recipients.remove();
        }
        ArenaManager manager = plugin.games().arena(sender);
        TeamColor team = manager == null ? null : manager.arena().team(sender.getUniqueId());
        String prefix = team == null
            ? plugin.getConfig().getString("chat.lobby-prefix", "&7[LOBBY] ")
            : plugin.getConfig().getString("chat.team-prefixes." + team.name().toLowerCase(), "&" + colorCode(team) + "[" + team.displayName() + "] ");
        String suffix = plugin.getConfig().getString("chat.name-suffix", " &8> &f");
        event.setFormat(colors(prefix) + "%1$s" + colors(suffix) + "%2$s");
    }

    private boolean sameChannel(Player first, Player second) {
        ArenaManager firstArena = plugin.games().arena(first);
        ArenaManager secondArena = plugin.games().arena(second);
        if (firstArena != null || secondArena != null) return firstArena != null && firstArena == secondArena;
        return first.getWorld().equals(second.getWorld());
    }

    private static String colors(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }

    private static char colorCode(TeamColor team) {
        switch (team) {
            case RED: return 'c';
            case BLUE: return '9';
            case GREEN: return 'a';
            case YELLOW: return 'e';
            default: return 'f';
        }
    }
}
