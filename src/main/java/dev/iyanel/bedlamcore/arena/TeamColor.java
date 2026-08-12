package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.Items;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

public enum TeamColor {
    RED(ChatColor.RED, "Red", "RED_WOOL", (short) 14),
    BLUE(ChatColor.BLUE, "Blue", "BLUE_WOOL", (short) 11),
    GREEN(ChatColor.GREEN, "Green", "GREEN_WOOL", (short) 13),
    YELLOW(ChatColor.YELLOW, "Yellow", "YELLOW_WOOL", (short) 4);

    private final ChatColor chatColor;
    private final String displayName;
    private final String modernWool;
    private final short legacyData;

    TeamColor(ChatColor chatColor, String displayName, String modernWool, short legacyData) {
        this.chatColor = chatColor;
        this.displayName = displayName;
        this.modernWool = modernWool;
        this.legacyData = legacyData;
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

    public ItemStack wool(int amount) {
        return Items.stack(modernWool, "WOOL", amount, legacyData);
    }
}
