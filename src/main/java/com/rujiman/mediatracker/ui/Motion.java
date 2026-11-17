package com.rujiman.mediatracker.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.util.List;

/**
 * Sistema de movimiento de MediaVerse: todas las animaciones de la app
 * salen de aquí, igual que todos los colores salen de MediaTypeTheme.
 *
 * Inspiración "estilo Apple":
 * - Curvas ease-out que arrancan rápido y frenan suave (nada lineal).
 * - Duraciones cortas (140–380 ms): la animación acompaña, no hace esperar.
 * - Movimientos pequeños: 1–4 % de escala, 8–16 px de desplazamiento.
 * - Un "muelle" con un mínimo rebote para lo que el usuario pulsa.
 *
 * Uso típico:
 *   Motion.install(scene);          // una vez por Scene: hover y pulsación globales
 *   Motion.enter(vista);            // al mostrar una pantalla nueva
 *   Motion.stagger(grid.getChildren());  // tarjetas apareciendo en cascada
 */
public final class Motion {

    private Motion() {}

    // ── Tiempos ────────────────────────────────────────────────────────

    public static final Duration FAST = Duration.millis(140);
    public static final Duration BASE = Duration.millis(240);
    public static final Duration SLOW = Duration.millis(380);

    // ── Curvas ─────────────────────────────────────────────────────────

    /** Ease-out pronunciado (tipo iOS): rápido al principio, frenada larga y suave. */
    public static final Interpolator EASE_OUT = Interpolator.SPLINE(0.16, 1, 0.3, 1);

    /** Ease-in-out suave, para salidas y cambios que no responden a un clic. */
    public static final Interpolator EASE_IN_OUT = Interpolator.SPLINE(0.65, 0, 0.35, 1);

    /**
     * Muelle amortiguado con un pequeño rebote final. JavaFX no permite
     * curvas SPLINE que se salgan de [0,1], así que el rebote se calcula
     * a mano: una oscilación que se apaga exponencialmente, normalizada
     * para terminar exactamente en 1.
     */
    public static final Interpolator SPRING = new Interpolator() {
        private final double end = raw(1.0);
        private double raw(double t) { return 1 - Math.exp(-6.5 * t) * Math.cos(10 * t); }
        @Override protected double curve(double t) { return raw(t) / end; }
    };

    // ── Entradas y salidas ─────────────────────────────────────────────

    /** Aparece subiendo un poco y enfocándose: para pantallas y paneles nuevos. */
    public static Animation enter(Node node) {
        return enter(node, Duration.ZERO);
    }

    public static Animation enter(Node node, Duration delay) {
        node.setOpacity(0);
        node.setTranslateY(14);
        node.setScaleX(0.985);
        node.setScaleY(0.985);

        FadeTransition fade = new FadeTransition(SLOW, node);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(SLOW, node);
        rise.setToY(0);
        ScaleTransition focus = new ScaleTransition(SLOW, node);
        focus.setToX(1);
        focus.setToY(1);

        ParallelTransition all = new ParallelTransition(node, fade, rise, focus);
        all.getChildren().forEach(a -> ((Transition) a).setInterpolator(EASE_OUT));
        all.setDelay(delay);
        all.play();
        return all;
    }

    /** Desaparece hundiéndose un poco; onDone se ejecuta al terminar. */
    public static Animation exit(Node node, Runnable onDone) {
        FadeTransition fade = new FadeTransition(FAST, node);
        fade.setToValue(0);
        ScaleTransition shrink = new ScaleTransition(FAST, node);
        shrink.setToX(0.985);
        shrink.setToY(0.985);

        ParallelTransition all = new ParallelTransition(node, fade, shrink);
        all.getChildren().forEach(a -> ((Transition) a).setInterpolator(EASE_IN_OUT));
        all.setOnFinished(e -> { if (onDone != null) onDone.run(); });
        all.play();
        return all;
    }

    /** Menús emergentes (perfil, diálogos propios): crecen desde 94 % con muelle. */
    public static Animation popIn(Node node) {
        node.setOpacity(0);
        node.setScaleX(0.94);
        node.setScaleY(0.94);

        FadeTransition fade = new FadeTransition(FAST, node);
        fade.setToValue(1);
        fade.setInterpolator(EASE_OUT);
        ScaleTransition grow = new ScaleTransition(BASE, node);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(SPRING);

        ParallelTransition all = new ParallelTransition(node, fade, grow);
        all.play();
        return all;
    }

    /**
     * Cascada: cada nodo entra un poco después que el anterior. Solo se
     * retrasan los 12 primeros (el resto entra con ellos), para que una
     * lista larga no tarde segundos en aparecer.
     */
    public static void stagger(List<? extends Node> nodes) {
        stagger(nodes, Duration.millis(28));
    }

    public static void stagger(List<? extends Node> nodes, Duration step) {
        for (int i = 0; i < nodes.size(); i++) {
            enter(nodes.get(i), step.multiply(Math.min(i, 12)));
        }
    }

    // ── Hover y pulsación ──────────────────────────────────────────────

    private static final String ANIM_KEY = "motion.anim";

    /** Eleva (o devuelve a su sitio) una tarjeta al pasar el ratón. */
    public static void lift(Node node, boolean up) {
        stopRunning(node);
        node.setViewOrder(up ? -1 : 0); // por encima de sus vecinas sin tocar el layout

        ScaleTransition scale = new ScaleTransition(BASE, node);
        scale.setToX(up ? 1.035 : 1);
        scale.setToY(up ? 1.035 : 1);
        TranslateTransition move = new TranslateTransition(BASE, node);
        move.setToY(up ? -4 : 0);

        ParallelTransition all = new ParallelTransition(node, scale, move);
        all.getChildren().forEach(a -> ((Transition) a).setInterpolator(EASE_OUT));
        node.getProperties().put(ANIM_KEY, all);
        all.play();
    }

    /** Pequeño "hundimiento" al pulsar y rebote al soltar. */
    public static void press(Node node, boolean down) {
        stopRunning(node);
        ScaleTransition scale = new ScaleTransition(down ? FAST : BASE, node);
        double target = down ? 0.96 : 1;
        scale.setToX(target);
        scale.setToY(target);
        scale.setInterpolator(down ? EASE_OUT : SPRING);
        node.getProperties().put(ANIM_KEY, scale);
        scale.play();
    }

    private static void stopRunning(Node node) {
        Object running = node.getProperties().get(ANIM_KEY);
        if (running instanceof Animation a) a.stop();
    }

    /**
     * Activa el movimiento global de una Scene, sin tocar cada controlador:
     * - Toda tarjeta con la clase CSS "card-base" (o "motion-lift") se
     *   eleva al pasar el ratón.
     * - Todo botón (Button, ToggleButton...) se hunde un poco al pulsarlo.
     * Se llama una vez por cada Scene que crea la app.
     */
    public static void install(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, e -> {
            if (e.getTarget() instanceof Node n && isLiftable(n)) lift(n, true);
        });
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, e -> {
            if (e.getTarget() instanceof Node n && isLiftable(n)) lift(n, false);
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Node b = buttonAncestor(e.getPickResult().getIntersectedNode());
            if (b != null) press(b, true);
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            Node b = buttonAncestor(e.getPickResult().getIntersectedNode());
            if (b != null) press(b, false);
        });
    }

    private static boolean isLiftable(Node n) {
        return n.getStyleClass().contains("card-base") || n.getStyleClass().contains("motion-lift");
    }

    private static Node buttonAncestor(Node n) {
        int depth = 0;
        while (n != null && depth++ < 6) {
            if (n instanceof ButtonBase) return n;
            Parent p = n.getParent();
            n = p;
        }
        return null;
    }
}
