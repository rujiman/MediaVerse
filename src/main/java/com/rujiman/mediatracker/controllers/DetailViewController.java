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
    @FXML private Button backButton;

    // Episodios (ANIME / SERIES)
    @FXML private Separator episodesSeparator;
    @FXML private VBox episodesSection;
    @FXML private Label episodesHeaderLabel;
    @FXML private VBox episodesList;

    // Canciones de álbum (MUSIC)
    @FXML private VBox trackCountSection;
    @FXML private Label trackCountLabel;

    private MediaItem currentItem;
    private FavoriteItem currentFavorite;

    /** Acción a ejecutar cuando se pulsa "Volver" (la define quien abre este detalle). */
    private Runnable onBackAction;

    public void setOnBackAction(Runnable onBackAction) {
        this.onBackAction = onBackAction;
    }

    @FXML
    private void onBack() {
        if (onBackAction != null) {
            onBackAction.run();
        }
    }

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

        // Verificar si está en favoritos (debe ir antes de construir la lista
        // de episodios, para que los checkboxes reflejen el progreso guardado)
        checkIfFavorite();

        // Episodios / canciones según el tipo de contenido
        setupEpisodesOrTracks(item);
    }

    /**
     * Configura la sección inferior según el tipo de contenido:
     * - ANIME/SERIES: lista de episodios marcables individualmente
     * - MOVIE/GAME: nada (el botón "Marcar como visto/jugado" ya cubre esto)
     * - MUSIC (álbum, con varias canciones): solo el total, sin desglose
     * - MUSIC (canción suelta): nada especial, es ella misma
     */
    private void setupEpisodesOrTracks(MediaItem item) {
        // Ocultar todo por defecto, se activa solo lo que aplique
        episodesSeparator.setVisible(false);
        episodesSeparator.setManaged(false);
        episodesSection.setVisible(false);
        episodesSection.setManaged(false);
        trackCountSection.setVisible(false);
        trackCountSection.setManaged(false);

        MediaType type = item.getType();

        if (type == MediaType.ANIME || type == MediaType.SERIES) {
            Integer total = item.getEpisodes();
            if (total != null && total > 0) {
                buildEpisodesList(total);
                episodesSeparator.setVisible(true);
                episodesSeparator.setManaged(true);
                episodesSection.setVisible(true);
                episodesSection.setManaged(true);
            }
            // Si no hay número de episodios disponible, no mostramos nada
            // (mejor que mostrar una lista vacía o incorrecta)

        } else if (type == MediaType.MUSIC) {
            // "episodes" se reutiliza en MusicService para nb_tracks (canciones del álbum)
            Integer trackCount = item.getEpisodes();
            if (trackCount != null && trackCount > 1) {
                // Es un álbum con varias canciones: mostramos solo el total
                trackCountLabel.setText("🎵 " + trackCount + " canciones");
                trackCountSection.setVisible(true);
                trackCountSection.setManaged(true);
            }
            // Si es 1 canción suelta o no hay dato, no mostramos nada extra
        }
        // MOVIE y GAME: no se muestra nada adicional aquí
    }

    /**
     * Construye la lista de checkboxes "Episodio 1".."Episodio N",
     * marcando como visto cada uno según lo guardado en favoritos.
     */
    private void buildEpisodesList(int totalEpisodes) {
        episodesList.getChildren().clear();
        episodesHeaderLabel.setText("Episodios (" + watchedCount() + "/" + totalEpisodes + ")");

        for (int i = 1; i <= totalEpisodes; i++) {
            final int episodeNumber = i;

            CheckBox checkBox = new CheckBox("Episodio " + episodeNumber);
            checkBox.setStyle(
                    "-fx-text-fill: #eaeaea; -fx-font-size: 12px;"
            );

            boolean watched = currentFavorite != null && currentFavorite.isEpisodeWatched(episodeNumber);
            checkBox.setSelected(watched);

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                onEpisodeToggled(episodeNumber);
            });

            episodesList.getChildren().add(checkBox);
        }
    }

    private int watchedCount() {
        return currentFavorite != null ? currentFavorite.getWatchedEpisodes().size() : 0;
    }

    /**
     * Se llama cuando el usuario marca/desmarca un episodio concreto.
     * Si el item aún no está en favoritos, lo añade automáticamente
     * (no se puede guardar progreso de episodios sin persistir el item).
     */
    private void onEpisodeToggled(int episodeNumber) {
        if (currentFavorite == null) {
            FavoriteItem fav = new FavoriteItem(currentItem);
            FavoritesService.addFavorite(fav);
            currentFavorite = fav;
            checkIfFavorite();
        }

        FavoritesService.toggleEpisodeWatched(currentFavorite.getId(), episodeNumber);

        // Recargar el estado real desde el favorito actualizado para mantener consistencia
        refreshCurrentFavoriteFromService();

        Integer total = currentItem.getEpisodes();
        if (total != null) {
            episodesHeaderLabel.setText("Episodios (" + watchedCount() + "/" + total + ")");
        }

        // Si se completaron todos los episodios, el botón de visto general también se actualiza
        updateViewedButtonStyle();
    }

    /**
     * Vuelve a leer el favorito actual desde el servicio, para reflejar
     * exactamente lo que quedó guardado (evita desincronización).
     */
    private void refreshCurrentFavoriteFromService() {
        if (currentFavorite == null) return;
        List<FavoriteItem> favs = FavoritesService.getFavorites();
        for (FavoriteItem fav : favs) {
            if (fav.getId().equals(currentFavorite.getId())) {
                currentFavorite = fav;
                return;
            }
        }
    }

    /**
     * Actualizar botones según el tipo de media.
     * Si el item tiene episodios desglosados (ANIME/SERIES con número de
     * episodios conocido), el botón "Marcar como visto" se oculta porque
     * el progreso se gestiona episodio a episodio en la lista de abajo.
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

        boolean hasEpisodeList = (item.getType() == MediaType.ANIME || item.getType() == MediaType.SERIES)
                && item.getEpisodes() != null && item.getEpisodes() > 0;

        viewedButton.setVisible(!hasEpisodeList);
        viewedButton.setManaged(!hasEpisodeList);
    }

    /**
     * Verificar si el item está en favoritos
     */
    private void checkIfFavorite() {
        boolean isFav = FavoritesService.isFavorite(currentItem.getTitle());
        if (isFav) {
            // Buscar el favorito para saber su estado
            currentFavorite = null;
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
            currentFavorite = null;
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