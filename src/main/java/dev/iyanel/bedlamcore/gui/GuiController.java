package dev.iyanel.bedlamcore.gui;

import dev.iyanel.bedlamcore.BedlamCore;
import dev.iyanel.bedlamcore.arena.Arena;
import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.arena.GameType;
import dev.iyanel.bedlamcore.arena.TeamColor;
import dev.iyanel.bedlamcore.compat.Enchantments;
import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.compat.Items;
import dev.iyanel.bedlamcore.compat.Particles;
import dev.iyanel.bedlamcore.compat.Skins;
import dev.iyanel.bedlamcore.compat.Sounds;
import dev.iyanel.bedlamcore.cosmetics.CosmeticsService;
import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.ProfileStats;
import dev.iyanel.bedlamcore.game.StatsStore;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardCategory;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardEntry;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardService;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardWindow;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.lobby.LobbySettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class GuiController {
    public static final String MAIN_TITLE = ChatColor.DARK_GRAY + "Bedlam Menu";
    public static final String ADMIN_TITLE = ChatColor.DARK_GRAY + "Bedlam Setup";
    public static final String LOBBY_TITLE = ChatColor.DARK_GRAY + "Lobby Setup";
    public static final String WORLDS_TITLE = ChatColor.DARK_GRAY + "Game Worlds";
    public static final String IMPORT_TITLE = ChatColor.DARK_GRAY + "Import Maps";
    public static final String IMPORT_TYPE_TITLE = ChatColor.DARK_GRAY + "Import As";
    public static final String TEMPLATES_TITLE = ChatColor.DARK_GRAY + "Templates";
    public static final String TEMPLATE_TYPE_TITLE = ChatColor.DARK_GRAY + "Template Mode";
    public static final String LEADERBOARD_TITLE = ChatColor.DARK_GRAY + "Leaderboards";
    private static final String LEADERBOARD_CAT_PREFIX = "Leaderboard: ";
    private static final LeaderboardCategory[] LEADERBOARD_CATS = {
        LeaderboardCategory.WINS, LeaderboardCategory.KILLS, LeaderboardCategory.FINAL_KILLS,
        LeaderboardCategory.BEDS, LeaderboardCategory.WINSTREAK, LeaderboardCategory.KDR,
        LeaderboardCategory.FKDR, LeaderboardCategory.LEVEL, LeaderboardCategory.TOKENS
    };
    public static final String SHOP_TITLE = ChatColor.DARK_GRAY + "Quick Buy";
    public static final String UPGRADES_TITLE = ChatColor.DARK_GRAY + "Upgrades & Traps";
    public static final String PLAY_TITLE_PREFIX = "Play Bed Wars ";
    public static final String MAP_TITLE_PREFIX = "Map Selector ";
    private static final String BORDER_RADIUS_PREFIX = "Build Border Radius:";
    private static final String META_SETUP_MARKER = "bedlamSetupMarker";
    private static final String DELETE_STICK_LORE = "Bedlam Setup Delete";

    private final BedlamCore plugin;
    private final Map<UUID, LobbySettings> lobbyDrafts = new HashMap<UUID, LobbySettings>();
    private final Map<UUID, ArenaDraft> arenaDrafts = new HashMap<UUID, ArenaDraft>();
    private final Map<UUID, String> selectedArena = new HashMap<UUID, String>();
    private final Map<UUID, TeamColor> selectedTeam = new HashMap<UUID, TeamColor>();
    private final Map<UUID, GameType> selectedNpc = new HashMap<UUID, GameType>();
    private final Map<UUID, GameType> skinInputs = new ConcurrentHashMap<UUID, GameType>();
    /** Cosmetics/Profile NPC skin editor: which one ("COSMETICS"/"PROFILE") the player is editing / typing a skin for. */
    private final Map<UUID, String> specialEditTarget = new HashMap<UUID, String>();
    private final Map<UUID, String> specialSkinInputs = new ConcurrentHashMap<UUID, String>();
    private final Set<UUID> radiusInputs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> shopCategory = new HashMap<UUID, String>();
    /** Next shop-item click assigns this favorite index instead of buying. */
    private final Map<UUID, Integer> favoriteAssignSlot = new HashMap<UUID, Integer>();
    /** Hypixel flow: sneak-click an offer, then click its destination in Quick Buy. */
    private final Map<UUID, String> favoritePendingItem = new HashMap<UUID, String>();
    private final Set<UUID> guiBusy = new HashSet<UUID>();
    /** Current cosmetics-category + page per player (pagination for large catalogs). */
    private final Map<UUID, String> cosmeticCategory = new HashMap<UUID, String>();
    private final Map<UUID, Integer> cosmeticPage = new HashMap<UUID, Integer>();
    /** Leaderboard GUI state per player: selected mode (absent = Overall), category, page. */
    private final Map<UUID, GameType> lbMode = new HashMap<UUID, GameType>();
    private final Map<UUID, LeaderboardCategory> lbCategory = new HashMap<UUID, LeaderboardCategory>();
    private final Map<UUID, Integer> lbPage = new HashMap<UUID, Integer>();
    /** Previous WorldBorder per world name while any setup draft overrides it. */
    private final Map<String, BorderSnapshot> savedBorders = new HashMap<String, BorderSnapshot>();
    /** Setup-only hologram markers keyed by draft owner. */
    private final Map<UUID, List<ArmorStand>> setupMarkers = new HashMap<UUID, List<ArmorStand>>();
    /** Pulsing setup particles while a draft is open. */
    private final Map<UUID, Integer> setupMarkerTasks = new HashMap<UUID, Integer>();

    public GuiController(BedlamCore plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        Inventory inventory = chest(27, MAIN_TITLE);
        inventory.setItem(8, Items.named(new ItemStack(Items.material("RED_BED", "BED")), ChatColor.RED + "Leave Game"));
        inventory.setItem(9, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Quick Join Solo", ChatColor.GRAY + "One player per team"));
        inventory.setItem(10, Items.named(new ItemStack(Material.MAP), ChatColor.AQUA + "Browse Solo Games", ChatColor.GRAY + "Select a waiting arena"));
        inventory.setItem(11, Items.named(new ItemStack(Material.DIAMOND_SWORD), ChatColor.GOLD + "Quick Join Doubles", ChatColor.GRAY + "Two players per team"));
        inventory.setItem(12, Items.named(new ItemStack(Material.MAP), ChatColor.GOLD + "Browse Doubles Games", ChatColor.GRAY + "Select a waiting arena"));
        inventory.setItem(13, Items.named(new ItemStack(Items.material("GOLDEN_SWORD", "GOLD_SWORD")), ChatColor.DARK_GREEN + "Quick Join Trios", ChatColor.GRAY + "Three players per team"));
        inventory.setItem(14, Items.named(new ItemStack(Material.MAP), ChatColor.DARK_GREEN + "Browse Trios Games", ChatColor.GRAY + "Select a waiting arena"));
        inventory.setItem(15, Items.named(new ItemStack(Items.material("NETHERITE_SWORD", "DIAMOND_SWORD")), ChatColor.LIGHT_PURPLE + "Quick Join Quads", ChatColor.GRAY + "Four players per team"));
        inventory.setItem(16, Items.named(new ItemStack(Material.MAP), ChatColor.LIGHT_PURPLE + "Browse Quads Games", ChatColor.GRAY + "Select a waiting arena"));
        if (admin(player)) inventory.setItem(22, Items.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "Admin Setup"));
        openGui(player, inventory);
    }

    public void openAdmin(Player player) {
        if (!admin(player)) return;
        Inventory inventory = chest(27, ADMIN_TITLE);
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
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        // Lobby (or any non-draft world) must not reopen leftover game-setup for a map the operator left.
        if (session != null && player.getWorld().getName().equals(session.settings.worldName())) {
            openArenaSetup(player);
            return;
        }
        ArenaManager manager = plugin.games().arenaInWorld(player.getWorld().getName());
        if (manager != null) beginArenaSetup(player, manager.arena().settings(), false);
        else openAdmin(player);
    }

    /** Drop this operator's arena draft only (leave / match end → lobby). Other operators untouched. */
    public void clearArenaDraft(Player player) {
        if (player == null) return;
        final ArenaDraft session = arenaDrafts.remove(player.getUniqueId());
        if (session == null) return;
        clearSetupMarkers(player);
        restoreSetupGameMode(player, session);
        releaseSetupWorldBorder(session.settings);
        removeTeamSetupWands(player, null);
        removeDeleteSticks(player);
        if (session.newWorld) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { plugin.worlds().delete(session.settings, player); }
            });
        }
        player.sendMessage(ChatColor.YELLOW + "Unsaved game setup for " + session.settings.id() + " was discarded.");
    }

    private void openLobbySetup(Player player) {
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null) { beginLobbySetup(player); return; }
        Inventory inventory = chest(27, LOBBY_TITLE);
        inventory.setItem(9, setupItem(Material.NETHER_STAR, "Set Lobby Spawn", draft.spawn() != null));
        inventory.setItem(10, npcItem(GameType.SOLO, draft));
        inventory.setItem(11, npcItem(GameType.DOUBLES, draft));
        inventory.setItem(12, npcItem(GameType.TRIOS, draft));
        inventory.setItem(13, npcItem(GameType.QUADS, draft));
        inventory.setItem(14, setupItem(Material.EMERALD, "Set Cosmetics NPC", draft.cosmeticsNpc() != null));
        inventory.setItem(15, setupItem(Material.PAPER, "Set Profile NPC", draft.profileNpc() != null));
        inventory.setItem(16, setupItem(Items.material("BEACON"), "Set Leaderboard NPC", draft.leaderboardNpc() != null));
        inventory.setItem(21, Items.named(new ItemStack(Material.BARRIER), ChatColor.RED + "Cancel", ChatColor.GRAY + "Discard all lobby changes"));
        inventory.setItem(23, Items.named(new ItemStack(Material.SLIME_BALL), ChatColor.GREEN + "Apply", ChatColor.GRAY + "Validate and save"));
        openGui(player, inventory);
    }

    public void openWorlds(Player player) {
        if (!admin(player)) return;
        Inventory inventory = chest(54, WORLDS_TITLE);
        inventory.setItem(0, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Create Solo World"));
        inventory.setItem(1, Items.named(new ItemStack(Material.DIAMOND_SWORD), ChatColor.GOLD + "Create Doubles World"));
        inventory.setItem(2, Items.named(new ItemStack(Items.material("GOLDEN_SWORD", "GOLD_SWORD")), ChatColor.DARK_GREEN + "Create Trios World", ChatColor.GRAY + "6 teams x 3"));
        inventory.setItem(3, Items.named(new ItemStack(Items.material("NETHERITE_SWORD", "DIAMOND_SWORD")), ChatColor.LIGHT_PURPLE + "Create Quads World", ChatColor.GRAY + "8 teams x 4"));
        inventory.setItem(5, Items.named(new ItemStack(Material.CHEST), ChatColor.LIGHT_PURPLE + "Import Map",
            ChatColor.GRAY + "Existing world folders without an arena"));
        inventory.setItem(6, Items.named(new ItemStack(Material.BOOK), ChatColor.GREEN + "Templates",
            ChatColor.GRAY + "Maps bundled with the plugin"));
        inventory.setItem(7, Items.named(new ItemStack(Material.COMPASS), ChatColor.YELLOW + "Current World", ChatColor.WHITE + player.getWorld().getName()));
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

    private void openImportMaps(Player player) {
        if (!admin(player)) return;
        List<String> maps = plugin.worlds().listImportable();
        Inventory inventory = chest(54, IMPORT_TITLE);
        inventory.setItem(0, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        if (maps.isEmpty()) {
            inventory.setItem(22, Items.named(new ItemStack(Material.BARRIER), ChatColor.RED + "No importable maps",
                ChatColor.GRAY + "Put a world folder (level.dat) in the server directory"));
        } else {
            int slot = 9;
            for (String name : maps) {
                if (slot >= inventory.getSize()) break;
                inventory.setItem(slot++, Items.named(new ItemStack(Items.material("GRASS_BLOCK", "GRASS")),
                    ChatColor.AQUA + "Import: " + name, ChatColor.GRAY + "Configure as a new arena"));
            }
        }
        openGui(player, inventory);
    }

    private void openImportType(Player player, String worldName) {
        selectedArena.put(player.getUniqueId(), worldName);
        Inventory inventory = chest(27, IMPORT_TYPE_TITLE);
        inventory.setItem(10, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Import as Solo",
            ChatColor.GRAY + worldName));
        inventory.setItem(12, Items.named(new ItemStack(Material.DIAMOND_SWORD), ChatColor.GOLD + "Import as Doubles",
            ChatColor.GRAY + worldName));
        inventory.setItem(14, Items.named(new ItemStack(Items.material("GOLDEN_SWORD", "GOLD_SWORD")), ChatColor.DARK_GREEN + "Import as Trios",
            ChatColor.GRAY + worldName, ChatColor.DARK_GRAY + "6 teams x 3"));
        inventory.setItem(16, Items.named(new ItemStack(Items.material("NETHERITE_SWORD", "DIAMOND_SWORD")), ChatColor.LIGHT_PURPLE + "Import as Quads",
            ChatColor.GRAY + worldName, ChatColor.DARK_GRAY + "8 teams x 4"));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        openGui(player, inventory);
    }

    private void openTemplates(Player player) {
        if (!admin(player)) return;
        Inventory inventory = chest(54, TEMPLATES_TITLE);
        inventory.setItem(0, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        int slot = 9;
        for (String id : plugin.templates().list()) {
            if (slot >= inventory.getSize()) break;
            java.util.EnumSet<GameType> allowed = plugin.templates().allowedModes(id);
            String modeLore = allowed.contains(GameType.TRIOS) ? "Trios/Quads only" : "Solo/Doubles only";
            inventory.setItem(slot++, Items.named(new ItemStack(Items.material("GRASS_BLOCK", "GRASS")),
                ChatColor.GREEN + "Template: " + id,
                ChatColor.GRAY + "Pre-setup map bundled with BedlamCore",
                ChatColor.DARK_GRAY + modeLore));
        }
        openGui(player, inventory);
    }

    private void openTemplateType(Player player, String templateId) {
        selectedArena.put(player.getUniqueId(), templateId);
        java.util.EnumSet<GameType> allowed = plugin.templates().allowedModes(templateId);
        Inventory inventory = chest(27, TEMPLATE_TYPE_TITLE);
        if (allowed.contains(GameType.SOLO))
            inventory.setItem(10, Items.named(new ItemStack(Material.IRON_SWORD), ChatColor.AQUA + "Solo",
                ChatColor.GRAY + templateId, ChatColor.DARK_GRAY + "team size 1"));
        if (allowed.contains(GameType.DOUBLES))
            inventory.setItem(12, Items.named(new ItemStack(Material.DIAMOND_SWORD), ChatColor.GOLD + "Doubles",
                ChatColor.GRAY + templateId, ChatColor.DARK_GRAY + "team size 2"));
        if (allowed.contains(GameType.TRIOS))
            inventory.setItem(14, Items.named(new ItemStack(Items.material("GOLDEN_SWORD", "GOLD_SWORD")), ChatColor.DARK_GREEN + "3v3v3v3",
                ChatColor.GRAY + templateId, ChatColor.DARK_GRAY + "4 teams x 3"));
        if (allowed.contains(GameType.QUADS))
            inventory.setItem(16, Items.named(new ItemStack(Items.material("NETHERITE_SWORD", "DIAMOND_SWORD")), ChatColor.LIGHT_PURPLE + "4v4v4v4",
                ChatColor.GRAY + templateId, ChatColor.DARK_GRAY + "4 teams x 4"));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        openGui(player, inventory);
    }

    private void openWorldActions(Player player, String id) {
        ArenaManager manager = plugin.games().byId(id);
        if (manager == null) { openWorlds(player); return; }
        selectedArena.put(player.getUniqueId(), id);
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + "World Actions");
        inventory.setItem(10, Items.named(new ItemStack(Items.material("ENDER_PEARL")), ChatColor.GREEN + "Teleport & Setup"));
        inventory.setItem(13, Items.named(new ItemStack(Material.PAPER), ChatColor.YELLOW + "Status", manager.arena().settings().validate().isEmpty() ? ChatColor.GREEN + "Ready" : ChatColor.RED + "Incomplete"));
        inventory.setItem(16, Items.named(new ItemStack(Material.TNT), ChatColor.RED + "Delete World", ChatColor.DARK_RED + "Requires confirmation"));
        openGui(player, inventory);
    }

    private void confirmDelete(Player player) {
        Inventory inventory = chest(27, ChatColor.DARK_RED + "Confirm World Delete");
        inventory.setItem(11, Items.named(new ItemStack(Material.BARRIER), ChatColor.GRAY + "Keep World"));
        inventory.setItem(15, Items.named(new ItemStack(Material.TNT), ChatColor.RED + "Confirm Delete", ChatColor.DARK_RED + "This cannot be undone"));
        openGui(player, inventory);
    }

    public void beginArenaSetup(Player player, ArenaSettings settings, boolean newWorld) {
        ArenaDraft session = new ArenaDraft(settings.copy(), newWorld, player.getGameMode());
        arenaDrafts.put(player.getUniqueId(), session);
        World world = plugin.worlds().load(settings);
        if (world == null) {
            arenaDrafts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "Could not load " + settings.worldName() + ".");
            return;
        }
        // load() unload+recreates the World; draft Locations still hold the dead World ref.
        session.settings.reattach(world);
        player.teleport(session.settings.spectator() == null ? world.getSpawnLocation() : session.settings.spectator());
        player.setGameMode(GameMode.CREATIVE);
        refreshSetupWorldBorder(session.settings);
        reportMissing(player, session.settings.validate());
        refreshSetupMarkers(player);
        openArenaSetup(player);
    }

    public boolean hasArenaDraft(Player player) { return arenaDrafts.containsKey(player.getUniqueId()); }

    public boolean acceptRadiusInput(final Player player, final String message) {
        if (!radiusInputs.remove(player.getUniqueId())) return false;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                ArenaDraft session = arenaDrafts.get(player.getUniqueId());
                if (session == null || message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ChatColor.YELLOW + "Radius input cancelled.");
                    return;
                }
                try {
                    int radius = Integer.parseInt(message.trim());
                    if (radius < 1 || radius > 512) {
                        player.sendMessage(ChatColor.RED + "Radius must be 1–512.");
                        openArenaSetup(player);
                        return;
                    }
                    session.settings.buildBorderRadius(radius);
                    refreshSetupWorldBorder(session.settings);
                    player.sendMessage(ChatColor.GREEN + "Build border radius set to " + radius
                        + (session.settings.hasBuildBorder()
                            ? " (XZ midpoint waiting↔spectator; height covers waiting + beds)."
                            : " — set waiting + spectator spawns to enable the border."));
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatColor.RED + "Type a whole number (or cancel).");
                }
                openArenaSetup(player);
            }
        });
        return true;
    }

    /** Setup preview / Apply: same AABB + diameter helper as mayPlace. */
    private void refreshSetupWorldBorder(ArenaSettings settings) {
        if (settings == null || !settings.hasBuildBorder()) return;
        World world = Bukkit.getWorld(settings.worldName());
        if (world == null) return;
        captureBorderIfNeeded(world);
        settings.applyWorldBorder(world);
    }

    private void releaseSetupWorldBorder(ArenaSettings leaving) {
        if (leaving == null || leaving.worldName() == null) return;
        String worldName = leaving.worldName();
        for (ArenaDraft draft : arenaDrafts.values()) {
            if (worldName.equals(draft.settings.worldName()) && draft.settings.hasBuildBorder()) {
                refreshSetupWorldBorder(draft.settings);
                return;
            }
        }
        // Clamp/hide must not throw — cancel already dropped the draft; a throw left orphan setup state.
        try {
            restoreSavedBorder(worldName);
            // Match/waiting never shows WB — mayPlace AABB still enforces the build edge.
            ArenaSettings.hideWorldBorder(Bukkit.getWorld(worldName));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("WorldBorder release failed for " + worldName + ": " + ex.getMessage());
        }
    }

    private void captureBorderIfNeeded(World world) {
        if (savedBorders.containsKey(world.getName())) return;
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        savedBorders.put(world.getName(), new BorderSnapshot(
            center.getX(), center.getZ(), ArenaSettings.clampWorldBorderSize(border.getSize()),
            border.getWarningDistance(), border.getDamageAmount()));
    }

    private void restoreSavedBorder(String worldName) {
        BorderSnapshot snap = savedBorders.remove(worldName);
        if (snap == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(snap.centerX, snap.centerZ);
        border.setSize(ArenaSettings.clampWorldBorderSize(snap.size));
        border.setWarningDistance(snap.warningDistance);
        border.setDamageAmount(snap.damageAmount);
    }

    /** Restore every setup WorldBorder override (plugin disable / reload). */
    public void restoreAllSetupBorders() {
        for (String worldName : new ArrayList<String>(savedBorders.keySet())) {
            restoreSavedBorder(worldName);
        }
        for (UUID uuid : new ArrayList<UUID>(setupMarkers.keySet())) {
            clearSetupMarkers(uuid);
        }
        arenaDrafts.clear();
    }

    public void disconnect(Player player) {
        UUID uuid = player.getUniqueId();
        LobbySettings lobbyDraft = lobbyDrafts.remove(uuid);
        if (lobbyDraft != null) plugin.npcs().respawnAll();
        final ArenaDraft arenaDraft = arenaDrafts.remove(uuid);
        if (arenaDraft != null) {
            restoreSetupGameMode(player, arenaDraft);
            releaseSetupWorldBorder(arenaDraft.settings);
            if (arenaDraft.newWorld) Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { plugin.worlds().delete(arenaDraft.settings, player); }
            });
        }
        selectedArena.remove(uuid);
        selectedTeam.remove(uuid);
        selectedNpc.remove(uuid);
        skinInputs.remove(uuid);
        specialEditTarget.remove(uuid);
        specialSkinInputs.remove(uuid);
        radiusInputs.remove(uuid);
        shopCategory.remove(uuid);
        favoriteAssignSlot.remove(uuid);
        favoritePendingItem.remove(uuid);
        guiBusy.remove(uuid);
        clearSetupMarkers(uuid);
        removeTeamSetupWands(player, null);
        removeDeleteSticks(player);
        ChestGuis.clear(uuid);
    }

    private void openArenaSetup(Player player) {
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null) { openWorlds(player); return; }
        ArenaSettings settings = session.settings;
        Inventory inventory = chest(54, ChatColor.DARK_GRAY + "Game Setup");
        inventory.setItem(0, Items.named(new ItemStack(Material.COMPASS), ChatColor.YELLOW + "Current World", ChatColor.WHITE + settings.worldName()));
        inventory.setItem(1, Items.named(new ItemStack(settings.gameType() == GameType.SOLO ? Material.IRON_SWORD : Material.DIAMOND_SWORD), ChatColor.AQUA + "Mode: " + settings.gameType().displayName()));
        inventory.setItem(3, setupItem(Material.GLASS, "Set Waiting Spawn", settings.waitingSpawn() != null));
        inventory.setItem(5, setupItem(Items.material("ENDER_EYE", "EYE_OF_ENDER"), "Set Spectator Spawn", settings.spectator() != null));
        inventory.setItem(7, Items.named(new ItemStack(Items.material("OAK_SIGN", "SIGN")),
            (settings.hasBuildBorder() ? ChatColor.GREEN : ChatColor.YELLOW) + BORDER_RADIUS_PREFIX + " " + settings.buildBorderRadius(),
            settings.hasBuildBorder() ? ChatColor.GREEN + "Center ready (waiting XZ↔spectator XZ; Y=waiting+beds)" : ChatColor.RED + "Needs waiting + spectator spawns",
            ChatColor.GRAY + "Click, then type a radius in chat"));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 17};
        int index = 0;
        for (TeamColor color : TeamColor.values()) {
            inventory.setItem(slots[index++], Items.named(color.wool(1), color.chatColor() + "Configure " + color.displayName(),
                status(settings.team(color).complete()), ChatColor.GRAY + "Shift-click: team wool"));
        }
        inventory.setItem(30, Items.named(new ItemStack(Material.DIAMOND), ChatColor.AQUA + "Add Diamond Generator", ChatColor.GRAY + "Count: " + settings.diamondGenerators().size()));
        inventory.setItem(32, Items.named(new ItemStack(Material.EMERALD), ChatColor.GREEN + "Add Emerald Generator", ChatColor.GRAY + "Count: " + settings.emeraldGenerators().size()));
        inventory.setItem(34, Items.named(new ItemStack(Material.STICK), ChatColor.RED + "Delete Stick",
            ChatColor.DARK_GRAY + DELETE_STICK_LORE,
            ChatColor.GRAY + "Left-click a setup point to remove it"));
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
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + "Team Setup");
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
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + PLAY_TITLE_PREFIX + type.displayName());
        inventory.setItem(11, Items.named(new ItemStack(Items.material("RED_BED", "BED")),
            ChatColor.GREEN + "Bed Wars " + type.displayName(),
            ChatColor.WHITE + "Play a game of Bed Wars " + type.displayName() + ".",
            ChatColor.WHITE + "" + type.teamSize() + " player(s) per team.",
            "",
            ChatColor.YELLOW + "Click to play!"));
        inventory.setItem(15, Items.named(new ItemStack(Items.material("OAK_SIGN", "SIGN")),
            ChatColor.GREEN + "Map Selector (" + type.displayName() + ")",
            ChatColor.WHITE + "Pick which map you want to play!",
            "",
            ChatColor.YELLOW + "Click to browse!"));
        // Party frame: when the sender leads a party show a "Queue as Party" button (else current behavior).
        dev.iyanel.bedlamcore.party.PartyService parties = plugin.partyService();
        if (parties != null && parties.enabled()) {
            dev.iyanel.bedlamcore.party.Party party = parties.partyOf(player.getUniqueId());
            if (party != null && party.size() > 1 && party.isLeader(player.getUniqueId())) {
                inventory.setItem(22, Items.named(Skins.head(party.leaderName()),
                    ChatColor.AQUA + "Queue as Party",
                    ChatColor.WHITE + "Leader: " + ChatColor.YELLOW + party.leaderName(),
                    ChatColor.WHITE + "Members: " + ChatColor.YELLOW + party.size(),
                    "",
                    ChatColor.YELLOW + "Click to queue your whole party!"));
            } else if (party != null && party.size() > 1) {
                inventory.setItem(22, Items.named(Skins.head(party.leaderName()),
                    ChatColor.AQUA + "Your Party",
                    ChatColor.WHITE + "Leader: " + ChatColor.YELLOW + party.leaderName(),
                    ChatColor.WHITE + "Members: " + ChatColor.YELLOW + party.size(),
                    "",
                    ChatColor.GRAY + "Only the leader can queue the party."));
            }
            inventory.setItem(24, Items.named(new ItemStack(Items.material("CAKE")),
                ChatColor.LIGHT_PURPLE + "Party Menu",
                ChatColor.WHITE + "Create or manage your party.",
                "",
                ChatColor.YELLOW + "Click to open!"));
        }
        openGui(player, inventory);
    }

    public void openMapSelector(Player player, GameType type) {
        Inventory inventory = chest(54, ChatColor.DARK_GRAY + MAP_TITLE_PREFIX + type.displayName());
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
        click(player, title, clicked, false, -1);
    }

    public void click(Player player, String title, ItemStack clicked, boolean shiftLeft, int rawSlot) {
        String cleanTitle = ChatColor.stripColor(title);
        String name = Items.name(clicked);
        if (cleanTitle.equals("Bedlam Menu")) clickMain(player, name);
        else if (cleanTitle.equals("Bedlam Setup")) clickAdmin(player, name);
        else if (cleanTitle.equals("Lobby Setup")) clickLobby(player, name);
        else if (cleanTitle.equals("Game Worlds")) clickWorlds(player, name);
        else if (cleanTitle.equals("Import Maps")) clickImportMaps(player, name);
        else if (cleanTitle.equals("Import As")) clickImportType(player, name);
        else if (cleanTitle.equals("Templates")) clickTemplates(player, name);
        else if (cleanTitle.equals("Template Mode")) clickTemplateType(player, name);
        else if (cleanTitle.equals("World Actions")) clickWorldActions(player, name);
        else if (cleanTitle.equals("Confirm World Delete")) clickDelete(player, name);
        else if (cleanTitle.equals("Game Setup")) clickArenaSetup(player, name, shiftLeft);
        else if (cleanTitle.equals("Team Setup")) clickTeamSetup(player, name);
        else if (cleanTitle.equals("NPC Editor")) clickNpcEditor(player, name);
        else if (cleanTitle.equals("Cosmetics NPC") || cleanTitle.equals("Profile NPC") || cleanTitle.equals("Leaderboard NPC")) clickSpecialNpcEditor(player, name);
        else if (cleanTitle.equals("Leaderboards")) clickLeaderboardHome(player, name);
        else if (cleanTitle.startsWith("Leaderboard: ")) clickLeaderboardCategory(player, name);
        else if (cleanTitle.equals("Skin Presets")) clickSkinPreset(player, name);
        else if (cleanTitle.startsWith("Play Bed Wars ")) {
            GameType type = GameType.parse(cleanTitle.substring("Play Bed Wars ".length()));
            clickPlay(player, type, name);
        } else if (cleanTitle.startsWith("Map Selector ")) {
            GameType type = GameType.parse(cleanTitle.substring("Map Selector ".length()));
            clickMap(player, type, name);
        } else if (cleanTitle.equals("Solo Games")) clickQueue(player, GameType.SOLO, name);
        else if (cleanTitle.equals("Doubles Games")) clickQueue(player, GameType.DOUBLES, name);
        else if (cleanTitle.equals("Quick Buy") || cleanTitle.equals("Item Shop")) buy(player, name, shiftLeft, rawSlot);
        else if (cleanTitle.equals("Upgrades & Traps") || cleanTitle.equals("Team Upgrades")) upgrade(player, name);
        else if (cleanTitle.equals("Spectate")) clickSpectate(player, name);
        else if (cleanTitle.equals("Cosmetics") || cleanTitle.equals("My Cosmetics")) clickCosmeticsHome(player, name);
        else if (cleanTitle.equals("Kill Messages") || cleanTitle.equals("Kill Effects") || cleanTitle.equals("Win Effects")
            || cleanTitle.equals("Wood Skins") || cleanTitle.equals("Final Kill Effects") || cleanTitle.equals("Prestige Customizer")
            || cleanTitle.equals("Bed Destroys") || cleanTitle.equals("Projectile Trails") || cleanTitle.equals("Shopkeeper Skins")) {
            clickCosmeticsCategory(player, cleanTitle, clicked);
        }
        else if (cleanTitle.equals("Bed Wars Statistics")) clickProfileStats(player, name);
        else if (cleanTitle.equals("Party")) clickPartyMenu(player, name, shiftLeft);
        else if (cleanTitle.equals("Party Invite")) clickPartyInvite(player, name);
    }

    private void clickMain(Player player, String name) {
        if (name.equals("Quick Join Solo")) plugin.games().quickJoin(player, GameType.SOLO);
        else if (name.equals("Quick Join Doubles")) plugin.games().quickJoin(player, GameType.DOUBLES);
        else if (name.equals("Quick Join Trios")) plugin.games().quickJoin(player, GameType.TRIOS);
        else if (name.equals("Quick Join Quads")) plugin.games().quickJoin(player, GameType.QUADS);
        else if (name.equals("Browse Solo Games")) openQueue(player, GameType.SOLO);
        else if (name.equals("Browse Doubles Games")) openQueue(player, GameType.DOUBLES);
        else if (name.equals("Browse Trios Games")) openQueue(player, GameType.TRIOS);
        else if (name.equals("Browse Quads Games")) openQueue(player, GameType.QUADS);
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
        else if (name.equals("Set Solo NPC")) setQueueNpcHere(player, draft, GameType.SOLO);
        else if (name.equals("Set Doubles NPC")) setQueueNpcHere(player, draft, GameType.DOUBLES);
        else if (name.equals("Set 3v3v3v3 NPC")) setQueueNpcHere(player, draft, GameType.TRIOS);
        else if (name.equals("Set 4v4v4v4 NPC")) setQueueNpcHere(player, draft, GameType.QUADS);
        else if (name.equals("Set Cosmetics NPC")) {
            Location loc = snapNpcLocation(player);
            draft.cosmeticsNpc(loc);
            plugin.npcs().spawnCosmetics(draft.cosmeticsNpc());
            player.sendMessage(ChatColor.GREEN + "Cosmetics NPC set here. Click Apply to save.");
        }
        else if (name.equals("Set Profile NPC")) {
            Location loc = snapNpcLocation(player);
            draft.profileNpc(loc);
            plugin.npcs().spawnProfile(draft.profileNpc());
            player.sendMessage(ChatColor.GREEN + "Profile NPC set here. Click Apply to save.");
        }
        else if (name.equals("Set Leaderboard NPC")) {
            Location loc = snapNpcLocation(player);
            draft.leaderboardNpc(loc);
            plugin.npcs().spawnLeaderboard(draft.leaderboardNpc());
            player.sendMessage(ChatColor.GREEN + "Leaderboard NPC set here. Shift-click it to edit its look. Click Apply to save.");
        }
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
        else if (name.equals("Create Trios World")) createWorld(player, GameType.TRIOS);
        else if (name.equals("Create Quads World")) createWorld(player, GameType.QUADS);
        else if (name.equals("Import Map")) openImportMaps(player);
        else if (name.equals("Templates")) openTemplates(player);
        else if (name.startsWith("World: ")) openWorldActions(player, name.substring(7));
    }

    private void clickImportMaps(Player player, String name) {
        if (name.equals("Back")) { openWorlds(player); return; }
        if (name.startsWith("Import: ")) openImportType(player, name.substring(8).trim());
    }

    private void clickTemplates(Player player, String name) {
        if (name.equals("Back")) { openWorlds(player); return; }
        if (name.startsWith("Template: ")) openTemplateType(player, name.substring(10).trim());
    }

    private void clickTemplateType(Player player, String name) {
        if (name.equals("Back")) { openTemplates(player); return; }
        String templateId = selectedArena.get(player.getUniqueId());
        if (templateId == null || templateId.isEmpty()) { openTemplates(player); return; }
        GameType type = null;
        if (name.equals("Solo")) type = GameType.SOLO;
        else if (name.equals("Doubles")) type = GameType.DOUBLES;
        else if (name.equals("3v3v3v3")) type = GameType.TRIOS;
        else if (name.equals("4v4v4v4")) type = GameType.QUADS;
        if (type == null) return;
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Loading template " + templateId + " as " + type.displayName() + "...");
        try {
            ArenaSettings settings = plugin.templates().materialize(templateId, type);
            beginArenaSetup(player, settings, false);
        } catch (Exception exception) {
            player.sendMessage(ChatColor.RED + "Template failed: " + exception.getMessage());
            openTemplates(player);
        }
    }

    private void clickImportType(Player player, String name) {
        if (name.equals("Back")) { openImportMaps(player); return; }
        String worldName = selectedArena.get(player.getUniqueId());
        if (worldName == null || worldName.isEmpty()) { openImportMaps(player); return; }
        GameType type = null;
        if (name.equals("Import as Solo")) type = GameType.SOLO;
        else if (name.equals("Import as Doubles")) type = GameType.DOUBLES;
        else if (name.equals("Import as Trios")) type = GameType.TRIOS;
        else if (name.equals("Import as Quads")) type = GameType.QUADS;
        if (type == null) return;
        if (plugin.games().byId(worldName) != null || plugin.games().arenaInWorld(worldName) != null) {
            player.sendMessage(ChatColor.RED + worldName + " already has an arena.");
            openImportMaps(player);
            return;
        }
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Importing " + worldName + " as " + type.displayName() + "...");
        beginArenaSetup(player, new ArenaSettings(worldName, type, worldName), false);
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

    private void clickArenaSetup(Player player, String name, boolean shiftLeft) {
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null) return;
        ArenaSettings settings = session.settings;
        if (name.equals("Set Waiting Spawn")) {
            settings.waitingSpawn(player.getLocation());
            refreshSetupWorldBorder(settings);
        }
        else if (name.equals("Set Spectator Spawn")) {
            settings.spectator(player.getLocation());
            refreshSetupWorldBorder(settings);
        }
        else if (name.startsWith(BORDER_RADIUS_PREFIX)) {
            radiusInputs.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Type the build border radius in chat (current "
                + settings.buildBorderRadius() + "). Type cancel to stop.");
            return;
        }
        else if (name.equals("Add Diamond Generator")) settings.diamondGenerators().add(player.getLocation());
        else if (name.equals("Add Emerald Generator")) settings.emeraldGenerators().add(player.getLocation());
        else if (name.equals("Delete Stick")) { giveDeleteStick(player); return; }
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
            arenaDrafts.remove(player.getUniqueId());
            radiusInputs.remove(player.getUniqueId());
            clearSetupMarkers(player);
            removeTeamSetupWands(player, null);
            removeDeleteSticks(player);
            restoreSetupGameMode(player, session);
            // Setup-only WB: hide for match (mayPlace AABB keeps the edge); drop setup snapshot.
            try {
                restoreSavedBorder(settings.worldName());
                ArenaSettings.hideWorldBorder(world);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("WorldBorder hide after apply failed for "
                    + settings.worldName() + ": " + ex.getMessage());
            }
            settings.warnBedsOutsideBorder(plugin.getLogger());
            plugin.worlds().saveOnce(world);
            plugin.games().register(settings.copy());
            plugin.saveSettings();
            player.closeInventory();
            Location lobby = plugin.lobby().spawn();
            if (lobby == null && !Bukkit.getWorlds().isEmpty()) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
            if (lobby != null) player.teleport(lobby);
            player.sendMessage(ChatColor.GREEN + "Game setup applied and world saved for " + settings.id() + ". Nothing is missing.");
            return;
        } else {
            for (TeamColor team : TeamColor.values()) {
                if (!name.equals("Configure " + team.displayName())) continue;
                if (shiftLeft) { giveTeamSetupWand(player, team); return; }
                openTeamSetup(player, team);
                return;
            }
        }
        refreshSetupMarkers(player);
        openArenaSetup(player);
    }

    private void cancelArena(Player player, ArenaDraft session) {
        arenaDrafts.remove(player.getUniqueId());
        radiusInputs.remove(player.getUniqueId());
        clearSetupMarkers(player);
        removeTeamSetupWands(player, null);
        removeDeleteSticks(player);
        restoreSetupGameMode(player, session);
        releaseSetupWorldBorder(session.settings);
        // Always leave the map — staying put made compass reopen call beginArenaSetup → load() lobby flash → bounce back.
        Location lobby = plugin.lobby().spawn();
        if (lobby == null && !Bukkit.getWorlds().isEmpty()) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        if (lobby != null) player.teleport(lobby);
        if (session.newWorld) plugin.worlds().delete(session.settings, player);
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
        refreshSetupMarkers(player);
        if (settings.complete()) {
            removeTeamSetupWands(player, team);
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + team.displayName() + " team setup complete.");
            return;
        }
        openTeamSetup(player, team);
    }

    private void clickPlay(Player player, GameType type, String name) {
        if (name.equals("Bed Wars " + type.displayName())) plugin.games().quickJoin(player, type);
        else if (name.equals("Queue as Party")) plugin.games().quickJoin(player, type);
        else if (name.equals("Party Menu")) openPartyMenu(player);
        else if (name.startsWith("Map Selector")) openMapSelector(player, type);
    }

    public void openPartyMenu(Player player) {
        dev.iyanel.bedlamcore.party.PartyService service = plugin.partyService();
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + "Party");
        dev.iyanel.bedlamcore.party.Party party = service == null ? null : service.partyOf(player.getUniqueId());
        if (party == null) {
            inventory.setItem(13, Items.named(new ItemStack(Items.material("CAKE")),
                ChatColor.GREEN + "Create Party",
                ChatColor.WHITE + "Start a party and invite friends.",
                "",
                ChatColor.YELLOW + "Click to create!"));
            openGui(player, inventory);
            return;
        }
        boolean leader = party.isLeader(player.getUniqueId());
        inventory.setItem(4, Items.named(Skins.head(party.leaderName()),
            ChatColor.AQUA + "Party",
            ChatColor.WHITE + "Leader: " + ChatColor.YELLOW + party.leaderName(),
            ChatColor.WHITE + "Members: " + ChatColor.YELLOW + party.size(),
            ChatColor.WHITE + "Joins: " + (party.open() ? ChatColor.GREEN + "open" : ChatColor.GRAY + "invite-only")));
        int slot = 9;
        for (java.util.UUID uuid : party.members()) {
            if (slot > 17) break;
            Player member = plugin.getServer().getPlayer(uuid);
            String memberName = member != null ? member.getName() : "?";
            String star = party.isLeader(uuid) ? ChatColor.GOLD + "★ " : ChatColor.WHITE.toString();
            String line1 = leader && !party.isLeader(uuid) ? ChatColor.YELLOW + "Left-click: promote" : ChatColor.GRAY + "Party member";
            String line2 = leader && !party.isLeader(uuid) ? ChatColor.RED + "Shift-click: kick" : "";
            inventory.setItem(slot++, Items.named(Skins.head(memberName), star + memberName, line1, line2));
        }
        if (leader) inventory.setItem(20, Items.named(new ItemStack(Items.material("PAPER")),
            ChatColor.GREEN + "Invite Players", ChatColor.WHITE + "Invite online lobby players.", "", ChatColor.YELLOW + "Click!"));
        inventory.setItem(22, Items.named(new ItemStack(Items.material("OAK_SIGN", "SIGN")),
            ChatColor.AQUA + "Party Chat", ChatColor.WHITE + "Toggle routing your chat to the party.", "", ChatColor.YELLOW + "Click to toggle!"));
        inventory.setItem(24, Items.named(new ItemStack(org.bukkit.Material.BARRIER), ChatColor.RED + "Leave Party"));
        if (leader) inventory.setItem(26, Items.named(new ItemStack(org.bukkit.Material.BARRIER), ChatColor.DARK_RED + "Disband Party"));
        openGui(player, inventory);
    }

    public void openPartyInvite(Player player) {
        dev.iyanel.bedlamcore.party.PartyService service = plugin.partyService();
        Inventory inventory = chest(54, ChatColor.DARK_GRAY + "Party Invite");
        int slot = 0;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (slot >= inventory.getSize()) break;
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (service != null && service.partyOf(online.getUniqueId()) != null) continue; // already partied
            if (plugin.games().arena(online) != null) continue; // only lobby players
            inventory.setItem(slot++, Items.named(Skins.head(online.getName()),
                ChatColor.GREEN + online.getName(),
                ChatColor.WHITE + "Click to invite to your party."));
        }
        openGui(player, inventory);
    }

    private void clickPartyMenu(Player player, String name, boolean shiftLeft) {
        dev.iyanel.bedlamcore.party.PartyService service = plugin.partyService();
        if (service == null) return;
        if (name.equals("Create Party")) { service.create(player); openPartyMenu(player); return; }
        if (name.equals("Invite Players")) { openPartyInvite(player); return; }
        if (name.equals("Party Chat")) { service.toggleChat(player); openPartyMenu(player); return; }
        if (name.equals("Leave Party")) { service.leave(player); openPartyMenu(player); return; }
        if (name.equals("Disband Party")) { service.disbandCommand(player); openPartyMenu(player); return; }
        if (name.equals("Party")) return; // info head
        // Otherwise a member head: promote (left) or kick (shift). name may carry the leader star prefix.
        String memberName = ChatColor.stripColor(name).replace("★", "").trim();
        Player target = plugin.getServer().getPlayerExact(memberName);
        if (target == null) return;
        dev.iyanel.bedlamcore.party.Party party = service.partyOf(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) return;
        if (shiftLeft) service.kick(target, player.getUniqueId());
        else service.promote(target, player.getUniqueId());
        openPartyMenu(player);
    }

    private void clickPartyInvite(Player player, String name) {
        Player target = plugin.getServer().getPlayerExact(name);
        if (target != null && plugin.partyService() != null) plugin.partyService().invite(target, player);
        openPartyInvite(player);
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

    public TeamColor teamSetupWand(ItemStack item) {
        if (!Items.hasLore(item, "Bedlam Team Setup:")) return null;
        for (TeamColor team : TeamColor.values()) if (Items.hasLore(item, "Bedlam Team Setup: " + team.name())) return team;
        return null;
    }

    public boolean isDeleteStick(ItemStack item) {
        return Items.hasLore(item, DELETE_STICK_LORE);
    }

    private void giveTeamSetupWand(Player player, TeamColor team) {
        selectedTeam.put(player.getUniqueId(), team);
        removeTeamSetupWands(player, null);
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        ArenaSettings.TeamSettings settings = session == null ? null : session.settings.team(team);
        if (settings != null && settings.complete()) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + team.displayName() + " team is already complete.");
            return;
        }
        ItemStack wand = Items.named(team.wool(1), team.chatColor() + team.displayName() + " Team Setup",
            ChatColor.DARK_GRAY + "Bedlam Team Setup: " + team.name(),
            ChatColor.GRAY + "Right-click: open Team Setup");
        player.getInventory().setItem(GameRules.slotBeforeSetup(setupCompassSlot(player)), wand);
        player.closeInventory();
    }

    private void giveDeleteStick(Player player) {
        if (!hasArenaDraft(player)) {
            player.sendMessage(ChatColor.RED + "No active game setup draft.");
            return;
        }
        removeDeleteSticks(player);
        ItemStack stick = Items.named(new ItemStack(Material.STICK), ChatColor.RED + "Setup Delete Stick",
            ChatColor.DARK_GRAY + DELETE_STICK_LORE,
            ChatColor.GRAY + "Left-click a setup point to remove it");
        player.getInventory().setItem(GameRules.deleteStickSlot(setupCompassSlot(player)), stick);
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Delete stick ready — left-click a marked setup point.");
    }

    /** Left-click block/entity with delete stick: remove matching draft point. */
    public boolean useDeleteStick(Player player, ItemStack item, Location hit) {
        if (!isDeleteStick(item)) return false;
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null || !admin(player)) {
            removeDeleteSticks(player);
            player.sendMessage(ChatColor.RED + "No active game setup draft.");
            return true;
        }
        if (hit == null) return true;
        String removed = session.settings.removeNear(hit);
        if (removed == null) {
            player.sendMessage(ChatColor.YELLOW + "No setup point here.");
            return true;
        }
        refreshSetupMarkers(player);
        player.sendMessage(ChatColor.GREEN + "Removed " + removed);
        return true;
    }

    /** Right-click team wool: open that team's Team Setup GUI (same as Teams → team). */
    public boolean useTeamSetupWand(Player player, ItemStack item) {
        TeamColor team = teamSetupWand(item);
        if (team == null) return false;
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null || !admin(player)) {
            removeTeamSetupWands(player, null);
            player.sendMessage(ChatColor.RED + "No active game setup draft.");
            return true;
        }
        ArenaSettings.TeamSettings settings = session.settings.team(team);
        if (settings.complete()) {
            removeTeamSetupWands(player, team);
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + team.displayName() + " team setup is already complete.");
            return true;
        }
        openTeamSetup(player, team);
        return true;
    }

    /** Hotbar slot holding Bedlam Setup compass (default 8 from giveNavigation). */
    private static int setupCompassSlot(Player player) {
        for (int slot = 0; slot < 9; slot++) {
            if (Items.name(player.getInventory().getItem(slot)).equals("Bedlam Setup")) return slot;
        }
        return 8;
    }

    private void restoreSetupGameMode(Player player, ArenaDraft session) {
        if (player == null || session == null || session.previousGameMode == null) return;
        player.setGameMode(session.previousGameMode);
    }

    private void removeTeamSetupWands(Player player, TeamColor only) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            TeamColor wand = teamSetupWand(player.getInventory().getItem(slot));
            if (wand == null) continue;
            if (only == null || wand == only) player.getInventory().setItem(slot, null);
        }
    }

    private void removeDeleteSticks(Player player) {
        if (player == null) return;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isDeleteStick(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        }
    }

    private void refreshSetupMarkers(Player player) {
        if (player == null) return;
        clearSetupMarkers(player.getUniqueId());
        ArenaDraft session = arenaDrafts.get(player.getUniqueId());
        if (session == null) return;
        List<ArmorStand> stands = new ArrayList<ArmorStand>();
        for (ArenaSettings.LabeledPoint point : session.settings.setupMarkerPoints()) {
            Location loc = point.location;
            if (loc == null || loc.getWorld() == null) continue;
            Location pin = loc.getBlock().getLocation().add(0.5, 0.2, 0.5);
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(pin, EntityType.ARMOR_STAND);
            LobbyNpcService.prepareArmorStand(stand, true);
            stand.setCustomName(point.label);
            stand.setCustomNameVisible(true);
            stand.setMetadata(META_SETUP_MARKER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
            stands.add(stand);
            for (Player viewer : pin.getWorld().getPlayers()) {
                if (viewer.getUniqueId().equals(player.getUniqueId())) EntityVisibility.show(plugin, viewer, stand);
                else EntityVisibility.hide(plugin, viewer, stand);
            }
        }
        if (!stands.isEmpty()) setupMarkers.put(player.getUniqueId(), stands);
        startSetupMarkerPulse(player);
    }

    private void startSetupMarkerPulse(final Player player) {
        stopSetupMarkerPulse(player.getUniqueId());
        if (player == null || !arenaDrafts.containsKey(player.getUniqueId())) return;
        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override public void run() {
                ArenaDraft session = arenaDrafts.get(player.getUniqueId());
                if (session == null || !player.isOnline()) {
                    clearSetupMarkers(player.getUniqueId());
                    return;
                }
                for (ArenaSettings.LabeledPoint point : session.settings.setupMarkerPoints()) {
                    if (point.location != null) Particles.setupPin(player, point.location);
                }
            }
        }, 5L, 10L);
        setupMarkerTasks.put(player.getUniqueId(), task);
    }

    private void stopSetupMarkerPulse(UUID owner) {
        Integer task = setupMarkerTasks.remove(owner);
        if (task != null) Bukkit.getScheduler().cancelTask(task);
    }

    private void clearSetupMarkers(Player player) {
        if (player != null) clearSetupMarkers(player.getUniqueId());
    }

    private void clearSetupMarkers(UUID owner) {
        stopSetupMarkerPulse(owner);
        List<ArmorStand> stands = setupMarkers.remove(owner);
        if (stands == null) return;
        for (ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
    }

    public void placeNpc(Player player, GameType type, Location location) {
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null || !admin(player)) return;
        Location pin = location.clone();
        // NPC faces the SAME direction the admin is looking when placing — one convention for every lobby NPC.
        // (Was atan2-toward-player, which now renders the NPC facing away from where the admin looks.)
        pin.setYaw(player.getLocation().getYaw());
        pin.setPitch(0f);
        draft.npc(type).location(pin);
        plugin.npcs().spawn(type, draft.npc(type));
        player.getInventory().removeItem(player.getItemInHand());
        player.sendMessage(ChatColor.GREEN + type.displayName() + " NPC placed. Shift-right-click it to edit its look.");
        if (lobbyMissing(draft).isEmpty()) player.sendMessage(ChatColor.GREEN + "Lobby setup is complete. Click Apply to save both NPCs.");
        openLobbySetup(player);
    }

    /** Hypixel-style default skin presets offered in the NPC skin picker (Minecraft usernames). */
    private static final String[] PRESET_SKINS =
        {"Notch", "jeb_", "Technoblade", "Dream", "Hypixel", "Herobrine", "Steve", "Alex"};

    public void openNpcEditor(Player player, GameType type) {
        if (!admin(player)) return;
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (draft == null) {
            draft = plugin.lobby().copy();
            lobbyDrafts.put(player.getUniqueId(), draft);
        }
        selectedNpc.put(player.getUniqueId(), type);
        specialEditTarget.remove(player.getUniqueId()); // queue edit — clear any cosmetics/profile edit context
        LobbySettings.NpcSettings settings = draft.npc(type);
        boolean fake = settings.human();
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + "NPC Editor");
        inventory.setItem(4, Items.named(fake ? Skins.head(settings.skin()) : new ItemStack(Items.material("VILLAGER_SPAWN_EGG", "MONSTER_EGG")),
            ChatColor.GOLD + type.displayName() + " NPC", ChatColor.GRAY + appearance(settings)));
        // Type toggle: Fake Player (packet skin) <-> Mob (real mob entity).
        inventory.setItem(10, Items.named(fake ? Skins.head(settings.skin()) : new ItemStack(Items.material("VILLAGER_SPAWN_EGG", "MONSTER_EGG")),
            ChatColor.AQUA + "Type: " + (fake ? "Fake Player" : "Mob"), ChatColor.GRAY + "Click to switch"));
        if (fake) {
            inventory.setItem(12, Items.named(new ItemStack(Material.NAME_TAG), ChatColor.LIGHT_PURPLE + "Set Skin",
                ChatColor.GRAY + "Username or textures.minecraft.net URL"));
            inventory.setItem(13, Items.named(Skins.head("Hypixel"), ChatColor.GREEN + "Default Skins",
                ChatColor.GRAY + "Pick a preset skin"));
            inventory.setItem(14, Items.named(new ItemStack(Items.material("ELYTRA", "FEATHER")),
                (settings.cape() ? ChatColor.GREEN : ChatColor.RED) + "Cape: " + (settings.cape() ? "ON" : "OFF"),
                ChatColor.GRAY + "Only skins that own a cape show one", ChatColor.GRAY + "Default: OFF"));
        } else {
            inventory.setItem(11, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Previous Mob"));
            inventory.setItem(13, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Next Mob"));
            inventory.setItem(15, Items.named(new ItemStack(Material.EGG),
                ChatColor.AQUA + "Age: " + (settings.baby() ? "Baby" : "Adult"), ChatColor.GRAY + "Click to toggle"));
        }
        inventory.setItem(16, Items.named(new ItemStack(Items.material("ENDER_EYE", "EYE_OF_ENDER")),
            (settings.lookAtPlayers() ? ChatColor.GREEN : ChatColor.RED) + "Look at Players: " + (settings.lookAtPlayers() ? "ON" : "OFF"), ChatColor.GRAY + "Default: OFF"));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        openGui(player, inventory);
    }

    private void clickNpcEditor(Player player, String name) {
        GameType type = selectedNpc.get(player.getUniqueId());
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (type == null || draft == null) return;
        LobbySettings.NpcSettings settings = draft.npc(type);
        if (name.startsWith("Type: ")) settings.human(!settings.human());
        else if (name.equals("Previous Mob") || name.equals("Next Mob")) {
            settings.human(false);
            settings.entityType(plugin.npcs().next(settings.entityType(), name.equals("Next Mob") ? 1 : -1));
        } else if (name.startsWith("Age: ")) settings.baby(!settings.baby());
        else if (name.startsWith("Cape: ")) settings.cape(!settings.cape());
        else if (name.startsWith("Look at Players: ")) settings.lookAtPlayers(!settings.lookAtPlayers());
        else if (name.equals("Default Skins")) { openSkinPicker(player, type); return; }
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

    /** Preset skin picker — a grid of heads; clicking one sets the NPC to that Fake Player skin. */
    private void openSkinPicker(Player player, GameType type) {
        selectedNpc.put(player.getUniqueId(), type);
        specialEditTarget.remove(player.getUniqueId()); // queue path — not a cosmetics/profile edit
        openSkinPicker(player);
    }

    /** Shared preset grid; the click is routed to whichever NPC is being edited (queue vs cosmetics/profile). */
    private void openSkinPicker(Player player) {
        if (!admin(player)) return;
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + "Skin Presets");
        int slot = 10;
        for (String skin : PRESET_SKINS) {
            inventory.setItem(slot++, Items.named(Skins.head(skin), ChatColor.GREEN + "Skin: " + skin,
                ChatColor.GRAY + "Click to use this skin"));
        }
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        openGui(player, inventory);
    }

    private void clickSkinPreset(Player player, String name) {
        String special = specialEditTarget.get(player.getUniqueId());
        if (special != null) {
            if (name.equals("Back")) { openSpecialNpcEditor(player, special); return; }
            if (!name.startsWith("Skin: ")) return;
            applySpecialSkin(player, special, name.substring("Skin: ".length()).trim());
            openSpecialNpcEditor(player, special);
            return;
        }
        GameType type = selectedNpc.get(player.getUniqueId());
        LobbySettings draft = lobbyDrafts.get(player.getUniqueId());
        if (type == null || draft == null) return;
        if (name.equals("Back")) { openNpcEditor(player, type); return; }
        if (!name.startsWith("Skin: ")) return;
        LobbySettings.NpcSettings settings = draft.npc(type);
        settings.human(true);
        settings.skin(name.substring("Skin: ".length()).trim());
        if (settings.location() != null) plugin.npcs().spawn(type, settings);
        openNpcEditor(player, type);
    }

    public boolean acceptSkinInput(final Player player, final String message) {
        final String special = specialSkinInputs.remove(player.getUniqueId());
        if (special != null) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    if (message.equalsIgnoreCase("cancel")) { player.sendMessage(ChatColor.YELLOW + "Skin input cancelled."); return; }
                    if (!message.matches("[A-Za-z0-9_]{1,16}") && !message.startsWith("https://textures.minecraft.net/texture/")) {
                        player.sendMessage(ChatColor.RED + "Use a Minecraft username or a direct https://textures.minecraft.net/texture/... URL.");
                        openSpecialNpcEditor(player, special);
                        return;
                    }
                    applySpecialSkin(player, special, message);
                    player.sendMessage(ChatColor.GREEN + "Skin saved.");
                    openSpecialNpcEditor(player, special);
                }
            });
            return true;
        }
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

    /** Skin editor for the Cosmetics / Profile NPC. These apply LIVE (no draft/Apply) — the NPC is already
     *  placed — so a pick saves to the lobby config and respawns the body immediately. target = COSMETICS|PROFILE. */
    public void openSpecialNpcEditor(Player player, String target) {
        if (!admin(player)) return;
        specialEditTarget.put(player.getUniqueId(), target);
        String skin = specialSkin(target);
        boolean cape = specialCape(target);
        String label = specialLabel(target);
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + label + " NPC");
        inventory.setItem(4, Items.named(Skins.head(skin != null ? skin : "Steve"),
            ChatColor.GOLD + label + " NPC", ChatColor.GRAY + "Skin: " + (skin != null ? skin : "Default")));
        inventory.setItem(11, Items.named(new ItemStack(Material.NAME_TAG), ChatColor.LIGHT_PURPLE + "Set Skin",
            ChatColor.GRAY + "Username or textures.minecraft.net URL"));
        inventory.setItem(13, Items.named(Skins.head("Hypixel"), ChatColor.GREEN + "Default Skins",
            ChatColor.GRAY + "Pick a preset skin"));
        inventory.setItem(15, Items.named(new ItemStack(Items.material("ELYTRA", "FEATHER")),
            (cape ? ChatColor.GREEN : ChatColor.RED) + "Cape: " + (cape ? "ON" : "OFF"),
            ChatColor.GRAY + "Only skins that own a cape show one", ChatColor.GRAY + "Default: OFF"));
        inventory.setItem(22, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        openGui(player, inventory);
    }

    private void clickSpecialNpcEditor(Player player, String name) {
        String target = specialEditTarget.get(player.getUniqueId());
        if (target == null) return;
        if (name.equals("Back")) { specialEditTarget.remove(player.getUniqueId()); openLobbySetup(player); return; }
        if (name.equals("Default Skins")) { openSkinPicker(player); return; }
        if (name.equals("Set Skin")) {
            specialSkinInputs.put(player.getUniqueId(), target);
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Type a Minecraft username or textures.minecraft.net URL in chat. Type cancel to stop.");
            return;
        }
        if (name.startsWith("Cape: ")) {
            if ("COSMETICS".equals(target)) plugin.lobby().cosmeticsCape(!plugin.lobby().cosmeticsCape());
            else if ("LEADERBOARD".equals(target)) plugin.lobby().leaderboardCape(!plugin.lobby().leaderboardCape());
            else plugin.lobby().profileCape(!plugin.lobby().profileCape());
            plugin.saveSettings();
            respawnSpecial(target);
        }
        openSpecialNpcEditor(player, target);
    }

    private String specialLabel(String target) {
        if ("COSMETICS".equals(target)) return "Cosmetics";
        if ("LEADERBOARD".equals(target)) return "Leaderboard";
        return "Profile";
    }

    private String specialSkin(String target) {
        if ("COSMETICS".equals(target)) return plugin.lobby().cosmeticsSkin();
        if ("LEADERBOARD".equals(target)) return plugin.lobby().leaderboardSkin();
        return plugin.lobby().profileSkin();
    }

    private boolean specialCape(String target) {
        if ("COSMETICS".equals(target)) return plugin.lobby().cosmeticsCape();
        if ("LEADERBOARD".equals(target)) return plugin.lobby().leaderboardCape();
        return plugin.lobby().profileCape();
    }

    private void applySpecialSkin(Player player, String target, String skin) {
        if ("COSMETICS".equals(target)) plugin.lobby().cosmeticsSkin(skin);
        else if ("LEADERBOARD".equals(target)) plugin.lobby().leaderboardSkin(skin);
        else plugin.lobby().profileSkin(skin);
        plugin.saveSettings();
        respawnSpecial(target);
    }

    private void respawnSpecial(String target) {
        if ("COSMETICS".equals(target)) plugin.npcs().spawnCosmetics(plugin.lobby().cosmeticsNpc());
        else if ("LEADERBOARD".equals(target)) plugin.npcs().spawnLeaderboard(plugin.lobby().leaderboardNpc());
        else plugin.npcs().spawnProfile(plugin.lobby().profileNpc());
    }

    /** Snap NPC placement to block center (x/z +0.5) with horizontal facing only (pitch = 0). */
    private static Location snapNpcLocation(Player player) {
        Location loc = player.getLocation().clone();
        loc.setX(Math.floor(loc.getX()) + 0.5);
        loc.setZ(Math.floor(loc.getZ()) + 0.5);
        loc.setPitch(0f);
        // NPC faces the SAME direction the admin is looking when placing (stand facing where you want the NPC
        // to look). The old +180 flip faced it away — with packet facing now rendering correctly that put the
        // NPC's face behind the admin.
        return loc;
    }

    /** Place/relocate a queue NPC at the admin's position + facing — same flow as the cosmetics/profile NPCs. */
    private void setQueueNpcHere(Player player, LobbySettings draft, GameType type) {
        Location loc = snapNpcLocation(player);
        draft.npc(type).location(loc);
        plugin.npcs().spawn(type, draft.npc(type));
        player.sendMessage(ChatColor.GREEN + type.displayName() + " NPC set here. Shift-click it to edit its look. Click Apply to save.");
    }

    public void openShop(Player player) {
        String category = shopCategory.get(player.getUniqueId());
        if (category == null || category.equals("Traps") || category.equals("Settings")) category = "Quick Buy";
        openShopCategory(player, category);
    }

    public void openProfileStats(Player player) {
        StatsStore.Record stats = plugin.stats().get(player.getUniqueId());
        Inventory inventory = chest(27, ChatColor.DARK_GRAY + "Bed Wars Statistics");
        inventory.setItem(10, statsPaper("Overall Statistics", ProfileStats.overallLore(stats)));
        inventory.setItem(11, statsPaper("Solo Statistics", ProfileStats.modeLore("Solo", stats, stats.solo)));
        inventory.setItem(12, statsPaper("Doubles Statistics", ProfileStats.modeLore("Doubles", stats, stats.doubles)));
        inventory.setItem(13, statsPaper("Trios Statistics", ProfileStats.modeLore("3v3v3v3", stats, stats.trios)));
        inventory.setItem(14, statsPaper("Quads Statistics", ProfileStats.modeLore("4v4v4v4", stats, stats.quads)));
        inventory.setItem(22, Items.named(new ItemStack(Material.BARRIER), ChatColor.RED + "Close"));
        openGui(player, inventory);
    }

    private void clickProfileStats(Player player, String name) {
        if (name.equals("Close")) player.closeInventory();
    }

    private static ItemStack statsPaper(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------------ leaderboards GUI (read-only)

    /** Home board: mode tabs across the top, a 3x3 of stat categories, and the caller's Wins rank. */
    public void openLeaderboard(Player player) {
        if (plugin.leaderboards() == null || !GameRules.LEADERBOARD_ENABLED) {
            player.sendMessage(ChatColor.RED + "Leaderboards are disabled.");
            return;
        }
        UUID uuid = player.getUniqueId();
        GameType mode = lbMode.get(uuid);
        Inventory inventory = chest(54, LEADERBOARD_TITLE);
        leaderboardBorder(inventory);
        inventory.setItem(2, modeTab("Overall", mode == null));
        inventory.setItem(3, modeTab("Solo", mode == GameType.SOLO));
        inventory.setItem(4, modeTab("Doubles", mode == GameType.DOUBLES));
        inventory.setItem(5, modeTab("3v3v3v3", mode == GameType.TRIOS));
        inventory.setItem(6, modeTab("4v4v4v4", mode == GameType.QUADS));
        int[] slots = {20, 22, 24, 29, 31, 33, 38, 40, 42};
        for (int i = 0; i < LEADERBOARD_CATS.length && i < slots.length; i++) {
            inventory.setItem(slots[i], leaderboardCategoryIcon(player, LEADERBOARD_CATS[i], mode));
        }
        int rank = plugin.leaderboards().rankOf(uuid, LeaderboardCategory.WINS, mode);
        inventory.setItem(48, Items.named(Skins.head(player.getName()),
            ChatColor.GREEN + "Your Rank " + ChatColor.GRAY + "(" + LeaderboardService.modeLabel(mode) + " Wins)",
            rank > 0 ? ChatColor.AQUA + "#" + rank : ChatColor.GRAY + "You are not ranked yet"));
        inventory.setItem(49, Items.named(new ItemStack(Material.BOOK), ChatColor.GOLD + "Bed Wars Leaderboards",
            ChatColor.GRAY + "Pick a mode, then a category."));
        inventory.setItem(50, Items.named(new ItemStack(Material.BARRIER), ChatColor.RED + "Close"));
        openGui(player, inventory);
    }

    /** Category page: up to top-n player heads with rank badges, values, your-rank footer, pagination. */
    public void openLeaderboardCategory(Player player, LeaderboardCategory category, GameType mode, int page) {
        if (category == null) { openLeaderboard(player); return; }
        if (plugin.leaderboards() == null || !GameRules.LEADERBOARD_ENABLED) {
            player.sendMessage(ChatColor.RED + "Leaderboards are disabled.");
            return;
        }
        UUID uuid = player.getUniqueId();
        lbCategory.put(uuid, category);
        List<LeaderboardEntry> rows = plugin.leaderboards().ranking(category, mode, LeaderboardWindow.ALL_TIME);
        int pageSize = Math.max(9, Math.min(36, GameRules.LEADERBOARD_MAX_ROWS_PER_PAGE));
        int pages = Math.max(1, (int) Math.ceil(rows.size() / (double) pageSize));
        page = Math.max(0, Math.min(page, pages - 1));
        lbPage.put(uuid, page);
        Inventory inventory = chest(54, GameRules.inventoryTitle(ChatColor.DARK_GRAY + LEADERBOARD_CAT_PREFIX + category.label()));
        leaderboardBorder(inventory);
        inventory.setItem(2, modeTab("Overall", mode == null));
        inventory.setItem(3, modeTab("Solo", mode == GameType.SOLO));
        inventory.setItem(4, modeTab("Doubles", mode == GameType.DOUBLES));
        inventory.setItem(5, modeTab("3v3v3v3", mode == GameType.TRIOS));
        inventory.setItem(6, modeTab("4v4v4v4", mode == GameType.QUADS));
        int start = page * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int idx = start + i;
            if (idx >= rows.size()) break;
            LeaderboardEntry entry = rows.get(idx);
            boolean you = uuid.equals(entry.uuid());
            String head = rankColor(entry.rank()) + "#" + entry.rank() + " " + ChatColor.WHITE + entry.name()
                + (you ? ChatColor.GREEN + " (You)" : "");
            inventory.setItem(9 + i, Items.named(Skins.head(entry.name()), head,
                ChatColor.GRAY + category.label() + ": " + ChatColor.AQUA + entry.formattedValue(),
                ChatColor.DARK_GRAY + LeaderboardService.modeLabel(mode)));
        }
        if (rows.isEmpty()) {
            inventory.setItem(22, Items.named(new ItemStack(Material.PAPER), ChatColor.GRAY + "No ranked players yet",
                ChatColor.GRAY + "Play some games to appear here."));
        }
        int myRank = plugin.leaderboards().rankOf(uuid, category, mode);
        inventory.setItem(49, Items.named(Skins.head(player.getName()), ChatColor.GREEN + "Your Rank",
            myRank > 0 ? ChatColor.AQUA + "#" + myRank : ChatColor.GRAY + "You are not ranked yet"));
        inventory.setItem(45, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        if (page > 0) inventory.setItem(46, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Previous Page"));
        if (page + 1 < pages) inventory.setItem(53, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Next Page"));
        if (pages > 1) inventory.setItem(47, Items.named(new ItemStack(Material.PAPER), ChatColor.YELLOW + "Page " + (page + 1) + "/" + pages));
        openGui(player, inventory);
    }

    private void clickLeaderboardHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        if (name.equals("Close")) { player.closeInventory(); return; }
        if (name.equals("Overall")) { lbMode.remove(uuid); openLeaderboard(player); return; }
        if (name.equals("Solo")) { lbMode.put(uuid, GameType.SOLO); openLeaderboard(player); return; }
        if (name.equals("Doubles")) { lbMode.put(uuid, GameType.DOUBLES); openLeaderboard(player); return; }
        if (name.equals("3v3v3v3")) { lbMode.put(uuid, GameType.TRIOS); openLeaderboard(player); return; }
        if (name.equals("4v4v4v4")) { lbMode.put(uuid, GameType.QUADS); openLeaderboard(player); return; }
        LeaderboardCategory category = categoryByLabel(name);
        if (category != null) openLeaderboardCategory(player, category, lbMode.get(uuid), 0);
    }

    private void clickLeaderboardCategory(Player player, String name) {
        UUID uuid = player.getUniqueId();
        LeaderboardCategory category = lbCategory.get(uuid);
        if (category == null) { openLeaderboard(player); return; }
        if (name.equals("Back")) { openLeaderboard(player); return; }
        if (name.equals("Overall")) { lbMode.remove(uuid); openLeaderboardCategory(player, category, null, 0); return; }
        if (name.equals("Solo")) { lbMode.put(uuid, GameType.SOLO); openLeaderboardCategory(player, category, GameType.SOLO, 0); return; }
        if (name.equals("Doubles")) { lbMode.put(uuid, GameType.DOUBLES); openLeaderboardCategory(player, category, GameType.DOUBLES, 0); return; }
        if (name.equals("3v3v3v3")) { lbMode.put(uuid, GameType.TRIOS); openLeaderboardCategory(player, category, GameType.TRIOS, 0); return; }
        if (name.equals("4v4v4v4")) { lbMode.put(uuid, GameType.QUADS); openLeaderboardCategory(player, category, GameType.QUADS, 0); return; }
        GameType mode = lbMode.get(uuid);
        int page = lbPage.get(uuid) == null ? 0 : lbPage.get(uuid);
        if (name.equals("Previous Page")) { openLeaderboardCategory(player, category, mode, page - 1); return; }
        if (name.equals("Next Page")) { openLeaderboardCategory(player, category, mode, page + 1); return; }
        // Clicking a head / your-rank / page indicator is read-only.
    }

    private ItemStack leaderboardCategoryIcon(Player player, LeaderboardCategory category, GameType mode) {
        List<LeaderboardEntry> rows = plugin.leaderboards().ranking(category, mode, LeaderboardWindow.ALL_TIME);
        List<String> lore = new ArrayList<String>();
        if (rows.isEmpty()) lore.add(ChatColor.GRAY + "No ranked players yet");
        for (int i = 0; i < rows.size() && i < 3; i++) {
            LeaderboardEntry entry = rows.get(i);
            lore.add(rankColor(entry.rank()) + "#" + entry.rank() + " " + ChatColor.WHITE + entry.name()
                + ChatColor.GRAY + " - " + ChatColor.AQUA + entry.formattedValue());
        }
        int myRank = plugin.leaderboards().rankOf(player.getUniqueId(), category, mode);
        lore.add("");
        lore.add(myRank > 0 ? ChatColor.GREEN + "Your Rank: #" + myRank : ChatColor.GRAY + "You are not ranked yet");
        lore.add(ChatColor.YELLOW + "Click to view the top " + plugin.leaderboards().topN());
        return Items.named(new ItemStack(categoryMaterial(category)), ChatColor.GOLD + category.label(),
            lore.toArray(new String[lore.size()]));
    }

    private ItemStack modeTab(String label, boolean selected) {
        Material material = label.equals("Solo") ? Material.IRON_SWORD
            : label.equals("Doubles") ? Material.DIAMOND_SWORD
            : label.equals("3v3v3v3") ? Items.material("GOLDEN_SWORD", "GOLD_SWORD")
            : label.equals("4v4v4v4") ? Items.material("NETHERITE_SWORD", "DIAMOND_SWORD")
            : Material.NETHER_STAR;
        return Items.named(new ItemStack(material), (selected ? ChatColor.GREEN : ChatColor.YELLOW) + label,
            selected ? ChatColor.GRAY + "Selected" : ChatColor.GRAY + "Click to view");
    }

    private static LeaderboardCategory categoryByLabel(String label) {
        for (LeaderboardCategory category : LEADERBOARD_CATS) {
            if (category.label().equals(label)) return category;
        }
        return null;
    }

    /** '&'-coded rank colour (from config formats) as a ChatColor sequence. */
    /** Gray-glass frame around the edges (top/bottom rows + side columns) for the Hypixel "settings"-style look. */
    private static void leaderboardBorder(Inventory inventory) {
        ItemStack pane = Items.named(Items.stack("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 7), " ");
        for (int i = 0; i < 9; i++) { inventory.setItem(i, pane); inventory.setItem(45 + i, pane); }
        for (int r = 1; r < 5; r++) { inventory.setItem(r * 9, pane); inventory.setItem(r * 9 + 8, pane); }
    }

    private static String rankColor(int rank) {
        return ChatColor.translateAlternateColorCodes('&', GameRules.rankColor(rank));
    }

    private static Material categoryMaterial(LeaderboardCategory category) {
        switch (category) {
            case WINS: return Material.GOLD_INGOT;
            case KILLS: return Material.IRON_SWORD;
            case FINAL_KILLS: return Material.DIAMOND_SWORD;
            case BEDS: return Items.material("RED_BED", "BED");
            case WINSTREAK: return Material.BLAZE_POWDER;
            case KDR: return Items.material("IRON_AXE");
            case FKDR: return Items.material("DIAMOND_AXE");
            case LEVEL: return Items.material("EXPERIENCE_BOTTLE", "EXP_BOTTLE");
            case XP: return Items.material("EXPERIENCE_BOTTLE", "EXP_BOTTLE");
            case TOKENS: return Material.EMERALD;
            default: return Material.PAPER;
        }
    }

    public void openCosmetics(Player player) {
        StatsStore.Record stats = plugin.stats().get(player.getUniqueId());
        Inventory inventory = chest(54, ChatColor.DARK_GRAY + "My Cosmetics");
        // Sparse diamond layout (spaced category icons).
        inventory.setItem(10, cosmeticsHomeIcon(player, "Bed Destroys",
            Items.material("RED_BED", "BED"),
            "Break beds with a flair.", CosmeticsService.CAT_BED_DESTROY));
        inventory.setItem(12, cosmeticsHomeIcon(player, "Projectile Trails",
            Material.EGG,
            "Leave a trail behind arrows and fireballs.", CosmeticsService.CAT_PROJECTILE_TRAIL));
        inventory.setItem(14, cosmeticsHomeIcon(player, "Victory Dances",
            Material.ARMOR_STAND,
            "Celebrate when your team wins.", CosmeticsService.CAT_WIN_EFFECT));
        inventory.setItem(16, cosmeticsHomeIcon(player, "Final Kill Effects",
            Material.REDSTONE,
            "Big effects on a final kill.", CosmeticsService.CAT_FINAL_KILL_EFFECT));
        inventory.setItem(19, cosmeticsHomeIcon(player, "Glyphs",
            Material.DIAMOND,
            "Draw a glyph when you kill.", null));
        inventory.setItem(21, cosmeticsHomeIcon(player, "Hats",
            Items.material("LEATHER_HELMET", "LEATHER_HELMET"),
            "Wear a hat in lobby and matches.", null));
        inventory.setItem(23, cosmeticsHomeIcon(player, "Kill Messages",
            Items.material("OAK_SIGN", "SIGN"),
            "Custom chat lines when you get a kill.", CosmeticsService.CAT_KILL_MESSAGE));
        inventory.setItem(25, cosmeticsHomeIcon(player, "Prestige Customizer",
            Material.NAME_TAG,
            "Customize your prestige look.", CosmeticsService.CAT_PRESTIGE));
        inventory.setItem(28, cosmeticsHomeIcon(player, "Shopkeeper Skins",
            Items.material("VILLAGER_SPAWN_EGG", "MONSTER_EGG"),
            "Reskin your team's shop NPCs.", CosmeticsService.CAT_SHOPKEEPER_SKIN));
        inventory.setItem(30, cosmeticsHomeIcon(player, "Sprays",
            Material.MAP,
            "Spray images on walls.", null));
        inventory.setItem(32, cosmeticsHomeIcon(player, "Death Cries",
            Items.material("WITHER_SKELETON_SKULL", "SKULL_ITEM"),
            "Play a cry when you die.", null));
        inventory.setItem(34, cosmeticsHomeIcon(player, "Island Toppers",
            Items.material("OAK_SAPLING", "SAPLING"),
            "Decorate your island spawn.", null));
        inventory.setItem(37, cosmeticsHomeIcon(player, "Wood Skins",
            Items.material("OAK_LOG", "LOG"),
            "Custom wood blocks for builds.", CosmeticsService.CAT_WOOD_SKIN));
        inventory.setItem(39, cosmeticsHomeIcon(player, "Figurines",
            Items.material("PLAYER_HEAD", "SKULL_ITEM"),
            "Place cute figurines on your island.", null));
        inventory.setItem(41, cosmeticsHomeIcon(player, "Kill Effects",
            Items.material("BLAZE_POWDER", "BLAZE_POWDER"),
            "Particles when you get a kill.", CosmeticsService.CAT_KILL_EFFECT));
        inventory.setItem(49, Items.named(new ItemStack(Material.EMERALD),
            ChatColor.GREEN + "Tokens: " + ChatColor.YELLOW + GameRules.commas(stats.tokens),
            ChatColor.GRAY + "Spend tokens to buy cosmetics"));
        openGui(player, inventory);
    }

    private ItemStack cosmeticsHomeIcon(Player player, String title, Material icon, String description, String category) {
        List<String> lore = new ArrayList<String>();
        lore.add("");
        lore.add(ChatColor.GRAY + description);
        lore.add("");
        boolean available = category != null;
        if (available) {
            int owned = ownedCount(player, category);
            int total = plugin.cosmetics().category(category).size();
            lore.add(progressBar(owned, total));
            lore.add(ChatColor.GRAY + "Unlocked: " + ChatColor.WHITE + owned + ChatColor.GRAY + "/"
                + ChatColor.WHITE + total);
            String selected = equippedName(player, category);
            if (selected != null && !selected.isEmpty()) {
                lore.add(ChatColor.GRAY + "Selected: " + ChatColor.AQUA + selected);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "\u25CF Click to view!");
        } else {
            lore.add(ChatColor.DARK_GRAY + "\u2715 " + ChatColor.RED + "Coming Soon");
        }
        // Name stays EXACTLY `title` — clickCosmeticsHome routes by stripped name.
        return Items.named(new ItemStack(icon), (available ? ChatColor.GREEN : ChatColor.GRAY) + title,
            lore.toArray(new String[0]));
    }

    private int ownedCount(Player player, String category) {
        int owned = 0;
        for (CosmeticsService.Cosmetic cosmetic : plugin.cosmetics().category(category)) {
            if (plugin.stats().ownsCosmetic(player.getUniqueId(), cosmetic.id)) owned++;
        }
        return owned;
    }

    private String equippedName(Player player, String category) {
        CosmeticsService.Cosmetic cosmetic = plugin.cosmetics().get(plugin.stats().equippedCosmetic(player.getUniqueId(), category));
        return cosmetic == null ? null : ChatColor.stripColor(cosmetic.name);
    }

    /** Number of cosmetic items we fit per category page (slots 9..44). */
    private static final int COSMETICS_PER_PAGE = 36;

    private void openCosmeticsCategory(Player player, String category) {
        String key = CosmeticsService.normalizeCategory(category);
        if (key == null) { openCosmetics(player); return; }
        UUID uuid = player.getUniqueId();
        String prevKey = cosmeticCategory.get(uuid);
        if (!key.equals(prevKey)) {
            cosmeticCategory.put(uuid, key);
            cosmeticPage.put(uuid, 0);
        }
        StatsStore.Record stats = plugin.stats().get(uuid);
        List<CosmeticsService.Cosmetic> all = plugin.cosmetics().category(key);
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) COSMETICS_PER_PAGE));
        int page = Math.max(0, Math.min(cosmeticPage.get(uuid) == null ? 0 : cosmeticPage.get(uuid), pages - 1));
        cosmeticPage.put(uuid, page);
        Inventory inventory = chest(54, ChatColor.DARK_GRAY + CosmeticsService.categoryDisplay(key));
        inventory.setItem(4, Items.named(new ItemStack(Material.EMERALD),
            ChatColor.GREEN + "Tokens: " + ChatColor.YELLOW + GameRules.commas(stats.tokens),
            ChatColor.GRAY + CosmeticsService.categoryDisplay(key)));
        inventory.setItem(45, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back"));
        int start = page * COSMETICS_PER_PAGE;
        int slot = 9;
        for (int i = start; i < all.size() && slot < 45; i++) {
            inventory.setItem(slot++, cosmeticsIcon(player, all.get(i)));
        }
        if (pages > 1) {
            inventory.setItem(49, Items.named(new ItemStack(Material.PAPER),
                ChatColor.YELLOW + "Page " + (page + 1) + "/" + pages,
                ChatColor.GRAY + "" + all.size() + " items"));
            if (page > 0) inventory.setItem(46, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Previous Page"));
            if (page + 1 < pages) inventory.setItem(52, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Next Page"));
        }
        openGui(player, inventory);
    }

    private ItemStack cosmeticsIcon(Player player, CosmeticsService.Cosmetic cosmetic) {
        boolean owned = plugin.stats().ownsCosmetic(player.getUniqueId(), cosmetic.id);
        boolean equipped = cosmetic.id.equals(plugin.stats().equippedCosmetic(player.getUniqueId(), cosmetic.category));
        String[] iconPair = CosmeticsService.iconFor(cosmetic);
        // Shopkeeper skins preview the actual skin as a player head (the effect is the Mojang username).
        ItemStack stack = CosmeticsService.CAT_SHOPKEEPER_SKIN.equals(cosmetic.category)
            ? Skins.head(cosmetic.effect)
            : new ItemStack(Items.material(iconPair[0], iconPair[1]));
        String displayName;
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.DARK_GRAY + "Bedlam Cosmetic");
        String flavor = CosmeticsService.flavorFor(cosmetic);
        if (flavor != null && !flavor.isEmpty()) {
            lore.add("");
            lore.add(ChatColor.GRAY + flavor);
        }
        String sample = cosmetic.templateFor("kill");
        if (sample != null && !sample.isEmpty()) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Preview:");
            String preview = ChatColor.stripColor(CosmeticsService.formatKillMessage(sample, "Victim", "You", false));
            lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + preview);
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "Rarity: " + CosmeticsService.rarityLabel(cosmetic));
        if (owned) {
            lore.add(ChatColor.GREEN + "\u2714 Unlocked");
            displayName = ChatColor.stripColor(cosmetic.name);
        } else {
            lore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + GameRules.commas(cosmetic.cost)
                + ChatColor.GOLD + " Tokens");
            displayName = ChatColor.stripColor(cosmetic.name);
        }
        lore.add("");
        if (equipped) {
            lore.add(ChatColor.GREEN + "\u25CF EQUIPPED " + ChatColor.DARK_GRAY + "("
                + ChatColor.GRAY + "click to unequip" + ChatColor.DARK_GRAY + ")");
            displayName = ChatColor.GREEN + "\u00BB " + displayName;
        } else if (owned) {
            lore.add(ChatColor.AQUA + "\u25CF OWNED " + ChatColor.DARK_GRAY + "("
                + ChatColor.GRAY + "click to equip" + ChatColor.DARK_GRAY + ")");
        } else {
            lore.add(ChatColor.YELLOW + "\u25CF Click to buy & equip");
        }
        // Routing tag — clickCosmeticsCategory resolves purchases from this line.
        lore.add(ChatColor.DARK_GRAY + "Bedlam Cosmetic: " + cosmetic.id);

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return Items.named(stack, displayName, lore.toArray(new String[0]));
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        if (equipped) applyGlint(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Enchant shimmer so equipped cosmetics stand out, hidden flags so no tooltip text leaks. */
    private static void applyGlint(ItemMeta meta) {
        org.bukkit.enchantments.Enchantment enchant = org.bukkit.enchantments.Enchantment.getByName("DURABILITY");
        if (enchant == null) enchant = org.bukkit.enchantments.Enchantment.getByName("UNBREAKING");
        if (enchant != null) {
            try { meta.addEnchant(enchant, 1, true); } catch (Throwable ignored) { }
        }
        try { meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); } catch (Throwable ignored) { }
    }

    /** Hypixel-style unlock bar: green filled slots, dark empty ones. */
    private static String progressBar(int owned, int total) {
        int filled = total <= 0 ? 0 : Math.round((owned * 10.0f) / total);
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.DARK_GRAY).append("[");
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? ChatColor.GREEN : ChatColor.DARK_GRAY).append("\u258C");
        }
        bar.append(ChatColor.DARK_GRAY).append("]");
        return bar.toString();
    }

    private void clickCosmeticsHome(Player player, String name) {
        if (name.startsWith("Tokens:")) return;
        if (name.equals("Kill Messages")) openCosmeticsCategory(player, CosmeticsService.CAT_KILL_MESSAGE);
        else if (name.equals("Kill Effects")) openCosmeticsCategory(player, CosmeticsService.CAT_KILL_EFFECT);
        else if (name.equals("Victory Dances") || name.equals("Win Effects")) {
            openCosmeticsCategory(player, CosmeticsService.CAT_WIN_EFFECT);
        } else if (name.equals("Wood Skins")) openCosmeticsCategory(player, CosmeticsService.CAT_WOOD_SKIN);
        else if (name.equals("Final Kill Effects")) openCosmeticsCategory(player, CosmeticsService.CAT_FINAL_KILL_EFFECT);
        else if (name.equals("Prestige Customizer")) openCosmeticsCategory(player, CosmeticsService.CAT_PRESTIGE);
        else if (name.equals("Projectile Trails")) openCosmeticsCategory(player, CosmeticsService.CAT_PROJECTILE_TRAIL);
        else if (name.equals("Bed Destroys")) openCosmeticsCategory(player, CosmeticsService.CAT_BED_DESTROY);
        else if (name.equals("Shopkeeper Skins")) openCosmeticsCategory(player, CosmeticsService.CAT_SHOPKEEPER_SKIN);
        else if (isComingSoonCosmetic(name)) {
            player.sendMessage(ChatColor.RED + "Coming Soon");
        }
    }

    private static boolean isComingSoonCosmetic(String name) {
        return name.equals("Glyphs") || name.equals("Hats")
            || name.equals("Sprays") || name.equals("Death Cries")
            || name.equals("Island Toppers") || name.equals("Figurines");
    }

    private void clickCosmeticsCategory(Player player, String title, ItemStack clicked) {
        String name = Items.name(clicked);
        if (name.equals("Back") || name.startsWith("Tokens:")) {
            openCosmetics(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (name.equals("Next Page") || name.equals("Previous Page")) {
            int page = cosmeticPage.get(uuid) == null ? 0 : cosmeticPage.get(uuid);
            cosmeticPage.put(uuid, name.equals("Next Page") ? page + 1 : Math.max(0, page - 1));
            openCosmeticsCategory(player, title);
            return;
        }
        String id = cosmeticId(clicked);
        if (id == null) return;
        String result = plugin.cosmetics().clickOffer(player, id);
        player.sendMessage(result);
        if (result.startsWith(ChatColor.GREEN.toString()) || result.startsWith(ChatColor.YELLOW.toString())) Sounds.purchase(player);
        openCosmeticsCategory(player, title);
    }

    private static String cosmeticId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().getLore() == null) return null;
        for (String line : item.getItemMeta().getLore()) {
            String clean = ChatColor.stripColor(line);
            if (clean.startsWith("Bedlam Cosmetic: ")) return clean.substring("Bedlam Cosmetic: ".length()).trim();
        }
        return null;
    }

    public void openSpectate(Player player) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        Inventory inventory = chest(54, ChatColor.DARK_GRAY + "Spectate");
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
        Inventory inventory = chest(54, SHOP_TITLE);
        inventory.setItem(0, categoryTab(Material.NETHER_STAR, "Quick Buy", category));
        inventory.setItem(1, categoryTab(Items.material("WHITE_TERRACOTTA", "STAINED_CLAY"), "Blocks", category));
        inventory.setItem(2, categoryTab(Items.material("GOLDEN_SWORD", "GOLD_SWORD"), "Melee", category));
        inventory.setItem(3, categoryTab(Items.material("CHAINMAIL_BOOTS"), "Armor", category));
        inventory.setItem(4, categoryTab(Items.material("STONE_PICKAXE"), "Tools", category));
        inventory.setItem(5, categoryTab(Material.BOW, "Ranged", category));
        // 1.8: BREWING_STAND is the block (id 117) — invisible in GUIs. Item is BREWING_STAND_ITEM.
        inventory.setItem(6, categoryTab(Items.material(Items.POTIONS_TAB_MATERIALS), "Potions", category));
        inventory.setItem(7, categoryTab(Material.TNT, "Utility", category));
        ItemStack gray = Items.named(Items.stack("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 7), " ");
        ItemStack lime = Items.named(Items.stack("LIME_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 5), " ");
        String[] cats = {"Quick Buy", "Blocks", "Melee", "Armor", "Tools", "Ranged", "Potions", "Utility"};
        for (int i = 0; i < 8; i++) inventory.setItem(9 + i, cats[i].equals(category) ? lime : gray);
        inventory.setItem(17, gray);
        int[] borders = {18, 26, 27, 35, 36, 44};
        for (int slot : borders) inventory.setItem(slot, gray);
        ArenaManager manager = plugin.games().arena(player);
        TeamColor team = manager == null ? TeamColor.RED : manager.arena().team(player.getUniqueId());
        if (team == null) team = TeamColor.RED;
        Arena arena = manager == null ? null : manager.arena();
        if (category.equals("Quick Buy")) {
            putQuickBuyFavorites(inventory, player, team, arena, gray);
        } else if (category.equals("Settings")) {
            putFavoriteEditor(inventory, player, team, arena, gray);
        } else if (category.equals("Tools")) {
            putToolOffers(inventory, player, arena, 19, 20, 21);
        } else {
            for (ShopCatalog.Offer offer : ShopCatalog.offers(category)) {
                inventory.setItem(offer.slot, catalogOffer(player, team, arena, offer));
            }
        }
        if (!category.equals("Quick Buy") && !category.equals("Settings")) addAssignHints(inventory);
        Integer assign = favoriteAssignSlot.get(player.getUniqueId());
        String settingsLore = category.equals("Settings")
            ? ChatColor.GRAY + "Editing favorites"
            : (assign != null
                ? ChatColor.YELLOW + "Assigning slot #" + (assign + 1) + " — click an item"
                : ChatColor.GRAY + "Edit your 9 favorite slots");
        inventory.setItem(48, Items.named(new ItemStack(Material.COMPASS), ChatColor.GREEN + "Quick Buy Settings", settingsLore));
        inventory.setItem(49, Items.named(new ItemStack(Items.material("FIREWORK_STAR", "FIREWORK_CHARGE")), ChatColor.GREEN + "Close", ChatColor.YELLOW + "Click to close"));
        boolean importEnabled = !hypixelApiKey().isEmpty();
        inventory.setItem(50, Items.named(new ItemStack(Material.PAPER), ChatColor.AQUA + "Import Hypixel Quick Buy",
            importEnabled ? ChatColor.GRAY + "Load your personal Hypixel layout" : ChatColor.RED + "Set BEDLAM_HYPIXEL_API_KEY",
            importEnabled ? ChatColor.YELLOW + "Click to import" : ChatColor.DARK_GRAY + "or hypixel-api-key in config.yml"));
        openGui(player, inventory);
    }

    private void putQuickBuyFavorites(Inventory inventory, Player player, TeamColor team, Arena arena, ItemStack emptyPane) {
        String[] favs = plugin.stats().favorites(player.getUniqueId());
        String pending = favoritePendingItem.get(player.getUniqueId());
        for (int i = 0; i < StatsStore.FAVORITE_SLOTS; i++) {
            String key = favs[i];
            int slot = GameRules.QUICK_BUY_SLOTS[i];
            if (key == null || key.isEmpty()) {
                inventory.setItem(slot, Items.named(emptyPane.clone(), ChatColor.RED + "Empty slot!",
                    pending == null ? ChatColor.GRAY + "Sneak Click any shop item" : ChatColor.GREEN + "Click to place " + pending,
                    pending == null ? ChatColor.GRAY + "to add it here." : ChatColor.GRAY + "in this Quick Buy slot."));
            } else {
                ItemStack offer = offerForKey(player, team, arena, key);
                inventory.setItem(slot, appendLore(offer,
                    pending == null ? ChatColor.AQUA + "Sneak Click to remove from Quick Buy!" : ChatColor.GREEN + "Click to replace with " + pending));
            }
        }
    }

    private void putFavoriteEditor(Inventory inventory, Player player, TeamColor team, Arena arena, ItemStack emptyPane) {
        String[] favs = plugin.stats().favorites(player.getUniqueId());
        Integer assign = favoriteAssignSlot.get(player.getUniqueId());
        for (int i = 0; i < StatsStore.FAVORITE_SLOTS; i++) {
            String key = favs[i];
            int slot = GameRules.QUICK_BUY_SLOTS[i];
            if (key == null || key.isEmpty()) {
                boolean picking = assign != null && assign == i;
                inventory.setItem(slot, Items.named(emptyPane.clone(), ChatColor.YELLOW + "Favorite #" + (i + 1),
                    picking ? ChatColor.GREEN + "Selected — click a shop item" : ChatColor.GRAY + "Click, then click a shop item",
                    ChatColor.DARK_GRAY + "Browse categories to pick"));
            } else {
                ItemStack offer = offerForKey(player, team, arena, key);
                List<String> lore = offer.getItemMeta() != null && offer.getItemMeta().getLore() != null
                    ? new ArrayList<String>(offer.getItemMeta().getLore()) : new ArrayList<String>();
                lore.add(ChatColor.RED + "Click to clear this slot");
                inventory.setItem(slot, Items.named(offer.clone(), ChatColor.GREEN + key, lore.toArray(new String[0])));
            }
        }
        inventory.setItem(45, Items.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Back",
            ChatColor.GRAY + "Return to Quick Buy"));
    }

    /** Shop offer icon for a persisted buy-key; unknown keys show a barrier. */
    private ItemStack offerForKey(Player player, TeamColor team, Arena arena, String key) {
        ShopCatalog.Offer catalog = ShopCatalog.offer(key);
        if (catalog != null) return catalogOffer(player, team, arena, catalog);
        if (key.equals("Shears") || key.equals("Permanent Shears")) {
            if (arena != null && arena.shearsOwned(player.getUniqueId())) {
                return Items.named(new ItemStack(Items.material("SHEARS")), ChatColor.GREEN + "Permanent Shears",
                    ChatColor.GREEN + "UNLOCKED", ChatColor.GRAY + "Kept on every respawn");
            }
            return shopOffer(player, new ItemStack(Items.material("SHEARS")), "Permanent Shears", "Shears", 20, Material.IRON_INGOT,
                ChatColor.GRAY + "Permanent item", ChatColor.DARK_GRAY + "Always respawn with shears");
        }
        if (key.endsWith(" Pickaxe") || key.endsWith(" Axe")) {
            boolean pickaxe = key.endsWith(" Pickaxe");
            int current = arena == null ? 0 : (pickaxe ? arena.pickaxeTier(player.getUniqueId()) : arena.axeTier(player.getUniqueId()));
            return nextToolOffer(player, pickaxe, current);
        }
        return Items.named(new ItemStack(Material.BARRIER), ChatColor.RED + key, ChatColor.GRAY + "Unknown shop item");
    }

    private ItemStack catalogOffer(Player player, TeamColor team, Arena arena, ShopCatalog.Offer offer) {
        String[] lore = new String[offer.lore.length];
        for (int i = 0; i < lore.length; i++) lore[i] = ChatColor.GRAY + offer.lore[i];
        return shopOffer(player, catalogItem(offer.key, team, arena), offer.display, offer.key, offer.cost,
            Items.material(offer.currency), lore);
    }

    private static ItemStack catalogItem(String key, TeamColor team, Arena arena) {
        if (key.equals("16 Wool")) return team.wool(16);
        if (key.equals("16 Hardened Clay")) return Items.stack("WHITE_TERRACOTTA", "STAINED_CLAY", 16, (short) 0);
        if (key.equals("4 Blast-Proof Glass")) return team.glass(4);
        if (key.equals("12 End Stone")) return new ItemStack(Items.material("END_STONE", "ENDER_STONE"), 12);
        if (key.equals("8 Ladders")) return new ItemStack(Material.LADDER, 8);
        if (key.equals("16 Oak Planks")) return Items.stack("OAK_PLANKS", "WOOD", 16, (short) 0);
        if (key.equals("4 Obsidian")) return new ItemStack(Material.OBSIDIAN, 4);
        if (key.equals("8 Ice")) return new ItemStack(Material.ICE, 8);
        boolean sharp = arena != null && team != null && arena.sharpness(team);
        if (key.equals("Stone Sword")) return sword(Items.material("STONE_SWORD"), sharp);
        if (key.equals("Iron Sword")) return sword(Material.IRON_SWORD, sharp);
        if (key.equals("Diamond Sword")) return sword(Items.material("DIAMOND_SWORD"), sharp);
        if (key.equals("Knockback Stick")) {
            ItemStack stick = Items.unbreakable(new ItemStack(Items.material("STICK")));
            Enchantments.add(stick, 1, "KNOCKBACK");
            return stick;
        }
        if (key.equals("Permanent Chainmail Armor")) return new ItemStack(Items.material("CHAINMAIL_BOOTS"));
        if (key.equals("Permanent Iron Armor")) return new ItemStack(Items.material("IRON_BOOTS"));
        if (key.equals("Permanent Diamond Armor")) return new ItemStack(Items.material("DIAMOND_BOOTS"));
        if (key.equals("Bow")) return Items.unbreakable(new ItemStack(Material.BOW));
        if (key.equals("8 Arrows")) return new ItemStack(Material.ARROW, 8);
        if (key.equals("Punch Bow")) {
            ItemStack bow = Items.unbreakable(new ItemStack(Material.BOW));
            Enchantments.add(bow, 1, "ARROW_KNOCKBACK", "PUNCH");
            return bow;
        }
        if (key.equals("Speed II Potion (45 seconds)")) return Items.drinkPotion(Items.potionType("SPEED"), GameRules.POTION_SPEED_TICKS, GameRules.POTION_SPEED_AMPLIFIER, (short) 8226);
        if (key.equals("Jump V Potion (45 seconds)")) return Items.drinkPotion(Items.potionType("JUMP", "JUMP_BOOST"), GameRules.POTION_JUMP_TICKS, GameRules.POTION_JUMP_AMPLIFIER, (short) 8235);
        if (key.equals("Invisibility Potion (30 seconds)")) return Items.drinkPotion(Items.potionType("INVISIBILITY"), GameRules.POTION_INVIS_TICKS, 0, (short) 8206);
        if (key.equals("Golden Apple")) return new ItemStack(Items.material("GOLDEN_APPLE"));
        if (key.equals("16 Snowballs")) return new ItemStack(Items.material("SNOWBALL", "SNOW_BALL"), 16);
        if (key.equals("Fireball")) return new ItemStack(Items.material("FIRE_CHARGE", "FIREBALL"));
        if (key.equals("TNT")) return new ItemStack(Material.TNT);
        if (key.equals("Ender Pearl")) return new ItemStack(Material.ENDER_PEARL);
        if (key.equals("Water Bucket")) return new ItemStack(Material.WATER_BUCKET);
        if (key.equals("Magic Milk")) return Items.named(new ItemStack(Items.material("MILK_BUCKET")), ChatColor.AQUA + "Magic Milk", ChatColor.GRAY + "30 seconds of trap immunity");
        if (key.equals("4 Sponges")) return new ItemStack(Material.SPONGE, 4);
        if (key.equals("Bridge Egg")) return Items.named(new ItemStack(Material.EGG), ChatColor.GREEN + "Bridge Egg",
            ChatColor.GRAY + "Throws a team-wool bridge", ChatColor.GRAY + "along its flight path");
        if (key.equals("Dream Defender")) return Items.named(Items.stack("IRON_GOLEM_SPAWN_EGG", "MONSTER_EGG", 1, (short) 99),
            ChatColor.AQUA + "Dream Defender", ChatColor.GRAY + "Guards your team for 4 minutes");
        if (key.equals("Pop-up Tower")) return Items.named(new ItemStack(Material.CHEST), ChatColor.GREEN + "Pop-up Tower",
            ChatColor.GRAY + "Right-click a block to deploy");
        throw new IllegalArgumentException("No item for shop key " + key);
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
        if (team == null) return;
        Inventory inventory = chest(45, UPGRADES_TITLE);

        inventory.setItem(10, upgradeOffer(player, new ItemStack(Material.IRON_SWORD), "Sharpened Swords", GameRules.sharpnessCost(),
            arena.sharpness(team), ChatColor.GRAY + "Your team gets Sharpness I", ChatColor.GRAY + "on all swords."));
        int protection = arena.protection(team);
        int protectionCost = GameRules.protectionCost(protection);
        inventory.setItem(11, upgradeOffer(player, new ItemStack(Material.IRON_CHESTPLATE),
            "Reinforced Armor " + roman(Math.min(protection + 1, 4)), protectionCost, protection >= 4,
            ChatColor.GRAY + "Your team gets Protection", ChatColor.GRAY + "on all armor pieces."));
        int haste = arena.hasteLevel(team);
        inventory.setItem(12, upgradeOffer(player, new ItemStack(Items.material("GOLDEN_PICKAXE", "GOLD_PICKAXE")),
            "Maniac Miner " + roman(Math.min(haste + 1, 2)), GameRules.hasteCost(haste), haste >= 2,
            ChatColor.GRAY + "Your team gets Haste", ChatColor.GRAY + "for the entire game."));

        int forge = arena.forgeLevel(team);
        inventory.setItem(19, upgradeOffer(player, new ItemStack(Material.FURNACE),
            "Iron Forge " + roman(Math.min(forge + 1, 4)), GameRules.forgeUpgradeCost(forge), forge >= 4,
            ChatColor.GRAY + "Upgrades your island resource", ChatColor.GRAY + "generator."));
        inventory.setItem(20, upgradeOffer(player, new ItemStack(Items.material("BEACON")), "Heal Pool", GameRules.healPoolCost(),
            arena.healPool(team), ChatColor.GRAY + "Creates a regeneration field", ChatColor.GRAY + "around your base."));
        int boots = arena.cushionedBootsLevel(team);
        inventory.setItem(21, upgradeOffer(player, new ItemStack(Items.material("DIAMOND_BOOTS")),
            "Cushioned Boots " + roman(Math.min(boots + 1, 2)), GameRules.cushionedBootsCost(boots), boots >= 2,
            ChatColor.GRAY + "Your team gets Feather Falling", ChatColor.GRAY + "on their boots."));

        int trapCost = GameRules.trapDiamondCost(arena.traps(team).size());
        boolean queueFull = arena.traps(team).size() >= GameRules.TRAP_QUEUE_MAX;
        inventory.setItem(15, trapOffer(player, "Blindness Trap", Items.material("TRIPWIRE_HOOK"), trapCost, queueFull,
            ChatColor.GRAY + "Inflicts Blindness and Slowness", ChatColor.GRAY + "for 8 seconds."));
        inventory.setItem(16, trapOffer(player, "Counter-Offensive Trap", Items.material("FEATHER"), trapCost, queueFull,
            ChatColor.GRAY + "Grants Speed II and Jump Boost II", ChatColor.GRAY + "for 15 seconds to allied players", ChatColor.GRAY + "near your base."));
        inventory.setItem(17, trapOffer(player, "Reveal Trap", Items.material("REDSTONE_TORCH", "REDSTONE_TORCH_ON"), trapCost, queueFull,
            ChatColor.GRAY + "Reveals invisible players as well as", ChatColor.GRAY + "their name and team."));
        inventory.setItem(24, trapOffer(player, "Miner Fatigue Trap", Items.material("GOLDEN_PICKAXE", "GOLD_PICKAXE"), trapCost, queueFull,
            ChatColor.GRAY + "Inflicts Mining Fatigue for 8 seconds."));

        ItemStack divider = Items.named(Items.stack("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 1, (short) 7),
            ChatColor.GRAY + "↑ Purchasable", ChatColor.GRAY + "↓ Traps Queue");
        for (int slot = 27; slot < 36; slot++) inventory.setItem(slot, divider);

        List<Arena.TrapType> traps = arena.traps(team);
        for (int i = 0; i < GameRules.TRAP_QUEUE_MAX; i++) {
            int amount = i + 1;
            if (i < traps.size()) {
                inventory.setItem(39 + i, Items.named(team.wool(amount), ChatColor.GREEN + "Trap #" + amount,
                    ChatColor.WHITE + traps.get(i).displayName()));
            } else {
                inventory.setItem(39 + i, Items.named(Items.stack("GRAY_WOOL", "WOOL", amount, (short) 7),
                    ChatColor.GRAY + "Trap slot #" + amount, ChatColor.DARK_GRAY + "Buy a trap above."));
            }
        }
        openGui(player, inventory);
    }

    private static ItemStack upgradeOffer(Player player, ItemStack icon, String name, int cost, boolean purchased, String... description) {
        List<String> lore = new ArrayList<String>();
        for (String line : description) lore.add(line);
        lore.add("");
        lore.add(diamondCostLine(cost));
        lore.add("");
        if (purchased) lore.add(ChatColor.GREEN + "Purchased!");
        else if (hasDiamonds(player, cost)) lore.add(ChatColor.YELLOW + "Click to purchase!");
        else lore.add(ChatColor.RED + "You don't have enough Diamonds!");
        return Items.named(icon, ChatColor.AQUA + name, lore.toArray(new String[0]));
    }

    private static ItemStack trapOffer(Player player, String name, Material icon, int cost, boolean queueFull, String... description) {
        List<String> lore = new ArrayList<String>();
        for (String line : description) lore.add(line);
        lore.add("");
        lore.add(diamondCostLine(cost));
        lore.add("");
        if (queueFull) lore.add(ChatColor.RED + "Your traps queue is full!");
        else if (hasDiamonds(player, cost)) lore.add(ChatColor.YELLOW + "Click to purchase!");
        else lore.add(ChatColor.RED + "You don't have enough Diamonds!");
        return Items.named(new ItemStack(icon), ChatColor.RED + name, lore.toArray(new String[0]));
    }

    private static String diamondCostLine(int cost) {
        return ChatColor.GRAY + "Cost: " + ChatColor.AQUA + cost + " Diamond" + (cost == 1 ? "" : "s");
    }

    private static boolean hasDiamonds(Player player, int cost) {
        return player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), cost);
    }
    private void buy(Player player, String name, boolean shiftLeft, int rawSlot) {
        if (name == null || name.isEmpty() || name.equals(" ")) return;
        if (name.equals("Quick Buy") || name.equals("Blocks") || name.equals("Melee") || name.equals("Armor")
            || name.equals("Tools") || name.equals("Ranged") || name.equals("Potions") || name.equals("Utility")) {
            openShopCategory(player, name);
            return;
        }
        if (name.equals("Close")) {
            favoriteAssignSlot.remove(player.getUniqueId());
            favoritePendingItem.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (name.equals("Import Hypixel Quick Buy")) {
            importHypixelQuickBuy(player);
            return;
        }
        if (name.equals("Quick Buy Settings")) {
            openShopCategory(player, "Settings");
            return;
        }
        if (name.equals("Back")) {
            favoriteAssignSlot.remove(player.getUniqueId());
            favoritePendingItem.remove(player.getUniqueId());
            openShopCategory(player, "Quick Buy");
            return;
        }
        String category = shopCategory.containsKey(player.getUniqueId()) ? shopCategory.get(player.getUniqueId()) : "Quick Buy";
        int favoriteIndex = quickBuyIndex(rawSlot);
        String pending = favoritePendingItem.get(player.getUniqueId());
        if (category.equals("Quick Buy") && favoriteIndex >= 0) {
            if (pending != null) {
                plugin.stats().setFavorite(player.getUniqueId(), favoriteIndex, pending);
                favoritePendingItem.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + pending + " added to Quick Buy slot #" + (favoriteIndex + 1) + ".");
                Sounds.purchase(player);
                openShopCategory(player, "Quick Buy");
                return;
            }
            if (shiftLeft) {
                plugin.stats().setFavorite(player.getUniqueId(), favoriteIndex, "");
                player.sendMessage(ChatColor.YELLOW + "Removed Quick Buy slot #" + (favoriteIndex + 1) + ".");
                openShopCategory(player, "Quick Buy");
                return;
            }
        }
        if (name.equals("Empty slot") || name.equals("Empty slot!")) return;
        if (shiftLeft && !category.equals("Quick Buy") && !category.equals("Settings") && isFavoriteOffer(name)) {
            String key = favoriteKey(name);
            favoritePendingItem.put(player.getUniqueId(), key);
            favoriteAssignSlot.remove(player.getUniqueId());
            player.sendMessage(ChatColor.AQUA + "Choose a Quick Buy slot for " + ChatColor.YELLOW + key + ChatColor.AQUA + ".");
            openShopCategory(player, "Quick Buy");
            return;
        }
        if (category.equals("Settings")) {
            if (favoriteIndex < 0) return;
            if (name.startsWith("Favorite #")) {
                favoriteAssignSlot.put(player.getUniqueId(), favoriteIndex);
                player.sendMessage(ChatColor.YELLOW + "Click a shop item to set favorite #" + (favoriteIndex + 1) + ".");
                openShopCategory(player, "Blocks");
                return;
            }
            plugin.stats().setFavorite(player.getUniqueId(), favoriteIndex, "");
            player.sendMessage(ChatColor.YELLOW + "Cleared favorite #" + (favoriteIndex + 1) + ".");
            openShopCategory(player, "Settings");
            return;
        }
        Integer assign = favoriteAssignSlot.get(player.getUniqueId());
        if (assign != null) {
            if (name.equals("MAXED") || name.contains("UNLOCKED")) return;
            plugin.stats().setFavorite(player.getUniqueId(), assign, favoriteKey(name));
            favoriteAssignSlot.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Favorite #" + (assign + 1) + " set to " + name + ".");
            openShopCategory(player, "Settings");
            return;
        }
        if (name.equals("MAXED") || name.contains("UNLOCKED")) return;
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null || arena.state() != Arena.State.RUNNING) return;
        ShopCatalog.Offer catalog = ShopCatalog.offer(name);
        if (catalog != null) {
            if (pay(player, Items.material(catalog.currency), catalog.cost)) grantCatalogOffer(player, manager, arena, team, catalog);
            return;
        }
        if ((name.endsWith(" Pickaxe") || name.endsWith(" Axe")) && buyToolUpgrade(player, arena, manager, name)) { /* done */ }
        else if ((name.equals("Shears") || name.equals("Permanent Shears")) && !arena.shearsOwned(player.getUniqueId()) && pay(player, Material.IRON_INGOT, 20)) {
            arena.shearsOwned(player.getUniqueId(), true);
            manager.giveOwnedTools(player);
        }
        openShopCategory(player, shopCategory.containsKey(player.getUniqueId()) ? shopCategory.get(player.getUniqueId()) : "Quick Buy");
    }

    private static void grantCatalogOffer(Player player, ArenaManager manager, Arena arena, TeamColor team, ShopCatalog.Offer offer) {
        if (offer.key.equals("Permanent Chainmail Armor")) {
            arena.chainmailOwned(player.getUniqueId(), true);
            manager.equipArmor(player, team);
            return;
        }
        if (offer.key.equals("Permanent Iron Armor")) {
            if (arena.armorTier(player.getUniqueId()) < 1) arena.armorTier(player.getUniqueId(), 1);
            manager.equipArmor(player, team);
            return;
        }
        if (offer.key.equals("Permanent Diamond Armor")) {
            arena.armorTier(player.getUniqueId(), 2);
            manager.equipArmor(player, team);
            return;
        }
        ItemStack item = catalogItem(offer.key, team, arena);
        if (GameRules.isSword(item.getType().name())) giveSword(player, item);
        else give(player, item);
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

    private void upgrade(Player player, String name) {
        ArenaManager manager = plugin.games().arena(player);
        if (manager == null) return;
        Arena arena = manager.arena();
        TeamColor team = arena.team(player.getUniqueId());
        if (team == null) return;
        if (name.equals("Sharpened Swords") && !arena.sharpness(team) && pay(player, Material.DIAMOND, GameRules.sharpnessCost())) {
            arena.sharpness(team, true);
            for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) enchantSwords(member);
        } else if (name.startsWith("Reinforced Armor") && arena.protection(team) < 4) {
            int level = arena.protection(team);
            if (pay(player, Material.DIAMOND, GameRules.protectionCost(level))) {
                arena.protection(team, level + 1);
                for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) manager.equipArmor(member, team);
            }
        } else if (name.startsWith("Iron Forge") && arena.forgeLevel(team) < 4) {
            int level = arena.forgeLevel(team);
            if (pay(player, Material.DIAMOND, GameRules.forgeUpgradeCost(level))) arena.forgeLevel(team, level + 1);
        } else if (name.startsWith("Maniac Miner") && arena.hasteLevel(team) < 2) {
            int level = arena.hasteLevel(team);
            if (pay(player, Material.DIAMOND, GameRules.hasteCost(level))) {
                arena.hasteLevel(team, level + 1);
                for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) manager.applyHaste(member, team);
            }
        } else if (name.equals("Heal Pool") && !arena.healPool(team) && pay(player, Material.DIAMOND, GameRules.healPoolCost())) {
            arena.healPool(team, true);
        } else if (name.startsWith("Cushioned Boots") && arena.cushionedBootsLevel(team) < 2) {
            int level = arena.cushionedBootsLevel(team);
            if (pay(player, Material.DIAMOND, GameRules.cushionedBootsCost(level))) {
                arena.cushionedBootsLevel(team, level + 1);
                for (Player member : Bukkit.getOnlinePlayers()) if (team == arena.team(member.getUniqueId())) manager.equipArmor(member, team);
            }
        } else if (buyTrap(player, arena, team, name)) {
            /* queued */
        }
        openUpgrades(player);
    }

    private boolean buyTrap(Player player, Arena arena, TeamColor team, String name) {
        Arena.TrapType type = null;
        if (name.equals("Blindness Trap")) type = Arena.TrapType.BLINDNESS;
        else if (name.equals("Counter-Offensive Trap")) type = Arena.TrapType.COUNTER_OFFENSIVE;
        else if (name.equals("Miner Fatigue Trap")) type = Arena.TrapType.MINER_FATIGUE;
        else if (name.equals("Reveal Trap")) type = Arena.TrapType.REVEAL;
        if (type == null) return false;
        if (arena.traps(team).size() >= GameRules.TRAP_QUEUE_MAX) {
            player.sendMessage(ChatColor.RED + "Your traps queue is full!");
            Sounds.cannotAfford(player);
            return true;
        }
        int cost = GameRules.trapDiamondCost(arena.traps(team).size());
        if (pay(player, Material.DIAMOND, cost)) arena.enqueueTrap(team, type);
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
        return Items.named(new ItemStack(Material.ARMOR_STAND),
            (npc.location() == null ? ChatColor.YELLOW : ChatColor.GREEN) + "Set " + type.displayName() + " NPC",
            npc.location() == null ? ChatColor.RED + "Not placed" : ChatColor.GREEN + "Placed as " + appearance(npc),
            ChatColor.GRAY + "Click to place/relocate at you",
            ChatColor.GRAY + "Shift-right-click the placed NPC to edit its look");
    }

    private static String appearance(LobbySettings.NpcSettings npc) {
        if (npc.human()) {
            return "Fake Player" + (npc.skin() == null ? "" : " (" + npc.skin() + ")") + (npc.cape() ? " +cape" : "");
        }
        return (npc.baby() ? "Baby " : "Adult ") + npc.entityType().name();
    }

    private static ItemStack setupItem(Material material, String name, boolean set) { return Items.named(new ItemStack(material), (set ? ChatColor.GREEN : ChatColor.YELLOW) + name, status(set)); }
    private static String status(boolean set) { return set ? ChatColor.GREEN + "Set" : ChatColor.RED + "Missing"; }

    private static int quickBuyIndex(int rawSlot) {
        for (int i = 0; i < GameRules.QUICK_BUY_SLOTS.length; i++) if (GameRules.QUICK_BUY_SLOTS[i] == rawSlot) return i;
        return -1;
    }

    private static boolean isFavoriteOffer(String name) {
        return ShopCatalog.offer(name) != null || name.equals("Shears") || name.equals("Permanent Shears")
            || name.endsWith(" Pickaxe") || name.endsWith(" Axe");
    }

    private static String favoriteKey(String name) {
        if (name.equals("Permanent Shears")) return "Shears";
        if (name.endsWith(" Pickaxe")) return "Wooden Pickaxe";
        if (name.endsWith(" Axe")) return "Wooden Axe";
        return name;
    }

    private static ItemStack appendLore(ItemStack item, String line) {
        ItemStack copy = item.clone();
        if (copy.getItemMeta() == null) return copy;
        org.bukkit.inventory.meta.ItemMeta meta = copy.getItemMeta();
        List<String> lore = meta.getLore() == null ? new ArrayList<String>() : new ArrayList<String>(meta.getLore());
        lore.add(line);
        meta.setLore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private static void addAssignHints(Inventory inventory) {
        for (int slot = 18; slot <= 44; slot++) {
            ItemStack item = inventory.getItem(slot);
            String name = Items.name(item);
            if (item == null || name.isEmpty() || name.equals(" ")) continue;
            inventory.setItem(slot, appendLore(item, ChatColor.AQUA + "Sneak Click to add to Quick Buy!"));
        }
    }

    private String hypixelApiKey() {
        try {
            String environment = System.getenv("BEDLAM_HYPIXEL_API_KEY");
            if (environment != null && !environment.trim().isEmpty()) return environment.trim();
        } catch (SecurityException ignored) { }
        return plugin.getConfig().getString("hypixel-api-key", "").trim();
    }

    private void importHypixelQuickBuy(final Player player) {
        final String apiKey = hypixelApiKey();
        if (apiKey.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Set BEDLAM_HYPIXEL_API_KEY or hypixel-api-key in config.yml, then reload.");
            Sounds.cannotAfford(player);
            return;
        }
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Loading your Quick Buy from Hypixel...");
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL("https://api.hypixel.net/v2/player?uuid=" + uuid.toString().replace("-", ""));
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("API-Key", apiKey);
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(7000);
                    int status = connection.getResponseCode();
                    InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                    String body = readUtf8(input);
                    if (status != 200) throw new IllegalStateException("Hypixel API returned HTTP " + status + ".");
                    JsonObject root = new JsonParser().parse(body).getAsJsonObject();
                    JsonElement playerElement = root.get("player");
                    if (playerElement == null || playerElement.isJsonNull()) throw new IllegalStateException("Hypixel could not find this player.");
                    JsonObject stats = playerElement.getAsJsonObject().getAsJsonObject("stats");
                    JsonObject bedwars = stats == null ? null : stats.getAsJsonObject("Bedwars");
                    JsonElement favorites = bedwars == null ? null : bedwars.get("favourites_2");
                    if (favorites == null || favorites.isJsonNull()) throw new IllegalStateException("This Hypixel account has no saved Bed Wars Quick Buy layout.");
                    final String[] imported = ShopCatalog.parseHypixelFavorites(favorites.getAsString());
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override public void run() {
                            Player online = Bukkit.getPlayer(uuid);
                            if (online == null) return;
                            plugin.stats().setFavorites(uuid, imported);
                            plugin.stats().save();
                            favoritePendingItem.remove(uuid);
                            favoriteAssignSlot.remove(uuid);
                            online.sendMessage(ChatColor.GREEN + "Imported your Hypixel Quick Buy layout.");
                            Sounds.levelUp(online);
                            openShopCategory(online, "Quick Buy");
                        }
                    });
                } catch (final Exception exception) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override public void run() {
                            Player online = Bukkit.getPlayer(uuid);
                            if (online != null) online.sendMessage(ChatColor.RED + "Quick Buy import failed: " + exception.getMessage());
                        }
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static String readUtf8(InputStream input) throws java.io.IOException {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        } finally {
            reader.close();
        }
        return text.toString();
    }

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

    public boolean guiBusy(Player player) {
        return guiBusy.contains(player.getUniqueId()) || ChestGuis.isPendingOpen(player);
    }

    public void beginGuiClick(Player player) { guiBusy.add(player.getUniqueId()); }

    public void endGuiClick(Player player) { guiBusy.remove(player.getUniqueId()); }

    private static Inventory chest(int size, String title) {
        return ChestGuis.create(size, title);
    }

    private void openGui(Player player, Inventory inventory) {
        ChestGuis.open(plugin, player, inventory);
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
        private final GameMode previousGameMode;
        private ArenaDraft(ArenaSettings settings, boolean newWorld, GameMode previousGameMode) {
            this.settings = settings;
            this.newWorld = newWorld;
            this.previousGameMode = previousGameMode;
        }
    }

    private static final class BorderSnapshot {
        private final double centerX;
        private final double centerZ;
        private final double size;
        private final int warningDistance;
        private final double damageAmount;

        private BorderSnapshot(double centerX, double centerZ, double size, int warningDistance, double damageAmount) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.size = size;
            this.warningDistance = warningDistance;
            this.damageAmount = damageAmount;
        }
    }
}
