package dev.iyanel.bedlamcore;

import dev.iyanel.bedlamcore.arena.ArenaManager;
import dev.iyanel.bedlamcore.arena.ArenaRepository;
import dev.iyanel.bedlamcore.arena.ArenaSettings;
import dev.iyanel.bedlamcore.command.BedlamCommand;
import dev.iyanel.bedlamcore.game.GameListener;
import dev.iyanel.bedlamcore.gui.GuiController;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedlamCore extends JavaPlugin {
    private ArenaRepository repository;
    private ArenaSettings settings;
    private ArenaManager arenaManager;
    private GuiController gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        repository = new ArenaRepository(this);
        settings = repository.load();
        arenaManager = new ArenaManager(this, settings);
        gui = new GuiController(this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        PluginCommand command = getCommand("bedlam");
        if (command != null) command.setExecutor(new BedlamCommand(this));
        getLogger().info("BedlamCore enabled on " + getServer().getVersion());
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) arenaManager.shutdown();
        if (repository != null && settings != null) repository.save(settings);
    }

    public ArenaSettings settings() { return settings; }
    public ArenaManager arenaManager() { return arenaManager; }
    public GuiController gui() { return gui; }

    public void saveSettings() {
        repository.save(settings);
    }

    public void reloadBedlam() {
        arenaManager.shutdown();
        reloadConfig();
        settings = repository.load();
        arenaManager = new ArenaManager(this, settings);
    }
}
