package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Soft spectate + respawn delay. Owned by ArenaManager. */
final class SoftSpectateService {
    private final ArenaManager manager;
    private final Set<UUID> respawning = new HashSet<UUID>();

    SoftSpectateService(ArenaManager manager) {
        this.manager = manager;
    }

    boolean isRespawning(UUID uuid) { return respawning.contains(uuid); }

    void markRespawning(UUID uuid) { respawning.add(uuid); }

    void clear(UUID uuid) { respawning.remove(uuid); }

    void clearAll() { respawning.clear(); }

    boolean isSoftSpectating(Player player) {
        Arena arena = manager.arena();
        return player != null && arena.contains(player.getUniqueId())
            && (arena.eliminated().contains(player.getUniqueId()) || respawning.contains(player.getUniqueId()));
    }

    /** Hypixel-style soft spectate: adventure flight + invis; never GameMode.SPECTATOR. */
    void applySoftSpectate(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1), true);
        try {
            player.getClass().getMethod("setCollidable", boolean.class).invoke(player, false);
        } catch (Throwable ignored) { }
        manager.plugin().views().updateAll();
    }

    Location respawnLocation(Player player) {
        Arena arena = manager.arena();
        if (!arena.contains(player.getUniqueId())) return player.getWorld().getSpawnLocation();
        if (arena.eliminated().contains(player.getUniqueId())) return spectatorLocation(player);
        TeamColor team = arena.team(player.getUniqueId());
        Location spawn = team == null ? null : arena.settings().team(team).spawn();
        if (spawn != null) return spawn;
        return spectatorLocation(player);
    }

    /** Arena spectator point rebound to the live game world (final death / bed-gone). */
    private Location spectatorLocation(Player player) {
        Arena arena = manager.arena();
        Location spectator = arena.settings().spectator();
        World world = Bukkit.getWorld(arena.settings().worldName());
        if (spectator != null) {
            if (world != null) spectator.setWorld(world);
            return spectator;
        }
        if (world != null) return world.getSpawnLocation();
        return player.getWorld().getSpawnLocation();
    }

    void afterRespawn(final Player player) {
        final Arena arena = manager.arena();
        if (!arena.contains(player.getUniqueId())) return;
        player.setFallDistance(0F);
        if (arena.eliminated().contains(player.getUniqueId())) {
            respawning.remove(player.getUniqueId());
            manager.clearPlayer(player);
            final Location spectator = spectatorLocation(player);
            player.teleport(spectator);
            applySoftSpectate(player);
            player.getInventory().setItem(0, Items.named(new ItemStack(Material.COMPASS), ChatColor.GREEN + "Spectate", ChatColor.GRAY + "Click to watch a player"));
            player.getInventory().setItem(7, PlayerLoadoutService.playAgainItem());
            player.getInventory().setItem(8, PlayerLoadoutService.leaveItem("Return to Lobby"));
            player.sendMessage(ChatColor.RED + "FINAL KILL! You are now spectating.");
            // Belt: some clients ignore same-tick teleports after respawn.
            Bukkit.getScheduler().runTask(manager.plugin(), new Runnable() {
                @Override public void run() {
                    if (!player.isOnline() || !arena.eliminated().contains(player.getUniqueId())) return;
                    player.teleport(spectatorLocation(player));
                    applySoftSpectate(player);
                }
            });
            return;
        }
        TeamColor team = arena.team(player.getUniqueId());
        Location spawn = team == null ? null : arena.settings().team(team).spawn();
        if (spawn != null) player.teleport(spawn);
        applySoftSpectate(player);
        final int seconds = Math.max(0, manager.plugin().getConfig().getInt("respawn-seconds", 5));
        if (seconds <= 0) {
            respawning.remove(player.getUniqueId());
            if (team != null) manager.spawnPlayer(player, team);
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Respawning in " + seconds + " seconds...");
        Bukkit.getScheduler().runTaskLater(manager.plugin(), new Runnable() {
            @Override public void run() {
                respawning.remove(player.getUniqueId());
                if (arena.state() == Arena.State.RUNNING && arena.contains(player.getUniqueId()) && player.isOnline()
                    && !arena.eliminated().contains(player.getUniqueId())) {
                    manager.spawnPlayer(player, arena.team(player.getUniqueId()));
                }
            }
        }, seconds * 20L);
    }
}
