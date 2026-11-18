package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanFolder;
import com.rujiman.mediatracker.models.PlanItem;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.FolderService;
import com.rujiman.mediatracker.services.PlanService;
import com.rujiman.mediatracker.services.WatchProgressService;
import com.rujiman.mediatracker.ui.Icons;
import com.rujiman.mediatracker.ui.Motion;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Mi biblioteca": UNA pantalla que sustituye a las 12 entradas antiguas
 * del menú ("Pienso ver/jugar/escuchar/leer", "Series vistas/viendo",
 * "Anime vistos/viendo", "Manga leídos/leyendo"...).
 *
 *   Estado:  Pendiente · En curso · Terminado
 *   Tipo:    Todo · Juegos · Anime · Series · Películas · Música · Libros · Manga
 *
 * - Pendiente  = lo que tienes en tus listas de "Pienso…" (PlanService).
 * - En curso   = con algún episodio/capítulo marcado pero sin terminar.
 * - Terminado  = marcado como visto/jugado/leído/escuchado.
 * En curso y Terminado incluyen favoritos Y títulos con progreso que no
 * son favoritos (ficha guardada en progress.json).
 *
 * Carpetas: cada estado tiene las suyas (FolderService, namespace
 * "biblioteca_<estado>"). Se mete un título en una carpeta ARRASTRANDO su
 * tarjeta encima de la carpeta, o con el botón de carpeta de la tarjeta
 * (útil si la carpeta queda lejos en pantallas largas).
 *
 * Todo se construye por código (como la vitrina de Inicio); el FXML solo
 * aporta el ScrollPane y el contenedor. Ancho fijo de 1240 px centrado.
 */
public class LibraryViewController {

    @FXML private ScrollPane libraryScroll;
    @FXML private VBox libraryRoot;

    public enum State { PENDING, IN_PROGRESS, DONE }

    /** Un título de la biblioteca, venga de un plan, de favoritos o de progreso. */
    public record Entry(String id, MediaItem item, WatchProgressService.Progress progress,
                        PlanService.ListKind planKind, String planItemId) {}

    /** Callback para abrir el detalle (planKind/planItemId != null si es "Pendiente"). */
    public interface OpenItemAction {
        void open(MediaItem item, PlanService.ListKind planKind, String planItemId);
    }

    // Se recuerda la última vista al volver de un detalle
    private static State state = State.IN_PROGRESS;
    private static MediaType typeFilter = null;   // null = Todo
    private static String currentFolderId = null;

    private static final double PAGE_W = 1240;
    private static final double CARD_W = 176, POSTER_H = 264;
    private static final String TEXT = "#f0eef5", DIM = "#a59fc0", PANEL = "#1a1530", LINE = "#2a2347";

    private OpenItemAction onOpenItem;
    private String draggedEntryId;

    public void setOnOpenItem(OpenItemAction action) { this.onOpenItem = action; }

    @FXML
    public void initialize() {
        libraryScroll.skinProperty().addListener((o, a, b) -> {
            if (b != null) applyViewportBackground();
        });
        refresh();
    }

    private void applyViewportBackground() {
        Node viewport = libraryScroll.lookup(".viewport");
        if (viewport != null) viewport.setStyle("-fx-background-color: #100c1c;");
    }

    private String namespace() {
        return "biblioteca_" + state.name().toLowerCase();
    }

    // ===========================================================
    // PINTADO
    // ===========================================================

    public void refresh() {
        libraryRoot.getChildren().clear();
        List<Entry> entries = collect();

        // Limpieza de asignaciones huérfanas (solo con "Todo": si hay un
        // filtro de tipo, faltarían los demás y se borrarían sus carpetas)
        if (typeFilter == null) {
            List<String> ids = new ArrayList<>();
            for (Entry e : entries) ids.add(e.id());
            FolderService.pruneDeletedFavorites(namespace(), ids);
        }

        libraryRoot.getChildren().add(buildHeader());
        libraryRoot.getChildren().add(buildTypeChips());

        if (currentFolderId != null) {
            PlanFolder folder = findFolder(currentFolderId);
            if (folder == null) currentFolderId = null;
        }

        if (currentFolderId == null) {
            List<PlanFolder> folders = FolderService.getFolders(namespace());
            if (!folders.isEmpty()) {
                libraryRoot.getChildren().add(sectionTitle("Carpetas"));
                FlowPane grid = fixedFlow(16);
                for (PlanFolder f : folders) grid.getChildren().add(buildFolderCard(f, entries));
                libraryRoot.getChildren().add(grid);
            }
        } else {
            libraryRoot.getChildren().add(buildBreadcrumb(findFolder(currentFolderId)));
        }

        List<Entry> visible = new ArrayList<>();
        for (Entry e : entries) {
            String folder = FolderService.getFolderIdFor(namespace(), e.id());
            if (currentFolderId == null ? folder == null : currentFolderId.equals(folder)) visible.add(e);
        }

        if (currentFolderId == null && !FolderService.getFolders(namespace()).isEmpty()) {
            libraryRoot.getChildren().add(sectionTitle("Sin carpeta"));
        }

        if (visible.isEmpty()) {
            libraryRoot.getChildren().add(emptyState(entries.isEmpty()));
        } else {
            FlowPane grid = fixedFlow(18);
            for (Entry e : visible) grid.getChildren().add(buildCard(e));
            libraryRoot.getChildren().add(grid);
            Motion.stagger(grid.getChildren());
        }
    }

    private Node buildHeader() {
        Label title = new Label("Mi biblioteca");
        title.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 32px; -fx-text-fill: " + TEXT + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Control segmentado de estado
        HBox segmented = new HBox(4);
        segmented.setPadding(new Insets(4));
        segmented.setStyle("-fx-background-color: " + PANEL + "; -fx-background-radius: 14; -fx-border-color: " + LINE + "; -fx-border-radius: 14;");
        for (State s : State.values()) {
            Button b = new Button(stateLabel(s));
            boolean active = s == state;
            b.setStyle("-fx-background-radius: 10; -fx-min-height: 40; -fx-padding: 0 18; -fx-font-family: 'Inter SemiBold'; -fx-font-size: 14px; -fx-cursor: hand;"
                    + (active ? "-fx-background-color: #f0eef5; -fx-text-fill: #120c1c;" : "-fx-background-color: transparent; -fx-text-fill: #cfc9e6;"));
            b.setOnAction(e -> {
                state = s;
                currentFolderId = null;
                refresh();
            });
            segmented.getChildren().add(b);
        }

        Button newFolder = new Button("Nueva carpeta");
        newFolder.setGraphic(Icons.of(Icons.FOLDER, 16, TEXT));
        newFolder.setGraphicTextGap(8);
        newFolder.getStyleClass().add("btn-secondary");
        newFolder.setOnAction(e -> createFolder(null));

        HBox header = new HBox(16, title, spacer, segmented, newFolder);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Node buildTypeChips() {
        FlowPane chips = new FlowPane(8, 8);
        chips.getChildren().add(typeChip(null, "Todo", "#f0eef5"));
        for (MediaType t : new MediaType[]{MediaType.GAME, MediaType.ANIME, MediaType.SERIES, MediaType.MOVIE,
                MediaType.MUSIC, MediaType.BOOK, MediaType.MANGA}) {
            chips.getChildren().add(typeChip(t, plural(t), MediaTypeTheme.accentHex(t)));
        }
        Label hint = new Label(stateHint());
        hint.setStyle("-fx-font-size: 14px; -fx-text-fill: " + DIM + ";");
        return new VBox(14, chips, hint);
    }

    private Node typeChip(MediaType t, String label, String dot) {
        boolean active = typeFilter == t;
        Button chip = new Button(label);
        chip.setGraphic(new Circle(4, javafx.scene.paint.Color.web(active && t == null ? "#120c1c" : dot)));
        chip.setGraphicTextGap(8);
        chip.setStyle("-fx-background-radius: 999; -fx-border-radius: 999; -fx-min-height: 36; -fx-padding: 0 14;"
                + "-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-cursor: hand;"
                + (active ? "-fx-background-color: #f0eef5; -fx-text-fill: #120c1c; -fx-border-color: #f0eef5;"
                          : "-fx-background-color: transparent; -fx-text-fill: #e6e1f5; -fx-border-color: #3a3160;"));
        chip.setOnAction(e -> {
            typeFilter = t;
            refresh();
        });
        return chip;
    }

    private Node buildBreadcrumb(PlanFolder folder) {
        Button back = new Button("Mi biblioteca");
        back.setGraphic(Icons.of(Icons.BACK, 16, "#e6e1f5"));
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(e -> {
            currentFolderId = null;
            refresh();
        });
        // Soltar una tarjeta aquí la saca de la carpeta
        makeDropTarget(back, null);
        Label sep = new Label("/");
        sep.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 16px;");
        Label name = new Label(folder != null ? folder.getName() : "");
        name.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + TEXT + ";");
        Label hint = new Label("Arrastra una tarjeta a «Mi biblioteca» para sacarla de la carpeta");
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

    private Node emptyState(boolean nothingAtAll) {
        Label title = new Label(nothingAtAll ? emptyTitle() : "Esta carpeta está vacía");
        title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + TEXT + ";");
        Label body = new Label(nothingAtAll ? emptyBody() : "Arrastra tarjetas hasta su carpeta o usa el botón de carpeta de cada tarjeta.");
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 14px; -fx-text-fill: " + DIM + ";");
        VBox box = new VBox(8, title, body);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(48));
        box.setMaxWidth(PAGE_W);
        box.setStyle("-fx-border-color: #3a3160; -fx-border-style: segments(6, 4); -fx-border-radius: 20;");
        return box;
    }

    // ===========================================================
    // TARJETAS
    // ===========================================================

    private Node buildCard(Entry e) {
        MediaType type = e.item().getType();
        String accent = MediaTypeTheme.accentHex(type);

        StackPane posterBox = poster(e.item().getImageUrl(), CARD_W, POSTER_H, accent);

        Label badge = new Label(MediaTypeTheme.label(type).toUpperCase());
        badge.setStyle("-fx-background-color: " + accent + "; -fx-text-fill: #120c1c; -fx-background-radius: 999;"
                + "-fx-padding: 3 10; -fx-font-family: 'Inter ExtraBold'; -fx-font-size: 10px;");
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(10));

        // Botón de carpeta (para cuando arrastrar queda lejos)
        Button folderBtn = new Button();
        folderBtn.setGraphic(Icons.of(Icons.FOLDER, 16, "#ffffff"));
        folderBtn.setTooltip(new Tooltip("Mover a una carpeta"));
        folderBtn.setStyle("-fx-background-color: #100c1cb3; -fx-background-radius: 10; -fx-min-width: 34; -fx-min-height: 34; -fx-cursor: hand;");
        StackPane.setAlignment(folderBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(folderBtn, new Insets(8));
        folderBtn.setOnAction(ev -> showFolderMenu(folderBtn, e));
        posterBox.getChildren().addAll(badge, folderBtn);

        Label title = new Label(e.item().getTitle());
        title.setMaxWidth(CARD_W - 24);
        title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: " + TEXT + ";");

        VBox info = new VBox(8, title);
        info.setPadding(new Insets(12));

        // Estado: misma función que Favoritos (CollectionUI.statusLines)
        String key = WatchProgressService.keyFor(type, e.item().getTitle());
        WatchProgressService.Progress p = e.progress() != null ? e.progress() : WatchProgressService.getProgress(key);
        Integer total = p.totalEpisodes != null ? p.totalEpisodes : e.item().getEpisodes();
        Integer rating = WatchProgressService.getUserRating(key);
        String fallback = state == State.PENDING ? statusText(e)
                : e.item().getYear() != null ? String.valueOf(e.item().getYear()) : null;
        info.getChildren().addAll(com.rujiman.mediatracker.ui.CollectionUI.statusLines(type, p, total, rating, fallback, CARD_W - 24));

        VBox card = new VBox(posterBox, info);
        card.setPrefWidth(CARD_W);
        card.setMaxWidth(CARD_W);
        card.getStyleClass().add("card-base");
        card.setStyle("-fx-background-color: " + PANEL + "; -fx-background-radius: 16; -fx-border-color: " + LINE
                + "; -fx-border-radius: 16; -fx-cursor: hand;");

        card.setOnMouseClicked(ev -> {
            if (!ev.isStillSincePress() || onOpenItem == null) return;
            onOpenItem.open(e.item(), e.planKind(), e.planItemId());
        });

        // Arrastrar la tarjeta hasta una carpeta
        card.setOnDragDetected(ev -> {
            draggedEntryId = e.id();
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            db.setDragView(card.snapshot(params, null), ev.getX(), ev.getY());
            ClipboardContent content = new ClipboardContent();
            content.putString(e.id());
            db.setContent(content);
            card.setOpacity(0.35);
            ev.consume();
        });
        card.setOnDragDone(ev -> {
            card.setOpacity(1);
            Motion.lift(card, false);
            boolean moved = ev.isAccepted();
            draggedEntryId = null;
            if (moved) refresh();
        });
        return card;
    }

    private StackPane poster(String url, double w, double h, String accent) {
        StackPane box = new StackPane();
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        box.setStyle("-fx-background-color: " + accent + "22; -fx-background-radius: 16 16 0 0;");
        if (url != null && !url.isBlank()) {
            ImageView img = new ImageView();
            img.setFitWidth(w);
            img.setFitHeight(h);
            img.setPreserveRatio(false);
            img.setSmooth(true);
            Rectangle clip = new Rectangle(w, h + 16);
            clip.setArcWidth(32);
            clip.setArcHeight(32);
            img.setClip(clip);
            try {
                Image image = new Image(url, w * 2, h * 2, true, true, true);
                img.setImage(image);
                img.setOpacity(0);
                Runnable ready = () -> {
                    cropToFill(img, image, w, h);
                    javafx.animation.FadeTransition f = new javafx.animation.FadeTransition(Motion.BASE, img);
                    f.setToValue(1);
                    f.play();
                };
                if (image.getProgress() >= 1.0) ready.run();
                else image.progressProperty().addListener((o, a, b) -> {
                    if (b.doubleValue() >= 1.0 && !image.isError()) ready.run();
                });
            } catch (Exception ignored) {}
            box.getChildren().add(img);
        }
        return box;
    }

    private void cropToFill(ImageView view, Image img, double w, double h) {
        double iw = img.getWidth(), ih = img.getHeight();
        if (iw <= 0 || ih <= 0) return;
        double target = w / h;
        double cw = iw, ch = iw / target;
        if (ch > ih) { ch = ih; cw = ih * target; }
        view.setViewport(new javafx.geometry.Rectangle2D((iw - cw) / 2, (ih - ch) / 2, cw, ch));
    }

    // ===========================================================
    // CARPETAS
    // ===========================================================

    private Node buildFolderCard(PlanFolder folder, List<Entry> entries) {
        List<String> ids = FolderService.getFavoriteIdsInFolder(namespace(), folder.getId());
        List<Entry> inside = new ArrayList<>();
        for (Entry e : entries) if (ids.contains(e.id())) inside.add(e);

        // Collage 2x2 con las primeras portadas
        GridPane collage = new GridPane();
        collage.setHgap(4);
        collage.setVgap(4);
        double cell = (CARD_W + 24 - 4) / 2 - 12;
        for (int i = 0; i < 4; i++) {
            Node tile;
            if (i < inside.size()) {
                Entry e = inside.get(i);
                StackPane p = poster(e.item().getImageUrl(), cell, cell, MediaTypeTheme.accentHex(e.item().getType()));
                p.setStyle("-fx-background-color: #241d3d; -fx-background-radius: 10;");
                ((ImageView) (p.getChildren().isEmpty() ? new ImageView() : p.getChildren().get(0))).setClip(roundClip(cell, cell, 10));
                tile = p;
            } else {
                Region empty = new Region();
                empty.setMinSize(cell, cell);
                empty.setStyle("-fx-background-color: #241d3d; -fx-background-radius: 10;");
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
        more.setStyle("-fx-background-color: #241d3d; -fx-background-radius: 8; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;");
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

    private Rectangle roundClip(double w, double h, double r) {
        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(r * 2);
        clip.setArcHeight(r * 2);
        return clip;
    }

    /** Hace que un nodo acepte tarjetas soltadas: folderId null = sacar de la carpeta. */
    private void makeDropTarget(Node target, String folderId) {
        String base = target.getStyle() == null ? "" : target.getStyle();
        target.setOnDragOver(e -> {
            if (draggedEntryId != null) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        target.setOnDragEntered(e -> {
            if (draggedEntryId != null) target.setStyle(base + "-fx-border-color: #ec4d80; -fx-border-width: 2;");
        });
        target.setOnDragExited(e -> target.setStyle(base));
        target.setOnDragDropped(e -> {
            boolean ok = false;
            if (draggedEntryId != null) {
                FolderService.assignToFolder(namespace(), draggedEntryId, folderId);
                ok = true;
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    private void showFolderMenu(Node anchor, Entry entry) {
        ContextMenu menu = new ContextMenu();
        String current = FolderService.getFolderIdFor(namespace(), entry.id());
        for (PlanFolder f : FolderService.getFolders(namespace())) {
            MenuItem item = new MenuItem((f.getId().equals(current) ? "✓ " : "") + f.getName());
            item.setOnAction(e -> {
                FolderService.assignToFolder(namespace(), entry.id(), f.getId());
                refresh();
            });
            menu.getItems().add(item);
        }
        if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        MenuItem create = new MenuItem("Nueva carpeta…");
        create.setOnAction(e -> createFolder(entry));
        menu.getItems().add(create);
        if (current != null) {
            MenuItem out = new MenuItem("Sacar de la carpeta");
            out.setOnAction(e -> {
                FolderService.assignToFolder(namespace(), entry.id(), null);
                refresh();
            });
            menu.getItems().add(out);
        }
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void showFolderOptions(Node anchor, PlanFolder folder) {
        MenuItem rename = new MenuItem("Renombrar…");
        rename.setOnAction(e -> {
            TextInputDialog d = textDialog("Renombrar carpeta", folder.getName());
            d.showAndWait().ifPresent(n -> {
                if (!n.trim().isEmpty()) {
                    FolderService.renameFolder(namespace(), folder.getId(), n.trim());
                    refresh();
                }
            });
        });
        MenuItem delete = new MenuItem("Eliminar carpeta");
        delete.setOnAction(e -> {
            // Los títulos no se borran: vuelven a "Sin carpeta"
            FolderService.deleteFolder(namespace(), folder.getId());
            if (folder.getId().equals(currentFolderId)) currentFolderId = null;
            refresh();
        });
        new ContextMenu(rename, delete).show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    /** Crea una carpeta; si viene de una tarjeta, la mete directamente dentro. */
    private void createFolder(Entry moveInto) {
        TextInputDialog d = textDialog("Nueva carpeta", "");
        d.showAndWait().ifPresent(n -> {
            String name = n.trim();
            if (name.isEmpty()) return;
            PlanFolder f = FolderService.createFolder(namespace(), name);
            if (moveInto != null) FolderService.assignToFolder(namespace(), moveInto.id(), f.getId());
            refresh();
        });
    }

    private TextInputDialog textDialog(String title, String value) {
        TextInputDialog d = new TextInputDialog(value);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText("Nombre:");
        if (libraryRoot.getScene() != null) d.initOwner(libraryRoot.getScene().getWindow());
        DialogPane pane = d.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm());
        pane.setStyle("-fx-background-color: #1c1730;");
        pane.lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: #f0eef5;"));
        pane.sceneProperty().addListener((o, a, s) -> { if (s != null) s.setFill(javafx.scene.paint.Color.web("#100c1c")); });
        return d;
    }

    private PlanFolder findFolder(String id) {
        for (PlanFolder f : FolderService.getFolders(namespace())) if (f.getId().equals(id)) return f;
        return null;
    }

    // ===========================================================
    // DATOS
    // ===========================================================

    private List<Entry> collect() {
        List<Entry> result = new ArrayList<>();
        if (state == State.PENDING) {
            for (PlanService.ListKind kind : PlanService.ListKind.values()) {
                for (PlanItem p : PlanService.getItems(kind)) {
                    if (typeFilter != null && p.getType() != typeFilter) continue;
                    result.add(new Entry(p.getId(), p.toMediaItem(), null, kind, p.getId()));
                }
            }
            return result;
        }

        Set<String> seen = new HashSet<>();
        for (MediaType t : MediaType.values()) {
            if (typeFilter != null && t != typeFilter) continue;
            for (FavoriteItem fav : FavoritesService.getFavoritesByType(t)) {
                String key = WatchProgressService.keyFor(t, fav.getTitle()).toLowerCase();
                WatchProgressService.Progress p = WatchProgressService.getProgress(key);
                if (matches(p) && seen.add(key)) result.add(new Entry(fav.getId(), fav.toMediaItem(), p, null, null));
            }
            for (WatchProgressService.Tracked tr : WatchProgressService.getTracked(t)) {
                String key = WatchProgressService.keyFor(t, tr.item().getTitle()).toLowerCase();
                if (matches(tr.progress()) && seen.add(key)) {
                    result.add(new Entry("progress::" + key, tr.item(), tr.progress(), null, null));
                }
            }
        }
        return result;
    }

    private boolean matches(WatchProgressService.Progress p) {
        if (p == null) return false;
        return state == State.DONE ? p.viewed : (!p.viewed && (!p.watchedEpisodes.isEmpty() || p.inProgress));
    }

    // ===========================================================
    // TEXTOS
    // ===========================================================

    private String stateLabel(State s) {
        return switch (s) {
            case PENDING -> "Pendiente";
            case IN_PROGRESS -> "En curso";
            case DONE -> "Terminado";
        };
    }

    private String stateHint() {
        return switch (state) {
            case PENDING -> "Lo que quieres ver, jugar, escuchar o leer más adelante (botón de marcador en cada detalle).";
            case IN_PROGRESS -> "Lo que tienes a medias, sea favorito o no.";
            case DONE -> "Todo lo que ya has terminado.";
        };
    }

    private String emptyTitle() {
        return switch (state) {
            case PENDING -> "Nada pendiente por aquí";
            case IN_PROGRESS -> "No tienes nada a medias";
            case DONE -> "Todavía no has terminado nada";
        };
    }

    private String emptyBody() {
        return switch (state) {
            case PENDING -> "Abre cualquier título y pulsa el marcador para guardarlo para más adelante.";
            case IN_PROGRESS -> "Marca un episodio o capítulo, o pulsa «Estoy jugando / viendo / leyendo» en cualquier detalle.";
            case DONE -> "Cuando marques algo como visto, jugado, leído o escuchado, aparecerá aquí.";
        };
    }

    private String statusText(Entry e) {
        if (state == State.PENDING && e.planKind() != null) {
            return switch (e.planKind()) {
                case WATCH -> "Pienso verlo";
                case PLAY -> "Pienso jugarlo";
                case LISTEN -> "Pienso escucharlo";
                case READ -> "Pienso leerlo";
            };
        }
        return MediaTypeTheme.doneText(e.item().getType());
    }

    private String plural(MediaType t) {
        return switch (t) {
            case GAME -> "Juegos";
            case SERIES -> "Series";
            case ANIME -> "Anime";
            case MUSIC -> "Música";
            case MOVIE -> "Películas";
            case BOOK -> "Libros";
            case MANGA -> "Manga";
        };
    }
}
