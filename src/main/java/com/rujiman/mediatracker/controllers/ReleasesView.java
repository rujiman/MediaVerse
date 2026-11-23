package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.services.ReleaseService;
import com.rujiman.mediatracker.services.ReleaseService.Release;
import com.rujiman.mediatracker.ui.CollectionUI;
import com.rujiman.mediatracker.ui.Motion;
import com.rujiman.mediatracker.ui.Palette;
import com.rujiman.mediatracker.util.ConfigLoader;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pantalla "Estrenos": próximos episodios y lanzamientos de lo que sigues,
 * agrupados por cuándo salen. Se construye por código, como la biblioteca.
 *
 * Las fechas se cargan en segundo plano (ReleaseService). Mientras, se ve
 * un aviso de carga; si se vuelve a cargar antes de que termine la
 * anterior, solo pinta la última (contador de peticiones).
 */
public class ReleasesView {

    private static final double PAGE_W = 900;
    private static final MediaType[] TYPES = {MediaType.SERIES, MediaType.ANIME, MediaType.MOVIE, MediaType.GAME};

    /** Filtro recordado entre aperturas (como en la biblioteca). null = Todo. */
    private static MediaType typeFilter = null;

    private final Consumer<MediaItem> onOpenItem;
    private final ScrollPane scroll = new ScrollPane();
    private final VBox page = new VBox(24);

    private List<Release> releases = null;  // null = cargando por primera vez
    private boolean loading = false;
    private int requestId = 0;

    public ReleasesView(Consumer<MediaItem> onOpenItem) {
        this.onOpenItem = onOpenItem;
        page.setPadding(new Insets(32, 32, 64, 32));
        page.setMaxWidth(PAGE_W + 64);
        page.setPrefWidth(PAGE_W + 64);
        VBox center = new VBox(page);
        center.setAlignment(Pos.TOP_CENTER);
        center.getStyleClass().add("bg-base");
        scroll.setContent(center);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().addAll("scroll-pane-transparent", "bg-base");
        render();
        load(false);
    }

    public Parent getRoot() { return scroll; }

    /** Pide las fechas en segundo plano. force = ignorar la caché. */
    private void load(boolean force) {
        // Lo que sigue el usuario se lee aquí, en el hilo de JavaFX
        Map<String, MediaItem> candidates = ReleaseService.collectCandidates();
        int myRequest = ++requestId;
        loading = true;
        render();

        Thread t = new Thread(() -> {
            List<Release> result;
            try {
                result = ReleaseService.load(candidates, force);
            } catch (Exception e) {
                System.err.println("⚠️ Error cargando estrenos: " + e.getMessage());
                result = releases != null ? releases : List.of();
            }
            List<Release> finalResult = result;
            Platform.runLater(() -> {
                if (myRequest != requestId) return; // hay otra carga más reciente
                releases = finalResult;
                loading = false;
                render();
            });
        }, "estrenos-carga");
        t.setDaemon(true);
        t.start();
    }

    // ===========================================================
    // PINTADO
    // ===========================================================

    private void render() {
        page.getChildren().clear();
        page.getChildren().addAll(buildHeader(), buildChips());

        Node keysHint = buildKeysHint();
        if (keysHint != null) page.getChildren().add(keysHint);

        if (releases == null) {
            page.getChildren().add(loadingBox("Buscando fechas de lo que sigues…"));
            return;
        }

        List<Release> visible = new ArrayList<>();
        for (Release r : releases) {
            if (typeFilter == null || r.item().getType() == typeFilter) visible.add(r);
        }

        if (visible.isEmpty()) {
            page.getChildren().add(emptyState());
            return;
        }

        Map<String, List<Release>> groups = new LinkedHashMap<>();
        for (Release r : visible) groups.computeIfAbsent(ReleaseService.group(r), k -> new ArrayList<>()).add(r);

        List<Node> sections = new ArrayList<>();
        for (Map.Entry<String, List<Release>> g : groups.entrySet()) {
            VBox rows = new VBox(8);
            for (Release r : g.getValue()) rows.getChildren().add(buildRow(r));
            VBox section = new VBox(12, groupTitle(g.getKey(), g.getValue().size()), rows);
            sections.add(section);
        }
        page.getChildren().addAll(sections);
        Motion.stagger(sections);
    }

    private Node buildHeader() {
        Label title = new Label("Estrenos");
        title.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 32px; -fx-text-fill: " + Palette.TEXT + ";");
        Label sub = new Label("Próximos episodios y lanzamientos de lo que sigues");
        sub.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
        VBox titles = new VBox(4, title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Node right;
        if (loading && releases != null) {
            ProgressIndicator spin = new ProgressIndicator();
            spin.setPrefSize(22, 22);
            spin.setMaxSize(22, 22);
            Label l = new Label("Actualizando…");
            l.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Palette.DIM + ";");
            HBox box = new HBox(10, spin, l);
            box.setAlignment(Pos.CENTER_RIGHT);
            right = box;
        } else {
            Button refresh = new Button("Actualizar");
            refresh.getStyleClass().add("btn-secondary");
            refresh.setDisable(loading);
            refresh.setOnAction(e -> load(true));
            right = refresh;
        }

        HBox header = new HBox(16, titles, spacer, right);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Node buildChips() {
        FlowPane chips = new FlowPane(8, 8);
        chips.getChildren().add(typeChip(null, "Todo", Palette.TEXT));
        for (MediaType t : TYPES) chips.getChildren().add(typeChip(t, MediaTypeTheme.plural(t), MediaTypeTheme.accentHex(t)));
        return chips;
    }

    /** Mismo chip que la biblioteca. */
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
            render();
        });
        return chip;
    }

    /** Sin clave de TMDB o Twitch, explica qué falta (sin bloquear lo demás). */
    private Node buildKeysHint() {
        boolean tmdb = ConfigLoader.hasTmdb(), igdb = ConfigLoader.hasIgdb();
        if (tmdb && igdb) return null;
        String text = !tmdb && !igdb ? "Sin las claves de TMDB y Twitch no se ven series, películas ni juegos."
                : !tmdb ? "Sin la clave de TMDB no se ven series ni películas."
                : "Sin la clave de Twitch no se ven juegos.";
        Label l = new Label(text + " Puedes añadirlas en tu perfil → Claves de API.");
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Palette.TEXT_MUTED + ";");
        HBox box = new HBox(l);
        box.setPadding(new Insets(12, 16, 12, 16));
        box.setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 12; -fx-border-color: " + Palette.LINE + "; -fx-border-radius: 12;");
        return box;
    }

    private Node groupTitle(String text, int count) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + Palette.TEXT + ";");
        Label n = new Label(String.valueOf(count));
        n.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Palette.DIM + ";");
        HBox box = new HBox(10, l, n);
        box.setAlignment(Pos.BASELINE_LEFT);
        return box;
    }

    private Node buildRow(Release r) {
        MediaType type = r.item().getType();
        String accent = MediaTypeTheme.accentHex(type);

        Node poster = CollectionUI.poster(r.item().getImageUrl(), 46, 66, accent, 8, true);

        Label title = new Label(r.item().getTitle());
        title.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 15px; -fx-text-fill: " + Palette.TEXT + ";");

        Circle dot = new Circle(4, javafx.scene.paint.Color.web(accent));
        Label typeLabel = new Label(MediaTypeTheme.label(type));
        typeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + accent + ";");
        String detail = r.hasDate() ? r.what() : r.note();
        Label what = new Label(detail == null ? "" : "·  " + detail);
        what.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Palette.DIM + ";");
        HBox meta = new HBox(6, dot, typeLabel, what);
        meta.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(4, title, meta);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMinWidth(0);

        HBox row = new HBox(16, poster, text);
        if (r.hasDate()) {
            boolean soon = ReleaseService.group(r).equals("Hoy") || ReleaseService.group(r).equals("Mañana");
            Label when = new Label(ReleaseService.whenText(r));
            when.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 14px; -fx-text-fill: "
                    + (soon ? accent : Palette.TEXT) + ";");
            Label countdown = new Label(ReleaseService.countdown(r));
            countdown.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";");
            VBox right = new VBox(2, when, countdown);
            right.setAlignment(Pos.CENTER_RIGHT);
            right.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(right);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 18, 10, 10));
        String base = "-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 14; -fx-border-radius: 14; -fx-cursor: hand;";
        row.setStyle(base + "-fx-border-color: " + Palette.LINE + ";");
        row.setOnMouseEntered(e -> row.setStyle(base + "-fx-border-color: " + accent + "88;"));
        row.setOnMouseExited(e -> row.setStyle(base + "-fx-border-color: " + Palette.LINE + ";"));
        row.setOnMouseClicked(e -> {
            if (e.isStillSincePress() && onOpenItem != null) onOpenItem.accept(r.item());
        });
        return row;
    }

    private Node loadingBox(String text) {
        ProgressIndicator spin = new ProgressIndicator();
        spin.setPrefSize(28, 28);
        spin.setMaxSize(28, 28);
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
        VBox box = new VBox(12, spin, l);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(48));
        return box;
    }

    private Node emptyState() {
        boolean filtered = typeFilter != null && !releases.isEmpty();
        Label title = new Label(filtered ? "Nada de este tipo a la vista" : "Nada a la vista");
        title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + Palette.TEXT + ";");
        Label body = new Label("Cuando algo de lo que sigues tenga fecha, aparecerá aquí. Entran las series y el anime "
                + "que estás viendo o tienes en favoritos, y lo que tienes en Pendiente.");
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
        VBox box = new VBox(8, title, body);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(48));
        box.setStyle("-fx-border-color: " + Palette.LINE_STRONG + "; -fx-border-style: segments(6, 4); -fx-border-radius: 20;");
        return box;
    }
}
