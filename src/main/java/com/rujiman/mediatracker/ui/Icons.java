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
