package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.BedlamCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Reconnect grace. A participant who disconnects mid-match keeps their team slot for a short window so a network
 * hiccup does not free the slot and destroy the team bed. They return by auto-restore on rejoin or {@code /rejoin};
 * if the window lapses the slot is finally released (which is when the bed / winner is re-evaluated).
 */
public final class ReconnectService {
    private final BedlamCore plugin;
    private final Map<UUID, Pending> pending = new HashMap<UUID, Pending>();

    public ReconnectService(BedlamCore plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        final ArenaManager manager;
        final String name;
        int taskId = -1;
        Pending(ArenaManager manager, String name) { this.manager = manager; this.name = name; }
    }

    /**
     * Hold a disconnecting player's slot when they are a live participant of a RUNNING match. Returns true when the
     * slot was held — the caller must then NOT run the normal leave — or false when the player should just leave.
     */
    public boolean hold(Player player) {
        ArenaManager manager = plugin.games() == null ? null : plugin.games().arena(player);
        if (manager == null) return false;
        Arena arena = manager.arena();
        UUID uuid = player.getUniqueId();
        if (arena.state() != Arena.State.RUNNING) return false;
        if (arena.eliminated().contains(uuid)) return false;
        if (arena.team(uuid) == null) return false;
        final int seconds = plugin.getConfig().getInt("reconnect-grace-seconds", 60);
        if (seconds <= 0) return false;
        clear(uuid); // replace any stale hold
        Pending held = new Pending(manager, player.getName());
        pending.put(uuid, held);
        manager.announceReconnectHold(player.getName(), seconds);
        final UUID id = uuid;
        held.taskId = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { expire(id); }
        }, seconds * 20L).getTaskId();
        return true;
    }

    /** Restore a reconnecting player to their held match, or send them to the lobby when the match already ended. */
    public boolean restore(Player player) {
        Pending held = pending.remove(player.getUniqueId());
        if (held == null) return false;
        if (held.taskId != -1) Bukkit.getScheduler().cancelTask(held.taskId);
        if (held.manager.restoreDisconnect(player)) return true;
        held.manager.sendToNetworkLobby(player);
        player.sendMessage(ChatColor.YELLOW + "Your match has ended.");
        return false;
    }

    /** True while the player still has a reserved slot — used by win checks so a held team counts as alive. */
    public boolean has(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /** Cancel a hold without restoring (intentional /leave). */
    public void clear(UUID uuid) {
        Pending held = pending.remove(uuid);
        if (held != null && held.taskId != -1) Bukkit.getScheduler().cancelTask(held.taskId);
    }

    /** Drop every hold tied to an arena (match reset / shutdown) so no expiry task touches a recycled arena. */
    public void clearForArena(ArenaManager manager) {
        for (Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Pending held = it.next().getValue();
            if (held.manager != manager) continue;
            if (held.taskId != -1) Bukkit.getScheduler().cancelTask(held.taskId);
            it.remove();
        }
    }

    private void expire(UUID uuid) {
        Pending held = pending.remove(uuid);
        if (held == null) return;
        held.manager.dropDisconnected(uuid, held.name);
    }
}
