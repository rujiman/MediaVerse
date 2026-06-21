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
}