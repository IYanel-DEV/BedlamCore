package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ArenaRepository {
    private final JavaPlugin plugin;
    private final File file;

    public ArenaRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public ArenaSettings load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ArenaSettings settings = new ArenaSettings("arena");
        settings.lobby(Locations.decode(yaml.getString("arena.lobby")));
        settings.spectator(Locations.decode(yaml.getString("arena.spectator")));
        for (TeamColor color : TeamColor.values()) {
            String path = "arena.teams." + color.name().toLowerCase() + ".";
            ArenaSettings.TeamSettings team = settings.team(color);
            team.spawn(Locations.decode(yaml.getString(path + "spawn")));
            team.bed(Locations.decode(yaml.getString(path + "bed")));
            team.forge(Locations.decode(yaml.getString(path + "forge")));
            team.itemShop(Locations.decode(yaml.getString(path + "item-shop")));
            team.upgradeShop(Locations.decode(yaml.getString(path + "upgrade-shop")));
        }
        readLocations(yaml.getStringList("arena.generators.diamond"), settings.diamondGenerators());
        readLocations(yaml.getStringList("arena.generators.emerald"), settings.emeraldGenerators());
        return settings;
    }

    public void save(ArenaSettings settings) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("arena.lobby", Locations.encode(settings.lobby()));
        yaml.set("arena.spectator", Locations.encode(settings.spectator()));
        for (TeamColor color : TeamColor.values()) {
            String path = "arena.teams." + color.name().toLowerCase() + ".";
            ArenaSettings.TeamSettings team = settings.team(color);
            yaml.set(path + "spawn", Locations.encode(team.spawn()));
            yaml.set(path + "bed", Locations.encode(team.bed()));
            yaml.set(path + "forge", Locations.encode(team.forge()));
            yaml.set(path + "item-shop", Locations.encode(team.itemShop()));
            yaml.set(path + "upgrade-shop", Locations.encode(team.upgradeShop()));
        }
        yaml.set("arena.generators.diamond", encode(settings.diamondGenerators()));
        yaml.set("arena.generators.emerald", encode(settings.emeraldGenerators()));
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create " + plugin.getDataFolder());
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save arenas.yml: " + exception.getMessage());
        }
    }

    private static void readLocations(List<String> values, List<Location> output) {
        for (String value : values) {
            Location location = Locations.decode(value);
            if (location != null) output.add(location);
        }
    }

    private static java.util.List<String> encode(List<Location> locations) {
        java.util.List<String> values = new java.util.ArrayList<String>();
        for (Location location : locations) values.add(Locations.encode(location));
        return values;
    }
}
