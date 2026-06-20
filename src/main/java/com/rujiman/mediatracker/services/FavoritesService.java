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
                    JsonObject obj = el.getAsJsonObject();
                    FavoriteItem item = parseFavoriteItem(obj);
                    favoritesList.add(item);
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
        item.setId(obj.get("id").getAsString());
        item.setType(MediaType.valueOf(obj.get("type").getAsString()));
        item.setTitle(obj.get("title").getAsString());
        item.setDescription(obj.get("description").getAsString());
        item.setImageUrl(obj.get("imageUrl").getAsString());
        if (obj.has("year") && !obj.get("year").isJsonNull()) {
            item.setYear(obj.get("year").getAsInt());
        }
        if (obj.has("score") && !obj.get("score").isJsonNull()) {
            item.setScore(obj.get("score").getAsInt());
        }
        item.setExternalUrl(obj.get("externalUrl").getAsString());
        item.setViewed(obj.get("viewed").getAsBoolean());
        item.setAddedDate(obj.get("addedDate").getAsLong());

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

        return item;
    }
}