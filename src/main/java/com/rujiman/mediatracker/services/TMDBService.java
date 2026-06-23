package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.util.ConfigLoader;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TMDBService {

    private static final String API_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/original";

    /**
     * Mapa fijo de TMDB genre_id -> nombre en español. Los IDs son
     * estables en TMDB (no cambian), así que este mapa es válido
     * indefinidamente. Se usa para convertir los genre_ids que vienen
     * en las respuestas de search/tv y search/movie (que solo traen IDs,
     * no nombres) a nombres legibles.
     *
     * Fuente: https://www.themoviedb.org/settings/languages
     * (aunque TMDB no documenta estos IDs públicamente, son conocidos
     * por la comunidad y estables desde hace años).
     */
    private static final java.util.Map<Integer, String> GENRE_MAP = new java.util.HashMap<>();
    static {
        // Series y películas comparten los mismos IDs de género en TMDB
        GENRE_MAP.put(10759, "Acción & Aventura");
        GENRE_MAP.put(16, "Animación");
        GENRE_MAP.put(35, "Comedia");
        GENRE_MAP.put(80, "Crimen");
        GENRE_MAP.put(99, "Documental");
        GENRE_MAP.put(18, "Drama");
        GENRE_MAP.put(10751, "Familia");
        GENRE_MAP.put(10762, "Infantil");
        GENRE_MAP.put(9648, "Misterio");
        GENRE_MAP.put(10763, "Noticias");
        GENRE_MAP.put(10764, "Reality");
        GENRE_MAP.put(10765, "Ciencia Ficción");
        GENRE_MAP.put(10766, "Telenovela");
        GENRE_MAP.put(10767, "Talk Show");
        GENRE_MAP.put(10768, "Guerra & Política");
        GENRE_MAP.put(37, "Western");
        GENRE_MAP.put(27, "Horror");
        GENRE_MAP.put(10402, "Música");
        GENRE_MAP.put(10404, "Película de TV");
        GENRE_MAP.put(10405, "Película de Acción");
        GENRE_MAP.put(14, "Fantasía");
        GENRE_MAP.put(36, "Historia");
        GENRE_MAP.put(10749, "Romance");
        GENRE_MAP.put(878, "Ciencia Ficción");
        GENRE_MAP.put(10770, "Película de TV");
        GENRE_MAP.put(53, "Thriller");
    }

    // Timeouts cortos: si TMDB no responde rápido, mejor fallar pronto y
    // seguir con el resto de resultados que quedarse esperando minutos
    // (con los valores por defecto de OkHttp, 10s, una búsqueda con muchos
    // resultados y varias llamadas fallidas en cadena podía sentirse
    // como si la app se hubiera quedado colgada para siempre).
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    private String getKey() {
        return ConfigLoader.getTMDBKey();
    }

    // ============================
    // BUSCAR SERIES
    // ============================
    public List<MediaItem> searchSeries(String query) {

        String url = API_URL + "/search/tv?api_key=" + getKey() + "&query=" + query.replace(" ", "%20");

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error TMDB: " + response);
            }

            String json = response.body().string();
            return parseSeries(json);

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ============================
    // BUSCAR PELÍCULAS
    // ============================
    public List<MediaItem> searchMovies(String query) {

        String url = API_URL + "/search/movie?api_key=" + getKey() + "&query=" + query.replace(" ", "%20");

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error TMDB: " + response);
            }

            String json = response.body().string();
            return parseMovies(json);

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ============================
    // PARSEAR SERIES
    // ============================
    private List<MediaItem> parseSeries(String json) {

        List<MediaItem> results = new ArrayList<>();

        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray items = root.getAsJsonArray("results");

        for (JsonElement el : items) {
            JsonObject obj = el.getAsJsonObject();

            MediaItem item = new MediaItem();
            item.setType(MediaType.SERIES);

            item.setTitle(obj.get("name").getAsString());
            item.setDescription(obj.get("overview").getAsString());

            // Imagen
            if (!obj.get("poster_path").isJsonNull()) {
                item.setImageUrl(IMAGE_BASE + obj.get("poster_path").getAsString());
            }

            // Año
            if (!obj.get("first_air_date").isJsonNull()) {
                String date = obj.get("first_air_date").getAsString();
                if (!date.isEmpty()) {
                    item.setYear(Integer.parseInt(date.substring(0, 4)));
                }
            }

            // Puntuación (0–100)
            if (!obj.get("vote_average").isJsonNull()) {
                item.setScore((int) (obj.get("vote_average").getAsDouble() * 10));
            }

            // Géneros: TMDB devuelve solo IDs (genre_ids), hay que convertirlos a nombres
            if (obj.has("genre_ids") && obj.get("genre_ids").isJsonArray()) {
                List<String> genres = new ArrayList<>();
                for (JsonElement genreIdEl : obj.getAsJsonArray("genre_ids")) {
                    Integer genreId = genreIdEl.getAsInt();
                    String genreName = GENRE_MAP.getOrDefault(genreId, null);
                    if (genreName != null) {
                        genres.add(genreName);
                    }
                }
                if (!genres.isEmpty()) {
                    item.setGenres(genres);
                }
            }

            // URL externa
            item.setExternalUrl("https://www.themoviedb.org/tv/" + obj.get("id").getAsInt());
            item.setTmdbId(obj.get("id").getAsInt());

            // Plataformas disponibles (requiere el endpoint de detalle).
            // El número de episodios YA NO se pide aquí: se consultaba con
            // una llamada extra por cada serie, lo que multiplicaba las
            // peticiones HTTP en búsquedas con muchos resultados y hacía
            // que la búsqueda pareciera colgada cuando TMDB iba lento.
            // Ahora se pide solo al abrir el detalle de una serie concreta
            // (ver DetailViewController + getEpisodeCount()).
            int seriesId = obj.get("id").getAsInt();
            item.setPlatforms(getWatchProviders(seriesId, false));

            results.add(item);
        }

        return results;
    }

    // ============================
    // PARSEAR PELÍCULAS
    // ============================
    private List<MediaItem> parseMovies(String json) {

        List<MediaItem> results = new ArrayList<>();

        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray items = root.getAsJsonArray("results");

        for (JsonElement el : items) {
            JsonObject obj = el.getAsJsonObject();

            MediaItem item = new MediaItem();
            item.setType(MediaType.MOVIE);

            item.setTitle(obj.get("title").getAsString());
            item.setDescription(obj.get("overview").getAsString());

            // Imagen
            if (!obj.get("poster_path").isJsonNull()) {
                item.setImageUrl(IMAGE_BASE + obj.get("poster_path").getAsString());
            }

            // Año
            if (!obj.get("release_date").isJsonNull()) {
                String date = obj.get("release_date").getAsString();
                if (!date.isEmpty()) {
                    item.setYear(Integer.parseInt(date.substring(0, 4)));
                }
            }

            // Puntuación (0–100)
            if (!obj.get("vote_average").isJsonNull()) {
                item.setScore((int) (obj.get("vote_average").getAsDouble() * 10));
            }

            // Géneros: TMDB devuelve solo IDs (genre_ids), hay que convertirlos a nombres
            if (obj.has("genre_ids") && obj.get("genre_ids").isJsonArray()) {
                List<String> genres = new ArrayList<>();
                for (JsonElement genreIdEl : obj.getAsJsonArray("genre_ids")) {
                    Integer genreId = genreIdEl.getAsInt();
                    String genreName = GENRE_MAP.getOrDefault(genreId, null);
                    if (genreName != null) {
                        genres.add(genreName);
                    }
                }
                if (!genres.isEmpty()) {
                    item.setGenres(genres);
                }
            }

            // URL externa
            item.setExternalUrl("https://www.themoviedb.org/movie/" + obj.get("id").getAsInt());
            item.setTmdbId(obj.get("id").getAsInt());

            // Plataformas disponibles
            item.setPlatforms(getWatchProviders(obj.get("id").getAsInt(), true));

            results.add(item);
        }

        return results;
    }
    /**
     * Obtiene el número total de episodios de una serie consultando
     * el endpoint de detalle de TMDB (/tv/{id}), ya que /search/tv
     * no incluye ese dato.
     */
    public Integer getEpisodeCount(int tmdbId) {
        String url = API_URL + "/tv/" + tmdbId + "?api_key=" + getKey();

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error TMDB detalle: " + response);
            }

            String json = response.body().string();
            JsonObject obj = gson.fromJson(json, JsonObject.class);

            if (obj.has("number_of_episodes") && !obj.get("number_of_episodes").isJsonNull()) {
                return obj.get("number_of_episodes").getAsInt();
            }

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Obtiene la clave de YouTube ("key") del tráiler oficial de una
     * película o serie, consultando el endpoint /videos de TMDB. Esa
     * clave es la que se usa para construir la URL de embed
     * (https://www.youtube.com/embed/{key}) que se carga en el WebView,
     * sin necesidad de abrir el navegador externo.
     *
     * Prioriza videos de tipo "Trailer" en español o inglés; si no hay
     * ninguno marcado como tráiler, devuelve el primer video disponible
     * como mejor opción razonable.
     */
    public String getTrailerKey(int tmdbId, boolean isMovie) {
        String url = API_URL + (isMovie ?
                "/movie/" + tmdbId + "/videos?api_key=" + getKey() :
                "/tv/" + tmdbId + "/videos?api_key=" + getKey()
        );

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error TMDB Videos: " + response);
            }

            String json = response.body().string();
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (!root.has("results")) return null;
            JsonArray videos = root.getAsJsonArray("results");

            String fallbackKey = null;

            for (JsonElement el : videos) {
                JsonObject video = el.getAsJsonObject();

                String site = safeString(video, "site");
                if (!"YouTube".equalsIgnoreCase(site)) continue; // solo nos sirve YouTube para el embed

                String key = safeString(video, "key");
                if (fallbackKey == null) fallbackKey = key;

                String type = safeString(video, "type");
                if ("Trailer".equalsIgnoreCase(type)) {
                    return key; // el primer tráiler real que encontremos, devolución inmediata
                }
            }

            return fallbackKey; // no había ningún "Trailer" explícito, pero sí algún video de YouTube

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String safeString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    public List<String> getWatchProviders(int tmdbId, boolean isMovie) {

        String url = API_URL + (isMovie ?
                "/movie/" + tmdbId + "/watch/providers?api_key=" + getKey() :
                "/tv/" + tmdbId + "/watch/providers?api_key=" + getKey()
        );

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error TMDB Providers: " + response);
            }

            String json = response.body().string();
            JsonObject root = gson.fromJson(json, JsonObject.class);

            JsonObject results = root.getAsJsonObject("results");

            // España (ES)
            if (!results.has("ES")) return new ArrayList<>();

            JsonObject es = results.getAsJsonObject("ES");

            if (!es.has("flatrate")) return new ArrayList<>();

            JsonArray providers = es.getAsJsonArray("flatrate");

            List<String> list = new ArrayList<>();
            for (JsonElement el : providers) {
                list.add(el.getAsJsonObject().get("provider_name").getAsString());
            }

            return list;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Pide hasta 10 recomendaciones para una película o serie, usando el
     * endpoint /recommendations de TMDB (basado en qué otros usuarios
     * reales de TMDB vieron/valoraron juntos, NO el endpoint /similar,
     * que solo compara género/keywords y da resultados de peor calidad).
     * Se llama bajo demanda al abrir el detalle, igual que tráiler/
     * episodios, para no ralentizar la búsqueda.
     */
    public List<MediaItem> getRecommendations(int tmdbId, boolean isMovie) {
        String url = API_URL + (isMovie ?
                "/movie/" + tmdbId + "/recommendations?api_key=" + getKey() :
                "/tv/" + tmdbId + "/recommendations?api_key=" + getKey()
        );

        Request request = new Request.Builder().url(url).build();
        List<MediaItem> results = new ArrayList<>();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error TMDB recommendations: " + response);
            }

            String json = response.body().string();
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (!root.has("results")) return results;
            JsonArray items = root.getAsJsonArray("results");

            int limit = Math.min(items.size(), 10);
            for (int i = 0; i < limit; i++) {
                JsonObject obj = items.get(i).getAsJsonObject();

                MediaItem item = new MediaItem();
                item.setType(isMovie ? MediaType.MOVIE : MediaType.SERIES);
                item.setTmdbId(obj.get("id").getAsInt());

                // El campo de título cambia de nombre entre película/serie
                String titleField = isMovie ? "title" : "name";
                if (obj.has(titleField) && !obj.get(titleField).isJsonNull()) {
                    item.setTitle(obj.get(titleField).getAsString());
                }

                if (obj.has("overview") && !obj.get("overview").isJsonNull()) {
                    item.setDescription(obj.get("overview").getAsString());
                }

                if (obj.has("poster_path") && !obj.get("poster_path").isJsonNull()) {
                    item.setImageUrl(IMAGE_BASE + obj.get("poster_path").getAsString());
                }

                String dateField = isMovie ? "release_date" : "first_air_date";
                if (obj.has(dateField) && !obj.get(dateField).isJsonNull()) {
                    String date = obj.get(dateField).getAsString();
                    if (!date.isEmpty()) {
                        item.setYear(Integer.parseInt(date.substring(0, 4)));
                    }
                }

                if (obj.has("vote_average") && !obj.get("vote_average").isJsonNull()) {
                    item.setScore((int) (obj.get("vote_average").getAsDouble() * 10));
                }

                item.setExternalUrl(
                        "https://www.themoviedb.org/" + (isMovie ? "movie/" : "tv/") + obj.get("id").getAsInt()
                );

                results.add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }
}