package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player tokens/XP YAML. Same pattern as ArenaRepository — no database. */
public final class StatsStore {
    private static final long FLUSH_INTERVAL_TICKS = 5L * 20L; // 5s

    public static final int FAVORITE_SLOTS = GameRules.FAVORITE_SLOTS;
    /** Matches the historical Quick Buy row (blocks + sword/shears/bow). */
    public static final String[] DEFAULT_FAVORITES = GameRules.DEFAULT_FAVORITES;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Record> records = new LinkedHashMap<UUID, Record>();
    private boolean dirty;

    public StatsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { save(); }
        }, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public Record get(UUID uuid) {
        Record record = records.get(uuid);
        return record == null ? new Record() : record;
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
        dirty = true;
    }

    public void setFavorites(UUID uuid, String[] favorites) {
        ensure(uuid).favorites = padFavorites(favorites);
        dirty = true;
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
        dirty = true;
    }

    public void addFinalKill(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.finalKills++;
        if (mode != null) record.mode(mode).finalKills++;
        dirty = true;
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
        dirty = true;
    }

    public void addBedLost(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.bedsLost++;
        if (mode != null) record.mode(mode).bedsLost++;
        dirty = true;
    }

    public void noteWin(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.winstreak = ProfileStats.nextWinstreak(record.winstreak, true);
        dirty = true;
    }

    public void noteLoss(UUID uuid, GameType mode) {
        Record record = ensure(uuid);
        record.losses++;
        record.winstreak = ProfileStats.nextWinstreak(record.winstreak, false);
        if (mode != null) record.mode(mode).losses++;
        dirty = true;
    }

    public boolean spendTokens(UUID uuid, int amount) {
        if (amount < 0) return false;
        Record record = ensure(uuid);
        if (record.tokens < amount) return false;
        record.tokens -= amount;
        dirty = true;
        return true;
    }

    public boolean ownsCosmetic(UUID uuid, String id) {
        if (id == null || id.isEmpty()) return false;
        Record record = records.get(uuid);
        return record != null && record.cosmeticsOwned.contains(id);
    }

    public void ownCosmetic(UUID uuid, String id) {
        if (id == null || id.isEmpty()) return;
        ensure(uuid).cosmeticsOwned.add(id);
        dirty = true;
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
        dirty = true;
    }

    /** Disk write if dirty. Periodic flush + plugin disable. */
    public void save() {
        if (!dirty) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Record> entry : records.entrySet()) {
            String path = "players." + entry.getKey().toString();
            Record record = entry.getValue();
            yaml.set(path + ".tokens", record.tokens);
            yaml.set(path + ".xp", record.xp);
            yaml.set(path + ".level", GameRules.levelFromXp(record.xp));
            yaml.set(path + ".kills", record.kills);
            yaml.set(path + ".wins", record.wins);
            yaml.set(path + ".beds", record.beds);
            yaml.set(path + ".games", record.games);
            yaml.set(path + ".losses", record.losses);
            yaml.set(path + ".deaths", record.deaths);
            yaml.set(path + ".final-kills", record.finalKills);
            yaml.set(path + ".final-deaths", record.finalDeaths);
            yaml.set(path + ".beds-lost", record.bedsLost);
            yaml.set(path + ".winstreak", record.winstreak);
            writeMode(yaml, path + ".solo", record.solo);
            writeMode(yaml, path + ".doubles", record.doubles);
            if (record.favorites != null) {
                yaml.set(path + ".favorites", Arrays.asList(padFavorites(record.favorites)));
            }
            if (!record.cosmeticsOwned.isEmpty()) {
                yaml.set(path + ".cosmetics.owned", new ArrayList<String>(record.cosmeticsOwned));
            }
            for (Map.Entry<String, String> equipped : record.cosmeticsEquipped.entrySet()) {
                if (equipped.getValue() != null && !equipped.getValue().isEmpty()) {
                    yaml.set(path + ".cosmetics.equipped." + equipped.getKey(), equipped.getValue());
                }
            }
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create " + plugin.getDataFolder());
            }
            AtomicFiles.writeUtf8(file.toPath(), yaml.saveToString());
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save stats.yml: " + exception.getMessage());
        }
    }

    private Record ensure(UUID uuid) {
        Record record = records.get(uuid);
        if (record == null) {
            record = new Record();
            records.put(uuid, record);
        }
        return record;
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Record record = new Record();
                String path = "players." + key;
                record.tokens = yaml.getInt(path + ".tokens");
                record.xp = yaml.getInt(path + ".xp");
                record.kills = yaml.getInt(path + ".kills");
                record.wins = yaml.getInt(path + ".wins");
                record.beds = yaml.getInt(path + ".beds");
                record.games = yaml.getInt(path + ".games");
                record.losses = yaml.getInt(path + ".losses");
                record.deaths = yaml.getInt(path + ".deaths");
                record.finalKills = yaml.getInt(path + ".final-kills");
                record.finalDeaths = yaml.getInt(path + ".final-deaths");
                record.bedsLost = yaml.getInt(path + ".beds-lost");
                record.winstreak = yaml.getInt(path + ".winstreak");
                record.level = GameRules.levelFromXp(record.xp);
                readMode(yaml, path + ".solo", record.solo);
                readMode(yaml, path + ".doubles", record.doubles);
                List<?> fav = yaml.getList(path + ".favorites");
                if (fav != null) {
                    String[] slots = new String[FAVORITE_SLOTS];
                    for (int i = 0; i < FAVORITE_SLOTS; i++) {
                        Object v = i < fav.size() ? fav.get(i) : null;
                        slots[i] = v == null ? "" : String.valueOf(v);
                    }
                    record.favorites = slots;
                }
                List<?> owned = yaml.getList(path + ".cosmetics.owned");
                if (owned != null) {
                    for (Object value : owned) {
                        if (value != null) record.cosmeticsOwned.add(String.valueOf(value));
                    }
                }
                ConfigurationSection equipped = yaml.getConfigurationSection(path + ".cosmetics.equipped");
                if (equipped != null) {
                    for (String category : equipped.getKeys(false)) {
                        String cosmeticId = equipped.getString(category);
                        if (cosmeticId != null && !cosmeticId.isEmpty()) {
                            record.cosmeticsEquipped.put(category, cosmeticId);
                        }
                    }
                }
                records.put(uuid, record);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private static void writeMode(YamlConfiguration yaml, String path, ModeStats mode) {
        if (mode == null || mode.isZero()) return;
        yaml.set(path + ".kills", mode.kills);
        yaml.set(path + ".wins", mode.wins);
        yaml.set(path + ".beds", mode.beds);
        yaml.set(path + ".games", mode.games);
        yaml.set(path + ".losses", mode.losses);
        yaml.set(path + ".deaths", mode.deaths);
        yaml.set(path + ".final-kills", mode.finalKills);
        yaml.set(path + ".final-deaths", mode.finalDeaths);
        yaml.set(path + ".beds-lost", mode.bedsLost);
    }

    private static void readMode(YamlConfiguration yaml, String path, ModeStats mode) {
        mode.kills = yaml.getInt(path + ".kills");
        mode.wins = yaml.getInt(path + ".wins");
        mode.beds = yaml.getInt(path + ".beds");
        mode.games = yaml.getInt(path + ".games");
        mode.losses = yaml.getInt(path + ".losses");
        mode.deaths = yaml.getInt(path + ".deaths");
        mode.finalKills = yaml.getInt(path + ".final-kills");
        mode.finalDeaths = yaml.getInt(path + ".final-deaths");
        mode.bedsLost = yaml.getInt(path + ".beds-lost");
    }

    private static String[] padFavorites(String[] raw) {
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

        boolean isZero() {
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
        public final ModeStats solo = new ModeStats();
        public final ModeStats doubles = new ModeStats();
        /** Null = never customized (use defaults). Length {@link #FAVORITE_SLOTS}; "" = empty slot. */
        public String[] favorites;
        public final Set<String> cosmeticsOwned = new LinkedHashSet<String>();
        public final Map<String, String> cosmeticsEquipped = new LinkedHashMap<String, String>();

        public ModeStats mode(GameType type) {
            return type == GameType.DOUBLES ? doubles : solo;
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
