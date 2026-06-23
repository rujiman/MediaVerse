package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanFolder;
import com.rujiman.mediatracker.models.PlanItem;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para las 3 listas "Pienso ver / Pienso jugar / Pienso
 * escuchar": cosas que llamaron la atención pero todavía no se han visto/
 * jugado/escuchado. Completamente independiente de FavoritesService — un
 * mismo título puede estar en ambas listas a la vez sin conflicto.
 *
 * Cada lista (ver/jugar/escuchar) es un ListKind distinto, con su propio
 * archivo JSON por usuario (plan_watch_<user>.json, etc.), conteniendo
 * tanto los items como las carpetas de esa lista en concreto. Las
 * carpetas de una lista nunca son visibles desde otra.
 *
 * Reutiliza el mismo patrón robusto de FavoritesService: helpers
 * safeGetX() para que un item con datos incompletos no rompa la carga de
 * toda la lista, y un bucle de carga que omite (sin abortar) cualquier
 * entrada individual que falle inesperadamente.
 */
public class PlanService {

    /**
     * A qué lista pertenece un item/carpeta. WATCH cubre series, películas
     * y anime juntos (todo lo que se "ve"), PLAY solo juegos, LISTEN solo
     * música — coincide con cómo el usuario las describió: "Pienso ver",
     * "Pienso jugar", "Pienso escuchar".
     */
    public enum ListKind {
        WATCH, PLAY, LISTEN
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Caché en memoria por ListKind, igual patrón que FavoritesService
    private static final Map<ListKind, List<PlanItem>> itemsCache = new HashMap<>();
    private static final Map<ListKind, List<PlanFolder>> foldersCache = new HashMap<>();
    private static final Map<ListKind, Boolean> loadedFlags = new HashMap<>();
    private static String loadedForUser = null;

    private static java.nio.file.Path getFile(ListKind kind) {
        String user = AuthService.getCurrentUser();
        String suffix = switch (kind) {
            case WATCH -> "watch";
            case PLAY -> "play";
            case LISTEN -> "listen";
        };
        return AppPaths.userFile(user, AppPaths.DIR_PLAN, "plan_" + suffix + ".json");
    }

    /**
     * Si el usuario logueado cambió desde la última carga, invalida TODO
     * el caché (las 3 listas), igual que FavoritesService hace con la suya.
     */
    private static void ensureCorrectUserLoaded() {
        String currentUser = AuthService.getCurrentUser();
        if (loadedForUser != null && java.util.Objects.equals(loadedForUser, currentUser)
                && loadedFlags.getOrDefault(ListKind.WATCH, false)
                && loadedFlags.getOrDefault(ListKind.PLAY, false)
                && loadedFlags.getOrDefault(ListKind.LISTEN, false)) {
            return;
        }
        itemsCache.clear();
        foldersCache.clear();
        loadedFlags.clear();
        for (ListKind kind : ListKind.values()) {
            loadList(kind);
        }
        loadedForUser = currentUser;
    }

    private static void loadList(ListKind kind) {
        itemsCache.put(kind, new ArrayList<>());
        foldersCache.put(kind, new ArrayList<>());

        java.nio.file.Path file = getFile(kind);

        try {
            if (!Files.exists(file)) {
                loadedFlags.put(kind, true);
                return;
            }

            String json = new String(Files.readAllBytes(file));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root != null && root.has("folders") && root.get("folders").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("folders")) {
                    try {
                        foldersCache.get(kind).add(parseFolder(el.getAsJsonObject()));
                    } catch (Exception e) {
                        System.err.println("⚠️ Carpeta de plan corrupta omitida (" + kind + "): " + e.getMessage());
                    }
                }
            }

            if (root != null && root.has("items") && root.get("items").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("items")) {
                    try {
                        itemsCache.get(kind).add(parseItem(el.getAsJsonObject()));
                    } catch (Exception e) {
                        System.err.println("⚠️ Item de plan corrupto omitido (" + kind + "): " + e.getMessage());
                    }
                }
            }

            loadedFlags.put(kind, true);

        } catch (IOException e) {
            System.err.println("❌ Error al cargar " + file + ": " + e.getMessage());
            loadedFlags.put(kind, true);
        }
    }

    private static void saveList(ListKind kind) {
        java.nio.file.Path file = getFile(kind);
        try {
            JsonObject root = new JsonObject();

            JsonArray foldersArray = new JsonArray();
            for (PlanFolder folder : foldersCache.get(kind)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", folder.getId());
                obj.addProperty("name", folder.getName());
                obj.addProperty("createdDate", folder.getCreatedDate());
                foldersArray.add(obj);
            }
            root.add("folders", foldersArray);

            JsonArray itemsArray = new JsonArray();
            for (PlanItem item : itemsCache.get(kind)) {
                itemsArray.add(serializeItem(item));
            }
            root.add("items", itemsArray);

            String json = gson.toJson(root);
            Files.write(file, json.getBytes());

        } catch (IOException e) {
            System.err.println("❌ Error al guardar " + file + ": " + e.getMessage());
        }
    }

    // ============================
    // API PÚBLICA — ITEMS
    // ============================

    public static List<PlanItem> getItems(ListKind kind) {
        ensureCorrectUserLoaded();
        return new ArrayList<>(itemsCache.get(kind));
    }

    /** Items sueltos (sin carpeta) de una lista. */
    public static List<PlanItem> getRootItems(ListKind kind) {
        ensureCorrectUserLoaded();
        List<PlanItem> result = new ArrayList<>();
        for (PlanItem item : itemsCache.get(kind)) {
            if (item.getFolderId() == null) result.add(item);
        }
        return result;
    }

    /** Items dentro de una carpeta concreta de una lista. */
    public static List<PlanItem> getItemsInFolder(ListKind kind, String folderId) {
        ensureCorrectUserLoaded();
        List<PlanItem> result = new ArrayList<>();
        for (PlanItem item : itemsCache.get(kind)) {
            if (folderId != null && folderId.equals(item.getFolderId())) result.add(item);
        }
        return result;
    }

    public static boolean isInPlan(ListKind kind, String title) {
        ensureCorrectUserLoaded();
        return itemsCache.get(kind).stream()
                .anyMatch(i -> i.getTitle().equalsIgnoreCase(title));
    }

    public static void addItem(ListKind kind, PlanItem item) {
        ensureCorrectUserLoaded();
        if (itemsCache.get(kind).stream().noneMatch(i -> i.getTitle().equalsIgnoreCase(item.getTitle()))) {
            itemsCache.get(kind).add(item);
            saveList(kind);
        }
    }

    public static void removeItem(ListKind kind, String itemId) {
        ensureCorrectUserLoaded();
        itemsCache.get(kind).removeIf(i -> i.getId().equals(itemId));
        saveList(kind);
    }

    /** Persiste cambios sobre un item ya en memoria (mismo patrón que FavoritesService.updateFavorite). */
    public static void updateItem(ListKind kind, PlanItem item) {
        ensureCorrectUserLoaded();
        saveList(kind);
    }

    public static void moveItemToFolder(ListKind kind, String itemId, String folderId) {
        ensureCorrectUserLoaded();
        for (PlanItem item : itemsCache.get(kind)) {
            if (item.getId().equals(itemId)) {
                item.setFolderId(folderId);
                saveList(kind);
                return;
            }
        }
    }

    // ============================
    // API PÚBLICA — CARPETAS
    // ============================

    public static List<PlanFolder> getFolders(ListKind kind) {
        ensureCorrectUserLoaded();
        return new ArrayList<>(foldersCache.get(kind));
    }

    public static PlanFolder createFolder(ListKind kind, String name) {
        ensureCorrectUserLoaded();
        PlanFolder folder = new PlanFolder(name);
        foldersCache.get(kind).add(folder);
        saveList(kind);
        return folder;
    }

    public static void renameFolder(ListKind kind, String folderId, String newName) {
        ensureCorrectUserLoaded();
        for (PlanFolder folder : foldersCache.get(kind)) {
            if (folder.getId().equals(folderId)) {
                folder.setName(newName);
                saveList(kind);
                return;
            }
        }
    }

    /**
     * Elimina una carpeta. Los items que estaban dentro NO se borran:
     * vuelven a quedar sueltos (sin carpeta) en la raíz de la lista, para
     * no perder accidentalmente contenido guardado al borrar una carpeta.
     */
    public static void deleteFolder(ListKind kind, String folderId) {
        ensureCorrectUserLoaded();
        for (PlanItem item : itemsCache.get(kind)) {
            if (folderId.equals(item.getFolderId())) {
                item.setFolderId(null);
            }
        }
        foldersCache.get(kind).removeIf(f -> f.getId().equals(folderId));
        saveList(kind);
    }

    // ============================
    // SERIALIZACIÓN / PARSEO
    // ============================

    private static JsonObject serializeItem(PlanItem item) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", item.getId());
        obj.addProperty("type", item.getType().name());
        obj.addProperty("title", item.getTitle());
        obj.addProperty("description", item.getDescription());
        obj.addProperty("imageUrl", item.getImageUrl());
        if (item.getYear() != null) obj.addProperty("year", item.getYear());
        if (item.getScore() != null) obj.addProperty("score", item.getScore());
        obj.addProperty("externalUrl", item.getExternalUrl());
        obj.addProperty("viewed", item.isViewed());
        obj.addProperty("addedDate", item.getAddedDate());

        if (item.getTotalEpisodes() != null) {
            obj.addProperty("totalEpisodes", item.getTotalEpisodes());
        }

        JsonArray watchedArray = new JsonArray();
        for (Integer ep : item.getWatchedEpisodes()) watchedArray.add(ep);
        obj.add("watchedEpisodes", watchedArray);

        if (item.getPlatforms() != null) {
            JsonArray platformsArray = new JsonArray();
            for (String p : item.getPlatforms()) platformsArray.add(p);
            obj.add("platforms", platformsArray);
        }

        if (item.getGenres() != null) {
            JsonArray genresArray = new JsonArray();
            for (String g : item.getGenres()) genresArray.add(g);
            obj.add("genres", genresArray);
        }

        if (item.getTmdbId() != null) obj.addProperty("tmdbId", item.getTmdbId());
        if (item.getTrailerKey() != null) obj.addProperty("trailerKey", item.getTrailerKey());
        if (item.getPreviewUrl() != null) obj.addProperty("previewUrl", item.getPreviewUrl());
        if (item.getAnilistId() != null) obj.addProperty("anilistId", item.getAnilistId());
        if (item.getIgdbId() != null) obj.addProperty("igdbId", item.getIgdbId());
        if (item.getFolderId() != null) obj.addProperty("folderId", item.getFolderId());

        return obj;
    }

    private static PlanItem parseItem(JsonObject obj) {
        PlanItem item = new PlanItem();

        item.setId(safeGetString(obj, "id", "sin_id_" + System.nanoTime()));
        item.setType(safeGetMediaType(obj, "type"));
        item.setTitle(safeGetString(obj, "title", "Sin título"));
        item.setDescription(safeGetString(obj, "description", ""));
        item.setImageUrl(safeGetString(obj, "imageUrl", ""));

        if (obj.has("year") && !obj.get("year").isJsonNull()) item.setYear(obj.get("year").getAsInt());
        if (obj.has("score") && !obj.get("score").isJsonNull()) item.setScore(obj.get("score").getAsInt());

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
            for (JsonElement el : obj.getAsJsonArray("platforms")) platforms.add(el.getAsString());
            item.setPlatforms(platforms);
        }

        if (obj.has("genres") && obj.get("genres").isJsonArray()) {
            List<String> genres = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("genres")) genres.add(el.getAsString());
            item.setGenres(genres);
        }

        if (obj.has("tmdbId") && !obj.get("tmdbId").isJsonNull()) item.setTmdbId(obj.get("tmdbId").getAsInt());
        if (obj.has("trailerKey") && !obj.get("trailerKey").isJsonNull()) item.setTrailerKey(obj.get("trailerKey").getAsString());
        if (obj.has("previewUrl") && !obj.get("previewUrl").isJsonNull()) item.setPreviewUrl(obj.get("previewUrl").getAsString());
        if (obj.has("anilistId") && !obj.get("anilistId").isJsonNull()) item.setAnilistId(obj.get("anilistId").getAsInt());
        if (obj.has("igdbId") && !obj.get("igdbId").isJsonNull()) item.setIgdbId(obj.get("igdbId").getAsInt());
        if (obj.has("folderId") && !obj.get("folderId").isJsonNull()) item.setFolderId(obj.get("folderId").getAsString());

        return item;
    }

    private static PlanFolder parseFolder(JsonObject obj) {
        PlanFolder folder = new PlanFolder();
        folder.setId(safeGetString(obj, "id", "folder_sin_id_" + System.nanoTime()));
        folder.setName(safeGetString(obj, "name", "Carpeta sin nombre"));
        folder.setCreatedDate(obj.has("createdDate") && !obj.get("createdDate").isJsonNull() ? obj.get("createdDate").getAsLong() : System.currentTimeMillis());
        return folder;
    }

    // ============================
    // HELPERS SEGUROS (mismo patrón que FavoritesService)
    // ============================

    private static String safeGetString(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    private static MediaType safeGetMediaType(JsonObject obj, String key) {
        String raw = safeGetString(obj, key, null);
        if (raw == null) return MediaType.SERIES;
        try {
            return MediaType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ MediaType desconocido en plan: '" + raw + "', usando SERIES como respaldo");
            return MediaType.SERIES;
        }
    }
}