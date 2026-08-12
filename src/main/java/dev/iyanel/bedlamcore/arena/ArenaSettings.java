package dev.iyanel.bedlamcore.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ArenaSettings {
    private final String id;
    private GameType gameType;
    private String worldName;
    private Location waitingSpawn;
    private Location spectator;
    private final Map<TeamColor, TeamSettings> teams = new EnumMap<TeamColor, TeamSettings>(TeamColor.class);
    private final List<Location> diamondGenerators = new ArrayList<Location>();
    private final List<Location> emeraldGenerators = new ArrayList<Location>();

    public ArenaSettings(String id, GameType gameType, String worldName) {
        this.id = id;
        this.gameType = gameType;
        this.worldName = worldName;
        for (TeamColor color : TeamColor.values()) teams.put(color, new TeamSettings());
    }

    public String id() { return id; }
    public GameType gameType() { return gameType; }
    public void gameType(GameType value) { gameType = value; }
    public String worldName() { return worldName; }
    public void worldName(String value) { worldName = value; }
    public Location waitingSpawn() { return clone(waitingSpawn); }
    public void waitingSpawn(Location value) { waitingSpawn = clone(value); }
    public Location spectator() { return clone(spectator); }
    public void spectator(Location value) { spectator = clone(value); }
    public TeamSettings team(TeamColor color) { return teams.get(color); }
    public List<Location> diamondGenerators() { return diamondGenerators; }
    public List<Location> emeraldGenerators() { return emeraldGenerators; }

    public List<TeamColor> configuredTeams() {
        List<TeamColor> result = new ArrayList<TeamColor>();
        for (Map.Entry<TeamColor, TeamSettings> entry : teams.entrySet()) {
            if (entry.getValue().complete()) result.add(entry.getKey());
        }
        return result;
    }

    public int maximumPlayers() { return configuredTeams().size() * gameType.teamSize(); }

    public List<String> validate() {
        List<String> missing = new ArrayList<String>();
        if (worldName == null || worldName.isEmpty()) missing.add("game world");
        if (waitingSpawn == null) missing.add("waiting spawn");
        if (spectator == null) missing.add("spectator spawn");
        int completeTeams = configuredTeams().size();
        if (completeTeams < 2) {
            missing.add("at least two complete teams (spawn, bed, forge, item shop, upgrade shop, team chest, ender chest)");
            for (TeamColor color : TeamColor.values()) addTeamMissing(missing, color, team(color));
        }
        if (diamondGenerators.isEmpty()) missing.add("at least one diamond generator");
        if (emeraldGenerators.isEmpty()) missing.add("at least one emerald generator");
        return missing;
    }

    public ArenaSettings copy() {
        ArenaSettings copy = new ArenaSettings(id, gameType, worldName);
        copy.waitingSpawn(waitingSpawn);
        copy.spectator(spectator);
        for (TeamColor color : TeamColor.values()) copy.team(color).copyFrom(team(color));
        for (Location location : diamondGenerators) copy.diamondGenerators.add(clone(location));
        for (Location location : emeraldGenerators) copy.emeraldGenerators.add(clone(location));
        return copy;
    }

    /** After unload+reload, Location still holds the old World instance; rebind to the live one. */
    public void reattach(World world) {
        if (world == null) return;
        waitingSpawn = rebind(waitingSpawn, world);
        spectator = rebind(spectator, world);
        for (TeamColor color : TeamColor.values()) team(color).reattach(world);
        rebindAll(diamondGenerators, world);
        rebindAll(emeraldGenerators, world);
    }

    private static void rebindAll(List<Location> locations, World world) {
        for (int i = 0; i < locations.size(); i++) locations.set(i, rebind(locations.get(i), world));
    }

    private static Location rebind(Location location, World world) {
        if (location == null) return null;
        Location copy = location.clone();
        copy.setWorld(world);
        return copy;
    }

    private static void addTeamMissing(List<String> missing, TeamColor color, TeamSettings team) {
        List<String> fields = new ArrayList<String>();
        if (team.spawn == null) fields.add("spawn");
        if (team.bed == null) fields.add("bed");
        if (team.forge == null) fields.add("forge");
        if (team.itemShop == null) fields.add("item shop");
        if (team.upgradeShop == null) fields.add("upgrade shop");
        if (team.teamChest == null) fields.add("team chest");
        if (team.enderChest == null) fields.add("ender chest");
        if (!fields.isEmpty()) missing.add(color.displayName() + ": " + join(fields));
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private static Location clone(Location value) { return value == null ? null : value.clone(); }

    public static final class TeamSettings {
        private Location spawn;
        private Location bed;
        private Location forge;
        private Location itemShop;
        private Location upgradeShop;
        private Location teamChest;
        private Location enderChest;

        public Location spawn() { return ArenaSettings.clone(spawn); }
        public void spawn(Location value) { spawn = ArenaSettings.clone(value); }
        public Location bed() { return ArenaSettings.clone(bed); }
        public void bed(Location value) { bed = ArenaSettings.clone(value); }
        public Location forge() { return ArenaSettings.clone(forge); }
        public void forge(Location value) { forge = ArenaSettings.clone(value); }
        public Location itemShop() { return ArenaSettings.clone(itemShop); }
        public void itemShop(Location value) { itemShop = ArenaSettings.clone(value); }
        public Location upgradeShop() { return ArenaSettings.clone(upgradeShop); }
        public void upgradeShop(Location value) { upgradeShop = ArenaSettings.clone(value); }
        public Location teamChest() { return ArenaSettings.clone(teamChest); }
        public void teamChest(Location value) { teamChest = ArenaSettings.clone(value); }
        public Location enderChest() { return ArenaSettings.clone(enderChest); }
        public void enderChest(Location value) { enderChest = ArenaSettings.clone(value); }

        public boolean complete() {
            return spawn != null && bed != null && forge != null && itemShop != null && upgradeShop != null
                && teamChest != null && enderChest != null;
        }

        private void copyFrom(TeamSettings source) {
            spawn(source.spawn); bed(source.bed); forge(source.forge); itemShop(source.itemShop); upgradeShop(source.upgradeShop);
            teamChest(source.teamChest); enderChest(source.enderChest);
        }

        private void reattach(World world) {
            spawn = rebind(spawn, world);
            bed = rebind(bed, world);
            forge = rebind(forge, world);
            itemShop = rebind(itemShop, world);
            upgradeShop = rebind(upgradeShop, world);
            teamChest = rebind(teamChest, world);
            enderChest = rebind(enderChest, world);
        }
    }
}
