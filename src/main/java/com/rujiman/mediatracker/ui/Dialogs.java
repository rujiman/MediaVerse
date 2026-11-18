package com.rujiman.mediatracker.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

/**
 * Diálogos con la paleta oscura de la app. JavaFX los pinta claros por
 * defecto: el DialogPane, la franja de cabecera ("header-panel") y la
 * ventana que lo envuelve son nodos distintos y hay que oscurecerlos
 * todos. Antes este código estaba copiado en 4 controladores.
 */
public final class Dialogs {

    private Dialogs() {}

    /** Aplica el estilo oscuro completo a un diálogo (contenido + cabecera + ventana). */
    public static void styleDark(DialogPane pane) {
        pane.getStylesheets().add(Dialogs.class.getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm());
        pane.getStyleClass().add("bg-panel-flat");
        pane.setStyle("-fx-background-color: " + Palette.PANEL_ALT + "; -fx-text-fill: " + Palette.TEXT + ";");
        pane.applyCss();

        Node header = pane.lookup(".header-panel");
        if (header != null) {
            header.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");
        } else {
            // La cabecera no existe hasta que el diálogo se muestra
            Platform.runLater(() -> {
                Node h = pane.lookup(".header-panel");
                if (h != null) h.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");
            });
        }
        forceLabelColor(pane, Palette.TEXT);
        darkenWindow(pane);
    }

    /** Fondo oscuro de la ventana nativa que envuelve el diálogo. */
    public static void darkenWindow(DialogPane pane) {
        if (pane.getScene() != null) {
            pane.getScene().setFill(Color.web(Palette.BG));
        } else {
            pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) newScene.setFill(Color.web(Palette.BG));
            });
        }
    }

    /**
     * Ventana padre del diálogo: sin ella, al cerrar diálogos encadenados
     * Windows no sabe a qué ventana volver y la app se iba al fondo.
     */
    public static void own(Dialog<?> dialog, Node anyNodeOfTheApp) {
        if (anyNodeOfTheApp != null && anyNodeOfTheApp.getScene() != null && anyNodeOfTheApp.getScene().getWindow() != null) {
            dialog.initOwner(anyNodeOfTheApp.getScene().getWindow());
        }
    }

    private static void forceLabelColor(Parent parent, String hex) {
        for (Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof Label label) label.setStyle("-fx-text-fill: " + hex + ";");
            if (node instanceof Parent child) forceLabelColor(child, hex);
        }
    }
}
