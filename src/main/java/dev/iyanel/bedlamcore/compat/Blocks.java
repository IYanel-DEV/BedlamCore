package dev.iyanel.bedlamcore.compat;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Version-safe block writes. {@code Block.setData(byte)} exists only on 1.8-era servers and throws
 * {@link NoSuchMethodError} on 1.13+/Paper 26.x, so every legacy data write must funnel through here.
 */
public final class Blocks {
    private Blocks() {
    }

    /** 1.8 {@code Block.setData(byte)}; a no-op on modern servers where the method is gone. */
    public static void setLegacyData(Block block, byte data) {
        if (block == null) return;
        try {
            Block.class.getMethod("setData", byte.class).invoke(block, Byte.valueOf(data));
        } catch (Throwable ignored) {
            // Modern server: block state carries no legacy data byte; callers needing facing use setFacing.
        }
    }

    /**
     * Point a directional block (ladder, etc.) at {@code face}. Uses modern {@code BlockData} first,
     * then the 1.8 data byte. {@code block.setType(...)} must already have run.
     */
    public static void setFacing(Block block, BlockFace face, byte legacyData) {
        if (block == null) return;
        if (!setModernFacing(block, face)) setLegacyData(block, legacyData);
    }

    private static boolean setModernFacing(Block block, BlockFace face) {
        try {
            Class<?> directional = Class.forName("org.bukkit.block.data.Directional");
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            if (!directional.isInstance(data)) return false;
            directional.getMethod("setFacing", BlockFace.class).invoke(data, face);
            Class<?> blockData = Class.forName("org.bukkit.block.data.BlockData");
            block.getClass().getMethod("setBlockData", blockData).invoke(block, data);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
