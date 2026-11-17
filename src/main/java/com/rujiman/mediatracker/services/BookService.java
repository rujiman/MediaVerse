package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.util.ConfigLoader;
import okhttp3.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de libros usando la API de Google Books, con Open Library
 * como respaldo automático si Google falla o no devuelve nada.
 *
 * - Sin GOOGLE_BOOKS_API_KEY en .env: Google tiene cupo 0, así que se usa
 *   directamente Open Library (sin clave, pero sin sinopsis).
 * - Con GOOGLE_BOOKS_API_KEY: Google Books primero, Open Library si falla.
 *
 * Mapeo a MediaItem:
 * - autores (+ "N páginas")  → platforms (el detalle lo pinta como "Autor")
 * - averageRating (1-5)     → score (0-100), igual escala que el resto
 * - imageLinks.thumbnail    → imageUrl, forzado a https y a mayor tamaño
 *
 * Docs: https://developers.google.com/books/docs/v1/using
 */
public class BookService {

    private static final String API_URL = "https://www.googleapis.com/books/v1/volumes";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    /**
     * Busca primero en Google Books (mejores sinopsis y portadas) y, si
     * falla (sin clave Google suele responder 429 por cupo agotado) o no
     * devuelve nada, cae a Open Library, que es gratis y no necesita clave.
     */
    public List<MediaItem> search(String query) {
        // Sin clave, Google Books tiene cupo 0 ("quota_limit_value": "0"):
        // ni se intenta, directamente Open Library.
        if (googleKey() == null) {
            return searchOpenLibrary(query);
        }
        List<MediaItem> results = searchGoogle(query);
        if (results.isEmpty()) {
            System.out.println("ℹ️ Google Books sin resultados para \"" + query + "\", probando Open Library...");
            results = searchOpenLibrary(query);
        }
        return results;
    }

    /** Clave de Google Books del .env, o null si no hay una válida. */
    private String googleKey() {
        String key = ConfigLoader.get("GOOGLE_BOOKS_API_KEY");
        if (key == null || key.isBlank() || key.startsWith("your_")) return null;
        return key.trim();
    }

    private List<MediaItem> searchGoogle(String query) {
        List<MediaItem> results = new ArrayList<>();

        StringBuilder url = new StringBuilder(API_URL)
                .append("?q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&maxResults=12")
                .append("&printType=books")
                .append("&orderBy=relevance");

        url.append("&key=").append(googleKey());

        Request request = new Request.Builder().url(url.toString()).get().build();

        try (Response response = client.newCall(request).execute()) {
            String json = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                System.err.println("❌ Error Google Books (" + response.code() + "): " + json);
                return results;
            }

            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("items")) return results; // sin resultados

            for (JsonElement el : root.getAsJsonArray("items")) {
                try {
                    MediaItem item = parseVolume(el.getAsJsonObject());
                    if (item != null) results.add(item);
                } catch (Exception e) {
                    System.err.println("⚠️ Libro omitido: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error consultando Google Books: " + e.getMessage());
        }

        return results;
    }

    // ===========================================================
    // OPEN LIBRARY (respaldo, sin clave)
    // https://openlibrary.org/dev/docs/api/search
    // ===========================================================

    private static final String OPEN_LIBRARY_URL = "https://openlibrary.org/search.json";

    private List<MediaItem> searchOpenLibrary(String query) {
        List<MediaItem> results = new ArrayList<>();
        String url = OPEN_LIBRARY_URL
                + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit=12"
                + "&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median,subject,ratings_average";

        // Open Library pide identificar la app con un User-Agent propio
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MediaVerse/1.0 (TFG DAM)")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String json = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                System.err.println("❌ Error Open Library (" + response.code() + ")");
                return results;
            }
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !has(root, "docs")) return results;

            for (JsonElement el : root.getAsJsonArray("docs")) {
                try {
                    MediaItem item = parseOpenLibraryDoc(el.getAsJsonObject());
                    if (item != null) results.add(item);
                } catch (Exception e) {
                    System.err.println("⚠️ Libro (Open Library) omitido: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error consultando Open Library: " + e.getMessage());
        }
        return results;
    }

    private MediaItem parseOpenLibraryDoc(JsonObject doc) {
        String title = getString(doc, "title");
        if (title == null) return null;

        MediaItem item = new MediaItem();
        item.setType(MediaType.BOOK);
        item.setTitle(title);

        if (has(doc, "first_publish_year")) item.setYear(doc.get("first_publish_year").getAsInt());

        if (has(doc, "ratings_average")) {
            item.setScore((int) Math.round(doc.get("ratings_average").getAsDouble() * 20));
        }

        List<String> credits = new ArrayList<>();
        if (has(doc, "author_name")) {
            for (JsonElement a : doc.getAsJsonArray("author_name")) {
                credits.add(a.getAsString());
                if (credits.size() >= 2) break;
            }
        }
        if (!credits.isEmpty()) item.setStudio(credits.get(0));
        if (has(doc, "number_of_pages_median")) {
            credits.add(doc.get("number_of_pages_median").getAsInt() + " páginas");
        }
        if (!credits.isEmpty()) item.setPlatforms(credits);

        // Los "subjects" de Open Library son muchos y muy variados: nos
        // quedamos con los 4 primeros para que hagan de géneros.
        if (has(doc, "subject")) {
            List<String> genres = new ArrayList<>();
            for (JsonElement s : doc.getAsJsonArray("subject")) {
                genres.add(s.getAsString());
                if (genres.size() >= 4) break;
            }
            item.setGenres(genres);
        }

        if (has(doc, "cover_i")) {
            item.setImageUrl("https://covers.openlibrary.org/b/id/" + doc.get("cover_i").getAsLong() + "-L.jpg");
        }

        String key = getString(doc, "key"); // "/works/OL82563W"
        if (key != null) item.setExternalUrl("https://openlibrary.org" + key);

        // La búsqueda no trae sinopsis (habría que pedir cada obra aparte)
        item.setDescription(null);

        return item;
    }

    // ===========================================================
    // GOOGLE BOOKS — parseo
    // ===========================================================

    private MediaItem parseVolume(JsonObject volume) {
        if (!has(volume, "volumeInfo")) return null;
        JsonObject info = volume.getAsJsonObject("volumeInfo");

        String title = getString(info, "title");
        if (title == null) return null;
        String subtitle = getString(info, "subtitle");

        MediaItem item = new MediaItem();
        item.setType(MediaType.BOOK);
        item.setTitle(subtitle != null && subtitle.length() <= 40 ? title + ": " + subtitle : title);
        item.setDescription(cleanDescription(getString(info, "description")));

        // Año: publishedDate puede ser "2004", "2004-05" o "2004-05-01"
        String date = getString(info, "publishedDate");
        if (date != null && date.length() >= 4) {
            try { item.setYear(Integer.parseInt(date.substring(0, 4))); } catch (NumberFormatException ignored) {}
        }

        // Puntuación 1-5 → 0-100 (misma escala que TMDB/AniList en la UI)
        if (has(info, "averageRating")) {
            item.setScore((int) Math.round(info.get("averageRating").getAsDouble() * 20));
        }

        // Autores + número de páginas, para la línea "Autor" del detalle
        List<String> credits = new ArrayList<>();
        if (has(info, "authors")) {
            for (JsonElement a : info.getAsJsonArray("authors")) credits.add(a.getAsString());
        }
        if (!credits.isEmpty()) item.setStudio(credits.get(0));
        if (has(info, "pageCount") && info.get("pageCount").getAsInt() > 0) {
            credits.add(info.get("pageCount").getAsInt() + " páginas");
        }
        if (!credits.isEmpty()) item.setPlatforms(credits);

        if (has(info, "categories")) {
            List<String> genres = new ArrayList<>();
            for (JsonElement c : info.getAsJsonArray("categories")) genres.add(c.getAsString());
            item.setGenres(genres);
        }

        if (has(info, "imageLinks")) {
            JsonObject links = info.getAsJsonObject("imageLinks");
            String img = getString(links, "thumbnail");
            if (img == null) img = getString(links, "smallThumbnail");
            if (img != null) item.setImageUrl(betterCover(img));
        }

        String infoLink = getString(info, "infoLink");
        if (infoLink == null) infoLink = getString(info, "canonicalVolumeLink");
        item.setExternalUrl(infoLink != null ? infoLink.replace("http://", "https://") : null);

        return item;
    }

    /**
     * Las miniaturas de Google Books vienen por http, con efecto de "página
     * doblada" (edge=curl) y a ~128px de ancho. Se fuerzan https, sin curl,
     * y se pide una versión más grande con el parámetro fife.
     */
    private String betterCover(String url) {
        String u = url.replace("http://", "https://").replace("&edge=curl", "");
        return u + (u.contains("?") ? "&" : "?") + "fife=w600-h900";
    }

    private String cleanDescription(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("(?i)<br\\s*/?>", "\n")
                  .replaceAll("(?i)</p>", "\n")
                  .replaceAll("<[^>]+>", "")
                  .trim();
    }

    private boolean has(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull();
    }

    private String getString(JsonObject obj, String key) {
        return has(obj, key) ? obj.get(key).getAsString() : null;
    }
}
