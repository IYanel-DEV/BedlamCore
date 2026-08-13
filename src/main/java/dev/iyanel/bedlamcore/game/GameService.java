package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameService {
    private final BedlamCore plugin;
    private final Map<String, ArenaManager> arenas = new LinkedHashMap<String, ArenaManager>();

    public GameService(BedlamCore plugin, Map<String, ArenaSettings> settings) {
        this.plugin = plugin;
        for (ArenaSettings arena : settings.values()) register(arena);
    }

    public Collection<ArenaManager> arenas() { return arenas.values(); }

    public Collection<ArenaSettings> settings() {
        java.util.List<ArenaSettings> result = new java.util.ArrayList<ArenaSettings>();
        for (ArenaManager manager : arenas.values()) result.add(manager.arena().settings());
        return result;
    }

    public ArenaManager byId(String id) { return arenas.get(id); }

    public ArenaManager arena(Player player) {
        for (ArenaManager manager : arenas.values()) if (manager.arena().contains(player.getUniqueId())) return manager;
        return null;
    }

    public ArenaManager arenaInWorld(String worldName) {
        for (ArenaManager manager : arenas.values()) {
            if (worldName.equals(manager.arena().settings().worldName())) return manager;
        }
        return null;
    }

    public void register(ArenaSettings settings) {
        ArenaManager old = arenas.remove(settings.id());
        if (old != null) old.shutdown();
        try {
            arenas.put(settings.id(), new ArenaManager(plugin, settings));
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Failed to load arena " + settings.id() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ArenaSettings remove(String id) {
        ArenaManager manager = arenas.remove(id);
        if (manager == null) return null;
        manager.shutdown();
        return manager.arena().settings();
    }

    public boolean quickJoin(Player player, GameType type) {
        ArenaManager selected = null;
        for (ArenaManager manager : arenas.values()) {
            Arena arena = manager.arena();
            if (arena.settings().gameType() != type || !arena.settings().validate().isEmpty()) continue;
            if (arena.state() == Arena.State.RUNNING || arena.state() == Arena.State.ENDING) continue;
            if (arena.players().size() >= arena.settings().maximumPlayers()) continue;
            if (selected == null || arena.players().size() > selected.arena().players().size()) selected = manager;
        }
        if (selected == null) {
            player.sendMessage(ChatColor.RED + "No configured " + type.displayName() + " game is waiting.");
            return false;
        }
        leave(player);
        return selected.join(player);
    }

    public void leave(Player player) {
        ArenaManager current = arena(player);
        if (current != null) current.leave(player);
    }

    public void rebuildWaitingStructures() {
        for (ArenaManager manager : arenas.values()) manager.rebuildWaitingStructure();
    }

    public int waiting(GameType type) {
        int count = 0;
        for (ArenaManager manager : arenas.values()) {
            if (manager.arena().settings().gameType() == type && (manager.arena().state() == Arena.State.WAITING || manager.arena().state() == Arena.State.COUNTDOWN)) count++;
        }
        return count;
    }

    public void shutdown() {
        for (ArenaManager manager : arenas.values()) manager.shutdown();
    }
}
