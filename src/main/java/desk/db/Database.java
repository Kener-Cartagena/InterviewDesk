package desk.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Abre conexiones a SQLite, crea el esquema y migra bases de datos de versiones anteriores. */
public final class Database {
    private final String url;

    public Database(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new DbException(e);
        }
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS company (
                  id              INTEGER PRIMARY KEY AUTOINCREMENT,
                  name            TEXT NOT NULL UNIQUE,
                  folder          TEXT,
                  meeting_link    TEXT,
                  job_description TEXT,
                  resume_path     TEXT,
                  added_at        TEXT NOT NULL
                )""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS session (
                  id               INTEGER PRIMARY KEY AUTOINCREMENT,
                  company_id       INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
                  video_path       TEXT NOT NULL UNIQUE,
                  recorded_at      TEXT NOT NULL,
                  duration_seconds INTEGER NOT NULL DEFAULT 0,
                  billed_override  REAL
                )""");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_session_date ON session(recorded_at)");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settings (
                  key   TEXT PRIMARY KEY,
                  value TEXT
                )""");
            addColumnIfMissing(c, "company", "hidden", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "session", "paid", "INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    private static void addColumnIfMissing(Connection c, String table, String column, String definition)
            throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return;
            }
        }
        try (Statement s = c.createStatement()) {
            s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
