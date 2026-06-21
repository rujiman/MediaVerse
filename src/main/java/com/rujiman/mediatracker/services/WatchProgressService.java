package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Servicio para guardar el progreso de visto/jugado/escuchado de un
 * contenido, completamente INDEPENDIENTE de si está o no en favoritos.
 *
 * Se guarda en progress_<usuario>.json, indexado por el título del
 * contenido (misma clave que usa FavoritesService para identificar items).
 */
public class WatchProgressService {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static class Progress {
        public boolean viewed;
        public Integer totalEpisodes;
        public Set<Integer> watchedEpisodes = new HashSet<>();

        // Valoración personal del usuario (1-5 estrellas), totalmente
        // independiente de la puntuación que traen las APIs (TMDB/AniList/
        // IGDB/Deezer). null = sin valorar todavía.
        public Integer userRating;
    }

    private static Map<String, Progress> cache = new HashMap<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private static String getProgressFile() {
        String user = AuthService.getCurrentUser();
        if (user == null || user.isBlank()) {
            return "progress.json";
        }
        return "progress_" + user + ".json";
    }

    private static void ensureCorrectUserLoaded() {
        String currentUser = AuthService.getCurrentUser();
        if (loaded && java.util.Objects.equals(loadedForUser, currentUser)) {
            return;
        }
        cache.clear();
        loaded = false;
        loadProgress();
    }

    private static void loadProgress() {
        if (loaded) return;

        String file = getProgressFile();

        try {
            if (!Files.exists(Paths.get(file))) {
                loaded = true;
                loadedForUser = AuthService.getCurrentUser();
                return;
            }

            String json = new String(Files.readAllBytes(Paths.get(file)));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root != null) {
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    String title = entry.getKey();
                    JsonObject obj = entry.getValue().getAsJsonObject();

                    Progress progress = new Progress();
                    progress.viewed = obj.has("viewed") && obj.get("viewed").getAsBoolean();

                    if (obj.has("totalEpisodes") && !obj.get("totalEpisodes").isJsonNull()) {
                        progress.totalEpisodes = obj.get("totalEpisodes").getAsInt();
                    }

                    if (obj.has("watchedEpisodes") && obj.get("watchedEpisodes").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("watchedEpisodes")) {
                            progress.watchedEpisodes.add(el.getAsInt());
                        }
                    }

                    if (obj.has("userRating") && !obj.get("userRating").isJsonNull()) {
                        progress.userRating = obj.get("userRating").getAsInt();
                    }

                    cache.put(title.toLowerCase(), progress);
                }
            }

            loaded = true;
            loadedForUser = AuthService.getCurrentUser();

        } catch (IOException e) {
            System.err.println("❌ Error al cargar progreso: " + e.getMessage());
            loaded = true;
            loadedForUser = AuthService.getCurrentUser();
        }
    }

    private static void saveProgress() {
        String file = getProgressFile();
        try {
            JsonObject root = new JsonObject();

            for (Map.Entry<String, Progress> entry : cache.entrySet()) {
                Progress p = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("viewed", p.viewed);
                if (p.totalEpisodes != null) {
                    obj.addProperty("totalEpisodes", p.totalEpisodes);
                }
                JsonArray watchedArray = new JsonArray();
                for (Integer ep : p.watchedEpisodes) watchedArray.add(ep);
                obj.add("watchedEpisodes", watchedArray);

                if (p.userRating != null) {
                    obj.addProperty("userRating", p.userRating);
                }

                root.add(entry.getKey(), obj);
            }

            String json = gson.toJson(root);
            Files.write(Paths.get(file), json.getBytes());

        } catch (IOException e) {
            System.err.println("❌ Error al guardar progreso: " + e.getMessage());
        }
    }

    /**
     * Devuelve el progreso guardado para un título, o uno nuevo vacío
     * si no existía todavía (sin persistirlo hasta que se modifique).
     */
    public static Progress getProgress(String title) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        return cache.containsKey(key) ? cache.get(key) : new Progress();
    }

    public static boolean isViewed(String title) {
        return getProgress(title).viewed;
    }

    /**
     * Devuelve la valoración personal (1-5) que el usuario ha dado a este
     * título, o null si todavía no lo ha valorado. Totalmente independiente
     * de la puntuación que viene de las APIs externas.
     */
    public static Integer getUserRating(String title) {
        return getProgress(title).userRating;
    }

    /**
     * Guarda la valoración personal (1-5 estrellas) para un título.
     * Pasar null o 0 quita la valoración.
     */
    public static void setUserRating(String title, Integer rating) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        Progress progress = cache.getOrDefault(key, new Progress());
        progress.userRating = (rating != null && rating > 0) ? rating : null;
        cache.put(key, progress);
        saveProgress();
    }

    /**
     * Alterna el estado visto/jugado de un item simple (sin episodios),
     * de forma totalmente independiente de favoritos.
     */
    public static void toggleViewed(String title) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        Progress progress = cache.getOrDefault(key, new Progress());
        progress.viewed = !progress.viewed;
        cache.put(key, progress);
        saveProgress();
    }

    /**
     * Marca/desmarca un episodio concreto como visto. Si se completan
     * todos los episodios, marca automáticamente el item como visto.
     */
    public static void toggleEpisodeWatched(String title, int episodeNumber, int totalEpisodes) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        Progress progress = cache.getOrDefault(key, new Progress());
        progress.totalEpisodes = totalEpisodes;

        if (progress.watchedEpisodes.contains(episodeNumber)) {
            progress.watchedEpisodes.remove(episodeNumber);
        } else {
            progress.watchedEpisodes.add(episodeNumber);
        }

        progress.viewed = progress.watchedEpisodes.size() >= totalEpisodes;

        cache.put(key, progress);
        saveProgress();
    }

    /**
     * Marca todos los episodios (1..totalEpisodes) como vistos de golpe.
     * Útil para series largas que ya viste antes de usar la app
     * (ej. Naruto, One Piece), sin tener que marcarlos uno a uno.
     */
    public static void markAllEpisodesWatched(String title, int totalEpisodes) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        Progress progress = cache.getOrDefault(key, new Progress());
        progress.totalEpisodes = totalEpisodes;

        progress.watchedEpisodes.clear();
        for (int i = 1; i <= totalEpisodes; i++) {
            progress.watchedEpisodes.add(i);
        }
        progress.viewed = true;

        cache.put(key, progress);
        saveProgress();
    }

    /**
     * Desmarca todos los episodios de golpe (vuelve a 0/total), por si
     * el usuario se equivocó o quiere reiniciar el seguimiento.
     */
    public static void unmarkAllEpisodesWatched(String title, int totalEpisodes) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        Progress progress = cache.getOrDefault(key, new Progress());
        progress.totalEpisodes = totalEpisodes;

        progress.watchedEpisodes.clear();
        progress.viewed = false;

        cache.put(key, progress);
        saveProgress();
    }

    /**
     * Marca como vistos todos los episodios desde el 1 hasta
     * upToEpisodeInclusive (incluido), y desmarca el resto. Útil para
     * decir "voy por el episodio 150 de One Piece" de una sola vez.
     */
    public static void markEpisodesUpTo(String title, int upToEpisodeInclusive, int totalEpisodes) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        Progress progress = cache.getOrDefault(key, new Progress());
        progress.totalEpisodes = totalEpisodes;

        int clamped = Math.max(0, Math.min(upToEpisodeInclusive, totalEpisodes));

        progress.watchedEpisodes.clear();
        for (int i = 1; i <= clamped; i++) {
            progress.watchedEpisodes.add(i);
        }
        progress.viewed = clamped >= totalEpisodes;

        cache.put(key, progress);
        saveProgress();
    }
}