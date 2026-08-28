package dev.iyanel.bedlamcore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * File-backed SQLite. Opens a short-lived connection per operation (flush is every 5s and access is
 * main-thread only, so a pool buys nothing here).
 */
public final class SqliteBackend extends SqlBackend {
    private final String url;

    public SqliteBackend(JavaPlugin plugin) throws SQLException, ClassNotFoundException {
        super(plugin, "");
        Class.forName("org.sqlite.JDBC");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite.file", "stats.db"));
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        ensureSchema();
    }

    @Override protected Connection connection() throws SQLException { return DriverManager.getConnection(url); }

    @Override public String name() { return "sqlite"; }

    @Override
    protected String upsertSql() {
        return "INSERT INTO " + table + " (" + columnList() + ") VALUES (" + placeholders() + ") "
            + "ON CONFLICT(uuid) DO UPDATE SET " + updateAssignments("excluded.%s");
    }
}
