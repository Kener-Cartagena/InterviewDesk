package desk.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Lee las carpetas de entrevistas y de videos. Solo lee, nunca modifica archivos. */
public final class FolderScanner {
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "mov", "m4v", "mkv", "webm", "avi");
    private static final Set<String> DURATION_READABLE = Set.of("mp4", "mov", "m4v");

    public record CompanyFolder(String name, Path path, String link, String jobDescription,
                                Path resume, LocalDateTime created) {}

    public record VideoFile(String companyFolder, Path path, LocalDateTime recordedAt, int durationSeconds) {}

    private FolderScanner() {}

    // ---------- carpetas de entrevistas ----------

    public static List<CompanyFolder> companies(Path root) throws IOException {
        List<CompanyFolder> out = new ArrayList<>();
        for (Path dir : subfolders(root)) out.add(readCompany(dir));
        return out;
    }

    private static CompanyFolder readCompany(Path dir) throws IOException {
        String link = null;
        String job = null;
        Path resume = null;
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path f : files) {
            String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".txt")) {
                if (isMeetingFile(name)) {
                    if (link == null) link = extractLink(readText(f));
                } else if (job == null) {
                    String text = readText(f).strip();
                    if (!text.isEmpty()) job = text;
                }
            } else if (name.endsWith(".pdf") && resume == null) {
                resume = f;
            }
        }
        LocalDateTime created = fileTime(dir, true);
        return new CompanyFolder(dir.getFileName().toString(), dir, link, job, resume, created);
    }

    private static boolean isMeetingFile(String lowerName) {
        return lowerName.contains("meeting") || lowerName.contains("reunion")
                || lowerName.contains("reunión") || lowerName.contains("enlace") || lowerName.contains("link");
    }

    private static String extractLink(String text) {
        String clean = text.strip();
        Matcher m = URL.matcher(clean);
        String link = m.find() ? m.group() : clean;
        return link.isBlank() ? null : link;
    }

    // ---------- carpetas de videos ----------

    public static List<VideoFile> videos(Path root) throws IOException {
        List<VideoFile> out = new ArrayList<>();
        for (Path dir : subfolders(root)) {
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path f : files) {
                String ext = extension(f);
                if (!VIDEO_EXT.contains(ext)) continue;
                int seconds = DURATION_READABLE.contains(ext) ? Mp4Duration.seconds(f) : 0;
                out.add(new VideoFile(dir.getFileName().toString(), f, fileTime(f, false), seconds));
            }
        }
        return out;
    }

    // ---------- helpers ----------

    private static List<Path> subfolders(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(p -> Files.isDirectory(p))
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return !n.startsWith(".") && !n.startsWith("__");
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String extension(Path f) {
        String n = f.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Fecha de creación (carpetas) o de última modificación (videos: cuando terminó la grabación). */
    private static LocalDateTime fileTime(Path p, boolean creation) throws IOException {
        BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
        var t = creation ? a.creationTime() : a.lastModifiedTime();
        return LocalDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault()).withNano(0);
    }

    private static String readText(Path f) throws IOException {
        byte[] bytes = Files.readAllBytes(f);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("windows-1252"));
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
