package com.rujiman.mediatracker.services;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * Comprueba de verdad que una clave de API funciona, haciendo una
 * petición mínima al servicio. Lo usa el asistente de claves para decir
 * "✅ funciona" o "❌ no vale" antes de guardarla.
 */
public final class ApiKeyChecker {

    private ApiKeyChecker() {}

    public enum Status { OK, INVALID, NETWORK }

    public record Result(Status status, String message) {
        static Result ok(String m) { return new Result(Status.OK, m); }
        static Result invalid(String m) { return new Result(Status.INVALID, m); }
        static Result network() {
            return new Result(Status.NETWORK, "No se ha podido comprobar (¿sin conexión?).");
        }
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public static Result checkTmdb(String key) {
        if (key == null || key.isBlank()) return Result.invalid("Pega tu API Key de TMDB.");
        if (key.startsWith("eyJ")) {
            return Result.invalid("Eso es el «API Read Access Token». Copia la «API Key», la corta de 32 caracteres.");
        }
        HttpUrl url = HttpUrl.parse("https://api.themoviedb.org/3/configuration").newBuilder()
                .addQueryParameter("api_key", key).build();
        try (Response r = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (r.isSuccessful()) return Result.ok("Clave de TMDB correcta.");
            if (r.code() == 401) return Result.invalid("TMDB dice que esa clave no es válida. Revisa que esté completa.");
            return Result.network();
        } catch (Exception e) {
            return Result.network();
        }
    }

    public static Result checkIgdb(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return Result.invalid("Pega el Client ID y el Client Secret.");
        }
        Request request = new Request.Builder()
                .url("https://id.twitch.tv/oauth2/token")
                .post(new FormBody.Builder()
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("grant_type", "client_credentials")
                        .build())
                .build();
        try (Response r = client.newCall(request).execute()) {
            if (r.isSuccessful()) return Result.ok("Claves de Twitch correctas.");
            if (r.code() == 400 || r.code() == 401 || r.code() == 403) {
                String body = r.body() != null ? r.body().string().toLowerCase() : "";
                if (body.contains("secret")) {
                    return Result.invalid("El Client Secret no es válido. En Twitch pulsa «New Secret» y pega el nuevo.");
                }
                return Result.invalid("El Client ID no es válido. Cópialo otra vez desde tu aplicación de Twitch.");
            }
            return Result.network();
        } catch (Exception e) {
            return Result.network();
        }
    }

    public static Result checkGoogleBooks(String key) {
        if (key == null || key.isBlank()) return Result.invalid("Pega tu clave de Google, o salta este paso.");
        HttpUrl url = HttpUrl.parse("https://www.googleapis.com/books/v1/volumes").newBuilder()
                .addQueryParameter("q", "harry potter")
                .addQueryParameter("maxResults", "1")
                .addQueryParameter("key", key).build();
        try (Response r = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (r.isSuccessful()) return Result.ok("Clave de Google Books correcta.");
            String body = r.body() != null ? r.body().string() : "";
            if (r.code() == 403 && body.contains("SERVICE_DISABLED")) {
                return Result.invalid("La clave existe, pero falta activar «Books API» en tu proyecto de Google.");
            }
            if (r.code() == 400 || r.code() == 403) return Result.invalid("Google dice que esa clave no es válida.");
            return Result.network();
        } catch (Exception e) {
            return Result.network();
        }
    }
}
