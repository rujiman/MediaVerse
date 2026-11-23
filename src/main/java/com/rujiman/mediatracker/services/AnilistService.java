package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de AniList (GraphQL) para ANIME y MANGA.
 *
 * AniList usa exactamente la misma API para los dos tipos: solo cambia el
 * argumento "type" de la query (ANIME / MANGA). Por eso un único servicio
 * cubre ambos, y cada resultado se convierte a MediaItem con el MediaType
 * que corresponde.
 *
 * Diferencias de mapeo:
 * - ANIME: "episodes" → episodios, estudio principal → studio.
 * - MANGA: "chapters" → episodes (se reutiliza el mismo sistema de
 *   progreso por unidades: en manga cada "episodio" es un capítulo), y
 *   los autores (staff de historia/arte) → platforms, que el detalle
 *   pinta como "Autor".
 *
 * El texto de búsqueda se envía como variable GraphQL ($search), nunca
 * concatenado dentro de la query: así unas comillas en el título
 * (p. ej. Kaguya-sama: "Love is War") no rompen la petición.
 */
public class AnilistService {

    private static final String API_URL = "https://graphql.anilist.co";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    /** Campos comunes que se piden para cualquier media (búsqueda y recomendaciones). */
    private static final String MEDIA_FIELDS = """
              id
              type
              title { romaji }
              description(asHtml: false)
              coverImage { extraLarge }
              startDate { year }
              studios(isMain: true) { nodes { name } }
              staff(perPage: 4, sort: RELEVANCE) { edges { role node { name { full } } } }
              format
              episodes
              chapters
              genres
              averageScore
              status
            """;

    // ===========================================================
    // BÚSQUEDA
    // ===========================================================

    /** Búsqueda de anime (se mantiene la firma original). */
    public List<MediaItem> search(String query) {
        return search(query, MediaType.ANIME);
    }

    /** Búsqueda de manga. */
    public List<MediaItem> searchManga(String query) {
        return search(query, MediaType.MANGA);
    }

    /**
     * Busca en AniList el tipo indicado (solo ANIME o MANGA).
     */
    public List<MediaItem> search(String query, MediaType type) {
        String graphqlQuery = """
            query ($search: String, $type: MediaType) {
              Page(perPage: 10) {
                media(search: $search, type: $type, sort: SEARCH_MATCH) {
            """ + MEDIA_FIELDS + """
                }
              }
            }
            """;

        JsonObject variables = new JsonObject();
        variables.addProperty("search", query);
        variables.addProperty("type", anilistType(type));

        List<MediaItem> results = new ArrayList<>();
        JsonObject data = execute(graphqlQuery, variables);
        if (data == null) return results;

        try {
            JsonArray media = data.getAsJsonObject("Page").getAsJsonArray("media");
            for (JsonElement el : media) {
                try {
                    results.add(parseMedia(el.getAsJsonObject(), type));
                } catch (Exception e) {
                    // Un resultado con datos raros no debe tumbar toda la búsqueda
                    System.err.println("⚠️ Resultado AniList omitido: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Respuesta inesperada de AniList: " + e.getMessage());
        }
        return results;
    }

    // ===========================================================
    // RECOMENDACIONES
    // ===========================================================

    /** Recomendaciones de anime (se mantiene la firma original). */
    public List<MediaItem> getRecommendations(int anilistId) {
        return getRecommendations(anilistId, MediaType.ANIME);
    }

    /**
     * Pide hasta 10 recomendaciones para un anime o manga concreto, usando
     * la conexión "recommendations" de AniList (votada por usuarios reales,
     * mejor calidad que un simple "mismo género"). Se llama bajo demanda
     * al abrir el detalle, para no ralentizar la búsqueda.
     *
     * Nota: AniList puede recomendar un anime desde un manga y viceversa;
     * cada resultado se etiqueta con SU tipo real, no con el del original.
     */
    public List<MediaItem> getRecommendations(int anilistId, MediaType type) {
        String graphqlQuery = """
            query ($id: Int, $type: MediaType) {
              Media(id: $id, type: $type) {
                recommendations(perPage: 10) {
                  nodes {
                    mediaRecommendation {
            """ + MEDIA_FIELDS + """
                    }
                  }
                }
              }
            }
            """;

        JsonObject variables = new JsonObject();
        variables.addProperty("id", anilistId);
        variables.addProperty("type", anilistType(type));

        List<MediaItem> results = new ArrayList<>();
        JsonObject data = execute(graphqlQuery, variables);
        if (data == null) return results;

        try {
            JsonArray nodes = data.getAsJsonObject("Media")
                    .getAsJsonObject("recommendations")
                    .getAsJsonArray("nodes");

            for (JsonElement el : nodes) {
                JsonObject node = el.getAsJsonObject();
                if (!node.has("mediaRecommendation") || node.get("mediaRecommendation").isJsonNull()) {
                    continue; // AniList puede devolver recomendaciones "vacías" si el título se eliminó
                }
                JsonObject obj = node.getAsJsonObject("mediaRecommendation");
                try {
                    results.add(parseMedia(obj, typeOf(obj, type)));
                } catch (Exception e) {
                    System.err.println("⚠️ Recomendación AniList omitida: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Respuesta inesperada de AniList (recomendaciones): " + e.getMessage());
        }
        return results;
    }

    // ===========================================================
    // HTTP + PARSEO
    // ===========================================================

    /**
     * Ejecuta una query GraphQL y devuelve el objeto "data", o null si hubo
     * cualquier error. AniList responde 200 OK con un campo "errors" cuando
     * la query es inválida, así que se comprueba explícitamente.
     */
    private JsonObject execute(String graphqlQuery, JsonObject variables) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("query", graphqlQuery);
        jsonBody.add("variables", variables);

        // Clase de OkHttp explícita para no chocar con nuestro enum MediaType
        okhttp3.MediaType jsonMediaType = okhttp3.MediaType.get("application/json");
        RequestBody body = RequestBody.create(jsonBody.toString(), jsonMediaType);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String json = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Error AniList (" + response.code() + "): " + json);
            }
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return null;
            if (root.has("errors")) {
                System.err.println("❌ AniList devolvió errores GraphQL: " + root.getAsJsonArray("errors"));
                return null;
            }
            return root.getAsJsonObject("data");
        } catch (Exception e) {
            System.err.println("❌ Error consultando AniList: " + e.getMessage());
            return null;
        }
    }

    /** Convierte un objeto "media" de AniList en MediaItem del tipo dado. */
    private MediaItem parseMedia(JsonObject obj, MediaType type) {
        MediaItem item = new MediaItem();
        item.setType(type);

        int id = obj.get("id").getAsInt();
        item.setAnilistId(id);
        item.setExternalUrl("https://anilist.co/" + (type == MediaType.MANGA ? "manga/" : "anime/") + id);

        item.setTitle(obj.getAsJsonObject("title").get("romaji").getAsString());

        String description = getString(obj, "description");
        if (description != null) item.setDescription(cleanDescription(description));

        if (has(obj, "coverImage")) {
            item.setImageUrl(getString(obj.getAsJsonObject("coverImage"), "extraLarge"));
        }

        if (has(obj, "startDate")) {
            Integer year = getInt(obj.getAsJsonObject("startDate"), "year");
            if (year != null) item.setYear(year);
        }

        item.setFormat(getString(obj, "format"));
        item.setStatus(getString(obj, "status"));

        Integer score = getInt(obj, "averageScore");
        if (score != null) item.setScore(score);

        if (has(obj, "genres")) {
            List<String> genreList = new ArrayList<>();
            for (JsonElement g : obj.getAsJsonArray("genres")) genreList.add(g.getAsString());
            item.setGenres(genreList);
        }

        if (type == MediaType.MANGA) {
            // En manga, la unidad de progreso es el capítulo. Los mangas en
            // emisión no tienen total conocido (null): en ese caso el detalle
            // muestra el botón "Marcar como leído" en vez de la lista.
            Integer chapters = getInt(obj, "chapters");
            if (chapters != null && chapters > 0) item.setEpisodes(chapters);

            List<String> authors = parseAuthors(obj);
            if (!authors.isEmpty()) {
                item.setPlatforms(authors);
                item.setStudio(authors.get(0));
            }
        } else {
            Integer episodes = getInt(obj, "episodes");
            if (episodes != null) item.setEpisodes(episodes);

            if (has(obj, "studios")) {
                JsonArray studios = obj.getAsJsonObject("studios").getAsJsonArray("nodes");
                if (studios.size() > 0) {
                    item.setStudio(studios.get(0).getAsJsonObject().get("name").getAsString());
                }
            }
        }

        return item;
    }

    /**
     * Autores de un manga: personas del staff con rol de historia o arte
     * ("Story & Art", "Story", "Art"), sin repetir, máximo 2.
     */
    private List<String> parseAuthors(JsonObject obj) {
        List<String> authors = new ArrayList<>();
        if (!has(obj, "staff")) return authors;
        for (JsonElement el : obj.getAsJsonObject("staff").getAsJsonArray("edges")) {
            JsonObject edge = el.getAsJsonObject();
            String role = getString(edge, "role");
            if (role == null) continue;
            String r = role.toLowerCase();
            if (!(r.contains("story") || r.contains("art"))) continue;
            try {
                String name = edge.getAsJsonObject("node").getAsJsonObject("name").get("full").getAsString();
                if (!authors.contains(name)) authors.add(name);
            } catch (Exception ignored) {}
            if (authors.size() >= 2) break;
        }
        return authors;
    }

    /** El tipo real de un resultado ("ANIME"/"MANGA"), o el de respaldo. */
    private MediaType typeOf(JsonObject obj, MediaType fallback) {
        String t = getString(obj, "type");
        if ("MANGA".equals(t)) return MediaType.MANGA;
        if ("ANIME".equals(t)) return MediaType.ANIME;
        return fallback;
    }

    private String anilistType(MediaType type) {
        return type == MediaType.MANGA ? "MANGA" : "ANIME";
    }

    /** AniList mete etiquetas HTML sueltas (<br>, <i>...) incluso en modo texto. */
    private String cleanDescription(String raw) {
        return raw.replaceAll("(?i)<br\\s*/?>", "\n")
                  .replaceAll("<[^>]+>", "")
                  .replaceAll("\n{3,}", "\n\n")
                  .trim();
    }

    // ── Helpers null-safe (mismo espíritu que FavoritesService.safeGetX) ──

    // ============================
    // ESTRENOS (calendario)
    // ============================

    /**
     * Emisión de un anime: próximo episodio (airingAt en segundos Unix) y,
     * si aún no ha empezado, su fecha de estreno (puede venir incompleta).
     */
    public record Airing(int id, Long airingAt, Integer episode, String status,
                         Integer startYear, Integer startMonth, Integer startDay) {}

    /**
     * Pide de golpe la emisión de varios animes (de 50 en 50, una petición
     * por bloque). Devuelve null si AniList falla, para no guardar nada.
     */
    public java.util.Map<Integer, Airing> getAiring(List<Integer> ids) {
        String graphqlQuery = """
            query ($ids: [Int]) {
              Page(page: 1, perPage: 50) {
                media(id_in: $ids, type: ANIME) {
                  id
                  status
                  nextAiringEpisode { airingAt episode }
                  startDate { year month day }
                }
              }
            }
            """;
        java.util.Map<Integer, Airing> result = new java.util.HashMap<>();
        for (int from = 0; from < ids.size(); from += 50) {
            JsonArray idArray = new JsonArray();
            for (Integer id : ids.subList(from, Math.min(from + 50, ids.size()))) idArray.add(id);
            JsonObject variables = new JsonObject();
            variables.add("ids", idArray);

            JsonObject data = execute(graphqlQuery, variables);
            if (data == null) return null;
            try {
                for (JsonElement el : data.getAsJsonObject("Page").getAsJsonArray("media")) {
                    JsonObject m = el.getAsJsonObject();
                    JsonObject next = has(m, "nextAiringEpisode") ? m.getAsJsonObject("nextAiringEpisode") : null;
                    JsonObject start = has(m, "startDate") ? m.getAsJsonObject("startDate") : null;
                    int id = m.get("id").getAsInt();
                    result.put(id, new Airing(id,
                            next != null && has(next, "airingAt") ? next.get("airingAt").getAsLong() : null,
                            next != null ? getInt(next, "episode") : null,
                            getString(m, "status"),
                            getInt(start, "year"), getInt(start, "month"), getInt(start, "day")));
                }
            } catch (Exception e) {
                System.err.println("⚠️ Respuesta de emisiones de AniList inesperada: " + e.getMessage());
                return null;
            }
        }
        return result;
    }

    private boolean has(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull();
    }

    private String getString(JsonObject obj, String key) {
        return has(obj, key) ? obj.get(key).getAsString() : null;
    }

    private Integer getInt(JsonObject obj, String key) {
        return has(obj, key) ? obj.get(key).getAsInt() : null;
    }
}
