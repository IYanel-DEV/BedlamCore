// REGISTER in BedlamCore: new NpcSoundListener(plugin)
package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;

/**
 * Shop / upgrade / lobby / Citizens NPCs: no idle, hurt, death, or step sounds.
 * Modern: Entity.setSilent. Paper 1.8.8: NMS Entity.b(true) (Silent datawatcher) —
 * CraftEntity.setSilent does not exist on this fork, so Bukkit-only reflection was a no-op.
 */
public final class NpcSoundListener implements Listener {
    private static Method silentMethod;
    private static boolean silentResolved;

    private final Plugin plugin;

    public NpcSoundListener(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerEntitySoundCancel();
        // Citizens / remount clears silent; re-apply every tick on tagged NPCs.
        new BukkitRunnable() {
            @Override public void run() { reapply(); }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public static void silence(Entity entity) {
        if (entity == null) return;
        Method method = silent();
        if (method == null) return;
        try {
            if (method.getDeclaringClass().getName().contains("minecraft.server")) {
                Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
                method.invoke(handle, true);
            } else {
                method.invoke(entity, true);
            }
        } catch (Exception ignored) { }
    }

    /** Bukkit Entity.setSilent → CraftEntity.setSilent → NMS Entity.b(true) on Paper 1.8.8. */
    private static Method silent() {
        if (silentResolved) return silentMethod;
        silentResolved = true;
        try {
            silentMethod = Entity.class.getMethod("setSilent", boolean.class);
            return silentMethod;
        } catch (Exception ignored) { }
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String nms = pkg.substring(pkg.lastIndexOf('.') + 1);
            silentMethod = Class.forName("org.bukkit.craftbukkit." + nms + ".entity.CraftEntity")
                .getMethod("setSilent", boolean.class);
            return silentMethod;
        } catch (Exception ignored) { }
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String nms = pkg.substring(pkg.lastIndexOf('.') + 1);
            // Paper 1.8.8: public void b(boolean) writes Silent into datawatcher index 4;
            // makeSound() checks R() and skips NamedSoundEffect when set.
            silentMethod = Class.forName("net.minecraft.server." + nms + ".Entity")
                .getMethod("b", boolean.class);
            return silentMethod;
        } catch (Exception ignored) { }
        return silentMethod;
    }

    // ponytail: O(world entities) scan; fine at lobby+arena NPC counts
    private void reapply() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (LobbyNpcService.isPluginNpc(entity)) {
                    LobbyNpcService.tagSilent(entity);
                    silence(entity);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void registerEntitySoundCancel() {
        try {
            final Class<? extends Event> soundEvent = (Class<? extends Event>) Class.forName("org.bukkit.event.entity.EntitySoundEvent");
            Bukkit.getPluginManager().registerEvent(soundEvent, this, EventPriority.HIGHEST, new EventExecutor() {
                @Override
                public void execute(Listener listener, Event event) {
                    try {
                        Entity entity = (Entity) soundEvent.getMethod("getEntity").invoke(event);
                        if (LobbyNpcService.isPluginNpc(entity)) {
                            soundEvent.getMethod("setCancelled", boolean.class).invoke(event, true);
                        }
                    } catch (Exception ignored) { }
                }
            }, plugin, true);
        } catch (ClassNotFoundException ignored) { }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (LobbyNpcService.isPluginNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcTarget(EntityTargetEvent event) {
        if (LobbyNpcService.isPluginNpc(event.getEntity())) event.setCancelled(true);
    }
}
