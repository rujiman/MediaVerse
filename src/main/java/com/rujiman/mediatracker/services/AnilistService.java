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
}
