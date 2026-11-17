package com.rujiman.mediatracker.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Gestiona la navegación entre vistas en la misma ventana principal.
 * Centraliza el back-stack que antes estaba mezclado en SearchController.
 *
 * Uso:
 *   NavigationManager nav = new NavigationManager(mainPane);
 *   nav.push("/views/DetailView.fxml", ctrl -> ((DetailViewController)ctrl).load(item));
 *   nav.pop();
 */
public class NavigationManager {

    private final StackPane container;
    private final Deque<Parent> backStack = new ArrayDeque<>();

    public NavigationManager(StackPane container) {
        if (container == null) throw new IllegalArgumentException("container no puede ser null");
        this.container = container;
    }

    // ── Navegación hacia adelante ──────────────────────────────────────

    /**
     * Carga un FXML, llama al configurador con el controller resultante,
     * y muestra la nueva vista encima de la actual.
     */
    public void push(String fxmlPath, Consumer<Object> configurator) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent newView = loader.load();
            Object controller = loader.getController();
            if (configurator != null) configurator.accept(controller);

            if (!container.getChildren().isEmpty()) {
                Node last = container.getChildren().get(container.getChildren().size() - 1);
            if (last instanceof Parent p) backStack.push(p);
            }
            container.getChildren().add(newView);
            newView.toFront();

        } catch (IOException e) {
            System.err.println("❌ Error al cargar vista: " + fxmlPath + " → " + e.getMessage());
        }
    }

    /** Variante sin configurador. */
    public void push(String fxmlPath) {
        push(fxmlPath, null);
    }

    // ── Navegación hacia atrás ─────────────────────────────────────────

    public void pop() {
        if (container.getChildren().size() > 1) {
            container.getChildren().remove(container.getChildren().size() - 1);
        } else if (!backStack.isEmpty()) {
            container.getChildren().setAll(backStack.pop());
        }
    }

    public boolean canGoBack() {
        return container.getChildren().size() > 1 || !backStack.isEmpty();
    }

    // ── Reemplazar todo el contenido ───────────────────────────────────

    public void replaceAll(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            backStack.clear();
            container.getChildren().setAll(view);
        } catch (IOException e) {
            System.err.println("❌ Error al reemplazar vista: " + fxmlPath + " → " + e.getMessage());
        }
    }

    // ── Ventana nueva ──────────────────────────────────────────────────

    public static void openInNewWindow(String fxmlPath, String title,
                                       double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(NavigationManager.class.getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, width, height));
            stage.setTitle(title);
            stage.show();
        } catch (IOException e) {
            System.err.println("❌ Error al abrir ventana: " + fxmlPath + " → " + e.getMessage());
        }
    }

    public void closeCurrentWindow() {
        Stage stage = (Stage) container.getScene().getWindow();
        stage.close();
    }
}
