package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.services.AchievementService;
import com.rujiman.mediatracker.services.AchievementService.Achievement;
import com.rujiman.mediatracker.services.ActivityService;
import com.rujiman.mediatracker.ui.Icons;
import com.rujiman.mediatracker.ui.Motion;
import com.rujiman.mediatracker.ui.Palette;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pantalla "Logros": racha y mapa de actividad arriba, filtros de
 * dificultad y estado, y los logros por categoría. Se construye por
 * código, como Estrenos y Mis reseñas.
 */
public class AchievementsView {

    private static final double PAGE_W = 900;
    private static final double CARD_W = (PAGE_W - 3 * 12) / 4;
    private static final int WEEKS = 18;
    private static final java.time.format.DateTimeFormatter DATE =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es"));

    private enum Status { ALL, DONE, PENDING }

    /** Filtros recordados entre aperturas. 0 = todas las dificultades. */
    private static int difficultyFilter = 0;
    private static Status statusFilter = Status.ALL;

    private final ScrollPane scroll = new ScrollPane();
    /** Aviso de un momento ("Ya tienes 3 fijados…"), se borra al repintar. */
    private Label pinMessage = null;
    private final VBox page = new VBox(24);

    public AchievementsView() {
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
        AchievementService.check(); // progreso al día antes de pintar
        render();
    }

    public Parent getRoot() { return scroll; }

    private void render() {
        double scrollPos = scroll.getVvalue();
        page.getChildren().clear();
        page.getChildren().addAll(buildHeader(), buildStreakCard(), buildFilters());

        List<Node> sections = new ArrayList<>();
        for (String category : AchievementService.CATEGORIES) {
            List<Achievement> all = new ArrayList<>(), visible = new ArrayList<>();
            for (Achievement a : AchievementService.catalog()) {
                if (!a.category().equals(category)) continue;
                all.add(a);
                boolean done = AchievementService.isUnlocked(a);
                if (difficultyFilter != 0 && a.difficulty() != difficultyFilter) continue;
                if (statusFilter == Status.DONE && !done) continue;
                if (statusFilter == Status.PENDING && done) continue;
                visible.add(a);
            }
            if (visible.isEmpty()) continue;
            long doneCount = all.stream().filter(AchievementService::isUnlocked).count();

            Label title = new Label(category);
            title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + Palette.TEXT + ";");
            Label count = new Label(doneCount + " / " + all.size());
            count.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Palette.DIM + ";");
            HBox head = new HBox(10, title, count);
            head.setAlignment(Pos.BASELINE_LEFT);

            FlowPane grid = new FlowPane(12, 12);
            grid.setPrefWrapLength(PAGE_W);
            grid.setMaxWidth(PAGE_W);
            for (Achievement a : visible) grid.getChildren().add(buildCard(a));
            sections.add(new VBox(12, head, grid));
        }
        pinMessage = null;
        javafx.application.Platform.runLater(() -> scroll.setVvalue(scrollPos)); // fijar no te sube arriba
        if (sections.isEmpty()) {
            Label none = new Label("Ningún logro con estos filtros.");
            none.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
            page.getChildren().add(none);
        } else {
            page.getChildren().addAll(sections);
            Motion.stagger(sections);
        }
    }

    // ── Cabecera ────────────────────────────────────────────────────

    private Node buildHeader() {
        int done = AchievementService.unlockedCount(), total = AchievementService.catalog().size();
        Label title = new Label("Logros");
        title.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 32px; -fx-text-fill: " + Palette.TEXT + ";");
        Label count = new Label(done + " de " + total + " conseguidos");
        count.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
        int pinned = AchievementService.pinnedCount();
        Label profile = new Label(pinned == 0
                ? "En tu perfil salen tus 3 logros más difíciles. Fija los que quieras con la chincheta."
                : "En tu perfil: " + pinned + " de " + AchievementService.MAX_PINNED + " fijados con la chincheta.");
        profile.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";");
        VBox titles = new VBox(4, title, count, bar(260, (double) done / total, Palette.BRAND, 6), profile);
        VBox.setMargin(profile, new Insets(4, 0, 0, 0));
        if (pinMessage != null) titles.getChildren().add(pinMessage);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button sound = new Button();
        sound.getStyleClass().add("btn-secondary-sm");
        Runnable paint = () -> sound.setText(AchievementService.soundEnabled() ? "Sonido al desbloquear: sí" : "Sonido al desbloquear: no");
        paint.run();
        sound.setOnAction(e -> {
            AchievementService.setSoundEnabled(!AchievementService.soundEnabled());
            paint.run();
        });

        HBox header = new HBox(16, titles, spacer, sound);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    // ── Racha y mapa de actividad ───────────────────────────────────

    private Node buildStreakCard() {
        int[] streaks = ActivityService.streaks();
        Map<LocalDate, Integer> byDay = ActivityService.liveCountsByDay();

        VBox numbers = new VBox(2,
                small("Racha actual"),
                big((streaks[0] == 1 ? "1 día" : streaks[0] + " días"), Palette.BRAND),
                small("Mejor racha: " + (streaks[1] == 1 ? "1 día" : streaks[1] + " días")));
        HBox flame = new HBox(14, Icons.of(Icons.FLAME, 30, streaks[0] > 0 ? Palette.BRAND : Palette.DIM), numbers);
        flame.setAlignment(Pos.CENTER_LEFT);

        // Mapa: columnas = semanas (de lunes a domingo), la última es la actual
        GridPane heat = new GridPane();
        heat.setHgap(3);
        heat.setVgap(3);
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(DayOfWeek.MONDAY).minusWeeks(WEEKS - 1);
        int activeDays = 0;
        for (int w = 0; w < WEEKS; w++) {
            for (int d = 0; d < 7; d++) {
                LocalDate day = start.plusWeeks(w).plusDays(d);
                Region cell = new Region();
                cell.setMinSize(11, 11);
                cell.setPrefSize(11, 11);
                if (day.isAfter(today)) {
                    cell.setStyle("-fx-background-color: transparent;");
                } else {
                    int n = byDay.getOrDefault(day, 0);
                    if (n > 0) activeDays++;
                    String color = n == 0 ? Palette.PANEL_3 : n <= 2 ? Palette.BRAND + "55" : n <= 6 ? Palette.BRAND + "99" : Palette.BRAND;
                    cell.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
                    Tooltip.install(cell, new Tooltip(DATE.format(day) + (n == 0 ? " · sin actividad"
                            : " · " + n + (n == 1 ? " acción" : " acciones"))));
                }
                heat.add(cell, w, d);
            }
        }
        Label heatTitle = small(activeDays + " días con actividad en las últimas " + WEEKS + " semanas");
        HBox legend = new HBox(4, small("Menos"), legendCell(Palette.PANEL_3), legendCell(Palette.BRAND + "55"),
                legendCell(Palette.BRAND + "99"), legendCell(Palette.BRAND), small("Más"));
        legend.setAlignment(Pos.CENTER_RIGHT);
        VBox heatBox = new VBox(8, heatTitle, heat, legend);
        heatBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox card = new HBox(24, flame, spacer, heatBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 16; -fx-border-color: " + Palette.LINE + "; -fx-border-radius: 16;");

        if (byDay.isEmpty()) {
            Label hint = small("Las rachas cuentan desde esta versión: cada día que marques algo, sumas.");
            VBox withHint = new VBox(10, card, hint);
            return withHint;
        }
        return card;
    }

    private Region legendCell(String color) {
        Region r = new Region();
        r.setMinSize(10, 10);
        r.setPrefSize(10, 10);
        r.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        return r;
    }

    // ── Filtros ─────────────────────────────────────────────────────

    private Node buildFilters() {
        FlowPane chips = new FlowPane(8, 8);
        for (int d = 0; d <= 5; d++) {
            final int level = d;
            String text = d == 0 ? "Todas" : AchievementService.DIFFICULTY[d];
            chips.getChildren().add(chip(text, difficultyFilter == d, d == 0 ? null : pips(d, difficultyFilter == d ? Palette.ON_ACCENT : Palette.BRAND), () -> {
                difficultyFilter = level;
                render();
            }));
        }
        HBox.setHgrow(chips, Priority.ALWAYS);

        HBox segmented = new HBox(4);
        segmented.setPadding(new Insets(4));
        segmented.setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 14; -fx-border-color: " + Palette.LINE + "; -fx-border-radius: 14;");
        for (Status s : Status.values()) {
            Button b = new Button(s == Status.ALL ? "Todos" : s == Status.DONE ? "Conseguidos" : "Pendientes");
            boolean active = statusFilter == s;
            b.setStyle("-fx-background-radius: 10; -fx-min-height: 34; -fx-padding: 0 14; -fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-cursor: hand;"
                    + (active ? "-fx-background-color: " + Palette.TEXT + "; -fx-text-fill: " + Palette.ON_ACCENT + ";"
                              : "-fx-background-color: transparent; -fx-text-fill: " + Palette.TEXT_MUTED + ";"));
            b.setOnAction(e -> {
                statusFilter = s;
                render();
            });
            segmented.getChildren().add(b);
        }
        // Dos filas: el selector de estado, compacto, y debajo las 6
        // dificultades en una sola línea. Antes iban juntos y el selector se
        // estiraba hasta el alto de los chips partidos en dos líneas.
        segmented.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        chips.setPrefWrapLength(PAGE_W);
        VBox filters = new VBox(12, segmented, chips);
        filters.setAlignment(Pos.TOP_LEFT);
        return filters;
    }

    private Node chip(String text, boolean active, Node graphic, Runnable action) {
        Button chip = new Button(text);
        if (graphic != null) {
            chip.setGraphic(graphic);
            chip.setGraphicTextGap(8);
        }
        chip.setStyle("-fx-background-radius: 999; -fx-border-radius: 999; -fx-min-height: 34; -fx-padding: 0 13;"
                + "-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-cursor: hand;"
                + (active ? "-fx-background-color: " + Palette.TEXT + "; -fx-text-fill: " + Palette.ON_ACCENT + "; -fx-border-color: " + Palette.TEXT + ";"
                          : "-fx-background-color: transparent; -fx-text-fill: " + Palette.TEXT_SOFT + "; -fx-border-color: " + Palette.LINE_STRONG + ";"));
        chip.setOnAction(e -> action.run());
        return chip;
    }

    /** De 1 a 5 puntitos: la dificultad. */
    private static HBox pips(int level, String color) {
        HBox box = new HBox(3);
        box.setAlignment(Pos.CENTER_LEFT);
        for (int i = 1; i <= 5; i++) {
            Region dot = new Region();
            dot.setMinSize(6, 6);
            dot.setPrefSize(6, 6);
            dot.setStyle("-fx-background-radius: 999; -fx-background-color: " + (i <= level ? color : color + "33") + ";");
            box.getChildren().add(dot);
        }
        return box;
    }

    // ── Tarjeta de logro ────────────────────────────────────────────

    private Node buildCard(Achievement a) {
        boolean done = AchievementService.isUnlocked(a);
        boolean hidden = a.secret() && !done;
        String accent = done ? a.accent() : Palette.DIM;

        StackPane icon = new StackPane(Icons.of(hidden ? Icons.LOCK : Icons.byName(a.icon()), 20, accent));
        icon.setMinSize(42, 42);
        icon.setMaxSize(42, 42);
        icon.setStyle("-fx-background-radius: 999; -fx-background-color: " + (done ? a.accent() + "33" : Palette.PANEL_3) + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox diff = new VBox(4, pips(a.difficulty(), done ? a.accent() : Palette.LINE_STRONG));
        Label diffName = new Label(AchievementService.DIFFICULTY[a.difficulty()]);
        diffName.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (done ? a.accent() : Palette.DIM) + ";");
        diff.getChildren().add(diffName);
        diff.setAlignment(Pos.TOP_RIGHT);
        HBox top = new HBox(icon, spacer, diff);
        top.setAlignment(Pos.TOP_LEFT);

        Label name = new Label(hidden ? "???" : AchievementService.displayName(a));
        name.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: "
                + (done ? Palette.TEXT : Palette.TEXT_SOFT) + ";");
        Label desc = new Label(hidden ? a.hint() : a.description());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";" + (hidden ? "-fx-font-style: italic;" : ""));

        VBox card = new VBox(8, top, name, desc);
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        card.getChildren().add(push);

        if (done && AchievementService.levelOf(a) > 0) {
            // Logro con niveles: barra hacia el siguiente año
            long value = AchievementService.progressOf(a), target = AchievementService.targetOf(a);
            long from = target - 365;
            Label nums = new Label(fmt(value) + " / " + fmt(target) + " días para el nivel " + (AchievementService.levelOf(a) + 1));
            nums.setStyle("-fx-font-size: 11px; -fx-text-fill: " + a.accent() + ";");
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox bottom = new HBox(6, nums, gap, pinButton(a));
            bottom.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(bar(CARD_W - 32, (double) Math.max(0, value - from) / 365, a.accent(), 4), bottom);
        } else if (done) {
            LocalDate on = AchievementService.unlockedOn(a);
            Label when = new Label(on != null ? "Conseguido el " + DATE.format(on) : "Conseguido");
            when.setStyle("-fx-font-size: 11px; -fx-text-fill: " + a.accent() + ";");
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox bottom = new HBox(6, when, gap, pinButton(a));
            bottom.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(bottom);
        } else if (!a.secret() && a.target() > 1) {
            long value = Math.min(AchievementService.progressOf(a), a.target());
            Label nums = new Label(fmt(value) + " / " + fmt(a.target()));
            nums.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.DIM + ";");
            card.getChildren().addAll(bar(CARD_W - 32, (double) value / a.target(), a.accent(), 4), nums);
        } else {
            Label pending = new Label(a.secret() ? "Secreto" : "Pendiente");
            pending.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.DIM + ";");
            card.getChildren().add(pending);
        }

        card.setPadding(new Insets(14, 16, 14, 16));
        card.setPrefWidth(CARD_W);
        card.setMinWidth(CARD_W);
        card.setMaxWidth(CARD_W);
        card.setMinHeight(176);
        card.setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: "
                + (done ? a.accent() + "88" : Palette.LINE) + ";");
        if (!done) card.setOpacity(0.85);
        return card;
    }

    /** Chincheta: fija o quita el logro de las medallas del perfil. */
    private Node pinButton(Achievement a) {
        boolean pinned = AchievementService.isPinned(a);
        Button pin = new Button();
        pin.setGraphic(pinned ? Icons.filled(Icons.PIN, 14, a.accent()) : Icons.of(Icons.PIN, 14, Palette.DIM));
        pin.setStyle("-fx-background-radius: 999; -fx-padding: 5; -fx-cursor: hand; -fx-background-color: "
                + (pinned ? a.accent() + "33" : "transparent") + ";");
        pin.setTooltip(new Tooltip(pinned ? "Quitar de tu perfil" : "Mostrar en tu perfil"));
        pin.setOnAction(e -> {
            if (!AchievementService.togglePin(a)) {
                pinMessage = new Label("Ya tienes " + AchievementService.MAX_PINNED
                        + " logros en tu perfil. Quita uno con su chincheta para poner este.");
                pinMessage.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.BRAND + ";");
            }
            render();
        });
        return pin;
    }

    // ── Ayudas ──────────────────────────────────────────────────────

    private static Node bar(double width, double fraction, String color, double h) {
        Region track = new Region();
        track.setPrefSize(width, h);
        track.setMaxSize(width, h);
        track.setStyle("-fx-background-color: " + Palette.LINE + "; -fx-background-radius: " + h / 2 + ";");
        Region fill = new Region();
        double w = width * Math.max(0, Math.min(1, fraction));
        fill.setPrefSize(w, h);
        fill.setMaxSize(w, h);
        fill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: " + h / 2 + ";");
        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setMaxWidth(width);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static Label small(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";");
        return l;
    }

    private static Label big(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 28px; -fx-text-fill: " + color + ";");
        return l;
    }

    private static String fmt(long n) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es")).format(n);
    }
}
