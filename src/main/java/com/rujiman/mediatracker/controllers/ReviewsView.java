package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.Review;
import com.rujiman.mediatracker.services.ReviewService;
import com.rujiman.mediatracker.services.WatchProgressService;
import com.rujiman.mediatracker.ui.CollectionUI;
import com.rujiman.mediatracker.ui.Motion;
import com.rujiman.mediatracker.ui.Palette;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pantalla "Mis reseñas": todas las reseñas del usuario, con orden
 * (recientes o mejor valoradas) y filtro por tipo. Al pinchar una se abre
 * su ficha, donde se edita. Se construye por código, como Estrenos.
 *
 * Solo lee ReviewService.all(): cuando las reseñas vivan en el servidor,
 * esta pantalla no cambia.
 */
public class ReviewsView {

    private static final double PAGE_W = 900;
    private static final MediaType[] TYPES = {MediaType.SERIES, MediaType.ANIME, MediaType.MOVIE, MediaType.GAME,
            MediaType.MUSIC, MediaType.BOOK, MediaType.MANGA};
    private static final java.time.format.DateTimeFormatter DATE =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es"));

    /** Filtro y orden recordados entre aperturas (como en la biblioteca). */
    private static MediaType typeFilter = null;
    private static boolean byRating = false;

    private final Consumer<MediaItem> onOpenItem;
    private final ScrollPane scroll = new ScrollPane();
    private final VBox page = new VBox(24);

    public ReviewsView(Consumer<MediaItem> onOpenItem) {
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
    }

    public Parent getRoot() { return scroll; }

    // ===========================================================
    // PINTADO
    // ===========================================================

    private record Entry(Review review, MediaType type, int rating) {}

    private void render() {
        page.getChildren().clear();

        List<Entry> all = new ArrayList<>();
        for (Review r : ReviewService.all()) {
            MediaType type;
            try { type = MediaType.valueOf(r.type); } catch (Exception e) { continue; }
            Integer stars = WatchProgressService.getUserRating(WatchProgressService.keyFor(type, r.title));
            all.add(new Entry(r, type, stars == null ? 0 : stars));
        }

        page.getChildren().add(buildHeader(all.size()));
        if (all.isEmpty()) {
            page.getChildren().add(emptyState(false));
            return;
        }
        page.getChildren().add(buildChips(all));

        List<Entry> visible = new ArrayList<>();
        for (Entry e : all) if (typeFilter == null || e.type() == typeFilter) visible.add(e);
        if (byRating) {
            // Las mismas estrellas: la más reciente primero (all ya viene así)
            visible.sort(Comparator.comparingInt(Entry::rating).reversed());
        }

        if (visible.isEmpty()) {
            page.getChildren().add(emptyState(true));
            return;
        }
        VBox list = new VBox(12);
        for (Entry e : visible) list.getChildren().add(buildCard(e));
        page.getChildren().add(list);
        Motion.stagger(list.getChildren());
    }

    private Node buildHeader(int total) {
        Label title = new Label("Mis reseñas");
        title.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 32px; -fx-text-fill: " + Palette.TEXT + ";");
        Label count = new Label(total == 1 ? "1 reseña" : total + " reseñas");
        count.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
        VBox titles = new VBox(4, title, count);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Control segmentado de orden (mismo estilo que la biblioteca)
        HBox segmented = new HBox(4);
        segmented.setPadding(new Insets(4));
        segmented.setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 14; -fx-border-color: " + Palette.LINE + "; -fx-border-radius: 14;");
        segmented.getChildren().addAll(sortButton("Recientes", false), sortButton("Mejor valoradas", true));

        HBox header = new HBox(16, titles, spacer);
        if (total > 1) header.getChildren().add(segmented);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Button sortButton(String text, boolean rating) {
        Button b = new Button(text);
        boolean active = byRating == rating;
        b.setStyle("-fx-background-radius: 10; -fx-min-height: 40; -fx-padding: 0 18; -fx-font-family: 'Inter SemiBold'; -fx-font-size: 14px; -fx-cursor: hand;"
                + (active ? "-fx-background-color: " + Palette.TEXT + "; -fx-text-fill: " + Palette.ON_ACCENT + ";"
                          : "-fx-background-color: transparent; -fx-text-fill: " + Palette.TEXT_MUTED + ";"));
        b.setOnAction(e -> {
            byRating = rating;
            render();
        });
        return b;
    }

    /** Chips por tipo; solo salen los tipos que tienen alguna reseña. */
    private Node buildChips(List<Entry> all) {
        FlowPane chips = new FlowPane(8, 8);
        chips.getChildren().add(typeChip(null, "Todo", Palette.TEXT, all.size()));
        for (MediaType t : TYPES) {
            long n = all.stream().filter(e -> e.type() == t).count();
            if (n > 0) chips.getChildren().add(typeChip(t, MediaTypeTheme.plural(t), MediaTypeTheme.accentHex(t), n));
        }
        return chips;
    }

    private Node typeChip(MediaType t, String label, String dot, long count) {
        boolean active = typeFilter == t;
        Button chip = new Button(label + "  " + count);
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

    private Node buildCard(Entry e) {
        Review r = e.review();
        String accent = MediaTypeTheme.accentHex(e.type());

        Node poster = CollectionUI.poster(r.imageUrl, 64, 96, accent, 10, true);

        Label title = new Label(r.title);
        title.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 16px; -fx-text-fill: " + Palette.TEXT + ";");
        title.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(12, title, spacer);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (e.rating() > 0) {
            Label stars = new Label("★".repeat(e.rating()) + "☆".repeat(5 - e.rating()));
            stars.setStyle("-fx-font-size: 15px; -fx-text-fill: " + accent + ";");
            stars.setMinWidth(Region.USE_PREF_SIZE);
            titleRow.getChildren().add(stars);
        }

        Circle dot = new Circle(4, javafx.scene.paint.Color.web(accent));
        Label typeLabel = new Label(MediaTypeTheme.label(e.type()));
        typeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + accent + ";");
        StringBuilder metaText = new StringBuilder();
        if (r.year != null) metaText.append("·  ").append(r.year).append("   ");
        String date = dateText(r);
        if (!date.isEmpty()) metaText.append("·  ").append(date);
        Label metaLabel = new Label(metaText.toString().trim());
        metaLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Palette.DIM + ";");
        HBox meta = new HBox(6, dot, typeLabel, metaLabel);
        meta.setAlignment(Pos.CENTER_LEFT);
        if (r.spoilers) {
            Label tag = new Label("Spoilers");
            tag.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 11px; -fx-text-fill: " + accent + ";"
                    + "-fx-border-color: " + accent + "; -fx-border-radius: 999; -fx-padding: 1 8;");
            HBox.setMargin(tag, new Insets(0, 0, 0, 6));
            meta.getChildren().add(tag);
        }

        // Texto cortado a ~4 líneas (JavaFX pone los puntos suspensivos)
        Label text = new Label(r.text);
        text.setWrapText(true);
        text.setLineSpacing(3);
        text.setMaxHeight(4 * 23);
        text.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.TEXT_SOFT + ";");

        VBox body = new VBox(6, titleRow, meta, text);
        VBox.setMargin(text, new Insets(4, 0, 0, 0));
        HBox.setHgrow(body, Priority.ALWAYS);
        body.setMinWidth(0);

        HBox card = new HBox(18, poster, body);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(16, 20, 16, 16));
        String base = "-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 16; -fx-border-radius: 16; -fx-cursor: hand;";
        card.setStyle(base + "-fx-border-color: " + Palette.LINE + ";");
        card.setOnMouseEntered(ev -> card.setStyle(base + "-fx-border-color: " + accent + "88;"));
        card.setOnMouseExited(ev -> card.setStyle(base + "-fx-border-color: " + Palette.LINE + ";"));
        card.setOnMouseClicked(ev -> {
            if (!ev.isStillSincePress() || onOpenItem == null) return;
            MediaItem item = ReviewService.toMediaItem(r);
            if (item != null) onOpenItem.accept(item);
        });
        return card;
    }

    private static String dateText(Review r) {
        try {
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.LocalDate created = java.time.Instant.parse(r.createdAt).atZone(zone).toLocalDate();
            java.time.LocalDate updated = r.updatedAt != null ? java.time.Instant.parse(r.updatedAt).atZone(zone).toLocalDate() : created;
            return updated.isAfter(created) ? "Editada el " + DATE.format(updated) : "Escrita el " + DATE.format(created);
        } catch (Exception e) {
            return "";
        }
    }

    private Node emptyState(boolean filtered) {
        Label title = new Label(filtered ? "Ninguna reseña de este tipo" : "Aún no has escrito reseñas");
        title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + Palette.TEXT + ";");
        Label body = new Label("Las escribes desde la ficha de cualquier título, debajo de tus estrellas.");
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
        VBox box = new VBox(8, title, body);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(48));
        box.setStyle("-fx-border-color: " + Palette.LINE_STRONG + "; -fx-border-style: segments(6, 4); -fx-border-radius: 20;");
        return box;
    }
}
