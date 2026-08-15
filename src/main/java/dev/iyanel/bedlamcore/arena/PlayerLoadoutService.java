package dev.iyanel.bedlamcore.arena;

import dev.iyanel.bedlamcore.compat.Enchantments;
import dev.iyanel.bedlamcore.compat.InvisArmor;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.game.GameRules;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/** Spawn kit, tools, armor, clear. Owned by ArenaManager. */
final class PlayerLoadoutService {
    private final ArenaManager manager;

    PlayerLoadoutService(ArenaManager manager) {
        this.manager = manager;
    }

    void prepareLobby(Player player) {
        clearPlayer(player);
        clearEnderChest(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(manager.arena().settings().waitingSpawn());
        player.getInventory().setItem(8, leaveItem("Leave Game"));
    }

    /** End screen: keep earned armor, remove every loose match item, and show navigation. */
    void prepareWinner(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        InvisArmor.clear(player);
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(armor);
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getInventory().setItem(7, playAgainItem());
        player.getInventory().setItem(8, leaveItem("Return to Lobby"));
    }

    static ItemStack playAgainItem() {
        return Items.named(new ItemStack(Material.PAPER), ChatColor.GREEN + "Play Again",
            ChatColor.GRAY + "Join another " + ChatColor.YELLOW + "Bed Wars" + ChatColor.GRAY + " game");
    }

    static ItemStack leaveItem(String name) {
        return Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + name,
            ChatColor.GRAY + "Leave this game");
    }

    void spawnPlayer(Player player, TeamColor team) {
        Arena arena = manager.arena();
        clearPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);
        resetMaxHealth(player);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFallDistance(0F);
        player.teleport(arena.settings().team(team).spawn());
        player.setPlayerListName(team.chatColor() + player.getName());
        ItemStack sword = Items.unbreakable(Items.named(new ItemStack(Items.material("WOODEN_SWORD", "WOOD_SWORD")), ChatColor.GREEN + "Wooden Sword"));
        if (arena.sharpness(team)) Enchantments.add(sword, 1, "SHARPNESS", "DAMAGE_ALL");
        player.getInventory().setItem(0, sword);
        equipArmor(player, team);
        giveOwnedTools(player);
        applyHaste(player, team);
        manager.plugin().views().updateAll();
    }

    /** Give permanent tools at current tiers (replace any existing pick/axe/shears). */
    void giveOwnedTools(Player player) {
        Arena arena = manager.arena();
        UUID uuid = player.getUniqueId();
        int pick = arena.pickaxeTier(uuid);
        if (pick > 0) replaceTool(player, true, toolPickaxe(pick));
        int axe = arena.axeTier(uuid);
        if (axe > 0) replaceTool(player, false, toolAxe(axe));
        if (arena.shearsOwned(uuid)) {
            removeMatching(player, "SHEARS");
            player.getInventory().addItem(Items.unbreakable(Items.named(new ItemStack(Items.material("SHEARS")),
                ChatColor.GREEN + "Permanent Shears", ChatColor.GRAY + "Kept on respawn")));
        }
    }

    static ItemStack toolPickaxe(int tier) {
        Material mat = pickaxeMaterial(tier);
        ItemStack item = Items.unbreakable(Items.named(new ItemStack(mat), ChatColor.GREEN + toolTierName(tier) + " Pickaxe",
            ChatColor.GRAY + "Upgradable", ChatColor.DARK_GRAY + "Loses 1 tier on death"));
        int eff = GameRules.pickaxeEfficiency(tier);
        if (eff > 0) Enchantments.add(item, eff, "DIG_SPEED", "EFFICIENCY");
        return item;
    }

    static ItemStack toolAxe(int tier) {
        Material mat = axeMaterial(tier);
        ItemStack item = Items.unbreakable(Items.named(new ItemStack(mat), ChatColor.GREEN + toolTierName(tier) + " Axe",
            ChatColor.GRAY + "Upgradable", ChatColor.DARK_GRAY + "Loses 1 tier on death"));
        int eff = GameRules.pickaxeEfficiency(tier);
        if (eff > 0) Enchantments.add(item, eff, "DIG_SPEED", "EFFICIENCY");
        return item;
    }

    void replaceTool(Player player, boolean pickaxe, ItemStack tool) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null) continue;
            String name = stack.getType().name();
            if (pickaxe ? GameRules.isPickaxe(name) : GameRules.isAxe(name)) {
                player.getInventory().setItem(i, tool);
                return;
            }
        }
        player.getInventory().addItem(tool);
    }

    void equipArmor(Player player, TeamColor team) {
        Arena arena = manager.arena();
        PlayerInventory inventory = player.getInventory();
        int tier = arena.armorTier(player.getUniqueId());
        ItemStack chest;
        ItemStack helmet;
        if (tier >= 2) {
            chest = Items.unbreakable(new ItemStack(Items.material("DIAMOND_CHESTPLATE")));
            helmet = Items.unbreakable(new ItemStack(Items.material("DIAMOND_HELMET")));
        } else if (tier >= 1) {
            chest = Items.unbreakable(new ItemStack(Items.material("IRON_CHESTPLATE")));
            helmet = Items.unbreakable(new ItemStack(Items.material("IRON_HELMET")));
        } else {
            chest = Items.unbreakable(team.leather("LEATHER_CHESTPLATE", "LEATHER_CHESTPLATE"));
            helmet = Items.unbreakable(team.leather("LEATHER_HELMET", "LEATHER_HELMET"));
        }
        ItemStack boots;
        ItemStack leggings;
        if (arena.chainmailOwned(player.getUniqueId())) {
            boots = Items.unbreakable(new ItemStack(Items.material("CHAINMAIL_BOOTS")));
            leggings = Items.unbreakable(new ItemStack(Items.material("CHAINMAIL_LEGGINGS")));
        } else {
            boots = Items.unbreakable(team.leather("LEATHER_BOOTS", "LEATHER_BOOTS"));
            leggings = Items.unbreakable(team.leather("LEATHER_LEGGINGS", "LEATHER_LEGGINGS"));
        }
        int protection = arena.protection(team);
        if (protection > 0) {
            Enchantments.add(boots, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            Enchantments.add(leggings, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            Enchantments.add(chest, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            Enchantments.add(helmet, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
        }
        int featherFalling = arena.cushionedBootsLevel(team);
        if (featherFalling > 0) Enchantments.add(boots, featherFalling, "PROTECTION_FALL", "FEATHER_FALLING");
        inventory.setBoots(boots);
        inventory.setLeggings(leggings);
        inventory.setChestplate(chest);
        inventory.setHelmet(helmet);
    }

    void applyHaste(Player player, TeamColor team) {
        int level = manager.arena().hasteLevel(team);
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        if (level > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, Integer.MAX_VALUE, level - 1), true);
    }

    void clearPlayer(Player player) {
        InvisArmor.clear(player);
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        try { player.setItemOnCursor(null); } catch (Throwable ignored) { }
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        try { player.setMaxHealth(20.0); } catch (Throwable ignored) { }
        player.setAllowFlight(false);
        player.setFlying(false);
        try {
            player.getClass().getMethod("setCollidable", boolean.class).invoke(player, true);
        } catch (Throwable ignored) { }
    }

    /** Personal ender only — never persists across matches. Team chest is separate (Arena.teamChest). */
    void clearEnderChest(Player player) {
        if (player != null) player.getEnderChest().clear();
    }

    private static void resetMaxHealth(Player player) {
        try {
            player.setMaxHealth(20.0);
        } catch (Throwable ignored) { }
    }

    private static void removeMatching(Player player, String needle) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && stack.getType().name().contains(needle)) player.getInventory().setItem(i, null);
        }
    }

    private static String toolTierName(int tier) {
        switch (tier) {
            case 2: return "Stone";
            case 3: return "Iron";
            case 4: return "Diamond";
            default: return "Wooden";
        }
    }

    private static Material pickaxeMaterial(int tier) {
        switch (tier) {
            case 2: return Items.material("STONE_PICKAXE");
            case 3: return Items.material("IRON_PICKAXE");
            case 4: return Items.material("DIAMOND_PICKAXE");
            default: return Items.material("WOODEN_PICKAXE", "WOOD_PICKAXE");
        }
    }

    private static Material axeMaterial(int tier) {
        switch (tier) {
            case 2: return Items.material("STONE_AXE");
            case 3: return Items.material("IRON_AXE");
            case 4: return Items.material("DIAMOND_AXE");
            default: return Items.material("WOODEN_AXE", "WOOD_AXE");
        }
    }
}
