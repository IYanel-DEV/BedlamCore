// REGISTER in BedlamCore: new NpcSoundListener(plugin)
package dev.iyanel.bedlamcore.game;

import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Shop / upgrade / lobby / Citizens NPCs: no idle, hurt, death, or step sounds.
 * Prefer metadata + EntitySoundEvent cancel; NMS Entity.b(true) only on spawn/remount (1.8).
 */
public final class NpcSoundListener implements Listener {
    private static Method silentMethod;
    private static boolean silentResolved;

    private final Plugin plugin;

    public NpcSoundListener(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerEntitySoundCancel();
        registerCitizensSpawnRemute();
        // Five-second safety net if a Citizens remount skips its event; tracked entities only.
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { remuteTagged(); }
        }, 100L, 100L);
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

    public static String compatibilityMode() {
        Method method = silent();
        if (method == null) return "unavailable";
        String owner = method.getDeclaringClass().getName();
        return owner.contains("minecraft.server") || owner.contains("craftbukkit")
            ? "reflective 1.8 silence fallback" : "Bukkit setSilent";
    }

    /** Only retained plugin entities — never scan every entity in every world. */
    private void remuteTagged() {
        LobbyNpcService.remuteTaggedEntities();
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

    /** Soft-dep: remount/spawn clears NMS silent — remute when Citizens fires NPCSpawnEvent. */
    @SuppressWarnings("unchecked")
    private void registerCitizensSpawnRemute() {
        try {
            final Class<? extends Event> spawnEvent = (Class<? extends Event>) Class.forName("net.citizensnpcs.api.event.NPCSpawnEvent");
            Bukkit.getPluginManager().registerEvent(spawnEvent, this, EventPriority.MONITOR, new EventExecutor() {
                @Override
                public void execute(Listener listener, Event event) {
                    try {
                        Object npc = spawnEvent.getMethod("getNPC").invoke(event);
                        Entity entity = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
                        if (entity == null) return;
                        if (LobbyNpcService.isPluginNpc(entity) || citizensMarkedSilent(npc)) {
                            LobbyNpcService.mute(entity);
                        }
                    } catch (Exception ignored) { }
                }
            }, plugin, true);
        } catch (ClassNotFoundException ignored) { }
    }

    private static boolean citizensMarkedSilent(Object npc) {
        try {
            Class<?> npcClass = Class.forName("net.citizensnpcs.api.npc.NPC");
            Object key = npcClass.getField("SILENT_METADATA").get(null);
            Object data = npc.getClass().getMethod("data").invoke(npc);
            try {
                return Boolean.TRUE.equals(data.getClass().getMethod("get", Object.class).invoke(data, key));
            } catch (NoSuchMethodException ignored) {
                return Boolean.TRUE.equals(data.getClass().getMethod("get", Object.class, Object.class).invoke(data, key, Boolean.FALSE));
            }
        } catch (Throwable ignored) { }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (LobbyNpcService.isPluginNpc(entity)) {
            silence(entity);
            return;
        }
        // Citizens remount tags "NPC" before our META_* may be re-applied — remute next tick if tagged.
        if (!entity.hasMetadata("NPC")) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (LobbyNpcService.isPluginNpc(entity)) LobbyNpcService.mute(entity);
            }
        });
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
