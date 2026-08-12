package dev.iyanel.bedlamcore.gui;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Enchantments;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.compat.Skins;
import dev.iyanel.bedlamcore.game.GameRules;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<UUID, GameType> selectedNpc = new HashMap<UUID, GameType>();
    private final Map<UUID, GameType> skinInputs = new ConcurrentHashMap<UUID, GameType>();
    private final Map<UUID, String> shopCategory = new HashMap<UUID, String>();

    public GuiController(BedlamCore plugin) { this.plugin = plugin; }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_TITLE);
        inventory.setItem(10, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Quick Join Solo", ChatColor.GRAY + "One player per team"));
        inventory.setItem(12, Items.named(new ItemStack(Material.DIAMOND_SWORD), ChatColor.GOLD + "Quick Join Doubles", ChatColor.GRAY + "Two players per team"));
        inventory.setItem(14, Items.named(new ItemStack(Material.EMERALD), ChatColor.AQUA + "Browse Solo Games", ChatColor.GRAY + "Select a waiting arena"));
        inventory.setItem(15, Items.named(new ItemStack(Material.MAP), ChatColor.GOLD + "Browse Doubles Games", ChatColor.GRAY + "Select a waiting arena"));
        inventory.setItem(16, Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + "Leave Game"));
        if (admin(player)) inventory.setItem(22, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Admin Setup"));
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
        if (!lobbyDrafts.containsKey(player.getUniqueId())) lobbyDrafts.put(player.getUniqueId(), plugin.lobby().copy());
        openLobbySetup(player);
    }

    public void openContextSetup(Player player) {
        if (!admin(player)) return;
        if (arenaDrafts.containsKey(player.getUniqueId())) { openArenaSetup(player); return; }
        ArenaManager manager = plugin.games().arenaInWorld(player.getWorld().getName());
        if (manager != null) beginArenaSetup(player, manager.arena().settings(), false);
        else openAdmin(player);
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

    public void disconnect(Player player) {
        UUID uuid = player.getUniqueId();
        LobbySettings lobbyDraft = lobbyDrafts.remove(uuid);
        if (lobbyDraft != null) plugin.npcs().respawnAll();
        final ArenaDraft arenaDraft = arenaDrafts.remove(uuid);
        if (arenaDraft != null && arenaDraft.newWorld) Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { plugin.worlds().delete(arenaDraft.settings, player); }
        });
        selectedArena.remove(uuid);
        selectedTeam.remove(uuid);
        selectedNpc.remove(uuid);
        skinInputs.remove(uuid);
        shopCategory.remove(uuid);
    }

    private void openArenaSetup(Player player) {
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null) { openWorlds(player); return; }
        ArenaSettings settings = session.settings;
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Game Setup");
        inventory.setItem(0, Items.named(new ItemStack(Material.COMPASS), ChatColor.YELLOW + "Current World", ChatColor.WHITE + settings.worldName()));
        inventory.setItem(1, Items.named(new ItemStack(settings.gameType() == GameType.SOLO ? Material.IRON_SWORD : Material.DIAMOND_SWORD), ChatColor.AQUA + "Mode: " + settings.gameType().displayName()));
        inventory.setItem(3, setupItem(Material.GLASS, "Set Waiting Spawn", settings.waitingSpawn() != null));
        inventory.setItem(5, setupItem(Items.material("ENDER_EYE", "EYE_OF_ENDER"), "Set Spectator Spawn", settings.spectator() != null));
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
        else if (cleanTitle.equals("NPC Editor")) clickNpcEditor(player, name);
        else if (cleanTitle.equals("Solo Games")) clickQueue(player, GameType.SOLO, name);
        else if (cleanTitle.equals("Doubles Games")) clickQueue(player, GameType.DOUBLES, name);
        else if (cleanTitle.equals("Item Shop")) buy(player, name);
        else if (cleanTitle.equals("Team Upgrades")) upgrade(player, name);
        else if (cleanTitle.equals("Spectate")) clickSpectate(player, name);
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
        if (name.equals("Set Waiting Spawn")) settings.waitingSpawn(player.getLocation());
        else if (name.equals("Set Spectator Spawn")) settings.spectator(player.getLocation());
        else if (name.equals("Add Diamond Generator")) settings.diamondGenerators().add(player.getLocation());
        else if (name.equals("Add Emerald Generator")) settings.emeraldGenerators().add(player.getLocation());
        else if (name.equals("Check Setup")) reportMissing(player, settings.validate());
        else if (name.equals("Cancel")) { cancelArena(player, session); return; }
        else if (name.equals("Apply")) {
            List<String> missing = settings.validate();
            if (!missing.isEmpty()) { reportMissing(player, missing); return; }
            plugin.games().remove(settings.id());
            World world = Bukkit.getWorld(settings.worldName());
            plugin.worlds().saveOnce(world);
            plugin.games().register(settings.copy());
            plugin.saveSettings();
            arenaDrafts.remove(player.getUniqueId());
            player.closeInventory();
            Location lobby = plugin.lobby().spawn();
            if (lobby == null && !Bukkit.getWorlds().isEmpty()) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
            if (lobby != null) player.teleport(lobby);
            player.sendMessage(ChatColor.GREEN + "Game setup applied and world saved for " + settings.id() + ". Nothing is missing.");
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
        plugin.npcs().spawn(type, draft.npc(type));
        player.getInventory().removeItem(player.getItemInHand());
        player.sendMessage(ChatColor.GREEN + type.displayName() + " NPC placed. Shift-left-click it to change its entity.");
        if (lobbyMissing(draft).isEmpty()) player.sendMessage(ChatColor.GREEN + "Lobby setup is complete. Click Apply to save both NPCs.");
        openLobbySetup(player);
    }

    public void openNpcEditor(Player player, GameType type) {
        if (!admin(player)) return;
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null) {
            draft = plugin.lobby().copy();
            lobbyDrafts.put(player.getUniqueId(), draft);
        }
        selectedNpc.put(player.getUniqueId(), type);
        LobbySettings.NpcSettings settings = draft.npc(type);
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "NPC Editor");
        inventory.setItem(4, Items.named(settings.human() ? Skins.head(settings.skin()) : new ItemStack(Items.material("VILLAGER_SPAWN_EGG", "MONSTER_EGG")),
            ChatColor.GOLD + type.displayName() + " NPC", ChatColor.GRAY + appearance(settings)));
        inventory.setItem(10, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Previous Mob"));
        inventory.setItem(12, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Next Mob"));
        inventory.setItem(14, Items.named(new ItemStack(Material.EGG), ChatColor.AQUA + "Age: " + (settings.baby() ? "Baby" : "Adult"), ChatColor.GRAY + "Click to toggle"));
        inventory.setItem(16, Items.named(Skins.head(settings.skin()), ChatColor.GREEN + "Use Human Player", ChatColor.GRAY + "Uses Citizens when installed"));
        inventory.setItem(20, Items.named(new ItemStack(Material.NAME_TAG), ChatColor.LIGHT_PURPLE + "Set Skin", ChatColor.GRAY + "Username or textures.minecraft.net URL"));
        inventory.setItem(24, Items.named(new ItemStack(Items.material("ENDER_EYE", "EYE_OF_ENDER")),
            (settings.lookAtPlayers() ? ChatColor.GREEN : ChatColor.RED) + "Look at Players: " + (settings.lookAtPlayers() ? "ON" : "OFF"), ChatColor.GRAY + "Default: OFF"));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        player.openInventory(inventory);
    }

    private void clickNpcEditor(Player player, String name) {
        GameType type = selectedNpc.get(player.getUniqueId());
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (type == null || draft == null) return;
        LobbySettings.NpcSettings settings = draft.npc(type);
        if (name.equals("Previous Mob") || name.equals("Next Mob")) {
            settings.human(false);
            settings.entityType(plugin.npcs().next(settings.entityType(), name.equals("Next Mob") ? 1 : -1));
        } else if (name.startsWith("Age: ")) settings.baby(!settings.baby());
        else if (name.equals("Use Human Player")) settings.human(true);
        else if (name.startsWith("Look at Players: ")) settings.lookAtPlayers(!settings.lookAtPlayers());
        else if (name.equals("Set Skin")) {
            settings.human(true);
            skinInputs.put(player.getUniqueId(), type);
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Type a Minecraft username or direct textures.minecraft.net URL in chat. Type cancel to stop.");
            return;
        } else if (name.equals("Back")) { openLobbySetup(player); return; }
        if (settings.location() != null) plugin.npcs().spawn(type, settings);
        openNpcEditor(player, type);
    }

    public boolean acceptSkinInput(final Player player, final String message) {
        final GameType type = skinInputs.remove(player.getUniqueId());
        if (type == null) return false;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
                if (draft == null || message.equalsIgnoreCase("cancel")) { player.sendMessage(ChatColor.YELLOW + "Skin input cancelled."); return; }
                if (!message.matches("[A-Za-z0-9_]{1,16}") && !message.startsWith("https://textures.minecraft.net/texture/")) {
                    player.sendMessage(ChatColor.RED + "Use a Minecraft username or a direct https://textures.minecraft.net/texture/... URL.");
                    openNpcEditor(player, type);
                    return;
                }
                LobbySettings.NpcSettings settings = draft.npc(type);
                settings.human(true);
                settings.skin(message);
                if (settings.location() != null) plugin.npcs().spawn(type, settings);
                player.sendMessage(ChatColor.GREEN + "Skin saved in the draft. Click Apply in Lobby Setup to keep it.");
                openNpcEditor(player, type);
            }
        });
        return true;
    }

    private void giveNpcPlacer(Player player, GameType type) {
        ItemStack item = Items.named(new ItemStack(Material.ARMOR_STAND), ChatColor.GOLD + "Place " + type.displayName() + " NPC",
            ChatColor.DARK_GRAY + "Bedlam NPC: " + type.name(), ChatColor.GRAY + "Right-click a block to place");
        player.getInventory().addItem(item);
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Right-click a block with the armor stand to place the " + type.displayName() + " NPC.");
    }

    public void openShop(Player player) {
        if (!shopCategory.containsKey(player.getUniqueId())) shopCategory.put(player.getUniqueId(), "Quick Buy");
        openShopCategory(player, shopCategory.get(player.getUniqueId()));
    }

    public void openSpectate(Player player) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Spectate");
        int slot = 0;
        for (Map.Entry<UUID, TeamColor> entry : arena.players().entrySet()) {
            if (arena.eliminated().contains(entry.getKey()) || slot >= inventory.getSize()) continue;
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null || target.equals(player)) continue;
            TeamColor team = entry.getValue();
            ItemStack head = Skins.head(target.getName());
            inventory.setItem(slot++, Items.named(head, team.chatColor() + target.getName(),
                ChatColor.GRAY + "Team: " + team.coloredName(), ChatColor.YELLOW + "Click to spectate"));
        }
        player.openInventory(inventory);
    }

    private void clickSpectate(Player player, String name) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null || name == null || name.isEmpty()) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.getName().equals(name)) continue;
            if (!manager.arena().contains(target.getUniqueId()) || manager.arena().eliminated().contains(target.getUniqueId())) return;
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.teleport(target);
            player.sendMessage(ChatColor.YELLOW + "Spectating " + target.getName());
            player.closeInventory();
            return;
        }
    }

    private void openShopCategory(Player player, String category) {
        shopCategory.put(player.getUniqueId(), category);
        Inventory inventory = Bukkit.createInventory(null, 54, SHOP_TITLE);
        inventory.setItem(0, categoryTab(Material.NETHER_STAR, "Quick Buy", category));
        inventory.setItem(1, categoryTab(Items.material("WHITE_WOOL", "WOOL"), "Blocks", category));
        inventory.setItem(2, categoryTab(Items.material("GOLDEN_SWORD", "GOLD_SWORD"), "Melee", category));
        inventory.setItem(3, categoryTab(Items.material("CHAINMAIL_BOOTS"), "Armor", category));
        inventory.setItem(4, categoryTab(Items.material("STONE_PICKAXE"), "Tools", category));
        inventory.setItem(5, categoryTab(Material.BOW, "Ranged", category));
        inventory.setItem(6, categoryTab(Items.material("BREWING_STAND", "BREWING_STAND_ITEM"), "Potions", category));
        inventory.setItem(7, categoryTab(Material.TNT, "Utility", category));
        ItemStack pane = Items.named(Items.stack("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 7), " ");
        for (int i = 9; i < 18; i++) inventory.setItem(i, pane);
        if (category.equals("Quick Buy") || category.equals("Blocks")) {
            inventory.setItem(19, offer(TeamColor.RED.wool(16), "16 Wool", 4, "Iron"));
            inventory.setItem(20, offer(Items.stack("OAK_PLANKS", "WOOD", 16, (short) 0), "16 Oak Planks", 4, "Gold"));
            inventory.setItem(21, offer(new ItemStack(Items.material("END_STONE", "ENDER_STONE"), 12), "12 End Stone", 24, "Iron"));
            inventory.setItem(22, offer(new ItemStack(Material.LADDER, 8), "8 Ladders", 4, "Iron"));
            inventory.setItem(23, offer(new ItemStack(Material.GLASS, 4), "4 Glass", 12, "Iron"));
        }
        if (category.equals("Quick Buy") || category.equals("Melee")) {
            int base = category.equals("Melee") ? 19 : 28;
            inventory.setItem(base, offer(new ItemStack(Items.material("STONE_SWORD")), "Stone Sword", 10, "Iron"));
            inventory.setItem(base + 1, offer(new ItemStack(Items.material("IRON_SWORD")), "Iron Sword", 7, "Gold"));
            inventory.setItem(base + 2, offer(new ItemStack(Items.material("DIAMOND_SWORD")), "Diamond Sword", 4, "Emerald"));
            inventory.setItem(base + 3, offer(new ItemStack(Items.material("STICK")), "Knockback Stick", 5, "Gold"));
        }
        if (category.equals("Quick Buy") || category.equals("Armor")) {
            int base = category.equals("Armor") ? 19 : 37;
            inventory.setItem(base, offer(new ItemStack(Items.material("CHAINMAIL_BOOTS")), "Permanent Chainmail Armor", 40, "Iron"));
            inventory.setItem(base + 1, offer(new ItemStack(Items.material("IRON_CHESTPLATE")), "Permanent Iron Armor", 12, "Gold"));
            inventory.setItem(base + 2, offer(new ItemStack(Items.material("DIAMOND_CHESTPLATE")), "Permanent Diamond Armor", 6, "Emerald"));
        }
        if (category.equals("Tools")) {
            inventory.setItem(19, offer(new ItemStack(Items.material("WOODEN_PICKAXE", "WOOD_PICKAXE")), "Wooden Pickaxe", 10, "Iron"));
            inventory.setItem(20, offer(new ItemStack(Items.material("IRON_PICKAXE")), "Iron Pickaxe", 10, "Gold"));
            inventory.setItem(21, offer(new ItemStack(Items.material("DIAMOND_PICKAXE")), "Diamond Pickaxe", 6, "Gold"));
            inventory.setItem(22, offer(new ItemStack(Items.material("WOODEN_AXE", "WOOD_AXE")), "Wooden Axe", 10, "Iron"));
            inventory.setItem(23, offer(new ItemStack(Items.material("SHEARS")), "Shears", 20, "Iron"));
        }
        if (category.equals("Ranged")) {
            inventory.setItem(19, offer(new ItemStack(Material.BOW), "Bow", 12, "Gold"));
            inventory.setItem(20, offer(new ItemStack(Material.ARROW, 8), "8 Arrows", 2, "Gold"));
        }
        if (category.equals("Potions")) {
            inventory.setItem(19, offer(new ItemStack(Items.material("POTION")), "Speed Potion", 1, "Emerald"));
            inventory.setItem(20, offer(new ItemStack(Items.material("POTION")), "Jump Potion", 1, "Emerald"));
            inventory.setItem(21, offer(new ItemStack(Items.material("POTION")), "Invisibility Potion", 2, "Emerald"));
        }
        if (category.equals("Utility") || category.equals("Quick Buy")) {
            if (category.equals("Utility")) {
                inventory.setItem(19, offer(new ItemStack(Items.material("GOLDEN_APPLE")), "Golden Apple", 3, "Gold"));
                inventory.setItem(20, offer(new ItemStack(Material.TNT), "TNT", 4, "Gold"));
                inventory.setItem(21, offer(new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")), "Fireball", 40, "Iron"));
                inventory.setItem(22, offer(new ItemStack(Material.ENDER_PEARL), "Ender Pearl", 4, "Emerald"));
                inventory.setItem(23, offer(new ItemStack(Material.WATER_BUCKET), "Water Bucket", 3, "Gold"));
                inventory.setItem(24, offer(new ItemStack(Material.SPONGE, 4), "4 Sponges", 3, "Gold"));
            } else {
                inventory.setItem(40, offer(new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")), "Fireball", 40, "Iron"));
                inventory.setItem(41, offer(new ItemStack(Material.TNT), "TNT", 4, "Gold"));
                inventory.setItem(42, offer(new ItemStack(Material.WATER_BUCKET), "Water Bucket", 3, "Gold"));
                inventory.setItem(43, offer(new ItemStack(Items.material("GOLDEN_APPLE")), "Golden Apple", 3, "Gold"));
            }
        }
        player.openInventory(inventory);
    }

    private static ItemStack categoryTab(Material material, String name, String selected) {
        boolean on = name.equals(selected);
        return Items.named(new ItemStack(material), (on ? ChatColor.GREEN : ChatColor.YELLOW) + name,
            on ? ChatColor.GRAY + "Selected" : ChatColor.GRAY + "Click to browse");
    }

    public void openUpgrades(Player player) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 45, UPGRADES_TITLE);
        ItemStack pane = Items.named(Items.stack("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 7), " ");
        for (int i = 0; i < 9; i++) inventory.setItem(i, pane);
        for (int i = 36; i < 45; i++) inventory.setItem(i, pane);
        inventory.setItem(4, Items.named(new ItemStack(Items.material("BEACON")), ChatColor.AQUA + "" + ChatColor.BOLD + "Team Upgrades",
            ChatColor.GRAY + "Purchases help your whole team"));
        inventory.setItem(19, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Sharpened Swords",
            arena.sharpness(team) ? ChatColor.GREEN + "Purchased" : ChatColor.GRAY + "Cost: 4 Diamond",
            ChatColor.DARK_GRAY + "Sharpness I on team swords"));
        int level = arena.protection(team);
        int cost = new int[] {2, 4, 8, 16}[Math.min(level, 3)];
        inventory.setItem(20, Items.named(new ItemStack(Material.IRON_CHESTPLATE), ChatColor.AQUA + "Reinforced Armor " + roman(level + 1),
            level >= 4 ? ChatColor.GREEN + "Maximum level" : ChatColor.GRAY + "Cost: " + cost + " Diamond",
            ChatColor.DARK_GRAY + "Protection on team armor"));
        int forge = arena.forgeLevel(team);
        inventory.setItem(21, Items.named(new ItemStack(Material.IRON_INGOT), ChatColor.AQUA + "Forge " + roman(forge + 1),
            forge >= 4 ? ChatColor.GREEN + "Maximum level" : ChatColor.GRAY + "Cost: " + (forge + 2) + " Diamond",
            ChatColor.DARK_GRAY + "Faster iron/gold at your forge"));
        int haste = arena.hasteLevel(team);
        inventory.setItem(22, Items.named(new ItemStack(Items.material("GOLDEN_PICKAXE", "GOLD_PICKAXE")), ChatColor.AQUA + "Maniac Miner " + roman(haste + 1),
            haste >= 2 ? ChatColor.GREEN + "Maximum level" : ChatColor.GRAY + "Cost: " + (haste == 0 ? 2 : 4) + " Diamond",
            ChatColor.DARK_GRAY + "Haste for your team"));
        inventory.setItem(23, Items.named(new ItemStack(Items.material("BEACON")), ChatColor.AQUA + "Heal Pool",
            arena.healPool(team) ? ChatColor.GREEN + "Purchased" : ChatColor.GRAY + "Cost: 3 Diamond",
            ChatColor.DARK_GRAY + "Regen + green particles at base"));
        player.openInventory(inventory);
    }

    private void buy(Player player, String name) {
        if (name.equals("Quick Buy") || name.equals("Blocks") || name.equals("Melee") || name.equals("Armor")
            || name.equals("Tools") || name.equals("Ranged") || name.equals("Potions") || name.equals("Utility")) {
            openShopCategory(player, name);
            return;
        }
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING) return;
        if (name.equals("16 Wool") && pay(player, Material.IRON_INGOT, 4)) give(player, team.wool(16));
        else if (name.equals("Stone Sword") && pay(player, Material.IRON_INGOT, 10)) giveSword(player, sword(Items.material("STONE_SWORD"), arena.sharpness(team)));
        else if (name.equals("Iron Sword") && pay(player, Material.GOLD_INGOT, 7)) giveSword(player, sword(Material.IRON_SWORD, arena.sharpness(team)));
        else if (name.equals("Diamond Sword") && pay(player, Material.EMERALD, 4)) giveSword(player, sword(Items.material("DIAMOND_SWORD"), arena.sharpness(team)));
        else if (name.equals("Knockback Stick") && pay(player, Material.GOLD_INGOT, 5)) {
            ItemStack stick = Items.unbreakable(new ItemStack(Items.material("STICK")));
            Enchantments.add(stick, 1, "KNOCKBACK");
            give(player, Items.named(stick, ChatColor.GREEN + "Knockback Stick"));
        }
        else if (name.equals("Permanent Chainmail Armor") && pay(player, Material.IRON_INGOT, 40)) {
            ItemStack boots = Items.unbreakable(new ItemStack(Items.material("CHAINMAIL_BOOTS")));
            ItemStack legs = Items.unbreakable(new ItemStack(Items.material("CHAINMAIL_LEGGINGS")));
            int protection = arena.protection(team);
            if (protection > 0) {
                Enchantments.add(boots, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
                Enchantments.add(legs, protection, "PROTECTION", "PROTECTION_ENVIRONMENTAL");
            }
            player.getInventory().setBoots(boots);
            player.getInventory().setLeggings(legs);
        }
        else if (name.equals("Permanent Iron Armor") && pay(player, Material.GOLD_INGOT, 12)) {
            if (arena.armorTier(player.getUniqueId()) < 1) arena.armorTier(player.getUniqueId(), 1);
            manager.equipArmor(player, team);
        } else if (name.equals("Permanent Diamond Armor") && pay(player, Material.EMERALD, 6)) {
            arena.armorTier(player.getUniqueId(), 2);
            manager.equipArmor(player, team);
        }
        else if (name.equals("16 Oak Planks") && pay(player, Material.GOLD_INGOT, 4)) give(player, Items.stack("OAK_PLANKS", "WOOD", 16, (short) 0));
        else if (name.equals("12 End Stone") && pay(player, Material.IRON_INGOT, 24)) give(player, new ItemStack(Items.material("END_STONE", "ENDER_STONE"), 12));
        else if (name.equals("8 Ladders") && pay(player, Material.IRON_INGOT, 4)) give(player, new ItemStack(Material.LADDER, 8));
        else if (name.equals("4 Glass") && pay(player, Material.IRON_INGOT, 12)) give(player, new ItemStack(Material.GLASS, 4));
        else if (name.equals("Wooden Pickaxe") && pay(player, Material.IRON_INGOT, 10)) give(player, Items.unbreakable(new ItemStack(Items.material("WOODEN_PICKAXE", "WOOD_PICKAXE"))));
        else if (name.equals("Iron Pickaxe") && pay(player, Material.GOLD_INGOT, 10)) give(player, Items.unbreakable(new ItemStack(Items.material("IRON_PICKAXE"))));
        else if (name.equals("Diamond Pickaxe") && pay(player, Material.GOLD_INGOT, 6)) give(player, Items.unbreakable(new ItemStack(Items.material("DIAMOND_PICKAXE"))));
        else if (name.equals("Wooden Axe") && pay(player, Material.IRON_INGOT, 10)) give(player, Items.unbreakable(new ItemStack(Items.material("WOODEN_AXE", "WOOD_AXE"))));
        else if (name.equals("Shears") && pay(player, Material.IRON_INGOT, 20)) give(player, Items.unbreakable(new ItemStack(Items.material("SHEARS"))));
        else if (name.equals("Golden Apple") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Items.material("GOLDEN_APPLE")));
        else if (name.equals("TNT") && pay(player, Material.GOLD_INGOT, 4)) give(player, new ItemStack(Material.TNT));
        else if (name.equals("Fireball") && pay(player, Material.IRON_INGOT, 40)) give(player, new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")));
        else if (name.equals("Ender Pearl") && pay(player, Material.EMERALD, 4)) give(player, new ItemStack(Material.ENDER_PEARL));
        else if (name.equals("Bow") && pay(player, Material.GOLD_INGOT, 12)) give(player, Items.unbreakable(new ItemStack(Material.BOW)));
        else if (name.equals("8 Arrows") && pay(player, Material.GOLD_INGOT, 2)) give(player, new ItemStack(Material.ARROW, 8));
        else if (name.equals("Water Bucket") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Material.WATER_BUCKET));
        else if (name.equals("4 Sponges") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Material.SPONGE, 4));
        else if (name.equals("Speed Potion") && pay(player, Material.EMERALD, 1)) give(player, potion(8194));
        else if (name.equals("Jump Potion") && pay(player, Material.EMERALD, 1)) give(player, potion(8203));
        else if (name.equals("Invisibility Potion") && pay(player, Material.EMERALD, 2)) give(player, potion(8206));
        openShopCategory(player, shopCategory.containsKey(player.getUniqueId()) ? shopCategory.get(player.getUniqueId()) : "Quick Buy");
    }

    @SuppressWarnings("deprecation")
    private static ItemStack potion(int legacyData) {
        ItemStack item = new ItemStack(Items.material("POTION"), 1, (short) legacyData);
        return item;
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
        } else if (name.startsWith("Forge") && arena.forgeLevel(team) < 4) {
            int level = arena.forgeLevel(team);
            if (pay(player, Material.DIAMOND, level + 2)) arena.forgeLevel(team, level + 1);
        } else if (name.startsWith("Maniac Miner") && arena.hasteLevel(team) < 2) {
            int level = arena.hasteLevel(team);
            if (pay(player, Material.DIAMOND, level == 0 ? 2 : 4)) {
                arena.hasteLevel(team, level + 1);
                for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) manager.applyHaste(member, team);
            }
        } else if (name.equals("Heal Pool") && !arena.healPool(team) && pay(player, Material.DIAMOND, 3)) {
            arena.healPool(team, true);
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

    /** Replace a weaker sword when upgrading; otherwise add so teammates can receive extras. */
    private static void giveSword(Player player, ItemStack sword) {
        int newRank = GameRules.swordRank(sword.getType().name());
        int replaceSlot = -1;
        int replaceRank = Integer.MAX_VALUE;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || !GameRules.isSword(stack.getType().name())) continue;
            int rank = GameRules.swordRank(stack.getType().name());
            if (rank < newRank && rank < replaceRank) {
                replaceSlot = i;
                replaceRank = rank;
            }
        }
        if (replaceSlot >= 0) player.getInventory().setItem(replaceSlot, sword);
        else give(player, sword);
    }

    private void removeNpcPlacers(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (npcPlacer(player.getInventory().getItem(slot)) != null) player.getInventory().setItem(slot, null);
        }
    }

    private static ItemStack npcItem(GameType type, LobbySettings draft) {
        LobbySettings.NpcSettings npc = draft.npc(type);
        return Items.named(new ItemStack(Material.ARMOR_STAND), ChatColor.GOLD + "Place " + type.displayName() + " NPC",
            npc.location() == null ? ChatColor.RED + "Not placed" : ChatColor.GREEN + "Placed as " + appearance(npc),
            ChatColor.GRAY + "Shift-left-click the placed NPC to edit");
    }

    private static String appearance(LobbySettings.NpcSettings npc) {
        return npc.human() ? "Human" + (npc.skin() == null ? "" : " (" + npc.skin() + ")") : (npc.baby() ? "Baby " : "Adult ") + npc.entityType().name();
    }

    private static ItemStack setupItem(Material material, String name, boolean set) { return Items.named(new ItemStack(material), (set ? ChatColor.GREEN : ChatColor.YELLOW) + name, status(set)); }
    private static String status(boolean set) { return set ? ChatColor.GREEN + "Set" : ChatColor.RED + "Missing"; }
    private static ItemStack offer(ItemStack icon, String name, int amount, String currency) { return Items.named(icon, ChatColor.GREEN + name, ChatColor.GRAY + "Cost: " + amount + " " + currency, ChatColor.YELLOW + "Click to purchase"); }
    private static ItemStack sword(Material material, boolean sharp) {
        ItemStack item = Items.unbreakable(new ItemStack(material));
        if (sharp) Enchantments.add(item, 1, "SHARPNESS", "DAMAGE_ALL");
        return item;
    }
    private static void enchantSwords(Player player) { for (ItemStack item : player.getInventory().getContents()) if (item != null && item.getType().name().endsWith("_SWORD")) Enchantments.add(item, 1, "SHARPNESS", "DAMAGE_ALL"); }
    private static String roman(int level) { return new String[] {"I", "II", "III", "IV", "MAX"}[Math.min(level - 1, 4)]; }
    private boolean admin(Player player) { return plugin.isAdmin(player); }

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
