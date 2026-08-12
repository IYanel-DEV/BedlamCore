package dev.iyanel.bedlamcore.game;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class GameRules {
    public static final double GEN_PROTECT = 3.0;
    public static final double SPAWN_PROTECT = 4.0;
    public static final double FORGE_PROTECT = 3.0;
    public static final double SHOP_PROTECT = 2.0;
    public static final double DISPLAY_VIEW = 20.0;
    public static final double HEAL_POOL_RADIUS = 8.0;
    public static final int ARENA_BOUND_PAD = 40;
    /** Shop hologram title height above villager feet (Hypixel-like, just over head). */
    public static final double SHOP_HOLO_TITLE_Y = 2.05;
    public static final double SHOP_HOLO_SUB_Y = 1.75;
    public static final double CHEST_HOLO_Y = 1.15;
    public static final double TRAP_TRIGGER_RADIUS = 10.0;
    public static final int TRAP_QUEUE_MAX = 3;

    private GameRules() {
    }

    /** Hypixel-like: deposit resources/blocks; keep sword/armor/tools in hand. */
    public static boolean canFastDeposit(String materialName) {
        if (materialName == null || materialName.equals("AIR")) return false;
        if (isSword(materialName) || isArmor(materialName)) return false;
        if (materialName.contains("PICKAXE") || materialName.contains("AXE") || materialName.contains("SHEARS")
            || materialName.contains("HOE") || materialName.contains("BOW") || materialName.contains("SHOVEL")
            || materialName.contains("SPADE")) return false;
        return true;
    }

    public static <T> T leastPopulated(List<T> orderedTeams, Map<T, Integer> sizes) {
        T best = null;
        int bestSize = Integer.MAX_VALUE;
        for (T team : orderedTeams) {
            int size = sizes.containsKey(team) ? sizes.get(team) : 0;
            if (size < bestSize) {
                best = team;
                bestSize = size;
            }
        }
        return best;
    }

    public static boolean canRespawn(boolean bedAlive, boolean alreadyEliminated) {
        return bedAlive && !alreadyEliminated;
    }

    public static int generatorTier(int elapsedSeconds, int tierTwoSeconds, int tierThreeSeconds) {
        if (elapsedSeconds >= tierThreeSeconds) return 3;
        if (elapsedSeconds >= tierTwoSeconds) return 2;
        return 1;
    }

    /** Instant void kill when at or below waiting-spawn Y minus this drop. */
    public static double voidKillY(double waitingSpawnY) {
        return waitingSpawnY - 30.0;
    }

    public static boolean tooHigh(int blockY, int waitingSpawnY) {
        return blockY > waitingSpawnY;
    }

    public static boolean inRadius(double dx, double dy, double dz, double radius) {
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static int swordRank(String materialName) {
        if (materialName == null) return 0;
        if (materialName.contains("DIAMOND_SWORD")) return 3;
        if (materialName.contains("IRON_SWORD")) return 2;
        if (materialName.contains("STONE_SWORD")) return 1;
        if (materialName.contains("WOOD") && materialName.contains("SWORD")) return 0;
        if (materialName.endsWith("_SWORD")) return 0;
        return -1;
    }

    public static boolean isSword(String materialName) {
        return swordRank(materialName) >= 0;
    }

    public static boolean isArmor(String materialName) {
        if (materialName == null) return false;
        return materialName.endsWith("_HELMET") || materialName.endsWith("_CHESTPLATE")
            || materialName.endsWith("_LEGGINGS") || materialName.endsWith("_BOOTS");
    }

    public static int countSwords(ItemStack[] contents) {
        if (contents == null) return 0;
        int count = 0;
        for (ItemStack stack : contents) {
            if (stack != null && isSword(stack.getType().name())) count++;
        }
        return count;
    }

    /** Last sword stays; extras (2+) may be dropped for teammates. */
    public static boolean canDropSword(int swordCount) {
        return swordCount >= 2;
    }
}
