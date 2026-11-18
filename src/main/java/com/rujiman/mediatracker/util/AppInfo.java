package com.rujiman.mediatracker.util;

/**
 * Datos de la versión de MediaVerse. Al publicar una versión nueva:
 *   1. Subir VERSION aquí (y en el pom.xml).
 *   2. Crear en GitHub una Release con la etiqueta "v" + VERSION
 *      (p. ej. v1.2.0) y subir como archivos MediaVerse.jar y
 *      MediaVerse-portable.zip (ver BUILD_EXE.md).
 * Las apps instaladas detectan la Release y ofrecen actualizarse solas.
 */
public final class AppInfo {

    private AppInfo() {}

    public static final String VERSION = "1.2.0";

    /** Repositorio de GitHub donde se publican las Releases. */
    public static final String GITHUB_REPO = "rujiman/MediaVerse";

    /** Archivo de la Release que usa el actualizador (solo la app, ~45 MB). */
    public static final String JAR_ASSET = "MediaVerse.jar";

    /** Archivo de la Release para instalaciones nuevas (app + Java incluido). */
    public static final String ZIP_ASSET = "MediaVerse-portable.zip";

    /**
     * Si las notas de una Release contienen esta marca, la actualización
     * NO se puede hacer solo con el .jar (p. ej. cambió la versión de Java
     * o JavaFX) y la app pide descargar el zip completo.
     */
    public static final String FULL_INSTALL_MARK = "[instalación completa]";
}
