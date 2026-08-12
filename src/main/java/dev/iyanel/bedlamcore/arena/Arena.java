package dev.iyanel.bedlamcore.arena;

import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Arena {
    public enum State { WAITING, COUNTDOWN, RUNNING, ENDING }

    private final ArenaSettings settings;
    private State state = State.WAITING;
    private final Map<UUID, TeamColor> players = new LinkedHashMap<UUID, TeamColor>();
    private final Set<UUID> eliminated = new HashSet<UUID>();
    /** 0 = leather, 1 = iron helm/chest, 2 = diamond helm/chest. Legs/boots always leather. */
    private final Map<UUID, Integer> armorTier = new HashMap<UUID, Integer>();
    private final Map<TeamColor, Boolean> beds = new EnumMap<TeamColor, Boolean>(TeamColor.class);
    private final Map<TeamColor, Integer> protection = new EnumMap<TeamColor, Integer>(TeamColor.class);
    private final Map<TeamColor, Integer> forgeLevel = new EnumMap<TeamColor, Integer>(TeamColor.class);
    private final Map<TeamColor, Integer> hasteLevel = new EnumMap<TeamColor, Integer>(TeamColor.class);
    private final Set<TeamColor> sharpness = new HashSet<TeamColor>();
    private final Set<TeamColor> healPool = new HashSet<TeamColor>();
    private final Set<String> placedBlocks = new HashSet<String>();
    private final Map<TeamColor, List<BlockState>> bedSnapshots = new EnumMap<TeamColor, List<BlockState>>(TeamColor.class);
    private final List<Integer> tasks = new ArrayList<Integer>();
    private final Set<UUID> generatedItems = new HashSet<UUID>();

    public Arena(ArenaSettings settings) {
        this.settings = settings;
        resetMatchData();
    }

    public ArenaSettings settings() { return settings; }
    public State state() { return state; }
    public void state(State value) { state = value; }
    public Map<UUID, TeamColor> players() { return players; }
    public TeamColor team(UUID player) { return players.get(player); }
    public boolean contains(UUID player) { return players.containsKey(player); }
    public Set<UUID> eliminated() { return eliminated; }
    public int armorTier(UUID player) { return armorTier.containsKey(player) ? armorTier.get(player) : 0; }
    public void armorTier(UUID player, int tier) { armorTier.put(player, Math.max(0, Math.min(2, tier))); }
    public boolean bedAlive(TeamColor team) { return Boolean.TRUE.equals(beds.get(team)); }
    public void destroyBed(TeamColor team) { beds.put(team, false); }
    public int protection(TeamColor team) { return protection.containsKey(team) ? protection.get(team) : 0; }
    public void protection(TeamColor team, int level) { protection.put(team, level); }
    public int forgeLevel(TeamColor team) { return forgeLevel.containsKey(team) ? forgeLevel.get(team) : 0; }
    public void forgeLevel(TeamColor team, int level) { forgeLevel.put(team, level); }
    public int hasteLevel(TeamColor team) { return hasteLevel.containsKey(team) ? hasteLevel.get(team) : 0; }
    public void hasteLevel(TeamColor team, int level) { hasteLevel.put(team, level); }
    public boolean sharpness(TeamColor team) { return sharpness.contains(team); }
    public void sharpness(TeamColor team, boolean value) { if (value) sharpness.add(team); else sharpness.remove(team); }
    public boolean healPool(TeamColor team) { return healPool.contains(team); }
    public void healPool(TeamColor team, boolean value) { if (value) healPool.add(team); else healPool.remove(team); }
    public Set<String> placedBlocks() { return placedBlocks; }
    public Map<TeamColor, List<BlockState>> bedSnapshots() { return bedSnapshots; }
    public List<Integer> tasks() { return tasks; }
    public Set<UUID> generatedItems() { return generatedItems; }

    public int aliveCount(TeamColor team) {
        int count = 0;
        for (Map.Entry<UUID, TeamColor> entry : players.entrySet()) {
            if (entry.getValue() == team && !eliminated.contains(entry.getKey())) count++;
        }
        return count;
    }

    public void resetMatchData() {
        eliminated.clear();
        armorTier.clear();
        sharpness.clear();
        healPool.clear();
        placedBlocks.clear();
        bedSnapshots.clear();
        generatedItems.clear();
        protection.clear();
        forgeLevel.clear();
        hasteLevel.clear();
        beds.clear();
        for (TeamColor team : TeamColor.values()) {
            beds.put(team, true);
            protection.put(team, 0);
            forgeLevel.put(team, 0);
            hasteLevel.put(team, 0);
        }
    }

    public Map<TeamColor, Integer> teamSizes() {
        Map<TeamColor, Integer> sizes = new HashMap<TeamColor, Integer>();
        for (TeamColor team : settings.configuredTeams()) sizes.put(team, 0);
        for (TeamColor team : players.values()) {
            if (team != null && sizes.containsKey(team)) sizes.put(team, sizes.get(team) + 1);
        }
        return sizes;
    }
}
