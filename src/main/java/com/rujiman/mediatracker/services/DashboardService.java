package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio que gestiona qué favoritos elige el usuario para mostrar en
 * cada sección fija de la pantalla de inicio ("Tu Universo"):
 * GAME (columna izquierda) y MUSIC / SERIES / MOVIE / ANIME (filas derecha).
 *
 * Guarda solo IDs de FavoriteItem (no copias), persistidos en
 * dashboard_<usuario>.json, indexados por MediaType.
 *
 * Es completamente manual: nada se añade aquí automáticamente al marcar
 * un favorito; el usuario decide qué entra en cada sección desde la UI.
 */
public class DashboardService {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Una entrada de lista por cada MediaType que tiene sección fija en la home
    private static Map<MediaType, List<String>> sections = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private static String getDashboardFile() {
        String user = AuthService.getCurrentUser();
        if (user == null || user.isBlank()) {
            return "dashboard.json";
        }
        return "dashboard_" + user + ".json";
    }

    private static void ensureCorrectUserLoaded() {
        String currentUser = AuthService.getCurrentUser();
        if (loaded && java.util.Objects.equals(loadedForUser, currentUser)) {
            return;
        }
        sections.clear();
        loaded = false;
        loadDashboard();
    }

    private static void loadDashboard() {
        if (loaded) return;

        initEmptySections();

        String file = getDashboardFile();

        try {
            if (!Files.exists(Paths.get(file))) {
                loaded = true;
                loadedForUser = AuthService.getCurrentUser();
                return;
            }

            String json = new String(Files.readAllBytes(Paths.get(file)));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root != null && root.has("sections")) {
                JsonObject sectionsObj = root.getAsJsonObject("sections");

                for (MediaType type : MediaType.values()) {
                    if (sectionsObj.has(type.name())) {
                        JsonArray idsArray = sectionsObj.getAsJsonArray(type.name());
                        List<String> ids = new ArrayList<>();
                        for (JsonElement el : idsArray) {
                            ids.add(el.getAsString());
                        }
                        sections.put(type, ids);
                    }
                }
            }

            loaded = true;
            loadedForUser = AuthService.getCurrentUser();

        } catch (IOException e) {
            System.err.println("❌ Error al cargar dashboard.json: " + e.getMessage());
            loaded = true;
            loadedForUser = AuthService.getCurrentUser();
        }
    }

    private static void initEmptySections() {
        sections.clear();
        for (MediaType type : MediaType.values()) {
            sections.put(type, new ArrayList<>());
        }
    }

    private static void saveDashboard() {
        String file = getDashboardFile();
        try {
            JsonObject root = new JsonObject();
            JsonObject sectionsObj = new JsonObject();

            for (Map.Entry<MediaType, List<String>> entry : sections.entrySet()) {
                JsonArray idsArray = new JsonArray();
                for (String id : entry.getValue()) idsArray.add(id);
                sectionsObj.add(entry.getKey().name(), idsArray);
            }

            root.add("sections", sectionsObj);

            String json = gson.toJson(root);
            Files.write(Paths.get(file), json.getBytes());
            System.out.println("💾 dashboard.json actualizado");

        } catch (IOException e) {
            System.err.println("❌ Error al guardar dashboard.json: " + e.getMessage());
        }
    }

    /**
     * Devuelve los IDs (en orden) colocados en la sección de un tipo.
     */
    public static List<String> getSectionIds(MediaType type) {
        ensureCorrectUserLoaded();
        return new ArrayList<>(sections.getOrDefault(type, new ArrayList<>()));
    }

    /**
     * Devuelve los FavoriteItem reales (resueltos) colocados en una sección,
     * cruzando los IDs guardados con FavoritesService.getFavorites().
     * Si algún ID ya no existe en favoritos (se eliminó), se omite
     * silenciosamente (auto-limpieza al guardar la próxima vez).
     */
    public static List<FavoriteItem> getSectionItems(MediaType type) {
        ensureCorrectUserLoaded();
        List<String> ids = sections.getOrDefault(type, new ArrayList<>());
        List<FavoriteItem> allFavorites = FavoritesService.getFavorites();

        List<FavoriteItem> result = new ArrayList<>();
        for (String id : ids) {
            for (FavoriteItem fav : allFavorites) {
                if (fav.getId().equals(id)) {
                    result.add(fav);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Añade un favorito a una sección (al final). No hace nada si ya estaba.
     */
    public static void addToSection(MediaType type, String favoriteId) {
        ensureCorrectUserLoaded();
        List<String> ids = sections.computeIfAbsent(type, t -> new ArrayList<>());
        if (!ids.contains(favoriteId)) {
            ids.add(favoriteId);
            saveDashboard();
        }
    }

    /**
     * Quita un favorito de una sección.
     */
    public static void removeFromSection(MediaType type, String favoriteId) {
        ensureCorrectUserLoaded();
        List<String> ids = sections.get(type);
        if (ids != null && ids.remove(favoriteId)) {
            saveDashboard();
        }
    }

    /**
     * Sustituye por completo el contenido de una sección con la lista
     * de IDs dada, en ese orden. Útil para el selector de "elegir favoritos".
     */
    public static void setSectionItems(MediaType type, List<String> favoriteIds) {
        ensureCorrectUserLoaded();
        sections.put(type, new ArrayList<>(favoriteIds));
        saveDashboard();
    }

    /**
     * Elimina de todas las secciones cualquier ID que ya no exista en
     * favoritos (por ejemplo, tras borrar un favorito desde otra vista).
     * Se puede llamar al abrir la home para mantener todo consistente.
     */
    public static void pruneDeletedFavorites() {
        ensureCorrectUserLoaded();
        List<FavoriteItem> allFavorites = FavoritesService.getFavorites();
        List<String> validIds = new ArrayList<>();
        for (FavoriteItem fav : allFavorites) validIds.add(fav.getId());

        boolean changed = false;
        for (Map.Entry<MediaType, List<String>> entry : sections.entrySet()) {
            boolean removed = entry.getValue().removeIf(id -> !validIds.contains(id));
            if (removed) changed = true;
        }

        if (changed) saveDashboard();
    }
}
