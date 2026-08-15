package dev.iyanel.bedlamcore.arena;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import dev.iyanel.bedlamcore.lobby.LobbyNpcService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Team-aware, temporary Iron Golem defenders. Owned by ArenaManager. */
final class DreamDefenderService {
    private static final double RANGE_SQUARED = 12.0 * 12.0;
    private static final long LIFETIME_MILLIS = 4L * 60L * 1000L;
    private final ArenaManager manager;
    private final Map<UUID, IronGolem> golems = new HashMap<UUID, IronGolem>();
    private final Map<UUID, TeamColor> teams = new HashMap<UUID, TeamColor>();
    private final Map<UUID, Long> expires = new HashMap<UUID, Long>();
    private BukkitTask task;

    DreamDefenderService(ArenaManager manager) {
        this.manager = manager;
    }

    boolean spawn(Player owner, Location location) {
        Arena arena = manager.arena();
        TeamColor team = arena.team(owner.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING || location == null || location.getWorld() == null) return false;
        if (manager.placeDenyReason(location) != null || location.getBlock().getType() != Material.AIR
            || location.clone().add(0, 1, 0).getBlock().getType() != Material.AIR) {
            owner.sendMessage(ChatColor.RED + "There is not enough room for a Dream Defender here.");
            return false;
        }
        IronGolem golem = location.getWorld().spawn(location.clone().add(0.5, 0, 0.5), IronGolem.class);
        golem.setPlayerCreated(true);
        golem.setRemoveWhenFarAway(false);
        golem.setCustomName(team.chatColor() + "Dream Defender");
        golem.setCustomNameVisible(true);
        golem.setMetadata(LobbyNpcService.META_PET, new FixedMetadataValue(manager.plugin(), true));
        UUID id = golem.getUniqueId();
        golems.put(id, golem);
        teams.put(id, team);
        expires.put(id, System.currentTimeMillis() + LIFETIME_MILLIS);
        ensureTask();
        return true;
    }

    TeamColor team(Entity entity) {
        return entity == null ? null : teams.get(entity.getUniqueId());
    }

    void clear() {
        for (IronGolem golem : golems.values()) if (golem != null && golem.isValid()) golem.remove();
        golems.clear();
        teams.clear();
        expires.clear();
        stopTask();
    }

    private void ensureTask() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override public void run() {
                if (manager.arena().state() != Arena.State.RUNNING) {
                    clear();
                    return;
                }
                for (UUID id : new java.util.ArrayList<UUID>(golems.keySet())) tick(id);
                if (golems.isEmpty()) stopTask();
            }
        }.runTaskTimer(manager.plugin(), 0L, 10L);
        manager.arena().tasks().add(task.getTaskId());
    }

    private void stopTask() {
        if (task == null) return;
        int id = task.getTaskId();
        task.cancel();
        task = null;
        // Integer.valueOf: List.remove(int) is index-based and crashed on raw task ids
        manager.arena().tasks().remove(Integer.valueOf(id));
    }

    private void tick(UUID id) {
        IronGolem golem = golems.get(id);
        Long expiry = expires.get(id);
        if (golem == null || !golem.isValid() || golem.isDead() || expiry == null || System.currentTimeMillis() >= expiry) {
            if (golem != null && golem.isValid()) golem.remove();
            golems.remove(id);
            teams.remove(id);
            expires.remove(id);
            return;
        }
        TeamColor ownTeam = teams.get(id);
        Player nearest = null;
        double nearestDistance = RANGE_SQUARED + 1.0;
        for (Player player : manager.arenaPlayers()) {
            if (manager.arena().team(player.getUniqueId()) == ownTeam || manager.isSoftSpectating(player)) continue;
            if (player.getWorld() != golem.getWorld()) continue;
            double distance = player.getLocation().distanceSquared(golem.getLocation());
            if (distance <= RANGE_SQUARED && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        golem.setTarget(nearest);
    }
}
