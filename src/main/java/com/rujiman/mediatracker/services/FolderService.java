package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.PlanFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio GENÉRICO de carpetas, reutilizable por cualquier pantalla que
 * necesite agrupar sus items en carpetas (Favoritos, y cada una de las 6
 * listas de seguimiento: Series vistas/viendo, Anime vistos/viendo,
 * Películas vistas, Juegos jugados).
 *
 * Cada pantalla tiene su propio "namespace" (un identificador de texto
 * único, ej. "favorites", "watch_series_vistas", "watch_anime_viendo"),
 * y sus carpetas viven en un archivo separado por usuario y namespace
 * (folders_<namespace>_<user>.json), completamente aisladas del resto —
 * una carpeta creada en "Series vistas" nunca es visible desde
 * "Series viendo" ni desde Favoritos, aunque ambas compartan items.
 *
 * A diferencia de PlanService (que también guarda los ITEMS, porque
 * "Pienso ver/jugar/escuchar" es una lista independiente de Favoritos),
 * este servicio solo guarda las CARPETAS y qué favorito está en cuál.
 * Los items en sí siguen viviendo en FavoritesService como siempre; aquí
 * solo se guarda la asignación favoriteId -> folderId para ese namespace
 * concreto, ya que un mismo favorito puede estar en una carpeta distinta
 * (o sin carpeta) en cada una de las 7 pantallas a la vez.
 */
public class FolderService {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, List<PlanFolder>> foldersCache = new HashMap<>();
    private static final Map<String, Map<String, String>> assignmentsCache = new HashMap<>();
    private static final Map<String, Boolean> loadedFlags = new HashMap<>();
    private static String loadedForUser = null;

    private static String getFile(String namespace) {
        String user = AuthService.getCurrentUser();
        if (user == null || user.isBlank()) {
            return "folders_" + namespace + ".json";
        }
        return "folders_" + namespace + "_" + user + ".json";
    }

    private static void ensureLoaded(String namespace) {
        String currentUser = AuthService.getCurrentUser();

        if (loadedForUser != null && !java.util.Objects.equals(loadedForUser, currentUser)) {
            foldersCache.clear();
            assignmentsCache.clear();
            loadedFlags.clear();
        }
        loadedForUser = currentUser;

        if (loadedFlags.getOrDefault(namespace, false)) return;

        foldersCache.put(namespace, new ArrayList<>());
        assignmentsCache.put(namespace, new HashMap<>());

        String file = getFile(namespace);

        try {
            if (!Files.exists(Paths.get(file))) {
                loadedFlags.put(namespace, true);
                return;
            }

            String json = new String(Files.readAllBytes(Paths.get(file)));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root != null && root.has("folders") && root.get("folders").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("folders")) {
                    try {
                        JsonObject obj = el.getAsJsonObject();
                        PlanFolder folder = new PlanFolder();
                        folder.setId(safeGetString(obj, "id", "folder_sin_id_" + System.nanoTime()));
                        folder.setName(safeGetString(obj, "name", "Carpeta sin nombre"));
                        folder.setCreatedDate(obj.has("createdDate") && !obj.get("createdDate").isJsonNull()
                                ? obj.get("createdDate").getAsLong() : System.currentTimeMillis());
                        foldersCache.get(namespace).add(folder);
                    } catch (Exception e) {
                        System.err.println("⚠️ Carpeta corrupta omitida (" + namespace + "): " + e.getMessage());
                    }
                }
            }

            if (root != null && root.has("assignments") && root.get("assignments").isJsonObject()) {
                JsonObject assignments = root.getAsJsonObject("assignments");
                for (Map.Entry<String, JsonElement> entry : assignments.entrySet()) {
                    try {
                        assignmentsCache.get(namespace).put(entry.getKey(), entry.getValue().getAsString());
                    } catch (Exception ignored) {}
                }
            }

            loadedFlags.put(namespace, true);

        } catch (IOException e) {
            System.err.println("❌ Error al cargar " + file + ": " + e.getMessage());
            loadedFlags.put(namespace, true);
        }
    }

    private static void save(String namespace) {
        String file = getFile(namespace);
        try {
            JsonObject root = new JsonObject();

            JsonArray foldersArray = new JsonArray();
            for (PlanFolder folder : foldersCache.get(namespace)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", folder.getId());
                obj.addProperty("name", folder.getName());
                obj.addProperty("createdDate", folder.getCreatedDate());
                foldersArray.add(obj);
            }
            root.add("folders", foldersArray);

            JsonObject assignmentsObj = new JsonObject();
            for (Map.Entry<String, String> entry : assignmentsCache.get(namespace).entrySet()) {
                assignmentsObj.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("assignments", assignmentsObj);

            String json = gson.toJson(root);
            Files.write(Paths.get(file), json.getBytes());

        } catch (IOException e) {
            System.err.println("❌ Error al guardar " + file + ": " + e.getMessage());
        }
    }

    // ============================
    // API PÚBLICA — CARPETAS
    // ============================

    public static List<PlanFolder> getFolders(String namespace) {
        ensureLoaded(namespace);
        return new ArrayList<>(foldersCache.get(namespace));
    }

    public static PlanFolder createFolder(String namespace, String name) {
        ensureLoaded(namespace);
        PlanFolder folder = new PlanFolder(name);
        foldersCache.get(namespace).add(folder);
        save(namespace);
        return folder;
    }

    public static void renameFolder(String namespace, String folderId, String newName) {
        ensureLoaded(namespace);
        for (PlanFolder folder : foldersCache.get(namespace)) {
            if (folder.getId().equals(folderId)) {
                folder.setName(newName);
                save(namespace);
                return;
            }
        }
    }

    /**
     * Elimina una carpeta. Los items que estaban dentro NO se borran de
     * Favoritos: simplemente quedan sin carpeta (sin asignación) en ese
     * namespace, igual que PlanService.deleteFolder().
     */
    public static void deleteFolder(String namespace, String folderId) {
        ensureLoaded(namespace);
        assignmentsCache.get(namespace).entrySet().removeIf(e -> e.getValue().equals(folderId));
        foldersCache.get(namespace).removeIf(f -> f.getId().equals(folderId));
        save(namespace);
    }

    // ============================
    // API PÚBLICA — ASIGNACIONES (favoriteId -> folderId)
    // ============================

    public static String getFolderIdFor(String namespace, String favoriteId) {
        ensureLoaded(namespace);
        return assignmentsCache.get(namespace).get(favoriteId);
    }

    public static void assignToFolder(String namespace, String favoriteId, String folderId) {
        ensureLoaded(namespace);
        if (folderId == null) {
            assignmentsCache.get(namespace).remove(favoriteId);
        } else {
            assignmentsCache.get(namespace).put(favoriteId, folderId);
        }
        save(namespace);
    }

    public static List<String> getFavoriteIdsInFolder(String namespace, String folderId) {
        ensureLoaded(namespace);
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : assignmentsCache.get(namespace).entrySet()) {
            if (entry.getValue().equals(folderId)) result.add(entry.getKey());
        }
        return result;
    }

    /**
     * Limpia asignaciones de favoritos que ya no existen (por ejemplo,
     * tras borrar un favorito desde el detalle). Se puede llamar al
     * abrir cada pantalla, igual que DashboardService.pruneDeletedFavorites().
     */
    public static void pruneDeletedFavorites(String namespace, List<String> validFavoriteIds) {
        ensureLoaded(namespace);
        boolean changed = assignmentsCache.get(namespace).keySet().retainAll(validFavoriteIds);
        if (changed) save(namespace);
    }

    private static String safeGetString(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}