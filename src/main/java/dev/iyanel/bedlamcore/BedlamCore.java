package dev.iyanel.bedlamcore;

import dev.iyanel.bedlamcore.arena.ArenaRepository;
import dev.iyanel.bedlamcore.arena.WaitingTemplateService;
import dev.iyanel.bedlamcore.command.BedlamCommand;
import dev.iyanel.bedlamcore.game.GameListener;
import dev.iyanel.bedlamcore.game.GameService;
import dev.iyanel.bedlamcore.game.NetworkViewService;
import dev.iyanel.bedlamcore.game.SidebarService;
import dev.iyanel.bedlamcore.gui.GuiController;
import dev.iyanel.bedlamcore.lobby.LobbyNpcService;
import dev.iyanel.bedlamcore.lobby.LobbySettings;
import dev.iyanel.bedlamcore.world.GameWorlds;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedlamCore extends JavaPlugin {
    private ArenaRepository repository;
    private WaitingTemplateService waitingTemplates;
    private LobbySettings lobby;
    private GameService games;
    private GameWorlds worlds;
    private LobbyNpcService npcs;
    private NetworkViewService views;
    private SidebarService sidebars;
    private GuiController gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        repository = new ArenaRepository(this);
        waitingTemplates = new WaitingTemplateService(this);
        lobby = repository.loadLobby();
        worlds = new GameWorlds(this);
        games = new GameService(this, repository.loadArenas());
        views = new NetworkViewService(this);
        gui = new GuiController(this);
        npcs = new LobbyNpcService(this);
        sidebars = new SidebarService(this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        PluginCommand command = getCommand("bedlam");
        if (command != null) command.setExecutor(new BedlamCommand(this));
        PluginCommand leave = getCommand("leave");
        if (leave != null) leave.setExecutor(new BedlamCommand(this));
        npcs.respawnAll();
        getLogger().info("BedlamCore enabled on " + getServer().getVersion());
    }

    @Override
    public void onDisable() {
        if (npcs != null) npcs.removeAll();
        if (games != null) games.shutdown();
        if (repository != null && lobby != null && games != null) saveSettings();
    }

    public LobbySettings lobby() { return lobby; }
    public WaitingTemplateService waitingTemplates() { return waitingTemplates; }
    public GameService games() { return games; }
    public GameWorlds worlds() { return worlds; }
    public LobbyNpcService npcs() { return npcs; }
    public NetworkViewService views() { return views; }
    public SidebarService sidebars() { return sidebars; }
    public GuiController gui() { return gui; }
    public boolean isAdmin(CommandSender sender) { return sender.isOp() || sender.hasPermission("bedlam.admin"); }

    public void applyLobby(LobbySettings value) { lobby = value; saveSettings(); }
    public void saveSettings() { repository.save(lobby, games.settings()); }

    public void reloadBedlam() {
        npcs.removeAll();
        games.shutdown();
        reloadConfig();
        lobby = repository.loadLobby();
        games = new GameService(this, repository.loadArenas());
        npcs.respawnAll();
        views.updateAll();
    }
}
