package com.rujiman.mediatracker.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Copia de seguridad de todos los datos (Documentos/MediaVerse/userdata)
 * en un .zip, y restauración desde uno.
 *
 * - exportTo(zip): mete en el zip todo userdata menos el log.
 * - importFrom(zip): antes de nada guarda una copia automática de lo que
 *   hay ahora (userdata-antes-de-importar-FECHA.zip, junto a userdata) y
 *   después descomprime encima. Como los servicios guardan datos en
 *   memoria, tras importar hay que cerrar la app sin guardar nada más.
 */
public final class BackupService {

    private BackupService() {}

    private static final String LOG = "mediaverse.log";

    public static Path dataDir() {
        return Paths.get(AppPaths.ROOT);
    }

    /** Nombre sugerido: mediaverse-copia-2026-11-22.zip */
    public static String suggestedName() {
        return "mediaverse-copia-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".zip";
    }

    /** Guarda todo userdata en un zip. Devuelve cuántos archivos se han copiado. */
    public static int exportTo(Path zipFile) throws IOException {
        Path root = dataDir();
        if (!Files.isDirectory(root)) throw new IOException("No hay datos que copiar todavía.");
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile));
             Stream<Path> files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(p) || p.getFileName().toString().equals(LOG)) continue;
                String name = root.relativize(p).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(p, zip);
                zip.closeEntry();
                count++;
            }
        }
        return count;
    }

    /** ¿Parece una copia de MediaVerse? (tiene users.json en la raíz) */
    public static boolean looksLikeBackup(Path zipFile) {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            return zip.getEntry("users.json") != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Restaura desde un zip. Devuelve la ruta de la copia automática que se
     * hizo de los datos anteriores (por si hay que volver atrás).
     */
    public static Path importFrom(Path zipFile) throws IOException {
        Path root = dataDir();
        Files.createDirectories(root);
        Path safety = root.resolveSibling("userdata-antes-de-importar-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".zip");
        exportTo(safety);

        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Path target = root.resolve(e.getName()).normalize();
                if (!target.startsWith(root)) continue; // nada fuera de userdata
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return safety;
    }
}
