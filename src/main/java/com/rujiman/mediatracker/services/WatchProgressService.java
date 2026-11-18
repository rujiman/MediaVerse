package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;

import java.io.IOException;
import java.nio.file.Files;
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

        // Mini ficha del título (portada, tipo, IDs...), guardada junto al
        // progreso para que las listas "viendo/leyendo/vistos/leídos"
        // puedan mostrar también lo que NO está en favoritos. null en
        // progresos antiguos hasta que se vuelva a abrir su detalle.
        public MediaItem item;

        // "Lo estoy jugando / viendo / escuchando / leyendo ahora": para lo
        // que no se sigue por episodios (juegos, pelis, música, libros...)
        // y así también puede estar "En curso". Se quita al terminarlo.
        public boolean inProgress;
    }

    /** Un título con progreso y su ficha guardada. */
    public record Tracked(MediaItem item, Progress progress) {}

    private static Map<String, Progress> cache = new HashMap<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private static java.nio.file.Path getProgressFile() {
        String user = AuthService.getCurrentUser();
        return AppPaths.userFile(user, AppPaths.DIR_PROGRESS, "progress.json");
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

        java.nio.file.Path file = getProgressFile();

        try {
            if (!Files.exists(file)) {
                loaded = true;
                loadedForUser = AuthService.getCurrentUser();
                return;
            }

            String json = new String(Files.readAllBytes(file));
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

                    progress.inProgress = obj.has("inProgress") && !obj.get("inProgress").isJsonNull()
                            && obj.get("inProgress").getAsBoolean();

                    // Ficha opcional: si está corrupta se ignora, el progreso sigue valiendo
                    if (obj.has("item") && obj.get("item").isJsonObject()) {
                        try {
                            progress.item = gson.fromJson(obj.get("item"), MediaItem.class);
                        } catch (Exception e) {
                            System.err.println("⚠️ Ficha de progreso corrupta ignorada (" + title + "): " + e.getMessage());
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
        java.nio.file.Path file = getProgressFile();
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

                if (p.inProgress) {
                    obj.addProperty("inProgress", true);
                }

                if (p.item != null) {
                    obj.add("item", gson.toJsonTree(p.item));
                }

                root.add(entry.getKey(), obj);
            }

            String json = gson.toJson(root);
            Files.write(file, json.getBytes());

        } catch (IOException e) {
            System.err.println("❌ Error al guardar progreso: " + e.getMessage());
        }
    }

    /**
     * Clave con la que se guarda el progreso de un item.
     *
     * Por compatibilidad con los progress.json existentes, los tipos
     * originales siguen usando solo el título. Libros y manga llevan el
     * tipo delante ("MANGA::Berserk") para no chocar con un anime, serie o
     * película que se llame igual (p. ej. el manga y el anime de
     * "Shingeki no Kyojin", o el libro y la película "Dune").
     */
    public static String keyFor(MediaType type, String title) {
        if (title == null) return "";
        if (type == MediaType.BOOK || type == MediaType.MANGA) return type.name() + "::" + title;
        return title;
    }

    /**
     * Guarda (o refresca) la ficha del item junto a su progreso. Solo hace
     * algo si el item YA tiene progreso guardado: abrir un detalle sin
     * marcar nada no crea entradas nuevas. Se llama tras cada cambio de
     * progreso desde el detalle.
     */
    public static void remember(MediaItem item) {
        if (item == null || item.getTitle() == null || item.getType() == null) return;
        ensureCorrectUserLoaded();
        Progress progress = cache.get(keyFor(item.getType(), item.getTitle()).toLowerCase());
        if (progress == null) return;
        progress.item = snapshotOf(item);
        saveProgress();
    }

    /**
     * Igual que remember(), pero solo si aún no había ficha. Sirve para
     * "rescatar" progresos guardados antes de que existieran las fichas:
     * basta con abrir su detalle una vez.
     */
    public static void rememberIfMissing(MediaItem item) {
        if (item == null || item.getTitle() == null || item.getType() == null) return;
        ensureCorrectUserLoaded();
        Progress progress = cache.get(keyFor(item.getType(), item.getTitle()).toLowerCase());
        if (progress != null && progress.item == null) remember(item);
    }

    /** Todos los títulos de un tipo que tienen progreso y ficha guardada. */
    public static java.util.List<Tracked> getTracked(MediaType type) {
        ensureCorrectUserLoaded();
        java.util.List<Tracked> result = new java.util.ArrayList<>();
        for (Progress progress : cache.values()) {
            if (progress.item != null && progress.item.getType() == type) {
                result.add(new Tracked(progress.item, progress));
            }
        }
        return result;
    }

    /** Copia independiente del item, sin datos efímeros (preview de Deezer caduca). */
    private static MediaItem snapshotOf(MediaItem item) {
        MediaItem copy = gson.fromJson(gson.toJsonTree(item), MediaItem.class);
        copy.setPreviewUrl(null);
        return copy;
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
        if (progress.viewed) progress.inProgress = false; // terminado: deja de estar en curso
        cache.put(key, progress);
        saveProgress();
    }

    /** ¿Está marcado como "lo estoy jugando/viendo/leyendo ahora"? */
    public static boolean isInProgress(String title) {
        ensureCorrectUserLoaded();
        Progress p = cache.get(title.toLowerCase());
        return p != null && p.inProgress;
    }

    /** Marca o quita "lo estoy jugando/viendo/leyendo ahora". */
    public static void setInProgress(String title, boolean value) {
        ensureCorrectUserLoaded();
        String key = title.toLowerCase();
        Progress progress = cache.getOrDefault(key, new Progress());
        progress.inProgress = value;
        if (value) progress.viewed = false; // si vuelves a empezarlo, ya no está terminado
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