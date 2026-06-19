package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.services.AuthService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    private static final String COLOR_PRIMARY = "#e94560";
    private static final String COLOR_ERROR = "#e74c3c";
    private static final String COLOR_TEXT = "#eaeaea";
    private static final String COLOR_BG_DARK = "#16213e";

    @FXML
    public void initialize() {
        // Permitir login con Enter
        passwordField.setOnAction(e -> onLogin());

        // Hover en botón
        String baseStyle = loginButton.getStyle();
        loginButton.setOnMouseEntered(e ->
                loginButton.setStyle(baseStyle.replace(COLOR_PRIMARY, "#ff6b8a")));
        loginButton.setOnMouseExited(e ->
                loginButton.setStyle(baseStyle));
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Usuario y contraseña son requeridos");
            return;
        }

        if (AuthService.login(username, password)) {
            // Login exitoso → abrir SearchView
            openSearchView();
        } else {
            showError("Usuario o contraseña incorrectos");
            passwordField.clear();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: " + COLOR_ERROR + ";");
        errorLabel.setVisible(true);
    }

    private void openSearchView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/SearchView.fxml")
            );

            Scene scene = new Scene(loader.load(), 1100, 720);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Mediaverse - Search");

        } catch (Exception e) {
            System.err.println("❌ Error al abrir SearchView: " + e.getMessage());
            e.printStackTrace();
        }
    }
}