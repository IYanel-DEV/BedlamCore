package dev.iyanel.bedlamcore.gui;

import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared custom-chest open: title clamp + 1.8 next-tick open (never sync in InventoryClickEvent).
 * Modern opens immediately.
 */
public final class ChestGuis {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private ChestGuis() {
    }

    public static Inventory create(int size, String title) {
        return Bukkit.createInventory(null, size, GameRules.inventoryTitle(title));
    }

    /** True while a deferred 1.8 open is scheduled — drop InventoryClickEvent handling. */
    public static boolean isPendingOpen(Player player) {
        return player != null && PENDING.contains(player.getUniqueId());
    }

    public static void clear(UUID uuid) {
        if (uuid != null) PENDING.remove(uuid);
    }

    /**
     * Open a plugin chest GUI. On 1.8 only: mark pending and open next tick
     * (sync open during InventoryClickEvent → IndexOutOfBounds / desync).
     */
    public static void open(Plugin plugin, final Player player, final Inventory inventory) {
        if (plugin == null || player == null || inventory == null) return;
        if (!isLegacy18()) {
            if (player.isOnline()) player.openInventory(inventory);
            return;
        }
        final UUID uuid = player.getUniqueId();
        PENDING.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    if (player.isOnline()) player.openInventory(inventory);
                } finally {
                    PENDING.remove(uuid);
                }
            }
        }, 1L);
    }

    /** CraftBukkit package segment {@code v1_8_R*} (same source as EntityVisibility.nmsVersion). */
    public static boolean isLegacy18() {
        return legacy18FromPackage(craftPackageName());
    }

    /** Pure parse for coreCheck — no Bukkit. */
    static boolean legacy18FromPackage(String craftPackage) {
        if (craftPackage == null || craftPackage.isEmpty()) return false;
        int dot = craftPackage.lastIndexOf('.');
        String ver = dot < 0 ? craftPackage : craftPackage.substring(dot + 1);
        return ver.startsWith("v1_8");
    }

    private static String craftPackageName() {
        try {
            return Bukkit.getServer().getClass().getPackage().getName();
        } catch (Exception ignored) {
            return null;
        }
    }
}
