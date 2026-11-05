package desk.db;

import desk.model.Company;
import desk.model.VideoSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Todo el acceso a datos vive aquí. */
public final class Repository {
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private static final String SESSION_SELECT =
            "SELECT s.*, c.name AS company_name FROM session s "
                    + "JOIN company c ON c.id = s.company_id AND c.hidden = 0 ";

    private final Database db;

    public Repository(Database db) {
        this.db = db;
    }

    // ---------- ajustes ----------

    public String setting(String key, String fallback) {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return fallback;
                String value = rs.getString(1);
                return value == null ? fallback : value;
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public void putSetting(String key, String value) {
        update("INSERT INTO settings(key, value) VALUES(?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value", ps -> {
            ps.setString(1, key);
            ps.setString(2, value);
        });
    }

    // ---------- empresas ----------

    /** Empresas visibles, las más nuevas primero. */
    public List<Company> companies() {
        return queryCompanies("SELECT * FROM company WHERE hidden = 0 ORDER BY added_at DESC, name");
    }

    /** Incluye las eliminadas por el usuario (para que la sincronización no las reviva). */
    public List<Company> allCompanies() {
        return queryCompanies("SELECT * FROM company ORDER BY added_at DESC, name");
    }

    /** Crea o actualiza una empresa por nombre. */
    public long upsertCompany(String name, String folder, String link, String job,
                              String resume, LocalDateTime addedAt) {
        String sql = """
            INSERT INTO company(name, folder, meeting_link, job_description, resume_path, added_at)
            VALUES(?, ?, ?, ?, ?, ?)
            ON CONFLICT(name) DO UPDATE SET
              folder          = COALESCE(excluded.folder, company.folder),
              meeting_link    = COALESCE(excluded.meeting_link, company.meeting_link),
              job_description = COALESCE(excluded.job_description, company.job_description),
              resume_path     = COALESCE(excluded.resume_path, company.resume_path)
            """;
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, folder);
                ps.setString(3, link);
                ps.setString(4, job);
                ps.setString(5, resume);
                ps.setString(6, addedAt.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM company WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    /** El usuario la eliminó: deja de verse y la sincronización no la vuelve a traer. */
    public void hideCompany(long companyId) {
        update("UPDATE company SET hidden = 1 WHERE id = ?", ps -> ps.setLong(1, companyId));
    }

    /** Borrado real (solo para limpiar empresas cuya carpeta ya no existe). */
    public void deleteCompany(long companyId) {
        update("DELETE FROM company WHERE id = ?", ps -> ps.setLong(1, companyId));
    }

    // ---------- reuniones ----------

    /** Inserta el video si es nuevo. Si ya existía, solo completa la duración cuando estaba vacía. */
    public void upsertSession(long companyId, String videoPath, LocalDateTime recordedAt, int durationSeconds) {
        String sql = """
            INSERT INTO session(company_id, video_path, recorded_at, duration_seconds)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(video_path) DO UPDATE SET
              duration_seconds = CASE WHEN session.duration_seconds = 0
                                      THEN excluded.duration_seconds
                                      ELSE session.duration_seconds END
            """;
        update(sql, ps -> {
            ps.setLong(1, companyId);
            ps.setString(2, videoPath);
            ps.setString(3, recordedAt.toString());
            ps.setInt(4, durationSeconds);
        });
    }

    /** Reunión sin video, registrada a mano. */
    public void insertManualSession(long companyId, LocalDateTime recordedAt, int durationSeconds) {
        upsertSession(companyId, VideoSession.MANUAL_PREFIX + UUID.randomUUID(), recordedAt, durationSeconds);
    }

    public void updateSession(long id, LocalDateTime recordedAt, int durationSeconds, Double billedOverride) {
        update("UPDATE session SET recorded_at = ?, duration_seconds = ?, billed_override = ? WHERE id = ?", ps -> {
            ps.setString(1, recordedAt.toString());
            ps.setInt(2, durationSeconds);
            if (billedOverride == null) ps.setNull(3, Types.REAL);
            else ps.setDouble(3, billedOverride);
            ps.setLong(4, id);
        });
    }

    public void deleteSession(long id) {
        update("DELETE FROM session WHERE id = ?", ps -> ps.setLong(1, id));
    }

    /** Marca como pagadas o pendientes todas las reuniones con fecha en [from, toExclusive). */
    public void setPaidBetween(LocalDate from, LocalDate toExclusive, boolean paid) {
        update("UPDATE session SET paid = ? WHERE recorded_at >= ? AND recorded_at < ?", ps -> {
            ps.setInt(1, paid ? 1 : 0);
            ps.setString(2, from.atStartOfDay().toString());
            ps.setString(3, toExclusive.atStartOfDay().toString());
        });
    }

    public List<VideoSession> sessionsOfCompany(long companyId) {
        return querySessions(SESSION_SELECT + "WHERE s.company_id = ? ORDER BY s.recorded_at DESC",
                ps -> ps.setLong(1, companyId));
    }

    /** Reuniones con fecha en [from, toExclusive). */
    public List<VideoSession> sessionsBetween(LocalDate from, LocalDate toExclusive) {
        return querySessions(
                SESSION_SELECT + "WHERE s.recorded_at >= ? AND s.recorded_at < ? ORDER BY s.recorded_at DESC",
                ps -> {
                    ps.setString(1, from.atStartOfDay().toString());
                    ps.setString(2, toExclusive.atStartOfDay().toString());
                });
    }

    /** Fecha de la primera reunión registrada, o null si no hay ninguna. */
    public LocalDate firstSessionDate() {
        try (Connection c = db.open();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT MIN(recorded_at) FROM session")) {
            if (!rs.next() || rs.getString(1) == null) return null;
            return LocalDateTime.parse(rs.getString(1)).toLocalDate();
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    // ---------- helpers ----------

    private void update(String sql, Binder binder) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    private List<Company> queryCompanies(String sql) {
        try (Connection c = db.open();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            List<Company> out = new ArrayList<>();
            while (rs.next()) out.add(company(rs));
            return out;
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    private List<VideoSession> querySessions(String sql, Binder binder) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<VideoSession> out = new ArrayList<>();
                while (rs.next()) out.add(session(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    private static Company company(ResultSet rs) throws SQLException {
        return new Company(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("folder"),
                rs.getString("meeting_link"),
                rs.getString("job_description"),
                rs.getString("resume_path"),
                LocalDateTime.parse(rs.getString("added_at")),
                rs.getInt("hidden") == 1);
    }

    private static VideoSession session(ResultSet rs) throws SQLException {
        double override = rs.getDouble("billed_override");
        Double billed = rs.wasNull() ? null : override;
        return new VideoSession(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("company_name"),
                rs.getString("video_path"),
                LocalDateTime.parse(rs.getString("recorded_at")),
                rs.getInt("duration_seconds"),
                billed,
                rs.getInt("paid") == 1);
    }
}
