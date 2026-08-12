package dev.iyanel.bedlamcore.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ArenaSettings {
    private final String id;
    private GameType gameType;
    private String worldName;
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
        if (spectator == null) missing.add("spectator spawn");
        int completeTeams = configuredTeams().size();
        if (completeTeams < 2) {
            missing.add("at least two complete teams (spawn, bed, forge, item shop, upgrade shop)");
            for (TeamColor color : TeamColor.values()) addTeamMissing(missing, color, team(color));
        }
        if (diamondGenerators.isEmpty()) missing.add("at least one diamond generator");
        if (emeraldGenerators.isEmpty()) missing.add("at least one emerald generator");
        return missing;
    }

    public ArenaSettings copy() {
        ArenaSettings copy = new ArenaSettings(id, gameType, worldName);
        copy.spectator(spectator);
        for (TeamColor color : TeamColor.values()) copy.team(color).copyFrom(team(color));
        for (Location location : diamondGenerators) copy.diamondGenerators.add(clone(location));
        for (Location location : emeraldGenerators) copy.emeraldGenerators.add(clone(location));
        return copy;
    }

    private static void addTeamMissing(List<String> missing, TeamColor color, TeamSettings team) {
        List<String> fields = new ArrayList<String>();
        if (team.spawn == null) fields.add("spawn");
        if (team.bed == null) fields.add("bed");
        if (team.forge == null) fields.add("forge");
        if (team.itemShop == null) fields.add("item shop");
        if (team.upgradeShop == null) fields.add("upgrade shop");
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

        public boolean complete() { return spawn != null && bed != null && forge != null && itemShop != null && upgradeShop != null; }

        private void copyFrom(TeamSettings source) {
            spawn(source.spawn); bed(source.bed); forge(source.forge); itemShop(source.itemShop); upgradeShop(source.upgradeShop);
        }
    }
}
