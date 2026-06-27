package com.rujiman.mediatracker.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Punto único donde se decide DÓNDE vive cada archivo de datos en disco.
 *
 * Antes, cada servicio construía su propio nombre de archivo plano
 * ("favorites_Rujiman.json", "dashboard_Rujiman.json"...) y lo escribía
 * directamente en el directorio de ejecución del .jar, lo que dejaba
 * decenas de .json sueltos mezclados con el ejecutable, el pom, etc.
 *
 * Ahora todo cuelga de una carpeta raíz "userdata/", organizada así:
 *
 *   userdata/
 *   ├── users.json                       (global: lista de cuentas)
 *   ├── profile.json                     (global: perfiles de todos)
 *   └── <usuario>/
 *       ├── <usuario>_profile.jpg        (foto de perfil; la escribe SearchController)
 *       ├── favoritos/favorites.json
 *       ├── dashboard/dashboard.json
 *       ├── progreso/progress.json
 *       ├── plan/plan_watch.json
 *       ├── plan/plan_play.json
 *       ├── plan/plan_listen.json
 *       └── carpetas/folders_<namespace>.json
 *
 * Ventaja principal: para hacer una copia de seguridad de un usuario basta
 * con copiar su carpeta entera, en vez de ir pescando archivos sueltos.
 *
 * UBICACIÓN DE LA CARPETA RAÍZ
 * -----------------------------
 * La carpeta raíz NO es relativa al directorio desde el que se ejecuta
 * la app. Antes lo era ("userdata" sin más), lo cual funcionaba bien
 * en desarrollo (ejecutando con `mvn javafx:run` desde la raíz del
 * proyecto), pero se rompía al empaquetar con jpackage: el directorio
 * de trabajo del .exe empaquetado no es predecible, así que userdata/
 * terminaba escondida dentro de la carpeta de instalación, donde el
 * usuario no la encuentra y donde además Windows puede no dar permiso
 * de escritura (Program Files está protegido para usuarios normales).
 *
 * Ahora ROOT es una ruta ABSOLUTA dentro de la carpeta personal del
 * usuario (~/Documents/MediaVerse/userdata), igual en todos los modos
 * de ejecución (IDE, jar suelto, o .exe empaquetado), y visible/
 * localizable con el explorador de archivos — lo que permite, por
 * ejemplo, meter esa carpeta dentro de Google Drive/OneDrive para
 * tener copia de seguridad automática del historial del usuario.
 *
 * Todos los métodos crean las carpetas intermedias necesarias antes de
 * devolver la ruta, de modo que el primer Files.write() nunca falle
 * por carpeta inexistente.
 */
public final class AppPaths {

    private AppPaths() {}

    /**
     * Carpeta raíz de todos los datos (y de las fotos de perfil).
     *
     * System.getProperty("user.home") devuelve la carpeta personal del
     * usuario del sistema operativo actual (en Windows, normalmente
     * C:\Users\<usuario>; en Linux/Mac, /home/<usuario> o
     * /Users/<usuario>), por lo que esta ruta es portable entre
     * sistemas sin necesidad de detectar el SO manualmente.
     */
    public static final String ROOT = Paths.get(
            System.getProperty("user.home"),
            "Documents",
            "MediaVerse",
            "userdata"
    ).toString();

    // Nombres de las subcarpetas por tipo dentro de cada usuario. Se
    // centralizan aquí como constantes para que un cambio de nombre sea
    // un único sitio, no una cadena suelta repetida por los servicios.
    public static final String DIR_FAVORITES = "favoritos";
    public static final String DIR_DASHBOARD = "dashboard";
    public static final String DIR_PROGRESS  = "progreso";
    public static final String DIR_PLAN      = "plan";
    public static final String DIR_FOLDERS   = "carpetas";

    /**
     * Archivo global (no pertenece a ningún usuario): <ROOT>/<fileName>.
     * Se usa para users.json y profile.json.
     */
    public static Path global(String fileName) {
        Path dir = Paths.get(ROOT);
        ensureDir(dir);
        return dir.resolve(fileName);
    }

    /**
     * Archivo de un usuario, dentro de una subcarpeta por tipo:
     * <ROOT>/<user>/<subDir>/<fileName>.
     *
     * Si no hay usuario logueado (user null/vacío) se usa "_sin_usuario"
     * como carpeta de respaldo, equivalente a los nombres genéricos
     * ("favorites.json" suelto) que los servicios usaban antes para ese
     * mismo caso — así no se pierde el comportamiento defensivo previo.
     */
    public static Path userFile(String user, String subDir, String fileName) {
        String safeUser = (user == null || user.isBlank()) ? "_sin_usuario" : user;
        Path dir = Paths.get(ROOT, safeUser, subDir);
        ensureDir(dir);
        return dir.resolve(fileName);
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("❌ No se pudo crear la carpeta de datos " + dir + ": " + e.getMessage());
        }
    }
}