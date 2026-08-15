package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.InvisArmor;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Trap triggers + heal-pool tick. Owned by ArenaManager. */
final class MatchEffectsService {
    private final ArenaManager manager;
    /** Enemies inside each team's trap AABB last tick — enter-only fire (already-in-base grace). */
    private final Map<TeamColor, Set<UUID>> trapInside = new EnumMap<TeamColor, Set<UUID>>(TeamColor.class);

    MatchEffectsService(ArenaManager manager) {
        this.manager = manager;
    }

    void start() {
        trapInside.clear();
        final Arena arena = manager.arena();
        int id = new BukkitRunnable() {
            @Override public void run() {
                if (arena.state() != Arena.State.RUNNING) return;
                tickTraps();
                for (TeamColor team : arena.settings().configuredTeams()) {
                    if (!arena.healPool(team)) continue;
                    Location spawn = arena.settings().team(team).spawn();
                    if (spawn == null || spawn.getWorld() == null) continue;
                    // Green heal-pool particles around the island (Hypixel-like).
                    for (int i = 0; i < 12; i++) {
                        double angle = (Math.PI * 2 * i) / 12.0;
                        Location particle = spawn.clone().add(Math.cos(angle) * 3.5, 0.4 + (i % 3) * 0.35, Math.sin(angle) * 3.5);
                        spawn.getWorld().playEffect(particle, Effect.HAPPY_VILLAGER, 0);
                    }
                    spawn.getWorld().playEffect(spawn.clone().add(0, 1.0, 0), Effect.HAPPY_VILLAGER, 0);
                }
                for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
                    if (arena.eliminated().contains(entry.getKey()) || !arena.healPool(entry.getValue())) continue;
                    Player player = Bukkit.getPlayer(entry.getKey());
                    Location spawn = arena.settings().team(entry.getValue()).spawn();
                    if (player == null || spawn == null || !Locations.near(player.getLocation(), spawn, GameRules.HEAL_POOL_RADIUS)) continue;
                    if (player.getHealth() < player.getMaxHealth()) player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1.0));
                }
            }
        }.runTaskTimer(manager.plugin(), 20L, 20L).getTaskId();
        arena.tasks().add(id);
    }

    private void tickTraps() {
        Arena arena = manager.arena();
        long now = System.currentTimeMillis();
        for (TeamColor team : arena.settings().configuredTeams()) {
            Location spawn = arena.settings().team(team).spawn();
            Location bed = arena.settings().team(team).bed();
            if (spawn == null || bed == null) continue;
            Location forge = arena.settings().team(team).forge();
            boolean useForge = forge != null && GameRules.trapForgeNatural(
                forge.getX(), forge.getZ(), bed.getX(), bed.getZ(), spawn.getX(), spawn.getZ());
            double[] aabb = GameRules.trapBaseAabb(
                spawn.getX(), spawn.getY(), spawn.getZ(),
                bed.getX(), bed.getZ(),
                useForge, useForge ? forge.getX() : 0, useForge ? forge.getZ() : 0);

            Set<UUID> was = trapInside.get(team);
            if (was == null) {
                was = new HashSet<UUID>();
                trapInside.put(team, was);
            }
            Set<UUID> nowInside = new HashSet<UUID>();
            List<Arena.TrapType> queue = arena.traps(team);
            boolean canFire = queue != null && !queue.isEmpty() && arena.trapReady(team, now);

            for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
                if (entry.getValue() == team || arena.eliminated().contains(entry.getKey())) continue;
                Player enemy = Bukkit.getPlayer(entry.getKey());
                if (enemy == null || arena.eliminated().contains(enemy.getUniqueId()) || manager.isRespawning(enemy.getUniqueId())) continue;
                Location at = enemy.getLocation();
                boolean inside = GameRules.inTrapBase(at.getX(), at.getY(), at.getZ(), aabb);
                if (inside) nowInside.add(enemy.getUniqueId());
                if (arena.trapImmune(enemy.getUniqueId(), now)) continue;
                if (!canFire || !GameRules.trapEnter(was.contains(enemy.getUniqueId()), inside)) continue;
                Arena.TrapType trap = arena.popTrap(team);
                if (trap == null) break;
                arena.armTrapCooldown(team, now + GameRules.TRAP_COOLDOWN_TICKS * 50L);
                fireTrap(team, trap, enemy, spawn);
                canFire = false;
            }
            was.clear();
            was.addAll(nowInside);
        }
    }

    private void fireTrap(TeamColor team, Arena.TrapType trap, Player enemy, Location spawn) {
        Arena arena = manager.arena();
        announceTrap(team, trap.displayName() + " — " + enemy.getName());
        enemy.sendMessage(ChatColor.RED + "You triggered " + trap.displayName() + "!");
        switch (trap) {
            case BLINDNESS:
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 8 * 20, 0), true);
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 8 * 20, 0), true);
                break;
            case MINER_FATIGUE:
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 8 * 20, 0), true);
                break;
            case REVEAL:
                enemy.removePotionEffect(PotionEffectType.INVISIBILITY);
                InvisArmor.clear(enemy);
                enemy.sendMessage(ChatColor.YELLOW + "Your invisibility was stripped!");
                break;
            case COUNTER_OFFENSIVE:
                for (Map.Entry<UUID, TeamColor> member : arena.players().entrySet()) {
                    if (member.getValue() != team || arena.eliminated().contains(member.getKey())) continue;
                    Player ally = Bukkit.getPlayer(member.getKey());
                    if (ally == null || !Locations.near(ally.getLocation(), spawn, GameRules.TRAP_TRIGGER_RADIUS + 6)) continue;
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15 * 20, 1), true);
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 15 * 20, 1), true);
                }
                break;
            default:
                break;
        }
    }

    private void announceTrap(TeamColor team, String detail) {
        Arena arena = manager.arena();
        for (Map.Entry<UUID, TeamColor> member : arena.players().entrySet()) {
            if (member.getValue() != team) continue;
            Player online = Bukkit.getPlayer(member.getKey());
            if (online == null) continue;
            manager.title(online, ChatColor.RED + "TRAP TRIGGERED!", ChatColor.WHITE + detail);
            Sounds.levelUp(online);
        }
    }
}
