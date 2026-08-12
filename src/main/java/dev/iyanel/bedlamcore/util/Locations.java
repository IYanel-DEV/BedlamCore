package dev.iyanel.bedlamcore.util;

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
}
