package dev.iyanel.bedlamcore.gui;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Enchantments;
import dev.iyanel.bedlamcore.compat.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public final class GuiController {
    public static final String MAIN_TITLE = ChatColor.DARK_GRAY + "Bedlam Menu";
    public static final String SETUP_TITLE = ChatColor.DARK_GRAY + "Arena Setup";
    public static final String SHOP_TITLE = ChatColor.DARK_GRAY + "Item Shop";
    public static final String UPGRADES_TITLE = ChatColor.DARK_GRAY + "Team Upgrades";
    private final BedlamCore plugin;

    public GuiController(BedlamCore plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_TITLE);
        inventory.setItem(11, Items.named(new ItemStack(Material.EMERALD), ChatColor.GREEN + "Quick Join", ChatColor.GRAY + "Join the Bedlam arena"));
        inventory.setItem(15, Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + "Leave Game"));
        if (player.hasPermission("bedlam.admin")) {
            inventory.setItem(22, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Arena Setup", ChatColor.GRAY + "Configure without commands"));
        }
        player.openInventory(inventory);
    }

    public void openSetup(Player player) {
        if (!player.hasPermission("bedlam.admin")) return;
        Inventory inventory = Bukkit.createInventory(null, 54, SETUP_TITLE);
        inventory.setItem(0, setupItem(Material.NETHER_STAR, "Set Lobby", plugin.settings().lobby() != null));
        inventory.setItem(1, setupItem(Items.material("ENDER_EYE", "EYE_OF_ENDER"), "Set Spectator Spawn", plugin.settings().spectator() != null));
        int[] slots = {10, 12, 14, 16};
        TeamColor[] colors = TeamColor.values();
        for (int i = 0; i < colors.length; i++) {
            boolean complete = plugin.settings().team(colors[i]).complete();
            inventory.setItem(slots[i], Items.named(colors[i].wool(1), colors[i].chatColor() + "Configure " + colors[i].displayName(),
                complete ? ChatColor.GREEN + "Complete" : ChatColor.YELLOW + "Needs setup"));
        }
        inventory.setItem(30, Items.named(new ItemStack(Material.DIAMOND), ChatColor.AQUA + "Add Diamond Generator",
            ChatColor.GRAY + "Current count: " + plugin.settings().diamondGenerators().size(), ChatColor.GRAY + "Uses your current location"));
        inventory.setItem(32, Items.named(new ItemStack(Material.EMERALD), ChatColor.GREEN + "Add Emerald Generator",
            ChatColor.GRAY + "Current count: " + plugin.settings().emeraldGenerators().size(), ChatColor.GRAY + "Uses your current location"));
        List<String> missing = plugin.settings().validate();
        inventory.setItem(49, Items.named(new ItemStack(missing.isEmpty() ? Material.SLIME_BALL : Material.BARRIER),
            missing.isEmpty() ? ChatColor.GREEN + "Validate & Save" : ChatColor.RED + "Validate & Save",
            missing.isEmpty() ? ChatColor.GREEN + "Ready to play" : ChatColor.GRAY + "Missing: " + join(missing)));
        player.openInventory(inventory);
    }

    private void openTeamSetup(Player player, TeamColor team) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Setup: " + team.name());
        ArenaSettings.TeamSettings settings = plugin.settings().team(team);
        inventory.setItem(10, setupItem(Material.ARMOR_STAND, "Set Team Spawn", settings.spawn() != null));
        inventory.setItem(11, setupItem(Items.material("RED_BED", "BED"), "Set Bed (look at it)", settings.bed() != null));
        inventory.setItem(12, setupItem(Material.IRON_INGOT, "Set Forge", settings.forge() != null));
        inventory.setItem(14, setupItem(Material.CHEST, "Set Item Shop", settings.itemShop() != null));
        inventory.setItem(15, setupItem(Items.material("ENCHANTING_TABLE", "ENCHANTMENT_TABLE"), "Set Upgrade Shop", settings.upgradeShop() != null));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        player.openInventory(inventory);
    }

    public void openShop(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 45, SHOP_TITLE);
        inventory.setItem(10, offer(TeamColor.RED.wool(16), "16 Wool", 4, "Iron"));
        inventory.setItem(11, offer(new ItemStack(Items.material("STONE_SWORD")), "Stone Sword", 10, "Iron"));
        inventory.setItem(12, offer(new ItemStack(Items.material("IRON_SWORD")), "Iron Sword", 7, "Gold"));
        inventory.setItem(13, offer(new ItemStack(Items.material("IRON_CHESTPLATE")), "Permanent Iron Armor", 12, "Gold"));
        inventory.setItem(14, offer(Items.stack("OAK_PLANKS", "WOOD", 16, (short) 0), "16 Oak Planks", 4, "Gold"));
        inventory.setItem(15, offer(new ItemStack(Items.material("LADDER"), 8), "8 Ladders", 4, "Iron"));
        inventory.setItem(16, offer(new ItemStack(Items.material("GOLDEN_APPLE", "GOLDEN_APPLE")), "Golden Apple", 3, "Gold"));
        inventory.setItem(20, offer(new ItemStack(Material.TNT), "TNT", 4, "Gold"));
        inventory.setItem(21, offer(new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")), "Fireball", 40, "Iron"));
        inventory.setItem(22, offer(new ItemStack(Items.material("ENDER_PEARL")), "Ender Pearl", 4, "Emerald"));
        inventory.setItem(23, offer(new ItemStack(Items.material("BOW")), "Bow", 12, "Gold"));
        inventory.setItem(24, offer(new ItemStack(Items.material("ARROW"), 8), "8 Arrows", 2, "Gold"));
        inventory.setItem(25, offer(new ItemStack(Items.material("WATER_BUCKET")), "Water Bucket", 3, "Gold"));
        player.openInventory(inventory);
    }

    public void openUpgrades(Player player) {
        Arena arena = plugin.arenaManager().arena();
        TeamColor team = arena.team(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 27, UPGRADES_TITLE);
        inventory.setItem(11, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Sharpened Swords",
            arena.sharpness(team) ? ChatColor.GREEN + "Purchased" : ChatColor.GRAY + "Cost: 4 Diamond"));
        int level = arena.protection(team);
        int cost = new int[] {2, 4, 8, 16}[Math.min(level, 3)];
        inventory.setItem(15, Items.named(new ItemStack(Material.IRON_CHESTPLATE), ChatColor.AQUA + "Reinforced Armor " + roman(level + 1),
            level >= 4 ? ChatColor.GREEN + "Maximum level" : ChatColor.GRAY + "Cost: " + cost + " Diamond"));
        player.openInventory(inventory);
    }

    public void click(Player player, String title, ItemStack clicked) {
        String name = Items.name(clicked);
        if (title.equals(MAIN_TITLE)) {
            if (name.equals("Quick Join")) plugin.arenaManager().join(player);
            else if (name.equals("Leave Game")) plugin.arenaManager().leave(player);
            else if (name.equals("Arena Setup")) openSetup(player);
            return;
        }
        if (title.equals(SETUP_TITLE)) {
            handleSetup(player, name);
            return;
        }
        if (ChatColor.stripColor(title).startsWith("Setup: ")) {
            TeamColor team;
            try { team = TeamColor.valueOf(ChatColor.stripColor(title).substring(7)); }
            catch (IllegalArgumentException exception) { return; }
            handleTeamSetup(player, team, name);
            return;
        }
        if (title.equals(SHOP_TITLE)) buy(player, name);
        else if (title.equals(UPGRADES_TITLE)) upgrade(player, name);
    }

    private void handleSetup(Player player, String name) {
        if (!player.hasPermission("bedlam.admin")) return;
        if (name.equals("Set Lobby")) plugin.settings().lobby(player.getLocation());
        else if (name.equals("Set Spectator Spawn")) plugin.settings().spectator(player.getLocation());
        else if (name.equals("Add Diamond Generator")) plugin.settings().diamondGenerators().add(player.getLocation());
        else if (name.equals("Add Emerald Generator")) plugin.settings().emeraldGenerators().add(player.getLocation());
        else if (name.equals("Validate & Save")) {
            plugin.saveSettings();
            List<String> missing = plugin.settings().validate();
            player.sendMessage(missing.isEmpty() ? ChatColor.GREEN + "Arena saved and ready." : ChatColor.RED + "Missing: " + join(missing));
        } else {
            for (TeamColor team : TeamColor.values()) {
                if (name.equals("Configure " + team.displayName())) {
                    openTeamSetup(player, team);
                    return;
                }
            }
        }
        plugin.saveSettings();
        openSetup(player);
    }

    private void handleTeamSetup(Player player, TeamColor team, String name) {
        ArenaSettings.TeamSettings settings = plugin.settings().team(team);
        if (name.equals("Set Team Spawn")) settings.spawn(player.getLocation());
        else if (name.equals("Set Forge")) settings.forge(player.getLocation());
        else if (name.equals("Set Item Shop")) settings.itemShop(player.getLocation());
        else if (name.equals("Set Upgrade Shop")) settings.upgradeShop(player.getLocation());
        else if (name.equals("Set Bed (look at it)")) {
            Block target = targetBlock(player, 6);
            if (target == null || !target.getType().name().contains("BED")) {
                player.sendMessage(ChatColor.RED + "Look directly at a bed within six blocks.");
                return;
            }
            settings.bed(target.getLocation());
        } else if (name.equals("Back")) {
            openSetup(player);
            return;
        }
        plugin.saveSettings();
        openTeamSetup(player, team);
    }

    private void buy(Player player, String name) {
        Arena arena = plugin.arenaManager().arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING) return;
        if (name.equals("16 Wool") && pay(player, Material.IRON_INGOT, 4)) give(player, team.wool(16));
        else if (name.equals("Stone Sword") && pay(player, Material.IRON_INGOT, 10)) give(player, enchantedSword(Items.material("STONE_SWORD"), arena.sharpness(team)));
        else if (name.equals("Iron Sword") && pay(player, Material.GOLD_INGOT, 7)) give(player, enchantedSword(Material.IRON_SWORD, arena.sharpness(team)));
        else if (name.equals("Permanent Iron Armor") && pay(player, Material.GOLD_INGOT, 12)) {
            arena.ironArmor().add(player.getUniqueId());
            plugin.arenaManager().equipArmor(player, team);
        }
        else if (name.equals("16 Oak Planks") && pay(player, Material.GOLD_INGOT, 4)) give(player, Items.stack("OAK_PLANKS", "WOOD", 16, (short) 0));
        else if (name.equals("8 Ladders") && pay(player, Material.IRON_INGOT, 4)) give(player, new ItemStack(Material.LADDER, 8));
        else if (name.equals("Golden Apple") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Items.material("GOLDEN_APPLE")));
        else if (name.equals("TNT") && pay(player, Material.GOLD_INGOT, 4)) give(player, new ItemStack(Material.TNT));
        else if (name.equals("Fireball") && pay(player, Material.IRON_INGOT, 40)) give(player, new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")));
        else if (name.equals("Ender Pearl") && pay(player, Material.EMERALD, 4)) give(player, new ItemStack(Material.ENDER_PEARL));
        else if (name.equals("Bow") && pay(player, Material.GOLD_INGOT, 12)) give(player, new ItemStack(Material.BOW));
        else if (name.equals("8 Arrows") && pay(player, Material.GOLD_INGOT, 2)) give(player, new ItemStack(Material.ARROW, 8));
        else if (name.equals("Water Bucket") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Material.WATER_BUCKET));
    }

    private void upgrade(Player player, String name) {
        Arena arena = plugin.arenaManager().arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null) return;
        if (name.equals("Sharpened Swords") && !arena.sharpness(team) && pay(player, Material.DIAMOND, 4)) {
            arena.sharpness(team, true);
            for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) enchantHeldSwords(member);
        } else if (name.startsWith("Reinforced Armor") && arena.protection(team) < 4) {
            int level = arena.protection(team);
            int cost = new int[] {2, 4, 8, 16}[level];
            if (pay(player, Material.DIAMOND, cost)) {
                arena.protection(team, level + 1);
                for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) plugin.arenaManager().equipArmor(member, team);
            }
        }
        openUpgrades(player);
    }

    private boolean pay(Player player, Material currency, int amount) {
        if (!player.getInventory().containsAtLeast(new ItemStack(currency), amount)) {
            player.sendMessage(ChatColor.RED + "You do not have enough " + currency.name().toLowerCase().replace('_', ' ') + ".");
            return false;
        }
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType() != currency) continue;
            int taken = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            remaining -= taken;
            if (stack.getAmount() == 0) player.getInventory().setItem(slot, null);
        }
        player.sendMessage(ChatColor.GREEN + "Purchased!");
        return true;
    }

    private static void give(Player player, ItemStack item) {
        java.util.Map<Integer, ItemStack> excess = player.getInventory().addItem(item);
        for (ItemStack stack : excess.values()) player.getWorld().dropItemNaturally(player.getLocation(), stack);
    }

    private static ItemStack offer(ItemStack icon, String name, int amount, String currency) {
        return Items.named(icon, ChatColor.GREEN + name, ChatColor.GRAY + "Cost: " + amount + " " + currency, ChatColor.YELLOW + "Click to purchase");
    }

    private static ItemStack setupItem(Material material, String name, boolean set) {
        return Items.named(new ItemStack(material), (set ? ChatColor.GREEN : ChatColor.YELLOW) + name, set ? ChatColor.GREEN + "Set" : ChatColor.GRAY + "Click to use your current location");
    }

    private static ItemStack enchantedSword(Material material, boolean sharp) {
        ItemStack sword = new ItemStack(material);
        if (sharp) Enchantments.add(sword, 1, "SHARPNESS", "DAMAGE_ALL");
        return sword;
    }

    private static void enchantHeldSwords(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().endsWith("_SWORD")) Enchantments.add(item, 1, "SHARPNESS", "DAMAGE_ALL");
        }
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private static String roman(int level) {
        return new String[] {"I", "II", "III", "IV", "MAX"}[Math.min(level - 1, 4)];
    }

    private static Block targetBlock(Player player, int range) {
        org.bukkit.Location point = player.getEyeLocation().clone();
        Vector step = point.getDirection().normalize().multiply(0.25);
        for (int i = 0; i < range * 4; i++) {
            point.add(step);
            Block block = point.getBlock();
            if (block.getType() != Material.AIR) return block;
        }
        return null;
    }
}
