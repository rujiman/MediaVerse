package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanFolder;
import com.rujiman.mediatracker.models.PlanItem;
import com.rujiman.mediatracker.services.PlanService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.util.List;

/**
 * Controller reutilizable para las 3 listas "Pienso ver / Pienso jugar /
 * Pienso escuchar". Mismo patrón que WatchlistViewController, pero con
 * soporte de carpetas: la vista raíz muestra carpetas + items sueltos;
 * al entrar en una carpeta, solo se ven sus items y aparece un botón
 * para volver a la raíz.
 *
 * Reutiliza DetailView para abrir cualquier item (mismo patrón que
 * favoritos/watchlist), pero además permite a quien la abre llamar a
 * setPlanContext() en el DetailViewController resultante, para que el
 * botón "Mover a Favoritos" aparezca correctamente — esa conexión la
 * hace quien abre esta vista (SearchController), no este controller.
 */
public class PlanListViewController {

    @FXML private Button backButton;
    @FXML private Label titleLabel;
    @FXML private Button backToRootButton;
    @FXML private Button newFolderButton;

    @FXML private StackPane statusPane;
    @FXML private Label statusLabel;
    @FXML private ScrollPane scrollPane;

    @FXML private VBox foldersSection;
    @FXML private FlowPane foldersGrid;

    @FXML private VBox itemsSection;
    @FXML private Label itemsSectionLabel;
    @FXML private FlowPane itemsGrid;

    private PlanService.ListKind listKind;
    private String currentFolderId; // null = vista raíz

    /** Se ejecuta al pulsar "Volver" (la define quien abre esta vista). */
    private Runnable onBackAction;

    /**
     * Se ejecuta al pinchar una tarjeta de item: recibe el MediaItem
     * equivalente, el ListKind de esta lista, y el ID del PlanItem
     * original (para que quien abra el detalle pueda llamar a
     * setPlanContext() en él).
     */
    private PlanItemOpenHandler onOpenItemAction;

    @FunctionalInterface
    public interface PlanItemOpenHandler {
        void open(MediaItem item, PlanService.ListKind kind, String planItemId);
    }

    public void setOnBackAction(Runnable onBackAction) {
        this.onBackAction = onBackAction;
    }

    public void setOnOpenItemAction(PlanItemOpenHandler handler) {
        this.onOpenItemAction = handler;
    }

    /**
     * Configura qué lista muestra esta vista. Debe llamarse justo
     * después de cargar el FXML.
     */
    public void setListKind(PlanService.ListKind kind) {
        this.listKind = kind;
        this.currentFolderId = null;

        String title = switch (kind) {
            case WATCH -> "📋 Pienso ver";
            case PLAY -> "🎮 Pienso jugar";
            case LISTEN -> "🎵 Pienso escuchar";
        };
        String accentHex = switch (kind) {
            case WATCH -> "#8b5cf6";
            case PLAY -> "#4dd9ec";
            case LISTEN -> "#4dec9e";
        };

        titleLabel.setText(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 0 0 0 12; -fx-text-fill: " + accentHex + ";");

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

    /** Vuelve de dentro de una carpeta a la vista raíz de la lista. */
    @FXML
    private void onBackToRoot() {
        currentFolderId = null;
        refresh();
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
                PlanService.createFolder(listKind, trimmed);
                refresh();
            }
        });
    }

    /**
     * Vuelve a calcular y pintar la vista actual (raíz o dentro de una
     * carpeta) desde cero. Público para refrescar al volver de un
     * detalle (por ejemplo, si se movió el item a Favoritos desde ahí).
     */
    public void refresh() {
        if (listKind == null) return;

        boolean atRoot = currentFolderId == null;

        backToRootButton.setVisible(!atRoot);
        backToRootButton.setManaged(!atRoot);
        newFolderButton.setVisible(atRoot);
        newFolderButton.setManaged(atRoot);

        foldersSection.setVisible(atRoot);
        foldersSection.setManaged(atRoot);
        foldersGrid.getChildren().clear();

        List<PlanItem> itemsToShow;
        List<PlanFolder> folders = atRoot ? PlanService.getFolders(listKind) : List.of();

        if (atRoot) {
            for (PlanFolder folder : folders) {
                foldersGrid.getChildren().add(buildFolderCard(folder));
            }
            itemsToShow = PlanService.getRootItems(listKind);
            itemsSectionLabel.setText("Sin carpeta");
        } else {
            PlanFolder folder = findFolder(currentFolderId);
            itemsSectionLabel.setText(folder != null ? folder.getName() : "Carpeta");
            itemsToShow = PlanService.getItemsInFolder(listKind, currentFolderId);
        }

        itemsGrid.getChildren().clear();
        for (PlanItem item : itemsToShow) {
            itemsGrid.getChildren().add(buildItemCard(item));
        }

        boolean nothingToShow = folders.isEmpty() && itemsToShow.isEmpty();

        if (nothingToShow) {
            statusPane.setVisible(true);
            statusPane.setManaged(true);
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            statusLabel.setText(emptyMessageFor(listKind));
        } else {
            statusPane.setVisible(false);
            statusPane.setManaged(false);
            scrollPane.setVisible(true);
            scrollPane.setManaged(true);
        }

        itemsSection.setVisible(!itemsToShow.isEmpty());
        itemsSection.setManaged(!itemsToShow.isEmpty());
    }

    private String emptyMessageFor(PlanService.ListKind kind) {
        return switch (kind) {
            case WATCH -> "Todavía no tienes nada en \"Pienso ver\".\nAñade algo desde su detalle con el botón 📋.";
            case PLAY -> "Todavía no tienes nada en \"Pienso jugar\".\nAñade algo desde su detalle con el botón 📋.";
            case LISTEN -> "Todavía no tienes nada en \"Pienso escuchar\".\nAñade algo desde su detalle con el botón 📋.";
        };
    }

    private PlanFolder findFolder(String folderId) {
        for (PlanFolder folder : PlanService.getFolders(listKind)) {
            if (folder.getId().equals(folderId)) return folder;
        }
        return null;
    }

    /**
     * Tarjeta de carpeta: icono grande + nombre + contador de items.
     * Pinchar entra en la carpeta (cambia currentFolderId y refresca).
     */
    private VBox buildFolderCard(PlanFolder folder) {
        List<PlanItem> contents = PlanService.getItemsInFolder(listKind, folder.getId());
        int count = contents.size();

        VBox card = new VBox(6);
        card.setPrefWidth(140);
        card.getStyleClass().add("card-base");
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-padding: 16 10 16 10; -fx-cursor: hand;");

        // Preview tipo "explorador de Windows": un mini-grid 2x2 con las
        // portadas de hasta 4 items que contiene la carpeta, en vez de un
        // icono genérico siempre igual. Si la carpeta tiene menos de 4
        // items (o está vacía), los huecos restantes se rellenan con un
        // icono 📁 tenue, para que el grid siempre se vea completo y
        // simétrico sin importar cuántos items tenga dentro.
        javafx.scene.layout.GridPane previewGrid = new javafx.scene.layout.GridPane();
        previewGrid.setHgap(3);
        previewGrid.setVgap(3);
        previewGrid.setPrefSize(110, 110);
        previewGrid.setMaxSize(110, 110);

        double cellSize = 53.5;
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
                placeholder.setStyle("-fx-font-size: 18px; -fx-opacity: 0.25;");
                cell.getChildren().add(placeholder);
            }

            previewGrid.add(cell, col, row);
        }

        Label nameLabel = new Label(folder.getName());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(120);
        nameLabel.setStyle("-fx-text-fill: #f0eef5; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-alignment: center;");
        nameLabel.setAlignment(Pos.CENTER);

        Label countLabel = new Label(count + (count == 1 ? " elemento" : " elementos"));
        countLabel.getStyleClass().add("text-dim");

        // Botones de gestión con el mismo aspecto de cápsula que los
        // filtros de tipo (Búsqueda/Favoritos), en vez de iconos planos
        // sin fondo.
        HBox actionsRow = new HBox(8);
        actionsRow.setAlignment(Pos.CENTER);

        Button renameBtn = new Button("✏️ Renombrar");
        renameBtn.getStyleClass().add("filter-toggle");
        renameBtn.setStyle("-fx-font-size: 10px; -fx-padding: 4 10 4 10;");
        renameBtn.setOnAction(e -> {
            e.consume();
            onRenameFolder(folder);
        });

        Button deleteBtn = new Button("🗑️ Eliminar");
        deleteBtn.getStyleClass().add("filter-toggle");
        deleteBtn.setStyle("-fx-font-size: 10px; -fx-padding: 4 10 4 10; -fx-text-fill: #e74c3c;");
        deleteBtn.setOnAction(e -> {
            e.consume();
            onDeleteFolder(folder);
        });

        actionsRow.getChildren().addAll(renameBtn, deleteBtn);

        card.getChildren().addAll(previewGrid, nameLabel, countLabel, actionsRow);

        card.setOnMouseClicked(e -> {
            currentFolderId = folder.getId();
            refresh();
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
                PlanService.renameFolder(listKind, folder.getId(), trimmed);
                refresh();
            }
        });
    }

    private void onDeleteFolder(PlanFolder folder) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar carpeta");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Eliminar \"" + folder.getName() + "\"? Los elementos dentro no se borrarán, volverán a quedar sin carpeta.");
        styleDialogDark(confirm.getDialogPane());
        darkenDialogWindow(confirm.getDialogPane());

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                PlanService.deleteFolder(listKind, folder.getId());
                refresh();
            }
        });
    }

    /**
     * Tarjeta de item: imagen, badge de tipo, título, año/score, y un
     * botón "📁" para mover el item a una carpeta. Pinchar la tarjeta
     * abre el detalle.
     */
    private VBox buildItemCard(PlanItem item) {
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

        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            try {
                cover.setImage(new Image(item.getImageUrl(), 160, 220, false, true, true));
            } catch (Exception ignored) {}
        }

        Label typeBadge = new Label(typeLabel(item.getType()));
        typeBadge.getStyleClass().add(badgeClassFor(item.getType()));
        StackPane.setAlignment(typeBadge, Pos.TOP_LEFT);
        StackPane.setMargin(typeBadge, new Insets(6));

        Label moveButton = new Label("📁");
        moveButton.setStyle(
                "-fx-background-color: rgba(28,23,48,0.9);" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 4 6 4 6;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        StackPane.setAlignment(moveButton, Pos.TOP_RIGHT);
        StackPane.setMargin(moveButton, new Insets(6));
        moveButton.setOnMouseClicked(e -> {
            e.consume();
            onMoveItemToFolder(item);
        });

        imageContainer.getChildren().addAll(cover, typeBadge, moveButton);

        Label cardTitle = new Label(item.getTitle());
        cardTitle.setWrapText(true);
        cardTitle.setMaxWidth(150);
        cardTitle.getStyleClass().add("text-heading");
        cardTitle.setStyle("-fx-font-size: 12px; -fx-padding: 6 8 0 8;");

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setStyle("-fx-padding: 0 8 8 8;");

        String yearText = item.getYear() != null ? String.valueOf(item.getYear()) : "—";
        Label yearLabel = new Label(yearText);
        yearLabel.getStyleClass().add("text-dim");
        metaRow.getChildren().add(yearLabel);

        if (item.getScore() != null && item.getScore() > 0) {
            Label scoreLabel = new Label("⭐ " + item.getScore());
            scoreLabel.getStyleClass().add("text-success");
            scoreLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
            HBox.setMargin(scoreLabel, new Insets(0, 0, 0, 4));
            metaRow.getChildren().add(scoreLabel);
        }

        card.getChildren().addAll(imageContainer, cardTitle, metaRow);

        card.setOnMouseClicked(e -> {
            if (onOpenItemAction != null) {
                onOpenItemAction.open(item.toMediaItem(), listKind, item.getId());
            }
        });

        return card;
    }

    /**
     * Selector simple: lista de carpetas existentes + opción "Sin
     * carpeta", para mover un item de sitio.
     */
    private void onMoveItemToFolder(PlanItem item) {
        List<PlanFolder> folders = PlanService.getFolders(listKind);

        List<String> options = new java.util.ArrayList<>();
        options.add("(Sin carpeta)");
        for (PlanFolder f : folders) options.add(f.getName());

        ChoiceDialog<String> dialog = new ChoiceDialog<>("(Sin carpeta)", options);
        dialog.setTitle("Mover \"" + item.getTitle() + "\"");
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
            PlanService.moveItemToFolder(listKind, item.getId(), targetFolderId);
            refresh();
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

    /**
     * Fuerza la paleta oscura en un DialogPane (mismo patrón que
     * HomeController.styleDialogDark, duplicado aquí porque cada
     * controller que abre diálogos los crea de forma independiente).
     */
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

    /**
     * Fuerza fondo oscuro en la ventana que envuelve un Dialog (mismo
     * patrón que HomeController.darkenDialogWindow).
     */
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