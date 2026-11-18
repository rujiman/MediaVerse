package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.ui.Dialogs;
import com.rujiman.mediatracker.ui.Palette;
import com.rujiman.mediatracker.ui.Layout;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanFolder;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.FolderService;
import com.rujiman.mediatracker.services.WatchProgressService;
import com.rujiman.mediatracker.ui.CollectionUI;
import com.rujiman.mediatracker.ui.Icons;
import com.rujiman.mediatracker.ui.Motion;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Favoritos (rediseño): mismo sistema visual que "Mi biblioteca".
 *
 * - Chips por tipo (Todo, Juegos, Anime...).
 * - Carpetas con collage de portadas. Se mete un favorito en una carpeta
 *   ARRASTRANDO su tarjeta encima, o con el botón de carpeta de la
 *   tarjeta (por si la carpeta queda lejos). Dentro de una carpeta,
 *   soltar una tarjeta en «Favoritos» (arriba) la saca de la carpeta.
 * - Tarjetas: portada, tipo, carpeta, quitar de favoritos, título y
 *   progreso / estado / tu nota.
 *
 * Las carpetas siguen guardándose en el namespace "favorites" de
 * FolderService, así que las carpetas que ya existían se conservan.
 */
public class FavoritesViewController {

    private static final String FOLDER_NAMESPACE = "favorites";

    @FXML private ScrollPane favoritesScroll;
    @FXML private VBox favoritesRoot;

    private static final double PAGE_W = Layout.PAGE_W;
    private static final double CARD_W = Layout.CARD_W, POSTER_H = Layout.POSTER_H;
    private static final String TEXT = Palette.TEXT, DIM = Palette.DIM, PANEL = Palette.PANEL, LINE = Palette.LINE;

    // Se recuerda el filtro y la carpeta al volver de un detalle
    private static MediaType typeFilter = null;
    private static String currentFolderId = null;

    private Runnable onBackAction;
    private java.util.function.Consumer<MediaItem> onOpenDetailAction;
    private String draggedId;

    public void setOnBackAction(Runnable onBackAction) { this.onBackAction = onBackAction; }

    public void setOnOpenDetailAction(java.util.function.Consumer<MediaItem> onOpenDetailAction) {
        this.onOpenDetailAction = onOpenDetailAction;
    }

    @FXML
    public void initialize() {
        CollectionUI.darkViewport(favoritesScroll);
        loadFavorites();
    }

    /** Vuelve a leer favoritos y repinta (público por compatibilidad). */
    public void loadFavorites() {
        List<String> validIds = new ArrayList<>();
        for (FavoriteItem f : FavoritesService.getFavorites()) validIds.add(f.getId());
        FolderService.pruneDeletedFavorites(FOLDER_NAMESPACE, validIds);
        refresh();
    }

    // ===========================================================
    // PINTADO
    // ===========================================================

    private void refresh() {
        favoritesRoot.getChildren().clear();

        List<FavoriteItem> all = new ArrayList<>();
        for (FavoriteItem f : FavoritesService.getFavorites()) {
            if (typeFilter == null || f.getType() == typeFilter) all.add(f);
        }
        if (currentFolderId != null && findFolder(currentFolderId) == null) currentFolderId = null;

        favoritesRoot.getChildren().add(buildHeader(FavoritesService.getFavorites().size()));
        favoritesRoot.getChildren().add(buildTypeChips());

        if (currentFolderId == null) {
            List<PlanFolder> folders = FolderService.getFolders(FOLDER_NAMESPACE);
            if (!folders.isEmpty()) {
                favoritesRoot.getChildren().add(sectionTitle("Carpetas"));
                FlowPane grid = fixedFlow(16);
                for (PlanFolder f : folders) grid.getChildren().add(buildFolderCard(f));
                favoritesRoot.getChildren().add(grid);
                favoritesRoot.getChildren().add(sectionTitle("Sin carpeta"));
            }
        } else {
            favoritesRoot.getChildren().add(buildBreadcrumb(findFolder(currentFolderId)));
        }

        List<FavoriteItem> visible = new ArrayList<>();
        for (FavoriteItem f : all) {
            String folder = FolderService.getFolderIdFor(FOLDER_NAMESPACE, f.getId());
            if (currentFolderId == null ? folder == null : currentFolderId.equals(folder)) visible.add(f);
        }

        if (visible.isEmpty()) {
            favoritesRoot.getChildren().add(emptyState(all.isEmpty() && currentFolderId == null));
        } else {
            FlowPane grid = fixedFlow(18);
            for (FavoriteItem f : visible) grid.getChildren().add(buildCard(f));
            favoritesRoot.getChildren().add(grid);
            Motion.stagger(grid.getChildren());
        }
    }

    private Node buildHeader(int total) {
        Label title = new Label("Favoritos");
        title.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 32px; -fx-text-fill: " + TEXT + ";");
        Label count = new Label(total + (total == 1 ? " título" : " títulos"));
        count.setStyle("-fx-font-size: 15px; -fx-text-fill: " + DIM + ";");
        HBox titleRow = new HBox(12, title, count);
        titleRow.setAlignment(Pos.BASELINE_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button newFolder = new Button("Nueva carpeta");
        newFolder.setGraphic(Icons.of(Icons.FOLDER, 16, TEXT));
        newFolder.setGraphicTextGap(8);
        newFolder.getStyleClass().add("btn-secondary");
        newFolder.setOnAction(e -> createFolder(null));

        HBox header = new HBox(16, titleRow, spacer, newFolder);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Node buildTypeChips() {
        FlowPane chips = new FlowPane(8, 8);
        chips.getChildren().add(typeChip(null, "Todo", Palette.TEXT));
        for (MediaType t : new MediaType[]{MediaType.GAME, MediaType.ANIME, MediaType.SERIES, MediaType.MOVIE,
                MediaType.MUSIC, MediaType.BOOK, MediaType.MANGA}) {
            chips.getChildren().add(typeChip(t, MediaTypeTheme.plural(t), MediaTypeTheme.accentHex(t)));
        }
        return chips;
    }

    private Node typeChip(MediaType t, String label, String dot) {
        boolean active = typeFilter == t;
        Button chip = new Button(label);
        chip.setGraphic(new Circle(4, javafx.scene.paint.Color.web(active && t == null ? Palette.ON_ACCENT : dot)));
        chip.setGraphicTextGap(8);
        chip.setStyle("-fx-background-radius: 999; -fx-border-radius: 999; -fx-min-height: 36; -fx-padding: 0 14;"
                + "-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-cursor: hand;"
                + (active ? "-fx-background-color: " + Palette.TEXT + "; -fx-text-fill: " + Palette.ON_ACCENT + "; -fx-border-color: " + Palette.TEXT + ";"
                          : "-fx-background-color: transparent; -fx-text-fill: " + Palette.TEXT_SOFT + "; -fx-border-color: " + Palette.LINE_STRONG + ";"));
        chip.setOnAction(e -> {
            typeFilter = t;
            refresh();
        });
        return chip;
    }

    private Node buildBreadcrumb(PlanFolder folder) {
        Button back = new Button("Favoritos");
        back.setGraphic(Icons.of(Icons.BACK, 16, Palette.TEXT_SOFT));
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(e -> {
            currentFolderId = null;
            refresh();
        });
        makeDropTarget(back, null);
        Label sep = new Label("/");
        sep.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 16px;");
        Label name = new Label(folder != null ? folder.getName() : "");
        name.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + TEXT + ";");
        Label hint = new Label("Arrastra una tarjeta a «Favoritos» para sacarla de la carpeta");
        hint.setStyle("-fx-font-size: 13px; -fx-text-fill: " + DIM + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, back, sep, name, spacer, hint);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + TEXT + ";");
        return l;
    }

    private FlowPane fixedFlow(double gap) {
        FlowPane grid = new FlowPane(gap, gap);
        grid.setPrefWrapLength(PAGE_W);
        grid.setMaxWidth(PAGE_W);
        return grid;
    }

    private Node emptyState(boolean noFavorites) {
        Label title = new Label(noFavorites ? "Aún no tienes favoritos" : "Aquí no hay nada");
        title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + TEXT + ";");
        Label body = new Label(noFavorites
                ? "Busca cualquier juego, serie, anime, peli, libro o manga y pulsa la estrella de su detalle."
                : "Arrastra tarjetas hasta aquí o usa el botón de carpeta de cada tarjeta.");
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 14px; -fx-text-fill: " + DIM + ";");
        VBox box = new VBox(8, title, body);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(48));
        box.setMaxWidth(PAGE_W);
        box.setStyle("-fx-border-color: " + Palette.LINE_STRONG + "; -fx-border-style: segments(6, 4); -fx-border-radius: 20;");
        return box;
    }

    // ===========================================================
    // TARJETAS
    // ===========================================================

    private Node buildCard(FavoriteItem fav) {
        MediaType type = fav.getType();
        String accent = MediaTypeTheme.accentHex(type);

        StackPane posterBox = CollectionUI.poster(fav.getImageUrl(), CARD_W, POSTER_H, accent, 16, false);

        Label badge = new Label(MediaTypeTheme.label(type).toUpperCase());
        badge.setStyle("-fx-background-color: " + accent + "; -fx-text-fill: " + Palette.ON_ACCENT + "; -fx-background-radius: 999;"
                + "-fx-padding: 3 10; -fx-font-family: 'Inter ExtraBold'; -fx-font-size: 10px;");
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(10));

        Button folderBtn = overlayButton(Icons.of(Icons.FOLDER, 16, "#ffffff"), "Mover a una carpeta");
        folderBtn.setOnAction(e -> showFolderMenu(folderBtn, fav));
        Button starBtn = overlayButton(Icons.filled(Icons.STAR, 16, accent), "Quitar de favoritos");
        starBtn.setOnAction(e -> confirmRemove(fav));
        HBox actions = new HBox(6, folderBtn, starBtn);
        actions.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(actions, Pos.TOP_RIGHT);
        StackPane.setMargin(actions, new Insets(8));
        posterBox.getChildren().addAll(badge, actions);

        Label title = new Label(fav.getTitle());
        title.setMaxWidth(CARD_W - 24);
        title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: " + TEXT + ";");

        VBox info = new VBox(8, title);
        info.setPadding(new Insets(12));

        // Progreso si está a medias; si no, estado o tu nota
        String key = WatchProgressService.keyFor(type, fav.getTitle());
        WatchProgressService.Progress p = WatchProgressService.getProgress(key);
        Integer total = fav.getTotalEpisodes() != null ? fav.getTotalEpisodes() : p.totalEpisodes;
        Integer rating = WatchProgressService.getUserRating(key);
        info.getChildren().addAll(CollectionUI.statusLines(type, p, total, rating,
                fav.getYear() != null ? String.valueOf(fav.getYear()) : null, CARD_W - 24));

        VBox card = new VBox(posterBox, info);
        card.setPrefWidth(CARD_W);
        card.setMaxWidth(CARD_W);
        card.getStyleClass().add("card-base");
        card.setStyle("-fx-background-color: " + PANEL + "; -fx-background-radius: 16; -fx-border-color: " + LINE
                + "; -fx-border-radius: 16; -fx-cursor: hand;");

        card.setOnMouseClicked(e -> {
            if (!e.isStillSincePress() || onOpenDetailAction == null) return;
            onOpenDetailAction.accept(fav.toMediaItem());
        });

        card.setOnDragDetected(e -> {
            draggedId = fav.getId();
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            db.setDragView(card.snapshot(params, null), e.getX(), e.getY());
            ClipboardContent content = new ClipboardContent();
            content.putString(fav.getId());
            db.setContent(content);
            card.setOpacity(0.35);
            e.consume();
        });
        card.setOnDragDone(e -> {
            card.setOpacity(1);
            Motion.lift(card, false);
            boolean moved = e.isAccepted();
            draggedId = null;
            if (moved) refresh();
        });
        return card;
    }

    private Button overlayButton(Node icon, String tooltip) {
        Button b = new Button();
        b.setGraphic(icon);
        b.setTooltip(new Tooltip(tooltip));
        b.setStyle("-fx-background-color: #100c1cb3; -fx-background-radius: 10; -fx-min-width: 34; -fx-min-height: 34; -fx-cursor: hand;");
        return b;
    }

    private void confirmRemove(FavoriteItem fav) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (favoritesRoot.getScene() != null) alert.initOwner(favoritesRoot.getScene().getWindow());
        alert.setTitle("Quitar de favoritos");
        alert.setHeaderText(null);
        alert.setContentText("¿Quitar \"" + fav.getTitle() + "\" de favoritos?\nTu progreso y tu nota se conservan.");
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm());
        pane.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");
        pane.lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: " + Palette.TEXT + ";"));
        pane.sceneProperty().addListener((o, a, s) -> { if (s != null) s.setFill(javafx.scene.paint.Color.web(Palette.BG)); });
        alert.showAndWait().ifPresent(b -> {
            if (b == ButtonType.OK) {
                FavoritesService.removeFavorite(fav.getId());
                loadFavorites();
            }
        });
    }

    // ===========================================================
    // CARPETAS
    // ===========================================================

    private Node buildFolderCard(PlanFolder folder) {
        List<String> ids = FolderService.getFavoriteIdsInFolder(FOLDER_NAMESPACE, folder.getId());
        List<FavoriteItem> inside = new ArrayList<>();
        for (FavoriteItem f : FavoritesService.getFavorites()) if (ids.contains(f.getId())) inside.add(f);

        GridPane collage = new GridPane();
        collage.setHgap(4);
        collage.setVgap(4);
        double cell = (CARD_W + 24 - 4) / 2 - 12;
        for (int i = 0; i < 4; i++) {
            Node tile;
            if (i < inside.size()) {
                FavoriteItem f = inside.get(i);
                tile = CollectionUI.poster(f.getImageUrl(), cell, cell, MediaTypeTheme.accentHex(f.getType()), 10, true);
            } else {
                Region empty = new Region();
                empty.setMinSize(cell, cell);
                empty.setStyle("-fx-background-color: " + Palette.PANEL_3 + "; -fx-background-radius: 10;");
                tile = empty;
            }
            collage.add(tile, i % 2, i / 2);
        }

        Label name = new Label(folder.getName());
        name.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: " + TEXT + ";");
        Label count = new Label(inside.size() + (inside.size() == 1 ? " título" : " títulos"));
        count.setStyle("-fx-font-size: 12px; -fx-text-fill: " + DIM + ";");

        Button more = new Button();
        more.setGraphic(Icons.of(Icons.EDIT, 14, TEXT));
        more.setTooltip(new Tooltip("Renombrar o eliminar"));
        more.setStyle("-fx-background-color: " + Palette.PANEL_3 + "; -fx-background-radius: 8; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;");
        more.setOnAction(e -> showFolderOptions(more, folder));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, new VBox(2, name, count), spacer, more);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, collage, footer);
        card.setPadding(new Insets(12));
        card.setPrefWidth(CARD_W + 24);
        card.setMaxWidth(CARD_W + 24);
        card.getStyleClass().add("motion-lift");
        card.setStyle("-fx-background-color: " + PANEL + "; -fx-background-radius: 16; -fx-border-color: " + LINE
                + "; -fx-border-radius: 16; -fx-cursor: hand;");
        card.setOnMouseClicked(e -> {
            if (!e.isStillSincePress()) return;
            currentFolderId = folder.getId();
            refresh();
        });
        makeDropTarget(card, folder.getId());
        return card;
    }

    /** Acepta tarjetas soltadas: folderId null = sacar de la carpeta. */
    private void makeDropTarget(Node target, String folderId) {
        String base = target.getStyle() == null ? "" : target.getStyle();
        target.setOnDragOver(e -> {
            if (draggedId != null) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        target.setOnDragEntered(e -> {
            if (draggedId != null) target.setStyle(base + "-fx-border-color: " + Palette.BRAND + "; -fx-border-width: 2;");
        });
        target.setOnDragExited(e -> target.setStyle(base));
        target.setOnDragDropped(e -> {
            boolean ok = draggedId != null;
            if (ok) FolderService.assignToFolder(FOLDER_NAMESPACE, draggedId, folderId);
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    private void showFolderMenu(Node anchor, FavoriteItem fav) {
        ContextMenu menu = new ContextMenu();
        String current = FolderService.getFolderIdFor(FOLDER_NAMESPACE, fav.getId());
        for (PlanFolder f : FolderService.getFolders(FOLDER_NAMESPACE)) {
            MenuItem item = new MenuItem((f.getId().equals(current) ? "✓ " : "") + f.getName());
            item.setOnAction(e -> {
                FolderService.assignToFolder(FOLDER_NAMESPACE, fav.getId(), f.getId());
                refresh();
            });
            menu.getItems().add(item);
        }
        if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        MenuItem create = new MenuItem("Nueva carpeta…");
        create.setOnAction(e -> createFolder(fav));
        menu.getItems().add(create);
        if (current != null) {
            MenuItem out = new MenuItem("Sacar de la carpeta");
            out.setOnAction(e -> {
                FolderService.assignToFolder(FOLDER_NAMESPACE, fav.getId(), null);
                refresh();
            });
            menu.getItems().add(out);
        }
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void showFolderOptions(Node anchor, PlanFolder folder) {
        MenuItem rename = new MenuItem("Renombrar…");
        rename.setOnAction(e -> CollectionUI.textDialog(window(), "Renombrar carpeta", folder.getName())
                .showAndWait().ifPresent(n -> {
                    if (!n.trim().isEmpty()) {
                        FolderService.renameFolder(FOLDER_NAMESPACE, folder.getId(), n.trim());
                        refresh();
                    }
                }));
        MenuItem delete = new MenuItem("Eliminar carpeta");
        delete.setOnAction(e -> {
            FolderService.deleteFolder(FOLDER_NAMESPACE, folder.getId());
            if (folder.getId().equals(currentFolderId)) currentFolderId = null;
            refresh();
        });
        new ContextMenu(rename, delete).show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void createFolder(FavoriteItem moveInto) {
        CollectionUI.textDialog(window(), "Nueva carpeta", "").showAndWait().ifPresent(n -> {
            String name = n.trim();
            if (name.isEmpty()) return;
            PlanFolder f = FolderService.createFolder(FOLDER_NAMESPACE, name);
            if (moveInto != null) FolderService.assignToFolder(FOLDER_NAMESPACE, moveInto.getId(), f.getId());
            refresh();
        });
    }

    private javafx.stage.Window window() {
        return favoritesRoot.getScene() != null ? favoritesRoot.getScene().getWindow() : null;
    }

    private PlanFolder findFolder(String id) {
        for (PlanFolder f : FolderService.getFolders(FOLDER_NAMESPACE)) if (f.getId().equals(id)) return f;
        return null;
    }

}
