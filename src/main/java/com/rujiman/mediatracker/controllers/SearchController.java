package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.ui.CollectionUI;
import com.rujiman.mediatracker.ui.Dialogs;
import com.rujiman.mediatracker.ui.Palette;
import com.rujiman.mediatracker.ui.Layout;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.services.*;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SearchController {

    // -------------------------
    // SHELL: HOME / BÚSQUEDA
    // -------------------------
    @FXML private StackPane centerContent;
    @FXML private StackPane homeContainer;
    @FXML private VBox searchContentBox;
    @FXML private HBox searchBarBox;
    @FXML private HBox filterBarBox;

    private HomeController homeController;

    // -------------------------
    // ELEMENTOS YA EXISTENTES
    // -------------------------
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Label statusLabel;
    @FXML private StackPane statusPane;

    // -------------------------
    // RESULTADOS / FILTROS
    // -------------------------
    @FXML private ScrollPane scrollPane;
    @FXML private FlowPane resultsGrid;
    @FXML private ProgressIndicator loadingSpinner;

    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterAnime;
    @FXML private ToggleButton filterSeries;
    @FXML private ToggleButton filterMovie;
    @FXML private ToggleButton filterMusic;
    @FXML private ToggleButton filterGame;
    @FXML private ToggleButton filterManga;
    @FXML private ToggleButton filterBook;

    private final ToggleGroup filterGroup = new ToggleGroup();

    // Resultados de la última búsqueda (sin filtrar), para poder re-filtrar sin re-buscar
    private final List<MediaItem> lastResults = new ArrayList<>();

    // Servicios de búsqueda externa
    private final TMDBService tmdbService = new TMDBService();
    private final AnilistService anilistService = new AnilistService();
    private final MusicService musicService = new MusicService();
    private final GameService gameService = new GameService();
    private final BookService bookService = new BookService();

    // -------------------------
    // NUEVOS ELEMENTOS DEL MENÚ
    // -------------------------
    @FXML private StackPane sideMenu;
    @FXML private StackPane profileMenu;
    @FXML private StackPane profileIconContainer;
    @FXML private ImageView profileIcon;
    @FXML private Label profileInitialLabel;
    @FXML private ImageView profilePicture;
    @FXML private Label profileInitialLabelBig;
    @FXML private Label usernameLabel;
    @FXML private ImageView navProfilePicture;
    @FXML private Label navProfileInitialLabel;
    @FXML private Label welcomeLabel;
    @FXML private StackPane detailOverlayContainer;
    @FXML private Button backToHomeButton;
    @FXML private Button navHome;
    @FXML private Button navSearch;
    @FXML private Button navFavorites;
    @FXML private Button navLibrary;
    @FXML private Button navReleases;
    @FXML private Button updateButton;
    @FXML private Label versionLabel;

    /** Versión nueva detectada en GitHub (null si no hay). */
    private com.rujiman.mediatracker.services.UpdateService.Release pendingUpdate;

    // sideMenu = panel grande de navegación (☰); profileMenu = popup
    // pequeño de "quién soy" (foto/nombre/cambiar usuario-contraseña),
    // anclado bajo la foto de perfil. Son independientes: abrir uno no
    // cierra el otro automáticamente salvo que el usuario navegue.
    private boolean navMenuOpen = false;
    private boolean profileMenuOpen = false;

    // Ancho del panel lateral de navegación (debe coincidir con
    // prefWidth/maxWidth del sideMenu en SearchView.fxml), usado para la
    // animación de deslizamiento
    private static final double SIDE_MENU_WIDTH = 270;

    /** Animación de apertura/cierre del overlay en curso (para poder cancelarla). */
    private javafx.animation.Animation overlayFade;

    // -------------------------
    // INICIALIZACIÓN
    // -------------------------
    @FXML
    public void initialize() {

        // Ctrl+K (Cmd+K en Mac): ir a buscar desde cualquier pantalla
        searchField.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(
                    new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.K, javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
                    () -> {
                        // Desde cualquier pantalla te lleva a Buscar, listo para escribir
                        goSearch();
                        searchField.requestFocus();
                        searchField.selectAll();
                    });
        });

        // Recorte circular: la imagen rellena el círculo sin deformarse (estilo "cover")
        clipCircular(profileIcon, 16);   // radio = mitad de 32 (icono del header)
        clipCircular(profilePicture, 32); // radio = mitad de 64 (popup de perfil, antes 80px)
        clipCircular(navProfilePicture, 20); // radio = mitad de 40 (cabecera del menú de navegación)

        loadProfileData();

        // Ocultar ambos menús al inicio
        sideMenu.setTranslateX(-SIDE_MENU_WIDTH);
        sideMenu.setVisible(false);
        profileMenu.setVisible(false);
        profileMenu.setManaged(false);

        // Agrupar los filtros para que solo uno esté activo a la vez
        filterAll.setToggleGroup(filterGroup);
        filterAnime.setToggleGroup(filterGroup);
        filterSeries.setToggleGroup(filterGroup);
        filterMovie.setToggleGroup(filterGroup);
        filterMusic.setToggleGroup(filterGroup);
        filterGame.setToggleGroup(filterGroup);
        filterManga.setToggleGroup(filterGroup);
        filterBook.setToggleGroup(filterGroup);

        // Evitar que el usuario pueda dejar el grupo sin ninguna selección
        filterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
                return;
            }
            updateFilterButtonStyles();
        });

        // Estilo inicial correcto (Todo seleccionado por defecto)
        updateFilterButtonStyles();

        // Acelerar el scroll con la rueda del ratón (por defecto JavaFX se mueve muy poco)
        speedUpScroll(scrollPane);

        // El FlowPane de resultados necesita conocer su ancho disponible
        // real para decidir cuántas tarjetas caben por fila y saltar de
        // línea correctamente. Sin este binding, el ancho se calculaba
        // en el momento en que el ScrollPane pasaba de invisible a
        // visible (al cambiar de filtro), y a veces quedaba "congelado"
        // en un valor mínimo: todas las tarjetas se apilaban en una sola
        // columna en la esquina superior izquierda, con el resto del
        // espacio vacío. Vincular el prefWrapLength directamente al ancho
        // del viewport interno del ScrollPane es la forma robusta de
        // evitarlo, sin depender de en qué momento exacto se hace visible.
        resultsGrid.prefWrapLengthProperty().bind(scrollPane.widthProperty().subtract(20));

        // Permitir buscar pulsando Enter en el campo de texto
        searchField.setOnAction(e -> onSearch());

        // Iconos vectoriales del menú lateral
        navHome.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.HOME, 18, Palette.TEXT_SOFT));
        navSearch.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.SEARCH, 18, Palette.TEXT_SOFT));
        navFavorites.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.STAR, 18, Palette.TEXT_SOFT));
        navLibrary.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.LIBRARY, 18, Palette.TEXT_SOFT));
        navReleases.setGraphic(com.rujiman.mediatracker.ui.Icons.of(com.rujiman.mediatracker.ui.Icons.CALENDAR, 18, Palette.TEXT_SOFT));

        // Versión instalada y comprobación de actualizaciones en segundo plano
        versionLabel.setText("MediaVerse " + com.rujiman.mediatracker.util.AppInfo.VERSION);
        checkForUpdates(false);

        // Cargar la Home dentro de su contenedor y mostrarla por defecto
        loadHomeView();
        goHome();
    }

    /**
     * Carga HomeView.fxml dentro de homeContainer una sola vez al iniciar,
     * conectando la apertura de detalle a la misma lógica que usa búsqueda.
     */
    private void loadHomeView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/HomeView.fxml")
            );
            javafx.scene.Parent homeRoot = loader.load();
            homeController = loader.getController();
            homeController.setOnOpenDetailAction(this::openDetailView);
            homeController.setOnOpenReleasesAction(this::openReleases);
            // La foto también se cambia pulsando el avatar de la vitrina
            homeController.setOnChangePhotoAction(() -> {
                onChangeProfilePicture();
                homeController.refreshBanner();
            });

            homeContainer.getChildren().setAll(homeRoot);

        } catch (Exception e) {
            System.err.println("❌ Error al cargar HomeView: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Muestra la pantalla de Inicio ("Tu MediaVerse") y oculta la búsqueda.
     * La barra de búsqueda y los filtros del header también se ocultan,
     * ya que no tienen sentido fuera del modo búsqueda.
     */
    @FXML
    private void goHome() {
        if (navMenuOpen) closeNavMenu();
        if (profileMenuOpen) closeProfileMenu();
        closeOverlayNow();

        homeContainer.setVisible(true);
        searchContentBox.setVisible(false);
        searchBarBox.setVisible(false);
        searchBarBox.setManaged(false);
        filterBarBox.setVisible(false);
        filterBarBox.setManaged(false);
        backToHomeButton.setVisible(false);
        backToHomeButton.setManaged(false);

        if (homeController != null) {
            homeController.refreshAll();
        }

        setWindowTitle("Mediaverse - Inicio");
    }

    /**
     * Muestra la pantalla de Búsqueda y oculta la Home. Activa la barra
     * de búsqueda y filtros en el header, y el botón "← Volver" (para no
     * depender de abrir el menú lateral solo para volver a Inicio).
     */
    @FXML
    private void goSearch() {
        if (navMenuOpen) closeNavMenu();
        if (profileMenuOpen) closeProfileMenu();
        closeOverlayNow();

        homeContainer.setVisible(false);
        searchContentBox.setVisible(true);
        searchBarBox.setVisible(true);
        searchBarBox.setManaged(true);
        filterBarBox.setVisible(true);
        filterBarBox.setManaged(true);
        backToHomeButton.setVisible(true);
        backToHomeButton.setManaged(true);

        searchField.requestFocus();

        setWindowTitle("Mediaverse - Buscar");
    }

    /**
     * Actualiza el título de la ventana (la barra de Windows) para que
     * refleje en qué pantalla está realmente el usuario, en vez de
     * quedarse fijo en "Mediaverse - Search" desde el login. Defensivo
     * ante el caso de que la Scene/Stage no esté disponible todavía
     * (por ejemplo, la primera llamada desde initialize()).
     */
    private void setWindowTitle(String title) {
        if (searchField != null && searchField.getScene() != null
                && searchField.getScene().getWindow() != null) {
            ((javafx.stage.Stage) searchField.getScene().getWindow()).setTitle(title);
        }
    }

    /**
     * Actualiza el estilo visual de los botones de filtro: el seleccionado
     * se pinta con el color de SU PROPIA sección (cian=Juegos, violeta=Series,
     * magenta=Anime, menta=Música, ámbar=Películas; "Todo" usa el rosa de
     * marca, al no representar una sección concreta), el resto en el fondo
     * oscuro normal. Sin esto, el ToggleGroup cambia la selección lógica
     * pero el color de fondo se queda igual, así que no se notaba
     * visualmente qué filtro estaba activo.
     */
    private void updateFilterButtonStyles() {
        updateSingleFilterStyle(filterAll, Palette.BRAND);
        updateSingleFilterStyle(filterAnime, MediaTypeTheme.accentHex(MediaType.ANIME));
        updateSingleFilterStyle(filterSeries, MediaTypeTheme.accentHex(MediaType.SERIES));
        updateSingleFilterStyle(filterMovie, MediaTypeTheme.accentHex(MediaType.MOVIE));
        updateSingleFilterStyle(filterMusic, MediaTypeTheme.accentHex(MediaType.MUSIC));
        updateSingleFilterStyle(filterGame, MediaTypeTheme.accentHex(MediaType.GAME));
        updateSingleFilterStyle(filterManga, MediaTypeTheme.accentHex(MediaType.MANGA));
        updateSingleFilterStyle(filterBook, MediaTypeTheme.accentHex(MediaType.BOOK));
    }

    private void updateSingleFilterStyle(ToggleButton btn, String accentHexWhenSelected) {
        boolean selected = btn.isSelected();
        String bgColor = selected ? accentHexWhenSelected : Palette.PANEL_ALT;
        String textColor = selected ? "white" : Palette.TEXT;
        String fontWeight = selected ? "-fx-font-weight: bold;" : "";

        btn.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                        "-fx-text-fill: " + textColor + ";" +
                        "-fx-font-size: 11px;" +
                        fontWeight +
                        "-fx-background-radius: 14;" +
                        "-fx-padding: 5 14 5 14;" +
                        "-fx-cursor: hand;"
        );
    }

    /**
     * Devuelve el hex de acento de la sección, según la paleta
     * "Constelaciones" de theme.css.
     */
    /** @deprecated Usar {@link MediaTypeTheme#accentHex(MediaType)} directamente. */
    @Deprecated
    /**
     * Multiplica la velocidad de desplazamiento de la rueda del ratón
     * en un ScrollPane vertical, ya que el valor por defecto de JavaFX
     * es muy lento para listas largas de resultados.
     */
    private void speedUpScroll(ScrollPane pane) {
        final double SCROLL_MULTIPLIER = 4.0;
        pane.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            double deltaY = event.getDeltaY() * SCROLL_MULTIPLIER;
            double height = pane.getContent().getBoundsInLocal().getHeight();
            double vValue = pane.getVvalue();
            pane.setVvalue(vValue - deltaY / height);
            event.consume();
        });
    }

    /**
     * Recorta un ImageView en forma de círculo y hace que la imagen
     * rellene ese círculo sin deformarse (similar a object-fit: cover).
     */
    private void clipCircular(ImageView imageView, double radius) {
        Circle clip = new Circle(radius, radius, radius);
        imageView.setClip(clip);
        imageView.setPreserveRatio(false); // controlamos el recorte nosotros vía el viewport
        imageView.setFitWidth(radius * 2);
        imageView.setFitHeight(radius * 2);
        imageView.setSmooth(true);

        // Cuando llega una imagen nueva, ajustamos el viewport para que
        // se recorte tipo "cover" (sin deformar, llenando todo el círculo)
        imageView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                fitImageCover(imageView, newImg, radius * 2);
            }
        });
    }

    /**
     * Calcula un viewport que recorta la imagen al cuadrado central más grande
     * posible, para que al escalarla a sizePx x sizePx no se deforme.
     */
    private void fitImageCover(ImageView imageView, Image image, double sizePx) {
        double imgW = image.getWidth();
        double imgH = image.getHeight();
        if (imgW <= 0 || imgH <= 0) return;

        double side = Math.min(imgW, imgH);
        double x = (imgW - side) / 2.0;
        double y = (imgH - side) / 2.0;

        imageView.setViewport(new javafx.geometry.Rectangle2D(x, y, side, side));
        imageView.setFitWidth(sizePx);
        imageView.setFitHeight(sizePx);
    }

    /**
     * Carga el nombre visible y la foto del usuario actualmente logueado
     * desde profile.json (a través de ProfileService).
     */
    private void loadProfileData() {
        String loginUser = AuthService.getCurrentUser();
        if (loginUser == null) {
            usernameLabel.setText("Usuario");
            updateProfileInitial("Usuario");
            return;
        }

        ProfileService.Profile profile = ProfileService.loadProfile(loginUser);
        String displayName = profile.displayName != null ? profile.displayName : loginUser;

        usernameLabel.setText(displayName);
        welcomeLabel.setText("Bienvenido a tu galaxia, " + displayName);
        updateProfileInitial(displayName);

        if (profile.photoPath != null && !profile.photoPath.isBlank()) {
            File photoFile = new File(profile.photoPath);
            if (photoFile.exists()) {
                Image img = new Image(photoFile.toURI().toString());
                profileIcon.setImage(img);
                profilePicture.setImage(img);
                navProfilePicture.setImage(img);
                profileIcon.setVisible(true);
                profilePicture.setVisible(true);
                navProfilePicture.setVisible(true);
                profileInitialLabel.setVisible(false);
                profileInitialLabelBig.setVisible(false);
                navProfileInitialLabel.setVisible(false);
            }
        }
    }

    /**
     * Muestra la primera letra del nombre de usuario en los círculos
     * placeholder (icono del header, popup de perfil, y cabecera del
     * menú de navegación).
     */
    private void updateProfileInitial(String username) {
        String initial = (username != null && !username.isBlank())
                ? username.trim().substring(0, 1).toUpperCase()
                : "U";
        profileInitialLabel.setText(initial);
        profileInitialLabelBig.setText(initial);
        navProfileInitialLabel.setText(initial);
    }

    // -------------------------
    // BÚSQUEDA
    // -------------------------
    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            statusLabel.setText("Escribe algo para buscar ✨");
            statusLabel.setVisible(true);
            statusPane.setVisible(true);
            scrollPane.setVisible(false);
            return;
        }


        // Deshabilitar mientras busca, para evitar búsquedas duplicadas
        searchButton.setDisable(true);
        searchField.setDisable(true);

        Task<List<MediaItem>> searchTask = new Task<>() {
            @Override
            protected List<MediaItem> call() {
                List<MediaItem> allResults = new ArrayList<>();

                boolean tmdb = com.rujiman.mediatracker.util.ConfigLoader.hasTmdb();
                if (tmdb) {
                    updateMessage("Buscando series en TMDB...");
                    try {
                        allResults.addAll(tmdbService.searchSeries(query));
                    } catch (Exception e) {
                        System.err.println("⚠️ Error buscando series: " + e.getMessage());
                    }

                    updateMessage("Buscando películas en TMDB...");
                    try {
                        allResults.addAll(tmdbService.searchMovies(query));
                    } catch (Exception e) {
                        System.err.println("⚠️ Error buscando películas: " + e.getMessage());
                    }
                }

                updateMessage("Buscando anime en AniList...");
                try {
                    allResults.addAll(anilistService.search(query));
                } catch (Exception e) {
                    System.err.println("⚠️ Error buscando anime: " + e.getMessage());
                }

                updateMessage("Buscando música en Deezer...");
                try {
                    allResults.addAll(musicService.search(query));
                } catch (Exception e) {
                    System.err.println("⚠️ Error buscando música: " + e.getMessage());
                }

                if (com.rujiman.mediatracker.util.ConfigLoader.hasIgdb()) {
                    updateMessage("Buscando videojuegos en IGDB...");
                    try {
                        allResults.addAll(gameService.search(query));
                    } catch (Exception e) {
                        System.err.println("⚠️ Error buscando videojuegos: " + e.getMessage());
                    }
                }

                updateMessage("Buscando manga en AniList...");
                try {
                    allResults.addAll(anilistService.searchManga(query));
                } catch (Exception e) {
                    System.err.println("⚠️ Error buscando manga: " + e.getMessage());
                }

                updateMessage("Buscando libros en Google Books...");
                try {
                    allResults.addAll(bookService.search(query));
                } catch (Exception e) {
                    System.err.println("⚠️ Error buscando libros: " + e.getMessage());
                }

                return allResults;
            }
        };

        // El mensaje de progreso de la tarea se refleja en el statusLabel en tiempo real
        statusLabel.textProperty().bind(searchTask.messageProperty());
        statusLabel.setVisible(true);
        statusPane.setVisible(true);
        scrollPane.setVisible(false);
        loadingSpinner.setVisible(true);
        loadingSpinner.setManaged(true);

        searchTask.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            loadingSpinner.setVisible(false);
            loadingSpinner.setManaged(false);
            searchButton.setDisable(false);
            searchField.setDisable(false);
            setResults(searchTask.getValue());
        });

        searchTask.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            loadingSpinner.setVisible(false);
            loadingSpinner.setManaged(false);
            searchButton.setDisable(false);
            searchField.setDisable(false);
            statusLabel.setText("Ocurrió un error al buscar. Inténtalo de nuevo.");
            System.err.println("❌ Error en la búsqueda: " + searchTask.getException());
        });

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Llamar a este método cuando lleguen resultados nuevos de la búsqueda
     * (por ejemplo, desde los services). Guarda los resultados sin filtrar
     * y aplica el filtro actualmente seleccionado.
     */
    public void setResults(List<MediaItem> items) {
        lastResults.clear();
        if (items != null) lastResults.addAll(items);
        applyFilter();
    }

    // -------------------------
    // FILTROS POR TIPO
    // -------------------------
    @FXML
    private void onFilterChanged() {
        applyFilter();
    }

    private void applyFilter() {
        Toggle selected = filterGroup.getSelectedToggle();
        MediaType typeFilter = null; // null = sin filtro (mostrar todo)

        if (selected == filterAnime) {
            typeFilter = MediaType.ANIME;
        } else if (selected == filterSeries) {
            typeFilter = MediaType.SERIES;
        } else if (selected == filterMovie) {
            typeFilter = MediaType.MOVIE;
        } else if (selected == filterMusic) {
            typeFilter = MediaType.MUSIC;
        } else if (selected == filterGame) {
            typeFilter = MediaType.GAME;
        } else if (selected == filterManga) {
            typeFilter = MediaType.MANGA;
        } else if (selected == filterBook) {
            typeFilter = MediaType.BOOK;
        }
        // Si selected == filterAll (o ninguno), typeFilter se queda en null

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : lastResults) {
            if (typeFilter == null || item.getType() == typeFilter) {
                filtered.add(item);
            }
        }

        renderResults(filtered);

        // Filtro de un tipo sin conectar: explicar por qué no sale nada
        if (filtered.isEmpty()) {
            boolean noTmdb = (typeFilter == MediaType.SERIES || typeFilter == MediaType.MOVIE)
                    && !com.rujiman.mediatracker.util.ConfigLoader.hasTmdb();
            boolean noIgdb = typeFilter == MediaType.GAME && !com.rujiman.mediatracker.util.ConfigLoader.hasIgdb();
            if (noTmdb) statusLabel.setText("Para buscar series y películas, conecta TMDB en tu perfil → Claves de API");
            if (noIgdb) statusLabel.setText("Para buscar juegos, conecta Twitch (IGDB) en tu perfil → Claves de API");
        }
    }

    /**
     * Pinta los resultados filtrados en el FlowPane como tarjetas
     * con imagen, tipo, año y botón rápido de favorito.
     * Al pinchar en la tarjeta se abre la ventana de detalle.
     */
    private void renderResults(List<MediaItem> items) {
        resultsGrid.getChildren().clear();

        if (items.isEmpty()) {
            statusPane.setVisible(true);
            statusPane.setManaged(true);
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            statusLabel.setText("Sin resultados para este filtro 🔍");
            statusLabel.setVisible(true);
            loadingSpinner.setVisible(false);
            loadingSpinner.setManaged(false);
            return;
        }

        statusPane.setVisible(false);
        statusPane.setManaged(false);
        scrollPane.setVisible(true);
        scrollPane.setManaged(true);

        for (MediaItem item : items) {
            resultsGrid.getChildren().add(buildCard(item));
        }
        // Las tarjetas aparecen en cascada
        com.rujiman.mediatracker.ui.Motion.stagger(resultsGrid.getChildren());
    }

    /**
     * Construye una tarjeta visual para un MediaItem: imagen de portada,
     * badge del tipo, título, año, y botón de favorito rápido.
     * Toda la tarjeta es clicable y abre DetailView.
     */
    private VBox buildCard(MediaItem item) {
        VBox card = new VBox(6);
        card.setPrefWidth(160);
        // card-base: mismo fondo que el resto de tarjetas (antes era un
        // azul #16213e fuera de la paleta) y elevación al pasar el ratón
        card.getStyleClass().add("card-base");
        card.setAlignment(Pos.TOP_LEFT);

        // ---- Imagen de portada con esquinas redondeadas ----
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(160, 220);
        imageContainer.setMaxSize(160, 220);
        imageContainer.setStyle("-fx-background-color: #0f0f1a; -fx-background-radius: 10 10 0 0;");

        ImageView cover = new ImageView();
        cover.setFitWidth(160);
        cover.setFitHeight(220);
        cover.setPreserveRatio(false);
        cover.setSmooth(true);

        Rectangle clip = new Rectangle(160, 220);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        cover.setClip(clip);

        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            try {
                cover.setImage(new Image(item.getImageUrl(), 160, 220, false, true, true));
            } catch (Exception ignored) {}
        }

        // ---- Badge de favorito (esquina superior derecha) ----
        boolean isFav = FavoritesService.isFavorite(item.getTitle(), item.getType());
        Button favButton = new Button("★");
        favButton.setTextFill(javafx.scene.paint.Color.web(isFav ? "#e94560" : "#ffffff"));
        favButton.setStyle(
                "-fx-background-color: rgba(15,15,26,0.85);" +
                        "-fx-font-size: 18px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 50;" +
                        "-fx-min-width: 30; -fx-min-height: 30;" +
                        "-fx-max-width: 30; -fx-max-height: 30;" +
                        "-fx-padding: 0;" +
                        "-fx-cursor: hand;"
        );
        StackPane.setAlignment(favButton, Pos.TOP_RIGHT);
        favButton.setOnAction(e -> {
            e.consume();
            toggleFavoriteQuick(item, favButton);
        });

        imageContainer.getChildren().addAll(cover, favButton);
        StackPane.setMargin(favButton, new javafx.geometry.Insets(6));

        // ---- Badge de tipo (esquina superior izquierda), con el color
        //      de acento de su propia sección ----
        Label typeBadge = new Label(typeLabel(item.getType()));
        typeBadge.getStyleClass().add(badgeClassFor(item.getType()));
        StackPane.setAlignment(typeBadge, Pos.TOP_LEFT);
        StackPane.setMargin(typeBadge, new javafx.geometry.Insets(6));
        imageContainer.getChildren().add(typeBadge);

        // ---- Texto: título, año y rating ----
        Label titleLabel = new Label(item.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(150);
        titleLabel.setStyle(
                "-fx-text-fill: #eaeaea;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 6 8 0 8;"
        );

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setStyle("-fx-padding: 0 8 8 8;");

        String yearText = item.getYear() != null ? String.valueOf(item.getYear()) : "—";
        Label yearLabel = new Label(yearText);
        yearLabel.setStyle("-fx-text-fill: #555577; -fx-font-size: 10px;");

        metaRow.getChildren().add(yearLabel);

        if (item.getScore() != null && item.getScore() > 0) {
            Label scoreLabel = new Label("⭐ " + item.getScore());
            scoreLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 10px; -fx-font-weight: bold;");
            metaRow.getChildren().add(scoreLabel);
        }

        card.getChildren().addAll(imageContainer, titleLabel, metaRow);

        // ---- Click en la tarjeta abre el detalle ----
        card.setOnMouseClicked(e -> openDetailView(item));

        return card;
    }

    private String typeLabel(MediaType type) {
        return MediaTypeTheme.badgeText(type);
    }

    /**
     * Clase CSS del badge de tipo, con el color de acento de su propia
     * sección (ver theme.css), en vez de un rojo fijo para todos los tipos.
     */
    private String badgeClassFor(MediaType type) {
        return MediaTypeTheme.badgeClass(type);
    }

    /**
     * Añade/quita de favoritos directamente desde la tarjeta de resultados,
     * sin necesidad de abrir la ventana de detalle.
     */
    private void toggleFavoriteQuick(MediaItem item, Button favButton) {
        boolean isFav = FavoritesService.isFavorite(item.getTitle(), item.getType());

        if (isFav) {
            List<FavoriteItem> favs = FavoritesService.getFavorites();
            for (FavoriteItem fav : favs) {
                if (fav.getTitle().equalsIgnoreCase(item.getTitle()) && fav.getType() == item.getType()) {
                    FavoritesService.removeFavorite(fav.getId());
                    break;
                }
            }
            favButton.setTextFill(javafx.scene.paint.Color.web("#ffffff"));
        } else {
            FavoritesService.addFavorite(new FavoriteItem(item));
            favButton.setTextFill(javafx.scene.paint.Color.web("#e94560"));
        }
    }

    /**
     * Abre la vista de detalle dentro del overlay interno (tercera capa
     * del StackPane raíz), sin crear una ventana nueva y sin perder
     * los resultados de búsqueda actuales.
     */
    /**
     * Abre el detalle de un item, apilado sobre lo que hubiera debajo
     * (búsqueda/home por defecto). Si desde aquí se pincha una tarjeta de
     * recomendación, se abre un NUEVO detalle apilado encima de ESTE: el
     * "Volver" del detalle de la recomendación reabre este mismo detalle
     * (no la búsqueda directamente), igual patrón que favoritos -> detalle.
     */
    private void openDetailView(MediaItem item) {
        openDetailView(item, this::closeOverlay);
    }

    /**
     * Variante interna que permite especificar a dónde regresa "Volver"
     * desde este detalle. Se usa tanto para la apertura normal (vuelve
     * a cerrar el overlay) como para encadenar detalle -> recomendación
     * -> nueva recomendación sin perder nunca el camino de vuelta.
     */
    private void openDetailView(MediaItem item, Runnable onBack) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/DetailView.fxml")
            );
            javafx.scene.Parent detailRoot = loader.load();

            DetailViewController controller = loader.getController();
            controller.setOnBackAction(onBack);
            // Al pinchar una recomendación desde este detalle, se abre un
            // nuevo detalle apilado, cuyo "Volver" reabre ESTE detalle
            // (con su propio onBack ya correcto), no la búsqueda.
            controller.setOnOpenDetailAction(recommended -> openDetailView(recommended, () -> openDetailView(item, onBack)));
            controller.loadItem(item);

            openOverlay(detailRoot);
            setWindowTitle("Mediaverse - " + item.getTitle());

        } catch (Exception e) {
            System.err.println("❌ Error al abrir DetailView: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Abre la vista de favoritos dentro del mismo overlay, con sus propios
     * filtros y tarjetas. Al pinchar una tarjeta de favorito, se abre el
     * detalle correspondiente apilado encima (el "volver" del detalle
     * regresa a la lista de favoritos, no directamente a la búsqueda).
     */
    @FXML
    private void openFavorites() {
        // Si el menú lateral está abierto, lo cerramos para no solapar visualmente
        if (navMenuOpen) closeNavMenu();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/FavoritesView.fxml")
            );
            javafx.scene.Parent favoritesRoot = loader.load();

            FavoritesViewController controller = loader.getController();
            controller.setOnBackAction(this::closeOverlay);
            controller.setOnOpenDetailAction(this::openDetailViewFromFavorites);

            openOverlay(favoritesRoot);
            setWindowTitle("Mediaverse - Mis favoritos");

        } catch (Exception e) {
            System.err.println("❌ Error al abrir Favoritos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Abre el detalle de un favorito apilándolo sobre la vista de favoritos
     * (en vez de sobre la búsqueda), para que "Volver" regrese a la lista
     * de favoritos y no se pierda ese contexto.
     */
    private void openDetailViewFromFavorites(MediaItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/DetailView.fxml")
            );
            javafx.scene.Parent detailRoot = loader.load();

            DetailViewController controller = loader.getController();
            // Al volver desde este detalle, reabrimos la vista de favoritos actualizada
            controller.setOnBackAction(this::openFavorites);
            // Las recomendaciones se apilan como cualquier otro detalle:
            // su "Volver" reabre ESTE detalle (no favoritos directamente).
            controller.setOnOpenDetailAction(recommended -> openDetailView(recommended, () -> openDetailViewFromFavorites(item)));
            controller.loadItem(item);

            openOverlay(detailRoot);
            setWindowTitle("Mediaverse - " + item.getTitle());

        } catch (Exception e) {
            System.err.println("❌ Error al abrir DetailView desde favoritos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Muestra cualquier nodo dentro del overlay genérico (tercera capa
     * del StackPane raíz) con una animación de fade in.
     */
    /**
     * Abre cualquier overlay (detalle, favoritos, listas de seguimiento)
     * con un fade-in. Cierra ambos menús (navegación y perfil) primero,
     * si estuvieran abiertos, para que nunca queden solapados visualmente
     * detrás del overlay.
     */
    private void openOverlay(javafx.scene.Parent content) {
        if (navMenuOpen) closeNavMenu();
        if (profileMenuOpen) closeProfileMenu();

        // Si había un cierre a medias, se cancela: si no, su onFinished
        // vaciaría el overlay que estamos abriendo ahora mismo.
        if (overlayFade != null) overlayFade.stop();

        detailOverlayContainer.getChildren().setAll(content);
        detailOverlayContainer.setVisible(true);
        detailOverlayContainer.setOpacity(1);

        // Con un overlay abierto, la barra y los filtros de búsqueda no
        // aplican a lo que se ve: se ocultan.
        filterBarBox.setVisible(false);
        filterBarBox.setManaged(false);
        updateSearchBar();

        // Entrada "estilo Apple": la vista sube un poco mientras aparece
        overlayFade = com.rujiman.mediatracker.ui.Motion.enter(content);
    }

    /**
     * Cierra el overlay al instante, sin animación. Lo usan las acciones
     * de la cabecera (logo, Inicio, Buscar, buscar algo nuevo) para saltar
     * directamente desde cualquier detalle, sin cadena de "Volver".
     */
    private void closeOverlayNow() {
        if (overlayFade != null) overlayFade.stop();
        detailOverlayContainer.setVisible(false);
        detailOverlayContainer.getChildren().clear();
        detailOverlayContainer.setOpacity(1);
        restoreFilterBar();
    }

    /** Los filtros solo se muestran si estamos en Buscar y sin overlay encima. */
    private void restoreFilterBar() {
        boolean show = searchContentBox.isVisible() && !detailOverlayContainer.isVisible();
        filterBarBox.setVisible(show);
        filterBarBox.setManaged(show);
        updateSearchBar();
    }

    /**
     * La barra de búsqueda solo aparece en la pantalla de Buscar. En el resto
     * (Favoritos, Mi biblioteca, fichas) se oculta para que no parezca que
     * busca dentro de esa sección. Ctrl+K lleva a Buscar desde cualquier sitio.
     */
    private void updateSearchBar() {
        boolean show = searchContentBox.isVisible() && !detailOverlayContainer.isVisible();
        searchBarBox.setVisible(show);
        searchBarBox.setManaged(show);
    }

    /**
     * Cierra el overlay genérico y vuelve a mostrar lo que hubiera debajo
     * (búsqueda o menú lateral), que nunca se destruyeron.
     */
    private void closeOverlay() {
        if (overlayFade != null) overlayFade.stop();
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                com.rujiman.mediatracker.ui.Motion.FAST, detailOverlayContainer
        );
        fade.setInterpolator(com.rujiman.mediatracker.ui.Motion.EASE_IN_OUT);
        overlayFade = fade;
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            detailOverlayContainer.setVisible(false);
            detailOverlayContainer.getChildren().clear();
            detailOverlayContainer.setOpacity(1);
            restoreFilterBar();

            // Al cerrar cualquier overlay (detalle, favoritos, watchlist,
            // plan...), restauramos el título de lo que queda visible
            // debajo, en vez de dejar el título del último overlay
            // abierto (por ejemplo, el nombre de un item de detalle).
            if (homeContainer.isVisible()) {
                setWindowTitle("Mediaverse - Inicio");
            } else if (searchContentBox.isVisible()) {
                setWindowTitle("Mediaverse - Buscar");
            }
        });
        fade.play();
    }

    // -------------------------
    // MENÚ DE NAVEGACIÓN (☰, junto al logo)
    // -------------------------
    @FXML
    private void toggleNavMenu() {
        if (profileMenuOpen) closeProfileMenu(); // no tener ambos abiertos a la vez
        if (navMenuOpen) closeNavMenu();
        else openNavMenu();
    }

    private void openNavMenu() {
        sideMenu.setVisible(true);

        TranslateTransition slide = new TranslateTransition(com.rujiman.mediatracker.ui.Motion.SLOW, sideMenu);
        slide.setInterpolator(com.rujiman.mediatracker.ui.Motion.EASE_OUT);
        slide.setFromX(-SIDE_MENU_WIDTH);
        slide.setToX(0);
        slide.play();

        navMenuOpen = true;
    }

    private void closeNavMenu() {
        TranslateTransition slide = new TranslateTransition(com.rujiman.mediatracker.ui.Motion.BASE, sideMenu);
        slide.setInterpolator(com.rujiman.mediatracker.ui.Motion.EASE_IN_OUT);
        slide.setFromX(0);
        slide.setToX(-SIDE_MENU_WIDTH);
        slide.setOnFinished(e -> sideMenu.setVisible(false));
        slide.play();

        navMenuOpen = false;
    }

    // -------------------------
    // POPUP DE PERFIL (foto de perfil del header)
    // -------------------------
    @FXML
    private void toggleProfileMenu() {
        if (navMenuOpen) closeNavMenu(); // no tener ambos abiertos a la vez
        if (profileMenuOpen) closeProfileMenu();
        else openProfileMenu();
    }

    private void openProfileMenu() {
        profileMenu.setVisible(true);
        profileMenu.setManaged(true);
        com.rujiman.mediatracker.ui.Motion.popIn(profileMenu);
        profileMenuOpen = true;
    }

    private void closeProfileMenu() {
        profileMenu.setVisible(false);
        profileMenu.setManaged(false);
        profileMenuOpen = false;
    }

    // -------------------------
    // FOTO DE PERFIL
    // -------------------------
    @FXML
    private void onChangeProfilePicture() {
        String loginUser = AuthService.getCurrentUser();
        if (loginUser == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona una foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg")
        );

        File file = chooser.showOpenDialog(profilePicture.getScene().getWindow());
        if (file != null) {
            // Determinar extensión original para conservar el formato
            String name = file.getName();
            String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".png";

            // La foto vive ahora dentro de la carpeta del propio usuario
            // (userdata/<user>/<user>_profile.<ext>), junto a sus JSON,
            // en vez de suelta en userdata/. getParentFile().mkdirs()
            // crea esa carpeta de usuario si aún no existe.
            File dest = AppPaths.userFile(loginUser, "", loginUser + "_profile" + extension).toFile();
            dest.getParentFile().mkdirs();

            try {
                java.nio.file.Files.copy(
                        file.toPath(),
                        dest.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.io.IOException e) {
                showAlert("No se pudo guardar la foto: " + e.getMessage());
                return;
            }

            Image img = new Image(dest.toURI().toString());
            profileIcon.setImage(img);
            profilePicture.setImage(img);
            navProfilePicture.setImage(img);
            profileIcon.setVisible(true);
            profilePicture.setVisible(true);
            navProfilePicture.setVisible(true);
            profileInitialLabel.setVisible(false);
            profileInitialLabelBig.setVisible(false);
            navProfileInitialLabel.setVisible(false);

            // Persistir la ruta en profile.json
            ProfileService.updatePhotoPath(loginUser, dest.getPath());
        }
    }

    // -------------------------
    // CAMBIAR NOMBRE DE USUARIO
    // -------------------------
    @FXML
    private void onChangeUsername() {
        TextInputDialog dialog = new TextInputDialog(usernameLabel.getText());
        dialog.setTitle("Cambiar nombre de usuario");
        dialog.setHeaderText("Introduce tu nuevo nombre de usuario");
        dialog.setContentText("Nuevo nombre:");

        dialog.showAndWait().ifPresent(newName -> {
            String trimmed = newName.trim();

            if (trimmed.length() < 3) {
                showAlert("El nombre debe tener al menos 3 caracteres.");
                return;
            }

            String loginUser = AuthService.getCurrentUser();
            if (loginUser == null) {
                showAlert("No hay sesión activa.");
                return;
            }

            // Actualiza el username de login real en users.json
            // (esto cambia con qué te identificas al iniciar sesión)
            boolean updated = AuthService.updateUsername(trimmed);
            if (!updated) {
                showAlert("Ese nombre de usuario ya existe o no es válido.");
                return;
            }

            // Guarda también el nombre visible en profile.json
            ProfileService.updateDisplayName(trimmed, trimmed);

            usernameLabel.setText(trimmed);
            welcomeLabel.setText("Bienvenido a tu galaxia, " + trimmed);
            updateProfileInitial(trimmed);
            showAlert("Nombre de usuario actualizado. Úsalo la próxima vez que inicies sesión.");
        });
    }

    // -------------------------
    // CAMBIAR CONTRASEÑA
    // -------------------------
    @FXML
    private void onChangePassword() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Cambiar contraseña");

        PasswordField pass1 = new PasswordField();
        pass1.setPromptText("Nueva contraseña");

        PasswordField pass2 = new PasswordField();
        pass2.setPromptText("Confirmar contraseña");

        VBox box = new VBox(10, pass1, pass2);
        dialog.getDialogPane().setContent(box);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) return pass1.getText();
            return null;
        });

        dialog.showAndWait().ifPresent(pass -> {
            if (!pass1.getText().equals(pass2.getText())) {
                showAlert("Las contraseñas no coinciden.");
                return;
            }

            if (pass1.getText().length() < 4) {
                showAlert("La contraseña debe tener al menos 4 caracteres.");
                return;
            }

            boolean updated = AuthService.updatePassword(pass1.getText());
            if (!updated) {
                showAlert("No se pudo actualizar la contraseña.");
                return;
            }

            showAlert("Contraseña actualizada.");
        });
    }

    // -------------------------
    // "PIENSO VER / JUGAR / ESCUCHAR"
    // -------------------------

    // -------------------------
    // MI BIBLIOTECA
    // -------------------------

    /**
     * Abre "Mi biblioteca" (Pendiente · En curso · Terminado). Recuerda el
     * último estado, tipo y carpeta, así al volver de un detalle se vuelve
     * exactamente al mismo sitio.
     */
    @FXML
    private void openLibrary() {
        if (navMenuOpen) closeNavMenu();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/LibraryView.fxml")
            );
            javafx.scene.Parent root = loader.load();
            LibraryViewController controller = loader.getController();
            controller.setOnOpenItem(this::openDetailFromLibrary);
            openOverlay(root);
            setWindowTitle("Mediaverse - Mi biblioteca");
        } catch (Exception e) {
            System.err.println("❌ Error al abrir Mi biblioteca: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Abre "Estrenos": próximos episodios y lanzamientos de lo que sigues.
     * Al abrir un título, "Volver" regresa a esta lista.
     */
    @FXML
    private void openReleases() {
        if (navMenuOpen) closeNavMenu();
        ReleasesView view = new ReleasesView(this::openDetailFromReleases);
        openOverlay(view.getRoot());
        setWindowTitle("Mediaverse - Estrenos");
    }

    private void openDetailFromReleases(MediaItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/DetailView.fxml")
            );
            javafx.scene.Parent detailRoot = loader.load();
            DetailViewController controller = loader.getController();
            controller.setOnBackAction(this::openReleases);
            controller.setOnOpenDetailAction(recommended -> openDetailView(recommended,
                    () -> openDetailFromReleases(item)));
            controller.loadItem(item);
            openOverlay(detailRoot);
            setWindowTitle("Mediaverse - " + item.getTitle());
        } catch (Exception e) {
            System.err.println("❌ Error al abrir DetailView desde Estrenos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Detalle abierto desde la biblioteca: "Volver" regresa a la biblioteca. */
    private void openDetailFromLibrary(MediaItem item, PlanService.ListKind planKind, String planItemId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/DetailView.fxml")
            );
            javafx.scene.Parent detailRoot = loader.load();
            DetailViewController controller = loader.getController();
            controller.setOnBackAction(this::openLibrary);
            controller.setOnOpenDetailAction(recommended -> openDetailView(recommended,
                    () -> openDetailFromLibrary(item, planKind, planItemId)));
            controller.loadItem(item);
            // Si viene de "Pendiente", el detalle sabe de qué lista es
            // (para que "Mover a Favoritos" funcione como en las listas de plan)
            if (planKind != null) controller.setPlanContext(planKind, planItemId);
            openOverlay(detailRoot);
            setWindowTitle("Mediaverse - " + item.getTitle());
        } catch (Exception e) {
            System.err.println("❌ Error al abrir DetailView desde la biblioteca: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------
    // ACTUALIZACIONES (GitHub Releases)
    // -------------------------

    /**
     * Pregunta a GitHub si hay una versión nueva, sin bloquear la UI. Si la
     * hay, aparece el botón "Actualizar a X" en la cabecera. Con
     * manual=true (desde el menú de perfil) también avisa si ya estás al día.
     */
    private void checkForUpdates(boolean manual) {
        Thread t = new Thread(() -> {
            var release = com.rujiman.mediatracker.services.UpdateService.checkLatest();
            javafx.application.Platform.runLater(() -> {
                if (release.isPresent()) {
                    pendingUpdate = release.get();
                    updateButton.setText("Actualizar a " + pendingUpdate.version());
                    updateButton.setVisible(true);
                    updateButton.setManaged(true);
                    com.rujiman.mediatracker.ui.Motion.popIn(updateButton);
                    if (manual) onUpdate();
                } else if (manual) {
                    showInfo("Ya tienes la última versión (" + com.rujiman.mediatracker.util.AppInfo.VERSION + ").");
                }
            });
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onApiKeys() {
        if (profileMenuOpen) closeProfileMenu();
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initOwner(mainStackWindow());
        dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        dialog.setTitle("Mediaverse - Claves de API");
        StackPane root = new StackPane();
        root.getStyleClass().add("login-root");
        root.getChildren().add(com.rujiman.mediatracker.ui.ApiKeysWizard.create(false, dialog::close));
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        javafx.scene.Scene scene = new javafx.scene.Scene(root, 560, Math.min(820, screen.getHeight() * 0.9));
        scene.getStylesheets().add(getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm());
        com.rujiman.mediatracker.ui.Motion.install(scene);
        dialog.setScene(scene);
        dialog.show();
    }

    private javafx.stage.Window mainStackWindow() {
        return updateButton != null && updateButton.getScene() != null ? updateButton.getScene().getWindow() : null;
    }

    @FXML
    private void onCheckUpdates() {
        if (profileMenuOpen) closeProfileMenu();
        checkForUpdates(true);
    }

    /**
     * Botón "Actualizar a X": muestra las novedades y, si se acepta,
     * descarga el nuevo MediaVerse.jar con barra de progreso, cierra la app
     * y la vuelve a abrir ya actualizada. Si esta instalación no se puede
     * actualizar sola (ejecución desde el IDE, o la versión pide instalación
     * completa), abre la página de descarga.
     */
    @FXML
    private void onUpdate() {
        var release = pendingUpdate;
        if (release == null) return;
        boolean auto = com.rujiman.mediatracker.services.UpdateService.canSelfUpdate(release);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        com.rujiman.mediatracker.ui.Dialogs.own(confirm, searchField);
        confirm.setTitle("Nueva versión de MediaVerse");
        confirm.setHeaderText("MediaVerse " + release.version() + " está disponible");
        String notes = release.notes() != null && !release.notes().isBlank()
                ? release.notes().replace(com.rujiman.mediatracker.util.AppInfo.FULL_INSTALL_MARK, "").trim()
                : "Mejoras y correcciones.";
        if (notes.length() > 600) notes = notes.substring(0, 600) + "…";
        confirm.setContentText(notes + "\n\n" + (auto
                ? "La app se cerrará, se actualizará y se volverá a abrir sola. Tus datos no se tocan."
                : "Se abrirá la página de descarga para instalar la nueva versión."));
        com.rujiman.mediatracker.ui.Dialogs.styleDark(confirm.getDialogPane());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        if (!auto) {
            openInBrowser(release.pageUrl());
            return;
        }

        // Diálogo de progreso de la descarga
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(320);
        bar.setStyle("-fx-accent: #ec4d80;");
        Label status = new Label("Descargando MediaVerse " + release.version() + "…");
        Dialog<Void> progress = new Dialog<>();
        com.rujiman.mediatracker.ui.Dialogs.own(progress, searchField);
        progress.setTitle("Actualizando MediaVerse");
        progress.getDialogPane().setContent(new javafx.scene.layout.VBox(12, status, bar));
        com.rujiman.mediatracker.ui.Dialogs.styleDark(progress.getDialogPane());
        progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        progress.getDialogPane().lookupButton(ButtonType.CANCEL).setVisible(false);

        javafx.concurrent.Task<java.nio.file.Path> download = new javafx.concurrent.Task<>() {
            @Override protected java.nio.file.Path call() throws Exception {
                return com.rujiman.mediatracker.services.UpdateService.downloadJar(release,
                        p -> updateProgress(p < 0 ? -1 : p, 1));
            }
        };
        bar.progressProperty().bind(download.progressProperty());
        download.setOnSucceeded(e -> {
            try {
                status.setText("Reiniciando…");
                com.rujiman.mediatracker.services.UpdateService.installAndRestart(download.getValue());
                javafx.application.Platform.exit();
                System.exit(0);
            } catch (Exception ex) {
                progress.close();
                showInfo("No se pudo instalar la actualización: " + ex.getMessage());
            }
        });
        download.setOnFailed(e -> {
            progress.close();
            showInfo("No se pudo descargar la actualización: " + download.getException().getMessage()
                    + "\nPuedes descargarla a mano desde GitHub.");
        });
        Thread t = new Thread(download, "update-download");
        t.setDaemon(true);
        t.start();
        progress.show();
    }

    private void openInBrowser(String url) {
        com.rujiman.mediatracker.util.Browser.open(url);
    }

    // -------------------------
    // COPIA DE SEGURIDAD
    // -------------------------
    @FXML
    private void onBackup() {
        if (profileMenuOpen) closeProfileMenu();
        ButtonType export = new ButtonType("Guardar copia", ButtonBar.ButtonData.OK_DONE);
        ButtonType restore = new ButtonType("Restaurar una copia", ButtonBar.ButtonData.OTHER);
        Alert choose = new Alert(Alert.AlertType.NONE, "", export, restore, ButtonType.CANCEL);
        Dialogs.own(choose, searchField);
        choose.setTitle("Copia de seguridad");
        choose.setHeaderText("Tus datos en un solo archivo");
        choose.setContentText("Guarda favoritos, progreso, vitrina, carpetas y claves en un .zip para no perderlos "
                + "o pasarlos a otro ordenador. Restaurar sustituye los datos actuales por los de la copia "
                + "(antes se guarda una copia automática de lo que tienes ahora).");
        Dialogs.styleDark(choose.getDialogPane());
        ButtonType picked = choose.showAndWait().orElse(ButtonType.CANCEL);
        javafx.stage.Window owner = searchField.getScene().getWindow();

        if (picked == export) {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Guardar copia de seguridad");
            fc.setInitialFileName(BackupService.suggestedName());
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Copia de MediaVerse (*.zip)", "*.zip"));
            java.io.File file = fc.showSaveDialog(owner);
            if (file == null) return;
            try {
                int n = BackupService.exportTo(file.toPath());
                showInfo("Copia guardada (" + n + " archivos):\n" + file.getAbsolutePath());
            } catch (Exception ex) {
                showInfo("No se pudo guardar la copia: " + ex.getMessage());
            }
        } else if (picked == restore) {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Elige una copia de MediaVerse");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Copia de MediaVerse (*.zip)", "*.zip"));
            java.io.File file = fc.showOpenDialog(owner);
            if (file == null) return;
            if (!BackupService.looksLikeBackup(file.toPath())) {
                showInfo("Ese archivo no parece una copia de MediaVerse.");
                return;
            }
            Alert sure = new Alert(Alert.AlertType.CONFIRMATION);
            Dialogs.own(sure, searchField);
            sure.setHeaderText("¿Restaurar esta copia?");
            sure.setContentText("Se sustituirán tus datos actuales. MediaVerse se cerrará al terminar: vuelve a abrirla para ver los datos restaurados.");
            Dialogs.styleDark(sure.getDialogPane());
            if (sure.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            try {
                java.nio.file.Path safety = BackupService.importFrom(file.toPath());
                Alert done = new Alert(Alert.AlertType.INFORMATION);
                Dialogs.own(done, searchField);
                done.setHeaderText("Copia restaurada");
                done.setContentText("Tus datos anteriores quedan guardados en:\n" + safety + "\n\nMediaVerse se cerrará ahora. Ábrela de nuevo.");
                Dialogs.styleDark(done.getDialogPane());
                done.showAndWait();
                // Salir sin guardar nada más: los datos en memoria son los viejos
                javafx.application.Platform.exit();
                System.exit(0);
            } catch (Exception ex) {
                showInfo("No se pudo restaurar la copia: " + ex.getMessage());
            }
        }
    }

    // -------------------------
    // ACERCA DE
    // -------------------------
    @FXML
    private void onAbout() {
        if (profileMenuOpen) closeProfileMenu();
        Dialog<Void> d = new Dialog<>();
        Dialogs.own(d, searchField);
        d.setTitle("Acerca de MediaVerse");
        Dialogs.styleDark(d.getDialogPane());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/com/rujiman/mediatracker/views/icons/mediaverse_logo.png")));
        logo.setFitWidth(56);
        logo.setFitHeight(56);
        Label name = new Label("MediaVerse " + com.rujiman.mediatracker.util.AppInfo.VERSION);
        name.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 24px; -fx-text-fill: " + Palette.TEXT + ";");
        Label tag = new Label("Tu vitrina de todo lo que ves, juegas, escuchas y lees.");
        tag.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Palette.TEXT_MUTED + ";");
        HBox head = new HBox(16, logo, new VBox(2, name, tag));
        head.setAlignment(Pos.CENTER_LEFT);

        Label author = new Label("Hecha por Rujiman como Trabajo de Fin de Grado de DAM.");
        author.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Palette.TEXT_SOFT + ";");

        HBox links = new HBox(8,
                aboutLink("Web", "https://rujiman.github.io/MediaVerse/"),
                aboutLink("Novedades", "https://github.com/" + com.rujiman.mediatracker.util.AppInfo.GITHUB_REPO + "/releases"),
                aboutLink("Código en GitHub", "https://github.com/" + com.rujiman.mediatracker.util.AppInfo.GITHUB_REPO));

        Label data = new Label("Datos de TMDB, AniList, IGDB, Deezer, Google Books y Open Library.\n"
                + "Este producto usa la API de TMDB pero no está respaldado ni certificado por TMDB.");
        data.setWrapText(true);
        data.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.DIM + ";");

        Button folder = new Button("Abrir carpeta de datos");
        folder.getStyleClass().add("btn-secondary-sm");
        folder.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(BackupService.dataDir().toFile());
            } catch (Exception ex) {
                showInfo("Tus datos están en:\n" + BackupService.dataDir());
            }
        });

        VBox box = new VBox(16, head, author, links, data, folder);
        box.setPadding(new javafx.geometry.Insets(8, 4, 4, 4));
        box.setPrefWidth(460);
        d.getDialogPane().setContent(box);
        d.showAndWait();
    }

    private Button aboutLink(String text, String url) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-secondary-sm");
        b.setOnAction(e -> openInBrowser(url));
        return b;
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        com.rujiman.mediatracker.ui.Dialogs.own(alert, searchField);
        alert.setHeaderText(null);
        alert.setContentText(message);
        com.rujiman.mediatracker.ui.Dialogs.styleDark(alert.getDialogPane());
        alert.show();
    }

    // -------------------------
    // LOGOUT
    // -------------------------
    @FXML
    private void logout() {
        AuthService.logout();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/LoginView.fxml")
            );
            Scene scene = new Scene(loader.load(),
                    com.rujiman.mediatracker.ui.WindowSize.loginWidth(),
                    com.rujiman.mediatracker.ui.WindowSize.loginHeight());
            // El login rediseñado usa clases de theme.css: hay que cargarla
            // también en esta Scene nueva (antes tenía estilos en línea)
            scene.getStylesheets().add(
                    getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm()
            );
            com.rujiman.mediatracker.ui.Motion.install(scene);

            Stage stage = (Stage) searchField.getScene().getWindow();
            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Mediaverse - Iniciar sesión");
            stage.sizeToScene();
            stage.centerOnScreen();

        } catch (Exception e) {
            System.err.println("❌ Error al volver al login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------
    // UTILIDAD
    // -------------------------
    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

}