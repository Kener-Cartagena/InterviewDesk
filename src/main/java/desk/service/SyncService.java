package desk.service;

import desk.db.Repository;
import desk.model.Company;
import desk.model.VideoSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Une las carpetas del disco con la base de datos. Se puede ejecutar las veces que quieras. */
public final class SyncService {
    public record Result(int companies, int videos, int createdFromVideos, int removed) {}

    private final Repository repo;

    public SyncService(Repository repo) {
        this.repo = repo;
    }

    public Result sync(Path interviewsRoot, Path videosRoot) throws IOException {
        Set<String> scannedInterviewFolders = new HashSet<>();
        int companyCount = 0;

        // 1. Escanear y actualizar todas las carpetas de entrevistas (documentos/txt)
        for (var cf : FolderScanner.companies(interviewsRoot)) {
            // Aseguramos que si la empresa ya existía por nombre, actualice su ruta de carpeta y datos correctamente
            repo.upsertCompany(cf.name(), cf.path().toString(), cf.link(), cf.jobDescription(),
                    cf.resume() == null ? null : cf.resume().toString(), cf.created());
            scannedInterviewFolders.add(cf.path().toString());
            companyCount++;
        }

        // Limpiar empresas de entrevistas cuya carpeta física ya no exista en disco
        int removed = 0;
        if (Files.isDirectory(interviewsRoot)) {
            for (Company c : repo.allCompanies()) {
                if (c.folder() != null && !c.hidden() && !scannedInterviewFolders.contains(c.folder())) {
                    repo.deleteCompany(c.id());
                    removed++;
                }
            }
        }

        List<Company> known = repo.allCompanies();
        int videoCount = 0;
        int created = 0;
        Set<String> scannedVideoPaths = new HashSet<>();

        // 2. Escanear y revisar profundamente la carpeta de videos
        for (var video : FolderScanner.videos(videosRoot)) {
            Company match = match(known, video.companyFolder());
            long companyId;
            if (match == null) {
                // Si la carpeta de video existe pero no tenía carpeta de entrevistas previa, la creamos
                companyId = repo.upsertCompany(video.companyFolder(), null, null, null, null, LocalDateTime.now());
                known = repo.allCompanies();
                created++;
            } else if (match.hidden()) {
                continue; // La empresa fue ocultada por el usuario: sus videos se ignoran
            } else {
                companyId = match.id();
            }

            // Inserta o actualiza la sesión del video
            repo.upsertSession(companyId, video.path().toString(), video.recordedAt(), video.durationSeconds());
            scannedVideoPaths.add(video.path().toString());
            videoCount++;
        }

        // 3. Limpieza de sesiones huérfanas (archivos renombrados o eliminados en la carpeta de videos)
        for (Company c : known) {
            List<VideoSession> sessions = repo.sessionsOfCompany(c.id());
            for (VideoSession session : sessions) {
                if (!session.videoPath().startsWith(VideoSession.MANUAL_PREFIX)) {
                    if (!scannedVideoPaths.contains(session.videoPath())) {
                        repo.deleteSession(session.id());
                    }
                }
            }
        }

        return new Result(companyCount, videoCount, created, removed);
    }

    /**
     * Compara por inclusión del nombre normalizado (sin guiones, espacios ni tildes).
     */
    private static Company match(List<Company> companies, String videoFolder) {
        String target = normalize(videoFolder);
        if (target.isEmpty()) return null;
        Company best = null;
        int bestLen = Integer.MAX_VALUE;
        for (Company c : companies) {
            String n = normalize(c.name());
            if (n.isEmpty()) continue;
            if (n.equals(target)) return c;
            if ((n.contains(target) || target.contains(n)) && n.length() < bestLen) {
                best = c;
                bestLen = n.length();
            }
        }
        return best;
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}