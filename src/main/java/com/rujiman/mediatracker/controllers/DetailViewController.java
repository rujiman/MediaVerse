package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.PlanItem;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.WatchProgressService;
import com.rujiman.mediatracker.services.TMDBService;
import com.rujiman.mediatracker.services.AnilistService;
import com.rujiman.mediatracker.services.GameService;
import com.rujiman.mediatracker.services.PlanService;
import com.rujiman.mediatracker.services.StreamingLinkResolver;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    @FXML private FlowPane detailGenresBox;
    @FXML private Label descriptionHeaderLabel;

    // Recomendaciones (cargadas bajo demanda)
    @FXML private Separator recommendationsSeparator;
    @FXML private VBox recommendationsSection;
    @FXML private Label recommendationsHeaderLabel;
    @FXML private FlowPane recommendationsGrid;
    @FXML private Label detailPlatforms;
    @FXML private Button viewedButton;
    @FXML private Button favoriteButton;
    @FXML private Button openExternalButton;
    @FXML private Button backButton;

    // Botón de tráiler (MOVIE / SERIES): abre YouTube en el navegador
    // del sistema. Se valoró embeberlo con WebView, pero YouTube exige
    // un Referer válido que el WebView de JavaFX no envía correctamente
    // (Error 153: "Video player configuration error"), un problema
    // conocido y muy extendido en cualquier WebView embebido, no solo en
    // JavaFX. Para un proyecto de TFG, un enlace simple es más robusto
    // y mantenible que pelear con esa limitación.
    @FXML private Button trailerButton;

    // "Pienso ver/jugar/escuchar" + "Mover a Favoritos"
    @FXML private Button planButton;
    @FXML private Button moveToFavoritesButton;

    // Episodios (ANIME / SERIES)
    @FXML private Separator episodesSeparator;
    @FXML private VBox episodesSection;
    @FXML private Label episodesHeaderLabel;
    @FXML private VBox episodesList;
    @FXML private Button markAllWatchedButton;
    @FXML private Button unmarkAllWatchedButton;
    @FXML private TextField jumpToEpisodeField;
    @FXML private Button jumpToEpisodeButton;

    // Canciones de álbum (MUSIC)
    @FXML private VBox trackCountSection;
    @FXML private Label trackCountLabel;

    // Preview de 30s (MUSIC, canciones sueltas con preview de Deezer)
    @FXML private VBox previewSection;
    @FXML private Button previewButton;
    @FXML private Label previewTimeLabel;
    @FXML private ProgressBar previewProgressBar;
    private MediaPlayer previewPlayer;

    // Valoración personal (1-5 estrellas), independiente del score de la API
    @FXML private HBox userRatingStars;

    private MediaItem currentItem;
    private FavoriteItem currentFavorite;

    /**
     * Si este detalle se abrió desde una de las listas "Pienso ver/jugar/
     * escuchar" (en vez de desde búsqueda o favoritos normales), aquí se
     * guarda desde cuál exactamente y el ID del PlanItem correspondiente.
     * null en ambos = el detalle NO viene del plan, así que el botón
     * "Mover a Favoritos" no tiene sentido y se mantiene oculto.
     * Se resetea a null en cada loadItem() y solo se vuelve a fijar si
     * SearchController llama a setPlanContext() justo después.
     */
    private PlanService.ListKind currentPlanContext;
    private String currentPlanItemId;

    /**
     * Marca que el item actual viene de una de las listas de plan. Debe
     * llamarse DESPUÉS de loadItem(), ya que loadItem() resetea el
     * contexto a null por defecto (asumiendo que no viene del plan,
     * salvo que se indique explícitamente lo contrario aquí).
     */
    public void setPlanContext(PlanService.ListKind listKind, String planItemId) {
        this.currentPlanContext = listKind;
        this.currentPlanItemId = planItemId;
        updateMoveToFavoritesButtonVisibility();
    }

    /** Acción a ejecutar cuando se pulsa "Volver" (la define quien abre este detalle). */
    private Runnable onBackAction;

    /**
     * Acción a ejecutar al pinchar una tarjeta de recomendación: recibe
     * el MediaItem recomendado y debe abrir un NUEVO detalle apilado
     * encima de este (para que "Volver" desde la recomendación regrese
     * aquí, no a la búsqueda). La define quien abre este detalle
     * (SearchController), igual patrón que onBackAction.
     */
    private java.util.function.Consumer<MediaItem> onOpenDetailAction;

    public void setOnBackAction(Runnable onBackAction) {
        this.onBackAction = onBackAction;
    }

    public void setOnOpenDetailAction(java.util.function.Consumer<MediaItem> onOpenDetailAction) {
        this.onOpenDetailAction = onOpenDetailAction;
    }

    @FXML
    private void onBack() {
        // Si hay un preview sonando, lo detenemos al salir del detalle;
        // si no, seguiría reproduciéndose de fondo sobre la búsqueda/home.
        stopAndDisposePreviewPlayer();

        if (onBackAction != null) {
            onBackAction.run();
        }
    }

    private static final String COLOR_PRIMARY = "#ec4d80";
    private static final String COLOR_BG_CARD = "#1c1730";
    private static final String COLOR_TEXT = "#f0eef5";
    private static final String COLOR_TEXT_DIM = "#756f94";
    private static final String COLOR_GREEN = "#4dec9e";

    /**
     * Devuelve el hex de acento de la sección del item, según la paleta
     * "Constelaciones" de theme.css. Se usa para colorear dinámicamente
     * elementos del detalle (título, botón de favorito, tráiler, cabecera
     * de episodios) según el tipo del item que se está viendo en cada
     * momento, en vez de usar siempre el mismo color de marca para todo.
     */
    private String sectionAccentHex(MediaType type) {
        if (type == null) return COLOR_PRIMARY;
        return switch (type) {
            case GAME -> "#4dd9ec";
            case SERIES -> "#8b5cf6";
            case ANIME -> "#ec4dc0";
            case MUSIC -> "#4dec9e";
            case MOVIE -> "#ecb14d";
        };
    }

    @FXML
    public void initialize() {
        detailScroll.setStyle("-fx-background-color: #100c1c;");
        detailContainer.setStyle("-fx-background-color: #100c1c;");

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
            viewport.setStyle("-fx-background-color: #100c1c;");
        }
    }

    /**
     * Cargar detalles de un MediaItem
     */
    public void loadItem(MediaItem item) {
        this.currentItem = item;

        // Por defecto, asumimos que el detalle NO viene de una lista de
        // plan; si sí viene, quien abre este detalle (SearchController)
        // llamará a setPlanContext() justo después de loadItem().
        this.currentPlanContext = null;
        this.currentPlanItemId = null;

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
        // El título se pinta con el color de acento de SU sección (cian
        // para Juegos, violeta para Series, magenta para Anime, etc.),
        // así el detalle "se siente" de esa sección concreta en vez de
        // usar siempre el mismo rosa para todo tipo de contenido.
        detailTitle.setStyle("-fx-text-fill: " + sectionAccentHex(item.getType()) + "; -fx-font-size: 24px; -fx-font-weight: bold;");

        detailYear.setText(item.getYear() != null ? String.valueOf(item.getYear()) : "—");

        // Puntuación
        int score = item.getScore() != null ? item.getScore() : 0;
        detailScore.setText(score > 0 ? "⭐ " + score + "/100" : "Sin puntuación");

        // Descripción: la cabecera se pinta con el acento de la sección
        // del item, igual que el título principal, para mantener
        // consistencia visual (antes se quedaba siempre en rosa fijo).
        detailDescription.setText(item.getDescription() != null ? item.getDescription() : "Sin descripción");
        descriptionHeaderLabel.setStyle("-fx-text-fill: " + sectionAccentHex(item.getType()) + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Géneros: un chip individual por cada género, en vez de texto
        // plano separado por comas (más legible y más bonito).
        detailGenresBox.getChildren().clear();
        if (item.getGenres() != null && !item.getGenres().isEmpty()) {
            for (String genre : item.getGenres()) {
                Label chip = new Label(genre);
                chip.getStyleClass().add("genre-chip");
                detailGenresBox.getChildren().add(chip);
            }
            detailGenresBox.setVisible(true);
            detailGenresBox.setManaged(true);
        } else {
            detailGenresBox.setVisible(false);
            detailGenresBox.setManaged(false);
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

        // Valoración personal (1-5 estrellas), independiente de favoritos y del score de la API
        setupUserRatingStars(item);

        // Tráiler en YouTube (solo MOVIE/SERIES; se consulta bajo demanda al pulsar el botón)
        setupTrailerButton(item);

        // Texto del botón "Abrir en navegador"/"Ver ahora": depende de si
        // hay alguna plataforma de streaming reconocida para este item
        // (ver detalle en el propio método).
        setupOpenExternalButton(item);

        // Preview de 30s (solo MUSIC, canciones sueltas con preview de Deezer)
        setupPreviewButton(item);

        // Recomendaciones del mismo tipo, cargadas en segundo plano
        // (TMDB para MOVIE/SERIES, AniList para ANIME, IGDB para GAME;
        // MUSIC se queda sin esta sección, Deezer no da recomendaciones
        // fiables por canción/álbum)
        setupRecommendations(item);

        // "Pienso ver/jugar/escuchar": qué lista corresponde según el
        // tipo, y si ya está añadido o no. El botón "Mover a Favoritos"
        // se actualiza aparte, en setPlanContext() (que se llama después
        // de loadItem() solo si el detalle viene de una de estas listas).
        setupPlanButton(item);
        updateMoveToFavoritesButtonVisibility();
    }

    /**
     * Muestra el botón de tráiler solo para películas y series (TMDB es la
     * única fuente que nos da videos de YouTube). El botón abre YouTube en
     * el navegador del sistema, igual que "Abrir en navegador" pero con
     * la URL del tráiler en concreto.
     */
    private void setupTrailerButton(MediaItem item) {
        boolean canHaveTrailer = (item.getType() == MediaType.MOVIE || item.getType() == MediaType.SERIES)
                && item.getTmdbId() != null;

        trailerButton.setVisible(canHaveTrailer);
        trailerButton.setManaged(canHaveTrailer);
        trailerButton.setText("▶ Ver tráiler en YouTube");
        trailerButton.setDisable(false);

        if (canHaveTrailer) {
            trailerButton.setStyle(
                    "-fx-background-color: " + sectionAccentHex(item.getType()) + ";" +
                            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;" +
                            "-fx-padding: 10 16 10 16; -fx-background-radius: 8; -fx-cursor: hand;"
            );
        }
    }

    /**
     * Abre el tráiler en el navegador del sistema. Si todavía no tenemos
     * la clave de YouTube guardada para este item, la consulta a TMDB en
     * un hilo de fondo primero (sin congelar la UI mientras se espera).
     */
    @FXML
    private void onOpenTrailer() {
        if (currentItem.getTrailerKey() != null && !currentItem.getTrailerKey().isBlank()) {
            openYoutubeUrl(currentItem.getTrailerKey());
            return;
        }

        trailerButton.setDisable(true);
        trailerButton.setText("Buscando tráiler...");

        final MediaItem itemAtRequestTime = currentItem;
        boolean isMovie = itemAtRequestTime.getType() == MediaType.MOVIE;
        Integer tmdbId = itemAtRequestTime.getTmdbId();

        new Thread(() -> {
            String key = new TMDBService().getTrailerKey(tmdbId, isMovie);

            Platform.runLater(() -> {
                trailerButton.setDisable(false);
                trailerButton.setText("▶ Ver tráiler en YouTube");

                // Si el usuario ya cerró este detalle o abrió otro mientras
                // esperábamos la respuesta, no abrimos nada para evitar
                // abrir el tráiler equivocado.
                if (currentItem != itemAtRequestTime) return;

                if (key == null || key.isBlank()) {
                    trailerButton.setText("Sin tráiler disponible");
                    trailerButton.setDisable(true);
                    return;
                }

                itemAtRequestTime.setTrailerKey(key);

                // Si el item ya está en favoritos, guardamos también la
                // clave ahí, para no tener que volver a pedirla a TMDB la
                // próxima vez que se abra este detalle desde favoritos.
                if (currentFavorite != null) {
                    currentFavorite.setTrailerKey(key);
                    FavoritesService.updateFavorite(currentFavorite);
                }

                openYoutubeUrl(key);
            });
        }).start();
    }

    /**
     * Abre la URL de YouTube correspondiente a una clave de vídeo en el
     * navegador por defecto del sistema.
     */
    private void openYoutubeUrl(String youtubeKey) {
        try {
            java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://www.youtube.com/watch?v=" + youtubeKey)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Muestra el bloque de preview solo si el item es una canción de tipo
     * MUSIC con preview disponible (Deezer no da preview para álbumes
     * completos, solo para canciones sueltas). Libera cualquier
     * reproductor anterior, para no dejar sonando el preview de un item
     * distinto al cambiar de detalle.
     */
    private void setupPreviewButton(MediaItem item) {
        stopAndDisposePreviewPlayer();

        boolean hasPreview = item.getType() == MediaType.MUSIC
                && item.getPreviewUrl() != null && !item.getPreviewUrl().isBlank();

        previewSection.setVisible(hasPreview);
        previewSection.setManaged(hasPreview);

        if (hasPreview) {
            previewButton.setText("▶ Escuchar preview (30s)");
            previewTimeLabel.setText("0:00 / 0:30");
            previewProgressBar.setProgress(0);
        }
    }

    // Duración total del preview, fijada una vez que el MediaPlayer la
    // conoce con certeza (evento "ready"); antes de eso usamos 30s como
    // valor de referencia, ya que es la duración estándar de los
    // previews de Deezer. Evita que la barra/tiempo salten de forma
    // descoordinada mientras totalDuration todavía vale UNKNOWN.
    private Duration previewKnownDuration = Duration.seconds(30);

    /**
     * Reproduce/pausa el preview de 30s. Crea el MediaPlayer la primera
     * vez que se pulsa; las siguientes veces solo lo pausa o reanuda.
     */
    @FXML
    private void onTogglePreview() {
        if (previewPlayer == null) {
            startPreviewPlayback(currentItem.getPreviewUrl(), false);
            return;
        }

        if (previewPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            previewPlayer.pause();
            previewButton.setText("▶ Escuchar preview (30s)");
        } else {
            previewPlayer.play();
            previewButton.setText("⏸ Pausar");
        }
    }

    /**
     * Crea el MediaPlayer y empieza a reproducir la URL dada.
     *
     * @param previewUrl     la URL de preview a reproducir
     * @param isRetryWithFreshUrl true si esta llamada ya es un reintento
     *                            con una URL recién pedida a Deezer (para
     *                            no entrar en bucle si esa URL también falla)
     */
    private void startPreviewPlayback(String previewUrl, boolean isRetryWithFreshUrl) {
        previewKnownDuration = Duration.seconds(30);

        previewPlayer = new MediaPlayer(new Media(previewUrl));
        previewPlayer.setVolume(0.4); // antes sonaba demasiado alto por defecto

        // En cuanto el MediaPlayer conoce la duración real (puede no
        // ser exactamente 30.000s), la fijamos como referencia estable
        // para el resto de la reproducción.
        previewPlayer.setOnReady(() -> {
            Duration real = previewPlayer.getMedia().getDuration();
            if (real != null && !real.isUnknown() && real.greaterThan(Duration.ZERO)) {
                previewKnownDuration = real;
            }
        });

        previewPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
            double progress = newVal.toSeconds() / previewKnownDuration.toSeconds();
            previewProgressBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
            previewTimeLabel.setText(formatTime(newVal) + " / " + formatTime(previewKnownDuration));
        });

        previewPlayer.setOnEndOfMedia(() -> {
            previewPlayer.stop();
            previewButton.setText("▶ Escuchar preview (30s)");
            previewProgressBar.setProgress(0);
            previewTimeLabel.setText("0:00 / " + formatTime(previewKnownDuration));
        });

        previewPlayer.setOnError(() -> {
            // La previewUrl de Deezer es una URL firmada con caducidad
            // (parámetro hdnea/exp): deja de funcionar pasado un tiempo
            // aunque siga guardada en favoritos. Si esto es la primera
            // vez que falla (no un reintento ya con URL fresca), pedimos
            // una nueva a Deezer por título y reintentamos una sola vez,
            // en vez de dejar el preview roto permanentemente.
            if (isRetryWithFreshUrl) {
                previewButton.setText("Error al reproducir");
                previewButton.setDisable(true);
                return;
            }

            stopAndDisposePreviewPlayer();
            previewButton.setText("Actualizando preview...");
            previewButton.setDisable(true);

            final MediaItem itemAtRequestTime = currentItem;

            new Thread(() -> {
                String freshUrl = new com.rujiman.mediatracker.services.MusicService()
                        .refreshPreviewUrl(itemAtRequestTime.getTitle());

                Platform.runLater(() -> {
                    // Si el usuario ya cambió de detalle mientras se
                    // esperaba la respuesta, no tocamos nada.
                    if (currentItem != itemAtRequestTime) return;

                    previewButton.setDisable(false);

                    if (freshUrl == null || freshUrl.isBlank()) {
                        previewButton.setText("Sin preview disponible");
                        previewButton.setDisable(true);
                        return;
                    }

                    itemAtRequestTime.setPreviewUrl(freshUrl);

                    // Si el item ya está en favoritos, guardamos también
                    // la URL fresca ahí, para que la próxima vez no haga
                    // falta este mismo reintento (aunque, al ser una URL
                    // con caducidad, puede que vuelva a caducar más
                    // adelante igualmente; esto solo reduce cuántas veces
                    // hace falta el reintento, no lo elimina del todo).
                    if (currentFavorite != null) {
                        currentFavorite.setPreviewUrl(freshUrl);
                        FavoritesService.updateFavorite(currentFavorite);
                    }

                    previewButton.setText("▶ Escuchar preview (30s)");
                    startPreviewPlayback(freshUrl, true);
                });
            }).start();
        });

        previewPlayer.play();
        previewButton.setText("⏸ Pausar");
    }

    /**
     * Detiene y libera los recursos del MediaPlayer del preview actual,
     * si existía. Hay que llamarlo siempre al cambiar de item (para no
     * dejar un preview sonando de fondo tras navegar a otro detalle) y
     * sería ideal también al cerrar la app, aunque al ser un clip de
     * solo 30s el impacto de no liberarlo explícitamente es mínimo.
     */
    private void stopAndDisposePreviewPlayer() {
        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.dispose();
            previewPlayer = null;
        }
    }

    private String formatTime(Duration duration) {
        int totalSeconds = (int) Math.round(duration.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // ===========================================================
    // RECOMENDACIONES
    // ===========================================================

    /**
     * Muestra recomendaciones del mismo tipo que el item actual, cargadas
     * en un hilo de fondo (no bloquean la apertura del detalle, igual que
     * tráiler/episodios). La fuente depende del tipo:
     * - MOVIE/SERIES: TMDB /recommendations (necesita tmdbId)
     * - ANIME: AniList GraphQL recommendations (necesita anilistId)
     * - GAME: IGDB similar_games (necesita igdbId)
     * - MUSIC: sin soporte, la sección se queda oculta
     *
     * Si el item no tiene el ID nativo necesario (por ejemplo, favoritos
     * guardados antes de que existiera este campo), la sección también
     * se queda oculta en vez de fallar o mostrar nada vacío.
     */
    private void setupRecommendations(MediaItem item) {
        recommendationsSection.setVisible(false);
        recommendationsSection.setManaged(false);
        recommendationsSeparator.setVisible(false);
        recommendationsSeparator.setManaged(false);
        recommendationsGrid.getChildren().clear();

        MediaType type = item.getType();
        if (type == MediaType.MUSIC) return; // sin soporte fiable en Deezer

        boolean canFetch =
                ((type == MediaType.MOVIE || type == MediaType.SERIES) && item.getTmdbId() != null) ||
                        (type == MediaType.ANIME && item.getAnilistId() != null) ||
                        (type == MediaType.GAME && item.getIgdbId() != null);

        if (!canFetch) return;

        final MediaItem itemAtRequestTime = item;

        new Thread(() -> {
            List<MediaItem> recommendations;

            switch (type) {
                case MOVIE -> recommendations = new TMDBService().getRecommendations(item.getTmdbId(), true);
                case SERIES -> recommendations = new TMDBService().getRecommendations(item.getTmdbId(), false);
                case ANIME -> recommendations = new AnilistService().getRecommendations(item.getAnilistId());
                case GAME -> recommendations = new GameService().getSimilarGames(item.getIgdbId());
                default -> recommendations = List.of();
            }

            final List<MediaItem> results = recommendations;

            Platform.runLater(() -> {
                // Si el usuario ya cerró este detalle o abrió otro mientras
                // se esperaba la respuesta, no pintamos nada para evitar
                // mezclar recomendaciones del item equivocado.
                if (currentItem != itemAtRequestTime) return;
                if (results == null || results.isEmpty()) return;

                recommendationsHeaderLabel.setText("Si te gusta " + itemAtRequestTime.getTitle() + ", te recomendamos:");
                recommendationsHeaderLabel.setStyle(
                        "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + sectionAccentHex(type) + ";"
                );

                for (MediaItem rec : results) {
                    recommendationsGrid.getChildren().add(buildRecommendationCard(rec));
                }

                recommendationsSeparator.setVisible(true);
                recommendationsSeparator.setManaged(true);
                recommendationsSection.setVisible(true);
                recommendationsSection.setManaged(true);

                FadeTransition fade = new FadeTransition(Duration.millis(250), recommendationsSection);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.play();
            });
        }).start();
    }

    /**
     * Tarjeta pequeña para una recomendación: imagen + título, clicable
     * para abrir un nuevo detalle de ese item (apilado igual que cualquier
     * otra navegación a detalle: "Volver" desde ahí regresa aquí mismo).
     */
    private VBox buildRecommendationCard(MediaItem rec) {
        double width = 130, height = 180;

        VBox card = new VBox(4);
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        card.getStyleClass().add("card-base");
        card.setAlignment(Pos.TOP_LEFT);

        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(width, height);
        imageContainer.setMaxSize(width, height);

        ImageView cover = new ImageView();
        cover.setFitWidth(width);
        cover.setFitHeight(height);
        cover.setPreserveRatio(false);
        cover.setSmooth(true);

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        cover.setClip(clip);

        if (rec.getImageUrl() != null && !rec.getImageUrl().isBlank()) {
            try {
                cover.setImage(new Image(rec.getImageUrl(), width * 2, height * 2, false, true, true));
            } catch (Exception ignored) {}
        }

        imageContainer.getChildren().add(cover);

        Label titleLabel = new Label(rec.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(width);
        titleLabel.getStyleClass().add("text-heading");
        titleLabel.setStyle("-fx-font-size: 11px;");

        card.getChildren().addAll(imageContainer, titleLabel);

        String hoverClass = cardHoverClassFor(rec.getType());
        card.setOnMouseEntered(e -> card.getStyleClass().add(hoverClass));
        card.setOnMouseExited(e -> card.getStyleClass().remove(hoverClass));

        card.setOnMouseClicked(e -> {
            if (onOpenDetailAction != null) {
                onOpenDetailAction.accept(rec);
            } else {
                // Si no hay acción de navegación externa configurada
                // (poco probable, pero por seguridad), al menos
                // recargamos este mismo detalle con el nuevo item.
                loadItem(rec);
            }
        });

        return card;
    }

    /**
     * Devuelve la clase CSS de hover (glow de color) correspondiente al
     * tipo de contenido, según la paleta "Constelaciones" de theme.css
     * (mismo patrón que HomeController.cardHoverClassFor()).
     */
    private String cardHoverClassFor(MediaType type) {
        if (type == null) return "card-hover-series";
        return switch (type) {
            case GAME -> "card-hover-game";
            case SERIES -> "card-hover-series";
            case ANIME -> "card-hover-anime";
            case MUSIC -> "card-hover-music";
            case MOVIE -> "card-hover-movie";
        };
    }

    /**
     * Construye las 5 estrellas clicables para la valoración personal del
     * usuario. Cada estrella, al pincharla, guarda esa puntuación (1-5) en
     * WatchProgressService, totalmente aparte de favoritos y del score
     * que viene de las APIs externas.
     */
    private void setupUserRatingStars(MediaItem item) {
        userRatingStars.getChildren().clear();

        Integer currentRating = WatchProgressService.getUserRating(item.getTitle());
        int rating = currentRating != null ? currentRating : 0;

        for (int i = 1; i <= 5; i++) {
            final int starValue = i;

            Label star = new Label(starValue <= rating ? "★" : "☆");
            star.setStyle(
                    "-fx-font-size: 18px;" +
                            "-fx-text-fill: " + (starValue <= rating ? "#f1c40f" : "#555577") + ";" +
                            "-fx-cursor: hand;"
            );

            star.setOnMouseClicked(e -> {
                Integer existing = WatchProgressService.getUserRating(item.getTitle());
                int existingValue = existing != null ? existing : 0;

                // Pinchar la misma estrella que ya estaba puesta como máxima
                // quita la valoración (toggle), igual que en apps como
                // Letterboxd; pinchar cualquier otra la establece directamente.
                int newRating = (starValue == existingValue) ? 0 : starValue;

                WatchProgressService.setUserRating(item.getTitle(), newRating > 0 ? newRating : null);
                setupUserRatingStars(item); // repintar con el nuevo estado
            });

            userRatingStars.getChildren().add(star);
        }
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
            episodesHeaderLabel.setStyle("-fx-text-fill: " + sectionAccentHex(type) + "; -fx-font-size: 14px; -fx-font-weight: bold;");

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
                    "-fx-text-fill: #f0eef5; -fx-font-size: 12px;"
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
     * Marca de golpe todos los episodios como vistos. Pensado para series
     * largas que el usuario ya vio antes de usar la app (ej. Naruto,
     * One Piece), sin tener que pasar episodio a episodio.
     */
    @FXML
    private void onMarkAllEpisodesWatched() {
        Integer total = currentItem.getEpisodes();
        if (total == null || total <= 0) return;

        WatchProgressService.markAllEpisodesWatched(currentItem.getTitle(), total);
        buildEpisodesList(total);
        updateViewedButtonStyle();
    }

    /**
     * Desmarca todos los episodios de golpe (vuelve a 0/total), por si el
     * usuario se equivocó al usar "Marcar todos" o quiere reiniciar el
     * seguimiento de esa serie.
     */
    @FXML
    private void onUnmarkAllEpisodesWatched() {
        Integer total = currentItem.getEpisodes();
        if (total == null || total <= 0) return;

        WatchProgressService.unmarkAllEpisodesWatched(currentItem.getTitle(), total);
        buildEpisodesList(total);
        updateViewedButtonStyle();
    }

    /**
     * Marca como vistos todos los episodios desde el 1 hasta el número
     * introducido en el campo (incluido), y desmarca el resto. Útil para
     * decir "voy por el episodio 150 de One Piece" de una sola vez, sin
     * tener que marcar uno a uno.
     */
    @FXML
    private void onJumpToEpisode() {
        Integer total = currentItem.getEpisodes();
        if (total == null || total <= 0) return;

        String text = jumpToEpisodeField.getText().trim();
        if (text.isEmpty()) return;

        int targetEpisode;
        try {
            targetEpisode = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            jumpToEpisodeField.setStyle(jumpToEpisodeField.getStyle() + "-fx-border-color: #e74c3c; -fx-border-width: 1;");
            return;
        }

        if (targetEpisode < 0) targetEpisode = 0;

        WatchProgressService.markEpisodesUpTo(currentItem.getTitle(), targetEpisode, total);
        buildEpisodesList(total);
        updateViewedButtonStyle();
        jumpToEpisodeField.clear();
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
        String accentHex = sectionAccentHex(currentItem.getType());

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
            favoriteButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10 16 10 16; -fx-background-radius: 8; -fx-cursor: hand;");
        } else {
            currentFavorite = null;
            favoriteButton.setText("⭐ Agregar a favoritos");
            favoriteButton.setStyle("-fx-background-color: " + accentHex + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10 16 10 16; -fx-background-radius: 8; -fx-cursor: hand;");
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

    // ===========================================================
    // "PIENSO VER / JUGAR / ESCUCHAR"
    // ===========================================================

    /**
     * Determina a cuál de las 3 listas pertenece un tipo de contenido.
     * SERIES/MOVIE/ANIME van todas a "Pienso ver" (WATCH), GAME a
     * "Pienso jugar" (PLAY), MUSIC a "Pienso escuchar" (LISTEN).
     */
    private PlanService.ListKind listKindForType(MediaType type) {
        if (type == MediaType.GAME) return PlanService.ListKind.PLAY;
        if (type == MediaType.MUSIC) return PlanService.ListKind.LISTEN;
        return PlanService.ListKind.WATCH; // SERIES, MOVIE, ANIME
    }

    private String planButtonLabelFor(MediaType type) {
        return switch (listKindForType(type)) {
            case WATCH -> "Pienso ver esto";
            case PLAY -> "Pienso jugar esto";
            case LISTEN -> "Pienso escuchar esto";
        };
    }

    /**
     * Configura el botón "Pienso ver/jugar/escuchar": el texto exacto
     * depende del tipo del item, y si ya está añadido a su lista
     * correspondiente, el botón cambia a "Quitar de..." en vez de
     * duplicar la entrada.
     */
    private void setupPlanButton(MediaItem item) {
        PlanService.ListKind kind = listKindForType(item.getType());
        boolean inPlan = PlanService.isInPlan(kind, item.getTitle());

        if (inPlan) {
            planButton.setText("✕ Quitar de \"" + planButtonLabelFor(item.getType()) + "\"");
        } else {
            planButton.setText("📋 " + capitalize(planButtonLabelFor(item.getType())));
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    @FXML
    private void onTogglePlan() {
        PlanService.ListKind kind = listKindForType(currentItem.getType());
        boolean inPlan = PlanService.isInPlan(kind, currentItem.getTitle());

        if (inPlan) {
            // Buscar el PlanItem por título para poder quitarlo por ID
            for (PlanItem planItem : PlanService.getItems(kind)) {
                if (planItem.getTitle().equalsIgnoreCase(currentItem.getTitle())) {
                    PlanService.removeItem(kind, planItem.getId());
                    break;
                }
            }
        } else {
            PlanService.addItem(kind, new PlanItem(currentItem));
        }

        setupPlanButton(currentItem);
    }

    /**
     * El botón "Mover a Favoritos" solo tiene sentido si este detalle se
     * abrió desde una de las listas de plan (currentPlanContext no nulo).
     * Se llama tanto desde loadItem() (lo oculta por defecto) como desde
     * setPlanContext() (lo muestra si corresponde).
     */
    private void updateMoveToFavoritesButtonVisibility() {
        boolean fromPlan = currentPlanContext != null && currentPlanItemId != null;
        moveToFavoritesButton.setVisible(fromPlan);
        moveToFavoritesButton.setManaged(fromPlan);
    }

    /**
     * Mueve el item actual de su lista de plan a Favoritos: crea el
     * FavoriteItem (con el mismo progreso de episodios que ya tuviera en
     * el plan, si alguno) y elimina el PlanItem original. El usuario
     * vuelve a quien le abrió este detalle (la propia lista de plan, ya
     * actualizada al no contener este item).
     */
    @FXML
    private void onMoveToFavorites() {
        if (currentPlanContext == null || currentPlanItemId == null) return;

        FavoriteItem fav = new FavoriteItem(currentItem);

        // Si el item tenía progreso de episodios en el plan, lo
        // trasladamos al nuevo favorito para no perderlo.
        for (PlanItem planItem : PlanService.getItems(currentPlanContext)) {
            if (planItem.getId().equals(currentPlanItemId)) {
                fav.setTotalEpisodes(planItem.getTotalEpisodes());
                fav.setWatchedEpisodes(planItem.getWatchedEpisodes());
                fav.setViewed(planItem.isViewed());
                break;
            }
        }

        FavoritesService.addFavorite(fav);
        PlanService.removeItem(currentPlanContext, currentPlanItemId);

        currentFavorite = fav;
        checkIfFavorite();

        // Tras moverlo, ya no tiene sentido seguir mostrando "Mover a
        // Favoritos" para este detalle (ya está movido); y el botón de
        // plan debe reflejar que ya no está en esa lista.
        currentPlanContext = null;
        currentPlanItemId = null;
        updateMoveToFavoritesButtonVisibility();
        setupPlanButton(currentItem);
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

    /**
     * Calcula, para SERIES/MOVIE/ANIME, las opciones de plataforma
     * reconocidas para este item (nombre -> URL ya resuelta). Para
     * GAME/MUSIC devuelve un mapa vacío, ya que esos tipos no pasan por
     * el selector de plataformas en absoluto. Centraliza aquí la misma
     * lógica que usan tanto el texto del botón (setupOpenExternalButton)
     * como la acción de abrir (onOpenExternal), para no recalcularla de
     * dos formas distintas que puedan desincronizarse.
     */
    private Map<String, String> resolvedStreamingOptions(MediaItem item) {
        if (item.getType() == MediaType.SERIES || item.getType() == MediaType.MOVIE) {
            return StreamingLinkResolver.resolveForPlatforms(item.getPlatforms(), item.getTitle());
        }
        if (item.getType() == MediaType.ANIME) {
            return StreamingLinkResolver.resolveForAnime(item.getTitle());
        }
        return Map.of();
    }

    /**
     * Texto del botón "Abrir en navegador"/"Ver ahora":
     * - SERIES/MOVIE con al menos una plataforma reconocida -> "Ver ahora"
     *   (sabemos que está realmente disponible en streaming).
     * - ANIME siempre -> "Ver ahora" (opciones fijas de búsqueda, aunque
     *   no hay disponibilidad confirmada por título).
     * - SERIES/MOVIE sin ninguna plataforma reconocida, y GAME/MUSIC ->
     *   "Abrir en navegador" (cae al externalUrl de siempre).
     */
    private void setupOpenExternalButton(MediaItem item) {
        boolean showViewNow;
        if (item.getType() == MediaType.ANIME) {
            showViewNow = true;
        } else if (item.getType() == MediaType.SERIES || item.getType() == MediaType.MOVIE) {
            showViewNow = !resolvedStreamingOptions(item).isEmpty();
        } else {
            showViewNow = false;
        }

        openExternalButton.setText(showViewNow ? "▶ Ver ahora" : "Abrir en navegador");
    }

    /**
     * "Abrir en navegador" / "Ver ahora": dónde aterriza el usuario
     * depende del tipo de contenido.
     *
     * SERIES/MOVIE: TMDB nos da nombres de proveedor reales
     * (item.getPlatforms()), pero no enlaces directos al título dentro
     * de cada plataforma (JustWatch no los cede vía API). Cruzamos esos
     * nombres contra un mapa fijo de URLs de búsqueda
     * (StreamingLinkResolver): si solo hay una plataforma reconocida se
     * abre directa, si hay varias se deja elegir con un ChoiceDialog, y
     * si no se reconoce ninguna (o no hay platforms) caemos al
     * externalUrl de siempre (la página de TMDB) como último recurso.
     *
     * ANIME: "platforms" en este tipo no es disponibilidad real (es
     * estudio/formato), así que aquí no se consulta en absoluto; se
     * ofrece siempre el mismo listado fijo de webs de anime.
     *
     * GAME/MUSIC: sin cambios respecto a la versión anterior, se abre
     * directamente el externalUrl (página de IGDB/Deezer del item).
     */
    @FXML
    private void onOpenExternal() {
        if (currentItem == null) return;

        MediaType type = currentItem.getType();

        if (type == MediaType.SERIES || type == MediaType.MOVIE) {
            openWithPlatformChoice(resolvedStreamingOptions(currentItem), true);
            return;
        }

        if (type == MediaType.ANIME) {
            openWithPlatformChoice(resolvedStreamingOptions(currentItem), false);
            return;
        }

        // GAME / MUSIC: comportamiento original, sin selector.
        openExternalUrlDirect(currentItem.getExternalUrl());
    }

    /**
     * Decide qué hacer una vez resueltas las opciones de plataforma:
     * 0 opciones -> fallback al externalUrl original (solo aplica a
     * SERIES/MOVIE, donde fallbackToExternalUrl llega como true; ANIME
     * siempre tiene opciones fijas, así que nunca debería caer aquí).
     * 1 opción -> se abre directa, sin molestar con un diálogo de un
     * solo botón. 2+ opciones -> ChoiceDialog para elegir.
     */
    private void openWithPlatformChoice(Map<String, String> options, boolean fallbackToExternalUrl) {
        if (options.isEmpty()) {
            if (fallbackToExternalUrl) {
                openExternalUrlDirect(currentItem.getExternalUrl());
            }
            return;
        }

        if (options.size() == 1) {
            openExternalUrlDirect(options.values().iterator().next());
            return;
        }

        List<String> names = new ArrayList<>(options.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Abrir plataforma");
        dialog.setHeaderText(null);
        dialog.setContentText("¿Dónde quieres buscar \"" + currentItem.getTitle() + "\"?");
        styleDialogDark(dialog.getDialogPane());
        darkenDialogWindow(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(selection -> openExternalUrlDirect(options.get(selection)));
    }

    private void openExternalUrlDirect(String url) {
        if (url != null && !url.isBlank()) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Mismo estilo oscuro ("Constelaciones") usado en los diálogos de
     * carpetas (FavoritesViewController/WatchlistViewController). No es
     * un helper compartido en una clase de utilidades común: se duplica
     * aquí igual que en esos controllers.
     */
    private void styleDialogDark(DialogPane pane) {
        pane.getStylesheets().add(
                getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm()
        );
        pane.getStyleClass().add("bg-panel-flat");
        pane.setStyle("-fx-background-color: #1c1730; -fx-text-fill: #f0eef5;");
        pane.applyCss();

        javafx.scene.Node headerPanel = pane.lookup(".header-panel");
        if (headerPanel != null) {
            headerPanel.setStyle("-fx-background-color: #1c1730;");
        } else {
            Platform.runLater(() -> {
                javafx.scene.Node hp = pane.lookup(".header-panel");
                if (hp != null) hp.setStyle("-fx-background-color: #1c1730;");
            });
        }

        forceLabelColorRecursive(pane, "#f0eef5");
    }

    private void forceLabelColorRecursive(javafx.scene.Parent parent, String hexColor) {
        for (javafx.scene.Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof Label label) {
                label.setStyle("-fx-text-fill: " + hexColor + ";");
            }
            if (node instanceof javafx.scene.Parent childParent) {
                forceLabelColorRecursive(childParent, hexColor);
            }
        }
    }

    private void darkenDialogWindow(DialogPane pane) {
        if (pane.getScene() != null) {
            pane.getScene().setFill(Color.web("#100c1c"));
        } else {
            pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setFill(Color.web("#100c1c"));
                }
            });
        }
    }
}