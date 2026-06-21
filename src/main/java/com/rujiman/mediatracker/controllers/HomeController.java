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
 * Controller de la pantalla de inicio "Tu MediaVerse": muestra, por
 * sección fija (Juegos / Música / Series / Películas / Anime), los
 * favoritos que el usuario eligió manualmente mediante el selector (✏️).
 */
public class HomeController {

    @FXML private ScrollPane homeScroll;
    @FXML private VBox homeRoot;

    @FXML private FlowPane gameSectionBox;
    @FXML private Label gameEmptyLabel;
    @FXML private Button editGameButton;

    @FXML private HBox musicSectionBox;
    @FXML private Label musicEmptyLabel;
    @FXML private Button editMusicButton;

    @FXML private HBox seriesSectionBox;
    @FXML private Label seriesEmptyLabel;
    @FXML private Button editSeriesButton;

    @FXML private HBox movieSectionBox;
    @FXML private Label movieEmptyLabel;
    @FXML private Button editMovieButton;

    @FXML private HBox animeSectionBox;
    @FXML private Label animeEmptyLabel;
    @FXML private Button editAnimeButton;

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
        renderHorizontalSection(MediaType.MUSIC, musicSectionBox, musicEmptyLabel);
        renderHorizontalSection(MediaType.SERIES, seriesSectionBox, seriesEmptyLabel);
        renderHorizontalSection(MediaType.MOVIE, movieSectionBox, movieEmptyLabel);
        renderHorizontalSection(MediaType.ANIME, animeSectionBox, animeEmptyLabel);
    }

    // ===========================================================
    // RENDERIZADO DE SECCIONES
    // ===========================================================

    /** Para la columna de Juegos: grid 5x3 (máximo 15). */
    private void renderVerticalSection(MediaType type, FlowPane container, Label emptyLabel) {
        container.getChildren().clear();
        List<FavoriteItem> items = DashboardService.getSectionItems(type);

        boolean empty = items.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);

        for (FavoriteItem fav : items) {
            container.getChildren().add(buildCard(fav, 110, 150));
        }
    }

    /** Para las filas de Música/Series/Películas/Anime: tarjetas en horizontal (máximo 5). */
    private void renderHorizontalSection(MediaType type, HBox container, Label emptyLabel) {
        container.getChildren().clear();
        List<FavoriteItem> items = DashboardService.getSectionItems(type);

        boolean empty = items.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);

        for (FavoriteItem fav : items) {
            container.getChildren().add(buildCard(fav, 150, 210));
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
     * Abre un diálogo con checkboxes de todos los favoritos de ese tipo,
     * preseleccionando los que ya están en la sección, y guarda la nueva
     * selección al confirmar. Si se alcanza el límite de la sección, los
     * checkboxes no marcados se deshabilitan hasta que se desmarque alguno.
     */
    private void openSectionEditor(MediaType type, String sectionDisplayName) {
        List<FavoriteItem> candidates = FavoritesService.getFavoritesByType(type);

        if (candidates.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText(null);
            alert.setContentText("Todavía no tienes favoritos de " + sectionDisplayName +
                    ".\nBusca contenido y añádelo a favoritos (⭐) para poder elegirlo aquí.");
            alert.show();
            return;
        }

        List<String> currentIds = DashboardService.getSectionIds(type);
        int max = DashboardService.getMaxItemsForSection(type);

        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Elegir " + sectionDisplayName + " para Tu MediaVerse");
        dialog.getDialogPane().setStyle("-fx-background-color: #1a1a2e;");

        Label limitLabel = new Label();
        limitLabel.setStyle("-fx-text-fill: #555577; -fx-font-size: 11px; -fx-padding: 0 0 8 0;");

        VBox content = new VBox(8);
        content.setStyle("-fx-padding: 10;");
        content.getChildren().add(limitLabel);

        List<CheckBox> checkBoxes = new ArrayList<>();
        for (FavoriteItem fav : candidates) {
            CheckBox cb = new CheckBox(fav.getTitle());
            cb.setUserData(fav.getId());
            cb.setSelected(currentIds.contains(fav.getId()));
            cb.setStyle("-fx-text-fill: #eaeaea;");
            checkBoxes.add(cb);
            content.getChildren().add(cb);
        }

        // Actualiza el contador y bloquea/desbloquea los checkboxes no
        // marcados según si se ha alcanzado el límite máximo.
        Runnable updateLimitState = () -> {
            long selectedCount = checkBoxes.stream().filter(CheckBox::isSelected).count();
            limitLabel.setText("Seleccionados: " + selectedCount + " / " + max);

            boolean limitReached = selectedCount >= max;
            for (CheckBox cb : checkBoxes) {
                if (!cb.isSelected()) {
                    cb.setDisable(limitReached);
                }
            }
        };

        for (CheckBox cb : checkBoxes) {
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> updateLimitState.run());
        }
        updateLimitState.run();

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(350, candidates.size() * 34 + 40));
        scroll.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: transparent;");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                List<String> selectedIds = new ArrayList<>();
                for (CheckBox cb : checkBoxes) {
                    if (cb.isSelected()) selectedIds.add((String) cb.getUserData());
                }
                return selectedIds;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(selectedIds -> {
            DashboardService.setSectionItems(type, selectedIds);
            refreshAll();
        });
    }
}