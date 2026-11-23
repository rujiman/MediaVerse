package com.rujiman.mediatracker.ui;

import com.rujiman.mediatracker.services.AchievementService;
import com.rujiman.mediatracker.services.AchievementService.Achievement;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Aviso de logro desbloqueado: una tarjeta abajo a la derecha que sube con
 * un fundido, suena (si está activado) y se va a los 5 segundos. Si llegan
 * varios, salen uno detrás de otro. Pinchar el aviso abre Logros.
 *
 * Vive en una capa encima de todo el contenido central que no captura
 * clics fuera de la tarjeta.
 */
public final class AchievementToasts {

    private static final Duration VISIBLE = Duration.seconds(5);

    private final StackPane layer = new StackPane();
    private final Runnable onOpen;
    private final Deque<Node> queue = new ArrayDeque<>();
    private boolean showing = false;
    private AudioClip chime;

    public AchievementToasts(StackPane host, Runnable onOpen) {
        this.onOpen = onOpen;
        layer.setPickOnBounds(false);
        layer.setAlignment(Pos.BOTTOM_RIGHT);
        layer.setPadding(new Insets(0, 24, 24, 0));
        host.getChildren().add(layer);
        try {
            var url = AchievementToasts.class.getResource("/com/rujiman/mediatracker/sounds/logro.wav");
            if (url != null) {
                chime = new AudioClip(url.toExternalForm());
                chime.setVolume(0.45);
            }
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo cargar el sonido de logros: " + e.getMessage());
        }
    }

    /** Pone en cola los logros recién conseguidos (o un resumen, la primera vez). */
    public void show(List<Achievement> fresh, boolean summary) {
        if (fresh.isEmpty()) return;
        if (summary && fresh.size() > 1) {
            queue.add(card(Palette.BRAND, Icons.TROPHY, "Logros desbloqueados",
                    "Has desbloqueado " + fresh.size() + " logros",
                    "Con todo lo que ya tenías. Míralos en Logros."));
        } else {
            for (Achievement a : fresh) {
                queue.add(card(a.accent(), Icons.byName(a.icon()),
                        "Logro desbloqueado · " + AchievementService.DIFFICULTY[a.difficulty()],
                        a.name(), a.description()));
            }
        }
        // El layer tiene que seguir encima de todo (pueden haberse añadido overlays)
        layer.toFront();
        playNext();
    }

    private void playNext() {
        if (showing || queue.isEmpty()) return;
        showing = true;
        Node card = queue.poll();
        layer.getChildren().setAll(card);
        if (chime != null && AchievementService.soundEnabled()) chime.play();

        card.setOpacity(0);
        card.setTranslateY(24);
        FadeTransition in = new FadeTransition(Motion.SLOW, card);
        in.setToValue(1);
        TranslateTransition up = new TranslateTransition(Motion.SLOW, card);
        up.setToY(0);
        up.setInterpolator(Motion.EASE_OUT);
        in.setInterpolator(Motion.EASE_OUT);

        PauseTransition hold = new PauseTransition(VISIBLE);
        hold.setOnFinished(e -> hide(card));
        ParallelTransition enter = new ParallelTransition(in, up);
        enter.setOnFinished(e -> hold.play());
        enter.play();

        card.setOnMouseClicked(e -> {
            hold.stop();
            queue.clear();
            hide(card);
            if (onOpen != null) onOpen.run();
        });
    }

    private void hide(Node card) {
        FadeTransition out = new FadeTransition(Motion.BASE, card);
        out.setToValue(0);
        TranslateTransition down = new TranslateTransition(Motion.BASE, card);
        down.setToY(12);
        ParallelTransition exit = new ParallelTransition(out, down);
        exit.setOnFinished(e -> {
            layer.getChildren().remove(card);
            showing = false;
            playNext();
        });
        exit.play();
    }

    private Node card(String accent, String iconPath, String kicker, String title, String body) {
        StackPane badge = new StackPane(Icons.of(iconPath, 22, accent));
        badge.setMinSize(46, 46);
        badge.setMaxSize(46, 46);
        badge.setStyle("-fx-background-color: " + accent + "33; -fx-background-radius: 999;");

        Label k = new Label(kicker);
        k.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.DIM + ";");
        Label t = new Label(title);
        t.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: " + Palette.TEXT + ";");
        Label b = new Label(body);
        b.setWrapText(true);
        b.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.TEXT_MUTED + ";");
        VBox text = new VBox(2, k, t, b);
        text.setMaxWidth(250);

        HBox card = new HBox(14, badge, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 18, 14, 14));
        card.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: " + Palette.PANEL_ALT + "; -fx-background-radius: 16;"
                + "-fx-border-color: " + accent + "88; -fx-border-radius: 16; -fx-cursor: hand;"
                + "-fx-effect: dropshadow(gaussian, #000000aa, 24, 0.2, 0, 6);");
        return card;
    }
}
