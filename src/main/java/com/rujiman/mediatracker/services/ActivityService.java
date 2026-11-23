package com.rujiman.mediatracker.services;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Registro de actividad: CUÁNDO hace el usuario cada cosa. Es la base de
 * las rachas, los maratones, algunos logros secretos y, en el futuro, del
 * Wrapped anual.
 *
 * Cada acción se guarda con su fecha y si es "en directo" (lo acabas de
 * ver) o "historial" (te pones al día con algo antiguo: "Marcar temporada",
 * "Ya he visto hasta..."). Las rachas y los maratones solo cuentan lo
 * que es en directo; los logros de cantidad lo cuentan todo.
 *
 * Archivo: userdata/<usuario>/actividad/actividad.jsonl, una línea por
 * acción (se añade al final, sin reescribir todo el archivo).
 */
public final class ActivityService {

    /** Una acción. Los nombres son cortos porque se guardan miles. */
    public static final class Event {
        public long t;            // momento, epoch ms
        public String a;          // EPISODE, DONE, START, REVIEW, FAVORITE, RATING
        public String k;          // clave del título (WatchProgressService.keyFor, en minúsculas)
        public int n = 1;         // cuántas unidades (episodios marcados de golpe...)
        public boolean live = true;
        public boolean premiere;  // episodio marcado el mismo día de su estreno

        public LocalDateTime time() {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault());
        }
        public LocalDate day() { return time().toLocalDate(); }
        /** Las claves de manga empiezan por "manga::". */
        public boolean isManga() { return k != null && k.startsWith("manga::"); }
    }

    public static final String EPISODE = "EPISODE", DONE = "DONE", START = "START",
            REVIEW = "REVIEW", FAVORITE = "FAVORITE", RATING = "RATING";

    private static final Gson gson = new Gson();
    private static final List<Event> events = new ArrayList<>();
    private static boolean loaded = false;
    private static String loadedForUser = null;

    private ActivityService() {}

    /** Apunta una acción en directo (una unidad). */
    public static void live(String action, String key) {
        log(action, key, 1, true);
    }

    /** Apunta una acción. live=false para ponerse al día con algo antiguo. */
    public static synchronized void log(String action, String key, int count, boolean live) {
        if (count <= 0) return;
        ensureLoaded();
        Event e = new Event();
        e.t = System.currentTimeMillis();
        e.a = action;
        e.k = key == null ? null : key.toLowerCase();
        e.n = count;
        e.live = live;
        if (live && EPISODE.equals(action) && e.k != null) e.premiere = ReleaseService.airsToday(e.k);
        events.add(e);
        try {
            Files.writeString(file(), gson.toJson(e) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            System.err.println("⚠️ No se pudo guardar la actividad: " + ex.getMessage());
        }
        // Cada acción puede desbloquear un logro
        AchievementService.requestCheck();
    }

    /** Todas las acciones, de la más antigua a la más reciente. */
    public static synchronized List<Event> all() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /** Acciones en directo por día (para el mapa de actividad y las rachas). */
    public static synchronized Map<LocalDate, Integer> liveCountsByDay() {
        ensureLoaded();
        Map<LocalDate, Integer> byDay = new TreeMap<>();
        for (Event e : events) if (e.live) byDay.merge(e.day(), e.n, Integer::sum);
        return byDay;
    }

    /** Días seguidos con actividad: { racha actual, mejor racha }. */
    public static int[] streaks() {
        List<LocalDate> days = new ArrayList<>(liveCountsByDay().keySet()); // ya ordenados
        int best = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate d : days) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        // La racha actual sigue viva si la última actividad fue hoy o ayer
        LocalDate today = LocalDate.now();
        int current = (prev != null && (prev.equals(today) || prev.equals(today.minusDays(1)))) ? run : 0;
        return new int[]{current, best};
    }

    private static Path file() {
        return AppPaths.userFile(AuthService.getCurrentUser(), AppPaths.DIR_ACTIVITY, "actividad.jsonl");
    }

    private static void ensureLoaded() {
        String user = AuthService.getCurrentUser();
        if (loaded && Objects.equals(loadedForUser, user)) return;
        events.clear();
        loaded = true;
        loadedForUser = user;
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    Event e = gson.fromJson(line, Event.class);
                    if (e != null && e.a != null) events.add(e);
                } catch (Exception ignored) { } // una línea rota no estropea el resto
            }
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo leer la actividad: " + e.getMessage());
        }
    }
}
