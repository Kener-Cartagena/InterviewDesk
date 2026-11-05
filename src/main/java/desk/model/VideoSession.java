package desk.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

/** Una reunión: un video grabado o un registro manual. */
public record VideoSession(
        long id,
        long companyId,
        String companyName,
        String videoPath,
        LocalDateTime recordedAt,
        int durationSeconds,
        Double billedOverride,
        boolean paid) {

    public static final String MANUAL_PREFIX = "manual:";

    /** Horas pagadas: el valor manual si existe, si no la regla automática. */
    public double paidHours() {
        return durationSeconds / 3600.0;
    }

    public boolean manual() {
        return videoPath.startsWith(MANUAL_PREFIX);
    }

    public String fileName() {
        return manual() ? "Registro manual" : Path.of(videoPath).getFileName().toString();
    }
}
