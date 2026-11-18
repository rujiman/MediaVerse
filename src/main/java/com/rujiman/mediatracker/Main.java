package com.rujiman.mediatracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    /**
     * En la app instalada no hay consola (sin ventana negra al abrirla), así
     * que los mensajes de System.out/err se guardan en
     * Documentos/MediaVerse/userdata/mediaverse.log (se reescribe en cada
     * arranque). Sirve para saber qué ha pasado si alguien avisa de un error.
     * Desde el IDE (sin .jar) todo sigue saliendo por la consola.
     */
    private static void redirectLogsIfInstalled() {
        if (com.rujiman.mediatracker.services.UpdateService.runningJar() == null) return;
        try {
            java.nio.file.Path log = java.nio.file.Paths.get(
                    com.rujiman.mediatracker.services.AppPaths.ROOT, "mediaverse.log");
            java.nio.file.Files.createDirectories(log.getParent());
            java.io.PrintStream out = new java.io.PrintStream(
                    java.nio.file.Files.newOutputStream(log), true, java.nio.charset.StandardCharsets.UTF_8);
            out.println("MediaVerse " + com.rujiman.mediatracker.util.AppInfo.VERSION
                    + " · " + System.getProperty("os.name") + " · " + java.time.LocalDateTime.now());
            System.setOut(out);
            System.setErr(out);
        } catch (Exception ignored) {
            // si no se puede crear el log, la app funciona igual
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        // Tipografía Inter (incluida en la app) antes de crear ninguna Scene
        com.rujiman.mediatracker.ui.Fonts.load();

        // Cargar LoginView en lugar de SearchView
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/com/rujiman/mediatracker/views/LoginView.fxml"));
        Scene scene = new Scene(loader.load(),
                com.rujiman.mediatracker.ui.WindowSize.loginWidth(),
                com.rujiman.mediatracker.ui.WindowSize.loginHeight());

        // theme.css centraliza toda la paleta de colores de la app
        // ("Constelaciones"): se carga UNA vez por cada Scene real que
        // se crea (aquí, y otra vez en LoginController al pasar a
        // SearchView), y de ahí lo heredan automáticamente todas las
        // sub-vistas que se inyectan dinámicamente dentro (Home, Detail,
        // Favorites, Watchlist), porque viven en el mismo árbol de Scene.
        scene.getStylesheets().add(
                Main.class.getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm()
        );

        // Movimiento global: tarjetas que se elevan y botones que se hunden
        com.rujiman.mediatracker.ui.Motion.install(scene);

        // Icono de la ventana / barra de tareas
        stage.getIcons().add(new Image(
                Main.class.getResourceAsStream("/com/rujiman/mediatracker/views/icons/mediaverse_logo.png")
        ));

        stage.setTitle("Mediaverse - Login");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        redirectLogsIfInstalled();
        launch();
    }
}