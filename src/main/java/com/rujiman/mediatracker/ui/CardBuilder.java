package com.rujiman.mediatracker.ui;

import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.util.function.Consumer;

/**
 * Fábrica de tarjetas (cards) reutilizable para SearchView y HomeView.
 *
 * Antes existían métodos buildCard() duplicados en SearchController y
 * HomeController con el mismo HTML/FXML manual. Ahora ambos controladores
 * llaman aquí y la lógica de presentación vive en un único sitio.
 *
 * Uso básico:
 *   VBox card = CardBuilder.build(item, onClick);
 *
 * Uso con callback de selección (HomeController dashboard editor):
 *   VBox card = CardBuilder.buildSelectable(item, isSelected, onToggle);
 */
public final class CardBuilder {

    // Dimensiones estándar de tarjeta
    public static final double CARD_WIDTH   = 140;
    public static final double CARD_HEIGHT  = 230;
    public static final double IMAGE_HEIGHT = 160;

    private CardBuilder() {}

    // ─────────────────────────────────────────────────────────────────
    // Tarjeta normal (búsqueda / favoritos / plan)
    // ─────────────────────────────────────────────────────────────────

    public static VBox build(MediaItem item, Consumer<MediaItem> onClick) {
        VBox card = baseCard(item);
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> { if (onClick != null) onClick.accept(item); });
        return card;
    }

    // ─────────────────────────────────────────────────────────────────
    // Tarjeta seleccionable (editor del dashboard en HomeController)
    // ─────────────────────────────────────────────────────────────────

    public static VBox buildSelectable(MediaItem item,
                                       boolean selected,
                                       Consumer<MediaItem> onToggle) {
        VBox card = baseCard(item);
        card.setCursor(Cursor.HAND);

        // Overlay de selección
        StackPane overlay = new StackPane();
        overlay.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        String accent = MediaTypeTheme.accentHex(item.getType());
        overlay.setStyle(selected
            ? "-fx-background-color: " + accent + "33; -fx-border-color: " + accent
              + "; -fx-border-width: 2; -fx-border-radius: 10;"
            : "-fx-background-color: transparent;");
        overlay.setMouseTransparent(true);

        StackPane wrapper = new StackPane(card, overlay);
        wrapper.setCursor(Cursor.HAND);
        wrapper.setOnMouseClicked(e -> { if (onToggle != null) onToggle.accept(item); });

        // Devolvemos el wrapper pero lo empaquetamos en VBox para mantener
        // el mismo tipo de retorno y no romper el API
        VBox container = new VBox(wrapper);
        container.setMaxWidth(CARD_WIDTH);
        return container;
    }

    // ─────────────────────────────────────────────────────────────────
    // Construcción base
    // ─────────────────────────────────────────────────────────────────

    private static VBox baseCard(MediaItem item) {
        VBox card = new VBox(6);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setPadding(new Insets(8));
        card.setStyle(
            "-fx-background-color: #1a1a2e; " +
            "-fx-background-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2);"
        );
        card.getStyleClass().add("media-card");

        // ── Imagen ────────────────────────────────────────────────────
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(CARD_WIDTH - 16, IMAGE_HEIGHT);
        imageContainer.setMaxSize(CARD_WIDTH - 16, IMAGE_HEIGHT);

        Rectangle clip = new Rectangle(CARD_WIDTH - 16, IMAGE_HEIGHT);
        clip.setArcWidth(8); clip.setArcHeight(8);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(CARD_WIDTH - 16);
        imageView.setFitHeight(IMAGE_HEIGHT);
        imageView.setPreserveRatio(false);
        imageView.setClip(clip);

        String url = item.getImageUrl();
        if (url != null && !url.isBlank()) {
            try {
                imageView.setImage(new Image(url, true));
            } catch (Exception ignored) {}
        }

        // Badge de tipo
        MediaType type = item.getType();
        String accent = MediaTypeTheme.accentHex(type);
        Label badge = new Label(MediaTypeTheme.label(type));
        badge.setStyle(
            "-fx-background-color: " + accent + "cc; " +
            "-fx-text-fill: #0d0d1a; " +
            "-fx-font-size: 9px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 2px 6px; " +
            "-fx-background-radius: 4;"
        );
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(4, 0, 0, 4));

        imageContainer.getChildren().addAll(imageView, badge);

        // ── Título ────────────────────────────────────────────────────
        Label titleLabel = new Label(item.getTitle() != null ? item.getTitle() : "Sin título");
        titleLabel.setStyle(
            "-fx-text-fill: #e2e8f0; " +
            "-fx-font-size: 11px; " +
            "-fx-font-weight: bold;"
        );
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(CARD_WIDTH - 16);

        // ── Año / score ───────────────────────────────────────────────
        HBox meta = new HBox(4);
        meta.setAlignment(Pos.CENTER_LEFT);
        if (item.getYear() != null) {
            Label yearLabel = new Label(String.valueOf(item.getYear()));
            yearLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 10px;");
            meta.getChildren().add(yearLabel);
        }
        if (item.getScore() != null) {
            Label scoreLabel = new Label("★ " + item.getScore());
            scoreLabel.setStyle(
                "-fx-text-fill: " + accent + "; -fx-font-size: 10px; -fx-font-weight: bold;"
            );
            meta.getChildren().add(scoreLabel);
        }

        card.getChildren().addAll(imageContainer, titleLabel);
        if (!meta.getChildren().isEmpty()) card.getChildren().add(meta);

        // ── Hover ─────────────────────────────────────────────────────
        String hoverClass = MediaTypeTheme.cardHoverClass(type);
        card.setOnMouseEntered(e -> {
            card.setStyle(
                "-fx-background-color: #252545; " +
                "-fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, " + accent + "66, 12, 0, 0, 0);"
            );
        });
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: #1a1a2e; " +
            "-fx-background-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2);"
        ));

        return card;
    }
}
