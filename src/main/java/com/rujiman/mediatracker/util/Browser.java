package com.rujiman.mediatracker.util;

import java.awt.Desktop;
import java.net.URI;

/**
 * Abre enlaces en el navegador del sistema.
 *
 * java.awt.Desktop funciona siempre en Windows, pero en muchos escritorios
 * de Linux (KDE, XFCE, i3...) no está soportado; ahí se usa xdg-open, que
 * es el comando estándar de Linux para abrir un enlace con la app por defecto.
 */
public final class Browser {

    private Browser() {}

    public static boolean open(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception ignored) {
            // probamos con xdg-open
        }
        if (AppInfo.LINUX) {
            try {
                new ProcessBuilder("xdg-open", url).start();
                return true;
            } catch (Exception ignored) {
            }
        }
        System.err.println("❌ No se pudo abrir el navegador: " + url);
        return false;
    }
}
