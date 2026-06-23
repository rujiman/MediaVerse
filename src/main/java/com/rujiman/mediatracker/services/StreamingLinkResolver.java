package com.rujiman.mediatracker.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resuelve a qué URL abrir cuando el usuario pulsa "Abrir en navegador"
 * desde el detalle, según la(s) plataforma(s) disponibles para el item.
 *
 * TMDB (watch/providers) da el NOMBRE del proveedor (provider_name) pero,
 * por acuerdo con JustWatch, nunca un enlace directo a la página del
 * título dentro de esa plataforma — solo un "link" que redirige a la
 * propia web de TMDB. Por eso aquí se usa un mapa fijo provider_name →
 * URL de búsqueda dentro de esa plataforma: no es un enlace directo al
 * título exacto, pero deja al usuario a un clic de encontrarlo.
 *
 * Solo se cubren las plataformas más comunes en España. Si
 * MediaItem.getPlatforms() trae un nombre que no está en este mapa, esa
 * plataforma se descarta en silencio (no se ofrece como opción
 * clicable) — decisión explícita para no acabar abriendo búsquedas
 * genéricas de Google que no aportan nada sobre simplemente usar el
 * enlace de TMDB como fallback.
 */
public final class StreamingLinkResolver {

    private StreamingLinkResolver() {}

    // provider_name de TMDB (tal cual aparece en watch/providers para
    // España) -> URL de búsqueda dentro de esa plataforma. {query} se
    // sustituye por el título codificado para URL.
    private static final Map<String, String> PLATFORM_SEARCH_URLS = new LinkedHashMap<>();
    static {
        PLATFORM_SEARCH_URLS.put("Netflix", "https://www.netflix.com/search?q={query}");
        PLATFORM_SEARCH_URLS.put("Amazon Prime Video", "https://www.primevideo.com/search/ref=atv_nb_sr?phrase={query}");
        PLATFORM_SEARCH_URLS.put("Disney Plus", "https://www.disneyplus.com/search?q={query}");
        PLATFORM_SEARCH_URLS.put("Max", "https://play.max.com/search?q={query}");
        PLATFORM_SEARCH_URLS.put("HBO Max", "https://play.max.com/search?q={query}");
        PLATFORM_SEARCH_URLS.put("Movistar Plus+", "https://ver.movistarplus.es/buscador/?q={query}");
        PLATFORM_SEARCH_URLS.put("Filmin", "https://www.filmin.es/buscar?q={query}");
        PLATFORM_SEARCH_URLS.put("Apple TV", "https://tv.apple.com/search?term={query}");
        PLATFORM_SEARCH_URLS.put("Apple TV Plus", "https://tv.apple.com/search?term={query}");
        PLATFORM_SEARCH_URLS.put("SkyShowtime", "https://www.skyshowtime.com/search?q={query}");
        PLATFORM_SEARCH_URLS.put("Crunchyroll", "https://www.crunchyroll.com/search?q={query}");
    }

    // Para ANIME, "platforms" no representa disponibilidad real (suele
    // ser estudio/formato), así que aquí se ofrece siempre el mismo
    // listado fijo de webs de anime con búsqueda por título, sin
    // pretender saber si el título está realmente disponible en cada
    // una. Crunchyroll primero por ser la más extendida en España.
    private static final Map<String, String> ANIME_SEARCH_URLS = new LinkedHashMap<>();
    static {
        ANIME_SEARCH_URLS.put("Crunchyroll", "https://www.crunchyroll.com/search?q={query}");
        ANIME_SEARCH_URLS.put("Netflix", "https://www.netflix.com/search?q={query}");
        ANIME_SEARCH_URLS.put("Amazon Prime Video", "https://www.primevideo.com/search/ref=atv_nb_sr?phrase={query}");
    }

    /**
     * Para SERIES/MOVIE: cruza los nombres de MediaItem.getPlatforms()
     * con el mapa fijo y devuelve solo las que se reconocen, en forma de
     * pares nombre -> URL ya resuelta con el título insertado. El orden
     * de salida sigue el orden de platformNames recibido (que viene del
     * orden de display_priority que ya da TMDBService).
     */
    public static Map<String, String> resolveForPlatforms(List<String> platformNames, String title) {
        Map<String, String> result = new LinkedHashMap<>();
        if (platformNames == null) return result;

        for (String name : platformNames) {
            String template = PLATFORM_SEARCH_URLS.get(name);
            if (template != null) {
                result.put(name, buildUrl(template, title));
            }
        }
        return result;
    }

    /**
     * Para ANIME: listado fijo de sitios de búsqueda, independiente de
     * MediaItem.getPlatforms() (que en este tipo no es streaming real).
     */
    public static Map<String, String> resolveForAnime(String title) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ANIME_SEARCH_URLS.entrySet()) {
            result.put(entry.getKey(), buildUrl(entry.getValue(), title));
        }
        return result;
    }

    private static String buildUrl(String template, String title) {
        String encoded = java.net.URLEncoder.encode(title == null ? "" : title, java.nio.charset.StandardCharsets.UTF_8);
        return template.replace("{query}", encoded);
    }
}
