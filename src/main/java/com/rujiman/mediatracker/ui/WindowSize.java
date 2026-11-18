package com.rujiman.mediatracker.ui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

/**
 * Tamaño de la ventana del login: 1280×800 si cabe, y si la pantalla es
 * más pequeña (portátiles, escalado de Windows al 125–150 %), el 90 % del
 * área útil de la pantalla. Así nunca se abre ni diminuta ni cortada.
 */
public final class WindowSize {

    private WindowSize() {}

    public static double loginWidth() {
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        return Math.min(1280, vb.getWidth() * 0.9);
    }

    public static double loginHeight() {
        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        return Math.min(800, vb.getHeight() * 0.9);
    }
}
