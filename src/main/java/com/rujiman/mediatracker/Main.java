package com.rujiman.mediatracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Cargar LoginView en lugar de SearchView
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/com/rujiman/mediatracker/views/LoginView.fxml"));
        Scene scene = new Scene(loader.load(), 800, 600);

        // theme.css centraliza toda la paleta de colores de la app
        // ("Constelaciones"): se carga UNA vez por cada Scene real que
        // se crea (aquí, y otra vez en LoginController al pasar a
        // SearchView), y de ahí lo heredan automáticamente todas las
        // sub-vistas que se inyectan dinámicamente dentro (Home, Detail,
        // Favorites, Watchlist), porque viven en el mismo árbol de Scene.
        scene.getStylesheets().add(
                Main.class.getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm()
        );

        // Icono de la ventana / barra de tareas
        stage.getIcons().add(new Image(
                Main.class.getResourceAsStream("/com/rujiman/mediatracker/views/icons/mediaverse_logo.png")
        ));

        stage.setTitle("Mediaverse - Login");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}