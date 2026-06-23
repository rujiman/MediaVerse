package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.services.AuthService;
import com.rujiman.mediatracker.services.ProfileService;
import com.rujiman.mediatracker.services.TMDBService;
import com.rujiman.mediatracker.services.AnilistService;
import com.rujiman.mediatracker.services.MusicService;
import com.rujiman.mediatracker.services.GameService;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.PlanService;
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

    private final ToggleGroup filterGroup = new ToggleGroup();

    // Resultados de la última búsqueda (sin filtrar), para poder re-filtrar sin re-buscar
    private final List<MediaItem> lastResults = new ArrayList<>();

    // Servicios de búsqueda externa
    private final TMDBService tmdbService = new TMDBService();
    private final AnilistService anilistService = new AnilistService();
    private final MusicService musicService = new MusicService();
    private final GameService gameService = new GameService();

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

    // -------------------------
    // INICIALIZACIÓN
    // -------------------------
    @FXML
    public void initialize() {

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
        updateSingleFilterStyle(filterAll, "#ec4d80");
        updateSingleFilterStyle(filterAnime, "#ec4dc0");
        updateSingleFilterStyle(filterSeries, "#8b5cf6");
        updateSingleFilterStyle(filterMovie, "#ecb14d");
        updateSingleFilterStyle(filterMusic, "#4dec9e");
        updateSingleFilterStyle(filterGame, "#4dd9ec");
    }

    private void updateSingleFilterStyle(ToggleButton btn, String accentHexWhenSelected) {
        boolean selected = btn.isSelected();
        String bgColor = selected ? accentHexWhenSelected : "#1c1730";
        String textColor = selected ? "white" : "#f0eef5";
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
    private String sectionAccentHex(MediaType type) {
        if (type == null) return "#ec4d80";
        return switch (type) {
            case GAME -> "#4dd9ec";
            case SERIES -> "#8b5cf6";
            case ANIME -> "#ec4dc0";
            case MUSIC -> "#4dec9e";
            case MOVIE -> "#ecb14d";
        };
    }

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

                updateMessage("Buscando videojuegos en IGDB...");
                try {
                    allResults.addAll(gameService.search(query));
                } catch (Exception e) {
                    System.err.println("⚠️ Error buscando videojuegos: " + e.getMessage());
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
        }
        // Si selected == filterAll (o ninguno), typeFilter se queda en null

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : lastResults) {
            if (typeFilter == null || item.getType() == typeFilter) {
                filtered.add(item);
            }
        }

        renderResults(filtered);
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
    }

    /**
     * Construye una tarjeta visual para un MediaItem: imagen de portada,
     * badge del tipo, título, año, y botón de favorito rápido.
     * Toda la tarjeta es clicable y abre DetailView.
     */
    private VBox buildCard(MediaItem item) {
        VBox card = new VBox(6);
        card.setPrefWidth(160);
        card.setStyle(
                "-fx-background-color: #16213e;" +
                        "-fx-background-radius: 10;" +
                        "-fx-cursor: hand;"
        );
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
        boolean isFav = FavoritesService.isFavorite(item.getTitle());
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
        if (type == null) return "";
        return switch (type) {
            case ANIME -> "🎌 ANIME";
            case SERIES -> "📺 SERIES";
            case MOVIE -> "🎬 PELÍCULA";
            case MUSIC -> "🎵 MÚSICA";
            case GAME -> "🎮 JUEGO";
        };
    }

    /**
     * Clase CSS del badge de tipo, con el color de acento de su propia
     * sección (ver theme.css), en vez de un rojo fijo para todos los tipos.
     */
    private String badgeClassFor(MediaType type) {
        if (type == null) return "badge-series";
        return switch (type) {
            case GAME -> "badge-game";
            case SERIES -> "badge-series";
            case ANIME -> "badge-anime";
            case MUSIC -> "badge-music";
            case MOVIE -> "badge-movie";
        };
    }

    /**
     * Añade/quita de favoritos directamente desde la tarjeta de resultados,
     * sin necesidad de abrir la ventana de detalle.
     */
    private void toggleFavoriteQuick(MediaItem item, Button favButton) {
        boolean isFav = FavoritesService.isFavorite(item.getTitle());

        if (isFav) {
            List<FavoriteItem> favs = FavoritesService.getFavorites();
            for (FavoriteItem fav : favs) {
                if (fav.getTitle().equalsIgnoreCase(item.getTitle())) {
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

        detailOverlayContainer.getChildren().setAll(content);
        detailOverlayContainer.setVisible(true);
        detailOverlayContainer.setOpacity(0);

        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                Duration.millis(180), detailOverlayContainer
        );
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * Cierra el overlay genérico y vuelve a mostrar lo que hubiera debajo
     * (búsqueda o menú lateral), que nunca se destruyeron.
     */
    private void closeOverlay() {
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                Duration.millis(150), detailOverlayContainer
        );
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            detailOverlayContainer.setVisible(false);
            detailOverlayContainer.getChildren().clear();

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

        TranslateTransition slide = new TranslateTransition(Duration.millis(250), sideMenu);
        slide.setFromX(-SIDE_MENU_WIDTH);
        slide.setToX(0);
        slide.play();

        navMenuOpen = true;
    }

    private void closeNavMenu() {
        TranslateTransition slide = new TranslateTransition(Duration.millis(250), sideMenu);
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

            File dest = new File("userdata/" + loginUser + "_profile" + extension);
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

    @FXML
    private void openPlanWatch() {
        openPlanList(PlanService.ListKind.WATCH);
    }

    @FXML
    private void openPlanPlay() {
        openPlanList(PlanService.ListKind.PLAY);
    }

    @FXML
    private void openPlanListen() {
        openPlanList(PlanService.ListKind.LISTEN);
    }

    /**
     * Abre la lista de plan (Pienso ver/jugar/escuchar) en el overlay,
     * con su propia navegación de carpetas. Mismo patrón que
     * openWatchlist(): "Volver" desde aquí cierra el overlay; al pinchar
     * un item, se abre su detalle apilado encima con setPlanContext() ya
     * configurado, para que "Mover a Favoritos" aparezca correctamente.
     */
    private void openPlanList(PlanService.ListKind kind) {
        if (navMenuOpen) closeNavMenu();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/PlanListView.fxml")
            );
            javafx.scene.Parent planRoot = loader.load();

            PlanListViewController controller = loader.getController();
            controller.setOnBackAction(this::closeOverlay);
            controller.setOnOpenItemAction((item, listKind, planItemId) ->
                    openDetailViewFromPlan(item, listKind, planItemId));
            controller.setListKind(kind);

            openOverlay(planRoot);

            String listLabel = switch (kind) {
                case WATCH -> "Pienso ver";
                case PLAY -> "Pienso jugar";
                case LISTEN -> "Pienso escuchar";
            };
            setWindowTitle("Mediaverse - " + listLabel);

        } catch (Exception e) {
            System.err.println("❌ Error al abrir lista de plan: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Abre el detalle de un item de una lista de plan, apilado sobre esa
     * misma lista (igual patrón que favoritos/watchlist). Llama a
     * setPlanContext() justo después de loadItem() para que el botón
     * "Mover a Favoritos" aparezca en el detalle. Al volver, se reabre
     * la lista de plan ya actualizada (por ejemplo, si se acaba de mover
     * el item a favoritos, ya no aparecerá aquí).
     */
    private void openDetailViewFromPlan(MediaItem item, PlanService.ListKind kind, String planItemId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/DetailView.fxml")
            );
            javafx.scene.Parent detailRoot = loader.load();

            DetailViewController controller = loader.getController();
            controller.setOnBackAction(() -> openPlanList(kind));
            // Las recomendaciones se apilan como cualquier otro detalle:
            // su "Volver" reabre ESTE detalle, no la lista de plan
            // directamente. Importante: el detalle de una recomendación
            // NO tiene contexto de plan propio (no viene de esta lista),
            // así que se abre con openDetailView() normal, no con
            // openDetailViewFromPlan().
            controller.setOnOpenDetailAction(recommended -> openDetailView(recommended,
                    () -> openDetailViewFromPlan(item, kind, planItemId)));
            controller.loadItem(item);
            controller.setPlanContext(kind, planItemId);

            openOverlay(detailRoot);
            setWindowTitle("Mediaverse - " + item.getTitle());

        } catch (Exception e) {
            System.err.println("❌ Error al abrir DetailView desde plan: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------
    // SECCIONES
    // -------------------------

    @FXML
    private void openWatchedMovies() {
        openWatchlist(MediaType.MOVIE,
                progress -> progress.viewed,
                "🎬 Películas vistas",
                "Todavía no has visto ninguna película.\nMárcala como vista desde su detalle.");
    }

    @FXML
    private void openGames() {
        openWatchlist(MediaType.GAME,
                progress -> progress.viewed,
                "🎮 Juegos jugados",
                "Todavía no has jugado a ninguno de tus juegos favoritos.\nMárcalo como jugado desde su detalle.");
    }

    @FXML
    private void openListenedMusic() {
        openWatchlist(MediaType.MUSIC,
                progress -> progress.viewed,
                "🎵 Música escuchada",
                "Todavía no has marcado ninguna canción como escuchada.\nMárcala desde su detalle.");
    }

    @FXML
    private void openWatchedSeries() {
        openWatchlist(MediaType.SERIES,
                progress -> progress.viewed,
                "📺 Series vistas",
                "Todavía no has terminado ninguna serie.\nMárcala como vista desde su detalle, o completa todos sus episodios.");
    }

    @FXML
    private void openWatchingSeries() {
        openWatchlist(MediaType.SERIES,
                progress -> !progress.viewed && !progress.watchedEpisodes.isEmpty(),
                "▶️ Series viendo",
                "No tienes ninguna serie a medias ahora mismo.\nMarca algún episodio como visto desde su detalle para que aparezca aquí.");
    }

    @FXML
    private void openWatchedAnime() {
        openWatchlist(MediaType.ANIME,
                progress -> progress.viewed,
                "🎌 Anime vistos",
                "Todavía no has terminado ningún anime.\nMárcalo como visto desde su detalle, o completa todos sus episodios.");
    }

    @FXML
    private void openWatchingAnime() {
        openWatchlist(MediaType.ANIME,
                progress -> !progress.viewed && !progress.watchedEpisodes.isEmpty(),
                "🎌 Anime viendo",
                "No tienes ningún anime a medias ahora mismo.\nMarca algún episodio como visto desde su detalle para que aparezca aquí.");
    }

    /**
     * Abre la lista de seguimiento (Series/Anime vistos/viendo) en el
     * overlay, configurada con el tipo y criterio de progreso dados.
     * Las 4 variantes del menú lateral comparten esta misma lógica;
     * solo cambian el MediaType, el predicado de progreso y los textos.
     */
    private void openWatchlist(MediaType type, java.util.function.Predicate<com.rujiman.mediatracker.services.WatchProgressService.Progress> progressFilter,
                               String displayTitle, String emptyMessage) {
        if (navMenuOpen) closeNavMenu();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/WatchlistView.fxml")
            );
            javafx.scene.Parent watchlistRoot = loader.load();

            WatchlistViewController controller = loader.getController();
            controller.setOnBackAction(this::closeOverlay);
            controller.setOnOpenDetailAction(item -> openDetailViewFromWatchlist(item, type, progressFilter, displayTitle, emptyMessage));
            controller.setFilter(type, progressFilter, displayTitle, emptyMessage);

            openOverlay(watchlistRoot);
            setWindowTitle("Mediaverse - " + stripLeadingEmoji(displayTitle));

        } catch (Exception e) {
            System.err.println("❌ Error al abrir lista de seguimiento: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Quita el emoji y espacio inicial de un texto tipo "📺 Series vistas",
     * dejando solo "Series vistas". Se usa para componer títulos de
     * ventana más limpios, ya que algunos emojis no se renderizan bien
     * en la barra de título nativa de Windows (aparecen como un
     * cuadrado de carácter no soportado).
     */
    private String stripLeadingEmoji(String text) {
        if (text == null) return "";
        return text.replaceFirst("^[^a-zA-Z0-9¡¿]+\\s*", "").trim();
    }

    /**
     * Abre el detalle de un item de una lista de seguimiento, apilado
     * sobre esa misma lista (igual patrón que favoritos): al pulsar
     * "Volver" desde el detalle, se reabre la lista ya actualizada (por
     * ejemplo, si se acaba de terminar una serie, desaparece de
     * "viendo" y pasa a "vistas" la próxima vez que se entre ahí).
     */
    private void openDetailViewFromWatchlist(MediaItem item, MediaType type,
                                             java.util.function.Predicate<com.rujiman.mediatracker.services.WatchProgressService.Progress> progressFilter,
                                             String displayTitle, String emptyMessage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/rujiman/mediatracker/views/DetailView.fxml")
            );
            javafx.scene.Parent detailRoot = loader.load();

            DetailViewController controller = loader.getController();
            controller.setOnBackAction(() -> openWatchlist(type, progressFilter, displayTitle, emptyMessage));
            // Mismo patrón de encadenado: las recomendaciones se apilan
            // sobre este detalle, no directamente sobre la watchlist.
            controller.setOnOpenDetailAction(recommended -> openDetailView(recommended,
                    () -> openDetailViewFromWatchlist(item, type, progressFilter, displayTitle, emptyMessage)));
            controller.loadItem(item);

            openOverlay(detailRoot);
            setWindowTitle("Mediaverse - " + item.getTitle());

        } catch (Exception e) {
            System.err.println("❌ Error al abrir DetailView desde lista de seguimiento: " + e.getMessage());
            e.printStackTrace();
        }
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
            Scene scene = new Scene(loader.load(), 800, 600);

            Stage stage = (Stage) searchField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Mediaverse - Iniciar sesión");
            stage.setWidth(800);
            stage.setHeight(600);
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