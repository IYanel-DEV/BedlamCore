package dev.iyanel.bedlamcore.game;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class GameRules {
    public static final double GEN_PROTECT = 3.0;
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
    /** Path cells (center of the 3-wide slice) one Bridge Egg may paint. */
    public static final int BRIDGE_EGG_MAX_PATH = 20;
    /** Fraction of max path that stays flat; after this the trail steps down. */
    public static final double BRIDGE_EGG_DIP_START = 0.6;
    /** Flight ticks before the egg trail stops (~2s). */
    public static final int BRIDGE_EGG_MAX_TICKS = 40;
    /** Straight-line flight cap in blocks (Hypixel-ish 15–25). */
    public static final double BRIDGE_EGG_MAX_DISTANCE = 20.0;
    public static final int TOKENS_WIN = 50;
    public static final int TOKENS_BED = 25;
    public static final int TOKENS_KILL = 5;
    public static final int TOKENS_FINAL_KILL = 10;
    public static final int TOKENS_PLAY = 10;
    public static final int XP_WIN = 100;
    public static final int XP_BED = 50;
    public static final int XP_KILL = 10;
    public static final int XP_FINAL_KILL = 25;
    public static final int XP_PLAY = 25;
    /** Flat XP per level (low-level Hypixel-like 5k bar). */
    public static final int XP_PER_LEVEL = 5000;
    public static final int XP_BAR_SLOTS = 10;
    /** 1.8 client silently fails to open a chest if the title is longer than this (including color codes). */
    public static final int INVENTORY_TITLE_MAX = 32;
    /** Shop drinkables (Hypixel-like durations). */
    public static final int POTION_SPEED_TICKS = 45 * 20;
    public static final int POTION_JUMP_TICKS = 45 * 20;
    public static final int POTION_INVIS_TICKS = 30 * 20;
    /** Amplifier 1 = Speed II; 4 = Jump Boost V. */
    public static final int POTION_SPEED_AMPLIFIER = 1;
    public static final int POTION_JUMP_AMPLIFIER = 4;
    public static final int POTION_SPEED_COST_EMERALD = 1;
    public static final int POTION_JUMP_COST_EMERALD = 1;
    public static final int POTION_INVIS_COST_EMERALD = 2;
    /** 1.8 PacketPlayOutEntityEquipment armor slots (0 = hand — never clear). */
    public static final int LEGACY_EQUIP_BOOTS = 1;
    public static final int LEGACY_EQUIP_LEGS = 2;
    public static final int LEGACY_EQUIP_CHEST = 3;
    public static final int LEGACY_EQUIP_HELMET = 4;
    public static final int RES_IRON = 0;
    public static final int RES_GOLD = 1;
    public static final int RES_DIAMOND = 2;
    public static final int RES_EMERALD = 3;

    /** Air-only so bridge eggs never overwrite map/beds/gens. */
    public static boolean isBridgeReplaceable(String materialName) {
        return materialName != null && (materialName.equals("AIR") || materialName.equals("CAVE_AIR")
            || materialName.equals("VOID_AIR"));
    }

    /** 1.8 chest GUIs are 27 or 54; size 45 is ContainerPlayer (crafting + armor + inv). */
    public static boolean isChestGuiSize(int size) {
        return size == 27 || size == 54;
    }

    public static String inventoryTitle(String title) {
        if (title == null) return "";
        return title.length() <= INVENTORY_TITLE_MAX ? title : title.substring(0, INVENTORY_TITLE_MAX);
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

    /** Match ores transferred on kill / void forge keep-roll (not wool/tools). */
    public static boolean isMatchOre(String materialName) {
        return materialName != null && (materialName.equals("IRON_INGOT") || materialName.equals("GOLD_INGOT")
            || materialName.equals("DIAMOND") || materialName.equals("EMERALD"));
    }

    public static int[] countMatchOres(ItemStack[] contents) {
        int[] counts = new int[4];
        if (contents == null) return counts;
        for (ItemStack stack : contents) {
            if (stack == null) continue;
            String name = stack.getType().name();
            if (name.equals("IRON_INGOT")) counts[RES_IRON] += stack.getAmount();
            else if (name.equals("GOLD_INGOT")) counts[RES_GOLD] += stack.getAmount();
            else if (name.equals("DIAMOND")) counts[RES_DIAMOND] += stack.getAmount();
            else if (name.equals("EMERALD")) counts[RES_EMERALD] += stack.getAmount();
        }
        return counts;
    }

    public static boolean hasMatchOres(int[] counts) {
        return counts != null && (counts[RES_IRON] > 0 || counts[RES_GOLD] > 0
            || counts[RES_DIAMOND] > 0 || counts[RES_EMERALD] > 0);
    }

    /** Killer share: iron 100%, gold/diamond/emerald floor 50%. */
    public static int[] killLootShares(int[] totals) {
        int[] shares = new int[4];
        if (totals == null) return shares;
        shares[RES_IRON] = Math.max(0, totals[RES_IRON]);
        shares[RES_GOLD] = Math.max(0, totals[RES_GOLD] / 2);
        shares[RES_DIAMOND] = Math.max(0, totals[RES_DIAMOND] / 2);
        shares[RES_EMERALD] = Math.max(0, totals[RES_EMERALD] / 2);
        return shares;
    }

    /** Hypixel-style: "+64 Iron! +4 Gold! +1 Diamond! +1 Emerald!" */
    public static String killLootKillerMessage(int[] shares) {
        if (!hasMatchOres(shares)) return null;
        StringBuilder sb = new StringBuilder();
        if (shares[RES_IRON] > 0) sb.append("\u00A7a+").append(shares[RES_IRON]).append(" Iron!");
        if (shares[RES_GOLD] > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("\u00A76+").append(shares[RES_GOLD]).append(" Gold!");
        }
        if (shares[RES_DIAMOND] > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("\u00A7b+").append(shares[RES_DIAMOND]).append(" Diamond!");
        }
        if (shares[RES_EMERALD] > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("\u00A72+").append(shares[RES_EMERALD]).append(" Emerald!");
        }
        return sb.toString();
    }

    public static String killLootVictimMessage(String killerName) {
        return "\u00A7c" + killerName + " killed you and stole your resources!";
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

    /**
     * Team blocks victory only if it has living players, or a bed and was assigned at least one
     * player this match. Empty force-start filler teams (bed up, never occupied) do not contend.
     */
    public static boolean teamContending(boolean bedAlive, int livingPlayers, boolean wasOccupiedThisMatch) {
        return livingPlayers > 0 || (bedAlive && wasOccupiedThisMatch);
    }

    /** Win / end when at most one contending team remains. Do not call at match start (solo force-start). */
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

    /** Selector must click a real map block — not glass / panes used as visual corners. */
    public static boolean isGlassBlock(String materialName) {
        return materialName != null && materialName.contains("GLASS");
    }

    /** Perpendicular X offset for a 3-wide wool trail (center ± this, 0). */
    public static int bridgeSideX(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? 0 : 1;
    }

    /** Perpendicular Z offset for a 3-wide wool trail. */
    public static int bridgeSideZ(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? 1 : 0;
    }

    /**
     * Extra blocks below egg y-1. 0 until 60% of max path, then +1 every 2 placements
     * so only the last ~5–8 columns ramp down (not a diagonal from the start).
     */
    public static int bridgeEggEndDip(int pathIndex, int maxPath) {
        if (pathIndex < 1 || maxPath < 1) return 0;
        int start = (int) Math.ceil(maxPath * BRIDGE_EGG_DIP_START);
        if (pathIndex <= start) return 0;
        return (pathIndex - start + 1) / 2;
    }

    /** Level 1 at 0 XP; +1 per XP_PER_LEVEL. */
    public static int levelFromXp(int xp) {
        if (xp < 0) xp = 0;
        return 1 + xp / XP_PER_LEVEL;
    }

    public static int xpIntoLevel(int xp) {
        if (xp < 0) xp = 0;
        return xp % XP_PER_LEVEL;
    }

    public static int xpBarFilled(int xpInto, int slots) {
        if (slots <= 0 || xpInto <= 0) return 0;
        if (xpInto >= XP_PER_LEVEL) return slots;
        int filled = (xpInto * slots + XP_PER_LEVEL / 2) / XP_PER_LEVEL;
        return filled > slots ? slots : filled;
    }

    /** Hypixel-like 3.4k / 5k; whole thousands drop the decimal. */
    public static String compactXp(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n % 1000 == 0) return (n / 1000) + "k";
        int tenths = (n * 10) / 1000;
        return (tenths / 10) + "." + (tenths % 10) + "k";
    }

    public static String commas(int n) {
        return String.format(java.util.Locale.US, "%,d", n);
    }

    /**
     * Hypixel-layout kill line. Colored names are pre-formatted; gray verbs use §7.
     * modes: void / void_kill / shot / kill / die
     */
    public static String killMessage(String victimColored, String killerColored, String mode, boolean finalKill) {
        String msg;
        if ("void_kill".equals(mode) && killerColored != null) {
            msg = victimColored + "\u00A77 was knocked into the void by " + killerColored + "\u00A77.";
        } else if ("void".equals(mode) || "void_kill".equals(mode)) {
            msg = victimColored + "\u00A77 fell into the void.";
        } else if (killerColored == null || "die".equals(mode)) {
            msg = victimColored + "\u00A77 died.";
        } else if ("shot".equals(mode)) {
            msg = victimColored + "\u00A77 was shot by " + killerColored + "\u00A77.";
        } else {
            msg = victimColored + "\u00A77 was killed by " + killerColored + "\u00A77.";
        }
        if (finalKill) msg += " \u00A7c\u00A7lFINAL KILL!";
        return msg;
    }

    /** Hypixel-layout bed break: BED DESTRUCTION > Team's Bed > Destroyed by Player */
    public static String bedBreakMessage(String teamColoredName, String breakerColored) {
        return "\u00A7f\u00A7lBED DESTRUCTION > " + teamColoredName + "\u00A7f\u00A7l's Bed\u00A77 > Destroyed by " + breakerColored;
    }
}
