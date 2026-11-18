package com.rujiman.mediatracker.util;

/**
 * Datos de la versión de MediaVerse. Al publicar una versión nueva:
 *   1. Subir VERSION aquí (y en el pom.xml).
 *   2. Hacer commit, crear la etiqueta "v" + VERSION (p. ej. v1.2.1) y
 *      subirla a GitHub. GitHub Actions compila Windows y Linux y publica
 *      la Release con todos los archivos (ver BUILD_EXE.md).
 * Las apps instaladas detectan la Release y ofrecen actualizarse solas.
 */
public final class AppInfo {

    private AppInfo() {}

    public static final String VERSION = "1.3.0";

    /** Repositorio de GitHub donde se publican las Releases. */
    public static final String GITHUB_REPO = "rujiman/MediaVerse";

    /** true si la app se está ejecutando en Linux. */
    public static final boolean LINUX = System.getProperty("os.name", "").toLowerCase().contains("linux");

    /** true si la app se está ejecutando en Windows. */
    public static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * Archivo de la Release que usa el actualizador (solo la app, ~15 MB).
     * Cada sistema tiene el suyo porque JavaFX lleva librerías nativas distintas.
     */
    public static final String JAR_ASSET = LINUX ? "MediaVerse-linux.jar" : "MediaVerse.jar";

    /** Archivo de la Release para instalaciones nuevas (app + Java incluido). */
    public static final String ZIP_ASSET = LINUX ? "MediaVerse-linux.tar.gz" : "MediaVerse-portable.zip";

    /**
     * Si las notas de una Release contienen esta marca, la actualización
     * NO se puede hacer solo con el .jar (p. ej. cambió la versión de Java
     * o JavaFX) y la app pide descargar el zip completo.
     */
    public static final String FULL_INSTALL_MARK = "[instalación completa]";
}
