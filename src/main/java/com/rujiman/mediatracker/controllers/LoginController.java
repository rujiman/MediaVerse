package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.ui.Palette;
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


    @FXML private javafx.scene.layout.GridPane brandMosaic;
    @FXML private StackPane brandPane;

    @FXML
    public void initialize() {
        passwordLoginField.setOnAction(e -> onLogin());
        usernameLoginField.setOnAction(e -> passwordLoginField.requestFocus());
        confirmPasswordField.setOnAction(e -> onRegister());
        setupButtonHovers();
        if (!com.rujiman.mediatracker.util.ConfigLoader.needsSetup()) {
            showLoginPanel();
        } else {
            showSetupPanel();
        }

        // El mosaico está girado y es más grande que su panel: se recorta
        // para que no invada la zona del formulario
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(brandPane.widthProperty());
        clip.heightProperty().bind(brandPane.heightProperty());
        brandPane.setClip(clip);

        // El mosaico (texto girado) se dibuja UNA vez como imagen y se mueve
        // esa imagen: si no, JavaFX redibuja el texto en cada fotograma
        // ajustándolo a píxeles y las letras "tiemblan".
        brandMosaic.setCache(true);
        brandMosaic.setCacheHint(javafx.scene.CacheHint.SPEED);

        // El mosaico de la izquierda flota muy despacio, arriba y abajo
        javafx.animation.TranslateTransition drift = new javafx.animation.TranslateTransition(Duration.seconds(9), brandMosaic);
        drift.setFromY(-18);
        drift.setToY(18);
        drift.setAutoReverse(true);
        drift.setCycleCount(javafx.animation.Animation.INDEFINITE);
        drift.setInterpolator(com.rujiman.mediatracker.ui.Motion.EASE_IN_OUT);
        drift.play();
    }

    // CAMBIAR A REGISTRO
    @FXML
    private void switchToRegister() {
        showRegisterPanel();
    }

    // VOLVER A LOGIN
    @FXML
    private void switchToLogin() {
        showLoginPanel();
    }

    // MOSTRAR LOGIN
    private void showLoginPanel() {
        fadeOut(RegisterPanel, () -> {
            RegisterPanel.setVisible(false);
            LoginPanel.setVisible(true);
            fadeIn(LoginPanel, null);
        });
        updateWindowTitle("Mediaverse - Iniciar sesión");
    }

    // MOSTRAR REGISTRO
    /**
     * Primera vez (o sin claves): asistente de claves de API antes del
     * login. Al terminar se pasa al formulario de inicio de sesión.
     */
    private void showSetupPanel() {
        LoginPanel.setVisible(false);
        RegisterPanel.setVisible(false);
        javafx.scene.Node[] holder = new javafx.scene.Node[1];
        holder[0] = com.rujiman.mediatracker.ui.ApiKeysWizard.create(true, () ->
                com.rujiman.mediatracker.ui.Motion.exit(holder[0], () -> {
                    mainStack.getChildren().remove(holder[0]);
                    LoginPanel.setVisible(true);
                    fadeIn(LoginPanel, null);
                    updateWindowTitle("Mediaverse - Iniciar sesión");
                }));
        mainStack.getChildren().add(holder[0]);
        updateWindowTitle("Mediaverse - Configuración");
    }

    private void showRegisterPanel() {
        fadeOut(LoginPanel, () -> {
            LoginPanel.setVisible(false);
            RegisterPanel.setVisible(true);

            fadeIn(RegisterPanel, null);
        });
        updateWindowTitle("Mediaverse - Crear cuenta");
    }


    // LOGIN
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
        loginErrorLabel.setStyle("-fx-text-fill: " + Palette.ERROR + ";");
        loginErrorLabel.setVisible(true);
        loginErrorLabel.setManaged(true);
    }

    // REGISTRO
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
        registerErrorLabel.setStyle("-fx-text-fill: " + Palette.ERROR + ";");
        registerErrorLabel.setVisible(true);
        registerErrorLabel.setManaged(true);
    }

    private void showRegisterSuccess(String message) {
        registerErrorLabel.setText(message);
        registerErrorLabel.setStyle("-fx-text-fill: " + Palette.SUCCESS + ";");
        registerErrorLabel.setVisible(true);
        registerErrorLabel.setManaged(true);
    }

    // ABRIR SEARCHVIEW
    private void openSearchView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/SearchView.fxml")
            );

            Scene scene = new Scene(loader.load(), 1100, 720);

            // theme.css también aquí: SearchView es una Scene nueva e
            // independiente de la de Login, así que necesita su propia
            // referencia a la hoja (no la hereda de la Scene anterior).
            scene.getStylesheets().add(
                    getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm()
            );

            com.rujiman.mediatracker.ui.Motion.install(scene);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Mediaverse - Search");

            // Maximizar al entrar: el login se queda en su tamaño normal,
            // pero la app principal (SearchView) se abre ocupando toda la
            // pantalla, en vez de la ventana pequeña de tamaño fijo de antes.
            stage.setMaximized(true);

        } catch (Exception e) {
            System.err.println("❌ Error al abrir SearchView: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ANIMACIONES
    /**
     * Actualiza el título de la ventana (la barra de Windows) para que
     * refleje en qué panel está realmente el usuario (Login o Registro),
     * en vez de quedarse fijo en "Mediaverse - Login" sin importar a
     * cuál de los dos paneles se cambie. Defensivo ante el caso de que
     * la Scene/Stage todavía no exista (puede pasar la primera vez que
     * se llama, desde initialize(), antes de que el nodo esté
     * completamente insertado en el árbol de la escena).
     */
    private void updateWindowTitle(String title) {
        if (mainStack.getScene() != null && mainStack.getScene().getWindow() != null) {
            ((Stage) mainStack.getScene().getWindow()).setTitle(title);
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
        // Entrada "estilo Apple": el formulario sube un poco mientras aparece
        javafx.animation.Animation a = com.rujiman.mediatracker.ui.Motion.enter(vbox);
        a.setOnFinished(e -> { if (onFinish != null) onFinish.run(); });
    }

    // HOVERS
    private void setupButtonHovers() {
        if (loginButton != null) setupButtonHover(loginButton);
        if (registerButton != null) setupButtonHover(registerButton);
        if (switchToRegisterButton != null) setupButtonHover(switchToRegisterButton);
        if (switchToLoginButton != null) setupButtonHover(switchToLoginButton);
    }

    /**
     * Hover para los botones principales (Iniciar sesión/Crear cuenta,
     * con fondo de degradado multicolor) y los de "cambiar de panel"
     * (texto plano #ec4d80). Antes esto buscaba COLOR_PRIMARY dentro del
     * string de estilo y lo sustituía por un tono más claro — funcionaba
     * mientras el fondo era un color plano, pero deja de tener sentido
     * con un fondo linear-gradient(...) (la cadena ya no aparece en el
     * estilo, así que el reemplazo no encontraría nada que cambiar, y el
     * hover se quedaría sin efecto visible, sin lanzar ningún error).
     * En su lugar, se aplica un leve aumento de opacidad al entrar el
     * ratón, que funciona igual de bien sobre cualquier fondo, sea un
     * color plano o un degradado.
     */
    private void setupButtonHover(Button btn) {
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
    }
}