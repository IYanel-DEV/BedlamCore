package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/** Bridge Egg trail placer. Owned by ArenaManager. */
final class BridgeEggLauncher {
    private final ArenaManager manager;

    BridgeEggLauncher(ArenaManager manager) {
        this.manager = manager;
    }

    /** Throw Bridge Egg: 3-wide team wool one block under the egg each tick (not on ProjectileHit). */
    void launch(final Player player) {
        final Arena arena = manager.arena();
        final TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING || manager.isSoftSpectating(player)) return;
        final UUID thrower = player.getUniqueId();
        final org.bukkit.entity.Egg egg = player.launchProjectile(org.bukkit.entity.Egg.class);
        final Location origin = egg.getLocation().clone();
        new BukkitRunnable() {
            private int ticks;
            private int path;
            private Location prev;
            @Override public void run() {
                ticks++;
                if (!egg.isValid() || egg.isDead() || ticks > GameRules.BRIDGE_EGG_MAX_TICKS
                    || path >= GameRules.BRIDGE_EGG_MAX_PATH
                    || origin.distanceSquared(egg.getLocation()) >= GameRules.BRIDGE_EGG_MAX_DISTANCE * GameRules.BRIDGE_EGG_MAX_DISTANCE
                    || arena.state() != Arena.State.RUNNING) {
                    cancel();
                    return;
                }
                Location here = egg.getLocation();
                Player online = Bukkit.getPlayer(thrower);
                if (online != null && here.distanceSquared(online.getLocation()) < 4.0) {
                    prev = here.clone();
                    return;
                }
                org.bukkit.util.Vector vel = egg.getVelocity();
                double dx = vel.getX();
                double dz = vel.getZ();
                if (prev == null) {
                    placeBridgeSlice(here, team, dx, dz);
                    prev = here.clone();
                    return;
                }
                dx = here.getX() - prev.getX();
                dz = here.getZ() - prev.getZ();
                double dist = prev.distance(here);
                int steps = Math.max(1, (int) Math.ceil(dist));
                for (int i = 1; i <= steps && path < GameRules.BRIDGE_EGG_MAX_PATH; i++) {
                    double t = (double) i / (double) steps;
                    Location point = prev.clone().add(
                        (here.getX() - prev.getX()) * t,
                        (here.getY() - prev.getY()) * t,
                        (here.getZ() - prev.getZ()) * t);
                    placeBridgeSlice(point, team, dx, dz);
                }
                prev = here.clone();
            }

            private void placeBridgeSlice(Location at, TeamColor color, double dx, double dz) {
                if (path >= GameRules.BRIDGE_EGG_MAX_PATH) return;
                path++;
                int ox = GameRules.bridgeSideX(dx, dz);
                int oz = GameRules.bridgeSideZ(dx, dz);
                int dip = GameRules.bridgeEggEndDip(path, GameRules.BRIDGE_EGG_MAX_PATH);
                Block under = at.getBlock().getRelative(0, -1 - dip, 0);
                int bx = under.getX();
                int by = under.getY();
                int bz = under.getZ();
                placeBridgeCell(under.getWorld().getBlockAt(bx, by, bz), color);
                placeBridgeCell(under.getWorld().getBlockAt(bx + ox, by, bz + oz), color);
                placeBridgeCell(under.getWorld().getBlockAt(bx - ox, by, bz - oz), color);
            }

            private void placeBridgeCell(Block block, TeamColor color) {
                if (!GameRules.isBridgeReplaceable(block.getType().name())) return;
                if (manager.placeDenyReason(block.getLocation()) != null) return;
                String key = Locations.blockKey(block.getLocation());
                if (arena.placedBlocks().contains(key)) return;
                color.placeAsBlock(block);
                arena.placedBlocks().add(key);
            }
        }.runTaskTimer(manager.plugin(), 1L, 1L);
    }
}
