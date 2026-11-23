package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Gestiona la lista de favoritos del usuario, persistida en JSON.
 *
 * DISEÑO: los métodos públicos son estáticos (compatibilidad con todos
 * los controllers existentes). La lógica real vive en métodos de instancia
 * privados; ServiceLocator proporciona la instancia por sesión.
 *
 * FIX NPE: parseFavoriteItem() protege TODOS los campos con null-checks.
 * Un favorito corrupto se omite y se loguea; el resto cargan correctamente.
 */
public class FavoritesService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Estado de instancia (un objeto por usuario/sesión) ─────────────
    private final String username;
    private final List<FavoriteItem> list = new ArrayList<>();
    private boolean loaded = false;

    FavoritesService(String username) {          // package-private: solo ServiceLocator
        this.username = username;
    }

    // ── Ruta del archivo ───────────────────────────────────────────────
    private Path file() {
        return AppPaths.userFile(username, AppPaths.DIR_FAVORITES, "favorites.json");
    }

    // ── Carga perezosa ─────────────────────────────────────────────────
    private void ensureLoaded() {
        if (loaded) return;
        Path f = file();
        System.out.println("📖 Cargando favoritos de: " + f);
        try {
            if (!Files.exists(f)) {
                System.out.println("✅ No hay favoritos previos para " + username);
                loaded = true;
                return;
            }
            String json = Files.readString(f);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null && root.has("favorites")) {
                for (JsonElement el : root.getAsJsonArray("favorites")) {
                    try { list.add(parseFavoriteItem(el.getAsJsonObject())); }
                    catch (Exception e) { System.err.println("⚠️ Favorito corrupto omitido: " + e.getMessage()); }
                }
            }
            System.out.println("✅ Cargados " + list.size() + " favoritos");
        } catch (IOException e) {
            System.err.println("❌ Error al cargar favoritos: " + e.getMessage());
        } finally { loaded = true; }
    }

    // ── CRUD de instancia (uso interno) ───────────────────────────────
    void instanceAdd(FavoriteItem item) {
        ensureLoaded();
        if (!list.contains(item)) {
            list.add(item);
            save();
            System.out.println("⭐ " + item.getTitle());
            if (item.getType() != null && item.getTitle() != null) {
                ActivityService.live(ActivityService.FAVORITE, WatchProgressService.keyFor(item.getType(), item.getTitle()));
            }
        }
    }
    void instanceUpdate() { ensureLoaded(); save(); }
    void instanceRemove(String id) {
        ensureLoaded(); list.removeIf(f -> id.equals(f.getId())); save();
        System.out.println("❌ Eliminado: " + id);
    }
    List<FavoriteItem> instanceGetAll() { ensureLoaded(); return Collections.unmodifiableList(list); }
    List<FavoriteItem> instanceGetByType(MediaType t) {
        ensureLoaded(); return list.stream().filter(f -> f.getType() == t).toList();
    }
    boolean instanceIsFavorite(String title) {
        ensureLoaded();
        return list.stream().anyMatch(f -> f.getTitle() != null && f.getTitle().equalsIgnoreCase(title));
    }
    boolean instanceIsFavorite(String title, MediaType type) {
        ensureLoaded();
        return list.stream().anyMatch(f -> f.getTitle() != null && f.getTitle().equalsIgnoreCase(title) && f.getType() == type);
    }
    Optional<FavoriteItem> instanceFindById(String id) {
        ensureLoaded(); return list.stream().filter(f -> id.equals(f.getId())).findFirst();
    }
    void instanceToggleViewed(String id) {
        ensureLoaded();
        instanceFindById(id).ifPresent(f -> { f.setViewed(!f.isViewed()); save(); });
    }
    void instanceToggleEpisode(String id, int ep) {
        ensureLoaded();
        instanceFindById(id).ifPresent(fav -> {
            fav.setEpisodeWatched(ep, !fav.isEpisodeWatched(ep));
            Integer total = fav.getTotalEpisodes();
            if (total != null && total > 0) fav.setViewed(fav.getWatchedEpisodes().size() >= total);
            save();
        });
    }

    // ── Persistencia ───────────────────────────────────────────────────
    private void save() {
        try {
            JsonArray arr = new JsonArray();
            for (FavoriteItem fav : list) arr.add(serialize(fav));
            JsonObject root = new JsonObject();
            root.add("favorites", arr);
            Files.writeString(file(), GSON.toJson(root));
        } catch (IOException e) { System.err.println("❌ Error al guardar favoritos: " + e.getMessage()); }
    }

    // ══════════════════════════════════════════════════════════════════
    // API ESTÁTICA PÚBLICA — usada por todos los controllers existentes
    // ══════════════════════════════════════════════════════════════════

    private static FavoritesService svc() { return ServiceLocator.get().favorites(); }

    public static void addFavorite(FavoriteItem item)                        { svc().instanceAdd(item); }
    public static void updateFavorite(FavoriteItem item)                     { svc().instanceUpdate(); }
    public static void removeFavorite(String id)                             { svc().instanceRemove(id); }
    public static List<FavoriteItem> getFavorites()                          { return new ArrayList<>(svc().instanceGetAll()); }
    public static List<FavoriteItem> getFavoritesByType(MediaType t)         { return svc().instanceGetByType(t); }
    public static boolean isFavorite(String title)                           { return svc().instanceIsFavorite(title); }
    /** Igual que isFavorite(title) pero exigiendo también el tipo (evita que el manga "Berserk" salga como favorito por el anime). */
    public static boolean isFavorite(String title, MediaType type)           { return svc().instanceIsFavorite(title, type); }
    public static void toggleViewed(String id)                               { svc().instanceToggleViewed(id); }
    public static void toggleEpisodeWatched(String id, int ep)              { svc().instanceToggleEpisode(id, ep); }

    // ── Serialización ──────────────────────────────────────────────────
    private static JsonObject serialize(FavoriteItem fav) {
        JsonObject o = new JsonObject();
        o.addProperty("id",          fav.getId());
        o.addProperty("type",        fav.getType() != null ? fav.getType().name() : "SERIES");
        o.addProperty("title",       fav.getTitle());
        o.addProperty("description", fav.getDescription());
        o.addProperty("imageUrl",    fav.getImageUrl());
        o.addProperty("externalUrl", fav.getExternalUrl());
        o.addProperty("viewed",      fav.isViewed());
        o.addProperty("addedDate",   fav.getAddedDate());
        if (fav.getYear()          != null) o.addProperty("year",          fav.getYear());
        if (fav.getScore()         != null) o.addProperty("score",         fav.getScore());
        if (fav.getTotalEpisodes() != null) o.addProperty("totalEpisodes", fav.getTotalEpisodes());
        if (fav.getTmdbId()        != null) o.addProperty("tmdbId",        fav.getTmdbId());
        if (fav.getAnilistId()     != null) o.addProperty("anilistId",     fav.getAnilistId());
        if (fav.getIgdbId()        != null) o.addProperty("igdbId",        fav.getIgdbId());
        if (fav.getTrailerKey()    != null) o.addProperty("trailerKey",    fav.getTrailerKey());
        if (fav.getPreviewUrl()    != null) o.addProperty("previewUrl",    fav.getPreviewUrl());
        JsonArray watched = new JsonArray();
        if (fav.getWatchedEpisodes() != null) fav.getWatchedEpisodes().forEach(watched::add);
        o.add("watchedEpisodes", watched);
        if (fav.getPlatforms() != null) { JsonArray a = new JsonArray(); fav.getPlatforms().forEach(a::add); o.add("platforms", a); }
        if (fav.getGenres()   != null) { JsonArray a = new JsonArray(); fav.getGenres().forEach(a::add);    o.add("genres", a); }
        return o;
    }

    // ── Deserialización — todos los campos con null-check (fix NPE) ────
    private static FavoriteItem parseFavoriteItem(JsonObject o) {
        FavoriteItem item = new FavoriteItem();
        item.setId(         safeStr(o, "id",          "sinId_" + System.nanoTime()));
        item.setType(       safeMediaType(o, "type"));
        item.setTitle(      safeStr(o, "title",       "Sin título"));
        item.setDescription(safeStr(o, "description", ""));
        item.setImageUrl(   safeStr(o, "imageUrl",    ""));
        item.setExternalUrl(safeStr(o, "externalUrl", ""));
        item.setTrailerKey( safeStr(o, "trailerKey",  null));
        item.setPreviewUrl( safeStr(o, "previewUrl",  null));
        item.setViewed(   safeBool(o, "viewed",   false));
        item.setAddedDate(safeLong(o, "addedDate", System.currentTimeMillis()));
        safeInt(o, "year"         ).ifPresent(item::setYear);
        safeInt(o, "score"        ).ifPresent(item::setScore);
        safeInt(o, "totalEpisodes").ifPresent(item::setTotalEpisodes);
        safeInt(o, "tmdbId"       ).ifPresent(item::setTmdbId);
        safeInt(o, "anilistId"    ).ifPresent(item::setAnilistId);
        safeInt(o, "igdbId"       ).ifPresent(item::setIgdbId);
        if (has(o,"watchedEpisodes") && o.get("watchedEpisodes").isJsonArray()) {
            Set<Integer> w = new HashSet<>();
            o.getAsJsonArray("watchedEpisodes").forEach(e -> w.add(e.getAsInt()));
            item.setWatchedEpisodes(w);
        }
        if (has(o,"platforms") && o.get("platforms").isJsonArray()) {
            List<String> l = new ArrayList<>(); o.getAsJsonArray("platforms").forEach(e -> l.add(e.getAsString())); item.setPlatforms(l);
        }
        if (has(o,"genres") && o.get("genres").isJsonArray()) {
            List<String> l = new ArrayList<>(); o.getAsJsonArray("genres").forEach(e -> l.add(e.getAsString())); item.setGenres(l);
        }
        return item;
    }

    // ── Helpers null-safe ──────────────────────────────────────────────
    private static boolean has(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull(); }
    private static String safeStr(JsonObject o, String k, String def) {
        if (!has(o,k)) return def; try { return o.get(k).getAsString(); } catch(Exception e){ return def; }
    }
    private static boolean safeBool(JsonObject o, String k, boolean def) {
        if (!has(o,k)) return def; try { return o.get(k).getAsBoolean(); } catch(Exception e){ return def; }
    }
    private static long safeLong(JsonObject o, String k, long def) {
        if (!has(o,k)) return def; try { return o.get(k).getAsLong(); } catch(Exception e){ return def; }
    }
    private static Optional<Integer> safeInt(JsonObject o, String k) {
        if (!has(o,k)) return Optional.empty(); try { return Optional.of(o.get(k).getAsInt()); } catch(Exception e){ return Optional.empty(); }
    }
    private static MediaType safeMediaType(JsonObject o, String k) {
        String raw = safeStr(o, k, null);
        if (raw == null) return MediaType.SERIES;
        try { return MediaType.valueOf(raw); }
        catch (IllegalArgumentException e) { System.err.println("⚠️ MediaType desconocido: '" + raw + "'"); return MediaType.SERIES; }
    }
}
