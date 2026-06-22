package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnilistService {

    private static final String API_URL = "https://graphql.anilist.co";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public List<MediaItem> search(String query) {

        String graphqlQuery = """
            {
              Page(perPage: 10) {
                media(search: "%s", type: ANIME) {
                  id
                  title { romaji }
                  description
                  coverImage { extraLarge }
                  startDate { year }
                  studios { nodes { name } }
                  format
                  episodes
                  genres
                  averageScore
                  status
                }
              }
            }
            """.formatted(query);

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("query", graphqlQuery);

        // Usamos la clase de OkHttp explícitamente para evitar conflicto con tu enum MediaType
        okhttp3.MediaType jsonMediaType = okhttp3.MediaType.get("application/json");

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                jsonMediaType
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Error AniList: " + response);
            }

            String json = response.body().string();
            return parseResults(json);

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private List<MediaItem> parseResults(String json) {

        List<MediaItem> results = new ArrayList<>();

        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray media = root.getAsJsonObject("data")
                .getAsJsonObject("Page")
                .getAsJsonArray("media");

        for (JsonElement el : media) {
            JsonObject obj = el.getAsJsonObject();

            MediaItem item = new MediaItem();
            item.setType(MediaType.ANIME);

            item.setTitle(obj.getAsJsonObject("title").get("romaji").getAsString());
            item.setDescription(obj.get("description").getAsString());
            item.setImageUrl(obj.getAsJsonObject("coverImage").get("extraLarge").getAsString());
            item.setExternalUrl("https://anilist.co/anime/" + obj.get("id").getAsInt());
            item.setAnilistId(obj.get("id").getAsInt());

            // Año
            JsonObject startDate = obj.getAsJsonObject("startDate");
            if (startDate.has("year") && !startDate.get("year").isJsonNull()) {
                item.setYear(startDate.get("year").getAsInt());
            }

            // Estudio
            JsonArray studios = obj.getAsJsonObject("studios")
                    .getAsJsonArray("nodes");
            if (studios.size() > 0) {
                item.setStudio(studios.get(0).getAsJsonObject().get("name").getAsString());
            }

            // Formato
            if (!obj.get("format").isJsonNull()) {
                item.setFormat(obj.get("format").getAsString());
            }

            // Episodios
            if (!obj.get("episodes").isJsonNull()) {
                item.setEpisodes(obj.get("episodes").getAsInt());
            }

            // Géneros
            JsonArray genres = obj.getAsJsonArray("genres");
            List<String> genreList = new ArrayList<>();
            for (JsonElement g : genres) genreList.add(g.getAsString());
            item.setGenres(genreList);

            // Puntuación
            if (!obj.get("averageScore").isJsonNull()) {
                item.setScore(obj.get("averageScore").getAsInt());
            }

            // Estado
            item.setStatus(obj.get("status").getAsString());

            results.add(item);
        }

        return results;
    }

    /**
     * Pide hasta 10 recomendaciones para un anime concreto, usando la
     * conexión "recommendations" de AniList (votada por usuarios reales
     * de AniList, no solo por género/etiquetas — mejor calidad que un
     * simple "mismo género"). Se llama bajo demanda al abrir el detalle,
     * igual que tráiler/episodios en TMDB, para no ralentizar la búsqueda.
     */
    public List<MediaItem> getRecommendations(int anilistId) {
        String graphqlQuery = """
            {
              Media(id: %d, type: ANIME) {
                recommendations(perPage: 10) {
                  nodes {
                    mediaRecommendation {
                      id
                      title { romaji }
                      coverImage { extraLarge }
                      startDate { year }
                      format
                      episodes
                      genres
                      averageScore
                      status
                      description
                    }
                  }
                }
              }
            }
            """.formatted(anilistId);

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("query", graphqlQuery);

        okhttp3.MediaType jsonMediaType = okhttp3.MediaType.get("application/json");
        RequestBody body = RequestBody.create(jsonBody.toString(), jsonMediaType);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        List<MediaItem> results = new ArrayList<>();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Sin detalles";
                System.err.println("❌ Error AniList recommendations (" + response.code() + "): " + errorBody);
                return results;
            }

            String json = response.body().string();
            JsonObject root = gson.fromJson(json, JsonObject.class);

            // Si la query tuviera algún error (por ejemplo, un argumento
            // GraphQL inválido), AniList responde 200 OK pero con un campo
            // "errors" en el JSON en vez de "data" — hay que comprobarlo
            // explícitamente, porque si no, root.getAsJsonObject("data")
            // lanzaría NullPointerException y el error real quedaría oculto
            // detrás de un simple "no hay recomendaciones".
            if (root.has("errors")) {
                System.err.println("❌ AniList devolvió errores GraphQL: " + root.getAsJsonArray("errors"));
                return results;
            }

            JsonArray nodes = root.getAsJsonObject("data")
                    .getAsJsonObject("Media")
                    .getAsJsonObject("recommendations")
                    .getAsJsonArray("nodes");

            for (JsonElement el : nodes) {
                JsonObject node = el.getAsJsonObject();
                if (!node.has("mediaRecommendation") || node.get("mediaRecommendation").isJsonNull()) {
                    continue; // AniList puede devolver recomendaciones "vacías" si el anime se eliminó
                }

                JsonObject obj = node.getAsJsonObject("mediaRecommendation");

                MediaItem item = new MediaItem();
                item.setType(MediaType.ANIME);
                item.setAnilistId(obj.get("id").getAsInt());
                item.setTitle(obj.getAsJsonObject("title").get("romaji").getAsString());

                if (obj.has("coverImage") && !obj.get("coverImage").isJsonNull()) {
                    JsonObject cover = obj.getAsJsonObject("coverImage");
                    if (cover.has("extraLarge") && !cover.get("extraLarge").isJsonNull()) {
                        item.setImageUrl(cover.get("extraLarge").getAsString());
                    }
                }

                if (obj.has("startDate") && !obj.get("startDate").isJsonNull()) {
                    JsonObject startDate = obj.getAsJsonObject("startDate");
                    if (startDate.has("year") && !startDate.get("year").isJsonNull()) {
                        item.setYear(startDate.get("year").getAsInt());
                    }
                }

                if (obj.has("averageScore") && !obj.get("averageScore").isJsonNull()) {
                    item.setScore(obj.get("averageScore").getAsInt());
                }

                item.setExternalUrl("https://anilist.co/anime/" + obj.get("id").getAsInt());

                results.add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }
}