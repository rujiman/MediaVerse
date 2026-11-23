package com.rujiman.mediatracker.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * Pinta las notas de una versión (el Markdown sencillo del CHANGELOG) con
 * formato y dentro de un panel con scroll, para que se puedan leer enteras
 * aunque sean largas.
 *
 * Entiende lo que usa el CHANGELOG: "### Sección", "- punto", "> nota",
 * "**negrita**" y `código`. El resto se muestra como texto normal.
 */
public final class ReleaseNotesView {

    private ReleaseNotesView() {}

    /** Panel con scroll (alto máximo maxHeight) con las notas formateadas. */
    public static Node create(String markdown, double width, double maxHeight) {
        VBox content = new VBox(6);
        content.setPadding(new Insets(4, 14, 8, 2));
        for (String raw : markdown.replace("\r", "").split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.equals("---")) continue;

            if (line.startsWith("#")) {
                String title = line.replaceFirst("^#+\\s*", "");
                Text t = new Text(title);
                t.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-fill: " + Palette.BRAND + ";");
                TextFlow flow = new TextFlow(t);
                VBox.setMargin(flow, new Insets(content.getChildren().isEmpty() ? 0 : 10, 0, 2, 0));
                content.getChildren().add(flow);
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                Text bullet = new Text("•");
                bullet.setStyle("-fx-font-size: 13px; -fx-fill: " + Palette.DIM + ";");
                TextFlow flow = inline(line.substring(2), Palette.TEXT_SOFT, 13);
                HBox.setHgrow(flow, Priority.ALWAYS);
                HBox row = new HBox(8, new TextFlow(bullet), flow);
                content.getChildren().add(row);
            } else if (line.startsWith(">")) {
                TextFlow flow = inline(line.replaceFirst("^>\\s*", ""), Palette.DIM, 12.5);
                flow.setPadding(new Insets(6, 10, 6, 10));
                flow.setStyle("-fx-background-color: " + Palette.PANEL_3 + "; -fx-background-radius: 8;");
                content.getChildren().add(flow);
            } else {
                content.getChildren().add(inline(line, Palette.TEXT_SOFT, 13));
            }
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(width);
        scroll.setPrefViewportHeight(Math.min(maxHeight, estimateHeight(content, width)));
        scroll.setMaxHeight(maxHeight);
        scroll.getStyleClass().add("scroll-pane-transparent");
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    /** Alto aproximado, para que las notas cortas no dejen hueco vacío. */
    private static double estimateHeight(VBox content, double width) {
        content.setPrefWidth(width - 20);
        content.applyCss();
        double h = content.prefHeight(width - 20);
        return h > 0 ? h + 8 : 200;
    }

    /** Una línea con **negrita** y `código` en el color dado. */
    private static TextFlow inline(String text, String color, double size) {
        List<Text> parts = new ArrayList<>();
        String base = "-fx-font-size: " + size + "px; -fx-fill: " + color + ";";
        int i = 0;
        while (i < text.length()) {
            int bold = text.indexOf("**", i);
            int code = text.indexOf('`', i);
            int next = minPositive(bold, code);
            if (next < 0) {
                parts.add(styled(text.substring(i), base));
                break;
            }
            if (next > i) parts.add(styled(text.substring(i, next), base));
            if (next == bold) {
                int end = text.indexOf("**", bold + 2);
                if (end < 0) { parts.add(styled(text.substring(bold), base)); break; }
                parts.add(styled(text.substring(bold + 2, end),
                        "-fx-font-family: 'Inter SemiBold'; -fx-font-size: " + size + "px; -fx-fill: " + Palette.TEXT + ";"));
                i = end + 2;
            } else {
                int end = text.indexOf('`', code + 1);
                if (end < 0) { parts.add(styled(text.substring(code), base)); break; }
                parts.add(styled(text.substring(code + 1, end),
                        "-fx-font-family: 'Consolas', monospace; -fx-font-size: " + (size - 1) + "px; -fx-fill: " + Palette.TEXT_MUTED + ";"));
                i = end + 1;
            }
        }
        TextFlow flow = new TextFlow(parts.toArray(new Text[0]));
        flow.setLineSpacing(2);
        return flow;
    }

    private static Text styled(String s, String style) {
        Text t = new Text(s);
        t.setStyle(style);
        return t;
    }

    private static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }
}
