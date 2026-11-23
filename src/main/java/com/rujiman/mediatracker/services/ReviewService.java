package com.rujiman.mediatracker.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.Review;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reseñas personales: un texto por título y usuario, guardado en
 * userdata/<usuario>/resenas/reviews.json.
 *
 * Se indexan con la misma clave que el progreso y las estrellas
 * (WatchProgressService.keyFor), así se encuentran desde la ficha, la
 * biblioteca y favoritos. Cada reseña lleva además su workId (ID de la
 * API) para poder compartirla en el futuro sin depender del título.
 */
public final class ReviewService {

    /** Longitud máxima del texto de una reseña. */
    public static final int MAX_LENGTH = 2000;

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Review>>() {}.getType();

    private static final Map<String, Review> cache = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private ReviewService() {}

    private static Path file() {
        return AppPaths.userFile(AuthService.getCurrentUser(), AppPaths.DIR_REVIEWS, "reviews.json");
    }

    private static void ensureLoaded() {
        String user = AuthService.getCurrentUser();
        if (loaded && Objects.equals(loadedForUser, user)) return;
        cache.clear();
        loaded = true;
        loadedForUser = user;
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            Map<String, Review> data = gson.fromJson(Files.readString(f, StandardCharsets.UTF_8), MAP_TYPE);
            if (data != null) cache.putAll(data);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudieron leer las reseñas: " + e.getMessage());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), gson.toJson(cache, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("⚠️ No se pudieron guardar las reseñas: " + e.getMessage());
        }
    }

    private static String key(MediaType type, String title) {
        return WatchProgressService.keyFor(type, title).toLowerCase();
    }

    /** La reseña de este título, o null si no tiene. */
    public static Review get(MediaType type, String title) {
        if (type == null || title == null) return null;
        ensureLoaded();
        return cache.get(key(type, title));
    }

    public static boolean has(MediaType type, String title) {
        return get(type, title) != null;
    }

    /** Guarda o actualiza la reseña. Si el texto queda vacío, la borra. */
    public static void save(MediaItem item, String text, boolean spoilers) {
        if (item == null || item.getType() == null || item.getTitle() == null) return;
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) {
            delete(item.getType(), item.getTitle());
            return;
        }
        if (clean.length() > MAX_LENGTH) clean = clean.substring(0, MAX_LENGTH);

        ensureLoaded();
        String k = key(item.getType(), item.getTitle());
        String now = Instant.now().toString();
        Review r = cache.get(k);
        if (r == null) {
            r = new Review();
            r.createdAt = now;
        }
        fillWorkInfo(r, item);
        r.text = clean;
        r.spoilers = spoilers;
        if (r.visibility == null) r.visibility = Review.PRIVATE;
        boolean isNew = r.createdAt.equals(now);
        r.updatedAt = now;
        cache.put(k, r);
        save();
        if (isNew) ActivityService.live(ActivityService.REVIEW, k);
    }

    /**
     * Todas las reseñas del usuario, de la más reciente a la más antigua.
     * Es lo único que usa la pantalla "Mis reseñas": cuando haya servidor,
     * este método pasará a pedírselas a él.
     */
    public static java.util.List<Review> all() {
        ensureLoaded();
        java.util.List<Review> list = new java.util.ArrayList<>(cache.values());
        list.sort(java.util.Comparator.comparing((Review r) -> r.updatedAt == null ? "" : r.updatedAt).reversed());
        return list;
    }

    public static int count() {
        ensureLoaded();
        return cache.size();
    }

    /**
     * Completa portada, año e ID de una reseña ya escrita (las antiguas no
     * los guardaban). Se llama al abrir la ficha; no cambia la fecha.
     */
    public static void refreshWorkInfo(MediaItem item) {
        if (item == null || item.getType() == null || item.getTitle() == null) return;
        ensureLoaded();
        Review r = cache.get(key(item.getType(), item.getTitle()));
        if (r == null) return;
        String before = r.imageUrl + "|" + r.year + "|" + r.workId;
        fillWorkInfo(r, item);
        if (!before.equals(r.imageUrl + "|" + r.year + "|" + r.workId)) save();
    }

    private static void fillWorkInfo(Review r, MediaItem item) {
        r.type = item.getType().name();
        r.title = item.getTitle();
        String id = workIdOf(item);
        if (id != null) r.workId = id;
        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) r.imageUrl = item.getImageUrl();
        if (item.getYear() != null) r.year = item.getYear();
    }

    /**
     * Ficha de la obra de una reseña, para abrir su detalle. Primero se busca
     * en el progreso y en favoritos (traen todos los datos); si no está,
     * se reconstruye con el workId, que basta para que la ficha cargue el resto.
     */
    public static MediaItem toMediaItem(Review r) {
        MediaType type;
        try { type = MediaType.valueOf(r.type); } catch (Exception e) { return null; }

        String key = key(type, r.title);
        WatchProgressService.Progress p = WatchProgressService.getProgress(key);
        if (p != null && p.item != null) return withReviewInfo(p.item, r);
        for (com.rujiman.mediatracker.models.FavoriteItem fav : FavoritesService.getFavoritesByType(type)) {
            if (fav.getTitle() != null && fav.getTitle().equalsIgnoreCase(r.title)) return withReviewInfo(fav.toMediaItem(), r);
        }

        MediaItem item = new MediaItem();
        item.setType(type);
        item.setTitle(r.title);
        item.setImageUrl(r.imageUrl);
        item.setYear(r.year);
        if (r.workId != null) {
            String[] parts = r.workId.split(":");
            try {
                int id = Integer.parseInt(parts[parts.length - 1]);
                switch (parts[0]) {
                    case "tmdb" -> item.setTmdbId(id);
                    case "anilist" -> item.setAnilistId(id);
                    case "igdb" -> item.setIgdbId(id);
                    default -> { }
                }
            } catch (NumberFormatException ignored) { }
        }
        return item;
    }

    private static MediaItem withReviewInfo(MediaItem item, Review r) {
        if ((item.getImageUrl() == null || item.getImageUrl().isBlank()) && r.imageUrl != null) item.setImageUrl(r.imageUrl);
        return item;
    }

    public static void delete(MediaType type, String title) {
        if (type == null || title == null) return;
        ensureLoaded();
        if (cache.remove(key(type, title)) != null) save();
    }

    /**
     * Identificador estable de la obra según su API. En TMDB las series y
     * las películas comparten numeración, por eso llevan "tv" o "movie".
     * Música y libros todavía no guardan su ID: devuelven null.
     */
    public static String workIdOf(MediaItem item) {
        if (item == null || item.getType() == null) return null;
        return switch (item.getType()) {
            case SERIES -> item.getTmdbId() != null ? "tmdb:tv:" + item.getTmdbId() : null;
            case MOVIE -> item.getTmdbId() != null ? "tmdb:movie:" + item.getTmdbId() : null;
            case ANIME, MANGA -> item.getAnilistId() != null ? "anilist:" + item.getAnilistId() : null;
            case GAME -> item.getIgdbId() != null ? "igdb:" + item.getIgdbId() : null;
            default -> null;
        };
    }
}
