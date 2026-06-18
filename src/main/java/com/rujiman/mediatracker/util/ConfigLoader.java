package com.rujiman.mediatracker.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
 */
public class ConfigLoader {

    // Variables estáticas = se cargan UNA SOLA VEZ cuando la app inicia
    private static Properties properties;

    // Static initializer block = se ejecuta cuando se carga la clase
    static {
        properties = new Properties();
        try {
            // Lee el archivo .env desde la carpeta raíz del proyecto
            properties.load(Files.newInputStream(Paths.get(".env")));
            System.out.println("✅ Variables de entorno cargadas correctamente");
        } catch (IOException e) {
            // Si no existe .env, muestra error
            System.err.println("❌ ERROR: .env no encontrado en la raíz del proyecto");
            System.err.println("   Copia .env.example a .env y rellena con tus claves");
            e.printStackTrace();
        }
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
