package dev.iyanel.bedlamcore.gui;

import dev.iyanel.bedlamcore.game.GameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single source of truth for fixed shop layout, prices, currency, and descriptions. */
public final class ShopCatalog {
    private static final Map<String, Offer> BY_KEY = new LinkedHashMap<String, Offer>();

    static {
        add("Blocks", 19, "16 Wool", "Wool", 4, "IRON_INGOT", "Basic building block", "Colored to your team");
        add("Blocks", 20, "16 Hardened Clay", "Hardened Clay", 12, "IRON_INGOT", "Sturdier than wool");
        add("Blocks", 21, "4 Blast-Proof Glass", "Blast-Proof Glass", 12, "IRON_INGOT", "See-through defense");
        add("Blocks", 22, "12 End Stone", "End Stone", 24, "IRON_INGOT", "Tough island block");
        add("Blocks", 23, "8 Ladders", "Ladder", 4, "IRON_INGOT", "Climb enemy walls");
        add("Blocks", 24, "16 Oak Planks", "Wood", 4, "GOLD_INGOT", "Cheap bridging wood");
        add("Blocks", 25, "4 Obsidian", "Obsidian", 4, "EMERALD", "Blast-resistant cover");
        add("Blocks", 28, "8 Ice", "Ice", 8, "GOLD_INGOT", "Slippery flooring");

        add("Melee", 19, "Stone Sword", "Stone Sword", 10, "IRON_INGOT", "Replaces a weaker sword");
        add("Melee", 20, "Iron Sword", "Iron Sword", 7, "GOLD_INGOT", "Replaces a weaker sword");
        add("Melee", 21, "Diamond Sword", "Diamond Sword", 4, "EMERALD", "Replaces a weaker sword");
        add("Melee", 22, "Knockback Stick", "Knockback Stick", 5, "GOLD_INGOT", "Knockback I stick");

        add("Armor", 19, "Permanent Chainmail Armor", "Chainmail Armor", 40, "IRON_INGOT", "Permanent chainmail legs + boots");
        add("Armor", 20, "Permanent Iron Armor", "Iron Armor", 12, "GOLD_INGOT", "Permanent iron helmet + chest");
        add("Armor", 21, "Permanent Diamond Armor", "Diamond Armor", 6, "EMERALD", "Permanent diamond helmet + chest");

        add("Ranged", 19, "Bow", "Bow", 12, "GOLD_INGOT", "Unbreakable bow");
        add("Ranged", 20, "8 Arrows", "Arrows", 2, "GOLD_INGOT", "Ammunition");
        add("Ranged", 21, "Punch Bow", "Punch Bow", 24, "GOLD_INGOT", "Bow with Punch I");

        add("Potions", 19, "Speed II Potion (45 seconds)", "Speed II Potion (45 seconds)", 1, "EMERALD", "Speed II for 45 seconds");
        add("Potions", 20, "Jump V Potion (45 seconds)", "Jump V Potion (45 seconds)", 1, "EMERALD", "Jump Boost V for 45 seconds");
        add("Potions", 21, "Invisibility Potion (30 seconds)", "Invisibility Potion (30 seconds)", 2, "EMERALD", "Full invisibility for 30 seconds", "Armor hidden from enemies");

        add("Utility", 19, "Golden Apple", "Golden Apple", 3, "GOLD_INGOT", "Well-rounded healing");
        add("Utility", 20, "16 Snowballs", "Snowball", 16, "IRON_INGOT", "Slow projectiles");
        add("Utility", 21, "Fireball", "Fireball", 40, "IRON_INGOT", "Explosive knockback charge");
        add("Utility", 22, "TNT", "TNT", 4, "GOLD_INGOT", "Auto-ignites when placed");
        add("Utility", 23, "Ender Pearl", "Ender Pearl", 4, "EMERALD", "Teleport across the map");
        add("Utility", 24, "Water Bucket", "Water Bucket", 3, "GOLD_INGOT", "One-use water place");
        add("Utility", 25, "Magic Milk", "Magic Milk", 4, "GOLD_INGOT", "30 seconds of trap immunity");
        add("Utility", 28, "4 Sponges", "Sponge", 3, "GOLD_INGOT", "Soak up water");
        add("Utility", 29, "Bridge Egg", "Bridge Egg", 1, "EMERALD", "Throws a team-wool bridge", "along its flight path");
        add("Utility", 30, "Dream Defender", "Dream Defender", 120, "IRON_INGOT", "Team-friendly Iron Golem", "Guards nearby teammates for 4 minutes");
        add("Utility", 31, "Pop-up Tower", "Pop-up Tower", 24, "IRON_INGOT", "Right-click a block to deploy", "an instant team-wool tower");
    }

    private ShopCatalog() {
    }

    public static Offer offer(String key) {
        if ("Speed Potion".equals(key)) key = "Speed II Potion (45 seconds)";
        else if ("Jump Potion".equals(key)) key = "Jump V Potion (45 seconds)";
        else if ("Invisibility Potion".equals(key)) key = "Invisibility Potion (30 seconds)";
        return BY_KEY.get(key);
    }

    public static List<Offer> offers(String category) {
        List<Offer> matches = new ArrayList<Offer>();
        for (Offer offer : BY_KEY.values()) if (offer.category.equals(category)) matches.add(offer);
        return matches;
    }

    public static List<Offer> all() {
        return Collections.unmodifiableList(new ArrayList<Offer>(BY_KEY.values()));
    }

    /** Hypixel API favourites_2 id -> this catalog's persisted buy key. */
    public static String fromHypixelId(String id) {
        if (id == null || id.equals("null") || id.isEmpty()) return "";
        switch (id.toLowerCase()) {
            case "wool": return "16 Wool";
            case "hardened_clay": return "16 Hardened Clay";
            case "blast-proof_glass": return "4 Blast-Proof Glass";
            case "end_stone": return "12 End Stone";
            case "ladder": return "8 Ladders";
            case "oak_wood_planks": return "16 Oak Planks";
            case "obsidian": return "4 Obsidian";
            case "ice": return "8 Ice";
            case "stone_sword": return "Stone Sword";
            case "iron_sword": return "Iron Sword";
            case "diamond_sword": return "Diamond Sword";
            case "stick": case "knockback_stick": return "Knockback Stick";
            case "chainmail_boots": return "Permanent Chainmail Armor";
            case "iron_boots": return "Permanent Iron Armor";
            case "diamond_boots": return "Permanent Diamond Armor";
            case "bow": return "Bow";
            case "arrow": return "8 Arrows";
            case "punch_bow": return "Punch Bow";
            case "wooden_pickaxe": return "Wooden Pickaxe";
            case "wooden_axe": return "Wooden Axe";
            case "shears": return "Shears";
            case "speed_ii_potion_(45_seconds)": return "Speed II Potion (45 seconds)";
            case "jump_v_potion_(45_seconds)": return "Jump V Potion (45 seconds)";
            case "invisibility_potion_(30_seconds)": return "Invisibility Potion (30 seconds)";
            case "golden_apple": return "Golden Apple";
            case "snowball": return "16 Snowballs";
            case "fireball": return "Fireball";
            case "tnt": return "TNT";
            case "ender_pearl": return "Ender Pearl";
            case "water_bucket": return "Water Bucket";
            case "magic_milk": case "milk_bucket": return "Magic Milk";
            case "sponge": return "4 Sponges";
            case "bridge_egg": return "Bridge Egg";
            case "dream_defender": return "Dream Defender";
            case "popup_tower": case "pop-up_tower": return "Pop-up Tower";
            default: return "";
        }
    }

    public static String[] parseHypixelFavorites(String csv) {
        String[] result = new String[GameRules.FAVORITE_SLOTS];
        String[] ids = csv == null ? new String[0] : csv.split(",", -1);
        for (int i = 0; i < result.length; i++) result[i] = i < ids.length ? fromHypixelId(ids[i].trim()) : "";
        return result;
    }

    private static void add(String category, int slot, String key, String display, int cost, String currency, String... lore) {
        if (BY_KEY.put(key, new Offer(category, slot, key, display, cost, currency, lore)) != null) {
            throw new IllegalStateException("Duplicate shop key: " + key);
        }
    }

    public static final class Offer {
        public final String category;
        public final int slot;
        public final String key;
        public final String display;
        public final int cost;
        public final String currency;
        public final String[] lore;

        private Offer(String category, int slot, String key, String display, int cost, String currency, String[] lore) {
            this.category = category;
            this.slot = slot;
            this.key = key;
            this.display = display;
            this.cost = cost;
            this.currency = currency;
            this.lore = lore.clone();
        }
    }
}
