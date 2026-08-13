package dev.iyanel.bedlamcore.compat;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Resolve renamed sounds across 1.8–modern without NMS. */
public final class Sounds {
    private Sounds() {
    }

    public static void play(Player player, String... names) {
        play(player, 1F, 1F, names);
    }

    public static void play(Player player, float volume, float pitch, String... names) {
        Sound sound = resolve(names);
        if (sound == null || player == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
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

    public static void purchase(Player player) {
        play(player, "ENTITY_EXPERIENCE_ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_TOUCH", "ORB_PICKUP");
    }

    public static void cannotAfford(Player player) {
        play(player, "ENTITY_VILLAGER_NO", "VILLAGER_NO");
    }

    public static void countdownTick(Player player) {
        play(player, "BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING");
    }

    public static void countdownStart(Player player) {
        play(player, "ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
    }

    public static void generatorUpgrade(Player player) {
        play(player, "BLOCK_NOTE_BLOCK_CHIME", "BLOCK_NOTE_PLING", "NOTE_PLING", "ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
    }

    /** Standing on/at forge: default item pickup. */
    public static void forgeCollect(Player player) {
        play(player, 1F, 1F, "ENTITY_ITEM_PICKUP", "ITEM_PICKUP");
    }

    /** In share range but not at forge: quieter alternate (Hypixel share cue). */
    public static void forgeShare(Player player) {
        play(player, 0.35F, 1.4F, "ENTITY_EXPERIENCE_ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_TOUCH", "ORB_PICKUP");
    }
}
