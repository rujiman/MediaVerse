package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.services.FavoritesService;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

/**
 * Controller para mostrar detalles de un MediaItem
 */
public class DetailViewController {

    @FXML private ScrollPane detailScroll;
    @FXML private VBox detailContainer;
    @FXML private ImageView detailImage;
    @FXML private Label detailTitle;
    @FXML private Label detailYear;
    @FXML private Label detailScore;
    @FXML private Label detailDescription;
    @FXML private Label detailGenres;
    @FXML private Label detailPlatforms;
    @FXML private Button viewedButton;
    @FXML private Button favoriteButton;
    @FXML private Button openExternalButton;

    private MediaItem currentItem;
    private FavoriteItem currentFavorite;

    private static final String COLOR_PRIMARY = "#e94560";
    private static final String COLOR_BG_CARD = "#1a1a2e";
    private static final String COLOR_TEXT = "#eaeaea";
    private static final String COLOR_TEXT_DIM = "#555577";
    private static final String COLOR_GREEN = "#2ecc71";

    @FXML
    public void initialize() {
        detailScroll.setStyle("-fx-background-color: #0f0f1a;");
        detailContainer.setStyle("-fx-background-color: #0f0f1a;");
    }

    /**
     * Cargar detalles de un MediaItem
     */
    public void loadItem(MediaItem item) {
        this.currentItem = item;

        // Cargar imagen con fade
        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            new Thread(() -> {
                try {
                    Image img = new Image(item.getImageUrl(), 400, 600, true, true, true);
                    Platform.runLater(() -> {
                        detailImage.setImage(img);
                        FadeTransition fade = new FadeTransition(Duration.millis(300), detailImage);
                        fade.setFromValue(0);
                        fade.setToValue(1);
                        fade.play();
                    });
                } catch (Exception ignored) {}
            }).start();
        }

        // Información básica
        detailTitle.setText(item.getTitle());
        detailYear.setText(item.getYear() != null ? String.valueOf(item.getYear()) : "—");

        // Puntuación
        int score = item.getScore() != null ? item.getScore() : 0;
        detailScore.setText(score > 0 ? "⭐ " + score + "/100" : "Sin puntuación");

        // Descripción
        detailDescription.setText(item.getDescription() != null ? item.getDescription() : "Sin descripción");

        // Géneros
        if (item.getGenres() != null && !item.getGenres().isEmpty()) {
            detailGenres.setText("Géneros: " + String.join(", ", item.getGenres()));
            detailGenres.setVisible(true);
        } else {
            detailGenres.setVisible(false);
        }

        // Plataformas
        if (item.getPlatforms() != null && !item.getPlatforms().isEmpty()) {
            detailPlatforms.setText("Plataformas: " + String.join(", ", item.getPlatforms()));
            detailPlatforms.setVisible(true);
        } else {
            detailPlatforms.setVisible(false);
        }

        // Botones según tipo
        updateButtons(item);

        // Verificar si está en favoritos
        checkIfFavorite();
    }

    /**
     * Actualizar botones según el tipo de media
     */
    private void updateButtons(MediaItem item) {
        String viewedLabel = "";
        switch (item.getType()) {
            case ANIME, SERIES -> viewedLabel = "Marcar como visto";
            case MOVIE -> viewedLabel = "Marcar como visto";
            case MUSIC -> viewedLabel = "Marcar como escuchado";
            case GAME -> viewedLabel = "Marcar como jugado";
        }
        viewedButton.setText(viewedLabel);
    }

    /**
     * Verificar si el item está en favoritos
     */
    private void checkIfFavorite() {
        boolean isFav = FavoritesService.isFavorite(currentItem.getTitle());
        if (isFav) {
            // Buscar el favorito para saber su estado
            List<FavoriteItem> favs = FavoritesService.getFavorites();
            for (FavoriteItem fav : favs) {
                if (fav.getTitle().equalsIgnoreCase(currentItem.getTitle())) {
                    currentFavorite = fav;
                    break;
                }
            }
            favoriteButton.setText("❌ Eliminar de favoritos");
            favoriteButton.setStyle("-fx-background-color: #e74c3c;");
        } else {
            favoriteButton.setText("⭐ Agregar a favoritos");
            favoriteButton.setStyle("-fx-background-color: " + COLOR_PRIMARY + ";");
        }
    }

    @FXML
    private void onToggleFavorite() {
        if (currentFavorite != null) {
            // Eliminar
            FavoritesService.removeFavorite(currentFavorite.getId());
            currentFavorite = null;
        } else {
            // Agregar
            FavoriteItem fav = new FavoriteItem(currentItem);
            FavoritesService.addFavorite(fav);
            currentFavorite = fav;
        }
        checkIfFavorite();
    }

    @FXML
    private void onToggleViewed() {
        if (currentFavorite != null) {
            FavoritesService.toggleViewed(currentFavorite.getId());
            currentFavorite.setViewed(!currentFavorite.isViewed());
            updateViewedButtonStyle();
        } else {
            // Primero agregarlo a favoritos
            FavoriteItem fav = new FavoriteItem(currentItem);
            fav.setViewed(true);
            FavoritesService.addFavorite(fav);
            currentFavorite = fav;
            checkIfFavorite();
            updateViewedButtonStyle();
        }
    }

    private void updateViewedButtonStyle() {
        if (currentFavorite != null && currentFavorite.isViewed()) {
            viewedButton.setStyle("-fx-background-color: " + COLOR_GREEN + ";");
        } else {
            viewedButton.setStyle("-fx-background-color: " + COLOR_BG_CARD + ";");
        }
    }

    @FXML
    private void onOpenExternal() {
        if (currentItem.getExternalUrl() != null && !currentItem.getExternalUrl().isBlank()) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(currentItem.getExternalUrl()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}