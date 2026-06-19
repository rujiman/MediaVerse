package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.services.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class RegisterWindowController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmField;
    @FXML private Label messageLabel;
    @FXML private Button switchToLoginButton;

    @FXML
    private void onCreateAccount() {
        String user = usernameField.getText().trim();
        String pass = passwordField.getText().trim();
        String confirm = confirmField.getText().trim();

        if (user.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            messageLabel.setText("Todos los campos son obligatorios");
            return;
        }

        if (!pass.equals(confirm)) {
            messageLabel.setText("Las contraseñas no coinciden");
            return;
        }

        if (AuthService.register(user, pass, confirm)) {
            messageLabel.setStyle("-fx-text-fill: #2ecc71;");
            messageLabel.setText("Cuenta creada correctamente");

            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    javafx.application.Platform.runLater(() -> {
                        Stage stage = (Stage) usernameField.getScene().getWindow();
                        stage.close();
                    });
                }
            }, 1000);

        } else {
            messageLabel.setText("El usuario ya existe");
        }
    }

    @FXML
    private void switchToLogin() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }
}
