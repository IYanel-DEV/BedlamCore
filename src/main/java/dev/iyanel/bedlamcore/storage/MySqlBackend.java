package dev.iyanel.bedlamcore.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

/** MySQL via a HikariCP pool (survives idle disconnects; supports a second server sharing the DB). */
public final class MySqlBackend extends SqlBackend {
    private final HikariDataSource pool;

    public MySqlBackend(JavaPlugin plugin) throws SQLException {
        super(plugin, plugin.getConfig().getString("storage.mysql.table-prefix", "bedlam_"));
        ConfigurationSection cfg = StatsBackend.section(plugin, "mysql");
        String host = cfg.getString("host", "localhost");
        int port = cfg.getInt("port", 3306);
        String database = cfg.getString("database", "bedlamcore");

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        hikari.setUsername(cfg.getString("user", "root"));
        hikari.setPassword(cfg.getString("password", ""));
        hikari.setMaximumPoolSize(Math.max(1, cfg.getInt("pool-size", 4)));
        hikari.setPoolName("BedlamCore-MySQL");
        this.pool = new HikariDataSource(hikari);
        ensureSchema();
    }

    @Override protected Connection connection() throws SQLException { return pool.getConnection(); }

    @Override public String name() { return "mysql"; }

    @Override
    protected String upsertSql() {
        return "INSERT INTO " + table + " (" + columnList() + ") VALUES (" + placeholders() + ") "
            + "ON DUPLICATE KEY UPDATE " + updateAssignments("VALUES(%s)");
    }

    @Override
    public void close() {
        if (pool != null && !pool.isClosed()) pool.close();
    }
}
