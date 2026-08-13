package dev.iyanel.bedlamcore.compat;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Items {
    /** 1.8-safe Potions category icon: item id first, then modern name, then potion bottle. */
    public static final String[] POTIONS_TAB_MATERIALS = {"BREWING_STAND_ITEM", "BREWING_STAND", "POTION"};

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

    /** Drinkable potion with custom effect — works on 1.8.8 (legacy data for bottle color) and modern Paper. */
    @SuppressWarnings("deprecation")
    public static ItemStack drinkPotion(PotionEffectType type, int durationTicks, int amplifier, short legacyData) {
        Material potionMat = material("POTION");
        ItemStack item = new ItemStack(potionMat, 1, legacyData);
        if (type == null) return item;
        try {
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof PotionMeta)) return item;
            PotionMeta potion = (PotionMeta) meta;
            try {
                potion.setMainEffect(type);
            } catch (Throwable ignored) {
            }
            potion.addCustomEffect(new PotionEffect(type, durationTicks, amplifier), true);
            item.setItemMeta(potion);
        } catch (Throwable ignored) {
            // 1.8 edge: keep colored bottle even if PotionMeta path fails
        }
        return item;
    }

    public static PotionEffectType potionType(String... names) {
        for (String name : names) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) return type;
        }
        return null;
    }
}
