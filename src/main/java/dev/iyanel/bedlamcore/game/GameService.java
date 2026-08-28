package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.arena.ReconnectService;
import dev.iyanel.bedlamcore.party.Party;
import dev.iyanel.bedlamcore.party.PartyService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameService {
    private final BedlamCore plugin;
    private final Map<String, ArenaManager> arenas = new LinkedHashMap<String, ArenaManager>();
    /** Settings whose ArenaManager failed to construct (e.g. a flaky world load). Retained so a
     *  transient failure never drops the arena from {@link #settings()} and overwrites arenas.yml. */
    private final Map<String, ArenaSettings> unloaded = new LinkedHashMap<String, ArenaSettings>();
    private final ReconnectService reconnect;

    public GameService(BedlamCore plugin, Map<String, ArenaSettings> settings) {
        this.plugin = plugin;
        this.reconnect = new ReconnectService(plugin);
        for (ArenaSettings arena : settings.values()) register(arena);
    }

    public ReconnectService reconnect() { return reconnect; }

    /** Server disconnect: hold the slot (reconnect grace) for a live participant, else leave normally. */
    public void handleDisconnect(Player player) {
        if (reconnect.hold(player)) return;
        leave(player);
    }

    public Collection<ArenaManager> arenas() { return arenas.values(); }

    public Collection<ArenaSettings> settings() {
        java.util.List<ArenaSettings> result = new java.util.ArrayList<ArenaSettings>();
        for (ArenaManager manager : arenas.values()) result.add(manager.arena().settings());
        result.addAll(unloaded.values());
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
        unloaded.remove(settings.id());
        try {
            arenas.put(settings.id(), new ArenaManager(plugin, settings));
        } catch (RuntimeException e) {
            // Keep the config so saveSettings() never writes an empty arenas.yml over a good one.
            unloaded.put(settings.id(), settings);
            plugin.getLogger().severe("Failed to load arena " + settings.id()
                + " (config preserved, world will retry next load): " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ArenaSettings remove(String id) {
        unloaded.remove(id);
        ArenaManager manager = arenas.remove(id);
        if (manager == null) return null;
        manager.shutdown();
        return manager.arena().settings();
    }

    public boolean quickJoin(Player player, GameType type) {
        // Party-aware: a leader with a party of >1 queues the whole party together; everyone else is solo.
        PartyService parties = plugin.partyService();
        if (parties != null && parties.enabled() && GameRules.PARTY_QUEUE_AS_TEAM) {
            Party party = parties.partyOf(player.getUniqueId());
            if (party != null && party.size() > 1) {
                if (!party.isLeader(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Only the party leader can queue the party.");
                    return false;
                }
                return quickJoinParty(party, type, player);
            }
        }
        return quickJoinSolo(player, type);
    }

    private boolean quickJoinSolo(Player player, GameType type) {
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

    /** Queue an entire party into one waiting arena, keeping it together. All-or-nothing: no partial joins. */
    private boolean quickJoinParty(Party party, GameType type, Player leader) {
        PartyService parties = plugin.partyService();
        List<Player> members = party.onlineMembers();
        int need = members.size();
        if (need <= 0) return false;

        // Refuse up front if any member is mid-match or holding a reconnect slot (cannot be pulled in).
        for (Player member : members) {
            ArenaManager current = arena(member);
            if (current != null && (current.arena().state() == Arena.State.RUNNING || current.arena().state() == Arena.State.ENDING)) {
                leader.sendMessage(ChatColor.RED + member.getName() + " is in a running match — the party cannot queue.");
                return false;
            }
            if (reconnect.has(member.getUniqueId())) {
                leader.sendMessage(ChatColor.RED + member.getName() + " has a match to /rejoin — the party cannot queue.");
                return false;
            }
        }

        ArenaManager selected = null;
        boolean anyModeFits = false;
        for (ArenaManager manager : arenas.values()) {
            Arena arena = manager.arena();
            if (arena.settings().gameType() != type || !arena.settings().validate().isEmpty()) continue;
            int teamCount = arena.settings().configuredTeams().size();
            if (!GameRules.partyFitsMode(need, type.teamSize(), teamCount)) continue;
            anyModeFits = true;
            if (arena.state() == Arena.State.RUNNING || arena.state() == Arena.State.ENDING) continue;
            int remaining = arena.settings().maximumPlayers() - arena.players().size();
            if (remaining < need) continue;
            if (selected == null || arena.players().size() > selected.arena().players().size()) selected = manager;
        }
        if (selected == null) {
            if (!anyModeFits) {
                if (type.teamSize() <= 1) leader.sendMessage(ChatColor.RED + "A party cannot queue " + type.displayName() + " — try Doubles so you stay together.");
                else leader.sendMessage(ChatColor.RED + "Your party (" + need + ") is too large for any " + type.displayName() + " game.");
            } else {
                leader.sendMessage(ChatColor.RED + "No " + type.displayName() + " game has room for your party of " + need + " right now.");
            }
            return false;
        }
        if (!parties.callPreQueue(party, type)) return false;

        List<Player> ordered = new ArrayList<Player>();
        ordered.add(leader);
        for (Player member : members) if (!member.getUniqueId().equals(leader.getUniqueId())) ordered.add(member);
        boolean ok = selected.joinParty(ordered);
        if (ok) {
            parties.callQueued(party, type);
            leader.sendMessage(ChatColor.GREEN + "Queued your party of " + need + " for " + type.displayName() + "!");
        }
        return ok;
    }

    public boolean playAgain(Player player) {
        ArenaManager current = arena(player);
        if (current == null) return false;
        return quickJoin(player, current.arena().settings().gameType());
    }

    public void leave(Player player) {
        reconnect.clear(player.getUniqueId()); // explicit leave overrides any pending reconnect grace
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
