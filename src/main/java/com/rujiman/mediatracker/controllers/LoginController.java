package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.services.AuthService;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.stage.Stage;

public class LoginController {

    @FXML private StackPane mainStack;

    // LOGIN PANEL (MAYÚSCULAS, IGUAL QUE EN EL FXML)
    @FXML private VBox LoginPanel;
    @FXML private TextField usernameLoginField;
    @FXML private PasswordField passwordLoginField;
    @FXML private Button loginButton;
    @FXML private Label loginErrorLabel;
    @FXML private Button switchToRegisterButton;

    // REGISTER PANEL (MAYÚSCULAS, IGUAL QUE EN EL FXML)
    @FXML private VBox RegisterPanel;
    @FXML private TextField usernameRegisterField;
    @FXML private PasswordField passwordRegisterField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Label registerErrorLabel;
    @FXML private Button switchToLoginButton;

    private static final String COLOR_PRIMARY = "#e94560";
    private static final String COLOR_ERROR = "#e74c3c";
    private static final String COLOR_SUCCESS = "#2ecc71";

    @FXML
    public void initialize() {
        passwordLoginField.setOnAction(e -> onLogin());
        confirmPasswordField.setOnAction(e -> onRegister());
        setupButtonHovers();
        showLoginPanel();
    }

    @FXML
    private void switchToRegister() {
        showRegisterPanel();
    }

    @FXML
    private void switchToLogin() {
        showLoginPanel();
    }

    private void showLoginPanel() {
        fadeOut(RegisterPanel, () -> {
            RegisterPanel.setVisible(false);
            LoginPanel.setVisible(true);
            fadeIn(LoginPanel, null);
        });
    }

    private void showRegisterPanel() {
        fadeOut(LoginPanel, () -> {
            LoginPanel.setVisible(false);
            RegisterPanel.setVisible(true);
            fadeIn(RegisterPanel, null);
        });
    }

    @FXML
    private void onLogin() {
        String username = usernameLoginField.getText().trim();
        String password = passwordLoginField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showLoginError("Usuario y contraseña son requeridos");
            return;
        }

        if (AuthService.login(username, password)) {
            openSearchView();
        } else {
            showLoginError("Usuario o contraseña incorrectos");
            passwordLoginField.clear();
        }
    }

    private void showLoginError(String message) {
        loginErrorLabel.setText(message);
        loginErrorLabel.setStyle("-fx-text-fill: " + COLOR_ERROR + ";");
        loginErrorLabel.setVisible(true);
    }

    @FXML
    private void onRegister() {
        String username = usernameRegisterField.getText().trim();
        String password = passwordRegisterField.getText().trim();
        String confirm = confirmPasswordField.getText().trim();

        if (username.isEmpty()) {
            showRegisterError("El usuario no puede estar vacío");
            return;
        }

        if (username.length() < 3) {
            showRegisterError("El usuario debe tener al menos 3 caracteres");
            return;
        }

        if (password.isEmpty()) {
            showRegisterError("La contraseña no puede estar vacía");
            return;
        }

        if (password.length() < 4) {
            showRegisterError("La contraseña debe tener al menos 4 caracteres");
            return;
        }

        if (!password.equals(confirm)) {
            showRegisterError("Las contraseñas no coinciden");
            confirmPasswordField.clear();
            return;
        }

        if (AuthService.register(username, password, confirm)) {
            showRegisterSuccess("¡Registro exitoso! Ahora inicia sesión");
            usernameRegisterField.clear();
            passwordRegisterField.clear();
            confirmPasswordField.clear();

            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            javafx.application.Platform.runLater(LoginController.this::switchToLogin);
                        }
                    },
                    2000
            );
        } else {
            showRegisterError("Error en el registro. El usuario podría ya existir");
        }
    }

    private void showRegisterError(String message) {
        registerErrorLabel.setText(message);
        registerErrorLabel.setStyle("-fx-text-fill: " + COLOR_ERROR + ";");
        registerErrorLabel.setVisible(true);
    }

    private void showRegisterSuccess(String message) {
        registerErrorLabel.setText(message);
        registerErrorLabel.setStyle("-fx-text-fill: " + COLOR_SUCCESS + ";");
        registerErrorLabel.setVisible(true);
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

    private void fadeOut(VBox vbox, Runnable onFinish) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), vbox);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> { if (onFinish != null) onFinish.run(); });
        fade.play();
    }

    private void fadeIn(VBox vbox, Runnable onFinish) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), vbox);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setOnFinished(e -> { if (onFinish != null) onFinish.run(); });
        fade.play();
    }

    private void setupButtonHovers() {
        if (loginButton != null) setupButtonHover(loginButton);
        if (registerButton != null) setupButtonHover(registerButton);
        if (switchToRegisterButton != null) setupButtonHover(switchToRegisterButton);
        if (switchToLoginButton != null) setupButtonHover(switchToLoginButton);
    }

    private void setupButtonHover(Button btn) {
        String baseStyle = btn.getStyle();
        btn.setOnMouseEntered(e -> btn.setStyle(baseStyle.replace(COLOR_PRIMARY, "#ff6b8a")));
        btn.setOnMouseExited(e -> btn.setStyle(baseStyle));
    }
}
