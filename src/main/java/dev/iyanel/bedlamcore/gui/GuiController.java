package dev.iyanel.bedlamcore.gui;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Enchantments;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.lobby.LobbySettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GuiController {
    public static final String MAIN_TITLE = ChatColor.DARK_GRAY + "Bedlam Menu";
    public static final String ADMIN_TITLE = ChatColor.DARK_GRAY + "Bedlam Setup";
    public static final String LOBBY_TITLE = ChatColor.DARK_GRAY + "Lobby Setup";
    public static final String WORLDS_TITLE = ChatColor.DARK_GRAY + "Game Worlds";
    public static final String SHOP_TITLE = ChatColor.DARK_GRAY + "Item Shop";
    public static final String UPGRADES_TITLE = ChatColor.DARK_GRAY + "Team Upgrades";

    private final BedlamCore plugin;
    private final Map<UUID, LobbySettings> lobbyDrafts = new HashMap<UUID, LobbySettings>();
    private final Map<UUID, ArenaDraft> arenaDrafts = new HashMap<UUID, ArenaDraft>();
    private final Map<UUID, String> selectedArena = new HashMap<UUID, String>();
    private final Map<UUID, TeamColor> selectedTeam = new HashMap<UUID, TeamColor>();

    public GuiController(BedlamCore plugin) { this.plugin = plugin; }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_TITLE);
        inventory.setItem(10, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Quick Join Solo", ChatColor.GRAY + "One player per team"));
        inventory.setItem(12, Items.named(new ItemStack(Material.DIAMOND_SWORD), ChatColor.GOLD + "Quick Join Doubles", ChatColor.GRAY + "Two players per team"));
        inventory.setItem(14, Items.named(new ItemStack(Material.EMERALD), ChatColor.AQUA + "Browse Solo Games", ChatColor.GRAY + "Select a waiting arena"));
        inventory.setItem(15, Items.named(new ItemStack(Material.MAP), ChatColor.GOLD + "Browse Doubles Games", ChatColor.GRAY + "Select a waiting arena"));
        inventory.setItem(16, Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + "Leave Game"));
        if (player.hasPermission("bedlam.admin")) inventory.setItem(22, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Admin Setup"));
        player.openInventory(inventory);
    }

    public void openAdmin(Player player) {
        if (!admin(player)) return;
        Inventory inventory = Bukkit.createInventory(null, 27, ADMIN_TITLE);
        inventory.setItem(10, Items.named(new ItemStack(Material.NETHER_STAR), ChatColor.GREEN + "Lobby Setup", status(plugin.lobby().complete())));
        inventory.setItem(12, Items.named(new ItemStack(Items.material("GRASS_BLOCK", "GRASS")), ChatColor.AQUA + "Game World Setup", ChatColor.GRAY + "Create, edit, teleport, delete"));
        inventory.setItem(14, Items.named(new ItemStack(Material.COMPASS), ChatColor.YELLOW + "Current World", ChatColor.WHITE + player.getWorld().getName()));
        ArenaManager manager = plugin.games().arenaInWorld(player.getWorld().getName());
        if (manager != null) inventory.setItem(16, Items.named(new ItemStack(Material.MAP), ChatColor.GOLD + "Edit Current Game", ChatColor.GRAY + manager.arena().settings().gameType().displayName()));
        player.openInventory(inventory);
    }

    public void beginLobbySetup(Player player) {
        lobbyDrafts.put(player.getUniqueId(), plugin.lobby().copy());
        openLobbySetup(player);
    }

    private void openLobbySetup(Player player) {
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null) { beginLobbySetup(player); return; }
        Inventory inventory = Bukkit.createInventory(null, 27, LOBBY_TITLE);
        inventory.setItem(10, setupItem(Material.NETHER_STAR, "Set Lobby Spawn", draft.spawn() != null));
        inventory.setItem(12, npcItem(GameType.SOLO, draft));
        inventory.setItem(14, npcItem(GameType.DOUBLES, draft));
        inventory.setItem(21, Items.named(new ItemStack(Material.BARRIER), ChatColor.RED + "Cancel", ChatColor.GRAY + "Discard all lobby changes"));
        inventory.setItem(23, Items.named(new ItemStack(Material.SLIME_BALL), ChatColor.GREEN + "Apply", ChatColor.GRAY + "Validate and save"));
        player.openInventory(inventory);
    }

    public void openWorlds(Player player) {
        if (!admin(player)) return;
        Inventory inventory = Bukkit.createInventory(null, 54, WORLDS_TITLE);
        inventory.setItem(0, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Create Solo World"));
        inventory.setItem(1, Items.named(new ItemStack(Material.DIAMOND_SWORD), ChatColor.GOLD + "Create Doubles World"));
        inventory.setItem(4, Items.named(new ItemStack(Material.COMPASS), ChatColor.YELLOW + "Current World", ChatColor.WHITE + player.getWorld().getName()));
        int slot = 9;
        for (ArenaManager manager : plugin.games().arenas()) {
            if (slot >= inventory.getSize()) break;
            Arena arena = manager.arena();
            List<String> missing = arena.settings().validate();
            inventory.setItem(slot++, Items.named(new ItemStack(Items.material("GRASS_BLOCK", "GRASS")), ChatColor.GREEN + "World: " + arena.settings().id(),
                ChatColor.GRAY + "Mode: " + arena.settings().gameType().displayName(),
                ChatColor.GRAY + "State: " + arena.state().name(),
                missing.isEmpty() ? ChatColor.GREEN + "Configured" : ChatColor.RED + "Missing " + missing.size() + " item(s)"));
        }
        player.openInventory(inventory);
    }

    private void openWorldActions(Player player, String id) {
        ArenaManager manager = plugin.games().byId(id);
        if (manager == null) { openWorlds(player); return; }
        selectedArena.put(player.getUniqueId(), id);
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "World Actions");
        inventory.setItem(10, Items.named(new ItemStack(Items.material("ENDER_PEARL")), ChatColor.GREEN + "Teleport & Setup"));
        inventory.setItem(13, Items.named(new ItemStack(Material.PAPER), ChatColor.YELLOW + "Status", manager.arena().settings().validate().isEmpty() ? ChatColor.GREEN + "Ready" : ChatColor.RED + "Incomplete"));
        inventory.setItem(16, Items.named(new ItemStack(Material.TNT), ChatColor.RED + "Delete World", ChatColor.DARK_RED + "Requires confirmation"));
        player.openInventory(inventory);
    }

    private void confirmDelete(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm World Delete");
        inventory.setItem(11, Items.named(new ItemStack(Material.BARRIER), ChatColor.GRAY + "Keep World"));
        inventory.setItem(15, Items.named(new ItemStack(Material.TNT), ChatColor.RED + "Confirm Delete", ChatColor.DARK_RED + "This cannot be undone"));
        player.openInventory(inventory);
    }

    public void beginArenaSetup(Player player, ArenaSettings settings, boolean newWorld) {
        ArenaDraft session = new ArenaDraft(settings.copy(), newWorld);
        arenaDrafts.put(player.getUniqueId(), session);
        World world = plugin.worlds().load(settings);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Could not load " + settings.worldName() + ".");
            return;
        }
        player.teleport(settings.spectator() == null ? world.getSpawnLocation() : settings.spectator());
        reportMissing(player, session.settings.validate());
        openArenaSetup(player);
    }

    public boolean hasArenaDraft(Player player) { return arenaDrafts.containsKey(player.getUniqueId()); }

    private void openArenaSetup(Player player) {
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null) { openWorlds(player); return; }
        ArenaSettings settings = session.settings;
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Game Setup");
        inventory.setItem(0, Items.named(new ItemStack(Material.COMPASS), ChatColor.YELLOW + "Current World", ChatColor.WHITE + settings.worldName()));
        inventory.setItem(1, Items.named(new ItemStack(settings.gameType() == GameType.SOLO ? Material.IRON_SWORD : Material.DIAMOND_SWORD), ChatColor.AQUA + "Mode: " + settings.gameType().displayName()));
        inventory.setItem(4, setupItem(Items.material("ENDER_EYE", "EYE_OF_ENDER"), "Set Spectator Spawn", settings.spectator() != null));
        int[] slots = {10, 12, 14, 16};
        int index = 0;
        for (TeamColor color : TeamColor.values()) {
            inventory.setItem(slots[index++], Items.named(color.wool(1), color.chatColor() + "Configure " + color.displayName(), status(settings.team(color).complete())));
        }
        inventory.setItem(30, Items.named(new ItemStack(Material.DIAMOND), ChatColor.AQUA + "Add Diamond Generator", ChatColor.GRAY + "Count: " + settings.diamondGenerators().size()));
        inventory.setItem(32, Items.named(new ItemStack(Material.EMERALD), ChatColor.GREEN + "Add Emerald Generator", ChatColor.GRAY + "Count: " + settings.emeraldGenerators().size()));
        inventory.setItem(45, Items.named(new ItemStack(Material.BOOK), ChatColor.YELLOW + "Check Setup", ChatColor.GRAY + "Print every missing field"));
        inventory.setItem(48, Items.named(new ItemStack(Material.BARRIER), ChatColor.RED + "Cancel", ChatColor.GRAY + "Discard every draft change"));
        inventory.setItem(50, Items.named(new ItemStack(Material.SLIME_BALL), ChatColor.GREEN + "Apply", ChatColor.GRAY + "Validate and save"));
        player.openInventory(inventory);
    }

    private void openTeamSetup(Player player, TeamColor team) {
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null) return;
        selectedTeam.put(player.getUniqueId(), team);
        ArenaSettings.TeamSettings settings = session.settings.team(team);
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Team Setup");
        inventory.setItem(10, setupItem(Material.ARMOR_STAND, "Set Team Spawn", settings.spawn() != null));
        inventory.setItem(11, setupItem(Items.material("RED_BED", "BED"), "Set Bed (look at it)", settings.bed() != null));
        inventory.setItem(12, setupItem(Material.IRON_INGOT, "Set Forge", settings.forge() != null));
        inventory.setItem(14, setupItem(Material.CHEST, "Set Item Shop", settings.itemShop() != null));
        inventory.setItem(15, setupItem(Items.material("ENCHANTING_TABLE", "ENCHANTMENT_TABLE"), "Set Upgrade Shop", settings.upgradeShop() != null));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        player.openInventory(inventory);
    }

    public void openQueue(Player player, GameType type) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + type.displayName() + " Games");
        inventory.setItem(4, Items.named(new ItemStack(Material.EMERALD), ChatColor.GREEN + "Quick Join " + type.displayName()));
        int slot = 9;
        for (ArenaManager manager : plugin.games().arenas()) {
            Arena arena = manager.arena();
            if (arena.settings().gameType() != type || !arena.settings().validate().isEmpty()) continue;
            if (arena.state() != Arena.State.WAITING && arena.state() != Arena.State.COUNTDOWN) continue;
            inventory.setItem(slot++, Items.named(new ItemStack(Material.MAP), ChatColor.YELLOW + "Join: " + arena.settings().id(),
                ChatColor.GRAY + "Players: " + arena.players().size() + "/" + arena.settings().maximumPlayers(), ChatColor.GRAY + "State: " + arena.state().name()));
            if (slot >= inventory.getSize()) break;
        }
        player.openInventory(inventory);
    }

    public void click(Player player, String title, ItemStack clicked) {
        String cleanTitle = ChatColor.stripColor(title);
        String name = Items.name(clicked);
        if (cleanTitle.equals("Bedlam Menu")) clickMain(player, name);
        else if (cleanTitle.equals("Bedlam Setup")) clickAdmin(player, name);
        else if (cleanTitle.equals("Lobby Setup")) clickLobby(player, name);
        else if (cleanTitle.equals("Game Worlds")) clickWorlds(player, name);
        else if (cleanTitle.equals("World Actions")) clickWorldActions(player, name);
        else if (cleanTitle.equals("Confirm World Delete")) clickDelete(player, name);
        else if (cleanTitle.equals("Game Setup")) clickArenaSetup(player, name);
        else if (cleanTitle.equals("Team Setup")) clickTeamSetup(player, name);
        else if (cleanTitle.equals("Solo Games")) clickQueue(player, GameType.SOLO, name);
        else if (cleanTitle.equals("Doubles Games")) clickQueue(player, GameType.DOUBLES, name);
        else if (cleanTitle.equals("Item Shop")) buy(player, name);
        else if (cleanTitle.equals("Team Upgrades")) upgrade(player, name);
    }

    private void clickMain(Player player, String name) {
        if (name.equals("Quick Join Solo")) plugin.games().quickJoin(player, GameType.SOLO);
        else if (name.equals("Quick Join Doubles")) plugin.games().quickJoin(player, GameType.DOUBLES);
        else if (name.equals("Browse Solo Games")) openQueue(player, GameType.SOLO);
        else if (name.equals("Browse Doubles Games")) openQueue(player, GameType.DOUBLES);
        else if (name.equals("Leave Game")) plugin.games().leave(player);
        else if (name.equals("Admin Setup")) openAdmin(player);
    }

    private void clickAdmin(Player player, String name) {
        if (name.equals("Lobby Setup")) beginLobbySetup(player);
        else if (name.equals("Game World Setup")) openWorlds(player);
        else if (name.equals("Edit Current Game")) {
            ArenaManager manager = plugin.games().arenaInWorld(player.getWorld().getName());
            if (manager != null) beginArenaSetup(player, manager.arena().settings(), false);
        }
    }

    private void clickLobby(Player player, String name) {
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null) return;
        if (name.equals("Set Lobby Spawn")) draft.spawn(player.getLocation());
        else if (name.equals("Place Solo NPC")) giveNpcPlacer(player, GameType.SOLO);
        else if (name.equals("Place Doubles NPC")) giveNpcPlacer(player, GameType.DOUBLES);
        else if (name.equals("Cancel")) {
            lobbyDrafts.remove(player.getUniqueId());
            removeNpcPlacers(player);
            plugin.npcs().respawnAll();
            player.sendMessage(ChatColor.YELLOW + "Lobby setup cancelled. No changes were saved.");
            openAdmin(player);
            return;
        } else if (name.equals("Apply")) {
            List<String> missing = lobbyMissing(draft);
            if (!missing.isEmpty()) { reportMissing(player, missing); return; }
            plugin.applyLobby(draft.copy());
            lobbyDrafts.remove(player.getUniqueId());
            removeNpcPlacers(player);
            plugin.npcs().respawnAll();
            player.sendMessage(ChatColor.GREEN + "Lobby setup applied.");
            openAdmin(player);
            return;
        }
        openLobbySetup(player);
    }

    private void clickWorlds(Player player, String name) {
        if (name.equals("Create Solo World")) createWorld(player, GameType.SOLO);
        else if (name.equals("Create Doubles World")) createWorld(player, GameType.DOUBLES);
        else if (name.startsWith("World: ")) openWorldActions(player, name.substring(7));
    }

    private void createWorld(Player player, GameType type) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Creating a new " + type.displayName() + " world...");
        try {
            ArenaSettings settings = plugin.worlds().create(type);
            beginArenaSetup(player, settings, true);
        } catch (RuntimeException exception) {
            player.sendMessage(ChatColor.RED + "World creation failed: " + exception.getMessage());
        }
    }

    private void clickWorldActions(Player player, String name) {
        String id = selectedArena.get(player.getUniqueId());
        ArenaManager manager = id == null ? null : plugin.games().byId(id);
        if (manager == null) return;
        if (name.equals("Teleport & Setup")) beginArenaSetup(player, manager.arena().settings(), false);
        else if (name.equals("Delete World")) confirmDelete(player);
    }

    private void clickDelete(Player player, String name) {
        if (name.equals("Keep World")) { openWorlds(player); return; }
        if (!name.equals("Confirm Delete")) return;
        String id = selectedArena.remove(player.getUniqueId());
        ArenaManager manager = id == null ? null : plugin.games().byId(id);
        if (manager == null) return;
        ArenaSettings settings = manager.arena().settings();
        plugin.games().remove(id);
        if (plugin.worlds().delete(settings, player)) {
            plugin.saveSettings();
            player.sendMessage(ChatColor.GREEN + "Deleted " + settings.worldName() + ".");
        } else plugin.games().register(settings);
        openWorlds(player);
    }

    private void clickArenaSetup(Player player, String name) {
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null) return;
        ArenaSettings settings = session.settings;
        if (name.equals("Set Spectator Spawn")) settings.spectator(player.getLocation());
        else if (name.equals("Add Diamond Generator")) settings.diamondGenerators().add(player.getLocation());
        else if (name.equals("Add Emerald Generator")) settings.emeraldGenerators().add(player.getLocation());
        else if (name.equals("Check Setup")) reportMissing(player, settings.validate());
        else if (name.equals("Cancel")) { cancelArena(player, session); return; }
        else if (name.equals("Apply")) {
            List<String> missing = settings.validate();
            if (!missing.isEmpty()) { reportMissing(player, missing); return; }
            plugin.games().register(settings.copy());
            plugin.saveSettings();
            arenaDrafts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Game setup applied for " + settings.id() + ".");
            openWorlds(player);
            return;
        } else {
            for (TeamColor team : TeamColor.values()) if (name.equals("Configure " + team.displayName())) { openTeamSetup(player, team); return; }
        }
        openArenaSetup(player);
    }

    private void cancelArena(Player player, ArenaDraft session) {
        arenaDrafts.remove(player.getUniqueId());
        if (session.newWorld) {
            Location lobby = plugin.lobby().spawn();
            if (lobby != null) player.teleport(lobby);
            plugin.worlds().delete(session.settings, player);
        }
        player.sendMessage(ChatColor.YELLOW + "Game setup cancelled. No changes were saved.");
        openWorlds(player);
    }

    private void clickTeamSetup(Player player, String name) {
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        TeamColor team = selectedTeam.get(player.getUniqueId());
        if (session == null || team == null) return;
        ArenaSettings.TeamSettings settings = session.settings.team(team);
        if (name.equals("Set Team Spawn")) settings.spawn(player.getLocation());
        else if (name.equals("Set Forge")) settings.forge(player.getLocation());
        else if (name.equals("Set Item Shop")) settings.itemShop(player.getLocation());
        else if (name.equals("Set Upgrade Shop")) settings.upgradeShop(player.getLocation());
        else if (name.equals("Set Bed (look at it)")) {
            Block target = targetBlock(player, 6);
            if (target == null || !target.getType().name().contains("BED")) { player.sendMessage(ChatColor.RED + "Look directly at a bed within six blocks."); return; }
            settings.bed(target.getLocation());
        } else if (name.equals("Back")) { openArenaSetup(player); return; }
        openTeamSetup(player, team);
    }

    private void clickQueue(Player player, GameType type, String name) {
        if (name.equals("Quick Join " + type.displayName())) plugin.games().quickJoin(player, type);
        else if (name.startsWith("Join: ")) {
            ArenaManager manager = plugin.games().byId(name.substring(6));
            if (manager != null) { plugin.games().leave(player); manager.join(player); }
        }
    }

    public GameType npcPlacer(ItemStack item) {
        if (!Items.hasLore(item, "Bedlam NPC:")) return null;
        for (GameType type : GameType.values()) if (Items.hasLore(item, "Bedlam NPC: " + type.name())) return type;
        return null;
    }

    public void placeNpc(Player player, GameType type, Location location) {
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null || !admin(player)) return;
        draft.npc(type).location(location);
        plugin.npcs().spawn(type, location, draft.npc(type).entityType());
        player.getInventory().removeItem(player.getItemInHand());
        player.sendMessage(ChatColor.GREEN + type.displayName() + " NPC placed. Shift-left-click it to change its entity.");
        openLobbySetup(player);
    }

    public void cycleNpc(Player player, GameType type) {
        if (!admin(player)) return;
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null) {
            draft = plugin.lobby().copy();
            lobbyDrafts.put(player.getUniqueId(), draft);
        }
        LobbySettings.NpcSettings settings = draft.npc(type);
        EntityType next = plugin.npcs().next(settings.entityType());
        settings.entityType(next);
        if (settings.location() != null) plugin.npcs().spawn(type, settings.location(), next);
        player.sendMessage(ChatColor.YELLOW + type.displayName() + " NPC changed to " + next.name() + ". Apply or Cancel in Lobby Setup.");
        openLobbySetup(player);
    }

    private void giveNpcPlacer(Player player, GameType type) {
        ItemStack item = Items.named(new ItemStack(Material.ARMOR_STAND), ChatColor.GOLD + "Place " + type.displayName() + " NPC",
            ChatColor.DARK_GRAY + "Bedlam NPC: " + type.name(), ChatColor.GRAY + "Right-click a block to place");
        player.getInventory().addItem(item);
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Right-click a block with the armor stand to place the " + type.displayName() + " NPC.");
    }

    public void openShop(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 45, SHOP_TITLE);
        inventory.setItem(10, offer(TeamColor.RED.wool(16), "16 Wool", 4, "Iron"));
        inventory.setItem(11, offer(new ItemStack(Items.material("STONE_SWORD")), "Stone Sword", 10, "Iron"));
        inventory.setItem(12, offer(new ItemStack(Items.material("IRON_SWORD")), "Iron Sword", 7, "Gold"));
        inventory.setItem(13, offer(new ItemStack(Items.material("IRON_CHESTPLATE")), "Permanent Iron Armor", 12, "Gold"));
        inventory.setItem(14, offer(Items.stack("OAK_PLANKS", "WOOD", 16, (short) 0), "16 Oak Planks", 4, "Gold"));
        inventory.setItem(15, offer(new ItemStack(Material.LADDER, 8), "8 Ladders", 4, "Iron"));
        inventory.setItem(16, offer(new ItemStack(Items.material("GOLDEN_APPLE")), "Golden Apple", 3, "Gold"));
        inventory.setItem(20, offer(new ItemStack(Material.TNT), "TNT", 4, "Gold"));
        inventory.setItem(21, offer(new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")), "Fireball", 40, "Iron"));
        inventory.setItem(22, offer(new ItemStack(Material.ENDER_PEARL), "Ender Pearl", 4, "Emerald"));
        inventory.setItem(23, offer(new ItemStack(Material.BOW), "Bow", 12, "Gold"));
        inventory.setItem(24, offer(new ItemStack(Material.ARROW, 8), "8 Arrows", 2, "Gold"));
        inventory.setItem(25, offer(new ItemStack(Material.WATER_BUCKET), "Water Bucket", 3, "Gold"));
        player.openInventory(inventory);
    }

    public void openUpgrades(Player player) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 27, UPGRADES_TITLE);
        inventory.setItem(11, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Sharpened Swords", arena.sharpness(team) ? ChatColor.GREEN + "Purchased" : ChatColor.GRAY + "Cost: 4 Diamond"));
        int level = arena.protection(team);
        int cost = new int[] {2, 4, 8, 16}[Math.min(level, 3)];
        inventory.setItem(15, Items.named(new ItemStack(Material.IRON_CHESTPLATE), ChatColor.AQUA + "Reinforced Armor " + roman(level + 1), level >= 4 ? ChatColor.GREEN + "Maximum level" : ChatColor.GRAY + "Cost: " + cost + " Diamond"));
        player.openInventory(inventory);
    }

    private void buy(Player player, String name) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING) return;
        if (name.equals("16 Wool") && pay(player, Material.IRON_INGOT, 4)) give(player, team.wool(16));
        else if (name.equals("Stone Sword") && pay(player, Material.IRON_INGOT, 10)) give(player, sword(Items.material("STONE_SWORD"), arena.sharpness(team)));
        else if (name.equals("Iron Sword") && pay(player, Material.GOLD_INGOT, 7)) give(player, sword(Material.IRON_SWORD, arena.sharpness(team)));
        else if (name.equals("Permanent Iron Armor") && pay(player, Material.GOLD_INGOT, 12)) { arena.ironArmor().add(player.getUniqueId()); manager.equipArmor(player, team); }
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
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null) return;
        if (name.equals("Sharpened Swords") && !arena.sharpness(team) && pay(player, Material.DIAMOND, 4)) {
            arena.sharpness(team, true);
            for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) enchantSwords(member);
        } else if (name.startsWith("Reinforced Armor") && arena.protection(team) < 4) {
            int level = arena.protection(team);
            if (pay(player, Material.DIAMOND, new int[] {2, 4, 8, 16}[level])) {
                arena.protection(team, level + 1);
                for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) manager.equipArmor(member, team);
            }
        }
        openUpgrades(player);
    }

    private boolean pay(Player player, Material currency, int amount) {
        if (!player.getInventory().containsAtLeast(new ItemStack(currency), amount)) { player.sendMessage(ChatColor.RED + "You do not have enough " + currency.name().toLowerCase().replace('_', ' ') + "."); return false; }
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

    private static List<String> lobbyMissing(LobbySettings settings) {
        List<String> missing = new ArrayList<String>();
        if (settings.spawn() == null) missing.add("lobby spawn");
        if (settings.npc(GameType.SOLO).location() == null) missing.add("Solo NPC");
        if (settings.npc(GameType.DOUBLES).location() == null) missing.add("Doubles NPC");
        return missing;
    }

    private static void reportMissing(Player player, List<String> missing) {
        if (missing.isEmpty()) { player.sendMessage(ChatColor.GREEN + "Setup check passed. Nothing is missing."); return; }
        player.sendMessage(ChatColor.RED + "Setup is incomplete:");
        for (String item : missing) player.sendMessage(ChatColor.RED + " - " + ChatColor.WHITE + item);
    }

    private static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> excess = player.getInventory().addItem(item);
        for (ItemStack stack : excess.values()) player.getWorld().dropItemNaturally(player.getLocation(), stack);
    }

    private void removeNpcPlacers(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (npcPlacer(player.getInventory().getItem(slot)) != null) player.getInventory().setItem(slot, null);
        }
    }

    private static ItemStack npcItem(GameType type, LobbySettings draft) {
        LobbySettings.NpcSettings npc = draft.npc(type);
        return Items.named(new ItemStack(Material.ARMOR_STAND), ChatColor.GOLD + "Place " + type.displayName() + " NPC",
            npc.location() == null ? ChatColor.RED + "Not placed" : ChatColor.GREEN + "Placed as " + npc.entityType().name());
    }

    private static ItemStack setupItem(Material material, String name, boolean set) { return Items.named(new ItemStack(material), (set ? ChatColor.GREEN : ChatColor.YELLOW) + name, status(set)); }
    private static String status(boolean set) { return set ? ChatColor.GREEN + "Set" : ChatColor.RED + "Missing"; }
    private static ItemStack offer(ItemStack icon, String name, int amount, String currency) { return Items.named(icon, ChatColor.GREEN + name, ChatColor.GRAY + "Cost: " + amount + " " + currency, ChatColor.YELLOW + "Click to purchase"); }
    private static ItemStack sword(Material material, boolean sharp) { ItemStack item = new ItemStack(material); if (sharp) Enchantments.add(item, 1, "SHARPNESS", "DAMAGE_ALL"); return item; }
    private static void enchantSwords(Player player) { for (ItemStack item : player.getInventory().getContents()) if (item != null && item.getType().name().endsWith("_SWORD")) Enchantments.add(item, 1, "SHARPNESS", "DAMAGE_ALL"); }
    private static String roman(int level) { return new String[] {"I", "II", "III", "IV", "MAX"}[Math.min(level - 1, 4)]; }
    private static boolean admin(Player player) { return player.hasPermission("bedlam.admin"); }

    private static Block targetBlock(Player player, int range) {
        Location point = player.getEyeLocation().clone();
        Vector step = point.getDirection().normalize().multiply(0.25);
        for (int i = 0; i < range * 4; i++) { point.add(step); Block block = point.getBlock(); if (block.getType() != Material.AIR) return block; }
        return null;
    }

    private static final class ArenaDraft {
        private final ArenaSettings settings;
        private final boolean newWorld;
        private ArenaDraft(ArenaSettings settings, boolean newWorld) { this.settings = settings; this.newWorld = newWorld; }
    }
}
