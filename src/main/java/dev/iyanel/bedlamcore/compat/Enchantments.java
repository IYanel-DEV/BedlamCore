package dev.iyanel.bedlamcore.compat;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class Enchantments {
    private Enchantments() {
    }

    public static void add(ItemStack item, int level, String... names) {
        for (String name : names) {
            Enchantment enchantment = Enchantment.getByName(name);
            if (enchantment != null) {
                item.addUnsafeEnchantment(enchantment, level);
                return;
            }
        }
    }
}
