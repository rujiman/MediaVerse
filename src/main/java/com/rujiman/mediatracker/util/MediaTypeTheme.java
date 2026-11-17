package com.rujiman.mediatracker.util;

import com.rujiman.mediatracker.models.MediaType;
import javafx.scene.paint.Color;

/**
 * Única fuente de verdad para todo lo visual asociado a un MediaType:
 * colores de acento, clases CSS hover y etiquetas legibles.
 *
 * Antes existían tres copias del mismo switch en SearchController,
 * HomeController y DetailViewController. Ahora todos llaman aquí.
 *
 * Paleta "Constelaciones":
 *   GAME   → cyan   #4dd9ec
 *   SERIES → violet #8b5cf6
 *   ANIME  → pink   #ec4dc0
 *   MUSIC  → green  #4dec9e
 *   MOVIE  → amber  #ecb14d
 *   BOOK   → orange #f97316
 *   MANGA  → red    #ef4444
 */
public final class MediaTypeTheme {

    private MediaTypeTheme() {}

    // ── Colores ────────────────────────────────────────────────────────

    public static String accentHex(MediaType type) {
        if (type == null) return "#ec4d80";
        return switch (type) {
            case GAME   -> "#4dd9ec";
            case SERIES -> "#8b5cf6";
            case ANIME  -> "#ec4dc0";
            case MUSIC  -> "#4dec9e";
            case MOVIE  -> "#ecb14d";
            case BOOK   -> "#f97316";
            case MANGA  -> "#ef4444";
        };
    }

    /** Mismo color como objeto JavaFX Paint (útil para setFill, etc.). */
    public static Color accentColor(MediaType type) {
        return Color.web(accentHex(type));
    }

    /** Versión semitransparente del color de acento (10 % opacidad). */
    public static String accentHexAlpha10(MediaType type) {
        return accentHex(type) + "1a";
    }

    // ── Clases CSS ─────────────────────────────────────────────────────

    /** Clase CSS para el estado hover de tarjetas según tipo. */
    public static String cardHoverClass(MediaType type) {
        if (type == null) return "card-hover-default";
        return switch (type) {
            case GAME   -> "card-hover-game";
            case SERIES -> "card-hover-series";
            case ANIME  -> "card-hover-anime";
            case MUSIC  -> "card-hover-music";
            case MOVIE  -> "card-hover-movie";
            case BOOK   -> "card-hover-book";
            case MANGA  -> "card-hover-manga";
        };
    }

    /** Clase CSS para el badge de tipo en las tarjetas. */
    public static String badgeClass(MediaType type) {
        if (type == null) return "badge-default";
        return switch (type) {
            case GAME   -> "badge-game";
            case SERIES -> "badge-series";
            case ANIME  -> "badge-anime";
            case MUSIC  -> "badge-music";
            case MOVIE  -> "badge-movie";
            case BOOK   -> "badge-book";
            case MANGA  -> "badge-manga";
        };
    }

    // ── Etiquetas legibles ─────────────────────────────────────────────

    /** Nombre en español para mostrar en la UI. */
    public static String label(MediaType type) {
        if (type == null) return "Desconocido";
        return switch (type) {
            case GAME   -> "Videojuego";
            case SERIES -> "Serie";
            case ANIME  -> "Anime";
            case MUSIC  -> "Música";
            case MOVIE  -> "Película";
            case BOOK   -> "Libro";
            case MANGA  -> "Manga";
        };
    }

    /** Icono emoji decorativo para secciones del dashboard. */
    public static String emoji(MediaType type) {
        if (type == null) return "🎯";
        return switch (type) {
            case GAME   -> "🎮";
            case SERIES -> "📺";
            case ANIME  -> "🎌";
            case MUSIC  -> "🎵";
            case MOVIE  -> "🎬";
            case BOOK   -> "📚";
            case MANGA  -> "📖";
        };
    }

    // ── Badges y progreso ──────────────────────────────────────────────

    /** Texto del badge de tipo en las tarjetas ("🎌 ANIME", "📖 MANGA"...). */
    public static String badgeText(MediaType type) {
        if (type == null) return "";
        return switch (type) {
            case GAME   -> "🎮 JUEGO";
            case SERIES -> "📺 SERIES";
            case ANIME  -> "🎌 ANIME";
            case MUSIC  -> "🎵 MÚSICA";
            case MOVIE  -> "🎬 PELÍCULA";
            case BOOK   -> "📚 LIBRO";
            case MANGA  -> "📖 MANGA";
        };
    }

    /** ¿Este tipo se sigue por unidades marcables (episodios / capítulos)? */
    public static boolean hasUnitProgress(MediaType type) {
        return type == MediaType.ANIME || type == MediaType.SERIES || type == MediaType.MANGA;
    }

    /** Nombre de la unidad de progreso en singular: "Episodio" o "Capítulo". */
    public static String unitSingular(MediaType type) {
        return type == MediaType.MANGA ? "Capítulo" : "Episodio";
    }

    /** Nombre de la unidad de progreso en plural: "Episodios" o "Capítulos". */
    public static String unitPlural(MediaType type) {
        return type == MediaType.MANGA ? "Capítulos" : "Episodios";
    }

    /** ¿Se lee (libro/manga) en vez de verse? Cambia los textos de la UI. */
    public static boolean isReadable(MediaType type) {
        return type == MediaType.BOOK || type == MediaType.MANGA;
    }

    /** Texto corto de progreso para tarjetas: "📺 3/12 vistos" o "📖 40/120 leídos". */
    public static String progressText(MediaType type, int done, int total) {
        return isReadable(type)
                ? "📖 " + done + "/" + total + " leídos"
                : "📺 " + done + "/" + total + " vistos";
    }

    /** Texto del estado "terminado" en tarjetas: "✔ Visto", "✔ Leído"... */
    public static String doneText(MediaType type) {
        if (type == MediaType.GAME)  return "✔ Jugado";
        if (type == MediaType.MUSIC) return "✔ Escuchado";
        return isReadable(type) ? "✔ Leído" : "✔ Visto";
    }

    // ── Inline style helpers ───────────────────────────────────────────

    /**
     * Genera el atributo style completo para un botón de filtro activo.
     * Mantiene exactamente el mismo look que el código anterior hardcodeado.
     */
    public static String activeFilterButtonStyle(MediaType type) {
        String hex = accentHex(type);
        return String.format(
            "-fx-background-color: %s; -fx-text-fill: #0d0d1a; " +
            "-fx-background-radius: 20px; -fx-padding: 6px 16px;",
            hex
        );
    }

    /** Style para botón de filtro inactivo (igual para todos los tipos). */
    public static String inactiveFilterButtonStyle() {
        return "-fx-background-color: transparent; " +
               "-fx-text-fill: #9ca3af; " +
               "-fx-background-radius: 20px; " +
               "-fx-padding: 6px 16px;";
    }

    /**
     * Style para el título de sección del dashboard con degradado de color
     * idéntico al que antes estaba hardcodeado en HomeController.
     */
    public static String sectionTitleStyle(MediaType type) {
        String hex = accentHex(type);
        return String.format(
            "-fx-font-size: 14px; -fx-font-weight: bold; " +
            "-fx-text-fill: %s;",
            hex
        );
    }
}
