package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Match token/XP grants + end summary. Owned by ArenaManager. */
final class MatchRewardsService {
    private final ArenaManager manager;
    private final Map<UUID, Integer> matchTokens = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> matchXp = new HashMap<UUID, Integer>();
    private final Set<UUID> playCredited = new HashSet<UUID>();

    MatchRewardsService(ArenaManager manager) {
        this.manager = manager;
    }

    void clear() {
        matchTokens.clear();
        matchXp.clear();
        playCredited.clear();
    }

    private GameType mode() {
        return manager.arena().settings().gameType();
    }

    void creditPlay(UUID uuid) {
        if (!playCredited.add(uuid)) return;
        grant(uuid, GameRules.TOKENS_PLAY, GameRules.XP_PLAY, 0, 0, 0, 1, null);
    }

    void grant(UUID uuid, int tokens, int xp, int kills, int beds, int wins, int games, String reason) {
        manager.plugin().stats().apply(uuid, mode(), tokens, xp, kills, beds, wins, games);
        Integer t = matchTokens.get(uuid);
        matchTokens.put(uuid, (t == null ? 0 : t) + tokens);
        Integer x = matchXp.get(uuid);
        matchXp.put(uuid, (x == null ? 0 : x) + xp);
        if (reason == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(ChatColor.GOLD + "+" + tokens + " Tokens " + ChatColor.GRAY + "(" + reason + ") " + ChatColor.AQUA + "+" + xp + " XP");
        }
    }

    void settleMatch(TeamColor winner) {
        Arena arena = manager.arena();
        GameType type = mode();
        for (UUID uuid : new ArrayList<UUID>(arena.players().keySet())) {
            creditPlay(uuid);
            if (winner != null && winner.equals(arena.players().get(uuid))) {
                grant(uuid, GameRules.TOKENS_WIN, GameRules.XP_WIN, 0, 0, 1, 0, "Win");
                manager.plugin().stats().noteWin(uuid, type);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) manager.title(player, ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY!", ChatColor.GREEN + "+" + GameRules.TOKENS_WIN + " Tokens");
            } else {
                manager.plugin().stats().noteLoss(uuid, type);
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) sendRewardsSummary(player);
        }
    }

    void sendRewardsSummary(Player player) {
        Integer t = matchTokens.get(player.getUniqueId());
        Integer x = matchXp.get(player.getUniqueId());
        int tokens = t == null ? 0 : t;
        int xp = x == null ? 0 : x;
        player.sendMessage(ChatColor.YELLOW + "Rewards: " + ChatColor.GREEN + "+" + tokens + " Tokens"
            + ChatColor.GRAY + " • " + ChatColor.AQUA + "+" + xp + " XP");
    }
}
