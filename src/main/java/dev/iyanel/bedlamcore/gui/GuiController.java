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
import dev.iyanel.bedlamcore.compat.Sounds;
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
    public static final String SHOP_TITLE = ChatColor.DARK_GRAY + "Quick Buy";
    public static final String UPGRADES_TITLE = ChatColor.DARK_GRAY + "Upgrades & Traps";
    public static final String PLAY_TITLE_PREFIX = "Play Bed Wars ";
    public static final String MAP_TITLE_PREFIX = "Map Selector ";

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
        openGui(player, inventory);
    }

    public void openAdmin(Player player) {
        if (!admin(player)) return;
        Inventory inventory = Bukkit.createInventory(null, 27, ADMIN_TITLE);
        inventory.setItem(10, Items.named(new ItemStack(Material.NETHER_STAR), ChatColor.GREEN + "Lobby Setup", status(plugin.lobby().complete())));
        inventory.setItem(12, Items.named(new ItemStack(Items.material("GRASS_BLOCK", "GRASS")), ChatColor.AQUA + "Game World Setup", ChatColor.GRAY + "Create, edit, teleport, delete"));
        inventory.setItem(14, Items.named(new ItemStack(Material.COMPASS), ChatColor.YELLOW + "Current World", ChatColor.WHITE + player.getWorld().getName()));
        ArenaManager manager = plugin.games().arenaInWorld(player.getWorld().getName());
        if (manager != null) inventory.setItem(16, Items.named(new ItemStack(Material.MAP), ChatColor.GOLD + "Edit Current Game", ChatColor.GRAY + manager.arena().settings().gameType().displayName()));
        openGui(player, inventory);
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
        openGui(player, inventory);
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
        openGui(player, inventory);
    }

    private void openWorldActions(Player player, String id) {
        ArenaManager manager = plugin.games().byId(id);
        if (manager == null) { openWorlds(player); return; }
        selectedArena.put(player.getUniqueId(), id);
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "World Actions");
        inventory.setItem(10, Items.named(new ItemStack(Items.material("ENDER_PEARL")), ChatColor.GREEN + "Teleport & Setup"));
        inventory.setItem(13, Items.named(new ItemStack(Material.PAPER), ChatColor.YELLOW + "Status", manager.arena().settings().validate().isEmpty() ? ChatColor.GREEN + "Ready" : ChatColor.RED + "Incomplete"));
        inventory.setItem(16, Items.named(new ItemStack(Material.TNT), ChatColor.RED + "Delete World", ChatColor.DARK_RED + "Requires confirmation"));
        openGui(player, inventory);
    }

    private void confirmDelete(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Confirm World Delete");
        inventory.setItem(11, Items.named(new ItemStack(Material.BARRIER), ChatColor.GRAY + "Keep World"));
        inventory.setItem(15, Items.named(new ItemStack(Material.TNT), ChatColor.RED + "Confirm Delete", ChatColor.DARK_RED + "This cannot be undone"));
        openGui(player, inventory);
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
        openGui(player, inventory);
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
        inventory.setItem(16, setupItem(Material.CHEST, "Set Team Chest", settings.teamChest() != null));
        inventory.setItem(17, setupItem(Items.material("ENDER_CHEST"), "Set Ender Chest", settings.enderChest() != null));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        openGui(player, inventory);
    }

    public void openQueue(Player player, GameType type) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + PLAY_TITLE_PREFIX + type.displayName());
        inventory.setItem(11, Items.named(new ItemStack(Items.material("RED_BED", "BED")),
            ChatColor.GREEN + "Bed Wars " + type.displayName(),
            ChatColor.WHITE + "Play a game of Bed Wars " + type.displayName() + ".",
            ChatColor.WHITE + (type == GameType.SOLO ? "One player per team." : "Two players per team."),
            "",
            ChatColor.YELLOW + "Click to play!"));
        inventory.setItem(15, Items.named(new ItemStack(Items.material("OAK_SIGN", "SIGN")),
            ChatColor.GREEN + "Map Selector (" + type.displayName() + ")",
            ChatColor.WHITE + "Pick which map you want to play!",
            "",
            ChatColor.YELLOW + "Click to browse!"));
        openGui(player, inventory);
    }

    public void openMapSelector(Player player, GameType type) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + MAP_TITLE_PREFIX + type.displayName());
        inventory.setItem(4, Items.named(new ItemStack(Items.material("RED_BED", "BED")),
            ChatColor.GREEN + "Random Map",
            ChatColor.WHITE + "Quick join any waiting " + type.displayName() + " game.",
            "",
            ChatColor.YELLOW + "Click to play!"));
        int slot = 9;
        for (ArenaManager manager : plugin.games().arenas()) {
            Arena arena = manager.arena();
            if (arena.settings().gameType() != type || !arena.settings().validate().isEmpty()) continue;
            if (arena.state() != Arena.State.WAITING && arena.state() != Arena.State.COUNTDOWN) continue;
            inventory.setItem(slot++, Items.named(new ItemStack(Items.material("OAK_SIGN", "SIGN")),
                ChatColor.GREEN + arena.settings().id(),
                ChatColor.WHITE + "Players: " + arena.players().size() + "/" + arena.settings().maximumPlayers(),
                ChatColor.GRAY + "State: " + arena.state().name(),
                "",
                ChatColor.YELLOW + "Click to join!"));
            if (slot >= inventory.getSize()) break;
        }
        openGui(player, inventory);
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
        else if (cleanTitle.startsWith("Play Bed Wars ")) {
            GameType type = GameType.parse(cleanTitle.substring("Play Bed Wars ".length()));
            clickPlay(player, type, name);
        } else if (cleanTitle.startsWith("Map Selector ")) {
            GameType type = GameType.parse(cleanTitle.substring("Map Selector ".length()));
            clickMap(player, type, name);
        } else if (cleanTitle.equals("Solo Games")) clickQueue(player, GameType.SOLO, name);
        else if (cleanTitle.equals("Doubles Games")) clickQueue(player, GameType.DOUBLES, name);
        else if (cleanTitle.equals("Quick Buy") || cleanTitle.equals("Item Shop")) buy(player, name);
        else if (cleanTitle.equals("Upgrades & Traps") || cleanTitle.equals("Team Upgrades")) upgrade(player, name);
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
            // Save while world is still loaded; remove/unload first discarded map edits and nullified Location worlds.
            ArenaManager existing = plugin.games().byId(settings.id());
            if (existing != null) existing.prepareWorldSave();
            World world = Bukkit.getWorld(settings.worldName());
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Game world " + settings.worldName() + " is not loaded.");
                return;
            }
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
        else if (name.equals("Set Team Chest")) settings.teamChest(player.getLocation());
        else if (name.equals("Set Ender Chest")) settings.enderChest(player.getLocation());
        else if (name.equals("Set Bed (look at it)")) {
            Block target = targetBlock(player, 6);
            if (target == null || !target.getType().name().contains("BED")) { player.sendMessage(ChatColor.RED + "Look directly at a bed within six blocks."); return; }
            settings.bed(target.getLocation());
        } else if (name.equals("Back")) { openArenaSetup(player); return; }
        openTeamSetup(player, team);
    }

    private void clickPlay(Player player, GameType type, String name) {
        if (name.equals("Bed Wars " + type.displayName())) plugin.games().quickJoin(player, type);
        else if (name.startsWith("Map Selector")) openMapSelector(player, type);
    }

    private void clickMap(Player player, GameType type, String name) {
        if (name.equals("Random Map")) plugin.games().quickJoin(player, type);
        else {
            ArenaManager manager = plugin.games().byId(name);
            if (manager != null) { plugin.games().leave(player); manager.join(player); }
        }
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
        openGui(player, inventory);
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
        String category = shopCategory.get(player.getUniqueId());
        if (category == null || category.equals("Traps")) category = "Quick Buy";
        openShopCategory(player, category);
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
        openGui(player, inventory);
    }

    private void clickSpectate(Player player, String name) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null || name == null || name.isEmpty()) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.getName().equals(name)) continue;
            if (!manager.arena().contains(target.getUniqueId()) || manager.arena().eliminated().contains(target.getUniqueId())) return;
            player.teleport(target);
            manager.applySoftSpectate(player);
            player.sendMessage(ChatColor.YELLOW + "Spectating " + target.getName());
            player.closeInventory();
            return;
        }
    }

    private void openShopCategory(Player player, String category) {
        shopCategory.put(player.getUniqueId(), category);
        Inventory inventory = Bukkit.createInventory(null, 54, SHOP_TITLE);
        inventory.setItem(0, categoryTab(Material.NETHER_STAR, "Quick Buy", category));
        inventory.setItem(1, categoryTab(Items.material("WHITE_TERRACOTTA", "STAINED_CLAY"), "Blocks", category));
        inventory.setItem(2, categoryTab(Items.material("GOLDEN_SWORD", "GOLD_SWORD"), "Melee", category));
        inventory.setItem(3, categoryTab(Items.material("CHAINMAIL_BOOTS"), "Armor", category));
        inventory.setItem(4, categoryTab(Items.material("STONE_PICKAXE"), "Tools", category));
        inventory.setItem(5, categoryTab(Material.BOW, "Ranged", category));
        inventory.setItem(6, categoryTab(Items.material("BREWING_STAND", "BREWING_STAND_ITEM"), "Potions", category));
        inventory.setItem(7, categoryTab(Material.TNT, "Utility", category));
        ItemStack gray = Items.named(Items.stack("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 7), " ");
        ItemStack lime = Items.named(Items.stack("LIME_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 5), " ");
        String[] cats = {"Quick Buy", "Blocks", "Melee", "Armor", "Tools", "Ranged", "Potions", "Utility"};
        for (int i = 0; i < 8; i++) inventory.setItem(9 + i, cats[i].equals(category) ? lime : gray);
        inventory.setItem(17, gray);
        ArenaManager manager = plugin.games().arena(player);
        TeamColor team = manager == null ? TeamColor.RED : manager.arena().team(player.getUniqueId());
        if (team == null) team = TeamColor.RED;
        Arena arena = manager == null ? null : manager.arena();
        if (category.equals("Quick Buy") || category.equals("Blocks")) {
            inventory.setItem(19, shopOffer(player, team.wool(16), "Wool", "16 Wool", 4, Material.IRON_INGOT,
                ChatColor.GRAY + "Basic building block", ChatColor.GRAY + "Colored to your team"));
            inventory.setItem(20, shopOffer(player, Items.stack("WHITE_TERRACOTTA", "STAINED_CLAY", 16, (short) 0), "Hardened Clay", "16 Hardened Clay", 12, Material.IRON_INGOT,
                ChatColor.GRAY + "Sturdier than wool"));
            inventory.setItem(21, shopOffer(player, Items.stack("GLASS", "GLASS", 4, (short) 0), "Blast-Proof Glass", "4 Blast-Proof Glass", 12, Material.IRON_INGOT,
                ChatColor.GRAY + "See-through defense"));
            inventory.setItem(22, shopOffer(player, new ItemStack(Items.material("END_STONE", "ENDER_STONE"), 12), "End Stone", "12 End Stone", 24, Material.IRON_INGOT,
                ChatColor.GRAY + "Tough island block"));
            inventory.setItem(23, shopOffer(player, new ItemStack(Material.LADDER, 8), "Ladder", "8 Ladders", 4, Material.IRON_INGOT,
                ChatColor.GRAY + "Climb enemy walls"));
            inventory.setItem(24, shopOffer(player, Items.stack("OAK_PLANKS", "WOOD", 16, (short) 0), "Wood", "16 Oak Planks", 4, Material.GOLD_INGOT,
                ChatColor.GRAY + "Cheap bridging wood"));
            if (category.equals("Blocks")) {
                inventory.setItem(25, shopOffer(player, new ItemStack(Material.OBSIDIAN, 4), "Obsidian", "4 Obsidian", 4, Material.EMERALD,
                    ChatColor.GRAY + "Blast-resistant cover"));
                inventory.setItem(28, shopOffer(player, new ItemStack(Material.ICE, 8), "Ice", "8 Ice", 8, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Slippery flooring"));
            }
        }
        if (category.equals("Quick Buy") || category.equals("Melee")) {
            int base = category.equals("Melee") ? 19 : 28;
            inventory.setItem(base, shopOffer(player, new ItemStack(Items.material("STONE_SWORD")), "Stone Sword", "Stone Sword", 10, Material.IRON_INGOT,
                ChatColor.GRAY + "Replaces a weaker sword"));
            inventory.setItem(base + 1, shopOffer(player, new ItemStack(Items.material("IRON_SWORD")), "Iron Sword", "Iron Sword", 7, Material.GOLD_INGOT,
                ChatColor.GRAY + "Replaces a weaker sword"));
            inventory.setItem(base + 2, shopOffer(player, new ItemStack(Items.material("DIAMOND_SWORD")), "Diamond Sword", "Diamond Sword", 4, Material.EMERALD,
                ChatColor.GRAY + "Replaces a weaker sword"));
            inventory.setItem(base + 3, shopOffer(player, new ItemStack(Items.material("STICK")), "Knockback Stick", "Knockback Stick", 5, Material.GOLD_INGOT,
                ChatColor.GRAY + "Knockback I stick"));
        }
        if (category.equals("Quick Buy") || category.equals("Armor")) {
            int base = category.equals("Armor") ? 19 : 37;
            inventory.setItem(base, shopOffer(player, new ItemStack(Items.material("CHAINMAIL_BOOTS")), "Chainmail Armor", "Permanent Chainmail Armor", 40, Material.IRON_INGOT,
                ChatColor.GRAY + "Permanent chainmail legs + boots"));
            inventory.setItem(base + 1, shopOffer(player, new ItemStack(Items.material("IRON_BOOTS")), "Iron Armor", "Permanent Iron Armor", 12, Material.GOLD_INGOT,
                ChatColor.GRAY + "Permanent iron helmet + chest"));
            inventory.setItem(base + 2, shopOffer(player, new ItemStack(Items.material("DIAMOND_BOOTS")), "Diamond Armor", "Permanent Diamond Armor", 6, Material.EMERALD,
                ChatColor.GRAY + "Permanent diamond helmet + chest"));
        }
        if (category.equals("Tools") || category.equals("Quick Buy")) {
            int base = category.equals("Tools") ? 19 : 29;
            if (category.equals("Tools")) {
                putToolOffers(inventory, player, arena, 19, 20, 21);
            } else {
                putShearsOffer(inventory, player, arena, base);
            }
        }
        if (category.equals("Ranged") || category.equals("Quick Buy")) {
            int base = category.equals("Ranged") ? 19 : 30;
            if (category.equals("Ranged")) {
                inventory.setItem(19, shopOffer(player, new ItemStack(Material.BOW), "Bow", "Bow", 12, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Unbreakable bow"));
                inventory.setItem(20, shopOffer(player, new ItemStack(Material.ARROW, 8), "Arrows", "8 Arrows", 2, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Ammunition"));
                ItemStack punch = new ItemStack(Material.BOW);
                Enchantments.add(punch, 1, "ARROW_KNOCKBACK", "PUNCH");
                inventory.setItem(21, shopOffer(player, punch, "Punch Bow", "Punch Bow", 24, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Bow with Punch I"));
            } else {
                inventory.setItem(base, shopOffer(player, new ItemStack(Material.BOW), "Bow", "Bow", 12, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Unbreakable bow"));
            }
        }
        if (category.equals("Potions")) {
            inventory.setItem(19, shopOffer(player, new ItemStack(Items.material("POTION")), "Speed II Potion (45 seconds)", "Speed Potion", 1, Material.EMERALD,
                ChatColor.GRAY + "Speed II for 45s"));
            inventory.setItem(20, shopOffer(player, new ItemStack(Items.material("POTION")), "Jump Boost V (45 seconds)", "Jump Potion", 1, Material.EMERALD,
                ChatColor.GRAY + "Jump Boost for 45s"));
            inventory.setItem(21, shopOffer(player, new ItemStack(Items.material("POTION")), "Invisibility Potion (30 seconds)", "Invisibility Potion", 2, Material.EMERALD,
                ChatColor.GRAY + "Complete invisibility for 30s"));
        }
        if (category.equals("Utility") || category.equals("Quick Buy")) {
            if (category.equals("Utility")) {
                inventory.setItem(19, shopOffer(player, new ItemStack(Items.material("GOLDEN_APPLE")), "Golden Apple", "Golden Apple", 3, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Well-rounded healing"));
                inventory.setItem(20, shopOffer(player, new ItemStack(Items.material("SNOWBALL", "SNOW_BALL"), 16), "Snowball", "16 Snowballs", 16, Material.IRON_INGOT,
                    ChatColor.GRAY + "Slow projectiles"));
                inventory.setItem(21, shopOffer(player, new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")), "Fireball", "Fireball", 40, Material.IRON_INGOT,
                    ChatColor.GRAY + "Explosive knockback charge"));
                inventory.setItem(22, shopOffer(player, new ItemStack(Material.TNT), "TNT", "TNT", 4, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Auto-ignites when placed"));
                inventory.setItem(23, shopOffer(player, new ItemStack(Material.ENDER_PEARL), "Ender Pearl", "Ender Pearl", 4, Material.EMERALD,
                    ChatColor.GRAY + "Teleport across the map"));
                inventory.setItem(24, shopOffer(player, new ItemStack(Material.WATER_BUCKET), "Water Bucket", "Water Bucket", 3, Material.GOLD_INGOT,
                    ChatColor.GRAY + "One-use water place"));
                inventory.setItem(25, shopOffer(player, new ItemStack(Items.material("MILK_BUCKET")), "Magic Milk", "Magic Milk", 4, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Brief trap immunity"));
                inventory.setItem(28, shopOffer(player, new ItemStack(Material.SPONGE, 4), "Sponge", "4 Sponges", 3, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Soak up water"));
                inventory.setItem(29, shopOffer(player, new ItemStack(Material.EGG), "Bridge Egg", "Bridge Egg", 1, Material.EMERALD,
                    ChatColor.GRAY + "Throws a team-wool bridge", ChatColor.GRAY + "along its flight path"));
            } else {
                inventory.setItem(39, shopOffer(player, new ItemStack(Material.TNT), "TNT", "TNT", 4, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Auto-ignites when placed"));
                inventory.setItem(40, shopOffer(player, new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")), "Fireball", "Fireball", 40, Material.IRON_INGOT,
                    ChatColor.GRAY + "Explosive knockback charge"));
                inventory.setItem(41, shopOffer(player, new ItemStack(Material.WATER_BUCKET), "Water Bucket", "Water Bucket", 3, Material.GOLD_INGOT,
                    ChatColor.GRAY + "One-use water place"));
                inventory.setItem(42, shopOffer(player, new ItemStack(Items.material("GOLDEN_APPLE")), "Golden Apple", "Golden Apple", 3, Material.GOLD_INGOT,
                    ChatColor.GRAY + "Well-rounded healing"));
                inventory.setItem(43, shopOffer(player, new ItemStack(Material.ENDER_PEARL), "Ender Pearl", "Ender Pearl", 4, Material.EMERALD,
                    ChatColor.GRAY + "Teleport across the map"));
            }
        }
        inventory.setItem(48, Items.named(new ItemStack(Material.COMPASS), ChatColor.GREEN + "Quick Buy Settings", ChatColor.GRAY + "Coming soon"));
        inventory.setItem(49, Items.named(new ItemStack(Items.material("FIREWORK_STAR", "FIREWORK_CHARGE")), ChatColor.GREEN + "Close", ChatColor.YELLOW + "Click to close"));
        openGui(player, inventory);
    }

    private void putShearsOffer(Inventory inventory, Player player, Arena arena, int slot) {
        if (arena != null && arena.shearsOwned(player.getUniqueId())) {
            inventory.setItem(slot, Items.named(new ItemStack(Items.material("SHEARS")), ChatColor.GREEN + "Permanent Shears",
                ChatColor.GREEN + "UNLOCKED", ChatColor.GRAY + "Kept on every respawn"));
            return;
        }
        inventory.setItem(slot, shopOffer(player, new ItemStack(Items.material("SHEARS")), "Permanent Shears", "Shears", 20, Material.IRON_INGOT,
            ChatColor.GRAY + "Permanent item", ChatColor.DARK_GRAY + "Always respawn with shears"));
    }

    private void putToolOffers(Inventory inventory, Player player, Arena arena, int shearsSlot, int pickSlot, int axeSlot) {
        putShearsOffer(inventory, player, arena, shearsSlot);
        int pick = arena == null ? 0 : arena.pickaxeTier(player.getUniqueId());
        int axe = arena == null ? 0 : arena.axeTier(player.getUniqueId());
        inventory.setItem(pickSlot, nextToolOffer(player, true, pick));
        inventory.setItem(axeSlot, nextToolOffer(player, false, axe));
    }

    private ItemStack nextToolOffer(Player player, boolean pickaxe, int current) {
        int next = GameRules.nextToolTier(current);
        if (next < 0) {
            ItemStack max = pickaxe ? ArenaManager.toolPickaxe(GameRules.TOOL_TIER_MAX) : ArenaManager.toolAxe(GameRules.TOOL_TIER_MAX);
            String label = pickaxe ? "Diamond Pickaxe" : "Diamond Axe";
            return Items.named(max, ChatColor.GREEN + label, ChatColor.GREEN + "MAXED",
                ChatColor.GRAY + "Upgradable", ChatColor.DARK_GRAY + "Loses 1 tier on death (min wooden)");
        }
        Material currency = next <= 2 ? Material.IRON_INGOT : Material.GOLD_INGOT;
        int cost = next <= 2 ? 10 : (next == 3 ? 3 : 6);
        ItemStack icon = pickaxe ? ArenaManager.toolPickaxe(next) : ArenaManager.toolAxe(next);
        String title = toolTierLabel(next) + (pickaxe ? " Pickaxe" : " Axe");
        return shopOffer(player, icon, title, title, cost, currency,
            ChatColor.GRAY + "Upgradable tool",
            ChatColor.DARK_GRAY + "Loses 1 tier on death",
            ChatColor.DARK_GRAY + "Respawns at current tier once owned");
    }

    private static String toolTierLabel(int tier) {
        switch (tier) {
            case 2: return "Stone";
            case 3: return "Iron";
            case 4: return "Diamond";
            default: return "Wooden";
        }
    }

    private static ItemStack categoryTab(Material material, String name, String selected) {
        boolean on = name.equals(selected);
        return Items.named(new ItemStack(material), (on ? ChatColor.GREEN : ChatColor.GRAY) + name,
            on ? ChatColor.GRAY + "Selected" : ChatColor.GRAY + "Click to browse");
    }

    public void openUpgrades(Player player) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 54, UPGRADES_TITLE);
        ItemStack pane = Items.named(Items.stack("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 7), " ");
        for (int row = 0; row < 6; row++) inventory.setItem(row * 9 + 4, pane);
        for (int i = 27; i < 36; i++) inventory.setItem(i, pane);

        inventory.setItem(10, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Sharpened Swords",
            arena.sharpness(team) ? ChatColor.GREEN + "Purchased" : costLine(4, Material.DIAMOND),
            ChatColor.GRAY + "Your team gets Sharpness I", ChatColor.GRAY + "on all swords"));
        int level = arena.protection(team);
        int cost = new int[] {2, 4, 8, 16}[Math.min(level, 3)];
        inventory.setItem(11, Items.named(new ItemStack(Material.IRON_CHESTPLATE), ChatColor.AQUA + "Reinforced Armor " + roman(level + 1),
            level >= 4 ? ChatColor.GREEN + "Maximum level" : costLine(cost, Material.DIAMOND),
            ChatColor.GRAY + "Protection on team armor"));
        int haste = arena.hasteLevel(team);
        inventory.setItem(12, Items.named(new ItemStack(Items.material("GOLDEN_PICKAXE", "GOLD_PICKAXE")), ChatColor.AQUA + "Maniac Miner " + roman(haste + 1),
            haste >= 2 ? ChatColor.GREEN + "Maximum level" : costLine(haste == 0 ? 2 : 4, Material.DIAMOND),
            ChatColor.GRAY + "Haste for your whole team"));
        int forge = arena.forgeLevel(team);
        inventory.setItem(19, Items.named(new ItemStack(Material.FURNACE), ChatColor.AQUA + "Iron Forge " + roman(forge + 1),
            forge >= 4 ? ChatColor.GREEN + "Maximum level" : costLine(forge + 2, Material.DIAMOND),
            ChatColor.GRAY + "Faster iron/gold forge", ChatColor.DARK_GRAY + "L2+ rare diamond/emerald"));
        inventory.setItem(20, Items.named(new ItemStack(Items.material("BEACON")), ChatColor.AQUA + "Heal Pool",
            arena.healPool(team) ? ChatColor.GREEN + "Purchased" : costLine(3, Material.DIAMOND),
            ChatColor.GRAY + "Regen + green particles at base"));
        inventory.setItem(21, Items.named(new ItemStack(Items.material("DRAGON_EGG")), ChatColor.AQUA + "Dragon Buff",
            arena.dragonBuff(team) ? ChatColor.GREEN + "Purchased" : costLine(5, Material.DIAMOND),
            ChatColor.GRAY + "+2 hearts max health for your team"));
        inventory.setItem(22, Items.named(new ItemStack(Items.material("FEATHER")), ChatColor.AQUA + "Cushioned Boots",
            arena.cushionedBoots(team) ? ChatColor.GREEN + "Purchased" : costLine(2, Material.DIAMOND),
            ChatColor.GRAY + "Feather Falling IV on team boots"));

        int trapCost = GameRules.trapDiamondCost(arena.traps(team).size());
        String queueLine = ChatColor.GRAY + "Queue: " + arena.traps(team).size() + "/" + GameRules.TRAP_QUEUE_MAX;
        inventory.setItem(14, trapOffer("Blindness Trap", Items.material("EYE_OF_ENDER", "ENDER_EYE"), trapCost, queueLine,
            ChatColor.GRAY + "Blind enemies who enter your base"));
        inventory.setItem(15, trapOffer("Counter-Offensive Trap", Items.material("FEATHER"), trapCost, queueLine,
            ChatColor.GRAY + "Speed II + Jump for allies near base"));
        inventory.setItem(16, trapOffer("Alarm Trap", Items.material("REDSTONE_TORCH", "REDSTONE_TORCH_ON"), trapCost, queueLine,
            ChatColor.GRAY + "Alerts your team when enemies enter"));
        inventory.setItem(23, trapOffer("Miner Fatigue Trap", Items.material("IRON_PICKAXE"), trapCost, queueLine,
            ChatColor.GRAY + "Mining Fatigue on base invaders"));
        inventory.setItem(24, trapOffer("Reveal Trap", Items.material("TRIPWIRE_HOOK"), trapCost, queueLine,
            ChatColor.GRAY + "Strip invisibility from invaders"));

        List<Arena.TrapType> traps = arena.traps(team);
        for (int i = 0; i < GameRules.TRAP_QUEUE_MAX; i++) {
            if (i < traps.size()) {
                inventory.setItem(39 + i, Items.named(team.wool(1), ChatColor.GREEN + "Trap #" + (i + 1),
                    ChatColor.WHITE + traps.get(i).displayName()));
            } else {
                inventory.setItem(39 + i, Items.named(Items.stack("GRAY_WOOL", "WOOL", 1, (short) 7), ChatColor.GRAY + "Trap slot #" + (i + 1),
                    ChatColor.DARK_GRAY + "Buy a trap above"));
            }
        }
        openGui(player, inventory);
    }

    private static ItemStack trapOffer(String name, Material icon, int cost, String queueLine, String... desc) {
        List<String> lore = new ArrayList<String>();
        lore.add(costLine(cost, Material.DIAMOND));
        for (String line : desc) lore.add(line);
        lore.add(queueLine);
        lore.add(ChatColor.YELLOW + "Click to purchase");
        return Items.named(new ItemStack(icon), ChatColor.YELLOW + name, lore.toArray(new String[0]));
    }

    private void buy(Player player, String name) {
        if (name.equals("Quick Buy") || name.equals("Blocks") || name.equals("Melee") || name.equals("Armor")
            || name.equals("Tools") || name.equals("Ranged") || name.equals("Potions") || name.equals("Utility")) {
            openShopCategory(player, name);
            return;
        }
        if (name.equals("Close") || name.equals("Quick Buy Settings")) { player.closeInventory(); return; }
        if (name.equals("MAXED") || name.contains("UNLOCKED")) return;
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING) return;
        if (name.equals("16 Wool") && pay(player, Material.IRON_INGOT, 4)) give(player, team.wool(16));
        else if (name.equals("16 Hardened Clay") && pay(player, Material.IRON_INGOT, 12)) give(player, Items.stack("WHITE_TERRACOTTA", "STAINED_CLAY", 16, (short) 0));
        else if (name.equals("4 Blast-Proof Glass") && pay(player, Material.IRON_INGOT, 12)) give(player, new ItemStack(Material.GLASS, 4));
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
            if (arena.cushionedBoots(team)) Enchantments.add(boots, 4, "PROTECTION_FALL", "FEATHER_FALLING");
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
        else if (name.equals("4 Obsidian") && pay(player, Material.EMERALD, 4)) give(player, new ItemStack(Material.OBSIDIAN, 4));
        else if (name.equals("8 Ice") && pay(player, Material.GOLD_INGOT, 8)) give(player, new ItemStack(Material.ICE, 8));
        else if ((name.endsWith(" Pickaxe") || name.endsWith(" Axe")) && buyToolUpgrade(player, arena, manager, name)) { /* done */ }
        else if ((name.equals("Shears") || name.equals("Permanent Shears")) && !arena.shearsOwned(player.getUniqueId()) && pay(player, Material.IRON_INGOT, 20)) {
            arena.shearsOwned(player.getUniqueId(), true);
            manager.giveOwnedTools(player);
        }
        else if (name.equals("Golden Apple") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Items.material("GOLDEN_APPLE")));
        else if (name.equals("16 Snowballs") && pay(player, Material.IRON_INGOT, 16)) give(player, new ItemStack(Items.material("SNOWBALL", "SNOW_BALL"), 16));
        else if (name.equals("TNT") && pay(player, Material.GOLD_INGOT, 4)) give(player, new ItemStack(Material.TNT));
        else if (name.equals("Fireball") && pay(player, Material.IRON_INGOT, 40)) give(player, new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL")));
        else if (name.equals("Ender Pearl") && pay(player, Material.EMERALD, 4)) give(player, new ItemStack(Material.ENDER_PEARL));
        else if (name.equals("Bow") && pay(player, Material.GOLD_INGOT, 12)) give(player, Items.unbreakable(new ItemStack(Material.BOW)));
        else if (name.equals("Punch Bow") && pay(player, Material.GOLD_INGOT, 24)) {
            ItemStack bow = Items.unbreakable(new ItemStack(Material.BOW));
            Enchantments.add(bow, 1, "ARROW_KNOCKBACK", "PUNCH");
            give(player, Items.named(bow, ChatColor.GREEN + "Punch Bow"));
        }
        else if (name.equals("8 Arrows") && pay(player, Material.GOLD_INGOT, 2)) give(player, new ItemStack(Material.ARROW, 8));
        else if (name.equals("Water Bucket") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Material.WATER_BUCKET));
        else if (name.equals("Magic Milk") && pay(player, Material.GOLD_INGOT, 4)) give(player, new ItemStack(Items.material("MILK_BUCKET")));
        else if (name.equals("4 Sponges") && pay(player, Material.GOLD_INGOT, 3)) give(player, new ItemStack(Material.SPONGE, 4));
        else if (name.equals("Bridge Egg") && pay(player, Material.EMERALD, 1)) {
            give(player, Items.named(new ItemStack(Material.EGG), ChatColor.GREEN + "Bridge Egg",
                ChatColor.GRAY + "Throws a team-wool bridge", ChatColor.GRAY + "along its flight path"));
        }
        else if (name.equals("Speed Potion") && pay(player, Material.EMERALD, 1)) give(player, potion(8194));
        else if (name.equals("Jump Potion") && pay(player, Material.EMERALD, 1)) give(player, potion(8203));
        else if (name.equals("Invisibility Potion") && pay(player, Material.EMERALD, 2)) give(player, potion(8206));
        openShopCategory(player, shopCategory.containsKey(player.getUniqueId()) ? shopCategory.get(player.getUniqueId()) : "Quick Buy");
    }

    private boolean buyToolUpgrade(Player player, Arena arena, ArenaManager manager, String name) {
        boolean pickaxe = name.endsWith(" Pickaxe");
        int wanted = toolNameTier(name);
        if (wanted <= 0) return false;
        UUID uuid = player.getUniqueId();
        int current = pickaxe ? arena.pickaxeTier(uuid) : arena.axeTier(uuid);
        int next = GameRules.nextToolTier(current);
        if (next < 0 || wanted != next) return false;
        Material currency = next <= 2 ? Material.IRON_INGOT : Material.GOLD_INGOT;
        int cost = next <= 2 ? 10 : (next == 3 ? 3 : 6);
        if (!pay(player, currency, cost)) return true;
        if (pickaxe) arena.pickaxeTier(uuid, next);
        else arena.axeTier(uuid, next);
        manager.giveOwnedTools(player);
        return true;
    }

    private static int toolNameTier(String name) {
        if (name.startsWith("Wooden ")) return 1;
        if (name.startsWith("Stone ")) return 2;
        if (name.startsWith("Iron ")) return 3;
        if (name.startsWith("Diamond ")) return 4;
        return -1;
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
        } else if (name.startsWith("Iron Forge") && arena.forgeLevel(team) < 4) {
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
        } else if (name.equals("Dragon Buff") && !arena.dragonBuff(team) && pay(player, Material.DIAMOND, 5)) {
            arena.dragonBuff(team, true);
            for (Player member : Bukkit.getOnlinePlayers()) {
                if (team != arena.team(member.getUniqueId()) || arena.eliminated().contains(member.getUniqueId())) continue;
                try {
                    member.setMaxHealth(24.0);
                    member.setHealth(Math.min(24.0, member.getHealth() + 4.0));
                } catch (Throwable ignored) { }
            }
        } else if (name.equals("Cushioned Boots") && !arena.cushionedBoots(team) && pay(player, Material.DIAMOND, 2)) {
            arena.cushionedBoots(team, true);
            for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) manager.equipArmor(member, team);
        } else if (buyTrap(player, arena, team, name)) {
            /* queued */
        }
        openUpgrades(player);
    }

    private boolean buyTrap(Player player, Arena arena, TeamColor team, String name) {
        Arena.TrapType type = null;
        if (name.equals("Alarm Trap")) type = Arena.TrapType.ALARM;
        else if (name.equals("Blindness Trap")) type = Arena.TrapType.BLINDNESS;
        else if (name.equals("Counter-Offensive Trap")) type = Arena.TrapType.COUNTER_OFFENSIVE;
        else if (name.equals("Miner Fatigue Trap")) type = Arena.TrapType.MINER_FATIGUE;
        else if (name.equals("Reveal Trap")) type = Arena.TrapType.REVEAL;
        if (type == null) return false;
        int cost = GameRules.trapDiamondCost(arena.traps(team).size());
        if (!pay(player, Material.DIAMOND, cost)) return true;
        if (!arena.enqueueTrap(team, type)) player.sendMessage(ChatColor.RED + "Trap queue is full.");
        return true;
    }

    private boolean pay(Player player, Material currency, int amount) {
        if (!player.getInventory().containsAtLeast(new ItemStack(currency), amount)) {
            player.sendMessage(ChatColor.RED + "You do not have enough " + currency.name().toLowerCase().replace('_', ' ') + ".");
            Sounds.cannotAfford(player);
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
        Sounds.purchase(player);
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

    private ItemStack shopOffer(Player player, ItemStack icon, String title, String buyName, int amount, Material currency, String... extra) {
        boolean afford = player.getInventory().containsAtLeast(new ItemStack(currency), amount);
        List<String> lore = new ArrayList<String>();
        lore.add(costLine(amount, currency));
        for (String line : extra) lore.add(line);
        if (!afford) lore.add(ChatColor.RED + "You don't have enough " + currencyLabel(currency) + "!");
        else lore.add(ChatColor.YELLOW + "Click to purchase!");
        return Items.named(icon, (afford ? ChatColor.GREEN : ChatColor.RED) + buyName, lore.toArray(new String[0]));
    }

    private static String costLine(int amount, Material currency) {
        return ChatColor.GRAY + "Cost: " + currencyColor(currency) + amount + " " + currencyLabel(currency);
    }

    private static ChatColor currencyColor(Material currency) {
        if (currency == Material.GOLD_INGOT) return ChatColor.GOLD;
        if (currency == Material.EMERALD) return ChatColor.GREEN;
        if (currency == Material.DIAMOND) return ChatColor.AQUA;
        return ChatColor.WHITE;
    }

    private static String currencyLabel(Material currency) {
        if (currency == Material.IRON_INGOT) return "Iron";
        if (currency == Material.GOLD_INGOT) return "Gold";
        if (currency == Material.EMERALD) return "Emerald";
        if (currency == Material.DIAMOND) return "Diamond";
        return currency.name();
    }

    private static ItemStack sword(Material material, boolean sharp) {
        ItemStack item = Items.unbreakable(new ItemStack(material));
        if (sharp) Enchantments.add(item, 1, "SHARPNESS", "DAMAGE_ALL");
        return item;
    }
    private static void enchantSwords(Player player) { for (ItemStack item : player.getInventory().getContents()) if (item != null && item.getType().name().endsWith("_SWORD")) Enchantments.add(item, 1, "SHARPNESS", "DAMAGE_ALL"); }
    private static String roman(int level) { return new String[] {"I", "II", "III", "IV", "MAX"}[Math.min(level - 1, 4)]; }
    private boolean admin(Player player) { return plugin.isAdmin(player); }

    /** Next-tick open: sync openInventory during InventoryClickEvent desyncs 1.8 (client chest vs ContainerPlayer size 45). */
    private void openGui(final Player player, final Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (player.isOnline()) player.openInventory(inventory);
            }
        });
    }

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
