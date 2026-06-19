package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SearchController {

    // -------------------------
    // ELEMENTOS YA EXISTENTES
    // -------------------------
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Label statusLabel;
    @FXML private StackPane statusPane;

    // -------------------------
    // RESULTADOS / FILTROS
    // -------------------------
    @FXML private ScrollPane scrollPane;
    @FXML private FlowPane resultsGrid;
    @FXML private ProgressIndicator loadingSpinner;

    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterAnime;
    @FXML private ToggleButton filterSeries;
    @FXML private ToggleButton filterMovie;
    @FXML private ToggleButton filterMusic;
    @FXML private ToggleButton filterGame;

    private final ToggleGroup filterGroup = new ToggleGroup();

    // Resultados de la última búsqueda (sin filtrar), para poder re-filtrar sin re-buscar
    private final List<MediaItem> lastResults = new ArrayList<>();

    // -------------------------
    // NUEVOS ELEMENTOS DEL MENÚ
    // -------------------------
    @FXML private StackPane sideMenu;
    @FXML private StackPane profileIconContainer;
    @FXML private ImageView profileIcon;
    @FXML private Label profileInitialLabel;
    @FXML private ImageView profilePicture;
    @FXML private Label profileInitialLabelBig;
    @FXML private Label usernameLabel;

    private boolean menuOpen = false;

    // -------------------------
    // INICIALIZACIÓN
    // -------------------------
    @FXML
    public void initialize() {

        // Cargar foto de perfil si existe; si no, se queda el círculo con la inicial
        File profileFile = new File("userdata/profile.png");
        if (profileFile.exists()) {
            Image img = new Image(profileFile.toURI().toString());
            profileIcon.setImage(img);
            profilePicture.setImage(img);
            profileIcon.setVisible(true);
            profilePicture.setVisible(true);
            profileInitialLabel.setVisible(false);
            profileInitialLabelBig.setVisible(false);
        }

        // Cargar nombre de usuario (placeholder)
        usernameLabel.setText("Usuario");
        updateProfileInitial(usernameLabel.getText());

        // Ocultar menú al inicio
        sideMenu.setTranslateX(-240);
        sideMenu.setVisible(false);

        // Agrupar los filtros para que solo uno esté activo a la vez
        filterAll.setToggleGroup(filterGroup);
        filterAnime.setToggleGroup(filterGroup);
        filterSeries.setToggleGroup(filterGroup);
        filterMovie.setToggleGroup(filterGroup);
        filterMusic.setToggleGroup(filterGroup);
        filterGame.setToggleGroup(filterGroup);

        // Evitar que el usuario pueda dejar el grupo sin ninguna selección
        filterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    /**
     * Muestra la primera letra del nombre de usuario en los círculos
     * placeholder (icono pequeño y foto grande del panel lateral).
     */
    private void updateProfileInitial(String username) {
        String initial = (username != null && !username.isBlank())
                ? username.trim().substring(0, 1).toUpperCase()
                : "U";
        profileInitialLabel.setText(initial);
        profileInitialLabelBig.setText(initial);
    }

    // -------------------------
    // BÚSQUEDA
    // -------------------------
    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            statusLabel.setText("Escribe algo para buscar ✨");
            return;
        }

        statusLabel.setText("Buscando \"" + query + "\"...");

        // TODO: aquí conectarás los servicios reales (TMDBService, AnilistService,
        // MusicService, GameService) y llamarás a setResults(...) con lo que devuelvan.
    }

    /**
     * Llamar a este método cuando lleguen resultados nuevos de la búsqueda
     * (por ejemplo, desde los services). Guarda los resultados sin filtrar
     * y aplica el filtro actualmente seleccionado.
     */
    public void setResults(List<MediaItem> items) {
        lastResults.clear();
        if (items != null) lastResults.addAll(items);
        applyFilter();
    }

    // -------------------------
    // FILTROS POR TIPO
    // -------------------------
    @FXML
    private void onFilterChanged() {
        applyFilter();
    }

    private void applyFilter() {
        Toggle selected = filterGroup.getSelectedToggle();
        MediaType typeFilter = null; // null = sin filtro (mostrar todo)

        if (selected == filterAnime) {
            typeFilter = MediaType.ANIME;
        } else if (selected == filterSeries) {
            typeFilter = MediaType.SERIES;
        } else if (selected == filterMovie) {
            typeFilter = MediaType.MOVIE;
        } else if (selected == filterMusic) {
            typeFilter = MediaType.MUSIC;
        } else if (selected == filterGame) {
            typeFilter = MediaType.GAME;
        }
        // Si selected == filterAll (o ninguno), typeFilter se queda en null

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : lastResults) {
            if (typeFilter == null || item.getType() == typeFilter) {
                filtered.add(item);
            }
        }

        renderResults(filtered);
    }

    /**
     * Pinta los resultados filtrados en el FlowPane.
     * TODO: sustituir el Label de marcador por tu componente de tarjeta real
     * (por ejemplo, un VBox con ImageView + título, abriendo DetailView al click).
     */
    private void renderResults(List<MediaItem> items) {
        resultsGrid.getChildren().clear();

        if (items.isEmpty()) {
            statusPane.setVisible(true);
            scrollPane.setVisible(false);
            statusLabel.setText("Sin resultados para este filtro 🔍");
            statusLabel.setVisible(true);
            loadingSpinner.setVisible(false);
            return;
        }

        statusPane.setVisible(false);
        scrollPane.setVisible(true);

        for (MediaItem item : items) {
            Label card = new Label(item.getTitle());
            card.setStyle(
                    "-fx-background-color: #16213e;" +
                            "-fx-text-fill: #eaeaea;" +
                            "-fx-padding: 12;" +
                            "-fx-background-radius: 10;" +
                            "-fx-pref-width: 160;" +
                            "-fx-pref-height: 90;"
            );
            resultsGrid.getChildren().add(card);
        }
    }

    // -------------------------
    // MENÚ LATERAL
    // -------------------------
    @FXML
    private void toggleProfileMenu() {
        if (menuOpen) closeMenu();
        else openMenu();
    }

    private void openMenu() {
        sideMenu.setVisible(true);

        TranslateTransition slide = new TranslateTransition(Duration.millis(250), sideMenu);
        slide.setFromX(-240);
        slide.setToX(0);
        slide.play();

        menuOpen = true;
    }

    private void closeMenu() {
        TranslateTransition slide = new TranslateTransition(Duration.millis(250), sideMenu);
        slide.setFromX(0);
        slide.setToX(-240);
        slide.setOnFinished(e -> sideMenu.setVisible(false));
        slide.play();

        menuOpen = false;
    }

    // -------------------------
    // FOTO DE PERFIL
    // -------------------------
    @FXML
    private void onChangeProfilePicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona una foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg")
        );

        File file = chooser.showOpenDialog(profilePicture.getScene().getWindow());
        if (file != null) {
            Image img = new Image(file.toURI().toString());
            profileIcon.setImage(img);
            profilePicture.setImage(img);
            profileIcon.setVisible(true);
            profilePicture.setVisible(true);
            profileInitialLabel.setVisible(false);
            profileInitialLabelBig.setVisible(false);

            // Guardar en userdata/
            File dest = new File("userdata/profile.png");
            dest.getParentFile().mkdirs();
            file.renameTo(dest);
        }
    }

    // -------------------------
    // CAMBIAR NOMBRE DE USUARIO
    // -------------------------
    @FXML
    private void onChangeUsername() {
        TextInputDialog dialog = new TextInputDialog(usernameLabel.getText());
        dialog.setTitle("Cambiar nombre de usuario");
        dialog.setHeaderText("Introduce tu nuevo nombre de usuario");
        dialog.setContentText("Nuevo nombre:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName.trim().length() < 3) {
                showAlert("El nombre debe tener al menos 3 caracteres.");
                return;
            }

            usernameLabel.setText(newName);
            updateProfileInitial(newName);
            showAlert("Nombre de usuario actualizado.");
        });
    }

    // -------------------------
    // CAMBIAR CONTRASEÑA
    // -------------------------
    @FXML
    private void onChangePassword() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Cambiar contraseña");

        PasswordField pass1 = new PasswordField();
        pass1.setPromptText("Nueva contraseña");

        PasswordField pass2 = new PasswordField();
        pass2.setPromptText("Confirmar contraseña");

        VBox box = new VBox(10, pass1, pass2);
        dialog.getDialogPane().setContent(box);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) return pass1.getText();
            return null;
        });

        dialog.showAndWait().ifPresent(pass -> {
            if (!pass1.getText().equals(pass2.getText())) {
                showAlert("Las contraseñas no coinciden.");
                return;
            }

            if (pass1.getText().length() < 4) {
                showAlert("La contraseña debe tener al menos 4 caracteres.");
                return;
            }

            showAlert("Contraseña actualizada.");
        });
    }

    // -------------------------
    // SECCIONES
    // -------------------------
    @FXML private void openFavorites() { showAlert("Abrir favoritos"); }
    @FXML private void openWatchedSeries() { showAlert("Abrir series vistas"); }
    @FXML private void openWatchingSeries() { showAlert("Abrir series viendo"); }
    @FXML private void openWatchedMovies() { showAlert("Abrir películas vistas"); }
    @FXML private void openGames() { showAlert("Abrir videojuegos"); }

    // -------------------------
    // LOGOUT
    // -------------------------
    @FXML
    private void logout() {
        showAlert("Sesión cerrada.");
        Stage stage = (Stage) searchField.getScene().getWindow();
        stage.close();
    }

    // -------------------------
    // UTILIDAD
    // -------------------------
    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

}