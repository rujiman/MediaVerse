package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para guardar y cargar favoritos en JSON, separados por usuario
 * logueado (cada cuenta tiene su propio archivo favorites_<usuario>.json).
 */
public class FavoritesService {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static List<FavoriteItem> favoritesList = new ArrayList<>();
    private static boolean loaded = false;
    private static String loadedForUser = null; // qué usuario tiene cargado el caché actual

    /**
     * Devuelve el nombre del archivo de favoritos del usuario actualmente
     * logueado (AuthService.getCurrentUser()). Si no hay sesión, usa un
     * archivo genérico de respaldo.
     */
    private static String getFavoritesFile() {
        String user = AuthService.getCurrentUser();
        if (user == null || user.isBlank()) {
            return "favorites.json";
        }
        return "favorites_" + user + ".json";
    }

    /**
     * Si el usuario logueado ha cambiado desde la última carga
     * (por ejemplo, tras un logout/login con otra cuenta), invalida
     * el caché en memoria para no mezclar favoritos entre cuentas.
     */
    private static void ensureCorrectUserLoaded() {
        String currentUser = AuthService.getCurrentUser();
        if (loaded && java.util.Objects.equals(loadedForUser, currentUser)) {
            return; // ya está cargado el usuario correcto
        }
        favoritesList.clear();
        loaded = false;
        loadFavorites();
    }

    // ============================
    // CARGAR FAVORITOS
    // ============================
    public static void loadFavorites() {
        if (loaded) return;

        String file = getFavoritesFile();
        System.out.println("📖 Cargando favoritos de: " + file);

        try {
            if (!Files.exists(Paths.get(file))) {
                System.out.println("✅ No hay favoritos previos para este usuario");
                loaded = true;
                loadedForUser = AuthService.getCurrentUser();
                return;
            }

            String json = new String(Files.readAllBytes(Paths.get(file)));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root != null && root.has("favorites")) {
                JsonArray favArray = root.getAsJsonArray("favorites");

                for (JsonElement el : favArray) {
                    try {
                        JsonObject obj = el.getAsJsonObject();
                        FavoriteItem item = parseFavoriteItem(obj);
                        favoritesList.add(item);
                    } catch (Exception e) {
                        // Un solo favorito con datos corruptos/inesperados
                        // no debe impedir cargar el resto: se salta ESE
                        // item y se sigue con los demás, en vez de que
                        // toda la lista de favoritos quede vacía (lo que
                        // antes provocaba que Inicio y Favoritos dejaran
                        // de funcionar por completo).
                        System.err.println("⚠️ Favorito corrupto omitido: " + e.getMessage());
                    }
                }

                System.out.println("✅ Cargados " + favoritesList.size() + " favoritos");
            }

            loaded = true;
            loadedForUser = AuthService.getCurrentUser();

        } catch (IOException e) {
            System.err.println("❌ Error al cargar favoritos: " + e.getMessage());
            loaded = true;
            loadedForUser = AuthService.getCurrentUser();
        }
    }

    // ============================
    // AGREGAR FAVORITO
    // ============================
    public static void addFavorite(FavoriteItem item) {
        ensureCorrectUserLoaded();
        if (!favoritesList.contains(item)) {
            favoritesList.add(item);
            saveFavorites();
            System.out.println("⭐ Agregado a favoritos: " + item.getTitle());
        }
    }

    /**
     * Persiste cambios hechos sobre un FavoriteItem que ya está en la lista
     * en memoria (por ejemplo, tras rellenar su trailerKey bajo demanda).
     * addFavorite() no sirve para esto porque, al ser la misma instancia
     * que ya está en favoritesList, "ya existe" y no se vuelve a guardar
     * nada en disco aunque el objeto haya cambiado en memoria.
     */
    public static void updateFavorite(FavoriteItem item) {
        ensureCorrectUserLoaded();
        saveFavorites();
    }

    // ============================
    // ELIMINAR FAVORITO
    // ============================
    public static void removeFavorite(String itemId) {
        ensureCorrectUserLoaded();
        favoritesList.removeIf(fav -> fav.getId().equals(itemId));
        saveFavorites();
        System.out.println("❌ Eliminado de favoritos");
    }

    // ============================
    // OBTENER TODOS LOS FAVORITOS
    // ============================
    public static List<FavoriteItem> getFavorites() {
        ensureCorrectUserLoaded();
        return new ArrayList<>(favoritesList);
    }

    // ============================
    // OBTENER FAVORITOS POR TIPO
    // ============================
    public static List<FavoriteItem> getFavoritesByType(MediaType type) {
        ensureCorrectUserLoaded();
        return favoritesList.stream()
                .filter(fav -> fav.getType() == type)
                .toList();
    }

    // ============================
    // ACTUALIZAR ESTADO (VISTO/JUGADO) - item completo, sin episodios
    // ============================
    public static void toggleViewed(String itemId) {
        ensureCorrectUserLoaded();
        for (FavoriteItem fav : favoritesList) {
            if (fav.getId().equals(itemId)) {
                fav.setViewed(!fav.isViewed());
                saveFavorites();
                System.out.println("✏️ Estado actualizado: " + fav.getTitle());
                return;
            }
        }
    }

    // ============================
    // MARCAR/DESMARCAR UN EPISODIO CONCRETO COMO VISTO
    // ============================
    public static void toggleEpisodeWatched(String itemId, int episodeNumber) {
        ensureCorrectUserLoaded();
        for (FavoriteItem fav : favoritesList) {
            if (fav.getId().equals(itemId)) {
                boolean currentlyWatched = fav.isEpisodeWatched(episodeNumber);
                fav.setEpisodeWatched(episodeNumber, !currentlyWatched);

                // Si se han visto todos los episodios, marcar el item completo como visto
                Integer total = fav.getTotalEpisodes();
                if (total != null && total > 0) {
                    fav.setViewed(fav.getWatchedEpisodes().size() >= total);
                }

                saveFavorites();
                System.out.println("✏️ Episodio " + episodeNumber + " actualizado en: " + fav.getTitle());
                return;
            }
        }
    }

    // ============================
    // VERIFICAR SI ESTÁ EN FAVORITOS
    // ============================
    public static boolean isFavorite(String title) {
        ensureCorrectUserLoaded();
        return favoritesList.stream()
                .anyMatch(fav -> fav.getTitle().equalsIgnoreCase(title));
    }

    // ============================
    // GUARDAR FAVORITOS EN JSON
    // ============================
    private static void saveFavorites() {
        String file = getFavoritesFile();
        try {
            JsonObject root = new JsonObject();
            JsonArray favArray = new JsonArray();

            for (FavoriteItem fav : favoritesList) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", fav.getId());
                obj.addProperty("type", fav.getType().name());
                obj.addProperty("title", fav.getTitle());
                obj.addProperty("description", fav.getDescription());
                obj.addProperty("imageUrl", fav.getImageUrl());
                if (fav.getYear() != null) obj.addProperty("year", fav.getYear());
                if (fav.getScore() != null) obj.addProperty("score", fav.getScore());
                obj.addProperty("externalUrl", fav.getExternalUrl());
                obj.addProperty("viewed", fav.isViewed());
                obj.addProperty("addedDate", fav.getAddedDate());

                if (fav.getTotalEpisodes() != null) {
                    obj.addProperty("totalEpisodes", fav.getTotalEpisodes());
                }

                JsonArray watchedArray = new JsonArray();
                for (Integer ep : fav.getWatchedEpisodes()) {
                    watchedArray.add(ep);
                }
                obj.add("watchedEpisodes", watchedArray);

                if (fav.getPlatforms() != null) {
                    JsonArray platformsArray = new JsonArray();
                    for (String p : fav.getPlatforms()) platformsArray.add(p);
                    obj.add("platforms", platformsArray);
                }

                if (fav.getGenres() != null) {
                    JsonArray genresArray = new JsonArray();
                    for (String g : fav.getGenres()) genresArray.add(g);
                    obj.add("genres", genresArray);
                }

                if (fav.getTmdbId() != null) {
                    obj.addProperty("tmdbId", fav.getTmdbId());
                }

                if (fav.getTrailerKey() != null) {
                    obj.addProperty("trailerKey", fav.getTrailerKey());
                }

                if (fav.getPreviewUrl() != null) {
                    obj.addProperty("previewUrl", fav.getPreviewUrl());
                }

                if (fav.getAnilistId() != null) {
                    obj.addProperty("anilistId", fav.getAnilistId());
                }

                if (fav.getIgdbId() != null) {
                    obj.addProperty("igdbId", fav.getIgdbId());
                }

                favArray.add(obj);
            }

            root.add("favorites", favArray);

            String json = gson.toJson(root);
            Files.write(Paths.get(file), json.getBytes());
            System.out.println("💾 Favoritos guardados en " + file);

        } catch (IOException e) {
            System.err.println("❌ Error al guardar favoritos: " + e.getMessage());
        }
    }

    // ============================
    // PARSEAR ITEM DESDE JSON
    // ============================
    private static FavoriteItem parseFavoriteItem(JsonObject obj) {
        FavoriteItem item = new FavoriteItem();

        // Campos "obligatorios" en teoría, pero blindados con valores por
        // defecto razonables: un favorito guardado por una versión anterior
        // de la app, o un item cuya API de origen no devolvió algún campo
        // (ej. un juego de IGDB sin "summary"), puede llegar aquí sin ese
        // campo. Antes esto lanzaba NullPointerException y tiraba abajo
        // TODA la carga de favoritos (Inicio y Favoritos dejaban de
        // funcionar por completo, no solo para ese item concreto).
        item.setId(safeGetString(obj, "id", "sin_id_" + System.nanoTime()));
        item.setType(safeGetMediaType(obj, "type"));
        item.setTitle(safeGetString(obj, "title", "Sin título"));
        item.setDescription(safeGetString(obj, "description", ""));
        item.setImageUrl(safeGetString(obj, "imageUrl", ""));

        if (obj.has("year") && !obj.get("year").isJsonNull()) {
            item.setYear(obj.get("year").getAsInt());
        }
        if (obj.has("score") && !obj.get("score").isJsonNull()) {
            item.setScore(obj.get("score").getAsInt());
        }

        item.setExternalUrl(safeGetString(obj, "externalUrl", ""));
        item.setViewed(obj.has("viewed") && !obj.get("viewed").isJsonNull() && obj.get("viewed").getAsBoolean());
        item.setAddedDate(obj.has("addedDate") && !obj.get("addedDate").isJsonNull() ? obj.get("addedDate").getAsLong() : System.currentTimeMillis());

        if (obj.has("totalEpisodes") && !obj.get("totalEpisodes").isJsonNull()) {
            item.setTotalEpisodes(obj.get("totalEpisodes").getAsInt());
        }

        if (obj.has("watchedEpisodes") && obj.get("watchedEpisodes").isJsonArray()) {
            java.util.Set<Integer> watched = new java.util.HashSet<>();
            for (JsonElement el : obj.getAsJsonArray("watchedEpisodes")) {
                watched.add(el.getAsInt());
            }
            item.setWatchedEpisodes(watched);
        }

        if (obj.has("platforms") && obj.get("platforms").isJsonArray()) {
            List<String> platforms = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("platforms")) {
                platforms.add(el.getAsString());
            }
            item.setPlatforms(platforms);
        }

        if (obj.has("genres") && obj.get("genres").isJsonArray()) {
            List<String> genres = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("genres")) {
                genres.add(el.getAsString());
            }
            item.setGenres(genres);
        }

        if (obj.has("tmdbId") && !obj.get("tmdbId").isJsonNull()) {
            item.setTmdbId(obj.get("tmdbId").getAsInt());
        }

        if (obj.has("trailerKey") && !obj.get("trailerKey").isJsonNull()) {
            item.setTrailerKey(obj.get("trailerKey").getAsString());
        }

        if (obj.has("previewUrl") && !obj.get("previewUrl").isJsonNull()) {
            item.setPreviewUrl(obj.get("previewUrl").getAsString());
        }

        if (obj.has("anilistId") && !obj.get("anilistId").isJsonNull()) {
            item.setAnilistId(obj.get("anilistId").getAsInt());
        }

        if (obj.has("igdbId") && !obj.get("igdbId").isJsonNull()) {
            item.setIgdbId(obj.get("igdbId").getAsInt());
        }

        return item;
    }

    /**
     * Lee un campo String de un JsonObject de forma segura: si el campo
     * no existe, es JSON null, o no es una cadena válida, devuelve el
     * valor por defecto en vez de lanzar NullPointerException. Evita que
     * un solo favorito con datos incompletos (por ejemplo, guardado por
     * una versión anterior de la app, o un item cuya API de origen no
     * devolvió ese campo) tire abajo la carga de TODOS los favoritos.
     */
    private static String safeGetString(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {
                // El campo existe pero no es una cadena válida (tipo inesperado)
            }
        }
        return defaultValue;
    }

    /**
     * Lee el MediaType de un favorito de forma segura: si el valor
     * guardado no coincide con ningún valor del enum (por ejemplo, si
     * en el futuro se renombra o elimina un tipo), devuelve SERIES como
     * valor de respaldo razonable en vez de lanzar una excepción que
     * tiraría abajo la carga de todos los favoritos.
     */
    private static MediaType safeGetMediaType(JsonObject obj, String key) {
        String raw = safeGetString(obj, key, null);
        if (raw == null) return MediaType.SERIES;
        try {
            return MediaType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ MediaType desconocido en favorito: '" + raw + "', usando SERIES como respaldo");
            return MediaType.SERIES;
        }
    }
}