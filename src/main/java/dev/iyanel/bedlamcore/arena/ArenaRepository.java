package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.lobby.LobbySettings;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaRepository {
    private final JavaPlugin plugin;
    private final File file;

    public ArenaRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public LobbySettings loadLobby() {
        YamlConfiguration yaml = loadYaml();
        LobbySettings lobby = new LobbySettings();
        lobby.spawn(decode(yaml.getString("lobby.spawn", yaml.getString("arena.lobby"))));
        for (GameType type : GameType.values()) {
            String path = "lobby.npcs." + type.name().toLowerCase() + ".";
            lobby.npc(type).location(decode(yaml.getString(path + "location")));
            try { lobby.npc(type).entityType(EntityType.valueOf(yaml.getString(path + "entity", "VILLAGER"))); }
            catch (IllegalArgumentException ignored) { lobby.npc(type).entityType(EntityType.VILLAGER); }
        }
        return lobby;
    }

    public Map<String, ArenaSettings> loadArenas() {
        YamlConfiguration yaml = loadYaml();
        Map<String, ArenaSettings> arenas = new LinkedHashMap<String, ArenaSettings>();
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section != null) {
            for (String id : section.getKeys(false)) arenas.put(id, readArena(yaml, "arenas." + id, id));
        } else if (yaml.getConfigurationSection("arena") != null) {
            ArenaSettings migrated = readArena(yaml, "arena", "arena");
            arenas.put(migrated.id(), migrated);
        }
        return arenas;
    }

    public void save(LobbySettings lobby, Collection<ArenaSettings> arenas) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("lobby.spawn", Locations.encode(lobby.spawn()));
        for (GameType type : GameType.values()) {
            String path = "lobby.npcs." + type.name().toLowerCase() + ".";
            yaml.set(path + "location", Locations.encode(lobby.npc(type).location()));
            yaml.set(path + "entity", lobby.npc(type).entityType().name());
        }
        for (ArenaSettings arena : arenas) writeArena(yaml, arena);
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) throw new IOException("Could not create " + plugin.getDataFolder());
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save arenas.yml: " + exception.getMessage());
        }
    }

    private ArenaSettings readArena(YamlConfiguration yaml, String root, String id) {
        String world = yaml.getString(root + ".world");
        loadExistingWorld(world);
        Location spectator = decode(yaml.getString(root + ".spectator"));
        if (world == null && spectator != null) world = spectator.getWorld().getName();
        ArenaSettings settings = new ArenaSettings(id, GameType.parse(yaml.getString(root + ".mode")), world);
        settings.spectator(spectator);
        for (TeamColor color : TeamColor.values()) {
            String path = root + ".teams." + color.name().toLowerCase() + ".";
            ArenaSettings.TeamSettings team = settings.team(color);
            team.spawn(decode(yaml.getString(path + "spawn")));
            team.bed(decode(yaml.getString(path + "bed")));
            team.forge(decode(yaml.getString(path + "forge")));
            team.itemShop(decode(yaml.getString(path + "item-shop")));
            team.upgradeShop(decode(yaml.getString(path + "upgrade-shop")));
        }
        readLocations(yaml.getStringList(root + ".generators.diamond"), settings.diamondGenerators());
        readLocations(yaml.getStringList(root + ".generators.emerald"), settings.emeraldGenerators());
        return settings;
    }

    private void writeArena(YamlConfiguration yaml, ArenaSettings settings) {
        String root = "arenas." + settings.id();
        yaml.set(root + ".mode", settings.gameType().name());
        yaml.set(root + ".world", settings.worldName());
        yaml.set(root + ".spectator", Locations.encode(settings.spectator()));
        for (TeamColor color : TeamColor.values()) {
            String path = root + ".teams." + color.name().toLowerCase() + ".";
            ArenaSettings.TeamSettings team = settings.team(color);
            yaml.set(path + "spawn", Locations.encode(team.spawn()));
            yaml.set(path + "bed", Locations.encode(team.bed()));
            yaml.set(path + "forge", Locations.encode(team.forge()));
            yaml.set(path + "item-shop", Locations.encode(team.itemShop()));
            yaml.set(path + "upgrade-shop", Locations.encode(team.upgradeShop()));
        }
        yaml.set(root + ".generators.diamond", encode(settings.diamondGenerators()));
        yaml.set(root + ".generators.emerald", encode(settings.emeraldGenerators()));
    }

    private YamlConfiguration loadYaml() { return YamlConfiguration.loadConfiguration(file); }

    private void readLocations(List<String> values, List<Location> output) {
        for (String value : values) {
            Location location = decode(value);
            if (location != null) output.add(location);
        }
    }

    private Location decode(String value) {
        if (value != null) {
            int comma = value.indexOf(',');
            if (comma > 0) loadExistingWorld(value.substring(0, comma));
        }
        return Locations.decode(value);
    }

    private void loadExistingWorld(String name) {
        if (name == null || name.isEmpty() || Bukkit.getWorld(name) != null) return;
        File container = Bukkit.getWorldContainer();
        File folder = new File(container, name);
        try {
            if (!folder.isDirectory() || !folder.getCanonicalFile().getParentFile().equals(container.getCanonicalFile())) return;
            new WorldCreator(name).createWorld();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load world " + name + ": " + exception.getMessage());
        }
    }

    private static List<String> encode(List<Location> locations) {
        List<String> values = new ArrayList<String>();
        for (Location location : locations) values.add(Locations.encode(location));
        return values;
    }
}
