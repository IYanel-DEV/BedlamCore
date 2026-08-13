package dev.iyanel.bedlamcore.compat;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypixel-like invis: hide armor packets from other players; keep held item visible.
 * Modern: Player#sendEquipmentChange. 1.8: PacketPlayOutEntityEquipment via EntityVisibility.
 * Real armor stays on the entity (protection unchanged).
 */
public final class InvisArmor {
    /** viewer -> subjects whose armor we emptied client-side. */
    private static final Map<UUID, Set<UUID>> hidden = new ConcurrentHashMap<UUID, Set<UUID>>();
    private static final Set<UUID> active = ConcurrentHashMap.newKeySet();

    private static boolean modernResolved;
    private static Method sendEquipmentChange;
    private static Method setArrowsInBody;
    private static boolean arrowsMethodResolved;
    private static Object slotHead;
    private static Object slotChest;
    private static Object slotLegs;
    private static Object slotFeet;
    private static ItemStack air;

    private InvisArmor() {
    }

    private static ItemStack air() {
        if (air == null) air = new ItemStack(Material.AIR);
        return air;
    }

    public static void tick(BedlamCore plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ArenaManager manager = plugin.games().arena(player);
            if (manager == null || manager.arena().state() != Arena.State.RUNNING
                || manager.isSoftSpectating(player) || manager.isRespawning(player.getUniqueId())) {
                clear(player);
                continue;
            }
            if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                active.add(player.getUniqueId());
                clearStuckArrows(player);
                hideFromOthers(player);
            } else {
                clear(player);
            }
        }
    }

    /** Force restore (death, milk, reveal trap, leave, player-hit). */
    public static void clear(Player player) {
        if (player == null) return;
        if (!active.remove(player.getUniqueId()) && !wasHidden(player.getUniqueId())) return;
        restoreToOthers(player);
    }

    /** Hide stuck-arrow visuals while invisible (1.9+ body count; 1.8 nearby Arrow entities). */
    public static void clearStuckArrows(Player player) {
        if (player == null) return;
        resolveArrowsMethod();
        if (setArrowsInBody != null) {
            try {
                setArrowsInBody.invoke(player, 0);
            } catch (Throwable ignored) {
            }
        }
        try {
            for (Entity nearby : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                if (!(nearby instanceof Arrow)) continue;
                Arrow arrow = (Arrow) nearby;
                if (arrow.getVelocity().lengthSquared() < 0.01) arrow.remove();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resolveArrowsMethod() {
        if (arrowsMethodResolved) return;
        arrowsMethodResolved = true;
        try {
            setArrowsInBody = LivingEntity.class.getMethod("setArrowsInBody", int.class);
        } catch (Throwable ignored) {
            setArrowsInBody = null;
        }
    }

    public static void clearAll() {
        for (UUID id : active.toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) clear(player);
            else active.remove(id);
        }
        hidden.clear();
    }

    private static boolean wasHidden(UUID subject) {
        for (Set<UUID> set : hidden.values()) {
            if (set.contains(subject)) return true;
        }
        return false;
    }

    private static void hideFromOthers(Player subject) {
        for (Player viewer : subject.getWorld().getPlayers()) {
            if (viewer.getUniqueId().equals(subject.getUniqueId())) continue;
            sendArmor(viewer, subject, true);
            mark(viewer.getUniqueId(), subject.getUniqueId());
        }
    }

    private static void restoreToOthers(Player subject) {
        for (Player viewer : subject.getWorld().getPlayers()) {
            if (viewer.getUniqueId().equals(subject.getUniqueId())) continue;
            sendArmor(viewer, subject, false);
            unmark(viewer.getUniqueId(), subject.getUniqueId());
        }
    }

    private static void mark(UUID viewer, UUID subject) {
        Set<UUID> set = hidden.get(viewer);
        if (set == null) {
            set = ConcurrentHashMap.newKeySet();
            hidden.put(viewer, set);
        }
        set.add(subject);
    }

    private static void unmark(UUID viewer, UUID subject) {
        Set<UUID> set = hidden.get(viewer);
        if (set == null) return;
        set.remove(subject);
        if (set.isEmpty()) hidden.remove(viewer);
    }

    private static void sendArmor(Player viewer, Player subject, boolean empty) {
        if (tryModern(viewer, subject, empty)) return;
        // 1.8 slots: 1 boots, 2 legs, 3 chest, 4 helm — never touch slot 0 (hand)
        PlayerInventory inv = subject.getInventory();
        EntityVisibility.sendLegacyEquipment(viewer, subject.getEntityId(), GameRules.LEGACY_EQUIP_BOOTS, empty ? air() : inv.getBoots());
        EntityVisibility.sendLegacyEquipment(viewer, subject.getEntityId(), GameRules.LEGACY_EQUIP_LEGS, empty ? air() : inv.getLeggings());
        EntityVisibility.sendLegacyEquipment(viewer, subject.getEntityId(), GameRules.LEGACY_EQUIP_CHEST, empty ? air() : inv.getChestplate());
        EntityVisibility.sendLegacyEquipment(viewer, subject.getEntityId(), GameRules.LEGACY_EQUIP_HELMET, empty ? air() : inv.getHelmet());
    }

    private static boolean tryModern(Player viewer, Player subject, boolean empty) {
        resolveModern();
        if (sendEquipmentChange == null) return false;
        try {
            PlayerInventory inv = subject.getInventory();
            ItemStack emptyStack = air();
            sendEquipmentChange.invoke(viewer, subject, slotHead, empty ? emptyStack : orAir(inv.getHelmet()));
            sendEquipmentChange.invoke(viewer, subject, slotChest, empty ? emptyStack : orAir(inv.getChestplate()));
            sendEquipmentChange.invoke(viewer, subject, slotLegs, empty ? emptyStack : orAir(inv.getLeggings()));
            sendEquipmentChange.invoke(viewer, subject, slotFeet, empty ? emptyStack : orAir(inv.getBoots()));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ItemStack orAir(ItemStack stack) {
        return stack == null ? air() : stack;
    }

    private static void resolveModern() {
        if (modernResolved) return;
        modernResolved = true;
        try {
            Class<?> slot = Class.forName("org.bukkit.inventory.EquipmentSlot");
            slotHead = slot.getField("HEAD").get(null);
            slotChest = slot.getField("CHEST").get(null);
            slotLegs = slot.getField("LEGS").get(null);
            slotFeet = slot.getField("FEET").get(null);
            sendEquipmentChange = Player.class.getMethod("sendEquipmentChange",
                org.bukkit.entity.LivingEntity.class, slot, ItemStack.class);
        } catch (Throwable ignored) {
            sendEquipmentChange = null;
        }
    }
}
