package com.rujiman.mediatracker.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Barra de navegación lateral fija. Plegada muestra solo iconos; desplegada,
 * iconos y nombres. La sección activa lleva una pestaña del color del fondo
 * que se "funde" con el contenido (esquinas curvadas hacia dentro) y se
 * desliza con un pequeño rebote al cambiar de sección.
 *
 * Debajo: tus secciones (puntos de color con cuántos favoritos tienes) y,
 * abajo del todo, el progreso de logros. Si la ventana es baja, "Tus
 * secciones" se oculta.
 * El estado plegado/desplegado se recuerda entre sesiones.
 */
public class NavRail extends StackPane {

    public static final double COLLAPSED_W = 68;
    public static final double EXPANDED_W = 232;

    private static final double ITEM_H = 40, ROW_H = 32, PAD = 8, R = 12;
    private static final String HOVER = "#ffffff0d", GOLD = "#f5c542";

    /** Rebote suave al final (el rail de "la pestaña" se pasa un poco y vuelve). */
    private static final Interpolator BACK_OUT = new Interpolator() {
        @Override protected double curve(double t) {
            double s = 1.4, u = t - 1;
            return u * u * ((s + 1) * u + s) + 1;
        }
    };

    private final java.util.prefs.Preferences prefs =
            java.util.prefs.Preferences.userNodeForPackage(NavRail.class);

    private final VBox items = new VBox(4);
    private final VBox typeRows = new VBox(2);
    private final VBox typesBox = new VBox(4);
    private final SVGPath tab = new SVGPath();
    private final Map<String, Item> byKey = new LinkedHashMap<>();
    private final List<Node> fading = new ArrayList<>();
    private final List<Node> typeFading = new ArrayList<>();


    private boolean expanded;
    private String activeKey;

    /** Logros, abajo a la izquierda: anillo de progreso y "15 de 67". */
    public static final String ACHIEVEMENTS = "achievements";
    private final Arc ring = new Arc();
    private final Label achCount = new Label();
    private final HBox achRow;
    private Timeline widthAnim, tabAnim;

    private record Item(String key, String icon, Label label, StackPane iconBox, HBox row, Label badge, Circle dot) {}

    public NavRail() {
        expanded = prefs.getBoolean("navExpanded", true);
        double w = expanded ? EXPANDED_W : COLLAPSED_W;
        setPrefWidth(w);
        setMinWidth(Region.USE_PREF_SIZE);
        setMaxWidth(Region.USE_PREF_SIZE);
        setAlignment(Pos.TOP_LEFT);
        setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-border-color: transparent " + Palette.LINE
                + " transparent transparent; -fx-border-width: 0;");

        // Lo que sobresale al plegar (nombres) se recorta
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        // Pestaña de la sección activa: detrás de todo, sin layout propio
        tab.setFill(Color.web(Palette.BG));
        tab.setManaged(false);
        tab.setVisible(false);
        tab.setMouseTransparent(true);
        widthProperty().addListener((o, a, b) -> buildTabShape());

        Label typesTitle = new Label("TUS SECCIONES");
        typesTitle.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 11px; -fx-text-fill: " + Palette.DIM + ";");
        typesTitle.setPadding(new Insets(14, 0, 4, 14));
        typesTitle.setMinWidth(Region.USE_PREF_SIZE);
        fading.add(typesTitle);
        typesBox.getChildren().addAll(typesTitle, typeRows);

        achRow = buildAchievementsRow();
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox content = new VBox(0, items, typesBox, spacer, achRow);
        content.setPadding(new Insets(14, 0, 12, PAD));
        content.setMinWidth(0);

        getChildren().addAll(tab, content);

        // Ventana baja: "Tus secciones" no cabe y se oculta. Y al cambiar
        // de tamaño (o al mostrarse por primera vez) se recoloca la pestaña
        heightProperty().addListener((o, a, h) -> {
            if (activeKey != null) placeTab(false);
            boolean fits = h.doubleValue() > 300 + typeRows.getChildren().size() * (ROW_H + 2) + 120;
            typesBox.setVisible(fits);
            typesBox.setManaged(fits);
        });

        applyLabelState(expanded, false);
    }

    // ── API ─────────────────────────────────────────────────────────────

    /** Añade una sección (Inicio, Buscar...). */
    public void addItem(String key, String icon, String text, Runnable action) {
        Label label = new Label(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setStyle(labelStyle(false, 14));
        fading.add(label);

        StackPane iconBox = new StackPane(Icons.of(icon, 18, Palette.DIM));
        iconBox.setMinSize(COLLAPSED_W - 2 * PAD, ITEM_H);
        iconBox.setMaxSize(COLLAPSED_W - 2 * PAD, ITEM_H);

        Circle dot = new Circle(4, Color.web(Palette.BRAND));
        dot.setStroke(Color.web(Palette.PANEL));
        dot.setStrokeWidth(2);
        dot.setVisible(false);
        dot.setMouseTransparent(true);
        StackPane.setAlignment(dot, Pos.TOP_RIGHT);
        StackPane.setMargin(dot, new Insets(8, 14, 0, 0));
        iconBox.getChildren().add(dot);

        Label badge = new Label();
        badge.setStyle("-fx-background-color: " + Palette.BRAND + "; -fx-background-radius: 999; -fx-text-fill: white;"
                + "-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 1 7; -fx-min-width: 20; -fx-alignment: center;");
        badge.setVisible(false);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        fading.add(badge);

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(2, iconBox, label, grow, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(ITEM_H);
        row.setMaxHeight(ITEM_H);
        row.setPadding(new Insets(0, 12, 0, 0));
        row.setMinWidth(0);
        row.setPrefWidth(EXPANDED_W - 2 * PAD);
        row.setMaxWidth(EXPANDED_W - 2 * PAD);
        row.setStyle(rowStyle(false, false));
        row.setOnMouseEntered(e -> row.setStyle(rowStyle(key.equals(activeKey), true)));
        row.setOnMouseExited(e -> row.setStyle(rowStyle(key.equals(activeKey), false)));
        row.setOnMouseClicked(e -> action.run());
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(350));
        Tooltip.install(row, tip);

        Item item = new Item(key, icon, label, iconBox, row, badge, dot);
        byKey.put(key, item);
        items.getChildren().add(row);
        row.layoutYProperty().addListener((o, a, b) -> { if (key.equals(activeKey)) placeTab(false); });
    }

    /** Número junto a una sección (0 = sin aviso). Plegada, se ve un puntito. */
    public void setBadge(String key, int count) {
        Item it = byKey.get(key);
        if (it == null) return;
        boolean show = count > 0;
        it.badge().setText(count > 9 ? "9+" : String.valueOf(count));
        it.badge().setVisible(show);
        it.dot().setVisible(show && !expanded);
        it.dot().getProperties().put("on", show);
    }

    /** Marca la sección activa (null = ninguna) y mueve la pestaña hasta ella. */
    public void setActive(String key) {
        if (java.util.Objects.equals(key, activeKey)) return;
        boolean wasShown = activeKey != null && tab.isVisible();
        activeKey = key;
        for (Item it : byKey.values()) {
            boolean on = it.key().equals(key);
            it.label().setStyle(labelStyle(on, 14));
            Node icon = Icons.of(it.icon(), 18, on ? Palette.TEXT : Palette.DIM);
            it.iconBox().getChildren().set(0, icon);
            it.row().setStyle(rowStyle(on, it.row().isHover()));
        }
        achRow.setStyle(rowStyle(false, ACHIEVEMENTS.equals(key) || achRow.isHover()));
        if (key == null || !byKey.containsKey(key)) { tab.setVisible(false); return; }
        placeTab(wasShown);
    }

    /** Tus secciones: un punto de color por tipo con su número de favoritos. */
    public void setTypes(List<TypeEntry> types, Consumer<String> onClick) {
        typeRows.getChildren().clear();
        fading.removeAll(typeFading);
        typeFading.clear();
        for (TypeEntry t : types) {
            Circle c = new Circle(4.5, Color.web(t.color()));
            StackPane dotBox = new StackPane(c);
            dotBox.setMinSize(COLLAPSED_W - 2 * PAD, ROW_H);
            dotBox.setMaxSize(COLLAPSED_W - 2 * PAD, ROW_H);
            Label name = new Label(t.name());
            name.setMinWidth(Region.USE_PREF_SIZE);
            name.setStyle(labelStyle(false, 13));
            Label count = new Label(String.valueOf(t.count()));
            count.setMinWidth(Region.USE_PREF_SIZE);
            count.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";");
            name.setOpacity(expanded ? 1 : 0);
            count.setOpacity(expanded ? 1 : 0);
            typeFading.add(name);
            typeFading.add(count);
            fading.add(name);
            fading.add(count);
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            HBox row = new HBox(2, dotBox, name, grow, count);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinHeight(ROW_H);
            row.setMaxHeight(ROW_H);
            row.setPadding(new Insets(0, 14, 0, 0));
            row.setMinWidth(0);
            row.setPrefWidth(EXPANDED_W - 2 * PAD);
            row.setMaxWidth(EXPANDED_W - 2 * PAD);
            row.setStyle(rowStyle(false, false));
            row.setOnMouseEntered(e -> row.setStyle(rowStyle(false, true)));
            row.setOnMouseExited(e -> row.setStyle(rowStyle(false, false)));
            row.setOnMouseClicked(e -> onClick.accept(t.id()));
            Tooltip tip = new Tooltip(t.name() + " · " + t.count());
            tip.setShowDelay(Duration.millis(350));
            Tooltip.install(row, tip);
            typeRows.getChildren().add(row);
        }
        // Se vuelve a comprobar si cabe con el número nuevo de filas
        double h = getHeight();
        if (h > 0) {
            boolean fits = h > 300 + typeRows.getChildren().size() * (ROW_H + 2) + 120;
            typesBox.setVisible(fits);
            typesBox.setManaged(fits);
        }
    }

    public record TypeEntry(String id, String name, String color, int count) {}

    /** Progreso de logros (anillo dorado) y qué hacer al pulsarlo. */
    public void setAchievements(int unlocked, int total, Runnable onClick) {
        double frac = total > 0 ? Math.min(1, (double) unlocked / total) : 0;
        ring.setLength(-360 * frac);
        achCount.setText(unlocked + " de " + total);
        achRow.setOnMouseClicked(e -> onClick.run());
    }

    public boolean isExpanded() { return expanded; }

    /** Pliega o despliega con animación (y lo recuerda). */
    public void toggleExpanded() {
        expanded = !expanded;
        prefs.putBoolean("navExpanded", expanded);
        if (widthAnim != null) widthAnim.stop();
        widthAnim = new Timeline(new KeyFrame(Motion.SLOW,
                new KeyValue(prefWidthProperty(), expanded ? EXPANDED_W : COLLAPSED_W, Motion.EASE_OUT)));
        widthAnim.play();
        applyLabelState(expanded, true);
    }

    // ── Interno ─────────────────────────────────────────────────────────

    private HBox buildAchievementsRow() {
        double size = 36, r = 15, c = size / 2;
        // Círculo y arco en un Pane con coordenadas fijas: si se centraran
        // por sus límites, el arco (solo lo recorrido) se descolocaría
        Circle track = new Circle(c, c, r);
        track.setFill(Color.TRANSPARENT);
        track.setStroke(Color.web(Palette.LINE_STRONG));
        track.setStrokeWidth(3);
        ring.setCenterX(c);
        ring.setCenterY(c);
        ring.setRadiusX(r);
        ring.setRadiusY(r);
        ring.setStartAngle(90);
        ring.setLength(0);
        ring.setType(ArcType.OPEN);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.web(GOLD));
        ring.setStrokeWidth(3);
        ring.setStrokeLineCap(StrokeLineCap.ROUND);
        javafx.scene.layout.Pane rings = new javafx.scene.layout.Pane(track, ring);
        rings.setMinSize(size, size);
        rings.setPrefSize(size, size);
        rings.setMaxSize(size, size);
        StackPane badge = new StackPane(rings, Icons.of(Icons.TROPHY, 14, GOLD));
        badge.setMinSize(size, size);
        badge.setMaxSize(size, size);
        StackPane box = new StackPane(badge);
        box.setMinSize(COLLAPSED_W - 2 * PAD, 48);
        box.setMaxSize(COLLAPSED_W - 2 * PAD, 48);

        Label title = new Label("Logros");
        title.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-text-fill: " + Palette.TEXT + ";");
        achCount.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.DIM + ";");
        VBox text = new VBox(0, title, achCount);
        text.setAlignment(Pos.CENTER_LEFT);
        text.setMinWidth(Region.USE_PREF_SIZE);
        fading.add(text);

        HBox row = new HBox(2, box, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setPrefWidth(EXPANDED_W - 2 * PAD);
        row.setMaxWidth(EXPANDED_W - 2 * PAD);
        row.setStyle(rowStyle(false, false));
        row.setOnMouseEntered(e -> row.setStyle(rowStyle(false, true)));
        row.setOnMouseExited(e -> row.setStyle(rowStyle(false, ACHIEVEMENTS.equals(activeKey))));
        Tooltip tip = new Tooltip("Logros");
        tip.setShowDelay(Duration.millis(350));
        Tooltip.install(row, tip);
        return row;
    }

    /** Nombres y números: aparecen escalonados al desplegar, se van al plegar. */
    private void applyLabelState(boolean show, boolean animate) {
        for (Item it : byKey.values()) {
            boolean on = Boolean.TRUE.equals(it.dot().getProperties().get("on"));
            it.dot().setVisible(on && !show);
        }
        int i = 0;
        for (Node n : fading) {
            if (!animate) {
                n.setOpacity(show ? 1 : 0);
                n.setTranslateX(0);
                continue;
            }
            Duration delay = show ? Duration.millis(90 + 18 * i++) : Duration.ZERO;
            Duration dur = show ? Duration.millis(200) : Duration.millis(110);
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(n.opacityProperty(), n.getOpacity()),
                            new KeyValue(n.translateXProperty(), show ? -6 : 0)),
                    new KeyFrame(dur,
                            new KeyValue(n.opacityProperty(), show ? 1 : 0, Motion.EASE_OUT),
                            new KeyValue(n.translateXProperty(), show ? 0 : -6, Motion.EASE_OUT)));
            t.setDelay(delay);
            t.play();
        }
    }

    /**
     * Forma de la pestaña: rectángulo con esquinas redondas a la izquierda
     * que llega hasta el borde derecho de la barra, y arriba y abajo dos
     * curvas hacia dentro que la unen con el contenido.
     */
    private void buildTabShape() {
        double w = getWidth() - PAD;
        if (w <= 0) return;
        double top = R, bottom = R + ITEM_H;
        tab.setContent(String.format(java.util.Locale.ROOT,
                "M %1$.1f 0 L %1$.1f %2$.1f A %3$.1f %3$.1f 0 0 0 %4$.1f %5$.1f L %3$.1f %5$.1f "
                        + "A %3$.1f %3$.1f 0 0 1 0 %6$.1f L 0 %7$.1f A %3$.1f %3$.1f 0 0 1 %3$.1f %8$.1f "
                        + "L %4$.1f %8$.1f A %3$.1f %3$.1f 0 0 0 %1$.1f 0 Z",
                w, bottom + R, R, w - R, bottom, bottom - R, top + R, top));
        tab.setLayoutX(PAD);
    }

    private void placeTab(boolean animate) {
        Item it = byKey.get(activeKey);
        if (it == null) return;
        // Se espera a que la fila esté colocada para saber su altura real
        javafx.application.Platform.runLater(() -> {
            if (it.row().getScene() == null) return;
            double y = sceneToLocal(it.row().localToScene(0, 0)).getY() - R;
            buildTabShape();
            if (tabAnim != null) tabAnim.stop();
            if (!animate || !tab.isVisible()) {
                tab.setLayoutY(y);
                tab.setVisible(true);
                return;
            }
            tabAnim = new Timeline(new KeyFrame(Duration.millis(420),
                    new KeyValue(tab.layoutYProperty(), y, BACK_OUT)));
            tabAnim.play();
        });
    }

    private static String labelStyle(boolean on, int size) {
        return "-fx-font-family: '" + (on ? "Inter SemiBold" : "Inter") + "'; -fx-font-size: " + size + "px; -fx-text-fill: "
                + (on ? Palette.TEXT : Palette.DIM) + ";";
    }

    private static String rowStyle(boolean active, boolean hover) {
        String bg = !active && hover ? HOVER : "transparent";
        return "-fx-background-color: " + bg + "; -fx-background-radius: " + R + "; -fx-cursor: hand;";
    }
}
