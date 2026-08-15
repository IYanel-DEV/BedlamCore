package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Arena {
    public enum State { WAITING, COUNTDOWN, RUNNING, ENDING }
    public enum TrapType {

        BLINDNESS("Blindness Trap"),
        COUNTER_OFFENSIVE("Counter-Offensive Trap"),
        MINER_FATIGUE("Miner Fatigue Trap"),
        REVEAL("Reveal Trap");

        private final String display;
        TrapType(String display) { this.display = display; }
        public String displayName() { return display; }
    }

    private final ArenaSettings settings;
    private State state = State.WAITING;
    private final Map<UUID, TeamColor> players = new LinkedHashMap<UUID, TeamColor>();
    private final Set<UUID> eliminated = new HashSet<UUID>();
    /** 0 = leather, 1 = iron helm/chest, 2 = diamond helm/chest. Legs/boots always leather. */
    private final Map<UUID, Integer> armorTier = new HashMap<UUID, Integer>();
    private final Set<UUID> chainmailOwned = new HashSet<UUID>();
    /** 0 = none; 1 wood … 4 diamond. Persists across deaths once purchased. */
    private final Map<UUID, Integer> pickaxeTier = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> axeTier = new HashMap<UUID, Integer>();
    private final Set<UUID> shearsOwned = new HashSet<UUID>();
    private final Map<TeamColor, Boolean> beds = new EnumMap<TeamColor, Boolean>(TeamColor.class);
    /** Teams that received ≥1 player assignment at match start (empty filler teams stay out). */
    private final Set<TeamColor> occupiedThisMatch = EnumSet.noneOf(TeamColor.class);
    private final Map<TeamColor, Integer> protection = new EnumMap<TeamColor, Integer>(TeamColor.class);
    private final Map<TeamColor, Integer> forgeLevel = new EnumMap<TeamColor, Integer>(TeamColor.class);
    private final Map<TeamColor, Integer> hasteLevel = new EnumMap<TeamColor, Integer>(TeamColor.class);
    private final Set<TeamColor> sharpness = new HashSet<TeamColor>();
    private final Set<TeamColor> healPool = new HashSet<TeamColor>();

    private final Map<TeamColor, Integer> cushionedBootsLevel = new EnumMap<TeamColor, Integer>(TeamColor.class);
    private final Map<TeamColor, List<TrapType>> traps = new EnumMap<TeamColor, List<TrapType>>(TeamColor.class);
    private final Map<TeamColor, Long> trapCooldownUntil = new EnumMap<TeamColor, Long>(TeamColor.class);
    private final Map<UUID, Long> trapImmunityUntil = new HashMap<UUID, Long>();
    private final Map<TeamColor, Inventory> teamChests = new EnumMap<TeamColor, Inventory>(TeamColor.class);
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
    public boolean chainmailOwned(UUID player) { return chainmailOwned.contains(player); }
    public void chainmailOwned(UUID player, boolean value) { if (value) chainmailOwned.add(player); else chainmailOwned.remove(player); }
    public int pickaxeTier(UUID player) { return pickaxeTier.containsKey(player) ? pickaxeTier.get(player) : 0; }
    public void pickaxeTier(UUID player, int tier) { pickaxeTier.put(player, GameRules.clampToolTier(tier)); }
    public int axeTier(UUID player) { return axeTier.containsKey(player) ? axeTier.get(player) : 0; }
    public void axeTier(UUID player, int tier) { axeTier.put(player, GameRules.clampToolTier(tier)); }
    public boolean shearsOwned(UUID player) { return shearsOwned.contains(player); }
    public void shearsOwned(UUID player, boolean value) {
        if (value) shearsOwned.add(player);
        else shearsOwned.remove(player);
    }
    public boolean bedAlive(TeamColor team) { return Boolean.TRUE.equals(beds.get(team)); }
    public void destroyBed(TeamColor team) { beds.put(team, false); }
    public boolean wasOccupiedThisMatch(TeamColor team) { return occupiedThisMatch.contains(team); }
    public void markOccupied(TeamColor team) { if (team != null) occupiedThisMatch.add(team); }
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

    public int cushionedBootsLevel(TeamColor team) { return cushionedBootsLevel.containsKey(team) ? cushionedBootsLevel.get(team) : 0; }
    public void cushionedBootsLevel(TeamColor team, int level) { cushionedBootsLevel.put(team, Math.max(0, Math.min(2, level))); }
    public List<TrapType> traps(TeamColor team) { return traps.get(team); }
    public Inventory teamChest(TeamColor team) { return teamChests.get(team); }
    public Set<String> placedBlocks() { return placedBlocks; }
    public Map<TeamColor, List<BlockState>> bedSnapshots() { return bedSnapshots; }
    public List<Integer> tasks() { return tasks; }
    public Set<UUID> generatedItems() { return generatedItems; }

    public void clearPlayerState(UUID player) {
        armorTier.remove(player);
        chainmailOwned.remove(player);
        pickaxeTier.remove(player);
        axeTier.remove(player);
        shearsOwned.remove(player);
    }

    public boolean enqueueTrap(TeamColor team, TrapType type) {
        List<TrapType> queue = traps.get(team);
        if (queue == null || queue.size() >= GameRules.TRAP_QUEUE_MAX) return false;
        queue.add(type);
        return true;
    }

    public TrapType popTrap(TeamColor team) {
        List<TrapType> queue = traps.get(team);
        if (queue == null || queue.isEmpty()) return null;
        return queue.remove(0);
    }

    public boolean trapReady(TeamColor team, long nowMillis) {
        Long until = trapCooldownUntil.get(team);
        return until == null || nowMillis >= until;
    }

    public void armTrapCooldown(TeamColor team, long untilMillis) {
        trapCooldownUntil.put(team, untilMillis);
    }

    public void grantTrapImmunity(UUID player, long untilMillis) { trapImmunityUntil.put(player, untilMillis); }
    public boolean trapImmune(UUID player, long nowMillis) {
        Long until = trapImmunityUntil.get(player);
        if (until == null) return false;
        if (GameRules.activeUntil(until, nowMillis)) return true;
        trapImmunityUntil.remove(player);
        return false;
    }

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
        chainmailOwned.clear();
        pickaxeTier.clear();
        axeTier.clear();
        shearsOwned.clear();
        sharpness.clear();
        healPool.clear();

        cushionedBootsLevel.clear();
        placedBlocks.clear();
        bedSnapshots.clear();
        generatedItems.clear();
        protection.clear();
        forgeLevel.clear();
        hasteLevel.clear();
        beds.clear();
        occupiedThisMatch.clear();
        traps.clear();
        trapCooldownUntil.clear();
        trapImmunityUntil.clear();
        teamChests.clear();
        for (TeamColor team : TeamColor.values()) {
            beds.put(team, true);
            protection.put(team, 0);
            forgeLevel.put(team, 0);
            hasteLevel.put(team, 0);
            traps.put(team, new ArrayList<TrapType>());
            teamChests.put(team, Bukkit.createInventory(null, 27, GameRules.inventoryTitle(team.coloredName() + " Chest")));
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
