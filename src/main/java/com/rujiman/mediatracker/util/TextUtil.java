package com.rujiman.mediatracker.util;

/**
 * Utilidades de texto para lo que llega de las APIs.
 */
public final class TextUtil {

    private TextUtil() {}

    /**
     * Convierte una descripción con restos de HTML en texto limpio:
     * - <br>, <p> y </p> pasan a saltos de línea
     * - cualquier otra etiqueta (<i>, <b>, <a ...>) se quita
     * - entidades típicas (&amp; &quot; &#039; &nbsp; &mdash;...) se traducen
     * - espacios y saltos de línea sobrantes se recortan
     *
     * Se aplica al MOSTRAR, así también se limpian las descripciones que
     * ya estaban guardadas en favoritos/planes antes de existir el filtro.
     */
    public static String cleanHtml(String raw) {
        if (raw == null) return null;
        String s = raw
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?p\\s*>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…");
        // Espacios al principio de cada línea y líneas en blanco de más
        s = s.replaceAll("[ \\t]+\\n", "\n")
             .replaceAll("\\n[ \\t]+", "\n")
             .replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}
