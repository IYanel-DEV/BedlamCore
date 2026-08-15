package dev.iyanel.bedlamcore.compat;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/** Resolve renamed particles across 1.8–modern without NMS. */
public final class Particles {
    private static Method spawnParticle;
    private static Method playerSpawnParticle;
    private static Class<?> particleClass;
    private static boolean resolved;

    private Particles() {
    }

    /** Pulse at a setup pin (CRIT / END_ROD / FLAME). Visible to viewer when possible. */
    public static void setupPin(Player viewer, Location location) {
        if (location == null || location.getWorld() == null) return;
        Location pin = location.getBlock().getLocation().add(0.5, 0.55, 0.5);
        if (play(viewer, pin, 6, 0.12, "CRIT", "CRITICAL_HIT")) return;
        if (play(viewer, pin, 4, 0.08, "END_ROD", "FIREWORKS_SPARK", "FIREWORK")) return;
        play(viewer, pin, 5, 0.1, "FLAME", "SMOKE");
    }

    public static boolean play(Player viewer, Location location, int count, double spread, String... names) {
        if (location == null || location.getWorld() == null) return false;
        Object particle = resolve(names);
        if (particle != null && spawnModern(viewer, location, particle, count, spread)) return true;
        return playEffect(location, names);
    }

    private static boolean spawnModern(Player viewer, Location location, Object particle, int count, double spread) {
        resolveApi();
        try {
            if (viewer != null && playerSpawnParticle != null) {
                playerSpawnParticle.invoke(viewer, particle, location, Integer.valueOf(count),
                    Double.valueOf(spread), Double.valueOf(spread), Double.valueOf(spread), Double.valueOf(0.0));
                return true;
            }
            if (spawnParticle != null) {
                spawnParticle.invoke(location.getWorld(), particle, location, Integer.valueOf(count),
                    Double.valueOf(spread), Double.valueOf(spread), Double.valueOf(spread), Double.valueOf(0.0));
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean playEffect(Location location, String... names) {
        for (String name : names) {
            Effect effect = effectOf(name);
            if (effect == null) continue;
            try {
                location.getWorld().playEffect(location, effect, 0);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Effect effectOf(String name) {
        if (name == null) return null;
        try {
            return Effect.valueOf(name);
        } catch (IllegalArgumentException ignored) {
        }
        for (Effect effect : Effect.values()) {
            String n = effect.name();
            if (n.equalsIgnoreCase(name) || n.contains(name)) return effect;
        }
        return null;
    }

    private static Object resolve(String... names) {
        resolveApi();
        if (particleClass == null) return null;
        Object[] constants = particleClass.getEnumConstants();
        if (constants == null) return null;
        for (String name : names) {
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(name)) return constant;
            }
        }
        for (Object constant : constants) {
            String n = ((Enum<?>) constant).name();
            for (String name : names) {
                if (n.equalsIgnoreCase(name) || n.contains(name)) return constant;
            }
        }
        return null;
    }

    private static void resolveApi() {
        if (resolved) return;
        resolved = true;
        try {
            particleClass = Class.forName("org.bukkit.Particle");
            spawnParticle = org.bukkit.World.class.getMethod("spawnParticle", particleClass, Location.class,
                int.class, double.class, double.class, double.class, double.class);
            try {
                playerSpawnParticle = Player.class.getMethod("spawnParticle", particleClass, Location.class,
                    int.class, double.class, double.class, double.class, double.class);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
            particleClass = null;
        }
    }
}
