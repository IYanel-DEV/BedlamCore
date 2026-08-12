package dev.iyanel.bedlamcore.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ArenaSettings {
    private final String id;
    private Location lobby;
    private Location spectator;
    private final Map<TeamColor, TeamSettings> teams = new EnumMap<TeamColor, TeamSettings>(TeamColor.class);
    private final List<Location> diamondGenerators = new ArrayList<Location>();
    private final List<Location> emeraldGenerators = new ArrayList<Location>();

    public ArenaSettings(String id) {
        this.id = id;
        for (TeamColor color : TeamColor.values()) {
            teams.put(color, new TeamSettings());
        }
    }

    public String id() { return id; }
    public Location lobby() { return lobby; }
    public void lobby(Location lobby) { this.lobby = clone(lobby); }
    public Location spectator() { return spectator; }
    public void spectator(Location spectator) { this.spectator = clone(spectator); }
    public TeamSettings team(TeamColor color) { return teams.get(color); }
    public List<Location> diamondGenerators() { return diamondGenerators; }
    public List<Location> emeraldGenerators() { return emeraldGenerators; }

    public List<TeamColor> configuredTeams() {
        List<TeamColor> result = new ArrayList<TeamColor>();
        for (Map.Entry<TeamColor, TeamSettings> entry : teams.entrySet()) {
            if (entry.getValue().complete()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public List<String> validate() {
        List<String> missing = new ArrayList<String>();
        if (lobby == null) missing.add("lobby");
        if (spectator == null) missing.add("spectator spawn");
        if (configuredTeams().size() < 2) missing.add("at least two complete teams");
        return missing;
    }

    private static Location clone(Location location) {
        return location == null ? null : location.clone();
    }

    public static final class TeamSettings {
        private Location spawn;
        private Location bed;
        private Location forge;
        private Location itemShop;
        private Location upgradeShop;

        public Location spawn() { return spawn; }
        public void spawn(Location value) { spawn = ArenaSettings.clone(value); }
        public Location bed() { return bed; }
        public void bed(Location value) { bed = ArenaSettings.clone(value); }
        public Location forge() { return forge; }
        public void forge(Location value) { forge = ArenaSettings.clone(value); }
        public Location itemShop() { return itemShop; }
        public void itemShop(Location value) { itemShop = ArenaSettings.clone(value); }
        public Location upgradeShop() { return upgradeShop; }
        public void upgradeShop(Location value) { upgradeShop = ArenaSettings.clone(value); }

        public boolean complete() {
            return spawn != null && bed != null && forge != null && itemShop != null && upgradeShop != null;
        }
    }
}
