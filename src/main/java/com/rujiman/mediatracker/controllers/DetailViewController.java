package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.WatchProgressService;
import com.rujiman.mediatracker.services.TMDBService;
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

        // El viewport interno del ScrollPane no hereda el color de fondo por
        // CSS normal, y su Skin no existe hasta que el nodo está realmente
        // en una escena. Un solo Platform.runLater no es fiable porque puede
        // ejecutarse antes de que el Skin se construya. En su lugar, forzamos
        // el estilo cada vez que cambia el Skin (se dispara de forma fiable
        // en cuanto el ScrollPane es renderizable).
        detailScroll.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                applyViewportBackground();
            }
        });

        // Por si el Skin ya existía en el momento de inicializar (poco común,
        // pero cubre ese caso también)
        if (detailScroll.getSkin() != null) {
            applyViewportBackground();
        }
    }

    /**
     * Fuerza el fondo oscuro en el viewport interno del ScrollPane,
     * evitando el hueco blanco/gris por defecto cuando el contenido
     * es más corto que el área visible.
     */
    private void applyViewportBackground() {
        javafx.scene.Node viewport = detailScroll.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: #0f0f1a;");
        }
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

        // Verificar si está en favoritos (independiente del progreso)
        checkIfFavorite();

        // Estado de visto/jugado y progreso de episodios (independiente de favoritos)
        updateViewedButtonStyle();

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
            } else if (type == MediaType.SERIES && item.getTmdbId() != null) {
                // Las series de TMDB no traen el número de episodios en la
                // búsqueda (para no ralentizarla); se consulta aquí, solo
                // al abrir el detalle, en un hilo de fondo.
                loadEpisodeCountInBackground(item);
            }
            // Si no hay forma de conocer el número de episodios, no se
            // muestra nada (mejor que una lista vacía o incorrecta)

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
     * Consulta el número de episodios de una serie de TMDB en un hilo
     * de fondo, y si llega un resultado válido y seguimos en el mismo
     * item, construye la lista de episodios y la muestra con un fade.
     * No bloquea la UI ni retrasa la apertura del detalle.
     */
    private void loadEpisodeCountInBackground(MediaItem item) {
        final MediaItem itemAtRequestTime = item;

        new Thread(() -> {
            Integer total = new TMDBService().getEpisodeCount(item.getTmdbId());

            Platform.runLater(() -> {
                // Si el usuario ya cerró este detalle o abrió otro mientras
                // se esperaba la respuesta, no tocamos nada para evitar
                // pintar episodios sobre el item equivocado.
                if (currentItem != itemAtRequestTime) return;
                if (total == null || total <= 0) return;

                item.setEpisodes(total);
                buildEpisodesList(total);

                episodesSeparator.setVisible(true);
                episodesSeparator.setManaged(true);
                episodesSection.setVisible(true);
                episodesSection.setManaged(true);

                // Ahora que sabemos que hay episodios, el botón general de
                // "Marcar como visto" deja de tener sentido (el progreso se
                // gestiona episodio a episodio en la lista que acabamos de mostrar)
                viewedButton.setVisible(false);
                viewedButton.setManaged(false);

                FadeTransition fade = new FadeTransition(Duration.millis(250), episodesSection);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.play();
            });
        }).start();
    }

    /**
     * Construye la lista de checkboxes "Episodio 1".."Episodio N",
     * marcando como visto cada uno según el progreso guardado
     * (independiente de si el item es favorito o no).
     */
    private void buildEpisodesList(int totalEpisodes) {
        episodesList.getChildren().clear();
        WatchProgressService.Progress progress = WatchProgressService.getProgress(currentItem.getTitle());
        episodesHeaderLabel.setText("Episodios (" + progress.watchedEpisodes.size() + "/" + totalEpisodes + ")");

        for (int i = 1; i <= totalEpisodes; i++) {
            final int episodeNumber = i;

            CheckBox checkBox = new CheckBox("Episodio " + episodeNumber);
            checkBox.setStyle(
                    "-fx-text-fill: #eaeaea; -fx-font-size: 12px;"
            );

            checkBox.setSelected(progress.watchedEpisodes.contains(episodeNumber));

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                onEpisodeToggled(episodeNumber, totalEpisodes);
            });

            episodesList.getChildren().add(checkBox);
        }
    }

    /**
     * Se llama cuando el usuario marca/desmarca un episodio concreto.
     * Esto NO añade el item a favoritos; el progreso vive en su propio
     * almacenamiento, totalmente independiente.
     */
    private void onEpisodeToggled(int episodeNumber, int totalEpisodes) {
        WatchProgressService.toggleEpisodeWatched(currentItem.getTitle(), episodeNumber, totalEpisodes);

        WatchProgressService.Progress progress = WatchProgressService.getProgress(currentItem.getTitle());
        episodesHeaderLabel.setText("Episodios (" + progress.watchedEpisodes.size() + "/" + totalEpisodes + ")");

        // Si se completaron todos los episodios, el botón de visto general también se actualiza
        updateViewedButtonStyle();
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
        WatchProgressService.toggleViewed(currentItem.getTitle());
        updateViewedButtonStyle();
    }

    private void updateViewedButtonStyle() {
        boolean viewed = WatchProgressService.isViewed(currentItem.getTitle());
        if (viewed) {
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