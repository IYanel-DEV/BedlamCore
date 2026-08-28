package dev.iyanel.bedlamcore.compat;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;

import java.lang.reflect.Method;

/**
 * Reflection-only blockstate applier for the flattened worlds (1.13+). The bundled waiting build is
 * captured on 1.8, where a stair's facing/half and a slab's species/half live in the legacy data byte.
 * On flattened servers {@code Block.setType} drops that byte, so stairs face north-bottom and slabs
 * lose their variant — the reported 26.2 defect. Here we rebuild the state from the legacy (name, data)
 * via a modern blockdata string and {@code setBlockData}, with no compile-time 1.13+ API. No-op on
 * pre-flattening servers, where {@code setTypeIdAndData} already carries the data.
 */
public final class BlockStates {
    private BlockStates() {}

    /** Facing from the low 2 bits of 1.8 stair data: 0=E, 1=W, 2=S, 3=N. */
    private static final String[] STAIR_FACING = {"east", "west", "south", "north"};

    private static final Method CREATE_BLOCK_DATA; // Bukkit.createBlockData(String)
    private static final Method SET_BLOCK_DATA;    // Block.setBlockData(BlockData, boolean)

    static {
        Method create = null, set = null;
        try {
            Class<?> blockData = Class.forName("org.bukkit.block.data.BlockData");
            create = Bukkit.class.getMethod("createBlockData", String.class);
            set = Block.class.getMethod("setBlockData", blockData, boolean.class);
        } catch (Throwable pre113) {
            create = null; set = null; // 1.8-1.12: the classes/methods do not exist; stay a no-op.
        }
        CREATE_BLOCK_DATA = create;
        SET_BLOCK_DATA = set;
    }

    /** True on 1.13+, where the blockdata API this helper needs exists. */
    public static boolean flattened() { return CREATE_BLOCK_DATA != null; }

    /**
     * On flattened servers, set {@code block} to the modern equivalent of the legacy (name, data) with
     * facing/half/species restored. Returns true if it fully handled the block (caller then skips the
     * plain setType path); false otherwise, including always on 1.8-1.12 (a no-op there).
     */
    public static boolean applyFlattened(Block block, String legacyName, byte data) {
        if (CREATE_BLOCK_DATA == null || block == null || legacyName == null) return false;
        String spec = blockData(legacyName, data);
        if (spec == null) return false;
        try {
            Object bd = CREATE_BLOCK_DATA.invoke(null, "minecraft:" + spec);
            SET_BLOCK_DATA.invoke(block, bd, Boolean.FALSE); // physics off; fences reconnect in a later pass
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String blockData(String name, byte data) {
        if ("WOOD_STAIRS".equals(name)) {
            String half = (data & 0x4) != 0 ? "top" : "bottom";
            return "oak_stairs[facing=" + STAIR_FACING[data & 0x3] + ",half=" + half + "]";
        }
        if ("WOOD_STEP".equals(name)) {
            // 1.8 wood-slab data: low 3 bits = species (0 oak, 1 spruce, ...), bit 0x8 = top half.
            String species = (data & 0x7) == 1 ? "spruce" : "oak";
            String type = (data & 0x8) != 0 ? "top" : "bottom";
            return species + "_slab[type=" + type + "]";
        }
        if ("WOOD_DOUBLE_STEP".equals(name)) return "oak_slab[type=double]";
        return null;
    }
}
