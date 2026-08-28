package dev.iyanel.bedlamcore.storage;

import dev.iyanel.bedlamcore.game.GameRules;
import dev.iyanel.bedlamcore.game.StatsStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared JDBC machinery for the SQL backends. One row per UUID; the dialect-specific upsert clause and
 * connection source come from subclasses. {@code level} is always derived from xp on read (never stored).
 */
public abstract class SqlBackend implements StatsBackend {
    /** Column order shared by DDL, upsert and reads (uuid first = primary key). */
    protected static final String[] COLUMNS = {
        "uuid", "tokens", "xp", "kills", "wins", "beds", "games", "losses", "deaths",
        "final_kills", "final_deaths", "beds_lost", "winstreak", "best_winstreak", "last_seen",
        "mode_solo", "mode_doubles", "mode_trios", "mode_quads", "favorites", "cos_owned", "cos_equipped"
    };

    protected final JavaPlugin plugin;
    protected final String table;

    protected SqlBackend(JavaPlugin plugin, String tablePrefix) {
        this.plugin = plugin;
        this.table = (tablePrefix == null ? "bedlam_" : tablePrefix) + "stats";
    }

    /** A ready-to-use connection (pooled or direct). Caller closes it. */
    protected abstract Connection connection() throws SQLException;

    /** Full upsert statement for this dialect (18 bind params, in {@link #bind} order). */
    protected abstract String upsertSql();

    /** Column type for the epoch-millis last_seen (BIGINT everywhere we target). */
    protected String bigIntType() { return "BIGINT"; }

    /** "col1, col2, ..." for the insert clause. */
    protected final String columnList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COLUMNS.length; i++) { if (i > 0) sb.append(", "); sb.append(COLUMNS[i]); }
        return sb.toString();
    }

    /** "?, ?, ..." matching {@link #COLUMNS}. */
    protected final String placeholders() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COLUMNS.length; i++) { if (i > 0) sb.append(", "); sb.append('?'); }
        return sb.toString();
    }

    /** "col = <fmt(col)>, ..." for every non-uuid column, e.g. fmt "excluded.%s" or "VALUES(%s)". */
    protected final String updateAssignments(String valueRefFormat) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < COLUMNS.length; i++) { // skip uuid (PK)
            if (i > 1) sb.append(", ");
            sb.append(COLUMNS[i]).append(" = ").append(String.format(valueRefFormat, COLUMNS[i]));
        }
        return sb.toString();
    }

    /** Run DDL once. Call from the subclass constructor after the connection source is ready. */
    protected final void ensureSchema() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
            + "uuid CHAR(36) PRIMARY KEY, tokens INT, xp INT, kills INT, wins INT, beds INT, games INT, "
            + "losses INT, deaths INT, final_kills INT, final_deaths INT, beds_lost INT, winstreak INT, "
            + "best_winstreak INT, last_seen " + bigIntType() + ", "
            + "mode_solo TEXT, mode_doubles TEXT, mode_trios TEXT, mode_quads TEXT, "
            + "favorites TEXT, cos_owned TEXT, cos_equipped TEXT)";
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.executeUpdate(ddl);
        }
    }

    @Override
    public Map<UUID, StatsStore.Record> loadAll() {
        Map<UUID, StatsStore.Record> records = new LinkedHashMap<UUID, StatsStore.Record>();
        String sql = "SELECT * FROM " + table;
        try (Connection c = connection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    records.put(uuid, readRow(rs));
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (SQLException failure) {
            plugin.getLogger().severe("Stats load from " + name() + " failed: " + failure.getMessage());
        }
        return records;
    }

    @Override
    public void saveAll(Map<UUID, StatsStore.Record> records) throws SQLException {
        // ponytail: upserts the whole map per flush (no per-record dirty tracking). Add a dirty-key set
        // if the player table ever grows large enough for the 5s batch to lag.
        if (records.isEmpty()) return;
        try (Connection c = connection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(upsertSql())) {
                for (Map.Entry<UUID, StatsStore.Record> entry : records.entrySet()) {
                    bind(ps, entry.getKey(), entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(auto);
            }
        }
        if (plugin.getConfig().getBoolean("storage.debug", false)) {
            plugin.getLogger().info("[" + name() + "] flushed " + records.size() + " stat rows");
        }
    }

    private void bind(PreparedStatement ps, UUID uuid, StatsStore.Record r) throws SQLException {
        int i = 1;
        ps.setString(i++, uuid.toString());
        ps.setInt(i++, r.tokens);
        ps.setInt(i++, r.xp);
        ps.setInt(i++, r.kills);
        ps.setInt(i++, r.wins);
        ps.setInt(i++, r.beds);
        ps.setInt(i++, r.games);
        ps.setInt(i++, r.losses);
        ps.setInt(i++, r.deaths);
        ps.setInt(i++, r.finalKills);
        ps.setInt(i++, r.finalDeaths);
        ps.setInt(i++, r.bedsLost);
        ps.setInt(i++, r.winstreak);
        ps.setInt(i++, r.bestWinstreak);
        ps.setLong(i++, r.lastSeen);
        ps.setString(i++, mode(r.solo));
        ps.setString(i++, mode(r.doubles));
        ps.setString(i++, mode(r.trios));
        ps.setString(i++, mode(r.quads));
        ps.setString(i++, favorites(r.favorites));
        ps.setString(i++, joinOwned(r.cosmeticsOwned));
        ps.setString(i, joinEquipped(r.cosmeticsEquipped));
    }

    private StatsStore.Record readRow(ResultSet rs) throws SQLException {
        StatsStore.Record r = new StatsStore.Record();
        r.tokens = rs.getInt("tokens");
        r.xp = rs.getInt("xp");
        r.kills = rs.getInt("kills");
        r.wins = rs.getInt("wins");
        r.beds = rs.getInt("beds");
        r.games = rs.getInt("games");
        r.losses = rs.getInt("losses");
        r.deaths = rs.getInt("deaths");
        r.finalKills = rs.getInt("final_kills");
        r.finalDeaths = rs.getInt("final_deaths");
        r.bedsLost = rs.getInt("beds_lost");
        r.winstreak = rs.getInt("winstreak");
        r.bestWinstreak = rs.getInt("best_winstreak");
        r.lastSeen = rs.getLong("last_seen");
        r.level = GameRules.levelFromXp(r.xp);
        readMode(rs.getString("mode_solo"), r.solo);
        readMode(rs.getString("mode_doubles"), r.doubles);
        readMode(rs.getString("mode_trios"), r.trios);
        readMode(rs.getString("mode_quads"), r.quads);
        String fav = rs.getString("favorites");
        if (fav != null && !fav.isEmpty()) r.favorites = StatsStore.padFavorites(fav.split(",", -1));
        String owned = rs.getString("cos_owned");
        if (owned != null && !owned.isEmpty()) {
            for (String id : owned.split(",")) if (!id.isEmpty()) r.cosmeticsOwned.add(id);
        }
        String equipped = rs.getString("cos_equipped");
        if (equipped != null && !equipped.isEmpty()) {
            for (String pair : equipped.split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0) r.cosmeticsEquipped.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return r;
    }

    // --- CSV helpers. Shop keys / cosmetic ids / categories contain no comma, ';' or '='. ---

    private static String mode(StatsStore.ModeStats m) {
        return m.kills + "," + m.wins + "," + m.beds + "," + m.games + "," + m.losses + ","
            + m.deaths + "," + m.finalKills + "," + m.finalDeaths + "," + m.bedsLost;
    }

    private static void readMode(String csv, StatsStore.ModeStats m) {
        if (csv == null || csv.isEmpty()) return;
        String[] p = csv.split(",", -1);
        if (p.length < 9) return;
        m.kills = i(p[0]); m.wins = i(p[1]); m.beds = i(p[2]); m.games = i(p[3]); m.losses = i(p[4]);
        m.deaths = i(p[5]); m.finalKills = i(p[6]); m.finalDeaths = i(p[7]); m.bedsLost = i(p[8]);
    }

    private static int i(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; } }

    private static String favorites(String[] favs) {
        if (favs == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] padded = StatsStore.padFavorites(favs);
        for (int i = 0; i < padded.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(padded[i] == null ? "" : padded[i]);
        }
        return sb.toString();
    }

    private static String joinOwned(java.util.Set<String> owned) {
        StringBuilder sb = new StringBuilder();
        for (String id : owned) { if (sb.length() > 0) sb.append(','); sb.append(id); }
        return sb.toString();
    }

    private static String joinEquipped(Map<String, String> equipped) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : equipped.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
