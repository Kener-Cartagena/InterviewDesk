package desk.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Descomprime el .zip que te pasan dentro de la carpeta de entrevistas. */
public final class ZipImporter {
    private ZipImporter() {}

    /** @return cantidad de archivos extraídos */
    public static int extract(Path zip, Path destination) throws IOException {
        Path base = destination.toAbsolutePath().normalize();
        Files.createDirectories(base);
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path target = base.resolve(entry.getName()).normalize();
                if (!target.startsWith(base)) {
                    throw new IOException("El ZIP tiene una ruta insegura: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        }
        return count;
    }
}
