package com.rujiman.mediatracker.ui;

import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Carga la tipografía Inter incluida en la app (licencia SIL OFL, ver
 * fonts/OFL-Inter.txt). Así MediaVerse se ve igual en cualquier PC, sin
 * depender de que Windows tenga la fuente instalada.
 *
 * Nombres de familia que quedan disponibles en CSS:
 *   "Inter"           → normal y bold (-fx-font-weight: bold)
 *   "Inter Medium"    → 500
 *   "Inter SemiBold"  → 600
 *   "Inter ExtraBold" → 800 (títulos grandes)
 */
public final class Fonts {

    private Fonts() {}

    private static final String[] FILES = {
            "Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf",
            "Inter-Bold.ttf", "Inter-ExtraBold.ttf"
    };

    /** Llamar una vez al arrancar, antes de crear la primera Scene. */
    public static void load() {
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream("/com/rujiman/mediatracker/fonts/" + file)) {
                if (in == null) {
                    System.err.println("⚠️ Fuente no encontrada: " + file);
                    continue;
                }
                Font.loadFont(in, 13);
            } catch (Exception e) {
                System.err.println("⚠️ No se pudo cargar " + file + ": " + e.getMessage());
            }
        }
    }
}
