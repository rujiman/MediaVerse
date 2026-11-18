package com.rujiman.mediatracker.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rujiman.mediatracker.util.AppInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;

/**
 * Actualizaciones automáticas desde GitHub Releases.
 *
 * 1. checkLatest(): pregunta a la API de GitHub cuál es la última Release
 *    y, si su versión es mayor que AppInfo.VERSION, la devuelve.
 * 2. downloadJar(): descarga SOLO el .jar de la app (unos 15 MB) junto al
 *    actual, como "MediaVerse.jar.new" (el jar en uso no se puede
 *    sobrescribir mientras la app está abierta).
 * 3. installAndRestart(): deja un pequeño script (.bat en Windows, .sh en
 *    Linux) que espera a que la app se cierre, cambia el jar viejo por el
 *    nuevo y vuelve a abrir MediaVerse. El .env y los datos del usuario no se tocan (los
 *    datos viven en Documentos\MediaVerse, fuera de la app).
 *
 * Si la app se está ejecutando desde el IDE (no hay .jar) o la Release
 * pide instalación completa, la app abre la página de descarga.
 */
public final class UpdateService {

    private UpdateService() {}

    /** Una versión publicada en GitHub. */
    public record Release(String version, String notes, String pageUrl, String jarUrl, String zipUrl) {
        /** Se puede actualizar solo con el .jar (sin reinstalar). */
        public boolean jarUpdatable() {
            return jarUrl != null && (notes == null || !notes.toLowerCase().contains(AppInfo.FULL_INSTALL_MARK));
        }
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    // ===========================================================
    // COMPROBAR
    // ===========================================================

    /** La última Release si es más nueva que la versión instalada; vacío si no hay o falla la red. */
    public static Optional<Release> checkLatest() {
        Request request = new Request.Builder()
                .url("https://api.github.com/repos/" + AppInfo.GITHUB_REPO + "/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MediaVerse/" + AppInfo.VERSION)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Optional.empty();
            JsonObject root = new Gson().fromJson(response.body().string(), JsonObject.class);

            String tag = str(root, "tag_name");
            if (tag == null) return Optional.empty();
            String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            if (!isNewer(version, AppInfo.VERSION)) return Optional.empty();

            String jarUrl = null, zipUrl = null;
            if (root.has("assets") && root.get("assets").isJsonArray()) {
                JsonArray assets = root.getAsJsonArray("assets");
                for (JsonElement el : assets) {
                    JsonObject a = el.getAsJsonObject();
                    String name = str(a, "name");
                    if (AppInfo.JAR_ASSET.equalsIgnoreCase(name)) jarUrl = str(a, "browser_download_url");
                    if (AppInfo.ZIP_ASSET.equalsIgnoreCase(name)) zipUrl = str(a, "browser_download_url");
                }
            }
            return Optional.of(new Release(version, str(root, "body"), str(root, "html_url"), jarUrl, zipUrl));
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo comprobar si hay actualizaciones: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Compara versiones tipo 1.2.10 por números (1.10.0 es mayor que 1.9.0). */
    public static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[.\\-]"), b = current.split("[.\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? toInt(a[i]) : 0;
            int y = i < b.length ? toInt(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    // ===========================================================
    // INSTALAR
    // ===========================================================

    /** El .jar que se está ejecutando ahora mismo, o null si se ejecuta desde el IDE. */
    public static Path runningJar() {
        try {
            File f = new File(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() && f.getName().toLowerCase().endsWith(".jar") ? f.toPath() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** ¿Esta instalación se puede actualizar sola con esta Release? */
    public static boolean canSelfUpdate(Release release) {
        return (AppInfo.WINDOWS || AppInfo.LINUX) && runningJar() != null && release.jarUpdatable();
    }

    /** Descarga el nuevo .jar junto al actual. progress recibe 0..1 (o -1 si el tamaño es desconocido). */
    public static Path downloadJar(Release release, DoubleConsumer progress) throws Exception {
        Path target = runningJar().resolveSibling(AppInfo.JAR_ASSET + ".new");
        Request request = new Request.Builder()
                .url(release.jarUrl())
                .header("User-Agent", "MediaVerse/" + AppInfo.VERSION)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("Descarga fallida (" + response.code() + ")");
            }
            ResponseBody body = response.body();
            long total = body.contentLength();
            try (InputStream in = body.byteStream(); OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    done += n;
                    progress.accept(total > 0 ? (double) done / total : -1);
                }
            }
        }
        // Comprobación mínima: un .jar es un zip y empieza por "PK"
        byte[] head = new byte[2];
        try (InputStream check = Files.newInputStream(target)) {
            if (check.read(head) != 2 || head[0] != 'P' || head[1] != 'K') {
                Files.deleteIfExists(target);
                throw new IllegalStateException("El archivo descargado no es válido");
            }
        }
        return target;
    }

    /**
     * Lanza el script que cambia el jar cuando la app se cierre y la vuelve
     * a abrir. Después de llamar a esto, la app debe cerrarse.
     */
    public static void installAndRestart(Path newJar) throws Exception {
        if (AppInfo.LINUX) {
            installAndRestartLinux(newJar);
            return;
        }
        Path jar = runningJar();
        Path exe = jar.getParent().getParent().resolve("MediaVerse.exe"); // app\..\MediaVerse.exe
        Path script = Files.createTempFile("mediaverse-update-", ".bat");
        String bat = String.join("\r\n",
                "@echo off",
                "set /a tries=0",
                ":wait",
                "timeout /t 1 /nobreak >nul",
                "move /y \"" + newJar + "\" \"" + jar + "\" >nul 2>&1",
                "if errorlevel 1 (",
                "  set /a tries+=1",
                "  if %tries% lss 30 goto wait",
                "  exit /b 1",
                ")",
                Files.exists(exe) ? "start \"\" \"" + exe + "\"" : "rem sin MediaVerse.exe: no se reabre",
                "del \"%~f0\"",
                "");
        Files.writeString(script, bat, StandardCharsets.ISO_8859_1);
        new ProcessBuilder("cmd.exe", "/c", script.toString()).start();
    }

    /**
     * Versión Linux. La carpeta de jpackage en Linux es:
     *   MediaVerse/bin/MediaVerse        (lanzador)
     *   MediaVerse/lib/app/<jar>         (la app)
     * El script espera a que termine este proceso (por su PID), cambia el
     * jar y vuelve a abrir el lanzador.
     */
    private static void installAndRestartLinux(Path newJar) throws Exception {
        Path jar = runningJar();
        Path launcher = jar.getParent().getParent().getParent().resolve("bin").resolve("MediaVerse");
        long pid = ProcessHandle.current().pid();
        Path script = Files.createTempFile("mediaverse-update-", ".sh");
        String sh = String.join("\n",
                "#!/bin/sh",
                "while kill -0 " + pid + " 2>/dev/null; do sleep 0.5; done",
                "mv -f " + quote(newJar) + " " + quote(jar) + " || exit 1",
                Files.isExecutable(launcher)
                        ? "nohup " + quote(launcher) + " >/dev/null 2>&1 &"
                        : "# sin lanzador: no se reabre",
                "rm -f \"$0\"",
                "");
        Files.writeString(script, sh, StandardCharsets.UTF_8);
        new ProcessBuilder("sh", script.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    /** Entrecomilla una ruta para sh (soporta espacios y comillas simples). */
    private static String quote(Path p) {
        return "'" + p.toString().replace("'", "'\\''") + "'";
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static int toInt(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }
}
