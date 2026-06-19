package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.util.ConfigLoader;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de videojuegos usando la API IGDB (Twitch).
 *
 * IGDB es la base de datos más completa de videojuegos con información de:
 * - Título, descripción, puntuaciones
 * - Portadas, screenshots
 * - Plataformas disponibles
 * - Géneros, fechas de lanzamiento
 *
 * Requiere: IGDB_CLIENT_ID y IGDB_CLIENT_SECRET en .env
 * Docs: https://api-docs.igdb.com/
 */
public class GameService {

    private static final String API_URL = "https://api.igdb.com/v4";
    private static final String IMAGE_BASE = "https://images.igdb.com/igdb/image/upload/t_cover_big/";
    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    private static String cachedAccessToken = null;
    private static long tokenExpireTime = 0;

    private String getClientId() {
        return ConfigLoader.getIGDBClientId();
    }

    private String getClientSecret() {
        return ConfigLoader.getIGDBClientSecret();
    }

    // ============================
    // OBTENER ACCESS TOKEN
    // ============================
    private String getAccessToken() throws IOException {
        // Si el token está en caché y no ha expirado, usarlo
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return cachedAccessToken;
        }

        System.out.println("🔐 Obteniendo Access Token de Twitch...");

        String clientId = getClientId();
        String clientSecret = getClientSecret();

        RequestBody body = new FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "client_credentials")
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Sin detalles";
                System.err.println("❌ Error al obtener token: " + response.code());
                System.err.println("   Body: " + errorBody);
                throw new IOException("No se pudo obtener token de Twitch: " + response.code());
            }

            String json = response.body().string();
            JsonObject tokenResponse = gson.fromJson(json, JsonObject.class);

            if (tokenResponse.has("access_token")) {
                String accessToken = tokenResponse.get("access_token").getAsString();
                int expiresIn = tokenResponse.has("expires_in")
                        ? tokenResponse.get("expires_in").getAsInt()
                        : 3600;

                // Cachear el token con un margen de 60 segundos antes de expirar
                cachedAccessToken = accessToken;
                tokenExpireTime = System.currentTimeMillis() + (expiresIn * 1000) - 60000;

                System.out.println("✅ Access Token obtenido correctamente");
                return accessToken;
            } else {
                throw new IOException("Token response sin access_token");
            }
        }
    }

    // ============================
    // BUSCAR JUEGOS
    // ============================
    public List<MediaItem> search(String query) {

        String igdbQuery = String.format(
                "fields id, name, summary, cover.image_id, rating, platforms.name, release_dates.human, genres.name, websites.url; " +
                        "search \"%s\"; " +
                        "limit 15; " +
                        "where rating != null;",
                query
        );

        RequestBody body = RequestBody.create(
                igdbQuery,
                okhttp3.MediaType.get("text/plain")
        );

        System.out.println("🎮 Buscando en IGDB: " + query);

        try {
            String accessToken = getAccessToken();
            String clientId = getClientId();

            Request request = new Request.Builder()
                    .url(API_URL + "/games")
                    .post(body)
                    .header("Client-ID", clientId)
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {

                System.out.println("   Response code: " + response.code());

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Sin detalles";
                    System.err.println("❌ Error IGDB " + response.code() + ": " + response.message());
                    System.err.println("   Body: " + errorBody);

                    // Si es 401, limpiar el token en caché para intentar nuevo
                    if (response.code() == 401) {
                        cachedAccessToken = null;
                        System.err.println("⚠️  Token inválido. Limpié el caché, intenta de nuevo.");
                    }

                    throw new IOException("Error IGDB: " + response.code());
                }

                String json = response.body().string();
                System.out.println("✅ Respuesta de IGDB recibida: " + json.length() + " bytes");
                return parseGames(json);

            }

        } catch (Exception e) {
            System.err.println("❌ Error al buscar en IGDB: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ============================
    // PARSEAR RESULTADOS
    // ============================
    private List<MediaItem> parseGames(String json) {

        List<MediaItem> results = new ArrayList<>();

        try {
            JsonArray games = gson.fromJson(json, JsonArray.class);

            if (games == null || games.size() == 0) {
                System.out.println("⚠️ IGDB retornó 0 resultados");
                return results;
            }

            System.out.println("📊 IGDB retornó " + games.size() + " juegos");

            for (JsonElement el : games) {
                JsonObject obj = el.getAsJsonObject();

                MediaItem item = new MediaItem();
                item.setType(MediaType.GAME);

                // Título
                item.setTitle(safeString(obj, "name"));

                // Descripción
                item.setDescription(safeString(obj, "summary"));

                // Imagen (cover)
                if (obj.has("cover") && !obj.get("cover").isJsonNull()) {
                    JsonObject cover = obj.getAsJsonObject("cover");
                    if (cover.has("image_id") && !cover.get("image_id").isJsonNull()) {
                        String imageId = cover.get("image_id").getAsString();
                        item.setImageUrl(IMAGE_BASE + imageId + ".jpg");
                    }
                }

                // Año (desde release_dates)
                if (obj.has("release_dates") && !obj.get("release_dates").isJsonNull()) {
                    JsonArray releaseDates = obj.getAsJsonArray("release_dates");
                    if (releaseDates.size() > 0) {
                        try {
                            long timestamp = releaseDates.get(0).getAsLong() * 1000L;
                            java.util.Calendar cal = java.util.Calendar.getInstance();
                            cal.setTimeInMillis(timestamp);
                            item.setYear(cal.get(java.util.Calendar.YEAR));
                        } catch (Exception ignored) {}
                    }
                }

                // Puntuación (0-100)
                if (obj.has("rating") && !obj.get("rating").isJsonNull()) {
                    item.setScore((int) obj.get("rating").getAsDouble());
                }

                // Géneros (IGDB devuelve IDs, no nombres directos)
                if (obj.has("genres") && !obj.get("genres").isJsonNull()) {
                    JsonArray genres = obj.getAsJsonArray("genres");
                    List<String> genreList = new ArrayList<>();
                    for (JsonElement g : genres) {
                        try {
                            // Si es un objeto, trata de extraer el nombre
                            if (g.isJsonObject()) {
                                String name = g.getAsJsonObject().get("name").getAsString();
                                genreList.add(name);
                            } else if (g.isJsonPrimitive()) {
                                // Si es un ID numérico, simplemente agregalo
                                genreList.add("Genre " + g.getAsInt());
                            }
                        } catch (Exception e) {
                            // Ignorar si no se puede parsear
                        }
                    }
                    if (!genreList.isEmpty()) {
                        item.setGenres(genreList);
                    }
                }

                // Plataformas (también pueden ser objetos)
                if (obj.has("platforms") && !obj.get("platforms").isJsonNull()) {
                    JsonArray platforms = obj.getAsJsonArray("platforms");
                    List<String> platformList = new ArrayList<>();
                    for (JsonElement p : platforms) {
                        try {
                            // Si es un objeto, trata de extraer el nombre
                            if (p.isJsonObject()) {
                                String name = p.getAsJsonObject().get("name").getAsString();
                                platformList.add(name);
                            } else if (p.isJsonPrimitive()) {
                                // Si es un ID numérico, simplemente agregalo
                                platformList.add("Platform " + p.getAsInt());
                            }
                        } catch (Exception e) {
                            // Ignorar si no se puede parsear
                        }
                    }
                    if (!platformList.isEmpty()) {
                        item.setPlatforms(platformList);
                    }
                }

                // URL externa (IGDB)
                item.setExternalUrl("https://www.igdb.com/games/" +
                        item.getTitle().toLowerCase().replace(" ", "-"));

                results.add(item);
            }

        } catch (Exception e) {
            System.err.println("❌ Error al parsear IGDB: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    // ============================
    // HELPERS
    // ============================

    private String safeString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }
}