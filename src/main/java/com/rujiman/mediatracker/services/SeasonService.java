package com.rujiman.mediatracker.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Temporadas de las series de TMDB, para mostrar los episodios por
 * temporada (T1, T2...) en vez de todos seguidos.
 *
 * El progreso se sigue guardando con la numeración seguida (1..N): las
 * temporadas solo reparten esos números para enseñarlos. Por ejemplo, con
 * T1 de 10 episodios, el "T2 · E3" es el episodio 13 guardado. Así lo que
 * ya estaba marcado sigue valiendo sin migrar nada.
 *
 * Se guarda en userdata/<usuario>/cache/temporadas.json y se refresca una
 * vez al día (las series en emisión van sumando episodios).
 */
public final class SeasonService {

    public record Season(int number, String name, int episodes) {}

    /** Reparto de la numeración seguida entre temporadas. */
    public static final class Layout {
        private final List<Season> seasons;
        private final int[] starts;

        Layout(List<Season> seasons) {
            this.seasons = List.copyOf(seasons);
            this.starts = new int[seasons.size()];
            int next = 1;
            for (int i = 0; i < seasons.size(); i++) {
                starts[i] = next;
                next += seasons.get(i).episodes();
            }
        }

        public int size()            { return seasons.size(); }
        public Season season(int i)  { return seasons.get(i); }
        /** Primer número (seguido) de la temporada i. */
        public int start(int i)      { return starts[i]; }
        /** Último número (seguido) de la temporada i. */
        public int end(int i)        { return starts[i] + seasons.get(i).episodes() - 1; }
        public int total()           { return end(seasons.size() - 1); }

        /** Índice de la temporada a la que pertenece un número seguido. */
        public int seasonOf(int absolute) {
            for (int i = 0; i < seasons.size(); i++) if (absolute <= end(i)) return i;
            return seasons.size() - 1;
        }

        /** Número dentro de su temporada (el 13 → 3 si T1 tiene 10). */
        public int episodeInSeason(int absolute) {
            return absolute - start(seasonOf(absolute)) + 1;
        }

        /** "T2 · E3". */
        public String label(int absolute) {
            return "T" + seasons.get(seasonOf(absolute)).number() + " · E" + episodeInSeason(absolute);
        }
    }

    private static class Entry {
        long fetchedAt;
        List<Season> seasons = new ArrayList<>();
    }

    private static final long TTL_HOURS = 24;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Entry>>() {}.getType();

    private static final Map<String, Entry> cache = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private SeasonService() {}

    /** Reparto guardado (aunque sea viejo), o null si no hay o solo tiene una temporada. */
    public static synchronized Layout layout(Integer tmdbId) {
        if (tmdbId == null) return null;
        ensureLoaded();
        Entry e = cache.get(String.valueOf(tmdbId));
        if (e == null || e.seasons == null || e.seasons.size() < 2) return null;
        return new Layout(e.seasons);
    }

    /** true si nunca se pidió o el dato tiene más de un día. */
    public static synchronized boolean needsRefresh(Integer tmdbId) {
        if (tmdbId == null) return false;
        ensureLoaded();
        Entry e = cache.get(String.valueOf(tmdbId));
        return e == null || System.currentTimeMillis() - e.fetchedAt > TimeUnit.HOURS.toMillis(TTL_HOURS);
    }

    public static synchronized void store(int tmdbId, List<Season> seasons) {
        if (seasons == null) return;
        ensureLoaded();
        Entry e = new Entry();
        e.fetchedAt = System.currentTimeMillis();
        e.seasons = new ArrayList<>(seasons);
        cache.put(String.valueOf(tmdbId), e);
        save();
    }

    private static Path file() {
        return AppPaths.userFile(AuthService.getCurrentUser(), AppPaths.DIR_CACHE, "temporadas.json");
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
            Map<String, Entry> data = gson.fromJson(Files.readString(f, StandardCharsets.UTF_8), MAP_TYPE);
            if (data != null) cache.putAll(data);
        } catch (Exception e) {
            System.err.println("⚠️ Caché de temporadas ilegible, se vuelve a pedir: " + e.getMessage());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), gson.toJson(cache, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo guardar la caché de temporadas: " + e.getMessage());
        }
    }
}
