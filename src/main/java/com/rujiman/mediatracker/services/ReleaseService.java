package com.rujiman.mediatracker.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanItem;
import com.rujiman.mediatracker.util.ConfigLoader;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Calendario de estrenos: próximos episodios y lanzamientos de lo que
 * sigue el usuario.
 *
 * Qué entra: series y anime con progreso o en favoritos, y películas,
 * series, anime y juegos que estén en favoritos o en Pendiente.
 *
 * Las fechas se piden a TMDB, AniList e IGDB y se guardan en
 * userdata/<usuario>/cache/estrenos.json. Cada título se vuelve a
 * consultar solo cuando su dato caduca, así la pantalla abre al momento
 * y funciona sin conexión con lo último que se supo.
 *
 * Hilos: collectCandidates() lee favoritos, pendientes y progreso y se
 * llama en el hilo de JavaFX. load() hace red y se llama SIEMPRE fuera
 * de él; es synchronized para que dos pantallas a la vez no repitan
 * las mismas consultas (la segunda espera y usa la caché).
 */
public final class ReleaseService {

    /** Un estreno para mostrar. date == null significa "sin fecha todavía". */
    public record Release(String key, MediaItem item, LocalDate date, Long airingAt, String what, String note) {
        public boolean hasDate() { return date != null; }
    }

    /** Dato guardado en caché por título. */
    private static class Entry {
        long fetchedAt;      // epoch ms
        long ttlHours;       // cuánto vale este dato
        String date;         // yyyy-MM-dd o null
        Long airingAt;       // segundos Unix (hora exacta, AniList) o null
        String what;         // "T2 · E5 · Nombre", "Episodio 14", "Estreno"...
        String note;         // si no hay fecha: "Nueva temporada en producción"...
        boolean nothing;     // no hay nada próximo que mostrar
    }

    private static final long TTL_UPCOMING = 6;          // horas: algo con fecha cercana o sin confirmar
    private static final long TTL_QUIET = 24 * 3;        // series terminadas, juegos ya publicados...
    private static final int THREADS = 4;
    private static final long FETCH_TIMEOUT_SECONDS = 40;

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Entry>>() {}.getType();

    private static final Map<String, Entry> cache = new LinkedHashMap<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private ReleaseService() {}

    // ===========================================================
    // QUÉ SIGUE EL USUARIO (hilo de JavaFX)
    // ===========================================================

    /** Títulos que pueden tener estrenos, por clave. Llamar en el hilo de JavaFX. */
    public static Map<String, MediaItem> collectCandidates() {
        Map<String, MediaItem> out = new LinkedHashMap<>();

        for (MediaType t : new MediaType[]{MediaType.SERIES, MediaType.ANIME}) {
            for (WatchProgressService.Tracked tr : WatchProgressService.getTracked(t)) addCandidate(out, tr.item());
        }
        for (MediaType t : new MediaType[]{MediaType.SERIES, MediaType.ANIME, MediaType.MOVIE, MediaType.GAME}) {
            for (FavoriteItem fav : FavoritesService.getFavoritesByType(t)) addCandidate(out, fav.toMediaItem());
        }
        for (PlanService.ListKind kind : new PlanService.ListKind[]{PlanService.ListKind.WATCH, PlanService.ListKind.PLAY}) {
            for (PlanItem p : PlanService.getItems(kind)) addCandidate(out, p.toMediaItem());
        }
        return out;
    }

    private static void addCandidate(Map<String, MediaItem> out, MediaItem item) {
        if (item == null || item.getType() == null || item.getTitle() == null) return;
        boolean hasId = switch (item.getType()) {
            case SERIES, MOVIE -> item.getTmdbId() != null;
            case ANIME -> item.getAnilistId() != null;
            case GAME -> item.getIgdbId() != null;
            default -> false;
        };
        if (!hasId) return;
        out.putIfAbsent(WatchProgressService.keyFor(item.getType(), item.getTitle()).toLowerCase(), item);
    }

    // ===========================================================
    // CARGA (fuera del hilo de JavaFX)
    // ===========================================================

    /**
     * Devuelve los estrenos ordenados: primero los que tienen fecha (del más
     * cercano al más lejano) y al final los que aún no la tienen. Consulta
     * solo lo que no está en caché o ha caducado; con force, todo.
     */
    public static synchronized List<Release> load(Map<String, MediaItem> candidates, boolean force) {
        ensureLoaded();
        long now = System.currentTimeMillis();

        Map<String, MediaItem> toFetch = new LinkedHashMap<>();
        for (Map.Entry<String, MediaItem> c : candidates.entrySet()) {
            Entry e = cache.get(c.getKey());
            if (force || e == null || isStale(e, now)) toFetch.put(c.getKey(), c.getValue());
        }

        if (!toFetch.isEmpty()) {
            cache.putAll(fetch(toFetch));
            // Se olvida lo que ya no se sigue
            cache.keySet().retainAll(candidates.keySet());
            save();
        }

        List<Release> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Map.Entry<String, MediaItem> c : candidates.entrySet()) {
            Entry e = cache.get(c.getKey());
            if (e == null || e.nothing) continue;
            LocalDate date = parseDate(e.date);
            if (date != null && date.isBefore(today)) continue; // ya salió
            result.add(new Release(c.getKey(), c.getValue(), date, e.airingAt, e.what, e.note));
        }
        result.sort(Comparator
                .comparing((Release r) -> r.date() == null)               // sin fecha al final
                .thenComparing(r -> r.date() == null ? LocalDate.MAX : r.date())
                .thenComparing(r -> r.airingAt() == null ? Long.MAX_VALUE : r.airingAt())
                .thenComparing(r -> r.item().getTitle(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Caduca por tiempo, o porque la fecha que tenía ya ha pasado (habrá un episodio nuevo). */
    private static boolean isStale(Entry e, long now) {
        if (now - e.fetchedAt > TimeUnit.HOURS.toMillis(e.ttlHours)) return true;
        if (e.airingAt != null && e.airingAt * 1000 < now) return true;
        LocalDate d = parseDate(e.date);
        return d != null && d.isBefore(LocalDate.now()) && e.ttlHours <= TTL_UPCOMING;
    }

    /**
     * Pide las fechas en paralelo con un pool pequeño (4 hilos) que se
     * cierra al terminar. Si algo no responde a tiempo se cancela; los
     * títulos que fallan conservan su dato anterior (si lo había) y se
     * reintentan la próxima vez.
     */
    private static Map<String, Entry> fetch(Map<String, MediaItem> toFetch) {
        List<Callable<Map<String, Entry>>> jobs = new ArrayList<>();
        Map<Integer, String> animeKeys = new LinkedHashMap<>();
        Map<Integer, String> gameKeys = new LinkedHashMap<>();
        boolean tmdb = ConfigLoader.hasTmdb();

        for (Map.Entry<String, MediaItem> c : toFetch.entrySet()) {
            MediaItem item = c.getValue();
            String key = c.getKey();
            switch (item.getType()) {
                case SERIES -> { if (tmdb) jobs.add(() -> single(key, seriesEntry(item.getTmdbId()))); }
                case MOVIE -> { if (tmdb) jobs.add(() -> single(key, movieEntry(item.getTmdbId()))); }
                case ANIME -> animeKeys.put(item.getAnilistId(), key);
                case GAME -> gameKeys.put(item.getIgdbId(), key);
                default -> { }
            }
        }
        if (!animeKeys.isEmpty()) jobs.add(() -> animeEntries(animeKeys));
        if (!gameKeys.isEmpty() && ConfigLoader.hasIgdb()) jobs.add(() -> gameEntries(gameKeys));

        Map<String, Entry> result = new HashMap<>();
        if (jobs.isEmpty()) return result;

        ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "estrenos");
            t.setDaemon(true);
            return t;
        });
        try {
            for (Future<Map<String, Entry>> f : pool.invokeAll(jobs, FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                if (f.isCancelled()) continue;
                try {
                    Map<String, Entry> part = f.get();
                    if (part != null) result.putAll(part);
                } catch (Exception e) {
                    System.err.println("⚠️ Estrenos: una consulta falló: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return result;
    }

    private static Map<String, Entry> single(String key, Entry e) {
        if (e == null) return null; // falló: no se guarda
        Map<String, Entry> m = new HashMap<>();
        m.put(key, e);
        return m;
    }

    // ── Series (TMDB) ──────────────────────────────────────────────
    private static Entry seriesEntry(int tmdbId) {
        TMDBService.NextEpisode next = new TMDBService().getNextEpisode(tmdbId);
        if (next == null) return null;
        Entry e = newEntry(TTL_UPCOMING);
        if (next.airDate() != null) {
            e.date = next.airDate();
            String name = next.name() == null ? "" : next.name().trim();
            boolean genericName = name.isEmpty() || name.matches("(?i)(episodio|episode)\\s*\\d+");
            e.what = "T" + next.season() + " · E" + next.episode() + (genericName ? "" : " · " + name);
        } else if ("In Production".equals(next.status()) || "Planned".equals(next.status())) {
            e.note = "Nueva temporada en producción";
        } else {
            e.nothing = true;
            if ("Ended".equals(next.status()) || "Canceled".equals(next.status())) e.ttlHours = TTL_QUIET;
        }
        return e;
    }

    // ── Películas (TMDB) ───────────────────────────────────────────
    private static Entry movieEntry(int tmdbId) {
        TMDBService.MovieRelease rel = new TMDBService().getMovieRelease(tmdbId);
        if (rel == null) return null;
        Entry e = newEntry(TTL_UPCOMING);
        LocalDate d = parseDate(rel.releaseDate());
        if (d != null && !d.isBefore(LocalDate.now())) {
            e.date = rel.releaseDate();
            e.what = "Estreno";
        } else if (d == null && !"Released".equals(rel.status())) {
            e.note = "Estreno sin fecha";
        } else {
            e.nothing = true;
            e.ttlHours = TTL_QUIET;
        }
        return e;
    }

    // ── Anime (AniList, todos en una petición) ─────────────────────
    private static Map<String, Entry> animeEntries(Map<Integer, String> keysById) {
        Map<Integer, AnilistService.Airing> airing = new AnilistService().getAiring(new ArrayList<>(keysById.keySet()));
        if (airing == null) return null;
        Map<String, Entry> out = new HashMap<>();
        for (Map.Entry<Integer, String> k : keysById.entrySet()) {
            AnilistService.Airing a = airing.get(k.getKey());
            Entry e = newEntry(TTL_UPCOMING);
            if (a != null && a.airingAt() != null) {
                e.airingAt = a.airingAt();
                e.date = Instant.ofEpochSecond(a.airingAt()).atZone(ZoneId.systemDefault()).toLocalDate().toString();
                e.what = a.episode() != null ? "Episodio " + a.episode() : "Próximo episodio";
            } else if (a != null && "NOT_YET_RELEASED".equals(a.status())) {
                if (a.startYear() != null && a.startMonth() != null && a.startDay() != null) {
                    e.date = LocalDate.of(a.startYear(), a.startMonth(), a.startDay()).toString();
                    e.what = "Estreno";
                } else if (a.startYear() != null && a.startMonth() != null) {
                    e.note = "Previsto para " + monthName(a.startMonth()) + " de " + a.startYear();
                } else if (a.startYear() != null) {
                    e.note = "Previsto para " + a.startYear();
                } else {
                    e.note = "Estreno sin fecha";
                }
            } else {
                e.nothing = true;
                if (a != null && ("FINISHED".equals(a.status()) || "CANCELLED".equals(a.status()))) e.ttlHours = TTL_QUIET;
            }
            out.put(k.getValue(), e);
        }
        return out;
    }

    // ── Juegos (IGDB, todos en una petición) ───────────────────────
    private static Map<String, Entry> gameEntries(Map<Integer, String> keysById) {
        Map<Integer, Long> dates = new GameService().getReleaseDates(new ArrayList<>(keysById.keySet()));
        if (dates == null) return null;
        Map<String, Entry> out = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (Map.Entry<Integer, String> k : keysById.entrySet()) {
            Long ts = dates.get(k.getKey());
            Entry e = newEntry(TTL_UPCOMING);
            if (ts == null) {
                e.note = "Lanzamiento sin fecha";
            } else {
                LocalDate d = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate();
                if (!d.isBefore(today)) {
                    e.date = d.toString();
                    e.what = "Lanzamiento";
                } else {
                    e.nothing = true;
                    e.ttlHours = TTL_QUIET;
                }
            }
            out.put(k.getValue(), e);
        }
        return out;
    }

    private static Entry newEntry(long ttlHours) {
        Entry e = new Entry();
        e.fetchedAt = System.currentTimeMillis();
        e.ttlHours = ttlHours;
        return e;
    }

    // ===========================================================
    // ESTA SEMANA Y AVISO DE NOVEDADES
    // ===========================================================

    /** Los estrenos de los próximos 7 días (hoy, mañana y esta semana). */
    public static List<Release> thisWeek(List<Release> all) {
        List<Release> week = new ArrayList<>();
        for (Release r : all) {
            String g = group(r);
            if (g.equals("Hoy") || g.equals("Mañana") || g.equals("Esta semana")) week.add(r);
        }
        return week;
    }

    /**
     * Identifica un estreno concreto: título + fecha + qué sale. Si se
     * anuncia el episodio siguiente, cambia y vuelve a contar como nuevo.
     */
    private static String idOf(Release r) {
        return r.key() + "|" + r.date() + "|" + r.what();
    }

    private static java.util.Set<String> seen = null;
    private static String seenForUser = null;

    /** Cuántos estrenos de la semana no se han visto aún en la pantalla Estrenos. */
    public static synchronized int unseenCount(List<Release> week) {
        loadSeen();
        int n = 0;
        for (Release r : week) if (!seen.contains(idOf(r))) n++;
        return n;
    }

    /** Se llama al abrir Estrenos: todo lo de esta semana pasa a visto. */
    public static synchronized void markSeen(List<Release> week) {
        loadSeen();
        java.util.Set<String> now = new java.util.HashSet<>();
        for (Release r : week) now.add(idOf(r));
        if (now.equals(seen)) return;
        seen = now; // solo se guarda lo de esta semana: el archivo no crece
        try {
            Files.writeString(seenFile(), gson.toJson(seen), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudieron guardar los estrenos vistos: " + e.getMessage());
        }
    }

    private static void loadSeen() {
        String user = AuthService.getCurrentUser();
        if (seen != null && Objects.equals(seenForUser, user)) return;
        seen = new java.util.HashSet<>();
        seenForUser = user;
        try {
            Path f = seenFile();
            if (Files.exists(f)) {
                java.util.Set<String> data = gson.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<java.util.HashSet<String>>() {}.getType());
                if (data != null) seen.addAll(data);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Estrenos vistos ilegibles: " + e.getMessage());
        }
    }

    private static Path seenFile() {
        return AppPaths.userFile(AuthService.getCurrentUser(), AppPaths.DIR_CACHE, "estrenos_vistos.json");
    }

    // ===========================================================
    // CACHÉ EN DISCO
    // ===========================================================

    private static Path file() {
        return AppPaths.userFile(AuthService.getCurrentUser(), AppPaths.DIR_CACHE, "estrenos.json");
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
            System.err.println("⚠️ Caché de estrenos ilegible, se vuelve a pedir: " + e.getMessage());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), gson.toJson(cache, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo guardar la caché de estrenos: " + e.getMessage());
        }
    }

    // ===========================================================
    // TEXTOS
    // ===========================================================

    private static final java.time.format.DateTimeFormatter DAY =
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.forLanguageTag("es"));
    private static final java.time.format.DateTimeFormatter DAY_YEAR =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es"));
    private static final java.time.format.DateTimeFormatter HOUR =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    /** "Hoy · 17:00", "Mañana", "vie 28 nov", "26 may 2027". */
    public static String whenText(Release r) {
        if (r.date() == null) return "";
        LocalDate today = LocalDate.now();
        String day;
        if (r.date().equals(today)) day = "Hoy";
        else if (r.date().equals(today.plusDays(1))) day = "Mañana";
        else if (r.date().getYear() == today.getYear() || r.date().isBefore(today.plusMonths(6))) day = DAY.format(r.date());
        else day = DAY_YEAR.format(r.date());
        if (r.airingAt() != null) {
            day += " · " + HOUR.format(Instant.ofEpochSecond(r.airingAt()).atZone(ZoneId.systemDefault()));
        }
        return Character.toUpperCase(day.charAt(0)) + day.substring(1);
    }

    /** "hoy", "mañana" o el día corto ("vie") para las etiquetas pequeñas. */
    public static String shortDay(Release r) {
        if (r.date() == null) return "";
        LocalDate today = LocalDate.now();
        if (!r.date().isAfter(today)) return "hoy";
        if (r.date().equals(today.plusDays(1))) return "mañana";
        return java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.forLanguageTag("es")).format(r.date())
                .replace(".", "");
    }

    /** "hoy", "mañana", "en 3 días", "en 5 semanas", "en 4 meses". */
    public static String countdown(Release r) {
        if (r.date() == null) return "";
        long days = Duration.between(LocalDate.now().atStartOfDay(), r.date().atStartOfDay()).toDays();
        if (days <= 0) return "hoy";
        if (days == 1) return "mañana";
        if (days < 14) return "en " + days + " días";
        if (days < 60) return "en " + (days / 7) + " semanas";
        long months = Math.round(days / 30.4);
        return months <= 1 ? "en un mes" : "en " + months + " meses";
    }

    /** Grupo en el que va cada estreno en la lista. */
    public static String group(Release r) {
        if (r.date() == null) return "Sin fecha todavía";
        long days = Duration.between(LocalDate.now().atStartOfDay(), r.date().atStartOfDay()).toDays();
        if (days <= 0) return "Hoy";
        if (days == 1) return "Mañana";
        if (days < 7) return "Esta semana";
        if (days < 31) return "Próximas semanas";
        return "Más adelante";
    }

    private static String monthName(int month) {
        return java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es"));
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }
}
