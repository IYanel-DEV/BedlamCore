package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.storage.StatsBackend;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player tokens/XP cache. Reads stay synchronous/in-memory; a {@link StatsBackend} owns load/flush. */
public final class StatsStore {
    private static final long FLUSH_INTERVAL_TICKS = 5L * 20L; // 5s

    public static final int FAVORITE_SLOTS = GameRules.FAVORITE_SLOTS;
    /** Matches the historical Quick Buy row (blocks + sword/shears/bow). */
    public static final String[] DEFAULT_FAVORITES = GameRules.DEFAULT_FAVORITES;

    private final JavaPlugin plugin;
    private final StatsBackend backend;
    private final Map<UUID, Record> records = new LinkedHashMap<UUID, Record>();
    private boolean dirty;
    /** Fired (null-safe) after any mutation so the leaderboard cache can mark itself stale. */
    private Runnable onChange;

    public StatsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.backend = StatsBackend.fromConfig(plugin);
        load();
        if (!"yaml".equals(backend.name())) plugin.getLogger().info("Stats backend: " + backend.name());
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { save(); }
        }, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public Record get(UUID uuid) {
        Record record = records.get(uuid);
        return record == null ? new Record() : record;
    }

    /** Register a listener invoked after every mutation (leaderboard staleness signal). */
    public void setChangeListener(Runnable listener) {
        this.onChange = listener;
    }

    /** Read-only view of all records, keyed by UUID. For main-thread ranking only. */
    public Map<UUID, Record> snapshot() {
        return java.util.Collections.unmodifiableMap(records);
    }

    /** Twenty-one shop buy-keys; never null. Unset players get {@link #DEFAULT_FAVORITES}. */
    public String[] favorites(UUID uuid) {
        Record record = records.get(uuid);
        if (record == null || record.favorites == null) return DEFAULT_FAVORITES.clone();
        return padFavorites(record.favorites);
    }

    public void setFavorite(UUID uuid, int slot, String key) {
        if (slot < 0 || slot >= FAVORITE_SLOTS) return;
        Record record = ensure(uuid);
        if (record.favorites == null) record.favorites = DEFAULT_FAVORITES.clone();
        record.favorites[slot] = key == null ? "" : key;
        touch(record);
    }

    public void setFavorites(UUID uuid, String[] favorites) {
        Record record = ensure(uuid);
        record.favorites = padFavorites(favorites);
        touch(record);
    }

    public void apply(UUID uuid, int tokens, int xp, int kills, int beds, int wins, int games) {
        apply(uuid, null, tokens, xp, kills, beds, wins, games);
    }

    public void apply(UUID uuid, GameType mode, int tokens, int xp, int kills, int beds, int wins, int games) {
        Record record = ensure(uuid);
        record.tokens += tokens;
        record.xp += xp;
        record.kills += kills;
        record.beds += beds;
        record.wins += wins;
        record.games += games;
        record.level = GameRules.levelFromXp(record.xp);
        if (mode != null) {
            ModeStats slice = record.mode(mode);
            slice.kills += kills;
            slice.beds += beds;
            slice.wins += wins;
            slice.games += games;
        }
        touch(record);
    }

    public void addFinalKill(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.finalKills++;
        if (mode != null) record.mode(mode).finalKills++;
        touch(record);
    }

    public void addDeath(UUID uuid, GameType mode, boolean finalDeath) {
        Record record = ensure(uuid);
        record.deaths++;
        if (finalDeath) record.finalDeaths++;
        if (mode != null) {
            ModeStats slice = record.mode(mode);
            slice.deaths++;
            if (finalDeath) slice.finalDeaths++;
        }
        touch(record);
    }

    public void addBedLost(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.bedsLost++;
        if (mode != null) record.mode(mode).bedsLost++;
        touch(record);
    }

    public void noteWin(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.winstreak = ProfileStats.nextWinstreak(record.winstreak, true);
        if (record.winstreak > record.bestWinstreak) record.bestWinstreak = record.winstreak;
        touch(record);
    }

    public void noteLoss(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.losses++;
        record.winstreak = ProfileStats.nextWinstreak(record.winstreak, false);
        if (mode != null) record.mode(mode).losses++;
        touch(record);
    }

    public boolean spendTokens(UUID uuid, int amount) {
        if (amount < 0) return false;
        Record record = ensure(uuid);
        if (record.tokens < amount) return false;
        record.tokens -= amount;
        touch(record);
        return true;
    }

    public boolean ownsCosmetic(UUID uuid, String id) {
        if (id == null || id.isEmpty()) return false;
        Record record = records.get(uuid);
        return record != null && record.cosmeticsOwned.contains(id);
    }

    public void ownCosmetic(UUID uuid, String id) {
        if (id == null || id.isEmpty()) return;
        Record record = ensure(uuid);
        record.cosmeticsOwned.add(id);
        touch(record);
    }

    public String equippedCosmetic(UUID uuid, String category) {
        if (category == null) return null;
        Record record = records.get(uuid);
        if (record == null) return null;
        String id = record.cosmeticsEquipped.get(category);
        return id == null || id.isEmpty() ? null : id;
    }

    public void equipCosmetic(UUID uuid, String category, String id) {
        if (category == null) return;
        Record record = ensure(uuid);
        if (id == null || id.isEmpty()) record.cosmeticsEquipped.remove(category);
        else record.cosmeticsEquipped.put(category, id);
        touch(record);
    }

    /** Flush to the backend if dirty. Periodic flush + plugin disable. Dirty stays set on failure (retry). */
    public void save() {
        if (!dirty) return;
        try {
            backend.saveAll(records);
            dirty = false;
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not save stats (" + backend.name() + "): " + exception.getMessage());
        }
    }

    /** Release backend resources (SQL pools). Call after the final {@link #save()} on disable. */
    public void close() {
        try { backend.close(); } catch (Throwable ignored) { }
    }

    private Record ensure(UUID uuid) {
        Record record = records.get(uuid);
        if (record == null) {
            record = new Record();
            records.put(uuid, record);
        }
        return record;
    }

    /** Stamp last-activity, flag the file dirty, and fire the change listener. */
    private void touch(Record record) {
        if (record != null) record.lastSeen = System.currentTimeMillis();
        markDirty();
    }

    private void markDirty() {
        dirty = true;
        Runnable listener = onChange;
        if (listener != null) listener.run();
    }

    private void load() {
        records.putAll(backend.loadAll());
    }

    /** Pad/truncate to exactly {@link #FAVORITE_SLOTS} entries; null/short entries become "". */
    public static String[] padFavorites(String[] raw) {
        String[] out = new String[FAVORITE_SLOTS];
        for (int i = 0; i < FAVORITE_SLOTS; i++) {
            out[i] = raw != null && i < raw.length && raw[i] != null ? raw[i] : "";
        }
        return out;
    }

    public interface StatSlice {
        int games();
        int wins();
        int losses();
        int bedsBroken();
        int bedsLost();
        int kills();
        int deaths();
        int finalKills();
        int finalDeaths();
    }

    public static final class ModeStats implements StatSlice {
        public int kills;
        public int wins;
        public int beds;
        public int games;
        public int losses;
        public int deaths;
        public int finalKills;
        public int finalDeaths;
        public int bedsLost;

        @Override public int games() { return games; }
        @Override public int wins() { return wins; }
        @Override public int losses() { return losses; }
        @Override public int bedsBroken() { return beds; }
        @Override public int bedsLost() { return bedsLost; }
        @Override public int kills() { return kills; }
        @Override public int deaths() { return deaths; }
        @Override public int finalKills() { return finalKills; }
        @Override public int finalDeaths() { return finalDeaths; }

        public boolean isZero() {
            return kills == 0 && wins == 0 && beds == 0 && games == 0 && losses == 0
                && deaths == 0 && finalKills == 0 && finalDeaths == 0 && bedsLost == 0;
        }
    }

    public static final class Record implements StatSlice {
        public int tokens;
        public int xp;
        public int level = 1;
        public int kills;
        public int wins;
        public int beds;
        public int games;
        public int losses;
        public int deaths;
        public int finalKills;
        public int finalDeaths;
        public int bedsLost;
        public int winstreak;
        /** Highest winstreak ever reached; updated whenever the live winstreak exceeds it. */
        public int bestWinstreak;
        /** Epoch millis of the last stat write; 0 for legacy files. Powers future weekly/monthly windows. */
        public long lastSeen;
        public final ModeStats solo = new ModeStats();
        public final ModeStats doubles = new ModeStats();
        public final ModeStats trios = new ModeStats();
        public final ModeStats quads = new ModeStats();
        /** Null = never customized (use defaults). Length {@link #FAVORITE_SLOTS}; "" = empty slot. */
        public String[] favorites;
        public final Set<String> cosmeticsOwned = new LinkedHashSet<String>();
        public final Map<String, String> cosmeticsEquipped = new LinkedHashMap<String, String>();

        public ModeStats mode(GameType type) {
            if (type == null) return solo;
            switch (type) {
                case DOUBLES: return doubles;
                case TRIOS: return trios;
                case QUADS: return quads;
                default: return solo;
            }
        }

        @Override public int games() { return games; }
        @Override public int wins() { return wins; }
        @Override public int losses() { return losses; }
        @Override public int bedsBroken() { return beds; }
        @Override public int bedsLost() { return bedsLost; }
        @Override public int kills() { return kills; }
        @Override public int deaths() { return deaths; }
        @Override public int finalKills() { return finalKills; }
        @Override public int finalDeaths() { return finalDeaths; }
    }
}
