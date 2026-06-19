package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.services.AnilistService;
import com.rujiman.mediatracker.services.TMDBService;
import com.rujiman.mediatracker.services.MusicService;
import com.rujiman.mediatracker.services.GameService;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class SearchController {

    // ===== FXML =====
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private FlowPane resultsGrid;
    @FXML private ScrollPane scrollPane;
    @FXML private StackPane statusPane;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterAnime;
    @FXML private ToggleButton filterSeries;
    @FXML private ToggleButton filterMovie;
    @FXML private ToggleButton filterMusic;
    @FXML private ToggleButton filterGame;

    // ===== Servicios =====
    private final AnilistService anilist = new AnilistService();
    private final TMDBService tmdb = new TMDBService();
    private final MusicService music = new MusicService();
    private final GameService games = new GameService();

    // ===== Estado =====
    private List<MediaItem> allResults = new ArrayList<>();
    private ToggleGroup filterGroup;
    private MediaType activeFilter = null;

    // ===== Colores =====
    private static final String COLOR_PRIMARY   = "#e94560";
    private static final String COLOR_BG_CARD   = "#1a1a2e";
    private static final String COLOR_BG_DARK   = "#16213e";
    private static final String COLOR_TEXT       = "#eaeaea";
    private static final String COLOR_TEXT_DIM   = "#555577";
    private static final String COLOR_GREEN      = "#2ecc71";
    private static final String COLOR_YELLOW     = "#f39c12";
    private static final String COLOR_RED        = "#e74c3c";

    // ===== Badges =====
    private static final String BADGE_ANIME  = "🎌 ANIME";
    private static final String BADGE_SERIES = "📺 SERIE";
    private static final String BADGE_MOVIE  = "🎬 PELÍCULA";
    private static final String BADGE_MUSIC  = "🎵 MÚSICA";
    private static final String BADGE_GAME   = "🎮 JUEGO";

    @FXML
    public void initialize() {

        searchField.setOnAction(e -> onSearch());

        // Hover en botón Buscar
        String baseStyle = searchButton.getStyle();
        searchButton.setOnMouseEntered(e ->
                searchButton.setStyle(baseStyle.replace(COLOR_PRIMARY, "#ff6b8a")));
        searchButton.setOnMouseExited(e ->
                searchButton.setStyle(baseStyle));

        // Grupo de filtros
        filterGroup = new ToggleGroup();
        filterAll.setToggleGroup(filterGroup);
        filterAnime.setToggleGroup(filterGroup);
        filterSeries.setToggleGroup(filterGroup);
        filterMovie.setToggleGroup(filterGroup);
        filterMusic.setToggleGroup(filterGroup);
        filterGame.setToggleGroup(filterGroup);

        filterGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null && oldVal != null) oldVal.setSelected(true);
        });

        updateFilterStyles();
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        showLoading(true);
        resultsGrid.getChildren().clear();

        new Thread(() -> {
            List<MediaItem> results = new ArrayList<>();

            try { results.addAll(anilist.search(query)); } catch (Exception ignored) {}
            try { results.addAll(tmdb.searchSeries(query)); } catch (Exception ignored) {}
            try { results.addAll(tmdb.searchMovies(query)); } catch (Exception ignored) {}
            try { results.addAll(music.search(query)); } catch (Exception ignored) {}
            try { results.addAll(games.search(query)); } catch (Exception ignored) {}

            Platform.runLater(() -> {
                allResults = results;
                applyFilterAndDisplay();
            });

        }).start();
    }

    @FXML
    private void onFilterChanged() {
        if (filterAnime.isSelected()) activeFilter = MediaType.ANIME;
        else if (filterSeries.isSelected()) activeFilter = MediaType.SERIES;
        else if (filterMovie.isSelected()) activeFilter = MediaType.MOVIE;
        else if (filterMusic.isSelected()) activeFilter = MediaType.MUSIC;
        else if (filterGame.isSelected()) activeFilter = MediaType.GAME;
        else activeFilter = null;

        updateFilterStyles();
        applyFilterAndDisplay();
    }

    private void updateFilterStyles() {
        List<ToggleButton> all = List.of(filterAll, filterAnime, filterSeries, filterMovie, filterMusic, filterGame);
        for (ToggleButton btn : all) {
            if (btn.isSelected()) {
                btn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 14; -fx-padding: 5 14; -fx-cursor: hand;");
            } else {
                btn.setStyle("-fx-background-color: " + COLOR_BG_DARK + "; -fx-text-fill: " + COLOR_TEXT + "; -fx-font-size: 11px; -fx-background-radius: 14; -fx-padding: 5 14; -fx-cursor: hand;");
            }
        }
    }

    private void applyFilterAndDisplay() {
        List<MediaItem> filtered =
                activeFilter == null
                        ? allResults
                        : allResults.stream().filter(i -> i.getType() == activeFilter).toList();

        displayResults(filtered);
    }

    private void displayResults(List<MediaItem> results) {
        showLoading(false);

        if (results.isEmpty()) {
            showStatus(activeFilter == null
                    ? "⚠️ No se encontraron resultados"
                    : "⚠️ Sin resultados para este filtro");
            return;
        }

        statusPane.setVisible(false);
        scrollPane.setVisible(true);

        for (int i = 0; i < results.size(); i++) {
            resultsGrid.getChildren().add(createCard(results.get(i), i));
        }
    }

    private VBox createCard(MediaItem item, int index) {

        VBox card = new VBox(8);
        card.setPrefWidth(150);
        card.setMaxWidth(150);
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle(
                "-fx-background-color: " + COLOR_BG_CARD + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-padding: 0 0 10 0;" +
                        "-fx-cursor: hand;"
        );

        DropShadow shadow = new DropShadow(15, Color.web("#000000bb"));
        card.setEffect(shadow);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(150);
        imageView.setFitHeight(210);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setCache(true);
        imageView.setImage(createPlaceholder());

        String imageUrl = item.getImageUrl();
        if (imageUrl != null && !imageUrl.isBlank()) {
            new Thread(() -> {
                try {
                    Image img = new Image(imageUrl, 600, 840, true, true, true);
                    Platform.runLater(() -> {
                        imageView.setImage(img);
                        FadeTransition fadeImg = new FadeTransition(Duration.millis(350), imageView);
                        fadeImg.setFromValue(0.2);
                        fadeImg.setToValue(1.0);
                        fadeImg.play();
                    });
                } catch (Exception ignored) {}
            }).start();
        }

        Label typeLabel = new Label(getBadgeText(item.getType()));
        typeLabel.setStyle(
                "-fx-background-color: " + getBadgeColor(item.getType()) + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 9px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 4;" +
                        "-fx-padding: 2 6;"
        );

        Label titleLabel = new Label(item.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(130);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setStyle(
                "-fx-text-fill: " + COLOR_TEXT + ";" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;"
        );

        Label yearLabel = new Label(item.getYear() != null ? String.valueOf(item.getYear()) : "—");
        yearLabel.setStyle("-fx-text-fill: " + COLOR_TEXT_DIM + "; -fx-font-size: 10px;");

        int score = item.getScore() != null ? item.getScore() : 0;
        String scoreColor = score >= 75 ? COLOR_GREEN : score >= 50 ? COLOR_YELLOW : COLOR_RED;
        Label scoreLabel = new Label(score > 0 ? "⭐ " + score + "/100" : "Sin puntuación");
        scoreLabel.setStyle("-fx-text-fill: " + scoreColor + "; -fx-font-size: 10px;");

        if (item.getPlatforms() != null && !item.getPlatforms().isEmpty()) {
            Label platLabel = new Label(String.join(" · ", item.getPlatforms().stream().limit(2).toList()));
            platLabel.setMaxWidth(130);
            platLabel.setWrapText(true);
            platLabel.setTextAlignment(TextAlignment.CENTER);
            platLabel.setAlignment(Pos.CENTER);
            platLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 9px;");
            card.getChildren().addAll(imageView, typeLabel, titleLabel, yearLabel, scoreLabel, platLabel);
        } else {
            card.getChildren().addAll(imageView, typeLabel, titleLabel, yearLabel, scoreLabel);
        }

        card.setOpacity(0);
        PauseTransition delay = new PauseTransition(Duration.millis(index * 55L));
        delay.setOnFinished(e -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), card);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            TranslateTransition slideUp = new TranslateTransition(Duration.millis(400), card);
            slideUp.setFromY(24);
            slideUp.setToY(0);

            new ParallelTransition(fadeIn, slideUp).play();
        });
        delay.play();

        card.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), card);
            scale.setToX(1.06);
            scale.setToY(1.06);
            scale.play();
            card.setEffect(new DropShadow(28, Color.web(COLOR_PRIMARY + "99")));
        });

        card.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), card);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
            card.setEffect(shadow);
        });

        card.setOnMouseClicked(e -> {
            String url = item.getExternalUrl();
            if (url != null && !url.isBlank()) {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ignored) {}
            }
        });

        return card;
    }

    private String getBadgeText(MediaType type) {
        return switch (type) {
            case ANIME -> BADGE_ANIME;
            case SERIES -> BADGE_SERIES;
            case MOVIE -> BADGE_MOVIE;
            case MUSIC -> BADGE_MUSIC;
            case GAME -> BADGE_GAME;
            default -> "• OTRO";
        };
    }

    private String getBadgeColor(MediaType type) {
        return switch (type) {
            case ANIME -> "#e94560";
            case SERIES -> "#0078d4";
            case MOVIE -> "#7b2d8b";
            case MUSIC -> "#1db954";
            case GAME -> "#f39c12";
            default -> "#555577";
        };
    }

    private Image createPlaceholder() {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(150, 210);
        var gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web(COLOR_BG_DARK));
        gc.fillRoundRect(0, 0, 150, 210, 12, 12);
        gc.setFill(Color.web(COLOR_TEXT_DIM));
        gc.setFont(javafx.scene.text.Font.font(28));
        gc.fillText("🎬", 52, 118);
        return canvas.snapshot(null, null);
    }

    private void showLoading(boolean loading) {
        statusPane.setVisible(true);
        scrollPane.setVisible(false);
        statusLabel.setVisible(!loading);
        loadingSpinner.setVisible(loading);

        if (loading) {
            FadeTransition fade = new FadeTransition(Duration.millis(200), loadingSpinner);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
    }

    private void showStatus(String message) {
        statusPane.setVisible(true);
        scrollPane.setVisible(false);
        loadingSpinner.setVisible(false);
        statusLabel.setVisible(true);
        statusLabel.setText(message);
    }
}
