package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanFolder;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.FolderService;
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
 * de tarjetas que la búsqueda, filtro por tipo de contenido, y carpetas
 * propias (namespace "favorites" en FolderService) — completamente
 * independientes de las carpetas de "Pienso ver/jugar/escuchar" o de
 * cualquiera de las 6 listas de seguimiento.
 */
public class FavoritesViewController {

    private static final String FOLDER_NAMESPACE = "favorites";

    @FXML private Button backButton;
    @FXML private Button backToRootButton;
    @FXML private Button newFolderButton;
    @FXML private HBox filterBarBox;

    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterAnime;
    @FXML private ToggleButton filterSeries;
    @FXML private ToggleButton filterMovie;
    @FXML private ToggleButton filterMusic;
    @FXML private ToggleButton filterGame;

    @FXML private StackPane statusPane;
    @FXML private Label statusLabel;
    @FXML private ScrollPane scrollPane;

    @FXML private VBox foldersSection;
    @FXML private FlowPane foldersGrid;

    @FXML private VBox itemsSection;
    @FXML private Label itemsSectionLabel;
    @FXML private FlowPane resultsGrid;

    private final ToggleGroup filterGroup = new ToggleGroup();
    private final List<FavoriteItem> allFavorites = new ArrayList<>();

    private String currentFolderId; // null = vista raíz

    private Runnable onBackAction;
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
            applyFilter();
        });

        updateFilterButtonStyles();
        speedUpScroll(scrollPane);
        loadFavorites();
    }

    private void updateFilterButtonStyles() {
        updateSingleFilterStyle(filterAll, "#ec4d80");
        updateSingleFilterStyle(filterAnime, "#ec4dc0");
        updateSingleFilterStyle(filterSeries, "#8b5cf6");
        updateSingleFilterStyle(filterMovie, "#ecb14d");
        updateSingleFilterStyle(filterMusic, "#4dec9e");
        updateSingleFilterStyle(filterGame, "#4dd9ec");
    }

    private void updateSingleFilterStyle(ToggleButton btn, String accentHexWhenSelected) {
        boolean selected = btn.isSelected();
        String bgColor = selected ? accentHexWhenSelected : "#1c1730";
        String textColor = selected ? "white" : "#f0eef5";
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

    /**
     * "← Volver": si estamos dentro de una carpeta, primero vuelve a la
     * raíz; solo si ya estamos en la raíz, sale de toda la pantalla.
     */
    @FXML
    private void onBack() {
        if (currentFolderId != null) {
            onBackToRoot();
        } else if (onBackAction != null) {
            onBackAction.run();
        }
    }

    @FXML
    private void onBackToRoot() {
        currentFolderId = null;
        applyFilter();
    }

    @FXML
    private void onCreateFolder() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nueva carpeta");
        dialog.setHeaderText(null);
        dialog.setContentText("Nombre de la carpeta:");
        styleDialogDark(dialog.getDialogPane());
        darkenDialogWindow(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                FolderService.createFolder(FOLDER_NAMESPACE, trimmed);
                applyFilter();
            }
        });
    }

    /**
     * Carga (o recarga) los favoritos del usuario actual y aplica el
     * filtro/carpeta activos. Público para refrescar al volver desde un
     * detalle donde se haya quitado algún favorito.
     */
    public void loadFavorites() {
        allFavorites.clear();
        allFavorites.addAll(FavoritesService.getFavorites());

        List<String> validIds = new ArrayList<>();
        for (FavoriteItem fav : allFavorites) validIds.add(fav.getId());
        FolderService.pruneDeletedFavorites(FOLDER_NAMESPACE, validIds);

        applyFilter();
    }

    @FXML
    private void onFilterChanged() {
        applyFilter();
    }

    /**
     * Recalcula y pinta tanto las carpetas (solo en la raíz) como los
     * favoritos visibles, combinando el filtro de tipo con la carpeta
     * actual.
     */
    private void applyFilter() {
        boolean atRoot = currentFolderId == null;

        backToRootButton.setVisible(!atRoot);
        backToRootButton.setManaged(!atRoot);
        newFolderButton.setVisible(atRoot);
        newFolderButton.setManaged(atRoot);
        filterBarBox.setVisible(atRoot);
        filterBarBox.setManaged(atRoot);

        Toggle selected = filterGroup.getSelectedToggle();
        MediaType typeFilter = null;
        if (selected == filterAnime) typeFilter = MediaType.ANIME;
        else if (selected == filterSeries) typeFilter = MediaType.SERIES;
        else if (selected == filterMovie) typeFilter = MediaType.MOVIE;
        else if (selected == filterMusic) typeFilter = MediaType.MUSIC;
        else if (selected == filterGame) typeFilter = MediaType.GAME;

        foldersSection.setVisible(atRoot);
        foldersSection.setManaged(atRoot);
        foldersGrid.getChildren().clear();

        List<PlanFolder> folders = atRoot ? FolderService.getFolders(FOLDER_NAMESPACE) : List.of();
        if (atRoot) {
            for (PlanFolder folder : folders) {
                foldersGrid.getChildren().add(buildFolderCard(folder));
            }
        }

        List<FavoriteItem> filtered = new ArrayList<>();
        for (FavoriteItem fav : allFavorites) {
            if (typeFilter != null && fav.getType() != typeFilter) continue;

            String favFolderId = FolderService.getFolderIdFor(FOLDER_NAMESPACE, fav.getId());
            if (atRoot) {
                if (favFolderId == null) filtered.add(fav);
            } else {
                if (currentFolderId.equals(favFolderId)) filtered.add(fav);
            }
        }

        if (atRoot) {
            itemsSectionLabel.setText("Sin carpeta");
        } else {
            PlanFolder folder = findFolder(currentFolderId);
            itemsSectionLabel.setText(folder != null ? folder.getName() : "Carpeta");
        }

        renderFavorites(filtered, folders.isEmpty());
    }

    private PlanFolder findFolder(String folderId) {
        for (PlanFolder folder : FolderService.getFolders(FOLDER_NAMESPACE)) {
            if (folder.getId().equals(folderId)) return folder;
        }
        return null;
    }

    private void renderFavorites(List<FavoriteItem> favorites, boolean noFolders) {
        resultsGrid.getChildren().clear();

        boolean nothingToShow = favorites.isEmpty() && noFolders;

        if (nothingToShow) {
            statusPane.setVisible(true);
            statusPane.setManaged(true);
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            statusLabel.setVisible(true);
            itemsSection.setVisible(false);
            itemsSection.setManaged(false);
            return;
        }

        statusPane.setVisible(false);
        statusPane.setManaged(false);
        scrollPane.setVisible(true);
        scrollPane.setManaged(true);

        itemsSection.setVisible(!favorites.isEmpty());
        itemsSection.setManaged(!favorites.isEmpty());

        for (FavoriteItem fav : favorites) {
            resultsGrid.getChildren().add(buildCard(fav));
        }
    }

    /**
     * Tarjeta de carpeta: preview 2x2 con las portadas de hasta 4
     * favoritos que contiene, nombre, contador, y botones Renombrar/
     * Eliminar (mismo patrón que PlanListViewController.buildFolderCard()).
     */
    private VBox buildFolderCard(PlanFolder folder) {
        List<String> favIdsInFolder = FolderService.getFavoriteIdsInFolder(FOLDER_NAMESPACE, folder.getId());
        List<FavoriteItem> contents = new ArrayList<>();
        for (FavoriteItem fav : allFavorites) {
            if (favIdsInFolder.contains(fav.getId())) contents.add(fav);
        }
        int count = contents.size();

        VBox card = new VBox(8);
        card.setPrefWidth(220);
        card.getStyleClass().add("card-base");
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-padding: 18 14 16 14; -fx-cursor: hand;");

        javafx.scene.layout.GridPane previewGrid = new javafx.scene.layout.GridPane();
        previewGrid.setHgap(4);
        previewGrid.setVgap(4);
        previewGrid.setPrefSize(190, 190);
        previewGrid.setMaxSize(190, 190);

        double cellSize = 93;
        for (int i = 0; i < 4; i++) {
            int row = i / 2;
            int col = i % 2;

            StackPane cell = new StackPane();
            cell.setPrefSize(cellSize, cellSize);
            cell.setMaxSize(cellSize, cellSize);
            cell.setStyle("-fx-background-color: #100c1c; -fx-background-radius: 6;");

            if (i < contents.size() && contents.get(i).getImageUrl() != null
                    && !contents.get(i).getImageUrl().isBlank()) {
                ImageView thumb = new ImageView();
                thumb.setFitWidth(cellSize);
                thumb.setFitHeight(cellSize);
                thumb.setPreserveRatio(false);
                thumb.setSmooth(true);

                Rectangle clip = new Rectangle(cellSize, cellSize);
                clip.setArcWidth(6);
                clip.setArcHeight(6);
                thumb.setClip(clip);

                try {
                    thumb.setImage(new Image(contents.get(i).getImageUrl(), cellSize * 2, cellSize * 2, false, true, true));
                } catch (Exception ignored) {}

                cell.getChildren().add(thumb);
            } else {
                Label placeholder = new Label("📁");
                placeholder.setStyle("-fx-font-size: 32px; -fx-opacity: 0.25;");
                cell.getChildren().add(placeholder);
            }

            previewGrid.add(cell, col, row);
        }

        Label nameLabel = new Label(folder.getName());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(195);
        nameLabel.setStyle("-fx-text-fill: #f0eef5; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-alignment: center;");
        nameLabel.setAlignment(Pos.CENTER);

        Label countLabel = new Label(count + (count == 1 ? " elemento" : " elementos"));
        countLabel.getStyleClass().add("text-dim");

        HBox actionsRow = new HBox(8);
        actionsRow.setAlignment(Pos.CENTER);

        Button renameBtn = new Button("Renombrar");
        renameBtn.getStyleClass().add("chip-action-neutral");
        renameBtn.setOnAction(e -> {
            e.consume();
            onRenameFolder(folder);
        });

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().add("chip-action-danger");
        deleteBtn.setOnAction(e -> {
            e.consume();
            onDeleteFolder(folder);
        });

        actionsRow.getChildren().addAll(renameBtn, deleteBtn);

        card.getChildren().addAll(previewGrid, nameLabel, countLabel, actionsRow);

        card.setOnMouseClicked(e -> {
            currentFolderId = folder.getId();
            applyFilter();
        });

        return card;
    }

    private void onRenameFolder(PlanFolder folder) {
        TextInputDialog dialog = new TextInputDialog(folder.getName());
        dialog.setTitle("Renombrar carpeta");
        dialog.setHeaderText(null);
        dialog.setContentText("Nuevo nombre:");
        styleDialogDark(dialog.getDialogPane());
        darkenDialogWindow(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                FolderService.renameFolder(FOLDER_NAMESPACE, folder.getId(), trimmed);
                applyFilter();
            }
        });
    }

    private void onDeleteFolder(PlanFolder folder) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar carpeta");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Eliminar \"" + folder.getName() + "\"? Los favoritos dentro no se borrarán, volverán a quedar sin carpeta.");
        styleDialogDark(confirm.getDialogPane());
        darkenDialogWindow(confirm.getDialogPane());

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                FolderService.deleteFolder(FOLDER_NAMESPACE, folder.getId());
                applyFilter();
            }
        });
    }

    /**
     * Tarjeta visual para un favorito: imagen, badge de tipo, título, año,
     * rating y progreso de episodios si aplica, más un botón "📁" para
     * mover el favorito a una carpeta.
     */
    private VBox buildCard(FavoriteItem fav) {
        VBox card = new VBox(6);
        card.setPrefWidth(160);
        card.getStyleClass().add("card-base");
        card.setAlignment(Pos.TOP_LEFT);

        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(160, 220);
        imageContainer.setMaxSize(160, 220);
        imageContainer.setStyle("-fx-background-color: #100c1c; -fx-background-radius: 10 10 0 0;");

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

        Button removeButton = new Button("★");
        removeButton.setTextFill(javafx.scene.paint.Color.web("#ec4d80"));
        removeButton.setStyle(
                "-fx-background-color: rgba(16,12,28,0.85);" +
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
            loadFavorites();
        });

        Label moveButton = new Label("📁");
        moveButton.setStyle(
                "-fx-background-color: rgba(16,12,28,0.9);" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 4 6 4 6;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        StackPane.setAlignment(moveButton, Pos.BOTTOM_LEFT);
        StackPane.setMargin(moveButton, new Insets(6));
        moveButton.setOnMouseClicked(e -> {
            e.consume();
            onMoveToFolder(fav);
        });

        Label typeBadge = new Label(typeLabel(fav.getType()));
        typeBadge.getStyleClass().add(badgeClassFor(fav.getType()));
        StackPane.setAlignment(typeBadge, Pos.TOP_LEFT);
        StackPane.setMargin(typeBadge, new Insets(6));

        imageContainer.getChildren().addAll(cover, removeButton, moveButton, typeBadge);

        Label titleLabel = new Label(fav.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(150);
        titleLabel.getStyleClass().add("text-heading");
        titleLabel.setStyle("-fx-font-size: 12px; -fx-padding: 6 8 0 8;");

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setStyle("-fx-padding: 0 8 4 8;");

        String yearText = fav.getYear() != null ? String.valueOf(fav.getYear()) : "—";
        Label yearLabel = new Label(yearText);
        yearLabel.getStyleClass().add("text-dim");
        metaRow.getChildren().add(yearLabel);

        if (fav.getScore() != null && fav.getScore() > 0) {
            Label scoreLabel = new Label("⭐ " + fav.getScore());
            scoreLabel.getStyleClass().add("text-success");
            scoreLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
            metaRow.getChildren().add(scoreLabel);
        }

        card.getChildren().addAll(imageContainer, titleLabel, metaRow);

        com.rujiman.mediatracker.services.WatchProgressService.Progress progress =
                com.rujiman.mediatracker.services.WatchProgressService.getProgress(fav.getTitle());

        HBox statusRow = new HBox();
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setStyle("-fx-padding: 0 8 8 8;");

        Integer total = fav.getTotalEpisodes();
        Label statusLbl;
        if (total != null && total > 0) {
            statusLbl = new Label("📺 " + progress.watchedEpisodes.size() + "/" + total + " vistos");
            statusLbl.getStyleClass().add("text-dim");
        } else if (progress.viewed) {
            statusLbl = new Label("✔ Visto");
            statusLbl.getStyleClass().add("text-success");
        } else {
            statusLbl = new Label(" ");
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

        statusRow.getChildren().addAll(statusLbl, spacerBox, userStars);
        card.getChildren().add(statusRow);

        card.setOnMouseClicked(e -> {
            if (onOpenDetailAction != null) {
                onOpenDetailAction.accept(fav.toMediaItem());
            }
        });

        return card;
    }

    /**
     * Selector simple: lista de carpetas existentes + "Sin carpeta",
     * para mover un favorito de sitio.
     */
    private void onMoveToFolder(FavoriteItem fav) {
        List<PlanFolder> folders = FolderService.getFolders(FOLDER_NAMESPACE);

        List<String> options = new ArrayList<>();
        options.add("(Sin carpeta)");
        for (PlanFolder f : folders) options.add(f.getName());

        ChoiceDialog<String> dialog = new ChoiceDialog<>("(Sin carpeta)", options);
        dialog.setTitle("Mover \"" + fav.getTitle() + "\"");
        dialog.setHeaderText(null);
        dialog.setContentText("Elige carpeta de destino:");
        styleDialogDark(dialog.getDialogPane());
        darkenDialogWindow(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(selection -> {
            String targetFolderId = null;
            if (!"(Sin carpeta)".equals(selection)) {
                for (PlanFolder f : folders) {
                    if (f.getName().equals(selection)) {
                        targetFolderId = f.getId();
                        break;
                    }
                }
            }
            FolderService.assignToFolder(FOLDER_NAMESPACE, fav.getId(), targetFolderId);
            applyFilter();
        });
    }

    private String typeLabel(MediaType type) {
        if (type == null) return "";
        return switch (type) {
            case ANIME -> "🎌 ANIME";
            case SERIES -> "📺 SERIES";
            case MOVIE -> "🎬 PELÍCULA";
            case MUSIC -> "🎵 MÚSICA";
            case GAME -> "🎮 JUEGO";
        };
    }

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

    private void styleDialogDark(DialogPane pane) {
        pane.getStylesheets().add(
                getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm()
        );
        pane.getStyleClass().add("bg-panel-flat");
        pane.setStyle("-fx-background-color: #1c1730; -fx-text-fill: #f0eef5;");
        pane.applyCss();

        javafx.scene.Node headerPanel = pane.lookup(".header-panel");
        if (headerPanel != null) {
            headerPanel.setStyle("-fx-background-color: #1c1730;");
        } else {
            javafx.application.Platform.runLater(() -> {
                javafx.scene.Node hp = pane.lookup(".header-panel");
                if (hp != null) hp.setStyle("-fx-background-color: #1c1730;");
            });
        }

        forceLabelColorRecursive(pane, "#f0eef5");
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

    private void darkenDialogWindow(DialogPane pane) {
        if (pane.getScene() != null) {
            pane.getScene().setFill(javafx.scene.paint.Color.web("#100c1c"));
        } else {
            pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setFill(javafx.scene.paint.Color.web("#100c1c"));
                }
            });
        }
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