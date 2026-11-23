package com.rujiman.mediatracker.ui;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

/**
 * Piezas visuales compartidas por las pantallas de colección (Favoritos,
 * Mi biblioteca...): portadas recortadas sin deformar, barras de progreso
 * y diálogos de texto con la paleta oscura.
 */
public final class CollectionUI {

    private CollectionUI() {}

    /**
     * Portada w×h con esquinas superiores redondeadas (o todas, si
     * allCorners). Se carga en segundo plano y aparece con un fundido;
     * mientras tanto se ve un rectángulo tintado del color del tipo.
     */
    public static StackPane poster(String url, double w, double h, String accent, double radius, boolean allCorners) {
        StackPane box = new StackPane();
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        String r = allCorners ? String.valueOf(radius) : radius + " " + radius + " 0 0";
        box.setStyle("-fx-background-color: " + accent + "22; -fx-background-radius: " + r + ";");
        if (url == null || url.isBlank()) return box;

        ImageView img = new ImageView();
        img.setFitWidth(w);
        img.setFitHeight(h);
        img.setPreserveRatio(false);
        img.setSmooth(true);
        Rectangle clip = new Rectangle(w, allCorners ? h : h + radius);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        img.setClip(clip);
        try {
            Image image = new Image(url, w * 2, h * 2, true, true, true);
            img.setImage(image);
            img.setOpacity(0);
            Runnable ready = () -> {
                cropToFill(img, image, w, h);
                javafx.animation.FadeTransition f = new javafx.animation.FadeTransition(Motion.BASE, img);
                f.setToValue(1);
                f.setInterpolator(Motion.EASE_OUT);
                f.play();
            };
            if (image.getProgress() >= 1.0) ready.run();
            else image.progressProperty().addListener((o, a, b) -> {
                if (b.doubleValue() >= 1.0 && !image.isError()) ready.run();
            });
        } catch (Exception ignored) {}
        box.getChildren().add(img);
        return box;
    }

    /**
     * Como cropToFill pero esperando a que la imagen termine de cargar.
     * En imágenes verticales centra un poco más arriba (las caras suelen
     * estar arriba). onReady (opcional) se ejecuta al aplicarlo.
     */
    public static void cropWhenReady(ImageView view, Image img, double w, double h, Runnable onReady) {
        Runnable apply = () -> {
            double iw = img.getWidth(), ih = img.getHeight();
            if (iw <= 0 || ih <= 0) return;
            double target = w / h;
            double cw = iw, ch = iw / target;
            if (ch > ih) { ch = ih; cw = ih * target; }
            double y = (ih - ch) * (ih > iw ? 0.3 : 0.5);
            view.setViewport(new Rectangle2D((iw - cw) / 2, y, cw, ch));
            if (onReady != null) onReady.run();
        };
        if (img.getProgress() >= 1.0 && !img.isError()) apply.run();
        else img.progressProperty().addListener((o, a, b) -> {
            if (b.doubleValue() >= 1.0 && !img.isError()) apply.run();
        });
    }

    /**
     * Fondo oscuro para el "viewport" interno de un ScrollPane, que no
     * hereda el fondo por CSS y deja un hueco gris. Se aplica en cuanto
     * existe el Skin del ScrollPane.
     */
    public static void darkViewport(javafx.scene.control.ScrollPane scroll) {
        Runnable apply = () -> {
            Node viewport = scroll.lookup(".viewport");
            if (viewport != null) viewport.setStyle("-fx-background-color: " + Palette.BG + ";");
        };
        scroll.skinProperty().addListener((o, a, b) -> { if (b != null) apply.run(); });
        if (scroll.getSkin() != null) apply.run();
    }

    /** Recorta al centro para rellenar w×h sin deformar (object-fit: cover). */
    public static void cropToFill(ImageView view, Image img, double w, double h) {
        double iw = img.getWidth(), ih = img.getHeight();
        if (iw <= 0 || ih <= 0) return;
        double target = w / h;
        double cw = iw, ch = iw / target;
        if (ch > ih) { ch = ih; cw = ih * target; }
        view.setViewport(new Rectangle2D((iw - cw) / 2, (ih - ch) / 2, cw, ch));
    }

    /**
     * Líneas de estado de una tarjeta de colección (Favoritos y Mi
     * biblioteca usan EXACTAMENTE esta función, así no pueden volver a
     * mostrar cosas distintas para el mismo título). Por prioridad:
     *
     *  1. A medias por episodios/capítulos → barra + "Episodio 3 de 12"
     *  2. Terminado                        → "✔ Visto / Jugado / Leído..." (color del tipo)
     *  3. "Estoy jugando/viendo/leyendo"   → "▶ Jugando ahora..." (color del tipo)
     *  4. Si no                            → fallbackText (p. ej. "Pienso verlo" o el año)
     *  + Tu nota, si la hay, en estrellas del color del tipo.
     *
     * @param progress progreso del título (puede ser null)
     * @param total    nº total de episodios/capítulos (null si no aplica)
     * @param rating   tu nota 1–5 (null o 0 si no la hay)
     */
    public static java.util.List<Node> statusLines(com.rujiman.mediatracker.models.MediaType type,
                                                  com.rujiman.mediatracker.services.WatchProgressService.Progress progress,
                                                  Integer total, Integer rating, String fallbackText, double width) {
        return statusLines(type, progress, total, rating, false, fallbackText, width);
    }

    /** Igual, y si el título tiene reseña añade un lápiz junto a las estrellas. */
    public static java.util.List<Node> statusLines(com.rujiman.mediatracker.models.MediaType type,
                                                  com.rujiman.mediatracker.services.WatchProgressService.Progress progress,
                                                  Integer total, Integer rating, boolean hasReview,
                                                  String fallbackText, double width) {
        String accent = com.rujiman.mediatracker.util.MediaTypeTheme.accentHex(type);
        String dim = "#a59fc0";
        java.util.List<Node> lines = new java.util.ArrayList<>();
        boolean viewed = progress != null && progress.viewed;
        boolean byUnits = progress != null && !viewed && total != null && total > 0 && !progress.watchedEpisodes.isEmpty();

        if (byUnits) {
            int done = progress.watchedEpisodes.size();
            lines.add(progressBar(width, (double) done / total, accent));
            // Series con temporadas: "Siguiente: T2 · E4"
            String seasonText = nextBySeason(progress, total);
            lines.add(meta(seasonText != null ? seasonText
                    : com.rujiman.mediatracker.util.MediaTypeTheme.unitSingular(type) + " " + done + " de " + total, dim));
        } else if (viewed) {
            lines.add(meta(com.rujiman.mediatracker.util.MediaTypeTheme.doneText(type), accent));
        } else if (progress != null && progress.inProgress) {
            lines.add(meta("▶ " + com.rujiman.mediatracker.util.MediaTypeTheme.inProgressLabel(type), accent));
        } else {
            lines.add(meta(fallbackText != null && !fallbackText.isBlank() ? fallbackText : " ", dim));
        }

        boolean rated = rating != null && rating > 0;
        if (rated || hasReview) {
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            if (rated) row.getChildren().add(meta("★".repeat(rating) + "☆".repeat(Math.max(0, 5 - rating)), accent));
            if (hasReview) {
                Node pencil = Icons.of(Icons.EDIT, 13, rated ? dim : accent);
                javafx.scene.control.Tooltip.install(pencil, new javafx.scene.control.Tooltip("Tiene reseña"));
                row.getChildren().add(pencil);
                if (!rated) row.getChildren().add(meta("Con reseña", dim));
            }
            lines.add(row);
        }
        return lines;
    }

    /**
     * "Siguiente: T2 · E4" si la serie tiene temporadas guardadas y le queda
     * algo por ver; null en cualquier otro caso.
     */
    public static String nextBySeason(com.rujiman.mediatracker.services.WatchProgressService.Progress progress, int total) {
        return nextBySeason(progress != null ? progress.item : null, progress, total);
    }

    /** Igual, pero con la ficha del título (por si el progreso aún no la guarda). */
    public static String nextBySeason(com.rujiman.mediatracker.models.MediaItem item,
                                      com.rujiman.mediatracker.services.WatchProgressService.Progress progress, int total) {
        if (item == null && progress != null) item = progress.item;
        if (progress == null || item == null || item.getTmdbId() == null
                || item.getType() != com.rujiman.mediatracker.models.MediaType.SERIES) return null;
        com.rujiman.mediatracker.services.SeasonService.Layout layout =
                com.rujiman.mediatracker.services.SeasonService.layout(item.getTmdbId());
        if (layout == null || layout.total() != total) return null;
        for (int i = 1; i <= total; i++) {
            if (!progress.watchedEpisodes.contains(i)) return "Siguiente: " + layout.label(i);
        }
        return null;
    }

    private static javafx.scene.control.Label meta(String text, String color) {
        javafx.scene.control.Label l = new javafx.scene.control.Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + ";");
        return l;
    }

    /** Barra de progreso fina de ancho fijo. */
    public static Node progressBar(double width, double fraction, String color) {
        Region track = new Region();
        track.setPrefSize(width, 4);
        track.setMaxSize(width, 4);
        track.setStyle("-fx-background-color: #2a2347; -fx-background-radius: 2;");
        Region fill = new Region();
        double w = width * Math.max(0, Math.min(1.0, fraction));
        fill.setPrefSize(w, 4);
        fill.setMaxSize(w, 4);
        fill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");
        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setMaxWidth(width);
        return bar;
    }

    /** Diálogo de texto (nueva carpeta, renombrar...) con la paleta oscura y ventana padre. */
    public static TextInputDialog textDialog(Window owner, String title, String value) {
        TextInputDialog d = new TextInputDialog(value);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText("Nombre:");
        if (owner != null) d.initOwner(owner);
        DialogPane pane = d.getDialogPane();
        pane.getStylesheets().add(CollectionUI.class.getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm());
        pane.setStyle("-fx-background-color: #1c1730;");
        pane.lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: #f0eef5;"));
        pane.sceneProperty().addListener((o, a, s) -> {
            if (s != null) s.setFill(javafx.scene.paint.Color.web("#100c1c"));
        });
        return d;
    }
}
