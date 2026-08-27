package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.lobby.LobbySettings;
import dev.iyanel.bedlamcore.util.Locations;
import dev.iyanel.bedlamcore.util.AtomicFiles;
import dev.iyanel.bedlamcore.world.GameWorlds;
import dev.iyanel.bedlamcore.world.MapTemplatesCheck;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            lobby.npc(type).baby(yaml.getBoolean(path + "baby", false));
            lobby.npc(type).human(yaml.getBoolean(path + "human", true));
            lobby.npc(type).skin(yaml.getString(path + "skin"));
            lobby.npc(type).lookAtPlayers(yaml.getBoolean(path + "look-at-players", false));
            lobby.npc(type).cape(yaml.getBoolean(path + "cape", false));
        }
        lobby.cosmeticsNpc(decode(yaml.getString("lobby.cosmetics.location")));
        lobby.profileNpc(decode(yaml.getString("lobby.profile.location")));
        lobby.cosmeticsSkin(yaml.getString("lobby.cosmetics.skin"));
        lobby.profileSkin(yaml.getString("lobby.profile.skin"));
        lobby.cosmeticsCape(yaml.getBoolean("lobby.cosmetics.cape", false));
        lobby.profileCape(yaml.getBoolean("lobby.profile.cape", false));
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

    /** Load a bundled template {@code arena.yml} (root key {@code arena:}), remap world, force mode. */
    public ArenaSettings readTemplatePreset(File file, String id, GameType type, String worldName) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        try {
            YamlConfiguration peek = new YamlConfiguration();
            peek.loadFromString(text);
            String fromWorld = peek.getString("arena.world", "bedwars-e2560");
            text = MapTemplatesCheck.remapWorld(text, fromWorld, worldName);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            ArenaSettings settings = readArena(yaml, "arena", id);
            settings.gameType(type);
            settings.worldName(worldName);
            return settings;
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Invalid template arena.yml: " + exception.getMessage(), exception);
        }
    }

    public void save(LobbySettings lobby, Collection<ArenaSettings> arenas) {
        YamlConfiguration existing = loadYaml(); // last good on disk — used to preserve arenas whose world is unloaded
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("lobby.spawn", Locations.encode(lobby.spawn()));
        for (GameType type : GameType.values()) {
            String path = "lobby.npcs." + type.name().toLowerCase() + ".";
            yaml.set(path + "location", Locations.encode(lobby.npc(type).location()));
            yaml.set(path + "entity", lobby.npc(type).entityType().name());
            yaml.set(path + "baby", lobby.npc(type).baby());
            yaml.set(path + "human", lobby.npc(type).human());
            yaml.set(path + "skin", lobby.npc(type).skin());
            yaml.set(path + "look-at-players", lobby.npc(type).lookAtPlayers());
            yaml.set(path + "cape", lobby.npc(type).cape());
        }
        yaml.set("lobby.cosmetics.location", Locations.encode(lobby.cosmeticsNpc()));
        yaml.set("lobby.profile.location", Locations.encode(lobby.profileNpc()));
        yaml.set("lobby.cosmetics.skin", lobby.cosmeticsSkin());
        yaml.set("lobby.profile.skin", lobby.profileSkin());
        yaml.set("lobby.cosmetics.cape", lobby.cosmeticsCape());
        yaml.set("lobby.profile.cape", lobby.profileCape());
        for (ArenaSettings arena : arenas) {
            String root = "arenas." + arena.id();
            org.bukkit.World world = Bukkit.getWorld(arena.worldName());
            if (world != null) {
                // Unload+reload leaves stale/null World refs on the Location fields; reattach so encode() keeps them.
                arena.reattach(world);
                writeArena(yaml, arena);
            } else if (existing.isConfigurationSection(root)) {
                // World not loaded: encoding stale Locations would write nulls and wipe a configured arena on
                // restart. Preserve the last good section from disk instead of overwriting it.
                copySection(existing.getConfigurationSection(root), yaml.createSection(root));
            } else {
                writeArena(yaml, arena);
            }
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) throw new IOException("Could not create " + plugin.getDataFolder());
            AtomicFiles.writeUtf8(file.toPath(), yaml.saveToString());
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save arenas.yml: " + exception.getMessage());
        }
    }

    /** Deep-copy one arena's saved YAML forward unchanged (used when its world is unloaded at save time). */
    private static void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            Object value = from.get(key);
            if (value instanceof ConfigurationSection) copySection((ConfigurationSection) value, to.createSection(key));
            else to.set(key, value);
        }
    }

    private ArenaSettings readArena(YamlConfiguration yaml, String root, String id) {
        String world = yaml.getString(root + ".world");
        loadExistingWorld(world);
        Location spectator = decode(yaml.getString(root + ".spectator"));
        if (world == null && spectator != null) world = spectator.getWorld().getName();
        ArenaSettings settings = new ArenaSettings(id, GameType.parse(yaml.getString(root + ".mode")), world);
        settings.waitingSpawn(decode(yaml.getString(root + ".waiting-spawn")));
        settings.spectator(spectator);
        settings.buildBorderRadius(yaml.getInt(root + ".build-border-radius", ArenaSettings.DEFAULT_BUILD_BORDER_RADIUS));
        for (TeamColor color : TeamColor.values()) {
            String path = root + ".teams." + color.name().toLowerCase() + ".";
            ArenaSettings.TeamSettings team = settings.team(color);
            team.spawn(decode(yaml.getString(path + "spawn")));
            team.bed(decode(yaml.getString(path + "bed")));
            team.forge(decode(yaml.getString(path + "forge")));
            team.itemShop(decode(yaml.getString(path + "item-shop")));
            team.upgradeShop(decode(yaml.getString(path + "upgrade-shop")));
            team.teamChest(decode(yaml.getString(path + "team-chest")));
            team.enderChest(decode(yaml.getString(path + "ender-chest")));
        }
        readLocations(yaml.getStringList(root + ".generators.diamond"), settings.diamondGenerators());
        readLocations(yaml.getStringList(root + ".generators.emerald"), settings.emeraldGenerators());
        return settings;
    }

    private void writeArena(YamlConfiguration yaml, ArenaSettings settings) {
        String root = "arenas." + settings.id();
        yaml.set(root + ".mode", settings.gameType().name());
        yaml.set(root + ".world", settings.worldName());
        yaml.set(root + ".waiting-spawn", Locations.encode(settings.waitingSpawn()));
        yaml.set(root + ".spectator", Locations.encode(settings.spectator()));
        yaml.set(root + ".build-border-radius", settings.buildBorderRadius());
        yaml.set(root + ".build-border-a", null);
        yaml.set(root + ".build-border-b", null);
        for (TeamColor color : TeamColor.values()) {
            String path = root + ".teams." + color.name().toLowerCase() + ".";
            ArenaSettings.TeamSettings team = settings.team(color);
            yaml.set(path + "spawn", Locations.encode(team.spawn()));
            yaml.set(path + "bed", Locations.encode(team.bed()));
            yaml.set(path + "forge", Locations.encode(team.forge()));
            yaml.set(path + "item-shop", Locations.encode(team.itemShop()));
            yaml.set(path + "upgrade-shop", Locations.encode(team.upgradeShop()));
            yaml.set(path + "team-chest", Locations.encode(team.teamChest()));
            yaml.set(path + "ender-chest", Locations.encode(team.enderChest()));
        }
        yaml.set(root + ".generators.diamond", encode(settings.diamondGenerators()));
        yaml.set(root + ".generators.emerald", encode(settings.emeraldGenerators()));
    }

    private YamlConfiguration loadYaml() {
        if (!file.exists()) return new YamlConfiguration();
        String text = readSanitized(file);
        boolean corrupt = text == null || nullBytes(file);
        if (text != null && !corrupt) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(text);
                return yaml;
            } catch (InvalidConfigurationException exception) {
                corrupt = true;
            }
        }

        // arenas.yml is unusable: preserve evidence, then recover from the last good backup.
        quarantine(file);
        File backup = new File(file.getParentFile(), file.getName() + ".bak");
        if (backup.exists()) {
            String backupText = readSanitized(backup);
            if (backupText != null) {
                try {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.loadFromString(backupText);
                    plugin.getLogger().warning("arenas.yml was corrupt; recovered from " + backup.getName() + ".");
                    try { AtomicFiles.writeUtf8(file.toPath(), backupText); }
                    catch (IOException ignored) { /* recovery copy is best-effort */ }
                    return yaml;
                } catch (InvalidConfigurationException ignored) { /* backup also unusable */ }
            }
        }
        if (text != null && !text.trim().isEmpty()) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(text); // sanitized parse (e.g. only trailing nulls) as a last resort
                plugin.getLogger().warning("arenas.yml was corrupt; recovered by discarding invalid bytes.");
                return yaml;
            } catch (InvalidConfigurationException ignored) { /* fall through to empty */ }
        }
        plugin.getLogger().severe("arenas.yml was corrupt and no usable backup exists; starting with an empty configuration.");
        return new YamlConfiguration();
    }

    /** Read a config file as UTF-8, dropping a leading BOM and any embedded NUL bytes; {@code null} on I/O failure. */
    private String readSanitized(File source) {
        try {
            String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
            if (text.indexOf('\0') >= 0) text = text.replace("\0", "");
            return text;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read " + source.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    private boolean nullBytes(File source) {
        try {
            for (byte b : Files.readAllBytes(source.toPath())) if (b == 0) return true;
        } catch (IOException ignored) { /* treat unreadable as corrupt via caller */ }
        return false;
    }

    private void quarantine(File source) {
        File dead = new File(source.getParentFile(), source.getName() + ".corrupt-" + System.currentTimeMillis());
        try { Files.copy(source.toPath(), dead.toPath()); }
        catch (IOException exception) { plugin.getLogger().warning("Could not quarantine " + source.getName() + ": " + exception.getMessage()); }
    }

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
            boolean classic = folder.isDirectory()
                && folder.getCanonicalFile().getParentFile().equals(container.getCanonicalFile());
            // Paper 26+ keeps the live world at world/dimensions/minecraft/<name> and retires the classic
            // top-level folder after a match+save. Without this, the world never loads on restart and every
            // team Location decodes to null → "setup missing". WorldCreator resolves the managed dimension.
            boolean managed = GameWorlds.managedDimensionExists(name);
            if (!classic && !managed) return;
            if (classic) GameWorlds.prepareCopiedWorldFolder(folder);
            WorldCreator creator = new WorldCreator(name);
            if (GameWorlds.managedName(name)) creator.generator(GameWorlds.oneBlockGenerator());
            creator.createWorld();
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
