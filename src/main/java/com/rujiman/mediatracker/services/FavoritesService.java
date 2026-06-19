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
 * Servicio para guardar y cargar favoritos en JSON
 */
public class FavoritesService {

    private static final String FAVORITES_FILE = "favorites.json";
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static List<FavoriteItem> favoritesList = new ArrayList<>();
    private static boolean loaded = false;

    // ============================
    // CARGAR FAVORITOS
    // ============================
    public static void loadFavorites() {
        if (loaded) return;

        System.out.println("📖 Cargando favoritos...");

        try {
            if (!Files.exists(Paths.get(FAVORITES_FILE))) {
                System.out.println("✅ No hay favoritos previos");
                loaded = true;
                return;
            }

            String json = new String(Files.readAllBytes(Paths.get(FAVORITES_FILE)));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("favorites")) {
                JsonArray favArray = root.getAsJsonArray("favorites");

                for (JsonElement el : favArray) {
                    JsonObject obj = el.getAsJsonObject();
                    FavoriteItem item = parseFavoriteItem(obj);
                    favoritesList.add(item);
                }

                System.out.println("✅ Cargados " + favoritesList.size() + " favoritos");
            }

            loaded = true;

        } catch (IOException e) {
            System.err.println("❌ Error al cargar favoritos: " + e.getMessage());
            loaded = true;
        }
    }

    // ============================
    // AGREGAR FAVORITO
    // ============================
    public static void addFavorite(FavoriteItem item) {
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
        favoritesList.removeIf(fav -> fav.getId().equals(itemId));
        saveFavorites();
        System.out.println("❌ Eliminado de favoritos");
    }

    // ============================
    // OBTENER TODOS LOS FAVORITOS
    // ============================
    public static List<FavoriteItem> getFavorites() {
        if (!loaded) loadFavorites();
        return new ArrayList<>(favoritesList);
    }

    // ============================
    // OBTENER FAVORITOS POR TIPO
    // ============================
    public static List<FavoriteItem> getFavoritesByType(MediaType type) {
        if (!loaded) loadFavorites();
        return favoritesList.stream()
                .filter(fav -> fav.getType() == type)
                .toList();
    }

    // ============================
    // ACTUALIZAR ESTADO (VISTO/JUGADO)
    // ============================
    public static void toggleViewed(String itemId) {
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
    // VERIFICAR SI ESTÁ EN FAVORITOS
    // ============================
    public static boolean isFavorite(String title) {
        return favoritesList.stream()
                .anyMatch(fav -> fav.getTitle().equalsIgnoreCase(title));
    }

    // ============================
    // GUARDAR FAVORITOS EN JSON
    // ============================
    private static void saveFavorites() {
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

                favArray.add(obj);
            }

            root.add("favorites", favArray);

            String json = gson.toJson(root);
            Files.write(Paths.get(FAVORITES_FILE), json.getBytes());
            System.out.println("💾 Favoritos guardados");

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
        return item;
    }
}
