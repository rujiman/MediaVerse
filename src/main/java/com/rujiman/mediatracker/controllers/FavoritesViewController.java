package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.services.FavoritesService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller para la vista de favoritos (overlay), con el mismo estilo
 * de tarjetas que la búsqueda y filtro por tipo de contenido.
 */
public class FavoritesViewController {

    @FXML private Button backButton;
    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterAnime;
    @FXML private ToggleButton filterSeries;
    @FXML private ToggleButton filterMovie;
    @FXML private ToggleButton filterMusic;
    @FXML private ToggleButton filterGame;

    @FXML private StackPane statusPane;
    @FXML private Label statusLabel;
    @FXML private ScrollPane scrollPane;
    @FXML private FlowPane resultsGrid;

    private final ToggleGroup filterGroup = new ToggleGroup();
    private final List<FavoriteItem> allFavorites = new ArrayList<>();

    /** Se ejecuta al pulsar "Volver" (la define quien abre esta vista). */
    private Runnable onBackAction;

    /** Se ejecuta al pinchar una tarjeta; recibe el MediaItem equivalente. */
    private java.util.function.Consumer<MediaItem> onOpenDetailAction;

    public void setOnBackAction(Runnable onBackAction) {
        this.onBackAction = onBackAction;
    }

    public void setOnOpenDetailAction(java.util.function.Consumer<MediaItem> onOpenDetailAction) {
        this.onOpenDetailAction = onOpenDetailAction;
    }

    @FXML
    public void initialize() {
        filterAll.setToggleGroup(filterGroup);
        filterAnime.setToggleGroup(filterGroup);
        filterSeries.setToggleGroup(filterGroup);
        filterMovie.setToggleGroup(filterGroup);
        filterMusic.setToggleGroup(filterGroup);
        filterGame.setToggleGroup(filterGroup);

        filterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
                return;
            }
            updateFilterButtonStyles();
        });

        // Estilo inicial correcto (Todo seleccionado por defecto)
        updateFilterButtonStyles();

        speedUpScroll(scrollPane);
        loadFavorites();
    }

    /**
     * Actualiza el estilo visual de los botones de filtro: el seleccionado
     * se pinta en rosa (color de marca), el resto en el fondo oscuro normal.
     * El ToggleGroup por sí solo solo cambia la selección lógica, no el
     * color de fondo, así que sin esto no se notaba visualmente qué
     * filtro estaba activo (mismo bug que ya arreglamos en SearchController).
     */
    private void updateFilterButtonStyles() {
        ToggleButton[] allFilters = { filterAll, filterAnime, filterSeries, filterMovie, filterMusic, filterGame };

        for (ToggleButton btn : allFilters) {
            boolean selected = btn.isSelected();
            String bgColor = selected ? "#e94560" : "#16213e";
            String textColor = selected ? "white" : "#eaeaea";
            String fontWeight = selected ? "-fx-font-weight: bold;" : "";

            btn.setStyle(
                    "-fx-background-color: " + bgColor + ";" +
                            "-fx-text-fill: " + textColor + ";" +
                            "-fx-font-size: 11px;" +
                            fontWeight +
                            "-fx-background-radius: 14;" +
                            "-fx-padding: 5 14 5 14;" +
                            "-fx-cursor: hand;"
            );
        }
    }

    @FXML
    private void onBack() {
        if (onBackAction != null) onBackAction.run();
    }

    /**
     * Carga (o recarga) los favoritos del usuario actual y aplica el filtro activo.
     * Público para poder refrescar la vista al volver desde un detalle donde
     * se haya quitado algún favorito.
     */
    public void loadFavorites() {
        allFavorites.clear();
        allFavorites.addAll(FavoritesService.getFavorites());
        applyFilter();
    }

    @FXML
    private void onFilterChanged() {
        applyFilter();
    }

    private void applyFilter() {
        Toggle selected = filterGroup.getSelectedToggle();
        MediaType typeFilter = null;

        if (selected == filterAnime) typeFilter = MediaType.ANIME;
        else if (selected == filterSeries) typeFilter = MediaType.SERIES;
        else if (selected == filterMovie) typeFilter = MediaType.MOVIE;
        else if (selected == filterMusic) typeFilter = MediaType.MUSIC;
        else if (selected == filterGame) typeFilter = MediaType.GAME;

        List<FavoriteItem> filtered = new ArrayList<>();
        for (FavoriteItem fav : allFavorites) {
            if (typeFilter == null || fav.getType() == typeFilter) {
                filtered.add(fav);
            }
        }

        renderFavorites(filtered);
    }

    private void renderFavorites(List<FavoriteItem> favorites) {
        resultsGrid.getChildren().clear();

        if (favorites.isEmpty()) {
            statusPane.setVisible(true);
            statusPane.setManaged(true);
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            statusLabel.setVisible(true);
            return;
        }

        statusPane.setVisible(false);
        statusPane.setManaged(false);
        scrollPane.setVisible(true);
        scrollPane.setManaged(true);

        for (FavoriteItem fav : favorites) {
            resultsGrid.getChildren().add(buildCard(fav));
        }
    }

    /**
     * Tarjeta visual para un favorito: imagen, badge de tipo, título, año,
     * rating y progreso de episodios si aplica. Reutiliza el mismo estilo
     * visual que las tarjetas de búsqueda.
     */
    private VBox buildCard(FavoriteItem fav) {
        VBox card = new VBox(6);
        card.setPrefWidth(160);
        card.setStyle(
                "-fx-background-color: #16213e;" +
                        "-fx-background-radius: 10;" +
                        "-fx-cursor: hand;"
        );
        card.setAlignment(Pos.TOP_LEFT);

        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(160, 220);
        imageContainer.setMaxSize(160, 220);
        imageContainer.setStyle("-fx-background-color: #0f0f1a; -fx-background-radius: 10 10 0 0;");

        ImageView cover = new ImageView();
        cover.setFitWidth(160);
        cover.setFitHeight(220);
        cover.setPreserveRatio(false);
        cover.setSmooth(true);

        Rectangle clip = new Rectangle(160, 220);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        cover.setClip(clip);

        if (fav.getImageUrl() != null && !fav.getImageUrl().isBlank()) {
            try {
                cover.setImage(new Image(fav.getImageUrl(), 160, 220, false, true, true));
            } catch (Exception ignored) {}
        }

        // Botón quitar de favoritos (esquina superior derecha)
        Button removeButton = new Button("★");
        removeButton.setTextFill(javafx.scene.paint.Color.web("#e94560"));
        removeButton.setStyle(
                "-fx-background-color: rgba(15,15,26,0.85);" +
                        "-fx-font-size: 18px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 50;" +
                        "-fx-min-width: 30; -fx-min-height: 30;" +
                        "-fx-max-width: 30; -fx-max-height: 30;" +
                        "-fx-padding: 0;" +
                        "-fx-cursor: hand;"
        );
        StackPane.setAlignment(removeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(removeButton, new Insets(6));
        removeButton.setOnAction(e -> {
            e.consume();
            FavoritesService.removeFavorite(fav.getId());
            loadFavorites(); // refrescar la grilla tras quitar
        });

        // Badge de tipo, con el color de acento de su propia sección
        Label typeBadge = new Label(typeLabel(fav.getType()));
        typeBadge.getStyleClass().add(badgeClassFor(fav.getType()));
        StackPane.setAlignment(typeBadge, Pos.TOP_LEFT);
        StackPane.setMargin(typeBadge, new Insets(6));

        imageContainer.getChildren().addAll(cover, removeButton, typeBadge);

        // Título
        Label titleLabel = new Label(fav.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(150);
        titleLabel.setStyle(
                "-fx-text-fill: #eaeaea;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 6 8 0 8;"
        );

        // Fila de meta: año, score
        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setStyle("-fx-padding: 0 8 4 8;");

        String yearText = fav.getYear() != null ? String.valueOf(fav.getYear()) : "—";
        Label yearLabel = new Label(yearText);
        yearLabel.setStyle("-fx-text-fill: #555577; -fx-font-size: 10px;");
        metaRow.getChildren().add(yearLabel);

        if (fav.getScore() != null && fav.getScore() > 0) {
            Label scoreLabel = new Label("⭐ " + fav.getScore());
            scoreLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 10px; -fx-font-weight: bold;");
            metaRow.getChildren().add(scoreLabel);
        }

        card.getChildren().addAll(imageContainer, titleLabel, metaRow);

        // Progreso de episodios / visto (siempre desde WatchProgressService,
        // independiente de favoritos, para reflejar el estado real)
        com.rujiman.mediatracker.services.WatchProgressService.Progress progress =
                com.rujiman.mediatracker.services.WatchProgressService.getProgress(fav.getTitle());

        HBox statusRow = new HBox();
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setStyle("-fx-padding: 0 8 8 8;");

        Integer total = fav.getTotalEpisodes();
        Label statusLabel;
        if (total != null && total > 0) {
            statusLabel = new Label("📺 " + progress.watchedEpisodes.size() + "/" + total + " vistos");
            statusLabel.setStyle("-fx-text-fill: #555577; -fx-font-size: 10px;");
        } else if (progress.viewed) {
            statusLabel = new Label("✔ Visto");
            statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 10px;");
        } else {
            statusLabel = new Label(" ");
        }

        // Espaciador flexible para empujar las estrellas a la derecha de la tarjeta
        HBox spacerBox = new HBox();
        HBox.setHgrow(spacerBox, Priority.ALWAYS);

        // Estrellas de valoración personal (1-5), solo se muestran si el
        // usuario ya valoró este título; si no, no ocupan espacio extra.
        Label userStars = new Label();
        Integer userRating = progress.userRating;
        if (userRating != null && userRating > 0) {
            StringBuilder starsText = new StringBuilder();
            for (int i = 0; i < userRating; i++) starsText.append("★");
            userStars.setText(starsText.toString());
            userStars.setStyle("-fx-text-fill: #f1c40f; -fx-font-size: 10px;");
        }

        statusRow.getChildren().addAll(statusLabel, spacerBox, userStars);
        card.getChildren().add(statusRow);

        card.setOnMouseClicked(e -> {
            if (onOpenDetailAction != null) {
                onOpenDetailAction.accept(fav.toMediaItem());
            }
        });

        return card;
    }

    private String typeLabel(MediaType type) {
        if (type == null) return "";
        return switch (type) {
            case ANIME -> "🎌 Anime";
            case SERIES -> "📺 Series";
            case MOVIE -> "🎬 Película";
            case MUSIC -> "🎵 Música";
            case GAME -> "🎮 Juego";
        };
    }

    /**
     * Clase CSS del badge de tipo, con el color de acento de su propia
     * sección (ver theme.css), en vez de un rojo fijo para todos los tipos.
     */
    private String badgeClassFor(MediaType type) {
        if (type == null) return "badge-series";
        return switch (type) {
            case GAME -> "badge-game";
            case SERIES -> "badge-series";
            case ANIME -> "badge-anime";
            case MUSIC -> "badge-music";
            case MOVIE -> "badge-movie";
        };
    }

    private void speedUpScroll(ScrollPane pane) {
        final double SCROLL_MULTIPLIER = 4.0;
        pane.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            double deltaY = event.getDeltaY() * SCROLL_MULTIPLIER;
            double height = pane.getContent().getBoundsInLocal().getHeight();
            double vValue = pane.getVvalue();
            pane.setVvalue(vValue - deltaY / height);
            event.consume();
        });
    }
}