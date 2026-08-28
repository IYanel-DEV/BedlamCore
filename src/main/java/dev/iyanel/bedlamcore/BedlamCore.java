package dev.iyanel.bedlamcore;

import dev.iyanel.bedlamcore.arena.ArenaRepository;
import dev.iyanel.bedlamcore.arena.WaitingTemplateService;
import dev.iyanel.bedlamcore.command.BedlamCommand;
import dev.iyanel.bedlamcore.compat.EntityVisibility;
import dev.iyanel.bedlamcore.cosmetics.CosmeticsService;
import dev.iyanel.bedlamcore.game.ChestSoundListener;
import dev.iyanel.bedlamcore.game.GameListener;
import dev.iyanel.bedlamcore.game.GameService;
import dev.iyanel.bedlamcore.game.NpcSoundListener;
import dev.iyanel.bedlamcore.game.PearlListener;
import dev.iyanel.bedlamcore.game.NetworkViewService;
import dev.iyanel.bedlamcore.game.SidebarService;
import dev.iyanel.bedlamcore.game.StatsStore;
import dev.iyanel.bedlamcore.gui.GuiController;
import dev.iyanel.bedlamcore.leaderboard.LeaderboardService;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.lobby.LobbySettings;
import dev.iyanel.bedlamcore.party.BedlamPartyApi;
import dev.iyanel.bedlamcore.party.PartyService;
import dev.iyanel.bedlamcore.command.PartyCommand;
import dev.iyanel.bedlamcore.world.GameWorlds;
import dev.iyanel.bedlamcore.world.MapTemplates;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedlamCore extends JavaPlugin {
    private ArenaRepository repository;
    private WaitingTemplateService waitingTemplates;
    private LobbySettings lobby;
    private GameService games;
    private GameWorlds worlds;
    private MapTemplates templates;
    private LobbyNpcService npcs;
    private NetworkViewService views;
    private SidebarService sidebars;
    private StatsStore stats;
    private LeaderboardService leaderboards;
    private CosmeticsService cosmetics;
    private GuiController gui;
    private GameListener listener;
    private PartyService party;
    private dev.iyanel.bedlamcore.config.BedlamSettings settings;
    /** dev.iyanel.bedlamcore.compat.PapiExpansion when PlaceholderAPI is present; else null (Object avoids class-load without PAPI). */
    private Object papiExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new dev.iyanel.bedlamcore.config.BedlamSettings(this); // loads game.yml/generators.yml, applies GameRules
        repository = new ArenaRepository(this);
        waitingTemplates = new WaitingTemplateService(this);
        stats = new StatsStore(this);
        leaderboards = new LeaderboardService(this, stats); // before LobbyNpcService: the board reads cached rankings
        cosmetics = new CosmeticsService(this);
        lobby = repository.loadLobby();
        worlds = new GameWorlds(this);
        templates = new MapTemplates(this);
        npcs = new LobbyNpcService(this);
        party = new PartyService(this); // before GameService: party-aware quickJoin reads through it
        games = new GameService(this, repository.loadArenas());
        views = new NetworkViewService(this);
        gui = new GuiController(this);
        sidebars = new SidebarService(this);
        listener = new GameListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(npcs, this);
        new NpcSoundListener(this);
        getServer().getPluginManager().registerEvents(new PearlListener(this), this);
        getServer().getPluginManager().registerEvents(new ChestSoundListener(this), this);
        PluginCommand command = getCommand("bedlam");
        if (command != null) command.setExecutor(new BedlamCommand(this));
        PluginCommand leave = getCommand("leave");
        if (leave != null) leave.setExecutor(new BedlamCommand(this));
        PluginCommand rejoin = getCommand("rejoin");
        if (rejoin != null) rejoin.setExecutor(new BedlamCommand(this));
        PartyCommand partyCommand = new PartyCommand(this);
        PluginCommand partyCmd = getCommand("party");
        if (partyCmd != null) { partyCmd.setExecutor(partyCommand); partyCmd.setTabCompleter(partyCommand); }
        PluginCommand partyChat = getCommand("pc");
        if (partyChat != null) partyChat.setExecutor(partyCommand);
        dev.iyanel.bedlamcore.command.LeaderboardCommand leaderboardCommand = new dev.iyanel.bedlamcore.command.LeaderboardCommand(this);
        PluginCommand leaderboard = getCommand("leaderboard");
        if (leaderboard != null) { leaderboard.setExecutor(leaderboardCommand); leaderboard.setTabCompleter(leaderboardCommand); }
        npcs.respawnAll();
        // Register %bedlamcore_*% placeholders only when PlaceholderAPI is installed (soft dependency).
        // Cast runs only inside this guard, so PapiExpansion is never class-loaded without PAPI present.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            dev.iyanel.bedlamcore.compat.PapiExpansion expansion = new dev.iyanel.bedlamcore.compat.PapiExpansion(this);
            expansion.register();
            papiExpansion = expansion;
            getLogger().info("PlaceholderAPI detected: %bedlamcore_*% placeholders registered.");
        }
        // No JVM shutdown hook: onDisable() already saves while worlds are loaded. A second save from a
        // shutdown-hook thread runs after (or races) world unload, so Locations.encode() sees null worlds
        // and overwrites arenas.yml with null spawns/beds/gens — wiping every arena setup on restart.
        getLogger().info("BedlamCore " + getDescription().getVersion() + " enabled on " + getServer().getVersion()
            + " | Java " + System.getProperty("java.version")
            + " | visibility: " + EntityVisibility.compatibilityMode()
            + " | NPC silence: " + NpcSoundListener.compatibilityMode()
            + " | NPCs: built-in entities");
    }

    @Override
    public void onDisable() {
        if (gui != null) try { gui.restoreAllSetupBorders(); } catch (Throwable ignored) { }
        if (npcs != null) try { npcs.removeAll(); } catch (Throwable ignored) { }
        // Save settings FIRST — worlds must still be loaded so Locations.encode() can read world names.
        if (repository != null && lobby != null && games != null) {
            try { saveSettings(); } catch (Throwable ignored) { }
        }
        // Unload arena worlds without save (clears win-dragon grief from memory; disk pristine untouched).
        try { if (games != null) games.shutdown(); } catch (Throwable ignored) { }
        if (party != null) try { party.shutdown(); } catch (Throwable ignored) { }
        if (leaderboards != null) try { leaderboards.shutdown(); } catch (Throwable ignored) { }
        if (papiExpansion != null) try { ((dev.iyanel.bedlamcore.compat.PapiExpansion) papiExpansion).unregister(); } catch (Throwable ignored) { }
        if (stats != null) try { stats.save(); stats.close(); } catch (Throwable ignored) { }
    }

    public LobbySettings lobby() { return lobby; }
    public WaitingTemplateService waitingTemplates() { return waitingTemplates; }
    public GameService games() { return games; }
    public GameWorlds worlds() { return worlds; }
    public MapTemplates templates() { return templates; }
    public ArenaRepository arenas() { return repository; }
    public LobbyNpcService npcs() { return npcs; }
    public NetworkViewService views() { return views; }
    public SidebarService sidebars() { return sidebars; }
    public StatsStore stats() { return stats; }
    public LeaderboardService leaderboards() { return leaderboards; }
    public CosmeticsService cosmetics() { return cosmetics; }
    public GuiController gui() { return gui; }
    public GameListener listener() { return listener; }
    /** Public party API for other plugins (create/inspect/disband). */
    public BedlamPartyApi party() { return party; }
    /** Internal party manager (queueing, chat, provider registration). */
    public PartyService partyService() { return party; }
    public dev.iyanel.bedlamcore.config.BedlamSettings settings() { return settings; }
    public boolean isAdmin(CommandSender sender) { return sender.isOp() || sender.hasPermission("bedlam.admin"); }

    public void applyLobby(LobbySettings value) {
        lobby = value;
        saveSettings();
        if (lobby.spawn() != null) worlds.lockAlwaysDay(lobby.spawn().getWorld());
    }
    public void saveSettings() { repository.save(lobby, games.settings()); }

    public void reloadBedlam() {
        npcs.removeAll();
        games.shutdown();
        reloadConfig();
        settings.reload(); // re-read game.yml/generators.yml + re-apply GameRules
        if (party != null) party.reload(); // re-select provider + reschedule invite expiry
        if (leaderboards != null) leaderboards.reload(); // re-read tunables + recompute rankings
        cosmetics.reload();
        lobby = repository.loadLobby();
        games = new GameService(this, repository.loadArenas());
        npcs.respawnAll();
        views.updateAll();
        if (lobby.spawn() != null) worlds.lockAlwaysDay(lobby.spawn().getWorld());
    }
}
