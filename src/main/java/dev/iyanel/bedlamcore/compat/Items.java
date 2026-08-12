package dev.iyanel.bedlamcore.compat;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Items {
    private Items() {
    }

    public static Material material(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.STONE;
    }

    public static ItemStack stack(String modern, String legacy, int amount, short legacyData) {
        Material material = material(modern, legacy);
        ItemStack item = new ItemStack(material, amount);
        if (material.name().equals(legacy) && legacyData != 0) {
            item.setDurability(legacyData);
        }
        return item;
    }

    public static ItemStack named(ItemStack item, String name, String... lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(name);
        meta.setLore(lore.length == 0 ? Collections.<String>emptyList() : Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** Spigot 1.8+ / Paper unbreakable so swords and armor never show durability wear. */
    public static ItemStack unbreakable(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        try {
            meta.spigot().setUnbreakable(true);
        } catch (Throwable ignored) {
            try {
                meta.getClass().getMethod("setUnbreakable", boolean.class).invoke(meta, true);
            } catch (Throwable ignored2) { }
        }
        item.setItemMeta(meta);
        return item;
    }

    public static String name(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return "";
        }
        return ChatColor.stripColor(item.getItemMeta().getDisplayName());
    }

    public static boolean hasLore(ItemStack item, String text) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) {
            return false;
        }
        for (String line : lore) {
            if (ChatColor.stripColor(line).contains(text)) {
                return true;
            }
        }
        return false;
    }
}
