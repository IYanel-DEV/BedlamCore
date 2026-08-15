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
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.lobby.LobbySettings;
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
    private CosmeticsService cosmetics;
    private GuiController gui;
    private GameListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        repository = new ArenaRepository(this);
        waitingTemplates = new WaitingTemplateService(this);
        stats = new StatsStore(this);
        cosmetics = new CosmeticsService(this);
        lobby = repository.loadLobby();
        worlds = new GameWorlds(this);
        templates = new MapTemplates(this);
        npcs = new LobbyNpcService(this);
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
        npcs.respawnAll();
        boolean citizens = getServer().getPluginManager().isPluginEnabled("Citizens");
        getLogger().info("BedlamCore " + getDescription().getVersion() + " enabled on " + getServer().getVersion()
            + " | Java " + System.getProperty("java.version")
            + " | visibility: " + EntityVisibility.compatibilityMode()
            + " | NPC silence: " + NpcSoundListener.compatibilityMode()
            + " | Citizens: " + (citizens ? "enabled" : "fallback entities"));
    }

    @Override
    public void onDisable() {
        if (gui != null) gui.restoreAllSetupBorders();
        if (npcs != null) npcs.removeAll();
        // Unload arena worlds without save (clears win-dragon grief from memory; disk pristine untouched).
        if (games != null) games.shutdown();
        if (stats != null) stats.save();
        if (repository != null && lobby != null && games != null) saveSettings();
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
    public CosmeticsService cosmetics() { return cosmetics; }
    public GuiController gui() { return gui; }
    public GameListener listener() { return listener; }
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
        cosmetics.reload();
        lobby = repository.loadLobby();
        games = new GameService(this, repository.loadArenas());
        npcs.respawnAll();
        views.updateAll();
        if (lobby.spawn() != null) worlds.lockAlwaysDay(lobby.spawn().getWorld());
    }
}
