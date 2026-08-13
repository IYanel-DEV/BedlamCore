package dev.iyanel.bedlamcore.game;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player tokens/XP YAML. Same pattern as ArenaRepository — no database. */
public final class StatsStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Record> records = new LinkedHashMap<UUID, Record>();

    public StatsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public Record get(UUID uuid) {
        Record record = records.get(uuid);
        return record == null ? new Record() : record;
    }

    public void apply(UUID uuid, int tokens, int xp, int kills, int beds, int wins, int games) {
        Record record = records.get(uuid);
        if (record == null) {
            record = new Record();
            records.put(uuid, record);
        }
        record.tokens += tokens;
        record.xp += xp;
        record.kills += kills;
        record.beds += beds;
        record.wins += wins;
        record.games += games;
        record.level = GameRules.levelFromXp(record.xp);
        save();
    }

    public void save() {
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
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create " + plugin.getDataFolder());
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save stats.yml: " + exception.getMessage());
        }
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
                record.tokens = yaml.getInt("players." + key + ".tokens");
                record.xp = yaml.getInt("players." + key + ".xp");
                record.kills = yaml.getInt("players." + key + ".kills");
                record.wins = yaml.getInt("players." + key + ".wins");
                record.beds = yaml.getInt("players." + key + ".beds");
                record.games = yaml.getInt("players." + key + ".games");
                record.level = GameRules.levelFromXp(record.xp);
                records.put(uuid, record);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public static final class Record {
        public int tokens;
        public int xp;
        public int level = 1;
        public int kills;
        public int wins;
        public int beds;
        public int games;
    }
}
