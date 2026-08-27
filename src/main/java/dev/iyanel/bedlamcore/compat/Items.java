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
            // 1. Direct enum lookup — most reliable on every server version
            try {
                @SuppressWarnings("deprecation")
                Material exact = Material.valueOf(name);
                return exact;
            } catch (IllegalArgumentException ignored) { }
            // 2. matchMaterial — handles legacy names, case-insensitive, "minecraft:" prefix, etc.
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
        if (type == null) return new ItemStack(potionMat, 1, legacyData);
        // Detect modern API: PotionMeta.setBasePotionType(PotionType)
        if (hasModernPotion()) {
            ItemStack item = new ItemStack(potionMat, 1);
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof PotionMeta)) return new ItemStack(potionMat, 1, legacyData);
            PotionMeta potion = (PotionMeta) meta;
            applyBasePotionType(potion, type);
            potion.addCustomEffect(new PotionEffect(type, durationTicks, amplifier), true);
            item.setItemMeta(potion);
            return item;
        }
        // Legacy 1.8: damage-based bottle color + custom effect
        ItemStack item = new ItemStack(potionMat, 1, legacyData);
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta)) return item;
        PotionMeta potion = (PotionMeta) meta;
        try { potion.setMainEffect(type); } catch (Throwable ignored) { }
        potion.addCustomEffect(new PotionEffect(type, durationTicks, amplifier), true);
        item.setItemMeta(potion);
        return item;
    }

    private static Boolean modernPotion;

    private static boolean hasModernPotion() {
        if (modernPotion != null) return modernPotion;
        try {
            Class<?> ptClass = Class.forName("org.bukkit.potion.PotionType");
            org.bukkit.inventory.meta.PotionMeta.class.getMethod("setBasePotionType", ptClass);
            modernPotion = Boolean.TRUE;
        } catch (Throwable t) {
            modernPotion = Boolean.FALSE;
        }
        return modernPotion;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyBasePotionType(PotionMeta potion, PotionEffectType type) {
        try {
            String name = type.getName();
            String baseName = name;
            if ("SPEED".equals(name)) baseName = "SWIFTNESS";
            else if ("JUMP_BOOST".equals(name)) baseName = "LEAPING";
            Class<?> ptClass = Class.forName("org.bukkit.potion.PotionType");
            Object baseType = Enum.valueOf((Class<Enum>) ptClass, baseName);
            potion.getClass().getMethod("setBasePotionType", ptClass).invoke(potion, baseType);
        } catch (Throwable ignored) {
        }
    }

    public static PotionEffectType potionType(String... names) {
        for (String name : names) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) return type;
        }
        return null;
    }
}
