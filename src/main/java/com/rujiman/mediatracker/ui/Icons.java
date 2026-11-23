package com.rujiman.mediatracker.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.SVGPath;

/**
 * Iconos vectoriales de trazo (estilo "outline", 24×24) dibujados con
 * SVGPath. Sustituyen a los emojis, que en Windows se ven en blanco y
 * negro y con tamaños desiguales. Al ser vectores se ven nítidos a
 * cualquier tamaño y toman el color que se les pida.
 *
 * Uso: Icons.of(Icons.SEARCH, 18, "#f0eef5")
 *      button.setGraphic(Icons.of(Icons.STAR, 18, MediaTypeTheme.accentHex(type)))
 */
public final class Icons {

    private Icons() {}

    public static final String MENU     = "M4 7h16M4 12h16M4 17h16";
    public static final String SEARCH   = "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z M20 20l-4-4";
    public static final String HOME     = "M3 11l9-7 9 7 M5 10v10h14V10";
    public static final String STAR     = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z";
    public static final String BOOKMARK = "M6 3h12v18l-6-4-6 4z";
    public static final String LIBRARY  = "M5 4v16 M10 4v16 M15 4.5l4.5 15";
    public static final String USER     = "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z M4 21c0-4 4-6 8-6s8 2 8 6";
    public static final String BACK     = "M15 18l-6-6 6-6";
    public static final String PLUS     = "M12 5v14M5 12h14";
    public static final String CHECK    = "M5 12l5 5 9-10";
    public static final String FOLDER   = "M3 6h6l2 2h10v11H3z";
    public static final String SHARE    = "M12 3v12 M7 8l5-5 5 5 M5 14v6h14v-6";
    public static final String EDIT     = "M4 20h4L19 9l-4-4L4 16z";
    public static final String PLAY     = "M7 4l13 8-13 8z";
    public static final String REVIEW   = "M4 5h16v11H10l-5 4v-4H4z M8 9h8 M8 12h5";
    public static final String TROPHY   = "M8 4h8v5a4 4 0 0 1-8 0z M8 6H5c0 2.5 1.3 4 3 4.2 M16 6h3c0 2.5-1.3 4-3 4.2 M12 13v4 M8.5 20h7 M10 17h4";
    public static final String FLAME    = "M12 3c.5 3 5 5.5 5 10.5a5 5 0 0 1-10 0c0-2.6 1.6-3.9 2.2-5.6.9 1 1.6 2 1.8 3.1 1-2.3 1.4-5.2 1-8z";
    public static final String LOCK     = "M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3";
    public static final String FILM     = "M4 5h16v14H4z M8 5v14 M16 5v14 M4 9.5h4 M4 14.5h4 M16 9.5h4 M16 14.5h4";
    public static final String GAMEPAD  = "M7 8h10a4 4 0 0 1 4 4v1a3 3 0 0 1-5.4 1.8L14.5 14h-5l-1.1.8A3 3 0 0 1 3 13v-1a4 4 0 0 1 4-4z M8 10.5v3 M6.5 12h3 M15.5 11.5h.01 M17.5 13h.01";
    public static final String BOOK     = "M5 4.5h10a3 3 0 0 1 3 3V20H8a3 3 0 0 1-3-3z M5 17a3 3 0 0 1 3-3h10";
    public static final String MUSIC    = "M9 17V6l10-2v11 M9 17a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z M19 15a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z";
    public static final String TV       = "M4 7h16v11H4z M9 3l3 4 3-4";
    public static final String COMPASS  = "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z M15.5 8.5l-2 5-5 2 2-5z";
    public static final String MOON     = "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z";
    public static final String SUN      = "M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0z M12 3v2 M12 19v2 M3 12h2 M19 12h2 M5.6 5.6l1.4 1.4 M17 17l1.4 1.4 M5.6 18.4L7 17 M17 7l1.4-1.4";

    /** Icono por nombre (los logros guardan el nombre del icono, no el trazo). */
    public static String byName(String name) {
        if (name == null) return TROPHY;
        return switch (name) {
            case "TV" -> TV;
            case "FILM" -> FILM;
            case "GAMEPAD" -> GAMEPAD;
            case "BOOK" -> BOOK;
            case "MUSIC" -> MUSIC;
            case "REVIEW" -> REVIEW;
            case "STAR" -> STAR;
            case "FLAME" -> FLAME;
            case "USER" -> USER;
            case "COMPASS" -> COMPASS;
            case "MOON" -> MOON;
            case "SUN" -> SUN;
            case "LOCK" -> LOCK;
            default -> TROPHY;
        };
    }

    public static final String PIN      = "M9 4h6l-1 5 3 3H7l3-3z M12 12v8";
    public static final String CALENDAR = "M4 6h16v14H4z M4 10h16 M8 3v5 M16 3v5";
    public static final String CLOSE    = "M6 6l12 12M18 6L6 18";
    public static final String PALETTE  = "M12 3a9 9 0 1 0 0 18c1 0 1.5-.7 1.5-1.5 0-.8-.7-1.2-.7-2 0-.8.7-1.5 1.5-1.5H17a4 4 0 0 0 4-4c0-5-4-9-9-9z M7.5 11.5h.01 M10 7.5h.01 M14.5 7.5h.01";

    /** Icono de trazo del tamaño (px) y color indicados. */
    public static Node of(String path, double size, String hexColor) {
        return build(path, size, hexColor, false);
    }

    /** Icono relleno (útil para la estrella de "ya es favorito"). */
    public static Node filled(String path, double size, String hexColor) {
        return build(path, size, hexColor, true);
    }

    private static Node build(String path, double size, String hexColor, boolean filled) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        Color color = Color.web(hexColor);
        svg.setStroke(color);
        svg.setFill(filled ? color : Color.TRANSPARENT);
        svg.setStrokeWidth(2);
        svg.setStrokeLineCap(StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(StrokeLineJoin.ROUND);

        double scale = size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        // Group: sus límites de layout incluyen la escala, así el icono
        // ocupa exactamente size×size dentro de botones y etiquetas.
        Group group = new Group(svg);
        group.setMouseTransparent(true);
        return group;
    }
}
