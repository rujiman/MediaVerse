package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.WatchProgressService;
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
import java.util.function.Predicate;

/**
 * Controller reutilizable para las 4 listas de seguimiento del menú
 * lateral: Series vistas, Series viendo, Anime vistos, Anime viendo.
 *
 * A diferencia de "Mis favoritos" (que muestra TODO y deja elegir el
 * filtro con botones), esta vista siempre llega YA filtrada por un
 * MediaType concreto y un criterio de progreso concreto, configurados
 * desde quien la abre (SearchController) mediante setFilter().
 *
 * Por qué separar Series de Anime en vez de una sola lista "Series y
 * anime vistos": TMDB y AniList son catálogos independientes que no se
 * conocen entre sí, así que el mismo título (ej. "Attack on Titan")
 * puede existir como dos favoritos distintos: uno de tipo SERIES (TMDB)
 * y otro de tipo ANIME (AniList). Mezclarlos en una sola lista
 * confundiría al usuario sobre cuál es cuál; separarlos por tipo deja
 * claro con qué versión está interactuando en cada momento.
 */
public class WatchlistViewController {

    @FXML private Button backButton;
    @FXML private Label titleLabel;
    @FXML private StackPane statusPane;
    @FXML private Label statusLabel;
    @FXML private ScrollPane scrollPane;
    @FXML private FlowPane resultsGrid;

    private MediaType typeFilter;
    private Predicate<WatchProgressService.Progress> progressFilter;
    private String emptyMessage = "Aquí no hay nada todavía";

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

    /**
     * Configura qué muestra esta vista. Debe llamarse justo después de
     * cargar el FXML, antes de que initialize() pinte nada (o se puede
     * volver a llamar para reconfigurar y refrescar sobre el mismo nodo).
     *
     * @param type           tipo de contenido a mostrar (ANIME o SERIES)
     * @param progressFilter qué favoritos de ese tipo entran en la lista,
     *                        según su Progress (viewed, watchedEpisodes...)
     * @param displayTitle   título mostrado en la cabecera (con emoji)
     * @param emptyMessage   mensaje cuando no hay nada que mostrar
     */
    public void setFilter(MediaType type, Predicate<WatchProgressService.Progress> progressFilter,
                          String displayTitle, String emptyMessage) {
        this.typeFilter = type;
        this.progressFilter = progressFilter;
        this.emptyMessage = emptyMessage;

        if (titleLabel != null) {
            titleLabel.setText(displayTitle);
        }
        if (statusLabel != null) {
            statusLabel.setText(emptyMessage);
        }

        refresh();
    }

    @FXML
    public void initialize() {
        speedUpScroll(scrollPane);
    }

    @FXML
    private void onBack() {
        if (onBackAction != null) onBackAction.run();
    }

    /**
     * Vuelve a calcular y pintar la lista desde cero. Público para poder
     * refrescar al volver de un detalle donde se haya cambiado el
     * progreso de algún título (por ejemplo, terminar una serie la
     * sacaría de "viendo" y la metería en "vistas" la próxima vez que
     * se abra esta pantalla).
     */
    public void refresh() {
        if (typeFilter == null || progressFilter == null) return;

        List<FavoriteItem> matching = new ArrayList<>();
        for (FavoriteItem fav : FavoritesService.getFavoritesByType(typeFilter)) {
            WatchProgressService.Progress progress = WatchProgressService.getProgress(fav.getTitle());
            if (progressFilter.test(progress)) {
                matching.add(fav);
            }
        }

        renderItems(matching);
    }

    private void renderItems(List<FavoriteItem> items) {
        resultsGrid.getChildren().clear();

        if (items.isEmpty()) {
            statusPane.setVisible(true);
            statusPane.setManaged(true);
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            statusLabel.setText(emptyMessage);
            statusLabel.setVisible(true);
            return;
        }

        statusPane.setVisible(false);
        statusPane.setManaged(false);
        scrollPane.setVisible(true);
        scrollPane.setManaged(true);

        for (FavoriteItem fav : items) {
            resultsGrid.getChildren().add(buildCard(fav));
        }
    }

    /**
     * Tarjeta visual: imagen, badge de tipo, título, año, score, y
     * progreso de episodios o valoración personal. Mismo estilo que las
     * tarjetas de "Mis favoritos" y de la búsqueda.
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

        // Badge de tipo (esquina superior izquierda)
        Label typeBadge = new Label(typeLabel(fav.getType()));
        typeBadge.setStyle(
                "-fx-background-color: #e94560;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 9px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 2 8 2 8;" +
                        "-fx-background-radius: 10;"
        );
        StackPane.setAlignment(typeBadge, Pos.TOP_LEFT);
        StackPane.setMargin(typeBadge, new Insets(6));

        imageContainer.getChildren().addAll(cover, typeBadge);

        // Título
        Label cardTitle = new Label(fav.getTitle());
        cardTitle.setWrapText(true);
        cardTitle.setMaxWidth(150);
        cardTitle.setStyle(
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

        card.getChildren().addAll(imageContainer, cardTitle, metaRow);

        // Fila de estado: progreso de episodios + valoración personal
        WatchProgressService.Progress progress = WatchProgressService.getProgress(fav.getTitle());

        HBox statusRow = new HBox();
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setStyle("-fx-padding: 0 8 8 8;");

        Integer total = fav.getTotalEpisodes();
        Label progressLabel;
        if (total != null && total > 0) {
            progressLabel = new Label("📺 " + progress.watchedEpisodes.size() + "/" + total);
            progressLabel.setStyle("-fx-text-fill: #555577; -fx-font-size: 10px;");
        } else if (progress.viewed) {
            progressLabel = new Label("✔ Visto");
            progressLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 10px;");
        } else {
            progressLabel = new Label(" ");
        }

        HBox spacerBox = new HBox();
        HBox.setHgrow(spacerBox, Priority.ALWAYS);

        Label userStars = new Label();
        Integer userRating = progress.userRating;
        if (userRating != null && userRating > 0) {
            StringBuilder starsText = new StringBuilder();
            for (int i = 0; i < userRating; i++) starsText.append("★");
            userStars.setText(starsText.toString());
            userStars.setStyle("-fx-text-fill: #f1c40f; -fx-font-size: 10px;");
        }

        statusRow.getChildren().addAll(progressLabel, spacerBox, userStars);
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
