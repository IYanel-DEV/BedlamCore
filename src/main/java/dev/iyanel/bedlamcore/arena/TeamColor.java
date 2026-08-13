package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.Items;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public enum TeamColor {
    RED(ChatColor.RED, "Red", "RED_WOOL", (short) 14, Color.fromRGB(170, 0, 0)),
    BLUE(ChatColor.BLUE, "Blue", "BLUE_WOOL", (short) 11, Color.fromRGB(0, 0, 170)),
    GREEN(ChatColor.GREEN, "Green", "GREEN_WOOL", (short) 13, Color.fromRGB(0, 170, 0)),
    YELLOW(ChatColor.YELLOW, "Yellow", "YELLOW_WOOL", (short) 4, Color.fromRGB(255, 255, 85));

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

    /** Place team wool at block (1.8 colored data + modern colored materials). */
    @SuppressWarnings("deprecation")
    public void placeAsBlock(Block block) {
        ItemStack stack = wool(1);
        block.setType(stack.getType());
        if (stack.getDurability() != 0) block.setData((byte) stack.getDurability());
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
