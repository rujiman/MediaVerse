package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.ui.CollectionUI;
import com.rujiman.mediatracker.ui.Dialogs;
import com.rujiman.mediatracker.ui.Palette;
import com.rujiman.mediatracker.ui.Layout;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import com.rujiman.mediatracker.models.PlanItem;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.WatchProgressService;
import com.rujiman.mediatracker.services.TMDBService;
import com.rujiman.mediatracker.services.AnilistService;
import com.rujiman.mediatracker.services.GameService;
import com.rujiman.mediatracker.services.PlanService;
import com.rujiman.mediatracker.services.StreamingLinkResolver;
import com.rujiman.mediatracker.services.ReviewService;
import com.rujiman.mediatracker.services.SeasonService;
import javafx.scene.Node;
import com.rujiman.mediatracker.models.Review;
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
    // JavaFX.
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
    @FXML private Label jumpToEpisodeLabel;

    // Rediseño "ficha": banda superior, progreso y acción principal
    @FXML private StackPane heroBand;
    @FXML private ImageView heroBackdrop;
    @FXML private StackPane posterBox;
    @FXML private Label typeBadge;
    @FXML private VBox progressBox;
    @FXML private Label progressCountLabel;
    @FXML private StackPane progressTrack;
    @FXML private javafx.scene.layout.Region progressFill;
    @FXML private Button primaryActionButton;
    @FXML private Button inProgressButton;

    /** Bloque de episodios visible (0 = 1–50, 1 = 51–100...) en series largas. */
    private int episodeBlock = -1;
    // Temporadas de la serie (TMDB), o null si no tiene o solo una
    private SeasonService.Layout seasonLayout = null;
    // Temporada visible en la cuadrícula (-1 = la del siguiente sin ver)
    private int seasonIdx = -1;
    private static final int EPISODE_BLOCK = 50;

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
    @FXML private VBox reviewSection;

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


    /**
     * Devuelve el hex de acento de la sección del item, según la paleta
     * "Constelaciones" de theme.css. Se usa para colorear dinámicamente
     * elementos del detalle (título, botón de favorito, tráiler, cabecera
     * de episodios) según el tipo del item que se está viendo en cada
     * momento, en vez de usar siempre el mismo color de marca para todo.
     */
    /** @deprecated Usar {@link MediaTypeTheme#accentHex(MediaType)} directamente. */
    @Deprecated
    /**
     * Banda superior: fondo = color del tipo mezclado con el fondo de la app,
     * badge del tipo, botón "Volver" con icono y póster desenfocado detrás.
     */
    private void styleHero(MediaItem item) {
        String accent = MediaTypeTheme.accentHex(item.getType());
        Color tint = Color.web(Palette.BG).interpolate(Color.web(accent), 0.16);
        heroBand.setStyle("-fx-background-color: " + toHex(tint) + ";");

        typeBadge.setText(MediaTypeTheme.label(item.getType()).toUpperCase());
        typeBadge.setStyle("-fx-background-color: " + accent + "; -fx-text-fill: " + Palette.ON_ACCENT + ";");

        backButton.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.BACK, 18, Palette.TEXT_SOFT));
        backButton.setGraphicTextGap(4);

        // Fondo: el póster muy desenfocado y oscurecido, a todo el ancho
        javafx.scene.effect.ColorAdjust darken = new javafx.scene.effect.ColorAdjust(0, -0.1, -0.35, 0);
        darken.setInput(new javafx.scene.effect.GaussianBlur(50));
        heroBackdrop.setEffect(darken);
        heroBackdrop.fitWidthProperty().bind(heroBand.widthProperty().multiply(1.1));
        heroBackdrop.fitHeightProperty().bind(heroBand.heightProperty().multiply(1.3));
        heroBackdrop.layoutXProperty().bind(heroBand.widthProperty().multiply(-0.05));
        heroBackdrop.layoutYProperty().bind(heroBand.heightProperty().multiply(-0.15));
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(heroBand.widthProperty());
        clip.heightProperty().bind(heroBand.heightProperty());
        heroBand.setClip(clip);

        javafx.scene.shape.Rectangle posterClip = new javafx.scene.shape.Rectangle(240, 360);
        posterClip.setArcWidth(36);
        posterClip.setArcHeight(36);
        detailImage.setClip(posterClip);
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    private void fadeTo(javafx.scene.Node n, double to) {
        FadeTransition f = new FadeTransition(com.rujiman.mediatracker.ui.Motion.BASE, n);
        f.setToValue(to);
        f.setInterpolator(com.rujiman.mediatracker.ui.Motion.EASE_OUT);
        f.play();
    }

    /** Estilo del botón de acción principal: relleno del color del tipo. */
    private String primaryStyle(MediaType type) {
        return "-fx-background-color: " + MediaTypeTheme.accentHex(type) + "; -fx-text-fill: " + Palette.ON_ACCENT + ";"
                + "-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 15px; -fx-background-radius: 14;"
                + "-fx-padding: 0 22; -fx-min-height: 48; -fx-cursor: hand;";
    }

    @FXML
    public void initialize() {
        detailScroll.setStyle("-fx-background-color: " + Palette.BG + ";");
        detailContainer.setStyle("-fx-background-color: " + Palette.BG + ";");

        // El viewport interno del ScrollPane no hereda el color de fondo por
        // CSS normal, y su Skin no existe hasta que el nodo está realmente
        // en una escena. Un solo Platform.runLater no es fiable porque puede
        // ejecutarse antes de que el Skin se construya. En su lugar, forzamos
        // el estilo cada vez que cambia el Skin (se dispara de forma fiable
        // en cuanto el ScrollPane es renderizable).
        CollectionUI.darkViewport(detailScroll);
    }

    /**
     * Cargar detalles de un MediaItem
     */
    public void loadItem(MediaItem item) {
        this.currentItem = item;

        // Si este título ya tenía progreso de antes (sin ficha), se la
        // guardamos ahora para que aparezca en las listas de seguimiento.
        WatchProgressService.rememberIfMissing(item);

        // Por defecto, asumimos que el detalle NO viene de una lista de
        // plan; si sí viene, quien abre este detalle (SearchController)
        // llamará a setPlanContext() justo después de loadItem().
        this.currentPlanContext = null;
        this.currentPlanItemId = null;
        this.episodeBlock = -1;
        this.seasonIdx = -1;
        this.seasonLayout = null;

        // Banda superior teñida con el color del tipo
        styleHero(item);

        // Póster (recortado, sin deformar) y el mismo póster, desenfocado,
        // de fondo de la banda superior
        detailImage.setImage(null);
        heroBackdrop.setImage(null);
        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            try {
                Image img = new Image(item.getImageUrl(), 480, 720, true, true, true);
                detailImage.setOpacity(0);
                heroBackdrop.setOpacity(0);
                detailImage.setImage(img);
                heroBackdrop.setImage(img);
                Runnable onLoaded = () -> {
                    CollectionUI.cropToFill(detailImage, img, 240, 360);
                    fadeTo(detailImage, 1);
                    fadeTo(heroBackdrop, 0.45);
                };
                if (img.getProgress() >= 1.0) onLoaded.run();
                else img.progressProperty().addListener((o, a, b) -> {
                    if (b.doubleValue() >= 1.0 && !img.isError()) onLoaded.run();
                });
            } catch (Exception ignored) {}
        }

        // Información básica
        detailTitle.setText(item.getTitle());
        // El título se pinta con el color de acento de SU sección (cian
        // para Juegos, violeta para Series, magenta para Anime, etc.),
        // así el detalle "se siente" de esa sección concreta en vez de
        // usar siempre el mismo rosa para todo tipo de contenido.
        // Título en blanco (el color del tipo ya está en el badge y la banda)
        detailTitle.setStyle(null);

        detailYear.setText(item.getYear() != null ? String.valueOf(item.getYear()) : "—");

        // Puntuación
        int score = item.getScore() != null ? item.getScore() : 0;
        detailScore.setText(score > 0 ? "Nota " + score + "/100" : "Sin puntuación");

        // Descripción: la cabecera se pinta con el acento de la sección
        // del item, igual que el título principal, para mantener
        // consistencia visual (antes se quedaba siempre en rosa fijo).
        // Se limpia cualquier resto de HTML (<br>, <i>, &quot;...) que traiga la API
        String description = com.rujiman.mediatracker.util.TextUtil.cleanHtml(item.getDescription());
        detailDescription.setText(description != null && !description.isBlank() ? description : "Sin descripción.");
        descriptionHeaderLabel.setStyle("-fx-text-fill: " + MediaTypeTheme.accentHex(item.getType()) + "; -fx-font-size: 14px; -fx-font-weight: bold;");

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

        // Plataformas: lo que ya venga en el item se pinta de inmediato.
        // Para SERIES/MOVIE, si llega vacío (caso típico de abrir una
        // recomendación, ver fetchPlatformsIfMissing), se completa
        // aparte en segundo plano sin bloquear el resto del detalle.
        renderPlatformsLabel(item);
        fetchPlatformsIfMissing(item);

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

        // Tu reseña: texto libre debajo de la cabecera
        setupReviewSection(item);

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
     * Pinta (o esconde) el label "Plataformas: ...", según lo que el
     * item tenga en ese momento en getPlatforms(). Separado de loadItem()
     * porque también se vuelve a llamar desde fetchPlatformsIfMissing()
     * una vez llega la respuesta de TMDB en segundo plano.
     */
    private void renderPlatformsLabel(MediaItem item) {
        if (item.getPlatforms() != null && !item.getPlatforms().isEmpty()) {
            // En libros y manga "platforms" guarda los autores (y en libros,
            // además, el número de páginas): se pinta como línea de autoría.
            if (MediaTypeTheme.isReadable(item.getType())) {
                detailPlatforms.setText("✍️ " + String.join(" · ", item.getPlatforms()));
            } else {
                detailPlatforms.setText("Plataformas: " + String.join(", ", item.getPlatforms()));
            }
            detailPlatforms.setVisible(true);
        } else {
            detailPlatforms.setVisible(false);
        }
    }

    /**
     * Estado intermedio mientras fetchPlatformsIfMissing() espera la
     * respuesta de TMDB: en vez de dejar el label oculto (lo que da la
     * sensación de que la recomendación "no tiene" plataformas durante
     * ese par de segundos), se muestra un aviso de carga, visualmente
     * igual al label final pero en gris/atenuado para distinguirlo del
     * resultado real.
     */
    private void renderPlatformsLoading() {
        detailPlatforms.setText("Plataformas: buscando...");
        detailPlatforms.setStyle("-fx-text-fill: " + Palette.DIM + ";");
        detailPlatforms.setVisible(true);
    }

    /**
     * Las plataformas de SERIES/MOVIE normalmente ya llegan rellenas
     * desde la búsqueda (TMDBService.parseSeries/parseMovies llaman a
     * getWatchProviders() ahí mismo). Pero los MediaItem que llegan como
     * tarjeta de "Recomendaciones" (TMDBService.getRecommendations) NO
     * pasan por ese mismo parseo y se quedan con platforms=null —
     * síntoma: al abrir una recomendación nunca aparece el label de
     * plataformas ni el botón dice "Ver ahora", aunque la serie/película
     * sí esté disponible en streaming según la propia web de TMDB.
     *
     * Aquí se completa ese hueco bajo demanda, igual que ya se hace con
     * el tráiler o el número de episodios: si el item no trae platforms
     * y sí tiene tmdbId, se pide en un hilo de fondo y, a la vuelta, se
     * actualizan tanto el label como el texto del botón "Ver ahora"/
     * "Abrir en navegador" (que dependen de este dato). Mientras se
     * espera la respuesta, el label muestra "buscando..." en vez de
     * quedarse oculto, para que no parezca que el dato simplemente no
     * existe (ver renderPlatformsLoading()).
     *
     * No se repite la llamada si el item ya trae platforms (búsqueda
     * directa) ni si no es SERIES/MOVIE (ANIME/GAME/MUSIC no usan
     * getWatchProviders en absoluto).
     */
    private void fetchPlatformsIfMissing(MediaItem item) {
        boolean alreadyHasPlatforms = item.getPlatforms() != null && !item.getPlatforms().isEmpty();
        boolean canFetch = (item.getType() == MediaType.MOVIE || item.getType() == MediaType.SERIES)
                && item.getTmdbId() != null;

        if (alreadyHasPlatforms || !canFetch) return;

        renderPlatformsLoading();

        final MediaItem itemAtRequestTime = item;
        boolean isMovie = item.getType() == MediaType.MOVIE;
        int tmdbId = item.getTmdbId();

        new Thread(() -> {
            List<String> platforms = new TMDBService().getWatchProviders(tmdbId, isMovie);

            Platform.runLater(() -> {
                // Si el usuario ya cambió de detalle mientras esperábamos
                // la respuesta, no pintamos nada sobre la pantalla actual.
                if (currentItem != itemAtRequestTime) return;

                // Quitamos el estilo gris de "buscando..." tanto si hay
                // resultado como si no, para no dejarlo aplicado por
                // error sobre un futuro label con datos reales.
                detailPlatforms.setStyle(null);

                if (platforms != null && !platforms.isEmpty()) {
                    itemAtRequestTime.setPlatforms(platforms);
                    renderPlatformsLabel(itemAtRequestTime);
                    setupOpenExternalButton(itemAtRequestTime);
                } else {
                    // TMDB no tiene ninguna plataforma para este título:
                    // ocultamos el label en vez de dejar "buscando..."
                    // colgado para siempre.
                    detailPlatforms.setVisible(false);
                }
            });
        }).start();
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
        trailerButton.setText("Ver tráiler");
        trailerButton.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.PLAY, 16, Palette.TEXT));
        trailerButton.setGraphicTextGap(8);
        trailerButton.setDisable(false);
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
        com.rujiman.mediatracker.util.Browser.open("https://www.youtube.com/watch?v=" + youtubeKey);
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
                        ((type == MediaType.ANIME || type == MediaType.MANGA) && item.getAnilistId() != null) ||
                        (type == MediaType.GAME && item.getIgdbId() != null);

        if (!canFetch) return;

        final MediaItem itemAtRequestTime = item;

        new Thread(() -> {
            List<MediaItem> recommendations;

            switch (type) {
                case MOVIE -> recommendations = new TMDBService().getRecommendations(item.getTmdbId(), true);
                case SERIES -> recommendations = new TMDBService().getRecommendations(item.getTmdbId(), false);
                case ANIME -> recommendations = new AnilistService().getRecommendations(item.getAnilistId());
                case MANGA -> recommendations = new AnilistService().getRecommendations(item.getAnilistId(), MediaType.MANGA);
                case GAME -> recommendations = new GameService().getSimilarGames(item.getIgdbId());
                case BOOK -> recommendations = new java.util.ArrayList<>();
                default -> recommendations = List.of();
            }

            final List<MediaItem> results = recommendations;

            Platform.runLater(() -> {
                // Si el usuario ya cerró este detalle o abrió otro mientras
                // se esperaba la respuesta, no pintamos nada para evitar
                // mezclar recomendaciones del item equivocado.
                if (currentItem != itemAtRequestTime) return;
                if (results == null || results.isEmpty()) return;

                recommendationsHeaderLabel.setText("Si te gusta " + itemAtRequestTime.getTitle() + ", también te gustará");
                recommendationsHeaderLabel.setStyle(null);

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
        return MediaTypeTheme.cardHoverClass(type);
    }

    /**
     * Construye las 5 estrellas clicables para la valoración personal del
     * usuario. Cada estrella, al pincharla, guarda esa puntuación (1-5) en
     * WatchProgressService, totalmente aparte de favoritos y del score
     * que viene de las APIs externas.
     */
    private void setupUserRatingStars(MediaItem item) {
        userRatingStars.getChildren().clear();

        Integer currentRating = WatchProgressService.getUserRating(WatchProgressService.keyFor(item.getType(), item.getTitle()));
        int rating = currentRating != null ? currentRating : 0;

        for (int i = 1; i <= 5; i++) {
            final int starValue = i;

            Label star = new Label(starValue <= rating ? "★" : "☆");
            star.setStyle(
                    "-fx-font-size: 22px;" +
                            // Estrellas del color de la categoría (ámbar solo en películas)
                            "-fx-text-fill: " + (starValue <= rating ? MediaTypeTheme.accentHex(item.getType()) : "#4a4170") + ";" +
                            "-fx-cursor: hand;"
            );

            star.setOnMouseClicked(e -> {
                Integer existing = WatchProgressService.getUserRating(WatchProgressService.keyFor(item.getType(), item.getTitle()));
                int existingValue = existing != null ? existing : 0;

                // Pinchar la misma estrella que ya estaba puesta como máxima
                // quita la valoración (toggle), igual que en apps como
                // Letterboxd; pinchar cualquier otra la establece directamente.
                int newRating = (starValue == existingValue) ? 0 : starValue;

                WatchProgressService.setUserRating(WatchProgressService.keyFor(item.getType(), item.getTitle()), newRating > 0 ? newRating : null);
                WatchProgressService.remember(item);
                setupUserRatingStars(item); // repintar con el nuevo estado
            });

            userRatingStars.getChildren().add(star);
        }
    }

    // ── Reseña personal ─────────────────────────────────────────────

    private static final java.time.format.DateTimeFormatter REVIEW_DATE =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es"));

    /** Muestra la reseña guardada, o la invitación a escribirla. */
    private void setupReviewSection(MediaItem item) {
        renderReview(item, false);
    }

    private void renderReview(MediaItem item, boolean editing) {
        if (reviewSection == null) return;
        reviewSection.getChildren().clear();
        Review review = ReviewService.get(item.getType(), item.getTitle());

        Label title = new Label("Tu reseña");
        title.getStyleClass().add("detail-section-title");
        Label privacy = new Label("Solo la ves tú");
        privacy.getStyleClass().add("detail-hint");
        HBox header = new HBox(12, title, privacy);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14);
        card.setPadding(new javafx.geometry.Insets(18, 20, 18, 20));
        card.setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 16;"
                + "-fx-border-color: " + Palette.LINE + "; -fx-border-radius: 16;");

        if (editing) {
            card.getChildren().addAll(reviewEditor(item, review));
        } else if (review == null) {
            Label empty = new Label("¿Qué te ha parecido? Apunta lo que quieras recordar: lo mejor, lo peor, "
                    + "a quién se lo recomendarías…");
            empty.setWrapText(true);
            empty.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.DIM + ";");
            Button write = new Button("Escribir reseña");
            write.getStyleClass().add("btn-secondary-sm");
            write.setOnAction(e -> renderReview(item, true));
            card.getChildren().addAll(empty, write);
        } else {
            Label text = new Label(review.text);
            text.setWrapText(true);
            text.setLineSpacing(3);
            text.setStyle("-fx-font-size: 15px; -fx-text-fill: " + Palette.TEXT_SOFT + ";");

            HBox footer = new HBox(10);
            footer.setAlignment(Pos.CENTER_LEFT);
            if (review.spoilers) footer.getChildren().add(spoilerTag(item.getType()));
            Label date = new Label(reviewDateText(review));
            date.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";");
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Button edit = new Button("Editar");
            edit.getStyleClass().add("btn-secondary-sm");
            edit.setOnAction(e -> renderReview(item, true));
            footer.getChildren().addAll(date, spacer, edit);

            card.getChildren().addAll(text, footer);
            // Doble clic en el texto también lo edita
            text.setOnMouseClicked(e -> { if (e.getClickCount() == 2) renderReview(item, true); });
        }

        reviewSection.getChildren().addAll(header, card);
    }

    /** Cuadro de texto + "Contiene spoilers" + Guardar / Cancelar / Borrar. */
    private java.util.List<javafx.scene.Node> reviewEditor(MediaItem item, Review review) {
        TextArea area = new TextArea(review != null ? review.text : "");
        area.getStyleClass().add("review-area");
        area.setWrapText(true);
        area.setPrefRowCount(5);
        area.setPromptText("Escribe tu reseña…");
        area.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= ReviewService.MAX_LENGTH ? change : null));

        Label counter = new Label();
        counter.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";");
        Runnable updateCounter = () -> counter.setText(area.getLength() + " / " + ReviewService.MAX_LENGTH);
        updateCounter.run();

        CheckBox spoilers = new CheckBox("Contiene spoilers");
        spoilers.getStyleClass().add("review-check");
        spoilers.setSelected(review != null && review.spoilers);

        Button save = new Button("Guardar");
        save.setStyle("-fx-background-color: " + MediaTypeTheme.accentHex(item.getType()) + ";"
                + "-fx-text-fill: " + Palette.ON_ACCENT + "; -fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px;"
                + "-fx-background-radius: 10; -fx-padding: 0 18; -fx-min-height: 38; -fx-cursor: hand;");
        save.setDisable(area.getText().isBlank());
        area.textProperty().addListener((o, a, b) -> {
            updateCounter.run();
            save.setDisable(b == null || b.isBlank());
        });
        Runnable doSave = () -> {
            if (area.getText().isBlank()) return;
            ReviewService.save(item, area.getText(), spoilers.isSelected());
            renderReview(item, false);
        };
        save.setOnAction(e -> doSave.run());

        Button cancel = new Button("Cancelar");
        cancel.getStyleClass().add("btn-secondary-sm");
        cancel.setOnAction(e -> renderReview(item, false));

        // Ctrl+Enter guarda, Esc cancela
        area.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && e.isShortcutDown()) { doSave.run(); e.consume(); }
            else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) { renderReview(item, false); e.consume(); }
        });

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox actions = new HBox(10, spoilers, spacer, counter);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (review != null) {
            Button delete = new Button("Borrar");
            delete.getStyleClass().add("btn-danger-sm");
            delete.setOnAction(e -> confirmDeleteReview(item));
            actions.getChildren().add(delete);
        }
        actions.getChildren().addAll(cancel, save);

        Platform.runLater(() -> { area.requestFocus(); area.end(); });
        return java.util.List.of(area, actions);
    }

    private void confirmDeleteReview(MediaItem item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (reviewSection.getScene() != null) alert.initOwner(reviewSection.getScene().getWindow());
        alert.setTitle("Borrar reseña");
        alert.setHeaderText(null);
        alert.setContentText("¿Borrar tu reseña de \"" + item.getTitle() + "\"?\nTus estrellas se conservan.");
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm());
        pane.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");
        pane.lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: " + Palette.TEXT + ";"));
        pane.sceneProperty().addListener((o, a, s) -> { if (s != null) s.setFill(Color.web(Palette.BG)); });
        alert.showAndWait().ifPresent(b -> {
            if (b == ButtonType.OK) {
                ReviewService.delete(item.getType(), item.getTitle());
                renderReview(item, false);
            }
        });
    }

    private Label spoilerTag(MediaType type) {
        String accent = MediaTypeTheme.accentHex(type);
        Label tag = new Label("Spoilers");
        tag.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 11px; -fx-text-fill: " + accent + ";"
                + "-fx-border-color: " + accent + "; -fx-border-radius: 999; -fx-padding: 2 9;");
        return tag;
    }

    private static String reviewDateText(Review r) {
        try {
            java.time.LocalDate created = java.time.Instant.parse(r.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            java.time.LocalDate updated = r.updatedAt != null
                    ? java.time.Instant.parse(r.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : created;
            return updated.isAfter(created) ? "Editada el " + REVIEW_DATE.format(updated)
                                            : "Escrita el " + REVIEW_DATE.format(created);
        } catch (Exception e) {
            return "";
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

        if (MediaTypeTheme.hasUnitProgress(type)) {
            // ANIME/SERIES: episodios. MANGA: capítulos (mismo sistema).
            jumpToEpisodeLabel.setText(type == MediaType.MANGA
                    ? "Ya he leído hasta el capítulo" : "Ya he visto hasta el episodio");
            episodesHeaderLabel.setStyle("-fx-text-fill: " + MediaTypeTheme.accentHex(type) + "; -fx-font-size: 14px; -fx-font-weight: bold;");

            // Series de TMDB: si ya conocemos sus temporadas, se usan al momento
            boolean tmdbSeries = type == MediaType.SERIES && item.getTmdbId() != null;
            if (tmdbSeries) {
                seasonLayout = SeasonService.layout(item.getTmdbId());
                if (seasonLayout != null) item.setEpisodes(seasonLayout.total());
            }

            Integer total = item.getEpisodes();
            if (total != null && total > 0) {
                buildEpisodesList(total);
                episodesSeparator.setVisible(true);
                episodesSeparator.setManaged(true);
                episodesSection.setVisible(true);
                episodesSection.setManaged(true);
            }
            // Las series de TMDB no traen episodios ni temporadas en la
            // búsqueda (para no ralentizarla): se piden aquí, en un hilo de
            // fondo, si faltan o tienen más de un día.
            if (tmdbSeries && (total == null || total <= 0 || SeasonService.needsRefresh(item.getTmdbId()))) {
                loadSeasonsInBackground(item);
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
     * Pide a TMDB las temporadas de la serie en un hilo de fondo (y, si no
     * las da, solo el número de episodios). Al llegar, si seguimos en la
     * misma ficha, guarda las temporadas y vuelve a pintar los episodios.
     */
    private void loadSeasonsInBackground(MediaItem item) {
        final MediaItem itemAtRequestTime = item;
        final int tmdbId = item.getTmdbId();

        Thread t = new Thread(() -> {
            TMDBService tmdb = new TMDBService();
            List<SeasonService.Season> seasons = tmdb.getSeasons(tmdbId);
            Integer total = null;
            if (seasons != null && !seasons.isEmpty()) {
                total = seasons.stream().mapToInt(SeasonService.Season::episodes).sum();
            } else {
                total = tmdb.getEpisodeCount(tmdbId);
            }
            final Integer finalTotal = total;

            Platform.runLater(() -> {
                if (seasons != null) SeasonService.store(tmdbId, seasons);
                // Si el usuario ya abrió otra ficha, no se pinta nada aquí
                if (currentItem != itemAtRequestTime) return;
                if (finalTotal == null || finalTotal <= 0) return;

                boolean wasHidden = !episodesSection.isVisible();
                seasonLayout = SeasonService.layout(tmdbId);
                item.setEpisodes(finalTotal);
                buildEpisodesList(finalTotal);

                episodesSeparator.setVisible(true);
                episodesSeparator.setManaged(true);
                episodesSection.setVisible(true);
                episodesSection.setManaged(true);

                // Con lista de episodios, el progreso va episodio a episodio
                viewedButton.setVisible(false);
                viewedButton.setManaged(false);

                if (wasHidden) {
                    FadeTransition fade = new FadeTransition(Duration.millis(250), episodesSection);
                    fade.setFromValue(0);
                    fade.setToValue(1);
                    fade.play();
                }
            });
        }, "temporadas");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Pinta la cuadrícula de episodios. Si la serie tiene varias temporadas
     * (SeasonService), arriba salen chips "T1 ✓", "T2 3/8"... y la
     * cuadrícula muestra solo la temporada elegida, numerada desde E1.
     * Internamente todo sigue con la numeración seguida 1..N.
     * Temporadas (o series) de más de 50 se parten en bloques de 50.
     */
    private void buildEpisodesList(int totalEpisodes) {
        episodesList.getChildren().clear();
        MediaType type = currentItem.getType();
        String accent = MediaTypeTheme.accentHex(type);
        WatchProgressService.Progress progress = WatchProgressService.getProgress(WatchProgressService.keyFor(type, currentItem.getTitle()));
        episodesHeaderLabel.setText(MediaTypeTheme.unitPlural(type) + " (" + progress.watchedEpisodes.size() + "/" + totalEpisodes + ")");

        SeasonService.Layout layout = seasonLayout != null && seasonLayout.total() == totalEpisodes ? seasonLayout : null;

        // Tramo visible: la temporada elegida, o todo si no hay temporadas
        int rangeStart = 1, rangeEnd = totalEpisodes;
        if (layout != null) {
            if (seasonIdx < 0 || seasonIdx >= layout.size()) {
                int next = nextUnwatched(totalEpisodes);
                seasonIdx = next > 0 ? layout.seasonOf(next) : layout.size() - 1;
            }
            rangeStart = layout.start(seasonIdx);
            rangeEnd = layout.end(seasonIdx);
            episodesList.getChildren().add(buildSeasonChips(layout, progress, accent, totalEpisodes));
        }
        final int fromEp = rangeStart, toEp = rangeEnd;

        // Textos que cambian con o sin temporadas
        String unit = MediaTypeTheme.unitSingular(type).toLowerCase();
        markAllWatchedButton.setText(layout != null ? "Marcar temporada" : "Marcar todos");
        unmarkAllWatchedButton.setText(layout != null ? "Desmarcar temporada" : "Desmarcar todos");
        if (layout != null) {
            jumpToEpisodeLabel.setText("De la T" + layout.season(seasonIdx).number() + ", ya he visto hasta el episodio");
        } else {
            jumpToEpisodeLabel.setText(type == MediaType.MANGA ? "Ya he leído hasta el capítulo" : "Ya he visto hasta el episodio");
        }

        // Bloques de 50 dentro del tramo. Por defecto, el del siguiente sin ver.
        int rangeSize = rangeEnd - rangeStart + 1;
        int blocks = (rangeSize + EPISODE_BLOCK - 1) / EPISODE_BLOCK;
        if (episodeBlock < 0 || episodeBlock >= blocks) {
            int next = nextUnwatchedIn(rangeStart, rangeEnd);
            episodeBlock = next > 0 ? (next - rangeStart) / EPISODE_BLOCK : 0;
        }
        if (blocks > 1) {
            FlowPane tabs = new FlowPane(8, 8);
            for (int bIdx = 0; bIdx < blocks; bIdx++) {
                final int block = bIdx;
                int from = bIdx * EPISODE_BLOCK + 1;
                int to = Math.min(rangeSize, (bIdx + 1) * EPISODE_BLOCK);
                Button tab = new Button(from + "–" + to);
                boolean active = bIdx == episodeBlock;
                tab.setStyle("-fx-background-radius: 999; -fx-padding: 6 14; -fx-font-size: 13px; -fx-cursor: hand;"
                        + (active ? "-fx-background-color: " + Palette.TEXT + "; -fx-text-fill: " + Palette.ON_ACCENT + "; -fx-font-family: 'Inter SemiBold';"
                                  : "-fx-background-color: transparent; -fx-border-color: " + Palette.LINE_STRONG + "; -fx-border-radius: 999; -fx-text-fill: " + Palette.TEXT_SOFT + ";"));
                tab.setOnAction(e -> {
                    episodeBlock = block;
                    buildEpisodesList(totalEpisodes);
                });
                tabs.getChildren().add(tab);
            }
            episodesList.getChildren().add(tabs);
        }

        // Cuadrícula de botones numerados: relleno del color del tipo = visto
        FlowPane grid = new FlowPane(8, 8);
        int start = fromEp + episodeBlock * EPISODE_BLOCK;
        int end = Math.min(toEp, start + EPISODE_BLOCK - 1);
        for (int i = start; i <= end; i++) {
            final int episodeNumber = i;
            boolean watched = progress.watchedEpisodes.contains(i);
            int shown = i - fromEp + 1;
            Button ep = new Button(String.valueOf(shown));
            ep.setMinSize(56, 44);
            ep.setPrefSize(56, 44);
            ep.setStyle("-fx-background-radius: 12; -fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand;"
                    + (watched ? "-fx-background-color: " + accent + "; -fx-text-fill: " + Palette.ON_ACCENT + ";"
                               : "-fx-background-color: " + Palette.PANEL + "; -fx-border-color: " + Palette.LINE + "; -fx-border-radius: 12; -fx-text-fill: " + Palette.TEXT + ";"));
            String name = layout != null ? layout.label(i) : MediaTypeTheme.unitSingular(type) + " " + i;
            ep.setTooltip(new Tooltip(name + (watched ? " · marcado" : "") + "\nMayús + clic: marcar hasta aquí"));
            // Clic normal: marcar/desmarcar este episodio
            ep.setOnAction(e -> {
                onEpisodeToggled(episodeNumber, totalEpisodes);
                buildEpisodesList(totalEpisodes);
            });
            // Mayús + clic: marcar todos hasta este (también las temporadas
            // anteriores). Los botones de JavaFX IGNORAN las pulsaciones con
            // Mayús/Ctrl/Alt (no disparan onAction), así que se escucha el
            // clic del ratón directamente.
            ep.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                if (e.isShiftDown() && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    WatchProgressService.markEpisodesUpTo(WatchProgressService.keyFor(type, currentItem.getTitle()), episodeNumber, totalEpisodes);
                    WatchProgressService.remember(currentItem);
                    updateViewedButtonStyle();
                    buildEpisodesList(totalEpisodes);
                    e.consume();
                }
            });
            grid.getChildren().add(ep);
        }
        episodesList.getChildren().add(grid);
        updatePrimaryAction();
    }

    /** Chips "T1 ✓", "T2 3/8", "T3"... con el progreso de cada temporada. */
    private Node buildSeasonChips(SeasonService.Layout layout, WatchProgressService.Progress progress,
                                  String accent, int totalEpisodes) {
        FlowPane chips = new FlowPane(8, 8);
        for (int s = 0; s < layout.size(); s++) {
            final int idx = s;
            int count = layout.season(s).episodes();
            int done = 0;
            for (int i = layout.start(s); i <= layout.end(s); i++) if (progress.watchedEpisodes.contains(i)) done++;
            boolean complete = done == count;
            boolean active = s == seasonIdx;

            Button chip = new Button("T" + layout.season(s).number());
            String countText = complete ? "✓" : done > 0 ? done + "/" + count : String.valueOf(count);
            Label countLabel = new Label(countText);
            countLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: "
                    + (active ? Palette.ON_ACCENT : complete ? accent : Palette.DIM) + ";"
                    + (complete ? "-fx-font-weight: bold;" : ""));
            chip.setGraphic(countLabel);
            chip.setContentDisplay(ContentDisplay.RIGHT);
            chip.setGraphicTextGap(8);
            chip.setStyle("-fx-background-radius: 999; -fx-border-radius: 999; -fx-min-height: 36; -fx-padding: 0 14;"
                    + "-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-cursor: hand;"
                    + (active ? "-fx-background-color: " + Palette.TEXT + "; -fx-text-fill: " + Palette.ON_ACCENT + "; -fx-border-color: " + Palette.TEXT + ";"
                              : "-fx-background-color: transparent; -fx-text-fill: " + Palette.TEXT_SOFT + "; -fx-border-color: "
                                + (complete ? accent + "88" : Palette.LINE_STRONG) + ";"));
            String name = layout.season(s).name();
            chip.setTooltip(new Tooltip((name == null || name.isBlank() ? "Temporada " + layout.season(s).number() : name)
                    + " · " + count + " episodios"));
            chip.setOnAction(e -> {
                seasonIdx = idx;
                episodeBlock = -1;
                buildEpisodesList(totalEpisodes);
            });
            chips.getChildren().add(chip);
        }
        return chips;
    }

    /** Primer número (seguido) sin ver dentro del tramo, o -1. */
    private int nextUnwatchedIn(int from, int to) {
        WatchProgressService.Progress p = WatchProgressService.getProgress(
                WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()));
        for (int i = from; i <= to; i++) if (!p.watchedEpisodes.contains(i)) return i;
        return -1;
    }

    /** Tramo (from, to) que afectan "Marcar/Desmarcar": la temporada visible, o todo. */
    private int[] visibleSeasonRange(int total) {
        if (seasonLayout != null && seasonLayout.total() == total && seasonIdx >= 0 && seasonIdx < seasonLayout.size()) {
            return new int[]{seasonLayout.start(seasonIdx), seasonLayout.end(seasonIdx)};
        }
        return null;
    }

    /**
     * Se llama cuando el usuario marca/desmarca un episodio concreto.
     * Esto NO añade el item a favoritos; el progreso vive en su propio
     * almacenamiento, totalmente independiente.
     */
    private void onEpisodeToggled(int episodeNumber, int totalEpisodes) {
        WatchProgressService.toggleEpisodeWatched(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()), episodeNumber, totalEpisodes);
        WatchProgressService.remember(currentItem);

        WatchProgressService.Progress progress = WatchProgressService.getProgress(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()));
        episodesHeaderLabel.setText(MediaTypeTheme.unitPlural(currentItem.getType()) + " (" + progress.watchedEpisodes.size() + "/" + totalEpisodes + ")");

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

        int[] season = visibleSeasonRange(total);
        if (season != null) {
            WatchProgressService.setEpisodesWatched(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()),
                    season[0], season[1], true, total);
        } else {
            WatchProgressService.markAllEpisodesWatched(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()), total);
        }
        WatchProgressService.remember(currentItem);
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

        int[] season = visibleSeasonRange(total);
        if (season != null) {
            WatchProgressService.setEpisodesWatched(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()),
                    season[0], season[1], false, total);
        } else {
            WatchProgressService.unmarkAllEpisodesWatched(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()), total);
        }
        WatchProgressService.remember(currentItem);
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

        // Con temporadas, el número es de la temporada visible: "hasta el 4
        // de la T2" marca toda la T1 y la T2 hasta el 4
        int[] season = visibleSeasonRange(total);
        if (season != null && targetEpisode > 0) {
            targetEpisode = season[0] + Math.min(targetEpisode, season[1] - season[0] + 1) - 1;
        }

        WatchProgressService.markEpisodesUpTo(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()), targetEpisode, total);
        WatchProgressService.remember(currentItem);
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
            case BOOK, MANGA -> viewedLabel = "Marcar como leído";
        }
        viewedButton.setText(viewedLabel);
        updatePrimaryAction();

        boolean hasEpisodeList = MediaTypeTheme.hasUnitProgress(item.getType())
                && item.getEpisodes() != null && item.getEpisodes() > 0;

        viewedButton.setVisible(!hasEpisodeList);
        viewedButton.setManaged(!hasEpisodeList);
    }

    /**
     * Verificar si el item está en favoritos
     */
    private void checkIfFavorite() {
        boolean isFav = FavoritesService.isFavorite(currentItem.getTitle(), currentItem.getType());
        String accentHex = MediaTypeTheme.accentHex(currentItem.getType());

        if (isFav) {
            // Buscar el favorito para saber su estado
            currentFavorite = null;
            List<FavoriteItem> favs = FavoritesService.getFavorites();
            for (FavoriteItem fav : favs) {
                if (fav.getTitle().equalsIgnoreCase(currentItem.getTitle()) && fav.getType() == currentItem.getType()) {
                    currentFavorite = fav;
                    break;
                }
            }
            // Estrella rellena del color del tipo = ya es favorito
            favoriteButton.setText(null);
            favoriteButton.setGraphic(com.rujiman.mediatracker.ui.Icons.filled(com.rujiman.mediatracker.ui.Icons.STAR, 20, accentHex));
            favoriteButton.setTooltip(new Tooltip("Quitar de favoritos"));
        } else {
            currentFavorite = null;
            favoriteButton.setText(null);
            favoriteButton.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.STAR, 20, Palette.TEXT));
            favoriteButton.setTooltip(new Tooltip("Añadir a favoritos"));
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
        if (type == MediaType.BOOK || type == MediaType.MANGA) return PlanService.ListKind.READ;
        return PlanService.ListKind.WATCH; // SERIES, MOVIE, ANIME
    }

    private String planButtonLabelFor(MediaType type) {
        return switch (listKindForType(type)) {
            case WATCH -> "Pienso ver esto";
            case PLAY -> "Pienso jugar esto";
            case LISTEN -> "Pienso escuchar esto";
            case READ -> "Pienso leer esto";
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

        String accent = MediaTypeTheme.accentHex(item.getType());
        planButton.setText(null);
        if (inPlan) {
            planButton.setGraphic(com.rujiman.mediatracker.ui.Icons.filled(com.rujiman.mediatracker.ui.Icons.BOOKMARK, 20, accent));
            planButton.setTooltip(new Tooltip("Quitar de \"" + planButtonLabelFor(item.getType()) + "\""));
        } else {
            planButton.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.BOOKMARK, 20, Palette.TEXT));
            planButton.setTooltip(new Tooltip(capitalize(planButtonLabelFor(item.getType()))));
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
        WatchProgressService.toggleViewed(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()));
        WatchProgressService.remember(currentItem);
        updateViewedButtonStyle();
    }

    private void updateViewedButtonStyle() {
        boolean viewed = WatchProgressService.isViewed(WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()));
        viewedButton.getStyleClass().remove("btn-secondary");
        if (viewed) {
            // Hecho: pasa a secundario con un check del color del tipo
            viewedButton.setText(MediaTypeTheme.doneText(currentItem.getType()).replace("✔ ", ""));
            viewedButton.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.CHECK, 16,
                    MediaTypeTheme.accentHex(currentItem.getType())));
            viewedButton.setGraphicTextGap(8);
            viewedButton.setStyle(null);
            viewedButton.getStyleClass().add("btn-secondary");
        } else {
            // Pendiente: es la acción principal (relleno del color del tipo)
            viewedButton.setText(viewedLabelFor(currentItem.getType()));
            viewedButton.setGraphic(null);
            viewedButton.setStyle(primaryStyle(currentItem.getType()));
        }
        updatePrimaryAction();
        updateInProgressButton();
    }

    /**
     * Botón "Estoy jugando / viendo / leyendo": solo para lo que no se
     * sigue por episodios (si hay lista de episodios, marcar uno ya lo
     * pone en curso). Activo = relleno sutil con icono de play.
     */
    private void updateInProgressButton() {
        boolean hasEpisodeList = MediaTypeTheme.hasUnitProgress(currentItem.getType())
                && currentItem.getEpisodes() != null && currentItem.getEpisodes() > 0;
        String key = WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle());
        boolean viewed = WatchProgressService.isViewed(key);
        boolean show = !hasEpisodeList && !viewed;
        inProgressButton.setVisible(show);
        inProgressButton.setManaged(show);
        if (!show) return;

        boolean active = WatchProgressService.isInProgress(key);
        String accent = MediaTypeTheme.accentHex(currentItem.getType());
        inProgressButton.setText(MediaTypeTheme.inProgressAction(currentItem.getType()));
        inProgressButton.setGraphic(com.rujiman.mediatracker.ui.Icons.of(
                active ? com.rujiman.mediatracker.ui.Icons.CHECK : com.rujiman.mediatracker.ui.Icons.PLAY, 16,
                active ? accent : Palette.TEXT));
        inProgressButton.setGraphicTextGap(8);
        inProgressButton.setStyle(active
                ? "-fx-border-color: " + accent + "; -fx-background-color: " + accent + "26;"
                : null);
        inProgressButton.setTooltip(new Tooltip(active
                ? "Está en «En curso». Pulsa para quitarlo"
                : "Añadir a «En curso» en tu biblioteca y en Inicio"));
    }

    @FXML
    private void onToggleInProgress() {
        String key = WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle());
        WatchProgressService.setInProgress(key, !WatchProgressService.isInProgress(key));
        WatchProgressService.remember(currentItem);
        updateViewedButtonStyle();
    }

    private String viewedLabelFor(MediaType type) {
        return switch (type) {
            case MUSIC -> "Marcar como escuchado";
            case GAME -> "Marcar como jugado";
            case BOOK, MANGA -> "Marcar como leído";
            default -> "Marcar como visto";
        };
    }

    // ===========================================================
    // ACCIÓN PRINCIPAL Y PROGRESO (anime / series / manga)
    // ===========================================================

    /** Primer episodio/capítulo sin marcar, o -1 si están todos. */
    private int nextUnwatched(int total) {
        WatchProgressService.Progress p = WatchProgressService.getProgress(
                WatchProgressService.keyFor(currentItem.getType(), currentItem.getTitle()));
        for (int i = 1; i <= total; i++) if (!p.watchedEpisodes.contains(i)) return i;
        return -1;
    }

    /**
     * Si el item tiene lista de episodios, la acción principal es "Marcar
     * episodio N" (el siguiente sin ver); al completarlos, "Completado".
     * También actualiza la barra de progreso de la banda superior.
     */
    private void updatePrimaryAction() {
        Integer total = currentItem.getEpisodes();
        boolean units = MediaTypeTheme.hasUnitProgress(currentItem.getType()) && total != null && total > 0;
        primaryActionButton.setVisible(units);
        primaryActionButton.setManaged(units);
        progressBox.setVisible(units);
        progressBox.setManaged(units);
        if (!units) return;

        MediaType type = currentItem.getType();
        String unit = MediaTypeTheme.unitSingular(type).toLowerCase();
        int next = nextUnwatched(total);
        if (next > 0) {
            boolean seasons = seasonLayout != null && seasonLayout.total() == total;
            primaryActionButton.setText("Marcar " + (seasons ? seasonLayout.label(next) : unit + " " + next));
            primaryActionButton.setGraphic(null);
            primaryActionButton.getStyleClass().remove("btn-secondary");
            primaryActionButton.setStyle(primaryStyle(type));
        } else {
            primaryActionButton.setText("Completado");
            primaryActionButton.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.CHECK, 16, MediaTypeTheme.accentHex(type)));
            primaryActionButton.setGraphicTextGap(8);
            primaryActionButton.setStyle(null);
            if (!primaryActionButton.getStyleClass().contains("btn-secondary")) primaryActionButton.getStyleClass().add("btn-secondary");
        }

        WatchProgressService.Progress p = WatchProgressService.getProgress(WatchProgressService.keyFor(type, currentItem.getTitle()));
        int done = p.watchedEpisodes.size();
        progressCountLabel.setText(done + " de " + total + " " + MediaTypeTheme.unitPlural(type).toLowerCase());
        progressFill.setStyle("-fx-background-color: " + MediaTypeTheme.accentHex(type) + ";");
        double width = progressTrack.getPrefWidth() * Math.min(1.0, (double) done / total);
        javafx.animation.Timeline grow = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                com.rujiman.mediatracker.ui.Motion.SLOW,
                new javafx.animation.KeyValue(progressFill.maxWidthProperty(), width, com.rujiman.mediatracker.ui.Motion.EASE_OUT),
                new javafx.animation.KeyValue(progressFill.prefWidthProperty(), width, com.rujiman.mediatracker.ui.Motion.EASE_OUT)));
        grow.play();
    }

    /** "Marcar episodio N": marca el siguiente sin ver. */
    @FXML
    private void onPrimaryAction() {
        Integer total = currentItem.getEpisodes();
        if (total == null || total <= 0) return;
        int next = nextUnwatched(total);
        if (next <= 0) return;
        onEpisodeToggled(next, total);
        // Se va a la temporada y al bloque del episodio recién marcado
        if (seasonLayout != null && seasonLayout.total() == total) {
            seasonIdx = seasonLayout.seasonOf(next);
            episodeBlock = (next - seasonLayout.start(seasonIdx)) / EPISODE_BLOCK;
        } else {
            episodeBlock = (next - 1) / EPISODE_BLOCK;
        }
        buildEpisodesList(total);
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
        Dialogs.styleDark(dialog.getDialogPane());
        Dialogs.darkenWindow(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(selection -> openExternalUrlDirect(options.get(selection)));
    }

    private void openExternalUrlDirect(String url) {
        com.rujiman.mediatracker.util.Browser.open(url);
    }

}