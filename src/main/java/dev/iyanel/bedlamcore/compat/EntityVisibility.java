package dev.iyanel.bedlamcore.compat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-viewer entity hide/show. Modern: Player#hideEntity. 1.8: destroy/spawn packets.
 * Spectators always hide — they see through ArmorStand invisibility.
 */
public final class EntityVisibility {
    private static Method hideEntity;
    private static Method showEntity;
    private static boolean modernResolved;

    private static boolean packetsResolved;
    private static boolean packetsOk;
    private static Constructor<?> destroyCtor;
    private static Constructor<?> spawnLivingCtor;
    private static Constructor<?> equipCtor;
    private static Method getHandlePlayer;
    private static Method getHandleEntity;
    private static Method sendPacket;
    private static Method asNmsCopy;
    private static Field playerConnection;

    /** viewer -> entity ids destroyed client-side (1.8 packet path only). */
    private static final Map<UUID, Set<Integer>> packetHidden = new ConcurrentHashMap<UUID, Set<Integer>>();

    private EntityVisibility() {
    }

    public static boolean isSpectator(Player player) {
        return player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    /** Distance + spectator gate: ArmorStands never shown to spectators (see through invis). */
    public static void apply(Plugin plugin, Player viewer, Entity entity, boolean near) {
        if (entity instanceof org.bukkit.entity.ArmorStand && isSpectator(viewer)) {
            hide(plugin, viewer, entity);
            return;
        }
        if (!near) hide(plugin, viewer, entity);
        else show(plugin, viewer, entity);
    }

    public static void hide(Plugin plugin, Player viewer, Entity entity) {
        if (viewer == null || entity == null || !entity.isValid()) return;
        if (invoke(hideEntity(), viewer, plugin, entity)) return;
        packetHide(viewer, entity);
    }

    public static void show(Plugin plugin, Player viewer, Entity entity) {
        if (viewer == null || entity == null || !entity.isValid()) return;
        if (entity instanceof org.bukkit.entity.ArmorStand && isSpectator(viewer)) {
            hide(plugin, viewer, entity);
            return;
        }
        if (invoke(showEntity(), viewer, plugin, entity)) return;
        packetShow(viewer, entity);
    }

    private static boolean invoke(Method method, Player viewer, Plugin plugin, Entity entity) {
        if (method == null) return false;
        try {
            method.invoke(viewer, plugin, entity);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Method hideEntity() {
        resolveModern();
        return hideEntity;
    }

    private static Method showEntity() {
        resolveModern();
        return showEntity;
    }

    private static void resolveModern() {
        if (modernResolved) return;
        modernResolved = true;
        try {
            hideEntity = Player.class.getMethod("hideEntity", Plugin.class, Entity.class);
            showEntity = Player.class.getMethod("showEntity", Plugin.class, Entity.class);
        } catch (Exception ignored) {
        }
    }

    private static void packetHide(Player viewer, Entity entity) {
        if (!resolvePackets()) return;
        Set<Integer> ids = packetHidden.get(viewer.getUniqueId());
        if (ids == null) {
            ids = new HashSet<Integer>();
            packetHidden.put(viewer.getUniqueId(), ids);
        }
        if (!ids.add(entity.getEntityId())) return;
        try {
            send(viewer, destroyCtor.newInstance((Object) new int[]{entity.getEntityId()}));
        } catch (Exception ignored) {
            ids.remove(entity.getEntityId());
        }
    }

    private static void packetShow(Player viewer, Entity entity) {
        if (!resolvePackets() || !(entity instanceof LivingEntity)) return;
        Set<Integer> ids = packetHidden.get(viewer.getUniqueId());
        if (ids == null || !ids.remove(entity.getEntityId())) return;
        try {
            Object handle = getHandleEntity.invoke(entity);
            send(viewer, spawnLivingCtor.newInstance(handle));
            sendEquip(viewer, entity);
        } catch (Exception ignored) {
        }
    }

    private static void sendEquip(Player viewer, Entity entity) {
        if (equipCtor == null || asNmsCopy == null || !(entity instanceof LivingEntity)) return;
        EntityEquipment eq = ((LivingEntity) entity).getEquipment();
        if (eq == null) return;
        sendEquipSlot(viewer, entity.getEntityId(), 4, eq.getHelmet());
        sendEquipSlot(viewer, entity.getEntityId(), 0, eq.getItemInHand());
    }

    /** 1.8 PacketPlayOutEntityEquipment: 0 hand, 1 boots, 2 legs, 3 chest, 4 helm. */
    public static void sendLegacyEquipment(Player viewer, int entityId, int slot, ItemStack stack) {
        if (!resolvePackets() || equipCtor == null || asNmsCopy == null) return;
        ItemStack send = stack == null || stack.getType() == Material.AIR
            ? new ItemStack(Material.AIR) : stack;
        try {
            Object nms = asNmsCopy.invoke(null, send);
            send(viewer, equipCtor.newInstance(entityId, slot, nms));
        } catch (Exception ignored) {
        }
    }

    private static void sendEquipSlot(Player viewer, int entityId, int slot, ItemStack stack) {
        if (stack == null) return;
        try {
            Object nms = asNmsCopy.invoke(null, stack);
            send(viewer, equipCtor.newInstance(entityId, slot, nms));
        } catch (Exception ignored) {
        }
    }

    private static void send(Player viewer, Object packet) throws Exception {
        Object nmsPlayer = getHandlePlayer.invoke(viewer);
        Object connection = playerConnection.get(nmsPlayer);
        sendPacket.invoke(connection, packet);
    }

    private static boolean resolvePackets() {
        if (packetsResolved) return packetsOk;
        packetsResolved = true;
        try {
            String v = nmsVersion();
            if (v == null) return false;
            destroyCtor = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityDestroy")
                .getConstructor(int[].class);
            Class<?> living = Class.forName("net.minecraft.server." + v + ".EntityLiving");
            spawnLivingCtor = Class.forName("net.minecraft.server." + v + ".PacketPlayOutSpawnEntityLiving")
                .getConstructor(living);
            Class<?> nmsItem = Class.forName("net.minecraft.server." + v + ".ItemStack");
            equipCtor = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityEquipment")
                .getConstructor(int.class, int.class, nmsItem);
            getHandlePlayer = Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer").getMethod("getHandle");
            getHandleEntity = Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftEntity").getMethod("getHandle");
            playerConnection = getHandlePlayer.getReturnType().getField("playerConnection");
            Class<?> packet = Class.forName("net.minecraft.server." + v + ".Packet");
            sendPacket = playerConnection.getType().getMethod("sendPacket", packet);
            asNmsCopy = Class.forName("org.bukkit.craftbukkit." + v + ".inventory.CraftItemStack")
                .getMethod("asNMSCopy", ItemStack.class);
            packetsOk = true;
        } catch (Exception ignored) {
            packetsOk = false;
        }
        return packetsOk;
    }

    private static String nmsVersion() {
        try {
            String name = Bukkit.getServer().getClass().getPackage().getName();
            return name.substring(name.lastIndexOf('.') + 1);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void clearViewer(UUID viewerId) {
        packetHidden.remove(viewerId);
    }
}
