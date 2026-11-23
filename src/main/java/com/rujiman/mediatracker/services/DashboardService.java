package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio que gestiona qué favoritos elige el usuario para mostrar en cada
 * sección de la vitrina y cuáles de las 7 secciones son principales
 * (grandes) y cuáles compactas, con su orden.
 *
 * Guarda solo IDs de FavoriteItem (no copias), persistidos en
 * dashboard_<usuario>.json, indexados por MediaType.
 *
 * Es completamente manual: nada se añade aquí automáticamente al marcar
 * un favorito; el usuario decide qué entra en cada sección desde la UI.
 */
public class DashboardService {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Vitrina: 4 secciones principales (favorito absoluto + ranking, 5
     * huecos) y 3 compactas (4 portadas). Cuáles son principales lo elige
     * el usuario en "Editar vitrina"; por defecto, Juegos, Anime, Series
     * y Películas.
     *
     * Una sección guarda hasta 5 aunque sea compacta: así, si pasa de
     * principal a compacta y vuelve, su quinto favorito no se pierde.
     */
    public static final int MAIN_COUNT = 4;
    public static final int MAIN_SLOTS = 5;
    public static final int COMPACT_SLOTS = 4;

    /** Orden de las secciones cuando no hay nada guardado. */
    private static final List<MediaType> DEFAULT_ORDER = List.of(
            MediaType.GAME, MediaType.ANIME, MediaType.SERIES, MediaType.MOVIE,
            MediaType.MUSIC, MediaType.BOOK, MediaType.MANGA);

    private static final List<MediaType> mainSections = new ArrayList<>(DEFAULT_ORDER.subList(0, MAIN_COUNT));
    private static final List<MediaType> compactSections = new ArrayList<>(DEFAULT_ORDER.subList(MAIN_COUNT, DEFAULT_ORDER.size()));

    /** Huecos visibles de una sección: 5 si es principal, 4 si es compacta. */
    public static int getMaxItemsForSection(MediaType type) {
        ensureCorrectUserLoaded();
        return mainSections.contains(type) ? MAIN_SLOTS : COMPACT_SLOTS;
    }

    public static boolean isMain(MediaType type) {
        ensureCorrectUserLoaded();
        return mainSections.contains(type);
    }

    /** Las 4 principales, en el orden en que se pintan. */
    public static List<MediaType> getMainSections() {
        ensureCorrectUserLoaded();
        return new ArrayList<>(mainSections);
    }

    /** Las 3 compactas, en el orden en que se pintan. */
    public static List<MediaType> getCompactSections() {
        ensureCorrectUserLoaded();
        return new ArrayList<>(compactSections);
    }

    /**
     * Cambia qué secciones son principales y el orden de todas. Recibe
     * las 7 en orden: las que estén en "main" (4) van arriba, en ese orden.
     */
    public static void setLayout(List<MediaType> main, List<MediaType> compact) {
        ensureCorrectUserLoaded();
        if (main.size() != MAIN_COUNT) return;
        applyLayout(main, compact);
        saveDashboard();
    }

    /** Valida y completa: 4 principales y el resto compactas, sin repetir. */
    private static void applyLayout(List<MediaType> main, List<MediaType> compact) {
        List<MediaType> m = new ArrayList<>();
        for (MediaType t : main) if (t != null && !m.contains(t) && m.size() < MAIN_COUNT) m.add(t);
        for (MediaType t : DEFAULT_ORDER) if (m.size() < MAIN_COUNT && !m.contains(t)) m.add(t);
        List<MediaType> c = new ArrayList<>();
        for (MediaType t : compact) if (t != null && !m.contains(t) && !c.contains(t)) c.add(t);
        for (MediaType t : DEFAULT_ORDER) if (!m.contains(t) && !c.contains(t)) c.add(t);
        mainSections.clear();
        mainSections.addAll(m);
        compactSections.clear();
        compactSections.addAll(c);
    }

    // Una entrada de lista por cada MediaType que tiene sección fija en la home
    private static Map<MediaType, List<String>> sections = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private static java.nio.file.Path getDashboardFile() {
        String user = AuthService.getCurrentUser();
        return AppPaths.userFile(user, AppPaths.DIR_DASHBOARD, "dashboard.json");
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

        java.nio.file.Path file = getDashboardFile();

        try {
            if (!Files.exists(file)) {
                loaded = true;
                loadedForUser = AuthService.getCurrentUser();
                return;
            }

            String json = new String(Files.readAllBytes(file));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root != null && root.has("main")) {
                applyLayout(readTypes(root.getAsJsonArray("main")),
                        root.has("compact") ? readTypes(root.getAsJsonArray("compact")) : List.of());
            }

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

    private static List<MediaType> readTypes(JsonArray array) {
        List<MediaType> out = new ArrayList<>();
        for (JsonElement el : array) {
            try { out.add(MediaType.valueOf(el.getAsString())); } catch (Exception ignored) { }
        }
        return out;
    }

    private static void initEmptySections() {
        applyLayout(DEFAULT_ORDER.subList(0, MAIN_COUNT), DEFAULT_ORDER.subList(MAIN_COUNT, DEFAULT_ORDER.size()));
        sections.clear();
        for (MediaType type : MediaType.values()) {
            sections.put(type, new ArrayList<>());
        }
    }

    private static void saveDashboard() {
        java.nio.file.Path file = getDashboardFile();
        try {
            JsonObject root = new JsonObject();
            JsonObject sectionsObj = new JsonObject();

            for (Map.Entry<MediaType, List<String>> entry : sections.entrySet()) {
                JsonArray idsArray = new JsonArray();
                for (String id : entry.getValue()) idsArray.add(id);
                sectionsObj.add(entry.getKey().name(), idsArray);
            }

            root.add("sections", sectionsObj);
            JsonArray mainArr = new JsonArray();
            for (MediaType t : mainSections) mainArr.add(t.name());
            JsonArray compactArr = new JsonArray();
            for (MediaType t : compactSections) compactArr.add(t.name());
            root.add("main", mainArr);
            root.add("compact", compactArr);

            String json = gson.toJson(root);
            Files.write(file, json.getBytes());
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
        List<String> ids = sections.getOrDefault(type, new ArrayList<>());
        return new ArrayList<>(ids.subList(0, Math.min(ids.size(), getMaxItemsForSection(type))));
    }

    /**
     * Devuelve los FavoriteItem reales (resueltos) colocados en una sección,
     * cruzando los IDs guardados con FavoritesService.getFavorites().
     * Si algún ID ya no existe en favoritos (se eliminó), se omite
     * silenciosamente (auto-limpieza al guardar la próxima vez).
     */
    public static List<FavoriteItem> getSectionItems(MediaType type) {
        ensureCorrectUserLoaded();
        List<String> ids = getSectionIds(type);   // solo los visibles
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
        if (!ids.contains(favoriteId) && ids.size() < getMaxItemsForSection(type)) {
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
     * Si se supera el límite de la sección, se recorta (defensa adicional;
     * la UI del selector ya debería impedir superarlo).
     */
    public static void setSectionItems(MediaType type, List<String> favoriteIds) {
        ensureCorrectUserLoaded();
        int max = getMaxItemsForSection(type);
        List<String> previous = sections.getOrDefault(type, new ArrayList<>());
        List<String> result = new ArrayList<>(favoriteIds.size() > max ? favoriteIds.subList(0, max) : favoriteIds);
        // Sección compacta llena: se conserva el quinto que estaba guardado
        // (oculto) para cuando vuelva a ser principal
        if (result.size() == max) {
            for (int k = max; k < previous.size() && result.size() < MAIN_SLOTS; k++) {
                if (!result.contains(previous.get(k))) result.add(previous.get(k));
            }
        }
        sections.put(type, result);
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