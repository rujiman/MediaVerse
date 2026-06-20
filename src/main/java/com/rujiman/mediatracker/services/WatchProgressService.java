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
}
