package desk.service;

import desk.db.Repository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Ajustes configurables (con valores por defecto razonables). */
public final class Config {
    public static final String INTERVIEWS_KEY = "interviews_dir";
    public static final String VIDEOS_KEY = "videos_dir";
    public static final String PAY_ANCHOR_KEY = "pay_anchor";

    private Config() {}

    public static Path interviewsDir(Repository repo) {
        Path fallback = Path.of(System.getProperty("user.home"), "Documents", "Interviewer Specialist");
        return Path.of(repo.setting(INTERVIEWS_KEY, fallback.toString()));
    }

    public static Path videosDir(Repository repo) {
        Path fallback = Path.of(System.getProperty("user.home"), "Videos");
        return Path.of(repo.setting(VIDEOS_KEY, fallback.toString()));
    }

    /** Primer día de cualquier periodo de pago (los periodos son de 14 días). */
    public static Optional<LocalDate> payAnchor(Repository repo) {
        String value = repo.setting(PAY_ANCHOR_KEY, "");
        if (value.isBlank()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
