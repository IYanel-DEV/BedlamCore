package dev.iyanel.bedlamcore.game;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class GameRules {
    public static final double GEN_PROTECT = 3.0;
    public static final double SPAWN_PROTECT = 4.0;
    public static final double FORGE_PROTECT = 3.0;
    /** Horizontal share radius (Hypixel-like: at forge or blocked/half away). */
    public static final double FORGE_SHARE_RADIUS = 2.5;
    /** Closer than this = standing on/at forge (default pickup sound). */
    public static final double FORGE_STANDING_RADIUS = 1.2;
    /** Vertical slack when checking forge share range. */
    public static final double FORGE_SHARE_Y = 3.0;
    /** Extra Y above forge block top for ground fallback drops (lower than mid gens). */
    public static final double FORGE_DROP_Y = 0.15;
    /** L2 rare diamond/emerald chances per forge iron/gold tick. */
    public static final double FORGE_L2_DIAMOND = 0.02;
    public static final double FORGE_L2_EMERALD = 0.01;
    /** L3+ slightly higher but still rare. */
    public static final double FORGE_L3_DIAMOND = 0.04;
    public static final double FORGE_L3_EMERALD = 0.025;
    public static final double SHOP_PROTECT = 2.0;
    public static final double DISPLAY_VIEW = 20.0;
    public static final double HEAL_POOL_RADIUS = 8.0;
    public static final int ARENA_BOUND_PAD = 40;
    /**
     * Hologram stand spawn Y (feet). Marker nametag sits near stand — tune so text is
     * just above villager head / chest top / gen pin (not through entity, not floating empty).
     */
    public static final double SHOP_HOLO_TITLE_Y = 2.25;
    public static final double SHOP_HOLO_SUB_Y = 1.95;
    /** Marker feet just above chest top (nametag sits on feet). */
    public static final double CHEST_HOLO_Y = 1.1;
    /** Full-size pin stand feet; helmet ~2.5 above gen floor (Hypixel-like). */
    public static final double GEN_STAND_Y = 2.5;
    public static final double GEN_HOLO_TITLE_Y = 3.15;
    public static final double GEN_HOLO_TIER_Y = 2.85;
    /** Lobby queue lines match shop spacing above NPC head. */
    public static final double LOBBY_HOLO_TITLE_Y = 2.25;
    public static final double LOBBY_HOLO_SUB_Y = 1.95;
    public static final double LOBBY_HOLO_INFO_Y = 1.65;
    public static final double TRAP_TRIGGER_RADIUS = 10.0;
    public static final int TRAP_QUEUE_MAX = 3;
    /** Brief re-arm delay after a trap fires (ticks). */
    public static final int TRAP_COOLDOWN_TICKS = 40;
    /** Wood=1 … Diamond=4; 0 = never purchased. */
    public static final int TOOL_TIER_MAX = 4;
    /** Max wool blocks one Bridge Egg may place along its flight. */
    public static final int BRIDGE_EGG_MAX_BLOCKS = 40;

    /** Air-only so bridge eggs never overwrite map/beds/gens. */
    public static boolean isBridgeReplaceable(String materialName) {
        return materialName != null && (materialName.equals("AIR") || materialName.equals("CAVE_AIR")
            || materialName.equals("VOID_AIR"));
    }

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

    /** Team still in the match while bed is up or any living player remains (empty force-start teams count via bed). */
    public static boolean teamContending(boolean bedAlive, int livingPlayers) {
        return bedAlive || livingPlayers > 0;
    }

    /** Win / end when at most one contending team remains. */
    public static boolean shouldEndMatch(int contendingTeams) {
        return contendingTeams <= 1;
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

    public static boolean forgeHorizontalInRange(double dx, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    public static boolean forgeShareInRange(double dx, double dy, double dz) {
        return Math.abs(dy) <= FORGE_SHARE_Y && forgeHorizontalInRange(dx, dz, FORGE_SHARE_RADIUS);
    }

    public static boolean forgeStandingInRange(double dx, double dz) {
        return forgeHorizontalInRange(dx, dz, FORGE_STANDING_RADIUS);
    }

    public static double forgeDiamondChance(int forgeLevel) {
        if (forgeLevel >= 3) return FORGE_L3_DIAMOND;
        if (forgeLevel >= 2) return FORGE_L2_DIAMOND;
        return 0;
    }

    public static double forgeEmeraldChance(int forgeLevel) {
        if (forgeLevel >= 3) return FORGE_L3_EMERALD;
        if (forgeLevel >= 2) return FORGE_L2_EMERALD;
        return 0;
    }

    /** Pure chance check for GameRulesCheck / forge bonus rolls. */
    public static boolean forgeBonusHits(double chance, double roll) {
        return chance > 0 && roll >= 0 && roll < chance;
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

    public static int clampToolTier(int tier) {
        if (tier < 0) return 0;
        if (tier > TOOL_TIER_MAX) return TOOL_TIER_MAX;
        return tier;
    }

    /** After first purchase, death drops one tier but never below wooden (1). */
    public static int toolTierAfterDeath(int tier) {
        if (tier <= 0) return 0;
        return Math.max(1, tier - 1);
    }

    /** Next shop tier to offer, or -1 when already maxed. */
    public static int nextToolTier(int current) {
        int tier = clampToolTier(current);
        return tier >= TOOL_TIER_MAX ? -1 : tier + 1;
    }

    /** Queue size 0→1 diamond, 1→2, 2→3 (Hypixel-like escalate). */
    public static int trapDiamondCost(int queueSize) {
        if (queueSize <= 0) return 1;
        if (queueSize == 1) return 2;
        return 3;
    }

    public static int pickaxeEfficiency(int tier) {
        if (tier >= 4) return 3;
        if (tier >= 3) return 2;
        if (tier >= 1) return 1;
        return 0;
    }

    public static boolean isPickaxe(String materialName) {
        return materialName != null && materialName.contains("PICKAXE");
    }

    public static boolean isAxe(String materialName) {
        return materialName != null && materialName.contains("AXE") && !materialName.contains("PICKAXE");
    }
}
