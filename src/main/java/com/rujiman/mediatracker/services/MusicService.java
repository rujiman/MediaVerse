package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Servicio de música usando la API pública de Deezer.
 * No requiere API key ni autenticación para búsquedas de catálogo.
 *
 * Docs: https://developers.deezer.com/api
 */
public class MusicService {

    private static final String API_URL = "https://api.deezer.com";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    // ============================
    // BUSCAR CANCIONES (tracks)
    // ============================
    public List<MediaItem> searchTracks(String query) {

        String url = API_URL + "/search/track?q=" + encode(query) + "&limit=15";

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error Deezer (tracks): " + response);
            }

            String json = response.body().string();
            return parseTracks(json);

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ============================
    // BUSCAR ÁLBUMES
    // ============================
    public List<MediaItem> searchAlbums(String query) {

        String url = API_URL + "/search/album?q=" + encode(query) + "&limit=15";

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error Deezer (albums): " + response);
            }

            String json = response.body().string();
            return parseAlbums(json);

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ============================
    // BUSCAR AMBOS (tracks + albums)
    // ============================
    public List<MediaItem> search(String query) {
        List<MediaItem> results = new ArrayList<>();
        results.addAll(searchTracks(query));
        results.addAll(searchAlbums(query));
        return results;
    }

    /**
     * Vuelve a buscar una canción suelta por su título completo (tal como
     * se guarda en favoritos: "Canción - Artista") y devuelve una
     * previewUrl fresca si encuentra una coincidencia razonable.
     *
     * Por qué hace falta esto: la previewUrl que da Deezer es una URL
     * firmada con caducidad (parámetro hdnea/exp en la query string,
     * típico de protección de CDN), que deja de funcionar pasado un
     * tiempo (minutos/horas) aunque siga guardada en el JSON de
     * favoritos. Cuando el MediaPlayer falla al reproducir una preview
     * guardada, se llama aquí para conseguir una URL nueva, en vez de
     * fallar permanentemente.
     *
     * @return la previewUrl fresca, o null si no se encontró ninguna
     *         coincidencia válida.
     */
    public String refreshPreviewUrl(String fullTitle) {
        if (fullTitle == null || fullTitle.isBlank()) return null;

        List<MediaItem> tracks = searchTracks(fullTitle);

        // Coincidencia exacta primero (mismo título completo "Canción - Artista")
        for (MediaItem track : tracks) {
            if (fullTitle.equalsIgnoreCase(track.getTitle()) && track.getPreviewUrl() != null) {
                return track.getPreviewUrl();
            }
        }

        // Si no hay coincidencia exacta, el primer resultado con preview
        // disponible es la mejor aproximación razonable (la búsqueda ya
        // viene ordenada por relevancia de Deezer).
        for (MediaItem track : tracks) {
            if (track.getPreviewUrl() != null && !track.getPreviewUrl().isBlank()) {
                return track.getPreviewUrl();
            }
        }

        return null;
    }

    // ============================
    // PARSEAR TRACKS
    // ============================
    private List<MediaItem> parseTracks(String json) {

        List<MediaItem> results = new ArrayList<>();

        JsonObject root = gson.fromJson(json, JsonObject.class);
        if (root == null || !root.has("data")) return results;

        JsonArray data = root.getAsJsonArray("data");

        for (JsonElement el : data) {
            JsonObject obj = el.getAsJsonObject();

            MediaItem item = new MediaItem();
            item.setType(MediaType.MUSIC);

            // Título: "Cancion - Artista"
            String trackTitle = safeString(obj, "title");
            String artistName = obj.has("artist") && !obj.get("artist").isJsonNull()
                    ? safeString(obj.getAsJsonObject("artist"), "name")
                    : "";

            item.setTitle(trackTitle + (artistName.isEmpty() ? "" : " - " + artistName));
            item.setDescription("Canción de " + artistName);

            // Imagen (carátula del álbum al que pertenece)
            if (obj.has("album") && !obj.get("album").isJsonNull()) {
                JsonObject album = obj.getAsJsonObject("album");
                String cover = safeString(album, "cover_xl");
                if (cover.isEmpty()) cover = safeString(album, "cover_big");
                item.setImageUrl(cover);
            }

            // Duración -> la guardamos como "episodios" no aplica, usamos formato
            if (obj.has("duration") && !obj.get("duration").isJsonNull()) {
                int seconds = obj.get("duration").getAsInt();
                item.setFormat(formatDuration(seconds));
            }

            // Puntuación: Deezer no da score 0-100 público fiable -> usamos "rank" normalizado
            if (obj.has("rank") && !obj.get("rank").isJsonNull()) {
                long rank = obj.get("rank").getAsLong();
                item.setScore(normalizeRank(rank));
            }

            // URL externa
            item.setExternalUrl(safeString(obj, "link"));

            // Preview de 30s (mp3 público, sin autenticación), solo
            // disponible para canciones sueltas, no para álbumes completos
            String preview = safeString(obj, "preview");
            if (!preview.isBlank()) {
                item.setPreviewUrl(preview);
            }

            // Plataforma
            List<String> platforms = new ArrayList<>();
            platforms.add("Deezer");
            item.setPlatforms(platforms);

            results.add(item);
        }

        return results;
    }

    // ============================
    // PARSEAR ÁLBUMES
    // ============================
    private List<MediaItem> parseAlbums(String json) {

        List<MediaItem> results = new ArrayList<>();

        JsonObject root = gson.fromJson(json, JsonObject.class);
        if (root == null || !root.has("data")) return results;

        JsonArray data = root.getAsJsonArray("data");

        for (JsonElement el : data) {
            JsonObject obj = el.getAsJsonObject();

            MediaItem item = new MediaItem();
            item.setType(MediaType.MUSIC);

            String albumTitle = safeString(obj, "title");
            String artistName = obj.has("artist") && !obj.get("artist").isJsonNull()
                    ? safeString(obj.getAsJsonObject("artist"), "name")
                    : "";

            item.setTitle(albumTitle + (artistName.isEmpty() ? "" : " - " + artistName));
            item.setDescription("Álbum de " + artistName);

            // Imagen
            String cover = safeString(obj, "cover_xl");
            if (cover.isEmpty()) cover = safeString(obj, "cover_big");
            item.setImageUrl(cover);

            // Número de canciones -> reutilizamos "episodes"
            if (obj.has("nb_tracks") && !obj.get("nb_tracks").isJsonNull()) {
                item.setEpisodes(obj.get("nb_tracks").getAsInt());
            }

            // Formato fijo
            item.setFormat("ÁLBUM");

            // Puntuación basada en rank
            if (obj.has("record_type") && !obj.get("record_type").isJsonNull()) {
                // No siempre viene rank en álbumes; si existe lo usamos
            }
            if (obj.has("rank") && !obj.get("rank").isJsonNull()) {
                long rank = obj.get("rank").getAsLong();
                item.setScore(normalizeRank(rank));
            }

            // URL externa
            item.setExternalUrl(safeString(obj, "link"));

            List<String> platforms = new ArrayList<>();
            platforms.add("Deezer");
            item.setPlatforms(platforms);

            results.add(item);
        }

        return results;
    }

    // ============================
    // HELPERS
    // ============================

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String safeString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * El "rank" de Deezer es un número grande sin tope fijo documentado
     * (puede ir de miles a varios cientos de miles). Lo normalizamos
     * a una escala aproximada 0-100 solo para mostrar un indicador visual,
     * usando una escala logarítmica ya que el rank crece muy rápido.
     */
    private int normalizeRank(long rank) {
        if (rank <= 0) return 0;
        double normalized = Math.log10(rank) * 12; // ajuste empírico
        int score = (int) Math.min(100, Math.max(0, normalized));
        return score;
    }
}