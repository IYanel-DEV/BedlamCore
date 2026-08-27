package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.util.AtomicFiles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WaitingTemplateService {
    private static final String TOOL_NAME = "Waiting Build Selector";
    private static final int MAX_BLOCKS = 100000;
    private static final String DEFAULT_RESOURCE = "waiting-build.yml";

    /** 1.8 block name → modern variants indexed by the legacy data byte (color/type lives in data). */
    private static final Map<String, String[]> LEGACY_VARIANTS = new HashMap<String, String[]>();
    /** 1.8 block name → single modern material; data only held facing/color that modern drops. */
    private static final Map<String, String> LEGACY_RENAMES = new HashMap<String, String>();

    static {
        LEGACY_RENAMES.put("IRON_FENCE", "IRON_BARS");
        LEGACY_RENAMES.put("DOUBLE_STEP", "SMOOTH_STONE");
        LEGACY_RENAMES.put("WALL_BANNER", "WHITE_WALL_BANNER");
        LEGACY_VARIANTS.put("WOOD", new String[] {"OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS", "JUNGLE_PLANKS", "ACACIA_PLANKS", "DARK_OAK_PLANKS"});
        LEGACY_VARIANTS.put("LOG", new String[] {"OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG"});
        LEGACY_VARIANTS.put("STEP", new String[] {"STONE_SLAB", "SANDSTONE_SLAB", "OAK_SLAB", "COBBLESTONE_SLAB", "BRICK_SLAB", "STONE_BRICK_SLAB", "NETHER_BRICK_SLAB", "QUARTZ_SLAB"});
        LEGACY_VARIANTS.put("STAINED_GLASS", new String[] {
            "WHITE_STAINED_GLASS", "ORANGE_STAINED_GLASS", "MAGENTA_STAINED_GLASS", "LIGHT_BLUE_STAINED_GLASS",
            "YELLOW_STAINED_GLASS", "LIME_STAINED_GLASS", "PINK_STAINED_GLASS", "GRAY_STAINED_GLASS",
            "LIGHT_GRAY_STAINED_GLASS", "CYAN_STAINED_GLASS", "PURPLE_STAINED_GLASS", "BLUE_STAINED_GLASS",
            "BROWN_STAINED_GLASS", "GREEN_STAINED_GLASS", "RED_STAINED_GLASS", "BLACK_STAINED_GLASS"
        });
        LEGACY_VARIANTS.put("CARPET", new String[] {
            "WHITE_CARPET", "ORANGE_CARPET", "MAGENTA_CARPET", "LIGHT_BLUE_CARPET",
            "YELLOW_CARPET", "LIME_CARPET", "PINK_CARPET", "GRAY_CARPET",
            "LIGHT_GRAY_CARPET", "CYAN_CARPET", "PURPLE_CARPET", "BLUE_CARPET",
            "BROWN_CARPET", "GREEN_CARPET", "RED_CARPET", "BLACK_CARPET"
        });
    }

    private final BedlamCore plugin;
    private final File file;
    private final Map<UUID, Location> first = new HashMap<UUID, Location>();
    private final Map<UUID, Location> second = new HashMap<UUID, Location>();
    private List<BlockSpec> blocks = new ArrayList<BlockSpec>();

    public WaitingTemplateService(BedlamCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "waiting-build.yml");
        load();
    }

    public void giveTool(Player player) {
        first.remove(player.getUniqueId());
        second.remove(player.getUniqueId());
        player.getInventory().addItem(Items.named(new ItemStack(Items.material("GOLDEN_AXE", "GOLD_AXE")), ChatColor.GOLD + TOOL_NAME,
            ChatColor.GRAY + "Left-click point 1", ChatColor.GRAY + "Right-click point 2", ChatColor.YELLOW + "Include one diamond block as the spawn anchor"));
        player.sendMessage(ChatColor.YELLOW + "Select the full waiting building. It saves automatically after point 2 and must contain exactly one diamond block.");
    }

    public boolean isTool(ItemStack item) { return Items.name(item).equals(TOOL_NAME) && Items.hasLore(item, "Left-click point 1"); }

    public void select(Player player, Block block, boolean firstPoint) {
        if (GameRules.isGlassBlock(block.getType().name())) {
            player.sendMessage(ChatColor.RED + "Click a real map block, not glass.");
            return;
        }
        Map<UUID, Location> target = firstPoint ? first : second;
        target.put(player.getUniqueId(), block.getLocation());
        markPoint(block);
        player.sendMessage(ChatColor.GREEN + "Waiting build point " + (firstPoint ? "1" : "2") + " set at " + coordinates(block.getLocation()) + ".");
        Location one = first.get(player.getUniqueId());
        Location two = second.get(player.getUniqueId());
        if (one != null && two != null) capture(player, one, two);
    }

    public boolean place(Location waitingSpawn, List<BlockSnap> replaced) {
        if (waitingSpawn == null || waitingSpawn.getWorld() == null || blocks.isEmpty()) return false;
        Location origin = waitingSpawn.getBlock().getLocation().add(0, -1, 0);
        for (BlockSpec spec : blocks) {
            Block block = origin.clone().add(spec.x, spec.y, spec.z).getBlock();
            Material material = pasteMaterial(spec.material, spec.data);
            replaced.add(BlockSnap.original(block, material, spec.data));
            setSilent(block, material, spec.data);
        }
        return true;
    }

    /** PLAY-start fallback when no pre-paste snapshot exists (empty replaced list). */
    public void clear(Location waitingSpawn) {
        if (waitingSpawn == null || waitingSpawn.getWorld() == null || blocks.isEmpty()) return;
        Location origin = waitingSpawn.getBlock().getLocation().add(0, -1, 0);
        for (BlockSpec spec : blocks) {
            setSilent(origin.clone().add(spec.x, spec.y, spec.z).getBlock(), Material.AIR, (byte) 0);
        }
    }

    /** After paste/restore: despawn dropped items in the cuboid. Never air template glass. */
    public void cleanupArea(Location center, List<BlockSnap> snaps) {
        if (center == null || center.getWorld() == null) return;
        int[] box = cuboid(center, snaps);
        sweepItems(center.getWorld(), box[0], box[1], box[2], box[3], box[4], box[5], 8);
    }

    @SuppressWarnings("deprecation")
    private void capture(Player player, Location one, Location two) {
        if (one.getWorld() == null || two.getWorld() == null || !one.getWorld().equals(two.getWorld())) {
            player.sendMessage(ChatColor.RED + "Both points must be in the same world.");
            return;
        }
        int minX = Math.min(one.getBlockX(), two.getBlockX());
        int minY = Math.min(one.getBlockY(), two.getBlockY());
        int minZ = Math.min(one.getBlockZ(), two.getBlockZ());
        int maxX = Math.max(one.getBlockX(), two.getBlockX());
        int maxY = Math.max(one.getBlockY(), two.getBlockY());
        int maxZ = Math.max(one.getBlockZ(), two.getBlockZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_BLOCKS) {
            player.sendMessage(ChatColor.RED + "That selection is too large. Maximum: " + MAX_BLOCKS + " blocks.");
            return;
        }
        Location anchor = null;
        int anchors = 0;
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            Block block = one.getWorld().getBlockAt(x, y, z);
            if (block.getType() == Material.DIAMOND_BLOCK) { anchor = block.getLocation(); anchors++; }
        }
        if (anchors != 1) {
            player.sendMessage(ChatColor.RED + "Selection must contain exactly one diamond block; found " + anchors + ".");
            return;
        }
        List<BlockSpec> captured = new ArrayList<BlockSpec>();
        int c1x = one.getBlockX(), c1y = one.getBlockY(), c1z = one.getBlockZ();
        int c2x = two.getBlockX(), c2y = two.getBlockY(), c2z = two.getBlockZ();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            // Axe clicks are markers only — never paste those two corner blocks into waiting builds.
            if ((x == c1x && y == c1y && z == c1z) || (x == c2x && y == c2y && z == c2z)) continue;
            Block block = one.getWorld().getBlockAt(x, y, z);
            String material = block.getType().name();
            // getData() triggers the CraftLegacy DataFixer on 1.13+ (server-freezing); the flattened
            // Material name already captures the variant, so store 0 there.
            byte data = isFlattened() ? 0 : block.getData();
            captured.add(new BlockSpec(x - anchor.getBlockX(), y - anchor.getBlockY(), z - anchor.getBlockZ(), material, data));
        }
        blocks = captured;
        save();
        first.remove(player.getUniqueId());
        second.remove(player.getUniqueId());
        stripSelector(player);
        plugin.games().rebuildWaitingStructures();
        player.sendMessage(ChatColor.GREEN + "Saved the default waiting building with " + captured.size() + " blocks. The diamond block is the player spawn anchor.");
    }

    private void load() {
        YamlConfiguration yaml = file.isFile() ? YamlConfiguration.loadConfiguration(file) : null;
        if (yaml == null || yaml.getMapList("blocks").isEmpty()) yaml = defaultYaml();
        if (yaml == null) return;
        for (Map<?, ?> entry : yaml.getMapList("blocks")) {
            Object material = entry.get("material");
            if (material == null) continue;
            String name = material.toString();
            byte data = (byte) number(entry.get("data"));
            blocks.add(new BlockSpec(number(entry.get("x")), number(entry.get("y")), number(entry.get("z")), name, data));
        }
    }

    /** Built-in default build (captured on 1.8) used until the operator selects their own via the axe tool. */
    private YamlConfiguration defaultYaml() {
        try {
            InputStream stream = plugin.getResource(DEFAULT_RESOURCE);
            if (stream == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (BlockSpec block : blocks) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("x", block.x); value.put("y", block.y); value.put("z", block.z);
            value.put("material", block.material); value.put("data", block.data & 0xff);
            values.add(value);
        }
        yaml.set("blocks", values);
        try { AtomicFiles.writeUtf8(file.toPath(), yaml.saveToString()); }
        catch (IOException exception) { throw new IllegalStateException("Could not save waiting-build.yml", exception); }
    }

    private static int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }
    private static String coordinates(Location location) { return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(); }

    private static void markPoint(Block block) {
        try {
            block.getWorld().playEffect(block.getLocation().add(0.5, 1.0, 0.5), Effect.ENDER_SIGNAL, 0);
        } catch (Throwable ignored) { }
    }

    private void stripSelector(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isTool(player.getInventory().getItem(i))) player.getInventory().setItem(i, null);
        }
    }

    /** Maps 1.8-named blocks (the bundled default) onto the running version; modern colors/types come from data. */
    private static Material pasteMaterial(String name, byte data) {
        if (name == null) return Material.AIR;
        String[] variants = LEGACY_VARIANTS.get(name);
        if (variants != null) {
            int index = data & 0xff;
            if (index < variants.length) {
                Material material = Material.matchMaterial(variants[index]);
                if (material != null) return material;
            }
        } else {
            String renamed = LEGACY_RENAMES.get(name);
            if (renamed != null) {
                Material material = Material.matchMaterial(renamed);
                if (material != null) return material;
            }
        }
        Material material = Material.matchMaterial(name);
        return material == null ? Material.AIR : material;
    }

    private int[] cuboid(Location center, List<BlockSnap> snaps) {
        if (snaps != null && !snaps.isEmpty()) {
            int minX = snaps.get(0).x, minY = snaps.get(0).y, minZ = snaps.get(0).z;
            int maxX = minX, maxY = minY, maxZ = minZ;
            for (BlockSnap snap : snaps) {
                minX = Math.min(minX, snap.x); minY = Math.min(minY, snap.y); minZ = Math.min(minZ, snap.z);
                maxX = Math.max(maxX, snap.x); maxY = Math.max(maxY, snap.y); maxZ = Math.max(maxZ, snap.z);
            }
            return new int[] {minX, minY, minZ, maxX, maxY, maxZ};
        }
        Location origin = center.getBlock().getLocation().add(0, -1, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        if (blocks.isEmpty()) {
            minX = -3; minY = -1; minZ = -3; maxX = 3; maxY = 4; maxZ = 3;
        } else {
            for (BlockSpec spec : blocks) {
                minX = Math.min(minX, spec.x); minY = Math.min(minY, spec.y); minZ = Math.min(minZ, spec.z);
                maxX = Math.max(maxX, spec.x); maxY = Math.max(maxY, spec.y); maxZ = Math.max(maxZ, spec.z);
            }
        }
        return new int[] {
            origin.getBlockX() + minX, origin.getBlockY() + minY, origin.getBlockZ() + minZ,
            origin.getBlockX() + maxX, origin.getBlockY() + maxY, origin.getBlockZ() + maxZ
        };
    }

    private static void sweepItems(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int pad) {
        double x0 = minX - pad, y0 = minY - pad, z0 = minZ - pad;
        double x1 = maxX + pad + 1, y1 = maxY + pad + 1, z1 = maxZ + pad + 1;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            Location at = item.getLocation();
            if (at.getX() >= x0 && at.getX() < x1 && at.getY() >= y0 && at.getY() < y1 && at.getZ() >= z0 && at.getZ() < z1) {
                item.remove();
            }
        }
    }

    private static Boolean flattened;

    /**
     * True on 1.13+ ("the flattening"), where byte block-data is deprecated. Calling legacy data APIs
     * ({@code Block.getData}, {@code Material.getId}, {@code setTypeIdAndData}) there routes through
     * {@code CraftLegacy}'s {@code DataFixerUpper}, whose first-call init blocks the server thread for
     * many seconds (Paper watchdog freeze during arena setup). Detect via the {@code LEGACY_*} enum
     * constants that only exist post-flattening. Pre-flattening (1.8–1.12) byte data stays valid/cheap.
     */
    static boolean isFlattened() {
        if (flattened != null) return flattened;
        try {
            flattened = Material.getMaterial("LEGACY_STONE") != null;
        } catch (Throwable ignored) {
            flattened = Boolean.FALSE;
        }
        return flattened;
    }

    @SuppressWarnings("deprecation")
    static void setSilent(Block block, Material type, byte data) {
        if (block == null) return;
        if (type == null) type = Material.AIR;
        if (!isFlattened()) {
            // Pre-flattening (1.8–1.12): legacy id+data places the sub-type (wool color, log axis, …).
            try {
                block.getClass().getMethod("setTypeIdAndData", int.class, byte.class, boolean.class)
                    .invoke(block, Integer.valueOf(type.getId()), Byte.valueOf(data), Boolean.FALSE);
                return;
            } catch (Throwable ignored) { }
        }
        // Flattened (1.13+): the Material already encodes the variant; never touch legacy id/data here.
        try {
            block.getClass().getMethod("setType", Material.class, boolean.class).invoke(block, type, Boolean.FALSE);
            return;
        } catch (Throwable ignored) { }
        BlockState state = block.getState();
        state.setType(type);
        state.update(true, false);
    }

    /** Material+data snapshot so PLAY restore survives stale BlockState / baked-in pastes. */
    static final class BlockSnap {
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final Material type;
        private final byte data;

        private BlockSnap(Block block, Material type, byte data) {
            World w = block.getWorld();
            this.world = w == null ? null : w.getName();
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
            this.type = type;
            this.data = data;
        }

        @SuppressWarnings("deprecation")
        static BlockSnap original(Block block, Material pasteType, byte pasteData) {
            Material type = block.getType();
            // getData() triggers the CraftLegacy DataFixer on 1.13+ (server-freezing); the flattened
            // Material already identifies the block, so skip legacy data there.
            byte data = isFlattened() ? 0 : block.getData();
            boolean baked = type == pasteType && (isFlattened() || data == pasteData);
            return baked ? new BlockSnap(block, Material.AIR, (byte) 0) : new BlockSnap(block, type, data);
        }

        void restore() {
            if (world == null) return;
            World w = Bukkit.getWorld(world);
            if (w == null) return;
            setSilent(w.getBlockAt(x, y, z), type == null ? Material.AIR : type, data);
        }
    }

    private static final class BlockSpec {
        private final int x;
        private final int y;
        private final int z;
        private final String material;
        private final byte data;

        private BlockSpec(int x, int y, int z, String material, byte data) {
            this.x = x; this.y = y; this.z = z; this.material = material; this.data = data;
        }
    }
}
