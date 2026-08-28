package dev.iyanel.bedlamcore.storage;

import dev.iyanel.bedlamcore.game.StatsStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Pluggable persistence for {@link StatsStore}. The in-memory record map stays the runtime source of
 * truth; only bulk load/flush routes through here. YAML is the default and byte-identical to older builds.
 */
public interface StatsBackend {
    /** Full read (called once on enable, on the main thread). Never null. */
    Map<UUID, StatsStore.Record> loadAll();

    /** Bulk upsert of the whole record map (flush + disable). May throw; caller keeps dirty on failure. */
    void saveAll(Map<UUID, StatsStore.Record> records) throws Exception;

    /** Human name for logs ("yaml"/"sqlite"/"mysql"). */
    String name();

    /** Release pooled connections etc. No-op for YAML. */
    default void close() {}

    /** Normalise the configured backend id; unknown values fall back to yaml. Pure string logic (coreCheck-safe). */
    static String resolve(String backend) {
        if (backend == null) return "yaml";
        String value = backend.trim().toLowerCase();
        if (value.equals("sqlite") || value.equals("mysql")) return value;
        return "yaml";
    }

    /** Build the configured backend; any construction failure logs and falls back to YAML (never disables the plugin). */
    static StatsBackend fromConfig(JavaPlugin plugin) {
        String choice = resolve(plugin.getConfig().getString("storage.backend", "yaml"));
        try {
            if (choice.equals("sqlite")) return new SqliteBackend(plugin);
            if (choice.equals("mysql")) return new MySqlBackend(plugin);
        } catch (Throwable failure) {
            plugin.getLogger().severe("Storage backend '" + choice + "' failed to initialise ("
                + failure.getMessage() + "); falling back to yaml.");
        }
        return new YamlBackend(plugin);
    }

    /** Convenience: read the storage.<section> block, tolerating a missing section. */
    static ConfigurationSection section(JavaPlugin plugin, String key) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("storage");
        ConfigurationSection sub = root == null ? null : root.getConfigurationSection(key);
        return sub == null ? plugin.getConfig().createSection("storage." + key) : sub;
    }
}
