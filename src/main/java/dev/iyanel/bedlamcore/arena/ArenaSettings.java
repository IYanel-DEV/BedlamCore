package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class ArenaSettings {
    public static final int DEFAULT_BUILD_BORDER_RADIUS = 64;

    private final String id;
    private GameType gameType;
    private String worldName;
    private Location waitingSpawn;
    private Location spectator;
    private int buildBorderRadius = DEFAULT_BUILD_BORDER_RADIUS;
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
    public int buildBorderRadius() { return buildBorderRadius; }
    public void buildBorderRadius(int value) { buildBorderRadius = Math.max(1, value); }
    /** Border needs waiting spawn + spectator; radius always defaults. */
    public boolean hasBuildBorder() { return waitingSpawn != null && spectator != null; }

    /** Bukkit WorldBorder.setSize is diameter; GUI/mayPlace use radius (center→edge). */
    public static int worldBorderDiameter(int radius) {
        return Math.max(1, radius) * 2;
    }

    /**
     * Inclusive XZ square of side 2*radius around midpoint(waiting, spectator) XZ;
     * Y spans waiting and every configured bed (each gets y-64 .. y+128), so a high
     * waiting lobby cannot exclude island beds (e.g. wait Y=203, bed Y=100).
     * Null if either spawn unset. Setup stores waiting as a point (not pos1/pos2).
     */
    public int[] buildBorderBounds() {
        if (!hasBuildBorder()) return null;
        int[] box = borderBox(
            waitingSpawn.getBlockX(), waitingSpawn.getBlockY(), waitingSpawn.getBlockZ(),
            spectator.getBlockX(), spectator.getBlockY(), spectator.getBlockZ(),
            buildBorderRadius);
        for (TeamColor color : TeamColor.values()) {
            Location bed = team(color).bed();
            if (bed == null) continue;
            box[1] = Math.min(box[1], bed.getBlockY() - 64);
            box[4] = Math.max(box[4], bed.getBlockY() + 128);
        }
        return box;
    }

    /**
     * Pure AABB math. Points a=waiting, b=spectator: XZ midpoint, Y from {@code ay} only.
     * {@code by} is unused (kept so call sites stay stable).
     * Edges match WorldBorder of diameter {@link #worldBorderDiameter(int)} at the same center.
     */
    public static int[] borderBox(int ax, int ay, int az, int bx, int by, int bz, int radius) {
        int r = Math.max(1, radius);
        int cx = (ax + bx) / 2;
        int cz = (az + bz) / 2;
        int minY = ay - 64;
        int maxY = ay + 128;
        return new int[] {cx - r, minY, cz - r, cx + r, maxY, cz + r};
    }

    /** True if block coords sit inside {@link #buildBorderBounds()} (inclusive). */
    public boolean insideBuildBorder(int x, int y, int z) {
        int[] box = buildBorderBounds();
        if (box == null) return true;
        return x >= box[0] && y >= box[1] && z >= box[2] && x <= box[3] && y <= box[4] && z <= box[5];
    }

    /** Soft warn when a configured bed is outside the mayPlace AABB (setup/load check). */
    public void warnBedsOutsideBorder(Logger log) {
        if (log == null || !hasBuildBorder()) return;
        int[] box = buildBorderBounds();
        if (box == null) return;
        for (TeamColor color : TeamColor.values()) {
            Location bed = team(color).bed();
            if (bed == null) continue;
            int x = bed.getBlockX(), y = bed.getBlockY(), z = bed.getBlockZ();
            if (insideBuildBorder(x, y, z)) continue;
            log.warning(id + ": " + color.displayName() + " bed at " + x + "," + y + "," + z
                + " is outside build border AABB [" + box[0] + ".." + box[3] + ", "
                + box[1] + ".." + box[4] + ", " + box[2] + ".." + box[5]
                + "] (radius " + buildBorderRadius + ") — players cannot build at that island.");
        }
    }

    /** Setup preview only: align vanilla WorldBorder to mayPlace XZ (damage off). */
    public void applyWorldBorder(World world) {
        if (world == null || !hasBuildBorder()) return;
        int[] box = buildBorderBounds();
        if (box == null) return;
        int cx = (box[0] + box[3]) / 2;
        int cz = (box[2] + box[5]) / 2;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(cx + 0.5, cz + 0.5);
        border.setSize(worldBorderDiameter(buildBorderRadius));
        border.setWarningDistance(0);
        border.setDamageAmount(0.0);
    }

    /** Match/waiting: hide WorldBorder (mayPlace AABB still enforces build edge). */
    public static void hideWorldBorder(World world) {
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setSize(6.0E7);
        border.setWarningDistance(0);
        border.setDamageAmount(0.0);
    }

    /** Lowest configured bed Y, else waiting spawn Y (void plane reference). */
    public double voidReferenceY() {
        Double min = null;
        for (TeamColor color : TeamColor.values()) {
            Location bed = team(color).bed();
            if (bed == null) continue;
            min = min == null ? bed.getY() : Math.min(min, bed.getY());
        }
        if (min != null) return min;
        return waitingSpawn != null ? waitingSpawn.getY() : 0;
    }

    public TeamSettings team(TeamColor color) { return teams.get(color); }
    public List<Location> diamondGenerators() { return diamondGenerators; }
    public List<Location> emeraldGenerators() { return emeraldGenerators; }

    /** All configured points for setup-mode hologram markers (draft only). */
    public List<LabeledPoint> setupMarkerPoints() {
        List<LabeledPoint> points = new ArrayList<LabeledPoint>();
        if (waitingSpawn != null) points.add(new LabeledPoint(ChatColor.WHITE + "Waiting Spawn", waitingSpawn));
        if (spectator != null) points.add(new LabeledPoint(ChatColor.GRAY + "Spectator", spectator));
        for (Location location : diamondGenerators) {
            if (location != null) points.add(new LabeledPoint(ChatColor.AQUA + "Diamond Gen", location));
        }
        for (Location location : emeraldGenerators) {
            if (location != null) points.add(new LabeledPoint(ChatColor.GREEN + "Emerald Gen", location));
        }
        for (TeamColor color : TeamColor.values()) {
            TeamSettings t = team(color);
            String prefix = color.chatColor() + color.displayName() + " ";
            if (t.spawn != null) points.add(new LabeledPoint(prefix + "Spawn", t.spawn));
            if (t.bed != null) points.add(new LabeledPoint(prefix + "Bed", t.bed));
            if (t.forge != null) points.add(new LabeledPoint(prefix + "Forge", t.forge));
            if (t.itemShop != null) points.add(new LabeledPoint(prefix + "Item Shop", t.itemShop));
            if (t.upgradeShop != null) points.add(new LabeledPoint(prefix + "Upgrade Shop", t.upgradeShop));
            if (t.teamChest != null) points.add(new LabeledPoint(prefix + "Team Chest", t.teamChest));
            if (t.enderChest != null) points.add(new LabeledPoint(prefix + "Ender Chest", t.enderChest));
        }
        return points;
    }

    /**
     * Clear the nearest draft point within ~2 blocks of hit. Returns chat label or null.
     * Example: "diamond generator", "Red bed".
     */
    public String removeNear(Location hit) {
        if (hit == null || hit.getWorld() == null) return null;
        int best = Integer.MAX_VALUE;
        String kind = null;
        TeamColor team = null;
        int listIndex = -1;

        int d = nearDist(hit, waitingSpawn);
        if (d < best) { best = d; kind = "waiting"; }
        d = nearDist(hit, spectator);
        if (d < best) { best = d; kind = "spectator"; }
        for (int i = 0; i < diamondGenerators.size(); i++) {
            d = nearDist(hit, diamondGenerators.get(i));
            if (d < best) { best = d; kind = "diamond"; listIndex = i; }
        }
        for (int i = 0; i < emeraldGenerators.size(); i++) {
            d = nearDist(hit, emeraldGenerators.get(i));
            if (d < best) { best = d; kind = "emerald"; listIndex = i; }
        }
        for (TeamColor color : TeamColor.values()) {
            TeamSettings t = team(color);
            String[] kinds = {"spawn", "bed", "forge", "item", "upgrade", "chest", "ender"};
            Location[] locs = {t.spawn, t.bed, t.forge, t.itemShop, t.upgradeShop, t.teamChest, t.enderChest};
            for (int i = 0; i < locs.length; i++) {
                d = nearDist(hit, locs[i]);
                if (d < best) { best = d; kind = kinds[i]; team = color; listIndex = i; }
            }
        }
        if (kind == null || best > 4) return null;
        if (kind.equals("waiting")) { waitingSpawn = null; return "waiting spawn"; }
        if (kind.equals("spectator")) { spectator = null; return "spectator spawn"; }
        if (kind.equals("diamond")) { diamondGenerators.remove(listIndex); return "diamond generator"; }
        if (kind.equals("emerald")) { emeraldGenerators.remove(listIndex); return "emerald generator"; }
        TeamSettings t = team(team);
        String name = team.displayName();
        if (kind.equals("spawn")) { t.spawn = null; return name + " spawn"; }
        if (kind.equals("bed")) { t.bed = null; return name + " bed"; }
        if (kind.equals("forge")) { t.forge = null; return name + " forge"; }
        if (kind.equals("item")) { t.itemShop = null; return name + " item shop"; }
        if (kind.equals("upgrade")) { t.upgradeShop = null; return name + " upgrade shop"; }
        if (kind.equals("chest")) { t.teamChest = null; return name + " team chest"; }
        t.enderChest = null;
        return name + " ender chest";
    }

    /** Squared block distance, or MAX if not same world / too far / null. */
    private static int nearDist(Location hit, Location point) {
        if (point == null || !sameSetupWorld(hit, point)) return Integer.MAX_VALUE;
        int dx = hit.getBlockX() - point.getBlockX();
        int dy = hit.getBlockY() - point.getBlockY();
        int dz = hit.getBlockZ() - point.getBlockZ();
        int dist = dx * dx + dy * dy + dz * dz;
        return dist <= 4 ? dist : Integer.MAX_VALUE;
    }

    private static boolean sameSetupWorld(Location hit, Location point) {
        if (hit.getWorld() == null || point.getWorld() == null) return false;
        return hit.getWorld().equals(point.getWorld())
            || hit.getWorld().getName().equals(point.getWorld().getName());
    }

    public static final class LabeledPoint {
        public final String label;
        public final Location location;

        public LabeledPoint(String label, Location location) {
            this.label = label;
            this.location = location;
        }
    }

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
        // build border = midpoint(waiting, spectator) ± radius; covered by the two spawns above
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
        copy.buildBorderRadius(buildBorderRadius);
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
            return nextMissing() == null;
        }

        /** First unset team field in Team Setup order, or null when complete. */
        public String nextMissing() {
            return GameRules.teamSetupNextMissing(
                spawn != null, bed != null, forge != null, itemShop != null,
                upgradeShop != null, teamChest != null, enderChest != null);
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
