package dev.iyanel.bedlamcore.util;

import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Locations {
    private Locations() {
    }

    public static String encode(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + ","
            + location.getZ() + "," + location.getYaw() + "," + location.getPitch();
    }

    public static Location decode(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean sameBlock(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld())
            && first.getBlockX() == second.getBlockX() && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    public static boolean near(Location first, Location second, double distance) {
        return first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld())
            && first.distanceSquared(second) <= distance * distance;
    }

    public static String blockKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    /**
     * Exact XZ center of the configured block. Always floors via block coords then +0.5
     * (never double-offsets an already-centered Location). Y sits on top of solid gens
     * so items are not physics-ejected sideways from inside the block.
     */
    public static Location genDropPoint(Location location) {
        if (location == null || location.getWorld() == null) return location;
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        double y = location.getBlock().getType().isSolid() ? by + 1.05 : by + 0.25;
        return new Location(location.getWorld(), bx + 0.5, y, bz + 0.5);
    }

    /** Forge ground fallback: same XZ center, lower Y near block top (Hypixel-like). */
    public static Location forgeDropPoint(Location location) {
        if (location == null || location.getWorld() == null) return location;
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        double y = location.getBlock().getType().isSolid()
            ? by + 1.0 + GameRules.FORGE_DROP_Y
            : by + GameRules.FORGE_DROP_Y;
        return new Location(location.getWorld(), bx + 0.5, y, bz + 0.5);
    }
}
