package desk.service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Lee la duración de un MP4/MOV sin librerías externas (caja "moov" > "mvhd").
 * Devuelve 0 si no puede leerla; en ese caso la duración se escribe a mano.
 */
public final class Mp4Duration {
    private static final int MOOV = 0x6D6F6F76;
    private static final int MVHD = 0x6D766864;

    private Mp4Duration() {}

    public static int seconds(Path file) {
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long len = f.length();
            long pos = 0;
            while (pos + 8 <= len) {
                f.seek(pos);
                long size = f.readInt() & 0xFFFFFFFFL;
                int type = f.readInt();
                long header = 8;
                if (size == 1) {
                    size = f.readLong();
                    header = 16;
                } else if (size == 0) {
                    size = len - pos;
                }
                if (size < header) return 0;
                if (type == MOOV) return findMvhd(f, pos + header, pos + size);
                pos += size;
            }
        } catch (IOException ignored) {
            // archivo no legible o formato raro: se devuelve 0
        }
        return 0;
    }

    private static int findMvhd(RandomAccessFile f, long start, long end) throws IOException {
        long pos = start;
        while (pos + 8 <= end) {
            f.seek(pos);
            long size = f.readInt() & 0xFFFFFFFFL;
            int type = f.readInt();
            if (size < 8) return 0;
            if (type == MVHD) {
                int version = f.readUnsignedByte();
                f.skipBytes(3);
                long timescale;
                long duration;
                if (version == 1) {
                    f.skipBytes(16);
                    timescale = f.readInt() & 0xFFFFFFFFL;
                    duration = f.readLong();
                } else {
                    f.skipBytes(8);
                    timescale = f.readInt() & 0xFFFFFFFFL;
                    duration = f.readInt() & 0xFFFFFFFFL;
                }
                return timescale == 0 ? 0 : (int) Math.round((double) duration / timescale);
            }
            pos += size;
        }
        return 0;
    }
}
