package dev.iyanel.bedlamcore.compat;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.StatsStore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * {@code %bedlamcore_*%} placeholders. All reads hit the in-memory {@link StatsStore} cache, so they are
 * async-safe. Registered only when PlaceholderAPI is installed; unknown placeholders return "".
 */
public final class PapiExpansion extends PlaceholderExpansion {
    private final BedlamCore plugin;

    public PapiExpansion(BedlamCore plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "bedlamcore"; }
    @Override public String getAuthor() { return "Youniss"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return "";
        String key = params.toLowerCase();

        // Server-wide (player-independent) placeholders.
        if (key.equals("player_count")) return Integer.toString(Bukkit.getOnlinePlayers().size());
        if (key.startsWith("waiting_")) return Integer.toString(waiting(GameType.parse(key.substring("waiting_".length()))));

        if (player == null) return "";
        UUID uuid = player.getUniqueId();
        StatsStore.Record r = plugin.stats().get(uuid);

        switch (key) {
            case "tokens": return Integer.toString(r.tokens);
            case "xp": return Integer.toString(r.xp);
            case "level": return Integer.toString(GameRules.levelFromXp(r.xp));
            case "level_progress": return Integer.toString(GameRules.xpBarFilled(GameRules.xpIntoLevel(r.xp), 100)) + "%";
            case "kills": return Integer.toString(r.kills);
            case "deaths": return Integer.toString(r.deaths);
            case "final_kills": return Integer.toString(r.finalKills);
            case "final_deaths": return Integer.toString(r.finalDeaths);
            case "wins": return Integer.toString(r.wins);
            case "losses": return Integer.toString(r.losses);
            case "beds": return Integer.toString(r.beds);
            case "beds_lost": return Integer.toString(r.bedsLost);
            case "games": return Integer.toString(r.games);
            case "winstreak": return Integer.toString(r.winstreak);
            case "best_winstreak": return Integer.toString(r.bestWinstreak);
            case "kdr": return GameRules.formatRatio1(GameRules.ratio1(r.kills, r.deaths));
            case "fkdr": return GameRules.formatRatio1(GameRules.ratio1(r.finalKills, r.finalDeaths));
            case "prestige_color": return colorOrBlank(player);
            case "prestige_name": return prestigeName(player);
            default: break;
        }

        // Per-mode: %bedlamcore_<stat>_<mode>%  (wins/kills/games/beds).
        StatsStore.StatSlice slice = sliceFor(r, key);
        if (slice != null) {
            if (key.startsWith("wins_")) return Integer.toString(slice.wins());
            if (key.startsWith("kills_")) return Integer.toString(slice.kills());
            if (key.startsWith("games_")) return Integer.toString(slice.games());
            if (key.startsWith("beds_")) return Integer.toString(slice.bedsBroken());
        }
        return "";
    }

    /** Resolve the mode slice for a "<stat>_<mode>" key, or null if the suffix is not a known mode. */
    private static StatsStore.StatSlice sliceFor(StatsStore.Record r, String key) {
        int us = key.indexOf('_');
        if (us < 0) return null;
        String mode = key.substring(us + 1);
        if (mode.equals("solo")) return r.solo;
        if (mode.equals("doubles")) return r.doubles;
        if (mode.equals("trios")) return r.trios;
        if (mode.equals("quads")) return r.quads;
        return null;
    }

    private String colorOrBlank(OfflinePlayer player) {
        if (!player.isOnline()) return "";
        String code = plugin.cosmetics().applyPrestige(player.getPlayer());
        // applyPrestige returns section-code colours; expose as '&' codes for use in other plugins.
        return code == null ? "" : code.replace('§', '&');
    }

    private String prestigeName(OfflinePlayer player) {
        if (!player.isOnline()) return "";
        return plugin.cosmetics().prestigeName(player.getPlayer());
    }

    private int waiting(GameType type) {
        int count = 0;
        for (ArenaManager manager : plugin.games().arenas()) {
            Arena arena = manager.arena();
            if (arena.settings().gameType() == type
                && (arena.state() == Arena.State.WAITING || arena.state() == Arena.State.COUNTDOWN)) {
                count += arena.players().size();
            }
        }
        return count;
    }
}
