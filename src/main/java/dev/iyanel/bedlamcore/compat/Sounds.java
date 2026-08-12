package dev.iyanel.bedlamcore.compat;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Resolve renamed sounds across 1.8–modern without NMS. */
public final class Sounds {
    private Sounds() {
    }

    public static void play(Player player, String... names) {
        Sound sound = resolve(names);
        if (sound == null || player == null) return;
        player.playSound(player.getLocation(), sound, 1F, 1F);
    }

    public static void playAt(Location location, String... names) {
        Sound sound = resolve(names);
        if (sound == null || location == null || location.getWorld() == null) return;
        location.getWorld().playSound(location, sound, 1F, 1F);
    }

    public static void playAll(Iterable<? extends Player> players, String... names) {
        Sound sound = resolve(names);
        if (sound == null) return;
        for (Player player : players) {
            if (player != null) player.playSound(player.getLocation(), sound, 1F, 1F);
        }
    }

    public static Sound resolve(String... names) {
        for (String name : names) {
            try {
                return Sound.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Sound sound : Sound.values()) {
            String n = sound.name();
            for (String name : names) {
                if (n.equalsIgnoreCase(name) || n.endsWith("_" + name) || n.contains(name)) return sound;
            }
        }
        return null;
    }

    public static void bedDestroyed(Player player) {
        play(player, "ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");
    }

    public static void kill(Player player) {
        play(player, "ENTITY_EXPERIENCE_ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_TOUCH", "ORB_PICKUP");
    }

    public static void death(Player player) {
        play(player, "ENTITY_WITHER_HURT", "WITHER_HURT");
    }

    public static void levelUp(Player player) {
        play(player, "ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
    }
}
