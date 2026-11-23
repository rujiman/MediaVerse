package com.rujiman.mediatracker.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanItem;
import com.rujiman.mediatracker.models.Review;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Logros: el catálogo, cómo se calcula cada uno y cuáles has conseguido.
 *
 * Todo sale de datos que ya existen (progreso, favoritos, reseñas,
 * vitrina, perfil) y del registro de actividad (rachas, maratones,
 * secretos). Un logro conseguido se queda conseguido aunque luego se
 * desmarque algo.
 *
 * check() se lanza tras cada acción (ActivityService) y al volver al
 * Inicio. La primera vez, con todo el historial, desbloquea de golpe lo
 * que ya cumples y avisa con UN solo resumen.
 *
 * Archivo: userdata/<usuario>/logros/logros.json.
 */
public final class AchievementService {

    // ===========================================================
    // CATÁLOGO
    // ===========================================================

    public static final String[] DIFFICULTY = {"", "Fácil", "Normal", "Difícil", "Épico", "Legendario"};

    /** Un logro. metric dice qué número mira; target, cuánto hace falta. */
    public record Achievement(String id, String category, String name, String description,
                              int difficulty, String accent, String icon, String metric, long target,
                              boolean secret, String hint) {}

    public static final String SERIES = "Series y anime", MOVIES = "Películas", GAMES = "Juegos",
            READING = "Lectura", MUSIC = "Música", SOCIAL = "Reseñas y favoritos", STREAKS = "Rachas y maratones",
            EXPLORER = "Explorador", SECRETS = "Secretos";
    public static final String[] CATEGORIES = {SERIES, MOVIES, GAMES, READING, MUSIC, SOCIAL, STREAKS, EXPLORER, SECRETS};

    private static final String C_SERIES = "#8b5cf6", C_MOVIE = "#ecb14d", C_GAME = "#4dd9ec", C_MANGA = "#ef4444",
            C_BOOK = "#f97316", C_MUSIC = "#4dec9e", C_BRAND = "#ec4d80";

    private static final List<Achievement> CATALOG = new ArrayList<>();

    /** Logro con niveles: un nivel por cada año de racha. */
    public static final String INFINITE_STREAK = "streak_infinite";
    private static final int DAYS_PER_LEVEL = 365;

    private static void tiers(String category, String accent, String icon, String metric, String descFormat,
                              long[] targets, String[] names) {
        for (int i = 0; i < targets.length; i++) {
            CATALOG.add(new Achievement(metric + "_" + targets[i], category, names[i],
                    String.format(descFormat, fmt(targets[i])), i + 1, accent, icon, metric, targets[i], false, null));
        }
    }

    private static void single(String id, String category, String name, String desc, int difficulty,
                               String accent, String icon, String metric, long target) {
        CATALOG.add(new Achievement(id, category, name, desc, difficulty, accent, icon, metric, target, false, null));
    }

    private static void secret(String id, String name, String desc, int difficulty, String icon, String hint) {
        CATALOG.add(new Achievement(id, SECRETS, name, desc, difficulty, C_BRAND, icon, id, 1, true, hint));
    }

    static {
        tiers(SERIES, C_SERIES, "TV", "episodes", "Ve %s episodios",
                new long[]{50, 500, 2000, 5000, 10000},
                new String[]{"Enganchado", "Sofá reservado", "Mando gastado", "Vive en la tele", "Leyenda del sofá"});
        tiers(SERIES, C_SERIES, "TV", "shows", "Termina %s series o animes",
                new long[]{1, 10, 50, 250, 1000},
                new String[]{"Primer final", "Cerrando historias", "Cazador de finales", "Biblioteca de series", "Lo ha visto todo"});
        tiers(MOVIES, C_MOVIE, "FILM", "movies", "Ve %s películas",
                new long[]{1, 25, 100, 500, 1000},
                new String[]{"Primera función", "Cinéfilo", "Abono anual", "Fila uno", "Director de culto"});
        tiers(GAMES, C_GAME, "GAMEPAD", "games", "Termina %s juegos",
                new long[]{1, 10, 50, 150, 500},
                new String[]{"Créditos finales", "Jugón", "Mando en mano", "Platino", "Final boss"});
        tiers(READING, C_MANGA, "BOOK", "chapters", "Lee %s capítulos de manga",
                new long[]{100, 1000, 5000, 10000, 25000},
                new String[]{"Página a página", "Tomo tras tomo", "Mangaka honorario", "Estantería infinita", "Leyenda del manga"});
        tiers(READING, C_BOOK, "BOOK", "books", "Lee %s libros",
                new long[]{1, 10, 50, 150, 500},
                new String[]{"Primer libro", "Ratón de biblioteca", "Lector voraz", "Bibliotecario", "Biblioteca de Alejandría"});
        tiers(MUSIC, C_MUSIC, "MUSIC", "music", "Marca %s canciones o álbumes como escuchados",
                new long[]{1, 25, 100, 500, 1000},
                new String[]{"Primer temazo", "Melómano", "Cascos puestos", "Disco de oro", "Disco de platino"});
        tiers(SOCIAL, C_BRAND, "REVIEW", "reviews", "Escribe %s reseñas",
                new long[]{1, 10, 50, 200, 500},
                new String[]{"Primera opinión", "Crítico", "Pluma afilada", "Columnista", "Crítico de culto"});
        tiers(SOCIAL, C_BRAND, "STAR", "favorites", "Guarda %s favoritos",
                new long[]{10, 50, 150, 400, 1000},
                new String[]{"Coleccionista", "Gran colección", "Museo personal", "Archivo nacional", "Todo me gusta"});

        // Rachas: hasta un año, subiendo de dificultad poco a poco
        long[] days = {3, 7, 14, 30, 60, 100, 180};
        int[] diff = {1, 1, 2, 2, 3, 3, 4};
        String[] streakNames = {"Encendido", "Una semana", "Constante", "Un mes sin fallar", "Disciplina",
                "Centenario", "Medio año"};
        for (int i = 0; i < days.length; i++) {
            single("streak_" + days[i], STREAKS, streakNames[i], "Ten actividad " + days[i] + " días seguidos",
                    diff[i], C_BRAND, "FLAME", "streak", days[i]);
        }
        // Sin tope: se consigue con un año seguido y sube un nivel por cada año más
        single(INFINITE_STREAK, STREAKS, "Racha infinita", "Ten actividad un año entero seguido. Cada año más, sube de nivel",
                5, C_BRAND, "FLAME", "streak", 365);
        single("marathon_5", STREAKS, "Maratón", "Ve 5 episodios en un mismo día", 2, C_SERIES, "TV", "marathon", 5);
        single("marathon_10", STREAKS, "Maratoniano", "Ve 10 episodios en un mismo día", 3, C_SERIES, "TV", "marathon", 10);
        single("marathon_25", STREAKS, "Sin dormir", "Ve 25 episodios en un mismo día", 4, C_SERIES, "TV", "marathon", 25);

        single("profile", EXPLORER, "A mi manera", "Pon foto de perfil y portada", 1, C_BRAND, "USER", "profile", 1);
        single("all_types", EXPLORER, "Explorador", "Ten algo de los 7 tipos", 2, C_BRAND, "COMPASS", "typesAny", 7);
        single("showcase_full", EXPLORER, "Vitrina llena", "Completa todas las secciones de la vitrina", 2, C_BRAND, "TROPHY", "showcase", 1);
        single("all_types_done", EXPLORER, "Todoterreno", "Termina algo de los 7 tipos", 3, C_BRAND, "COMPASS", "typesDone", 7);

        secret("night_owl", "Noctámbulo", "Marca algo entre las 2 y las 5 de la madrugada", 2, "MOON", "Hay quien no duerme…");
        secret("early_bird", "Madrugador", "Marca algo entre las 5 y las 7 de la mañana", 2, "SUN", "A quien madruga…");
        secret("premiere_day", "Primero de la fila", "Ve un episodio el mismo día que se estrena", 3, "FLAME", "Nadie llega antes que tú");
        secret("two_versions", "Dos versiones", "Termina el anime y el manga de la misma obra", 3, "BOOK", "Las dos caras de una historia");
        secret("no_mercy", "Sin piedad", "Escribe una reseña de algo que hayas puntuado con 1 estrella", 1, "REVIEW", "No todo merece la pena");
        secret("new_year", "Año nuevo, serie nueva", "Marca algo un 1 de enero", 3, "STAR", "Empieza el año con buen pie");
        secret("up_to_date", "Al día", "Ve todos los episodios emitidos de algo que sigue en emisión", 2, "TV", "Ni un episodio de retraso");
    }

    public static List<Achievement> catalog() { return CATALOG; }

    private static String fmt(long n) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es")).format(n);
    }

    // ===========================================================
    // ESTADO GUARDADO
    // ===========================================================

    private static class State {
        boolean initialized;
        boolean sound = true;
        Map<String, String> unlocked = new LinkedHashMap<>(); // id -> fecha ISO
        List<String> pinned = new ArrayList<>();             // medallas elegidas para el perfil (máx. 3)
        Map<String, Integer> levels = new LinkedHashMap<>(); // nivel alcanzado de los logros con niveles
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static State state = null;
    private static String stateUser = null;
    private static Map<String, Long> lastMetrics = new HashMap<>();

    private static Path file() {
        return AppPaths.userFile(AuthService.getCurrentUser(), AppPaths.DIR_ACHIEVEMENTS, "logros.json");
    }

    private static void ensureLoaded() {
        String user = AuthService.getCurrentUser();
        if (state != null && Objects.equals(stateUser, user)) return;
        stateUser = user;
        state = new State();
        lastMetrics = new HashMap<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                State s = gson.fromJson(Files.readString(f, StandardCharsets.UTF_8), State.class);
                if (s != null) {
                    if (s.unlocked == null) s.unlocked = new LinkedHashMap<>();
                    if (s.pinned == null) s.pinned = new ArrayList<>();
                    if (s.levels == null) s.levels = new LinkedHashMap<>();
                    state = s;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ No se pudieron leer los logros: " + e.getMessage());
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), gson.toJson(state), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudieron guardar los logros: " + e.getMessage());
        }
    }

    public static boolean isUnlocked(Achievement a) {
        ensureLoaded();
        return state.unlocked.containsKey(a.id());
    }

    /** Fecha en que se consiguió, o null. */
    public static LocalDate unlockedOn(Achievement a) {
        ensureLoaded();
        String iso = state.unlocked.get(a.id());
        try { return iso == null ? null : Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate(); }
        catch (Exception e) { return null; }
    }

    /** Valor actual de lo que mide el logro (del último cálculo). */
    public static long progressOf(Achievement a) {
        return lastMetrics.getOrDefault(a.metric(), 0L);
    }

    public static int unlockedCount() {
        ensureLoaded();
        int n = 0;
        for (Achievement a : CATALOG) if (state.unlocked.containsKey(a.id())) n++;
        return n;
    }

    public static boolean soundEnabled() {
        ensureLoaded();
        return state.sound;
    }

    public static void setSoundEnabled(boolean on) {
        ensureLoaded();
        state.sound = on;
        save();
    }

    /** Nivel de un logro con niveles (0 si no tiene o no está conseguido). */
    public static int levelOf(Achievement a) {
        ensureLoaded();
        if (!INFINITE_STREAK.equals(a.id()) || !state.unlocked.containsKey(a.id())) return 0;
        return Math.max(1, state.levels.getOrDefault(a.id(), 1));
    }

    /** Nombre para mostrar: "Racha infinita · Nivel 3" en los logros con niveles. */
    public static String displayName(Achievement a) {
        int level = levelOf(a);
        return level > 0 ? a.name() + " · Nivel " + level : a.name();
    }

    /** Meta actual: en los logros con niveles, el año siguiente. */
    public static long targetOf(Achievement a) {
        int level = levelOf(a);
        return level > 0 ? (long) (level + 1) * DAYS_PER_LEVEL : a.target();
    }

    public static final int MAX_PINNED = 3;

    public static boolean isPinned(Achievement a) {
        ensureLoaded();
        return state.pinned.contains(a.id());
    }

    public static int pinnedCount() {
        ensureLoaded();
        return state.pinned.size();
    }

    /**
     * Fija o quita un logro de las medallas del perfil. Devuelve false si
     * no se puede (no está conseguido o ya hay 3 fijados).
     */
    public static boolean togglePin(Achievement a) {
        ensureLoaded();
        if (state.pinned.remove(a.id())) { save(); return true; }
        if (!state.unlocked.containsKey(a.id()) || state.pinned.size() >= MAX_PINNED) return false;
        state.pinned.add(a.id());
        save();
        return true;
    }

    /** Medallas del perfil: las fijadas, o si no hay ninguna, las 3 más difíciles. */
    public static List<Achievement> featured() {
        ensureLoaded();
        List<Achievement> list = new ArrayList<>();
        for (String id : state.pinned) {
            for (Achievement a : CATALOG) if (a.id().equals(id) && state.unlocked.containsKey(id)) list.add(a);
        }
        return list.isEmpty() ? hardestUnlocked(MAX_PINNED) : list;
    }

    /** Los n logros conseguidos más difíciles (para la vitrina). */
    public static List<Achievement> hardestUnlocked(int n) {
        ensureLoaded();
        List<Achievement> list = new ArrayList<>();
        for (Achievement a : CATALOG) if (state.unlocked.containsKey(a.id())) list.add(a);
        list.sort((x, y) -> {
            if (x.difficulty() != y.difficulty()) return y.difficulty() - x.difficulty();
            return state.unlocked.get(y.id()).compareTo(state.unlocked.get(x.id())); // más reciente primero
        });
        return list.size() > n ? list.subList(0, n) : list;
    }

    // ===========================================================
    // COMPROBAR
    // ===========================================================

    /** Quién recibe los logros recién conseguidos (la ventana principal, para el aviso). */
    public interface Listener {
        /** summary=true: primera vez, solo se avisa del número total. */
        void unlocked(List<Achievement> fresh, boolean summary);
    }

    private static Listener listener;
    private static boolean checkPending = false;

    public static void setListener(Listener l) { listener = l; }

    /**
     * Pide una comprobación en el hilo de JavaFX. Varias acciones seguidas
     * (marcar un episodio también puede terminar la serie) se juntan en una.
     */
    public static void requestCheck() {
        if (checkPending) return;
        checkPending = true;
        try {
            javafx.application.Platform.runLater(() -> {
                checkPending = false;
                check();
            });
        } catch (IllegalStateException notFx) {
            checkPending = false; // sin JavaFX arrancado (pruebas): se comprueba al momento
            check();
        }
    }

    /** Recalcula todo y desbloquea lo nuevo. Llamar en el hilo de JavaFX. */
    public static List<Achievement> check() {
        ensureLoaded();
        if (AuthService.getCurrentUser() == null) return List.of();
        Map<String, Long> m;
        try {
            m = computeMetrics();
        } catch (Exception e) {
            System.err.println("⚠️ No se pudieron calcular los logros: " + e.getMessage());
            return List.of();
        }
        lastMetrics = m;

        String now = Instant.now().toString();
        List<Achievement> fresh = new ArrayList<>();
        for (Achievement a : CATALOG) {
            if (state.unlocked.containsKey(a.id())) continue;
            if (m.getOrDefault(a.metric(), 0L) >= a.target()) {
                state.unlocked.put(a.id(), now);
                fresh.add(a);
            }
        }
        // Racha infinita: un nivel por año. Cada subida se avisa como un logro más
        boolean levelsChanged = false;
        for (Achievement a : CATALOG) {
            if (!INFINITE_STREAK.equals(a.id()) || !state.unlocked.containsKey(a.id())) continue;
            int reached = (int) Math.max(1, m.getOrDefault(a.metric(), 0L) / DAYS_PER_LEVEL);
            int stored = state.levels.getOrDefault(a.id(), 0);
            if (reached > stored) {
                state.levels.put(a.id(), reached);
                levelsChanged = true;
                if (stored > 0) {
                    fresh.add(new Achievement(a.id(), a.category(), a.name() + " · Nivel " + reached,
                            reached + " años seguidos con actividad", a.difficulty(), a.accent(), a.icon(),
                            a.metric(), a.target(), false, null));
                }
            }
        }
        boolean first = !state.initialized;
        if (first) state.initialized = true;
        if (!fresh.isEmpty() || first || levelsChanged) save();
        if (!fresh.isEmpty() && listener != null) listener.unlocked(fresh, first);
        return fresh;
    }

    // ===========================================================
    // MÉTRICAS
    // ===========================================================

    private static Map<String, Long> computeMetrics() {
        Map<String, Long> m = new HashMap<>();

        // Tipo de cada título por su clave, para el progreso antiguo sin ficha
        Map<String, MediaType> typeByKey = new HashMap<>();
        for (FavoriteItem f : FavoritesService.getFavorites()) {
            if (f.getType() != null && f.getTitle() != null) {
                typeByKey.putIfAbsent(WatchProgressService.keyFor(f.getType(), f.getTitle()).toLowerCase(), f.getType());
            }
        }
        for (PlanService.ListKind kind : PlanService.ListKind.values()) {
            for (PlanItem p : PlanService.getItems(kind)) {
                if (p.getType() != null && p.getTitle() != null) {
                    typeByKey.putIfAbsent(WatchProgressService.keyFor(p.getType(), p.getTitle()).toLowerCase(), p.getType());
                }
            }
        }

        long episodes = 0, chapters = 0, shows = 0, movies = 0, games = 0, books = 0, music = 0;
        Set<MediaType> anyType = EnumSet.noneOf(MediaType.class);
        Set<MediaType> doneType = EnumSet.noneOf(MediaType.class);
        Set<String> doneAnime = new java.util.HashSet<>(), doneManga = new java.util.HashSet<>();
        boolean upToDate = false;
        Map<String, WatchProgressService.Progress> progress = WatchProgressService.snapshot();

        for (Map.Entry<String, WatchProgressService.Progress> e : progress.entrySet()) {
            String key = e.getKey();
            WatchProgressService.Progress p = e.getValue();
            MediaType type = p.item != null && p.item.getType() != null ? p.item.getType()
                    : key.startsWith("manga::") ? MediaType.MANGA
                    : key.startsWith("book::") ? MediaType.BOOK
                    : typeByKey.get(key);
            int watched = p.watchedEpisodes != null ? p.watchedEpisodes.size() : 0;
            if (type == null && watched > 0) type = MediaType.SERIES; // con episodios: serie o anime
            if (type == null) continue;

            if (type == MediaType.MANGA) chapters += watched; else episodes += watched;
            if (watched > 0 || p.viewed || p.inProgress) anyType.add(type);
            if (p.viewed) {
                doneType.add(type);
                switch (type) {
                    case SERIES, ANIME -> shows++;
                    case MOVIE -> movies++;
                    case GAME -> games++;
                    case BOOK -> books++;
                    case MUSIC -> music++;
                    default -> { }
                }
                String bare = key.contains("::") ? key.substring(key.indexOf("::") + 2) : key;
                if (type == MediaType.ANIME) doneAnime.add(bare);
                if (type == MediaType.MANGA) doneManga.add(bare);
            }
            if (!upToDate && (type == MediaType.SERIES || type == MediaType.ANIME)) upToDate = isUpToDate(key, p);
        }
        for (FavoriteItem f : FavoritesService.getFavorites()) if (f.getType() != null) anyType.add(f.getType());

        m.put("episodes", episodes);
        m.put("chapters", chapters);
        m.put("shows", shows);
        m.put("movies", movies);
        m.put("games", games);
        m.put("books", books);
        m.put("music", music);
        m.put("reviews", (long) ReviewService.count());
        m.put("favorites", (long) FavoritesService.getFavorites().size());
        m.put("typesAny", (long) anyType.size());
        m.put("typesDone", (long) doneType.size());
        m.put("profile", profileCustomized() ? 1L : 0L);
        m.put("showcase", showcaseFull() ? 1L : 0L);

        // Rachas y maratones: solo actividad en directo
        m.put("streak", (long) ActivityService.streaks()[1]);
        Map<LocalDate, Long> episodesPerDay = new HashMap<>();
        boolean night = false, early = false, premiere = false, newYear = false;
        for (ActivityService.Event ev : ActivityService.all()) {
            if (!ev.live) continue;
            int hour = ev.time().getHour();
            boolean marks = ActivityService.EPISODE.equals(ev.a) || ActivityService.DONE.equals(ev.a);
            if (marks && hour >= 2 && hour < 5) night = true;
            if (marks && hour >= 5 && hour < 7) early = true;
            if (ev.premiere) premiere = true;
            if (ev.day().getMonthValue() == 1 && ev.day().getDayOfMonth() == 1) newYear = true;
            if (ActivityService.EPISODE.equals(ev.a) && !ev.isManga()) episodesPerDay.merge(ev.day(), (long) ev.n, Long::sum);
        }
        m.put("marathon", episodesPerDay.values().stream().mapToLong(Long::longValue).max().orElse(0));

        // Secretos
        m.put("night_owl", night ? 1L : 0L);
        m.put("early_bird", early ? 1L : 0L);
        m.put("premiere_day", premiere ? 1L : 0L);
        m.put("new_year", newYear ? 1L : 0L);
        doneAnime.retainAll(doneManga);
        m.put("two_versions", doneAnime.isEmpty() ? 0L : 1L);
        m.put("no_mercy", hasOneStarReview() ? 1L : 0L);
        m.put("up_to_date", upToDate ? 1L : 0L);
        return m;
    }

    /** Foto y portada (imagen o color) puestas. */
    private static boolean profileCustomized() {
        String user = AuthService.getCurrentUser();
        if (user == null) return false;
        ProfileService.Profile p = ProfileService.loadProfile(user);
        return p != null && p.photoPath != null && !p.photoPath.isBlank()
                && ((p.bannerUrl != null && !p.bannerUrl.isBlank()) || (p.bannerColor != null && !p.bannerColor.isBlank()));
    }

    /** Todas las secciones de la vitrina llenas hasta su máximo. */
    private static boolean showcaseFull() {
        for (MediaType t : MediaType.values()) {
            int max = DashboardService.getMaxItemsForSection(t);
            if (max > 0 && DashboardService.getSectionItems(t).size() < max) return false;
        }
        return true;
    }

    private static boolean hasOneStarReview() {
        for (Review r : ReviewService.all()) {
            try {
                MediaType t = MediaType.valueOf(r.type);
                Integer stars = WatchProgressService.getUserRating(WatchProgressService.keyFor(t, r.title));
                if (stars != null && stars == 1) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    /**
     * Al día: el título tiene un próximo episodio anunciado y ya has visto
     * todos los anteriores. "Episodio 14" (AniList) → del 1 al 13;
     * "T2 · E5" (TMDB) → hasta el 4 de la T2, usando las temporadas.
     */
    private static boolean isUpToDate(String key, WatchProgressService.Progress p) {
        String[] next = ReleaseService.nextKnown(key);
        if (next == null || next[1] == null || p.watchedEpisodes == null || p.watchedEpisodes.isEmpty()) return false;
        int aired = -1;
        java.util.regex.Matcher anime = java.util.regex.Pattern.compile("^Episodio (\\d+)").matcher(next[1]);
        java.util.regex.Matcher series = java.util.regex.Pattern.compile("^T(\\d+) · E(\\d+)").matcher(next[1]);
        if (anime.find()) {
            aired = Integer.parseInt(anime.group(1)) - 1;
        } else if (series.find() && p.item != null && p.item.getTmdbId() != null) {
            int season = Integer.parseInt(series.group(1)), ep = Integer.parseInt(series.group(2));
            SeasonService.Layout layout = SeasonService.layout(p.item.getTmdbId());
            if (layout != null) {
                for (int i = 0; i < layout.size(); i++) {
                    if (layout.season(i).number() == season) { aired = layout.start(i) + ep - 2; break; }
                }
            } else if (season == 1) {
                aired = ep - 1;
            }
        }
        if (aired < 1) return false;
        for (int i = 1; i <= aired; i++) if (!p.watchedEpisodes.contains(i)) return false;
        return true;
    }

    /** Para quien quiera escuchar los recién conseguidos sin ser la ventana principal. */
    public static void forEachUnlocked(Consumer<Achievement> action) {
        for (Achievement a : CATALOG) if (isUnlocked(a)) action.accept(a);
    }
}
