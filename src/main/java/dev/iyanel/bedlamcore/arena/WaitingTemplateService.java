package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.compat.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WaitingTemplateService {
    private static final String TOOL_NAME = "Waiting Build Selector";
    private static final int MAX_BLOCKS = 100000;

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
        player.getInventory().addItem(Items.named(new ItemStack(Items.material("GOLDEN_AXE", "GOLD_AXE")), ChatColor.GOLD + TOOL_NAME,
            ChatColor.GRAY + "Left-click point 1", ChatColor.GRAY + "Right-click point 2", ChatColor.YELLOW + "Include one diamond block as the spawn anchor"));
        player.sendMessage(ChatColor.YELLOW + "Select the full waiting building. It saves automatically after point 2 and must contain exactly one diamond block.");
    }

    public boolean isTool(ItemStack item) { return Items.name(item).equals(TOOL_NAME) && Items.hasLore(item, "Left-click point 1"); }

    public void select(Player player, Block block, boolean firstPoint) {
        Map<UUID, Location> target = firstPoint ? first : second;
        target.put(player.getUniqueId(), block.getLocation());
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
            Material material = Material.matchMaterial(spec.material);
            if (material == null) material = Material.AIR;
            replaced.add(BlockSnap.original(block, material, spec.data));
            block.setType(material);
            block.setData(spec.data, false);
        }
        return true;
    }

    /** PLAY-start fallback when no pre-paste snapshot exists (empty replaced list). */
    public void clear(Location waitingSpawn) {
        if (waitingSpawn == null || waitingSpawn.getWorld() == null || blocks.isEmpty()) return;
        Location origin = waitingSpawn.getBlock().getLocation().add(0, -1, 0);
        for (BlockSpec spec : blocks) {
            origin.clone().add(spec.x, spec.y, spec.z).getBlock().setType(Material.AIR);
        }
    }

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
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            Block block = one.getWorld().getBlockAt(x, y, z);
            captured.add(new BlockSpec(x - anchor.getBlockX(), y - anchor.getBlockY(), z - anchor.getBlockZ(), block.getType().name(), block.getData()));
        }
        blocks = captured;
        save();
        first.remove(player.getUniqueId());
        second.remove(player.getUniqueId());
        plugin.games().rebuildWaitingStructures();
        player.sendMessage(ChatColor.GREEN + "Saved the default waiting building with " + captured.size() + " blocks. The diamond block is the player spawn anchor.");
    }

    private void load() {
        if (!file.isFile()) return;
        for (Map<?, ?> entry : YamlConfiguration.loadConfiguration(file).getMapList("blocks")) {
            Object material = entry.get("material");
            if (material == null) continue;
            blocks.add(new BlockSpec(number(entry.get("x")), number(entry.get("y")), number(entry.get("z")), material.toString(), (byte) number(entry.get("data"))));
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
        try { yaml.save(file); }
        catch (IOException exception) { throw new IllegalStateException("Could not save waiting-build.yml", exception); }
    }

    private static int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }
    private static String coordinates(Location location) { return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(); }

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

        static BlockSnap original(Block block, Material pasteType, byte pasteData) {
            boolean baked = block.getType() == pasteType && block.getData() == pasteData;
            return baked ? new BlockSnap(block, Material.AIR, (byte) 0) : new BlockSnap(block, block.getType(), block.getData());
        }

        void restore() {
            if (world == null) return;
            World w = Bukkit.getWorld(world);
            if (w == null) return;
            Block block = w.getBlockAt(x, y, z);
            block.setType(type == null ? Material.AIR : type);
            block.setData(data, false);
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
