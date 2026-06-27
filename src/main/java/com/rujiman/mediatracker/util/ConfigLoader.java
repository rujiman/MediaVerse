package com.rujiman.mediatracker.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Carga variables de entorno desde .env
 *
 * EXPLICACIÓN:
 * - Lee el archivo .env
 * - Almacena en memoria las claves API
 * - Las proporciona a través de métodos estáticos
 *
 * USO:
 * String apiKey = ConfigLoader.getTMDBKey();
 *
 * DÓNDE BUSCA EL .env
 * --------------------
 * Antes se buscaba con una ruta relativa fija (".env"), lo cual
 * funcionaba bien ejecutando con `mvn javafx:run` desde la raíz del
 * proyecto (que es el directorio de trabajo en ese caso), pero se
 * rompía al empaquetar con jpackage: el directorio de trabajo del .exe
 * empaquetado no es la carpeta de instalación, así que el .env nunca
 * se encontraba aunque el usuario lo hubiera puesto justo al lado del
 * ejecutable.
 *
 * Ahora se calcula la ruta del .env de forma dinámica, a partir de
 * dónde está físicamente el .jar/.exe en ejecución (no del directorio
 * de trabajo), así que el usuario simplemente coloca su .env en la
 * misma carpeta donde está el ejecutable, sea cual sea esa carpeta en
 * su sistema.
 *
 * NOTA: en este modo, si el usuario reinstala la app sobrescribiendo
 * esa carpeta, sí podría perder su .env (a diferencia de userdata/,
 * que vive en su carpeta personal). Conviene advertirlo en el
 * README/INSTALL: "no borres tu .env al actualizar la app".
 */
public class ConfigLoader {

    // Variables estáticas = se cargan UNA SOLA VEZ cuando la app inicia
    private static Properties properties;

    // Static initializer block = se ejecuta cuando se carga la clase
    static {
        properties = new Properties();
        Path envPath = resolveEnvPath();

        try {
            properties.load(Files.newInputStream(envPath));
            System.out.println("✅ Variables de entorno cargadas correctamente desde: " + envPath);
        } catch (IOException e) {
            // Si no existe .env, muestra error
            System.err.println("❌ ERROR: .env no encontrado en: " + envPath);
            System.err.println("   Copia .env.example a .env (en esa misma carpeta) y rellena con tus claves");
            e.printStackTrace();
        }
    }

    /**
     * Calcula la ruta donde debe estar el .env: la misma carpeta donde
     * vive el .jar/.exe que se está ejecutando ahora mismo.
     *
     * getCodeSource().getLocation() devuelve la URL de origen del
     * código (el .jar en producción, o la carpeta target/classes al
     * ejecutar desde el IDE/Maven), independientemente del directorio
     * de trabajo desde el que se haya lanzado el proceso.
     */
    private static Path resolveEnvPath() {
        try {
            File jarOrClassesLocation = new File(
                    ConfigLoader.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );

            // Si es un .jar, su padre es la carpeta donde vive (junto al .exe).
            // Si es una carpeta (target/classes, ejecutando desde el IDE),
            // subimos lo suficiente para llegar a la raíz del proyecto,
            // que es donde developers ya colocan su .env manualmente.
            File baseDir = jarOrClassesLocation.isFile()
                    ? jarOrClassesLocation.getParentFile()
                    : findProjectRootFromClasses(jarOrClassesLocation);

            return baseDir.toPath().resolve(".env");

        } catch (URISyntaxException e) {
            // Fallback defensivo: si por lo que sea no se puede resolver
            // la ubicación del jar, se mantiene el comportamiento
            // anterior (ruta relativa al directorio de trabajo) en vez
            // de romper el arranque de la app con una excepción.
            System.err.println("⚠️ No se pudo resolver la ubicación del .jar, usando ruta relativa como fallback");
            return java.nio.file.Paths.get(".env");
        }
    }

    /**
     * Cuando se ejecuta desde el IDE/Maven, la "ubicación del código"
     * es una carpeta tipo .../target/classes, no el .jar final. Subimos
     * dos niveles (classes -> target -> raíz del proyecto) para llegar
     * a donde developers ya colocan su .env durante el desarrollo.
     */
    private static File findProjectRootFromClasses(File classesDir) {
        File target = classesDir.getParentFile();      // .../target
        if (target != null && target.getParentFile() != null) {
            return target.getParentFile();              // raíz del proyecto
        }
        return classesDir; // fallback defensivo si la estructura es inesperada
    }

    /**
     * Método genérico para obtener cualquier variable de .env
     *
     * @param key Nombre de la variable (ej: "TMDB_API_KEY")
     * @return El valor de la variable, o null si no existe
     */
    public static String get(String key) {
        return properties.getProperty(key);
    }

    // ===== MÉTODOS ESPECÍFICOS PARA CADA API =====

    /**
     * @return API key de TheMovieDB
     */
    public static String getTMDBKey() {
        String key = get("TMDB_API_KEY");
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("TMDB_API_KEY no configurada en .env");
        }
        return key;
    }

    /**
     * @return Client ID de Spotify
     */
    public static String getSpotifyClientId() {
        String id = get("SPOTIFY_CLIENT_ID");
        if (id == null || id.isEmpty()) {
            throw new RuntimeException("SPOTIFY_CLIENT_ID no configurada en .env");
        }
        return id;
    }

    /**
     * @return Client Secret de Spotify
     */
    public static String getSpotifyClientSecret() {
        String secret = get("SPOTIFY_CLIENT_SECRET");
        if (secret == null || secret.isEmpty()) {
            throw new RuntimeException("SPOTIFY_CLIENT_SECRET no configurada en .env");
        }
        return secret;
    }

    /**
     * @return Client ID de IGDB
     */
    public static String getIGDBClientId() {
        String id = get("IGDB_CLIENT_ID");
        if (id == null || id.isEmpty()) {
            throw new RuntimeException("IGDB_CLIENT_ID no configurada en .env");
        }
        return id;
    }

    /**
     * @return Client Secret de IGDB
     */
    public static String getIGDBClientSecret() {
        String secret = get("IGDB_CLIENT_SECRET");
        if (secret == null || secret.isEmpty()) {
            throw new RuntimeException("IGDB_CLIENT_SECRET no configurada en .env");
        }
        return secret;
    }
}