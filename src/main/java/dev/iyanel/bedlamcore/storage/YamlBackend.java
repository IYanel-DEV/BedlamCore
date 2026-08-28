package dev.iyanel.bedlamcore.storage;

import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.StatsStore;
import dev.iyanel.bedlamcore.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Default backend: the historical stats.yml load/save, moved verbatim so its output stays byte-identical. */
public final class YamlBackend implements StatsBackend {
    private static final int SLOTS = StatsStore.FAVORITE_SLOTS;

    private final JavaPlugin plugin;
    private final File file;

    public YamlBackend(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    @Override public String name() { return "yaml"; }

    @Override
    public Map<UUID, StatsStore.Record> loadAll() {
        Map<UUID, StatsStore.Record> records = new LinkedHashMap<UUID, StatsStore.Record>();
        if (!file.isFile()) return records;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return records;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                StatsStore.Record record = new StatsStore.Record();
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
                record.bestWinstreak = yaml.getInt(path + ".best-winstreak", record.winstreak);
                record.lastSeen = yaml.getLong(path + ".last-seen", 0L);
                record.level = GameRules.levelFromXp(record.xp);
                readMode(yaml, path + ".solo", record.solo);
                readMode(yaml, path + ".doubles", record.doubles);
                readMode(yaml, path + ".trios", record.trios);
                readMode(yaml, path + ".quads", record.quads);
                List<?> fav = yaml.getList(path + ".favorites");
                if (fav != null) {
                    String[] slots = new String[SLOTS];
                    for (int i = 0; i < SLOTS; i++) {
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
        return records;
    }

    @Override
    public void saveAll(Map<UUID, StatsStore.Record> records) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, StatsStore.Record> entry : records.entrySet()) {
            String path = "players." + entry.getKey().toString();
            StatsStore.Record record = entry.getValue();
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
            yaml.set(path + ".best-winstreak", record.bestWinstreak);
            if (record.lastSeen > 0L) yaml.set(path + ".last-seen", record.lastSeen);
            writeMode(yaml, path + ".solo", record.solo);
            writeMode(yaml, path + ".doubles", record.doubles);
            writeMode(yaml, path + ".trios", record.trios);
            writeMode(yaml, path + ".quads", record.quads);
            if (record.favorites != null) {
                yaml.set(path + ".favorites", Arrays.asList(StatsStore.padFavorites(record.favorites)));
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
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Could not create " + plugin.getDataFolder());
        }
        AtomicFiles.writeUtf8(file.toPath(), yaml.saveToString());
    }

    private static void writeMode(YamlConfiguration yaml, String path, StatsStore.ModeStats mode) {
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

    private static void readMode(YamlConfiguration yaml, String path, StatsStore.ModeStats mode) {
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
}
