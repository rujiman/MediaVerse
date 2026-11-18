package com.rujiman.mediatracker.ui;

/**
 * Colores NEUTROS de la app (fondos, bordes, textos, marca) en un único
 * sitio. Los colores de cada tipo de contenido (juegos, anime...) siguen
 * en MediaTypeTheme. Mismos valores que los "lookup colors" de theme.css,
 * para el código que construye estilos desde Java.
 */
public final class Palette {

    private Palette() {}

    // Fondos y superficies (de más oscuro a más claro)
    public static final String BG          = "#100c1c";
    public static final String PANEL       = "#1a1530";
    public static final String PANEL_ALT   = "#1c1730"; // diálogos y menús
    public static final String PANEL_2     = "#211a39";
    public static final String PANEL_3     = "#241d3d";
    public static final String SELECTED    = "#2b2250";

    // Bordes
    public static final String LINE        = "#2a2347";
    public static final String LINE_STRONG = "#3a3160";

    // Texto
    public static final String TEXT        = "#f0eef5";
    public static final String TEXT_SOFT   = "#e6e1f5";
    public static final String TEXT_MUTED  = "#cfc9e6";
    public static final String DIM         = "#a59fc0";
    /** Texto oscuro sobre fondos de acento (botones y badges de color). */
    public static final String ON_ACCENT   = "#120c1c";

    // Marca y estados
    public static final String BRAND       = "#ec4d80";
    public static final String ERROR       = "#e74c3c";
    public static final String SUCCESS     = "#4dec9e";
}
