package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// REGISTER in BedlamCore: new PearlListener(plugin)
public final class PearlListener implements Listener {
    private final BedlamCore plugin;
    private final Set<UUID> pearled = new HashSet<UUID>();

    public PearlListener(BedlamCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Location dest = event.getTo();
        if (dest != null) Sounds.playAt(dest, "ENDERMAN_TELEPORT", "ENTITY_ENDERMAN_TELEPORT", "ENTITY_ENDERMEN_TELEPORT");
        mark(event.getPlayer().getUniqueId());
    }

    /** 1.8: pearl hit can apply FALL before TeleportCause.ENDER_PEARL is observed. */
    @EventHandler
    public void onPearlHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;
        mark(((Player) event.getEntity().getShooter()).getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPearlDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        String cause = event.getCause().name();
        if (cause.equals("ENDER_PEARL") || (cause.equals("FALL") && pearled.contains(event.getEntity().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    private void mark(final UUID id) {
        pearled.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { pearled.remove(id); }
        }, 2L);
    }
}
