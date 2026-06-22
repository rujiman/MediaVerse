package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.services.DashboardService;
import com.rujiman.mediatracker.services.FavoritesService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller de la pantalla de inicio "Mi MediaVerse": muestra, por
 * sección fija (Juegos / Música / Series / Películas / Anime), los
 * favoritos que el usuario eligió manualmente mediante el selector (✏️).
 */
public class HomeController {

    @FXML private ScrollPane homeScroll;
    @FXML private VBox homeRoot;

    @FXML private VBox gameColumnBox;
    @FXML private FlowPane gameSectionBox;
    @FXML private Label gameEmptyLabel;
    @FXML private Button editGameButton;

    @FXML private VBox seriesColumnBox;
    @FXML private FlowPane seriesSectionBox;
    @FXML private Label seriesEmptyLabel;
    @FXML private Button editSeriesButton;

    @FXML private VBox animeColumnBox;
    @FXML private FlowPane animeSectionBox;
    @FXML private Label animeEmptyLabel;
    @FXML private Button editAnimeButton;

    @FXML private VBox musicSectionContainer;
    @FXML private HBox musicSectionBox;
    @FXML private Label musicEmptyLabel;
    @FXML private Button editMusicButton;

    @FXML private VBox movieSectionContainer;
    @FXML private HBox movieSectionBox;
    @FXML private Label movieEmptyLabel;
    @FXML private Button editMovieButton;

    /** Se ejecuta al pinchar una tarjeta; recibe el MediaItem equivalente. */
    private java.util.function.Consumer<MediaItem> onOpenDetailAction;

    public void setOnOpenDetailAction(java.util.function.Consumer<MediaItem> action) {
        this.onOpenDetailAction = action;
    }

    @FXML
    public void initialize() {
        // El viewport interno del ScrollPane no hereda el fondo oscuro por
        // CSS normal, dejando un hueco blanco/gris cuando el contenido es
        // más corto que el área visible. Forzamos el estilo en cuanto el
        // Skin del ScrollPane existe (mismo fix que en DetailViewController).
        homeScroll.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) applyViewportBackground();
        });
        if (homeScroll.getSkin() != null) {
            applyViewportBackground();
        }

        DashboardService.pruneDeletedFavorites();
        refreshAll();
    }

    private void applyViewportBackground() {
        javafx.scene.Node viewport = homeScroll.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: #0f0f1a;");
        }
    }

    /**
     * Vuelve a pintar todas las secciones desde cero. Público para poder
     * refrescar la home al volver de un detalle donde se haya quitado
     * un favorito, o tras editar una sección.
     */
    public void refreshAll() {
        renderVerticalSection(MediaType.GAME, gameSectionBox, gameEmptyLabel);
        renderVerticalSection(MediaType.SERIES, seriesSectionBox, seriesEmptyLabel);
        renderVerticalSection(MediaType.ANIME, animeSectionBox, animeEmptyLabel);
        renderHorizontalSection(MediaType.MUSIC, musicSectionBox, musicEmptyLabel);
        renderHorizontalSection(MediaType.MOVIE, movieSectionBox, movieEmptyLabel);
    }

    // ===========================================================
    // RENDERIZADO DE SECCIONES
    // ===========================================================

    /**
     * Para las 3 columnas grandes de arriba (Juegos / Series / Anime): grid
     * 4x4 (máximo 16). El FlowPane que las contiene tiene una altura FIJA
     * en el FXML (prefHeight/minHeight=610, calculada para 4 filas exactas
     * de este tamaño de tarjeta), así que el hueco para las 4 filas queda
     * siempre reservado, esté lleno o no, y las 3 columnas resultan
     * idénticas en altura sin necesidad de medir nada en código.
     */
    private void renderVerticalSection(MediaType type, FlowPane container, Label emptyLabel) {
        container.getChildren().clear();
        List<FavoriteItem> items = DashboardService.getSectionItems(type);

        boolean empty = items.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);

        for (FavoriteItem fav : items) {
            container.getChildren().add(buildCard(fav, 90, 115));
        }
    }

    /**
     * Para las 2 filas de abajo (Música / Películas), cada una con ancho
     * fijo (628px, ver FXML): UNA sola fila de máximo 5 tarjetas,
     * centradas en su contenedor. El tamaño de tarjeta está calculado
     * para que 5 + sus gaps quepan exactamente en ese ancho fijo.
     */
    private void renderHorizontalSection(MediaType type, HBox container, Label emptyLabel) {
        container.getChildren().clear();
        List<FavoriteItem> items = DashboardService.getSectionItems(type);

        boolean empty = items.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);

        for (FavoriteItem fav : items) {
            container.getChildren().add(buildCard(fav, 105, 150));
        }
    }

    /**
     * Tarjeta compacta para el dashboard: imagen + título, sin badges
     * extra (el contexto de la sección ya indica el tipo).
     *
     * La imagen se pide al doble de resolución del tamaño visual y luego
     * se escala hacia abajo con suavizado: pedirla exactamente al tamaño
     * final hace que JavaFX la reescale desde una versión ya pequeña de
     * la fuente remota, lo que se ve borroso/pixelado.
     */
    private VBox buildCard(FavoriteItem fav, double width, double imageHeight) {
        VBox card = new VBox(4);
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        card.setStyle("-fx-cursor: hand;");
        card.setAlignment(Pos.TOP_LEFT);

        ImageView cover = new ImageView();
        cover.setFitWidth(width);
        cover.setFitHeight(imageHeight);
        cover.setPreserveRatio(false);
        cover.setSmooth(true);

        Rectangle clip = new Rectangle(width, imageHeight);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        cover.setClip(clip);

        boolean hasImage = fav.getImageUrl() != null && !fav.getImageUrl().isBlank();
        if (hasImage) {
            try {
                // Pedimos la imagen al doble de resolución visual para que
                // no se vea pixelada al mostrarla en pantallas de alta densidad
                double requestW = width * 2;
                double requestH = imageHeight * 2;
                cover.setImage(new Image(fav.getImageUrl(), requestW, requestH, false, true, true));
                card.getChildren().add(cover);
            } catch (Exception ignored) {
                hasImage = false;
            }
        }
        if (!hasImage) {
            Pane placeholder = new Pane();
            placeholder.setPrefSize(width, imageHeight);
            placeholder.setStyle("-fx-background-color: #0f0f1a; -fx-background-radius: 10;");
            card.getChildren().add(placeholder);
        }

        Label titleLabel = new Label(fav.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(width);
        titleLabel.setStyle("-fx-text-fill: #eaeaea; -fx-font-size: 11px; -fx-font-weight: bold;");
        card.getChildren().add(titleLabel);

        card.setOnMouseClicked(e -> {
            if (onOpenDetailAction != null) {
                onOpenDetailAction.accept(fav.toMediaItem());
            }
        });

        return card;
    }

    // ===========================================================
    // SELECTORES (✏️) POR SECCIÓN
    // ===========================================================

    @FXML private void onEditGameSection() { openSectionEditor(MediaType.GAME, "Juegos"); }
    @FXML private void onEditMusicSection() { openSectionEditor(MediaType.MUSIC, "Música"); }
    @FXML private void onEditSeriesSection() { openSectionEditor(MediaType.SERIES, "Series"); }
    @FXML private void onEditMovieSection() { openSectionEditor(MediaType.MOVIE, "Películas"); }
    @FXML private void onEditAnimeSection() { openSectionEditor(MediaType.ANIME, "Anime"); }

    /**
     * Abre un selector visual con tarjetas (imagen + título) de todos los
     * favoritos de ese tipo. Pinchar una tarjeta la marca/desmarca (borde
     * rosa cuando está seleccionada). Al llegar al límite de la sección,
     * las tarjetas no seleccionadas quedan atenuadas y no se pueden marcar
     * hasta que se quite alguna.
     */
    private void openSectionEditor(MediaType type, String sectionDisplayName) {
        List<FavoriteItem> candidates = FavoritesService.getFavoritesByType(type);

        if (candidates.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            styleDialogDark(alert.getDialogPane());
            alert.setHeaderText(null);
            alert.setContentText("Todavía no tienes favoritos de " + sectionDisplayName +
                    ".\nBusca contenido y añádelo a favoritos (⭐) para poder elegirlo aquí.");
            alert.show();
            return;
        }

        List<String> currentIds = DashboardService.getSectionIds(type);
        int max = DashboardService.getMaxItemsForSection(type);

        // Selección en curso, mutable mientras el diálogo está abierto
        List<String> selectedIds = new ArrayList<>(currentIds);

        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Elegir " + sectionDisplayName + " para Mi MediaVerse");
        styleDialogDark(dialog.getDialogPane());

        Label limitLabel = new Label();
        limitLabel.setStyle("-fx-text-fill: #555577; -fx-font-size: 12px; -fx-padding: 0 0 10 0;");

        FlowPane grid = new FlowPane(10, 10);

        Runnable updateLimitState = () -> limitLabel.setText("Seleccionados: " + selectedIds.size() + " / " + max);

        List<javafx.scene.layout.StackPane> cards = new ArrayList<>();

        for (FavoriteItem fav : candidates) {
            javafx.scene.layout.StackPane card = buildSelectableCard(fav, selectedIds, max, updateLimitState);
            cards.add(card);
            grid.getChildren().add(card);
        }

        updateLimitState.run();

        VBox content = new VBox(0, limitLabel, grid);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(560, 420);
        scroll.setStyle("-fx-background-color: #1a1a2e; -fx-background: #1a1a2e; -fx-border-color: transparent;");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Los botones por defecto del diálogo se pintan claros; forzamos su estilo
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (okBtn != null) okBtn.setStyle("-fx-background-color: #e94560; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6;");
        if (cancelBtn != null) cancelBtn.setStyle("-fx-background-color: #16213e; -fx-text-fill: #eaeaea; -fx-background-radius: 6;");

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? selectedIds : null);

        dialog.showAndWait().ifPresent(finalIds -> {
            DashboardService.setSectionItems(type, finalIds);
            refreshAll();
        });
    }

    /**
     * Tarjeta seleccionable para el selector de secciones: imagen + título,
     * con un botón circular en la esquina (rojo "+" para añadir, verde "✓"
     * cuando ya está seleccionada) en vez de un simple checkbox. Pinchar
     * la imagen O el botón marca/desmarca igual.
     */
    private javafx.scene.layout.StackPane buildSelectableCard(
            FavoriteItem fav, List<String> selectedIds, int max, Runnable onChange) {

        double width = 100, height = 140;

        javafx.scene.layout.StackPane card = new javafx.scene.layout.StackPane();
        card.setPrefSize(width, height + 28);
        card.setStyle("-fx-cursor: hand; -fx-background-color: transparent;");

        VBox content = new VBox(4);
        content.setAlignment(Pos.TOP_CENTER);

        ImageView cover = new ImageView();
        cover.setFitWidth(width);
        cover.setFitHeight(height);
        cover.setPreserveRatio(false);
        cover.setSmooth(true);

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        cover.setClip(clip);

        if (fav.getImageUrl() != null && !fav.getImageUrl().isBlank()) {
            try {
                cover.setImage(new Image(fav.getImageUrl(), width * 2, height * 2, false, true, true));
            } catch (Exception ignored) {}
        }

        Label titleLabel = new Label(fav.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(width);
        titleLabel.setStyle("-fx-text-fill: #eaeaea; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-alignment: center;");
        titleLabel.setAlignment(Pos.TOP_CENTER);

        javafx.scene.layout.StackPane imageContainer = new javafx.scene.layout.StackPane(cover);
        imageContainer.setPrefSize(width, height);
        imageContainer.setMaxSize(width, height);

        // Botón circular de añadir/quitar, esquina superior derecha de la imagen
        Label addButton = new Label();
        addButton.setMinSize(26, 26);
        addButton.setMaxSize(26, 26);
        addButton.setAlignment(Pos.CENTER);
        addButton.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 13;" +
                        "-fx-effect: dropshadow(gaussian, #000000aa, 4, 0, 0, 1);"
        );
        javafx.scene.layout.StackPane.setAlignment(addButton, Pos.TOP_RIGHT);
        javafx.scene.layout.StackPane.setMargin(addButton, new javafx.geometry.Insets(6));

        imageContainer.getChildren().add(addButton);

        content.getChildren().addAll(imageContainer, titleLabel);
        card.getChildren().add(content);

        Runnable updateBorder = () -> {
            boolean selected = selectedIds.contains(fav.getId());

            if (selected) {
                imageContainer.setStyle("-fx-border-color: #e94560; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
                addButton.setText("✓");
                addButton.setStyle(
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;" +
                                "-fx-background-color: #2ecc71;" +
                                "-fx-background-radius: 13;" +
                                "-fx-effect: dropshadow(gaussian, #000000aa, 4, 0, 0, 1);"
                );
            } else {
                imageContainer.setStyle("-fx-border-color: transparent; -fx-border-width: 3;");
                addButton.setText("+");
                addButton.setStyle(
                        "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;" +
                                "-fx-background-color: #e94560;" +
                                "-fx-background-radius: 13;" +
                                "-fx-effect: dropshadow(gaussian, #000000aa, 4, 0, 0, 1);"
                );
            }
            boolean limitReached = !selected && selectedIds.size() >= max;
            card.setOpacity(limitReached ? 0.35 : 1.0);
            card.setDisable(limitReached);
        };

        updateBorder.run();

        card.setOnMouseClicked(e -> {
            boolean selected = selectedIds.contains(fav.getId());
            if (selected) {
                selectedIds.remove(fav.getId());
            } else {
                if (selectedIds.size() >= max) return; // protección extra
                selectedIds.add(fav.getId());
            }
            updateBorder.run();
            onChange.run();
        });

        return card;
    }

    /**
     * Fuerza la paleta oscura de la app en un DialogPane de JavaFX, que
     * por defecto usa el tema claro del sistema (causaba texto blanco
     * invisible sobre fondo claro en los selectores).
     */
    private void styleDialogDark(DialogPane pane) {
        pane.setStyle(
                "-fx-background-color: #1a1a2e;" +
                        "-fx-text-fill: #eaeaea;"
        );
        pane.applyCss();
        // El texto de contenido por defecto de un Alert vive anidado dentro
        // de un GridPane interno, no como hijo directo del DialogPane, así
        // que recorremos el árbol completo para no dejar texto invisible.
        forceLabelColorRecursive(pane, "#eaeaea");
    }

    private void forceLabelColorRecursive(javafx.scene.Parent parent, String hexColor) {
        for (javafx.scene.Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof Label label) {
                label.setStyle("-fx-text-fill: " + hexColor + ";");
            }
            if (node instanceof javafx.scene.Parent childParent) {
                forceLabelColorRecursive(childParent, hexColor);
            }
        }
    }
}