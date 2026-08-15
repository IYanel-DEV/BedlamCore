package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.Items;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/** Hypixel Bed Wars classic eight: Red, Blue, Green, Yellow, Aqua, White, Pink, Gray. */
public enum TeamColor {
    RED(ChatColor.RED, "Red", "RED_WOOL", (short) 14, Color.fromRGB(170, 0, 0)),
    BLUE(ChatColor.BLUE, "Blue", "BLUE_WOOL", (short) 11, Color.fromRGB(0, 0, 170)),
    GREEN(ChatColor.GREEN, "Green", "GREEN_WOOL", (short) 13, Color.fromRGB(0, 170, 0)),
    YELLOW(ChatColor.YELLOW, "Yellow", "YELLOW_WOOL", (short) 4, Color.fromRGB(255, 255, 85)),
    AQUA(ChatColor.AQUA, "Aqua", "CYAN_WOOL", (short) 9, Color.fromRGB(85, 255, 255)),
    WHITE(ChatColor.WHITE, "White", "WHITE_WOOL", (short) 0, Color.fromRGB(255, 255, 255)),
    PINK(ChatColor.LIGHT_PURPLE, "Pink", "PINK_WOOL", (short) 6, Color.fromRGB(255, 85, 255)),
    GRAY(ChatColor.GRAY, "Gray", "GRAY_WOOL", (short) 7, Color.fromRGB(85, 85, 85));

    private final ChatColor chatColor;
    private final String displayName;
    private final String modernWool;
    private final short legacyData;
    private final Color leather;

    TeamColor(ChatColor chatColor, String displayName, String modernWool, short legacyData, Color leather) {
        this.chatColor = chatColor;
        this.displayName = displayName;
        this.modernWool = modernWool;
        this.legacyData = legacyData;
        this.leather = leather;
    }

    public String coloredName() {
        return chatColor + displayName;
    }

    public String displayName() {
        return displayName;
    }

    public ChatColor chatColor() {
        return chatColor;
    }

    public Color leatherColor() {
        return leather;
    }

    public ItemStack wool(int amount) {
        return Items.stack(modernWool, "WOOL", amount, legacyData);
    }

    /** Team stained glass (shop Blast-Proof Glass). */
    public ItemStack glass(int amount) {
        String modern = modernWool.replace("_WOOL", "_STAINED_GLASS");
        return Items.stack(modern, "STAINED_GLASS", amount, legacyData);
    }

    /** Place team wool at block (1.8 colored data + modern colored materials). */
    @SuppressWarnings("deprecation")
    public void placeAsBlock(Block block) {
        ItemStack stack = wool(1);
        block.setType(stack.getType());
        if (stack.getDurability() != 0) block.setData((byte) stack.getDurability());
    }

    /** Colored bed on modern; BED_BLOCK/BED on 1.8. Aqua → CYAN_BED. */
    public org.bukkit.Material bedMaterial() {
        String modern = this == AQUA ? "CYAN_BED" : name() + "_BED";
        return Items.material(modern, "BED_BLOCK", "BED");
    }

    public ItemStack leather(String modern, String legacy) {
        ItemStack item = new ItemStack(Items.material(modern, legacy));
        if (item.getItemMeta() instanceof LeatherArmorMeta) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(leather);
            item.setItemMeta(meta);
        }
        return item;
    }
}
