package com.rujiman.mediatracker.controllers;

import com.rujiman.mediatracker.ui.CollectionUI;
import com.rujiman.mediatracker.ui.Dialogs;
import com.rujiman.mediatracker.ui.Palette;
import com.rujiman.mediatracker.ui.Layout;
import com.rujiman.mediatracker.models.FavoriteItem;
import com.rujiman.mediatracker.models.MediaItem;
import com.rujiman.mediatracker.models.MediaType;
import com.rujiman.mediatracker.util.MediaTypeTheme;
import com.rujiman.mediatracker.services.AuthService;
import com.rujiman.mediatracker.services.DashboardService;
import com.rujiman.mediatracker.services.FavoritesService;
import com.rujiman.mediatracker.services.ProfileService;
import com.rujiman.mediatracker.services.WatchProgressService;
import com.rujiman.mediatracker.ui.Icons;
import com.rujiman.mediatracker.ui.Motion;
import com.rujiman.mediatracker.ui.PngExporter;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pantalla de inicio "Mi MediaVerse" en formato VITRINA (dirección A del
 * rediseño): un perfil para "flexear" tus gustos, pensado para que en el
 * futuro sea el perfil público de cada usuario en la red social.
 *
 *   1. Banda de perfil: avatar, nombre, contadores por tipo y botones
 *      "Editar vitrina" / "Compartir perfil" (exporta la vitrina a PNG).
 *   2. "Continúa donde lo dejaste": máx. 3 títulos a medias (discreto,
 *      nada de filas infinitas estilo Netflix).
 *   3. Vitrinas grandes (Juegos, Anime, Series, Películas): tu FAVORITO
 *      ABSOLUTO en grande + del 2º al 5º en ranking.
 *   4. Vitrinas compactas (Música, Libros, Manga): 4 portadas.
 *
 * El contenido de cada vitrina se elige con el selector de siempre
 * (botón lápiz) y se ordena arrastrando: el que quede primero es el
 * favorito absoluto. Todo se construye por código porque depende de los
 * datos del usuario; el FXML solo aporta el ScrollPane y el contenedor.
 *
 * Tamaños FIJOS (decisión del proyecto: nada de grids responsive): la
 * página mide 1240 px de ancho y se centra en pantallas más grandes.
 */
public class HomeController {

    @FXML private ScrollPane homeScroll;
    @FXML private VBox homeRoot;

    // ── Medidas fijas ──────────────────────────────────────────────────
    private static final double PAGE_W = Layout.PAGE_W;
    private static final double GAP = Layout.GAP;
    private static final double SHOWCASE_W = (PAGE_W - GAP) / 2;      // 610
    private static final double MINI_W = (PAGE_W - 2 * GAP) / 3;      // 400
    private static final double HERO_W = 176, HERO_H = 264;

    // ── Colores de superficie (mismos que theme.css) ───────────────────
    private static final String PANEL = Palette.PANEL;
    private static final String PANEL_2 = Palette.PANEL_2;
    private static final String LINE = Palette.LINE;
    private static final String TEXT = Palette.TEXT;
    private static final String DIM = Palette.DIM;

    /** Vitrinas grandes y compactas, en el orden en que se pintan. */
    private static final MediaType[] BIG = {MediaType.GAME, MediaType.ANIME, MediaType.SERIES, MediaType.MOVIE};
    private static final MediaType[] SMALL = {MediaType.MUSIC, MediaType.BOOK, MediaType.MANGA};

    /** Contenedor de cada vitrina, para reconstruir solo esa al reordenar. */
    private final Map<MediaType, Pane> showcaseSlots = new LinkedHashMap<>();

    /** Id del favorito que se está arrastrando y su sección. */
    private String draggedId;
    private MediaType draggedType;

    /** Se ejecuta al pinchar una tarjeta; recibe el MediaItem equivalente. */
    private java.util.function.Consumer<MediaItem> onOpenDetailAction;

    public void setOnOpenDetailAction(java.util.function.Consumer<MediaItem> action) {
        this.onOpenDetailAction = action;
    }

    /**
     * Cambiar la foto de perfil lo gestiona SearchController (que también
     * actualiza los avatares de la cabecera); la vitrina solo lo invoca.
     */
    private Runnable onChangePhotoAction;

    public void setOnChangePhotoAction(Runnable action) {
        this.onChangePhotoAction = action;
    }

    /** Hueco de la banda de perfil, para refrescarla sola. */
    private final StackPane bannerSlot = new StackPane();

    /** Vuelve a pintar solo la banda de perfil (tras cambiar foto o portada). */
    public void refreshBanner() {
        bannerSlot.getChildren().setAll(buildProfileBanner());
    }

    @FXML
    public void initialize() {
        // El viewport interno del ScrollPane no hereda el fondo oscuro por
        // CSS normal (mismo fix que en DetailViewController).
        CollectionUI.darkViewport(homeScroll);

        DashboardService.pruneDeletedFavorites();
        refreshAll();
    }

    /**
     * Vuelve a pintar toda la vitrina. Público para refrescar al volver de
     * un detalle (p. ej. si se quitó un favorito) o tras editar una sección.
     */
    public void refreshAll() {
        showcaseSlots.clear();
        homeRoot.getChildren().clear();

        bannerSlot.setMaxWidth(PAGE_W);
        refreshBanner();
        homeRoot.getChildren().add(bannerSlot);

        // "Continúa donde lo dejaste" va en su hueco: se repinta solo cuando
        // llegan los estrenos de la semana (etiqueta "Nuevo ep. vie")
        continueSlot.setPrefWidth(PAGE_W);
        continueSlot.setMaxWidth(PAGE_W);
        rebuildContinueSection();
        homeRoot.getChildren().add(continueSlot);
        loadWeekReleases();

        for (int i = 0; i < BIG.length; i += 2) {
            homeRoot.getChildren().add(pageRow(showcaseSlot(BIG[i], true), showcaseSlot(BIG[i + 1], true)));
        }
        HBox minis = pageRow();
        for (MediaType t : SMALL) minis.getChildren().add(showcaseSlot(t, false));
        homeRoot.getChildren().add(minis);

        Motion.stagger(homeRoot.getChildren(), javafx.util.Duration.millis(60));
    }

    /**
     * Fila de ancho fijo (PAGE_W) centrada. Sin el maxWidth, un HBox dentro
     * del VBox se estira a todo el ancho y sus hijos quedan pegados a la
     * izquierda: por eso las vitrinas salían descentradas respecto al perfil.
     */
    private HBox pageRow(Node... children) {
        HBox row = new HBox(GAP, children);
        row.setPrefWidth(PAGE_W);
        row.setMaxWidth(PAGE_W);
        row.setAlignment(Pos.TOP_CENTER);
        return row;
    }

    // ===========================================================
    // 1. BANDA DE PERFIL
    // ===========================================================

    private Node buildProfileBanner() {
        String user = AuthService.getCurrentUser();
        ProfileService.Profile profile = user != null ? ProfileService.loadProfile(user) : null;
        String name = profile != null && profile.displayName != null ? profile.displayName : (user != null ? user : "Usuario");

        VBox banner = new VBox();
        banner.setPrefWidth(PAGE_W);
        banner.setMaxWidth(PAGE_W);
        banner.setStyle(panelStyle(24));

        // ── Portada: tu favorito absoluto, difuminado y oscurecido ─────
        double coverH = 180;
        StackPane cover = new StackPane();
        cover.setMinHeight(coverH);
        cover.setPrefHeight(coverH);
        cover.setStyle("-fx-background-color: #2b1f4a; -fx-background-radius: 24 24 0 0;");
        Rectangle coverClip = new Rectangle(PAGE_W, coverH);
        coverClip.setArcWidth(48);
        coverClip.setArcHeight(48);
        cover.setClip(coverClip);

        String solid = profile != null ? profile.bannerColor : null;
        boolean blur = profile != null && profile.bannerBlur;
        String backdrop = profile != null && profile.bannerUrl != null ? profile.bannerUrl : bannerImageUrl();
        int blurAmount = profile != null ? profile.bannerBlurAmount : 12;
        if (solid != null) {
            // Portada de color sólido elegido por el usuario
            cover.setStyle("-fx-background-color: " + solid + "; -fx-background-radius: 24 24 0 0;");
            backdrop = null;
            if (blur) cover.getChildren().add(blurredColorGlow(solid, coverH, blurAmount));
        }
        if (backdrop != null) {
            ImageView bg = new ImageView();
            bg.setFitWidth(PAGE_W);
            bg.setFitHeight(coverH);
            bg.setPreserveRatio(false);
            bg.setSmooth(true);
            // Se pide en la máxima resolución disponible para que no se
            // pixele al ocupar 1240 px de ancho. Desenfoque opcional.
            if (blur) {
                // Desenfoque con la intensidad elegida y un leve oscurecido
                // (más intenso cuanto más desenfoque, para que no "brille")
                javafx.scene.effect.ColorAdjust darken =
                        new javafx.scene.effect.ColorAdjust(0, -0.05, -0.08 - blurAmount / 200.0, 0);
                darken.setInput(new javafx.scene.effect.GaussianBlur(blurAmount));
                bg.setEffect(darken);
                // El desenfoque deja los bordes transparentes: se agranda lo
                // justo para que queden fuera de la zona visible
                bg.setScaleX((PAGE_W + 2.5 * blurAmount) / PAGE_W);
                bg.setScaleY((coverH + 2.5 * blurAmount) / coverH);
            }
            bg.setOpacity(0);
            Image img = new Image(highResUrl(backdrop), 0, 0, true, true, true);
            bg.setImage(img);
            CollectionUI.cropWhenReady(bg, img, PAGE_W, coverH, () -> fadeIn(bg));
            // Velo inferior para que el avatar y los botones contrasten
            Region veil = new Region();
            veil.setStyle("-fx-background-color: linear-gradient(to bottom, #1a153000 55%, #1a1530d9 100%);");
            cover.getChildren().addAll(bg, veil);
        }

        // Botón "Cambiar portada": aparece al pasar el ratón por la portada
        Button changeCover = new Button("Cambiar portada");
        changeCover.setGraphic(Icons.of(Icons.EDIT, 14, "#ffffff"));
        changeCover.setGraphicTextGap(8);
        changeCover.setStyle("-fx-background-color: #100c1cb3; -fx-text-fill: white; -fx-font-family: 'Inter SemiBold';"
                + "-fx-font-size: 13px; -fx-background-radius: 10; -fx-padding: 8 14; -fx-cursor: hand;");
        StackPane.setAlignment(changeCover, Pos.TOP_RIGHT);
        StackPane.setMargin(changeCover, new Insets(16));
        changeCover.setOpacity(0);
        cover.hoverProperty().addListener((o, was, is) -> fadeTo(changeCover, is ? 1 : 0));
        changeCover.setOnAction(e -> showCoverMenu(changeCover));
        cover.getChildren().add(changeCover);

        // Botón "Estilo" (izquierda): desenfoque y color sólido
        Button style = new Button("Estilo");
        style.setGraphic(Icons.of(Icons.PALETTE, 14, "#ffffff"));
        style.setGraphicTextGap(8);
        style.setStyle(changeCover.getStyle());
        StackPane.setAlignment(style, Pos.TOP_LEFT);
        StackPane.setMargin(style, new Insets(16));
        style.setOpacity(0);
        cover.hoverProperty().addListener((o, was, is) -> fadeTo(style, is ? 1 : 0));
        style.setOnAction(e -> showStyleMenu(style, profile));
        cover.getChildren().add(style);

        // ── Avatar solapando la portada + botones a la derecha ─────────
        StackPane avatar = buildAvatar(profile, name, 120);

        Button edit = secondaryButton("Editar vitrina", Icons.EDIT);
        edit.setOnAction(e -> chooseSectionToEdit());
        Button share = primaryButton("Compartir perfil", Icons.SHARE, Palette.BRAND);
        share.setOnAction(e -> exportVitrina());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox identity = new HBox(12, avatar, spacer, edit, share);
        // Tus logros destacados, junto a la foto: aquí caben con su nombre
        // entero sin quitarle sitio al nombre de usuario
        Node medals = buildMedals();
        if (medals != null) {
            // Misma altura que los botones y pegadas abajo como ellos: si no, la
            // fila (alta por el avatar) las estira y suben hasta la portada
            if (medals instanceof Region r) {
                r.minHeightProperty().bind(edit.heightProperty());
                r.prefHeightProperty().bind(edit.heightProperty());
                r.setMaxHeight(Region.USE_PREF_SIZE);
            }
            HBox.setMargin(medals, new Insets(0, 0, 0, 8));
            identity.getChildren().add(1, medals);
        }
        identity.setAlignment(Pos.BOTTOM_LEFT);
        identity.setPadding(new Insets(0, 32, 0, 32));
        VBox.setMargin(identity, new Insets(-64, 0, 0, 0));

        // ── Nombre, usuario y total, debajo del avatar ──────────────────
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 34px; -fx-text-fill: " + TEXT + ";");
        Label handle = new Label(user != null ? "@" + user.toLowerCase() : "");
        handle.setStyle("-fx-font-size: 15px; -fx-text-fill: " + DIM + ";");
        HBox nameRow = new HBox(12, nameLabel, handle);
        nameRow.setAlignment(Pos.BASELINE_LEFT);

        int total = 0;
        for (MediaType t : MediaType.values()) total += Math.min(DashboardService.getSectionItems(t).size(), DashboardService.getMaxItemsForSection(t));
        Label sub = new Label(total + " títulos en la vitrina");
        sub.setStyle("-fx-font-size: 14px; -fx-text-fill: " + DIM + ";");

        VBox nameBox = new VBox(2, nameRow, sub);

        // ── Tus números: salen del progreso guardado ────────────────────
        WatchProgressService.Stats st = WatchProgressService.getStats();
        HBox stats = new HBox(28,
                stat(formatInt(st.episodes()), st.episodes() == 1 ? "episodio visto" : "episodios vistos"),
                stat(formatInt(st.chapters()), st.chapters() == 1 ? "capítulo leído" : "capítulos leídos"),
                stat(formatInt(st.finished()), st.finished() == 1 ? "terminado" : "terminados"),
                stat(formatInt(st.inProgress()), "en curso"),
                stat(st.rated() == 0 ? "–" : String.format(java.util.Locale.forLanguageTag("es"), "%.1f", st.averageRating()), "tu nota media"));
        stats.setAlignment(Pos.BOTTOM_RIGHT);

        Region nameSpacer = new Region();
        HBox.setHgrow(nameSpacer, Priority.ALWAYS);
        HBox nameAndStats = new HBox(16, nameBox, nameSpacer, stats);
        nameAndStats.setAlignment(Pos.BOTTOM_LEFT);
        nameAndStats.setPadding(new Insets(14, 32, 18, 32));

        // ── Contadores por tipo ─────────────────────────────────────────
        // HBox y no FlowPane: un FlowPane calcula su alto como si se partiera
        // en varias filas y alargaba la banda al ponerle el botón al lado
        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER_LEFT);
        for (MediaType t : new MediaType[]{MediaType.GAME, MediaType.ANIME, MediaType.SERIES, MediaType.MOVIE,
                MediaType.MUSIC, MediaType.BOOK, MediaType.MANGA}) {
            int count = FavoritesService.getFavoritesByType(t).size();
            chips.getChildren().add(chip(MediaTypeTheme.accentHex(t), MediaTypeTheme.plural(t) + "  " + count));
        }

        // "Mis reseñas", abajo a la derecha, bajo las estadísticas
        int reviews = com.rujiman.mediatracker.services.ReviewService.count();
        // Misma forma y alto que los chips de tipo (píldoras finas)
        Button reviewsButton = pillButton(reviews > 0 ? "Mis reseñas  ·  " + reviews : "Mis reseñas", Icons.REVIEW,
                () -> { if (onOpenReviewsAction != null) onOpenReviewsAction.run(); });
        Button achievementsButton = pillButton("Logros  ·  " + com.rujiman.mediatracker.services.AchievementService.unlockedCount()
                        + "/" + com.rujiman.mediatracker.services.AchievementService.catalog().size(), Icons.TROPHY,
                () -> { if (onOpenAchievementsAction != null) onOpenAchievementsAction.run(); });
        HBox.setHgrow(chips, Priority.ALWAYS);
        HBox chipsRow = new HBox(8, chips, reviewsButton, achievementsButton);
        HBox.setMargin(reviewsButton, new Insets(0, 0, 0, 8));
        chipsRow.setAlignment(Pos.CENTER_LEFT);
        chipsRow.setPadding(new Insets(0, 32, 26, 32));

        banner.getChildren().addAll(cover, identity, nameAndStats, chipsRow);
        return banner;
    }

    /**
     * "Desenfoque" para portadas de color sólido: tres manchas del color
     * (una más clara, una con el tono desplazado y una más oscura)
     * difuminadas, que dan un degradado suave tipo fondo de iPhone.
     * Más intensidad = manchas más difusas y grandes.
     */
    private Node blurredColorGlow(String hex, double h, int amount) {
        javafx.scene.paint.Color base = javafx.scene.paint.Color.web(hex);
        Pane glow = new Pane();
        glow.setPrefSize(PAGE_W, h);
        glow.setMouseTransparent(true);
        double grow = 1 + amount / 40.0;
        Circle light = new Circle(PAGE_W * 0.22, h * 0.25, 150 * grow, base.deriveColor(0, 0.9, 1.45, 0.9));
        Circle shifted = new Circle(PAGE_W * 0.62, h * 0.85, 190 * grow, base.deriveColor(35, 1.0, 1.1, 0.85));
        Circle dark = new Circle(PAGE_W * 0.92, h * 0.15, 140 * grow, base.deriveColor(-25, 1.0, 0.55, 0.9));
        glow.getChildren().addAll(light, shifted, dark);
        glow.setEffect(new javafx.scene.effect.GaussianBlur(Math.min(63, 20 + amount * 1.1)));
        return glow;
    }

    /**
     * Versión en alta resolución de una portada, para usarla a todo lo
     * ancho de la vitrina: IGDB y TMDB sirven la misma imagen en varios
     * tamaños cambiando un trozo de la URL.
     */
    private String highResUrl(String url) {
        if (url == null) return null;
        if (url.contains("images.igdb.com")) {
            return url.replaceAll("/t_[a-z0-9_]+/", "/t_1080p/");
        }
        if (url.contains("image.tmdb.org/t/p/")) {
            return url.replaceAll("/t/p/[a-z0-9]+/", "/t/p/original/");
        }
        return url;
    }

    /** Imagen de la portada del perfil: el favorito absoluto de la primera vitrina que tenga uno. */
    private String bannerImageUrl() {
        for (MediaType t : BIG) {
            List<FavoriteItem> items = DashboardService.getSectionItems(t);
            if (!items.isEmpty() && items.get(0).getImageUrl() != null && !items.get(0).getImageUrl().isBlank()) {
                return items.get(0).getImageUrl();
            }
        }
        return null;
    }

    private StackPane buildAvatar(ProfileService.Profile profile, String name, double size) {
        StackPane ring = new StackPane();
        ring.setMinSize(size, size);
        ring.setMaxSize(size, size);
        ring.setStyle("-fx-background-color: " + PANEL + "; -fx-background-radius: " + size + ";");

        double inner = size - 12;
        Node content;
        File photo = profile != null && profile.photoPath != null ? new File(profile.photoPath) : null;
        if (photo != null && photo.exists()) {
            // Recorte cuadrado centrado: antes se forzaba la foto a un
            // cuadrado sin recortar y se veía estirada
            Image source = new Image(photo.toURI().toString(), 0, 0, true, true, false);
            ImageView img = new ImageView(source);
            img.setFitWidth(inner);
            img.setFitHeight(inner);
            img.setPreserveRatio(false);
            img.setSmooth(true);
            CollectionUI.cropWhenReady(img, source, inner, inner, null);
            img.setClip(new Circle(inner / 2, inner / 2, inner / 2));
            content = img;
        } else {
            Label initial = new Label(name.substring(0, 1).toUpperCase());
            initial.setMinSize(inner, inner);
            initial.setAlignment(Pos.CENTER);
            initial.setStyle("-fx-background-color: #8b5cf6; -fx-background-radius: " + inner + "; -fx-text-fill: white; -fx-font-family: 'Inter ExtraBold'; -fx-font-size: 40px;");
            content = initial;
        }
        ring.getChildren().add(content);

        // Al pasar el ratón: velo oscuro con "Cambiar"; clic = elegir foto
        Label change = new Label("Cambiar");
        change.setGraphic(Icons.of(Icons.EDIT, 16, "#ffffff"));
        change.setContentDisplay(ContentDisplay.TOP);
        change.setGraphicTextGap(4);
        change.setMinSize(inner, inner);
        change.setMaxSize(inner, inner);
        change.setAlignment(Pos.CENTER);
        change.setStyle("-fx-background-color: #000000a0; -fx-background-radius: " + inner + ";"
                + "-fx-text-fill: white; -fx-font-family: 'Inter SemiBold'; -fx-font-size: 12px;");
        change.setOpacity(0);
        ring.getChildren().add(change);
        ring.hoverProperty().addListener((o, was, is) -> fadeTo(change, is ? 1 : 0));
        ring.setStyle(ring.getStyle() + "-fx-cursor: hand;");
        ring.setOnMouseClicked(e -> {
            if (onChangePhotoAction != null) onChangePhotoAction.run();
        });
        Tooltip.install(ring, new Tooltip("Cambiar foto de perfil"));
        return ring;
    }

    private void fadeTo(Node n, double to) {
        javafx.animation.FadeTransition f = new javafx.animation.FadeTransition(Motion.FAST, n);
        f.setToValue(to);
        f.setInterpolator(Motion.EASE_OUT);
        f.play();
    }

    // ===========================================================
    // PORTADA PERSONALIZABLE
    // ===========================================================

    private void showCoverMenu(Node anchor) {
        MenuItem fromFavorites = new MenuItem("Elegir de mis favoritos…");
        fromFavorites.setOnAction(e -> pickCoverFromFavorites());
        MenuItem upload = new MenuItem("Subir una imagen…");
        upload.setOnAction(e -> uploadCover());
        MenuItem auto = new MenuItem("Automática (tu favorito absoluto)");
        auto.setOnAction(e -> setCover(null));
        ContextMenu menu = new ContextMenu(fromFavorites, upload, new SeparatorMenuItem(), auto);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 6);
    }

    /** Colores de la paleta rápida: los de MediaVerse + un par de neutros oscuros. */
    private static final String[] COVER_SWATCHES = {
            Palette.BRAND, "#8b5cf6", "#ec4dc0", "#4dd9ec", "#4dec9e",
            "#ecb14d", "#f97316", "#ef4444", "#2b1f4a", Palette.PANEL
    };

    /**
     * Menú "Estilo" de la portada: interruptor de desenfoque, colores
     * sólidos rápidos, selector de cualquier color y volver a la imagen.
     */
    private void showStyleMenu(Node anchor, ProfileService.Profile profile) {
        String user = AuthService.getCurrentUser();
        if (user == null) return;
        boolean usingColor = profile != null && profile.bannerColor != null;

        boolean blurOn = profile != null && profile.bannerBlur;
        CheckMenuItem blurItem = new CheckMenuItem(usingColor ? "Degradado difuso" : "Desenfocar imagen");
        blurItem.setSelected(blurOn);
        blurItem.setOnAction(e -> {
            ProfileService.updateBannerBlur(user, blurItem.isSelected());
            refreshBanner();
        });

        ContextMenu menu = new ContextMenu();

        // Intensidad del desenfoque: se aplica al soltar el deslizador
        Label amountLabel = new Label("Intensidad");
        amountLabel.setStyle("-fx-text-fill: " + Palette.DIM + "; -fx-font-size: 12px;");
        Slider amount = new Slider(2, 40, profile != null ? profile.bannerBlurAmount : 12);
        amount.setPrefWidth(5 * 28 + 4 * 8);
        amount.setDisable(!blurOn);
        amount.valueChangingProperty().addListener((o, was, changing) -> {
            if (!changing) {
                ProfileService.updateBannerBlurAmount(user, (int) Math.round(amount.getValue()));
                menu.hide();
                refreshBanner();
            }
        });
        amount.setOnMouseReleased(e -> {
            ProfileService.updateBannerBlurAmount(user, (int) Math.round(amount.getValue()));
            menu.hide();
            refreshBanner();
        });
        VBox amountBox = new VBox(6, amountLabel, amount);
        amountBox.setPadding(new Insets(4));
        CustomMenuItem amountItem = new CustomMenuItem(amountBox);
        amountItem.setHideOnClick(false);

        // Fila de muestras de color
        Label title = new Label("Color sólido");
        title.setStyle("-fx-text-fill: " + Palette.DIM + "; -fx-font-size: 12px;");
        FlowPane swatches = new FlowPane(8, 8);
        swatches.setPrefWrapLength(5 * 28 + 4 * 8);
        for (String hex : COVER_SWATCHES) {
            Button sw = new Button();
            boolean selected = usingColor && hex.equalsIgnoreCase(profile.bannerColor);
            sw.setStyle("-fx-background-color: " + hex + "; -fx-background-radius: 999; -fx-min-width: 28; -fx-min-height: 28;"
                    + "-fx-max-width: 28; -fx-max-height: 28; -fx-cursor: hand;"
                    + (selected ? "-fx-border-color: white; -fx-border-width: 2; -fx-border-radius: 999;" : ""));
            sw.setOnAction(e -> {
                menu.hide();
                ProfileService.updateBannerColor(user, hex);
                refreshBanner();
            });
            swatches.getChildren().add(sw);
        }

        // Cualquier otro color: en un diálogo aparte (un ColorPicker dentro
        // del propio menú se cerraría al abrir su paleta)
        Button other = new Button("Otro color…");
        other.setStyle("-fx-background-color: " + Palette.PANEL_3 + "; -fx-text-fill: " + Palette.TEXT + "; -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand;");
        other.setOnAction(e -> {
            menu.hide();
            pickCustomCoverColor(user, usingColor ? profile.bannerColor : "#2b1f4a");
        });

        VBox colorBox = new VBox(8, title, swatches, other);
        colorBox.setPadding(new Insets(4, 4, 4, 4));
        CustomMenuItem colorItem = new CustomMenuItem(colorBox);
        colorItem.setHideOnClick(false);

        MenuItem backToImage = new MenuItem("Usar imagen en vez de color");
        backToImage.setDisable(!usingColor);
        backToImage.setOnAction(e -> {
            ProfileService.updateBannerColor(user, null);
            refreshBanner();
        });

        menu.getItems().addAll(blurItem, amountItem, new SeparatorMenuItem(), colorItem, new SeparatorMenuItem(), backToImage);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 6);
    }

    /**
     * Asigna la ventana principal como "padre" del diálogo. Sin esto, al
     * cerrar un diálogo encadenado (p. ej. "Personalizar color..." del
     * ColorPicker, que se abre encima de otro diálogo) Windows no sabe a
     * qué ventana devolver el foco y la app se iba al fondo / minimizada.
     */
    private void ownDialog(Dialog<?> dialog) {
        if (homeRoot.getScene() != null && homeRoot.getScene().getWindow() != null) {
            dialog.initOwner(homeRoot.getScene().getWindow());
        }
    }

    private void pickCustomCoverColor(String user, String initialHex) {
        ColorPicker picker = new ColorPicker(javafx.scene.paint.Color.web(initialHex));
        picker.setStyle("-fx-background-color: " + Palette.PANEL_3 + "; -fx-background-radius: 8;");
        Dialog<javafx.scene.paint.Color> dialog = new Dialog<>();
        ownDialog(dialog);
        dialog.setTitle("Color de la portada");
        Dialogs.styleDark(dialog.getDialogPane());
        Dialogs.darkenWindow(dialog.getDialogPane());
        dialog.getDialogPane().setContent(new VBox(10, new Label("Elige cualquier color:"), picker));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (ok != null) ok.getStyleClass().add("btn-pill-brand");
        if (cancel != null) cancel.getStyleClass().add("btn-pill-neutral");
        dialog.setResultConverter(b -> b == ButtonType.OK ? picker.getValue() : null);
        dialog.showAndWait().ifPresent(c -> {
            String hex = String.format("#%02x%02x%02x",
                    (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
            ProfileService.updateBannerColor(user, hex);
            refreshBanner();
        });
    }

    private void setCover(String url) {
        String user = AuthService.getCurrentUser();
        if (user == null) return;
        ProfileService.updateBannerUrl(user, url);
        refreshBanner();
    }

    /** Sube una imagen propia: se copia junto a los datos del usuario. */
    private void uploadCover() {
        String user = AuthService.getCurrentUser();
        if (user == null) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Elige una imagen para tu portada");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(homeRoot.getScene().getWindow());
        if (file == null) return;

        String name = file.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".png";
        // Nombre con marca de tiempo: si se reutilizara siempre el mismo
        // nombre, la imagen anterior podría seguir viéndose en la sesión
        File dest = com.rujiman.mediatracker.services.AppPaths
                .userFile(user, "", user + "_banner_" + System.currentTimeMillis() + ext).toFile();
        dest.getParentFile().mkdirs();
        try {
            java.nio.file.Files.copy(file.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            setCover(dest.toURI().toString());
        } catch (java.io.IOException ex) {
            System.err.println("❌ No se pudo guardar la portada: " + ex.getMessage());
        }
    }

    /** Selector con las portadas de todos tus favoritos: clic = esa es tu portada. */
    private void pickCoverFromFavorites() {
        List<FavoriteItem> favs = new ArrayList<>();
        for (FavoriteItem f : FavoritesService.getFavorites()) {
            if (f.getImageUrl() != null && !f.getImageUrl().isBlank()) favs.add(f);
        }
        Dialog<String> dialog = new Dialog<>();
        ownDialog(dialog);
        dialog.setTitle("Elige tu portada");
        Dialogs.styleDark(dialog.getDialogPane());
        Dialogs.darkenWindow(dialog.getDialogPane());

        FlowPane grid = new FlowPane(10, 10);
        grid.setPrefWrapLength(560);
        grid.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");
        for (FavoriteItem f : favs) {
            Node thumb = CollectionUI.poster(f.getImageUrl(), 96, 144, MediaTypeTheme.accentHex(f.getType()), 10, true);
            thumb.getStyleClass().add("motion-lift");
            thumb.setStyle(thumb.getStyle() + "-fx-cursor: hand;");
            Tooltip.install(thumb, new Tooltip(f.getTitle()));
            thumb.setOnMouseClicked(e -> {
                dialog.setResult(f.getImageUrl());
                dialog.close();
            });
            grid.getChildren().add(thumb);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(600, 440);
        scroll.setStyle("-fx-background-color: " + Palette.PANEL_ALT + "; -fx-background: " + Palette.PANEL_ALT + "; -fx-border-color: transparent;");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancel != null) cancel.getStyleClass().add("btn-pill-neutral");
        dialog.setResultConverter(b -> null);
        dialog.showAndWait().ifPresent(this::setCover);
    }

    // ===========================================================
    // 2. CONTINÚA DONDE LO DEJASTE (máx. 3)
    // ===========================================================

    /** Un título a medias: su ficha y su progreso. */
    private record InProgress(MediaItem item, WatchProgressService.Progress progress, int total) {}

    private Node buildContinueSection() {
        List<InProgress> list = collectInProgress(3);
        if (list.isEmpty()) return null;

        Label title = new Label("Continúa donde lo dejaste");
        title.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + TEXT + ";");

        HBox cards = new HBox(16);
        double w = (PAGE_W - 32) / 3;
        for (InProgress p : list) cards.getChildren().add(buildContinueCard(p, w));

        VBox section = new VBox(14, title, cards);
        section.setPrefWidth(PAGE_W);
        section.setMaxWidth(PAGE_W);
        return section;
    }

    /**
     * Títulos con progreso por unidades (anime, series, manga) que no están
     * terminados ni sin empezar. Primero los favoritos, luego el resto.
     */
    private List<InProgress> collectInProgress(int max) {
        List<InProgress> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        MediaType[] types = MediaType.values();

        for (MediaType t : types) {
            for (FavoriteItem fav : FavoritesService.getFavoritesByType(t)) {
                String key = WatchProgressService.keyFor(t, fav.getTitle()).toLowerCase();
                addIfInProgress(result, seen, key, fav.toMediaItem(), WatchProgressService.getProgress(key));
            }
        }
        for (MediaType t : types) {
            for (WatchProgressService.Tracked tr : WatchProgressService.getTracked(t)) {
                String key = WatchProgressService.keyFor(t, tr.item().getTitle()).toLowerCase();
                addIfInProgress(result, seen, key, tr.item(), tr.progress());
            }
        }
        return result.size() > max ? result.subList(0, max) : result;
    }

    private void addIfInProgress(List<InProgress> out, java.util.Set<String> seen, String key,
                                 MediaItem item, WatchProgressService.Progress p) {
        if (seen.contains(key) || p == null || p.viewed) return;
        Integer total = p.totalEpisodes != null ? p.totalEpisodes : item.getEpisodes();
        boolean byUnits = !p.watchedEpisodes.isEmpty() && total != null && total > 0;
        // Por episodios/capítulos, o marcado con "Estoy jugando/viendo/leyendo"
        if (!byUnits && !p.inProgress) return;
        seen.add(key);
        out.add(new InProgress(item, p, byUnits ? total : 0));
    }

    // ===========================================================
    // 2b. ESTRENOS DE ESTA SEMANA (etiqueta en Continúa + aviso del menú)
    // ===========================================================

    /** Estrenos de los próximos 7 días por clave de título (último dato conocido). */
    private Map<String, com.rujiman.mediatracker.services.ReleaseService.Release> weekByKey = new java.util.HashMap<>();
    private int weekRequest = 0;
    private java.util.function.Consumer<List<com.rujiman.mediatracker.services.ReleaseService.Release>> onWeekReleases;

    private Runnable onOpenReviewsAction;
    private Runnable onOpenAchievementsAction;

    /** Botón "Logros" y medallas del banner: abren la pantalla de Logros. */
    public void setOnOpenAchievementsAction(Runnable action) {
        this.onOpenAchievementsAction = action;
    }

    /** Píldora fina con icono, del mismo alto que los chips de tipo. */
    private Button pillButton(String text, String icon, Runnable action) {
        Button b = new Button(text);
        b.setGraphic(Icons.of(icon, 14, TEXT));
        b.setGraphicTextGap(8);
        String pill = "-fx-background-radius: 999; -fx-border-radius: 999; -fx-padding: 6 14; -fx-min-height: 0;"
                + "-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-text-fill: " + TEXT + "; -fx-cursor: hand;"
                + "-fx-border-color: " + Palette.LINE_STRONG + ";";
        b.setStyle(pill + "-fx-background-color: transparent;");
        b.setOnMouseEntered(e -> b.setStyle(pill + "-fx-background-color: " + Palette.PANEL_3 + ";"));
        b.setOnMouseExited(e -> b.setStyle(pill + "-fx-background-color: transparent;"));
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        return b;
    }

    /**
     * Medallas del perfil: píldoras con icono y nombre del logro, en su color.
     * Son las que eliges en Logros (chincheta) o, si no eliges, las 3 más
     * difíciles. Pasando el ratón se ve la ficha completa.
     */
    private Node buildMedals() {
        var medals = com.rujiman.mediatracker.services.AchievementService.featured();
        if (medals.isEmpty()) return null;
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        box.setFillHeight(false);
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es"));
        for (var a : medals) {
            StackPane icon = new StackPane(Icons.of(Icons.byName(a.icon()), 14, a.accent()));
            icon.setMinSize(26, 26);
            icon.setMaxSize(26, 26);
            icon.setStyle("-fx-background-color: " + a.accent() + "33; -fx-background-radius: 999;");
            Label name = new Label(com.rujiman.mediatracker.services.AchievementService.displayName(a));
            name.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 13px; -fx-text-fill: " + TEXT + ";");
            name.setMaxWidth(240);
            HBox medal = new HBox(6, icon, name);
            medal.setAlignment(Pos.CENTER_LEFT);
            medal.setPadding(new Insets(4, 14, 4, 4));
            medal.setStyle("-fx-background-color: " + a.accent() + "1a; -fx-background-radius: 999;"
                    + "-fx-border-color: " + a.accent() + "66; -fx-border-radius: 999; -fx-cursor: hand;");
            var on = com.rujiman.mediatracker.services.AchievementService.unlockedOn(a);
            Tooltip tip = new Tooltip(com.rujiman.mediatracker.services.AchievementService.displayName(a) + "  ·  "
                    + com.rujiman.mediatracker.services.AchievementService.DIFFICULTY[a.difficulty()]
                    + "\n" + a.description() + (on != null ? "\nConseguido el " + fmt.format(on) : ""));
            tip.setShowDelay(javafx.util.Duration.millis(200));
            Tooltip.install(medal, tip);
            medal.setOnMouseClicked(e -> { if (onOpenAchievementsAction != null) onOpenAchievementsAction.run(); });
            box.getChildren().add(medal);
        }
        return box;
    }

    /** Botón "Mis reseñas" del banner: abre la misma pantalla que el menú lateral. */
    public void setOnOpenReviewsAction(Runnable action) {
        this.onOpenReviewsAction = action;
    }

    /** Recibe los estrenos de esta semana cada vez que se calculan (para el aviso del menú). */
    public void setOnWeekReleases(java.util.function.Consumer<List<com.rujiman.mediatracker.services.ReleaseService.Release>> action) {
        this.onWeekReleases = action;
    }

    /**
     * Pide en segundo plano los estrenos de esta semana. Al llegar, si
     * cambian, se repinta solo "Continúa donde lo dejaste" (con la etiqueta
     * "Nuevo ep. vie") y se avisa al menú. No añade nada más al Inicio.
     */
    private void loadWeekReleases() {
        java.util.Map<String, MediaItem> candidates = com.rujiman.mediatracker.services.ReleaseService.collectCandidates();
        int myRequest = ++weekRequest;
        Thread t = new Thread(() -> {
            List<com.rujiman.mediatracker.services.ReleaseService.Release> week;
            try {
                week = com.rujiman.mediatracker.services.ReleaseService.thisWeek(
                        com.rujiman.mediatracker.services.ReleaseService.load(candidates, false));
            } catch (Exception e) {
                System.err.println("⚠️ Estrenos del Inicio: " + e.getMessage());
                return;
            }
            javafx.application.Platform.runLater(() -> {
                if (myRequest != weekRequest) return;
                Map<String, com.rujiman.mediatracker.services.ReleaseService.Release> byKey = new java.util.HashMap<>();
                for (var r : week) byKey.putIfAbsent(r.key(), r);
                boolean changed = !byKey.keySet().equals(weekByKey.keySet())
                        || byKey.entrySet().stream().anyMatch(en -> !en.getValue().equals(weekByKey.get(en.getKey())));
                weekByKey = byKey;
                if (changed) rebuildContinueSection();
                if (onWeekReleases != null) onWeekReleases.accept(week);
            });
        }, "estrenos-inicio");
        t.setDaemon(true);
        t.start();
    }

    /** Hueco fijo de "Continúa donde lo dejaste", para repintarlo solo. */
    private final VBox continueSlot = new VBox();

    private void rebuildContinueSection() {
        Node cont = buildContinueSection();
        if (cont == null) {
            continueSlot.getChildren().clear();
            continueSlot.setVisible(false);
            continueSlot.setManaged(false);
        } else {
            continueSlot.getChildren().setAll(cont);
            continueSlot.setVisible(true);
            continueSlot.setManaged(true);
        }
    }

    /** "Nuevo ep. vie" en el color del tipo, si ese título estrena episodio esta semana. */
    private Node newEpisodeTag(MediaItem item) {
        if (item.getType() != MediaType.SERIES && item.getType() != MediaType.ANIME) return null;
        var r = weekByKey.get(WatchProgressService.keyFor(item.getType(), item.getTitle()).toLowerCase());
        if (r == null) return null;
        String accent = MediaTypeTheme.accentHex(item.getType());
        Label tag = new Label("Nuevo ep. " + com.rujiman.mediatracker.services.ReleaseService.shortDay(r));
        tag.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 11px; -fx-text-fill: " + accent + ";"
                + "-fx-background-color: " + accent + "22; -fx-background-radius: 999; -fx-padding: 1 8;");
        tag.setTooltip(new Tooltip(com.rujiman.mediatracker.services.ReleaseService.whenText(r)
                + (r.what() != null ? " · " + r.what() : "")));
        return tag;
    }

    private Node buildContinueCard(InProgress p, double width) {
        MediaType type = p.item().getType();
        String accent = MediaTypeTheme.accentHex(type);
        int done = p.progress().watchedEpisodes.size();
        int next = p.progress().watchedEpisodes.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;

        Node poster = CollectionUI.poster(p.item().getImageUrl(), 52, 78, accent, 8, true);

        Label typeLabel = new Label(MediaTypeTheme.label(type).toUpperCase());
        typeLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + accent + ";");
        HBox typeRow = new HBox(8, typeLabel);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        Node newTag = newEpisodeTag(p.item());
        if (newTag != null) typeRow.getChildren().add(newTag);
        Label title = new Label(p.item().getTitle());
        title.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 15px; -fx-text-fill: " + TEXT + ";");
        title.setMaxWidth(width - 110);

        double barW = width - 110;
        Region track = new Region();
        track.setPrefSize(barW, 6);
        track.setMaxSize(barW, 6);
        track.setStyle("-fx-background-color: " + LINE + "; -fx-background-radius: 3;");
        Region fill = new Region();
        double fraction = p.total() > 0 ? Math.min(1.0, (double) done / p.total()) : 0;
        fill.setPrefSize(barW * fraction, 6);
        fill.setMaxSize(barW * fraction, 6);
        fill.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 3;");
        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setMaxWidth(barW);

        String unit = MediaTypeTheme.unitSingular(type);
        boolean byUnits = p.total() > 0;
        if (!byUnits) {
            // Sin episodios (juego, peli, libro...): barra llena a medias no
            // tiene sentido; se muestra solo el estado
            bar.setVisible(false);
            bar.setManaged(false);
        }
        String seasonText = byUnits ? CollectionUI.nextBySeason(p.item(), p.progress(), p.total()) : null;
        Label status = new Label(!byUnits
                ? MediaTypeTheme.inProgressLabel(type)
                : seasonText != null ? seasonText
                : next <= p.total()
                    ? "Siguiente: " + unit.toLowerCase() + " " + next + " de " + p.total()
                    : done + " de " + p.total());
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + DIM + ";");

        VBox text = new VBox(6, typeRow, title, bar, status);
        HBox card = new HBox(14, poster, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12));
        card.setPrefWidth(width);
        card.setStyle(panelStyle(16) + "-fx-cursor: hand;");
        card.getStyleClass().add("motion-lift");
        card.setOnMouseClicked(e -> {
            if (e.isStillSincePress() && onOpenDetailAction != null) onOpenDetailAction.accept(p.item());
        });
        return card;
    }

    // ===========================================================
    // 3 y 4. VITRINAS
    // ===========================================================

    /** Hueco fijo que contiene la vitrina de un tipo (para poder reconstruirla sola). */
    private Pane showcaseSlot(MediaType type, boolean big) {
        StackPane slot = new StackPane();
        slot.setUserData(big);
        slot.getChildren().add(big ? buildBigShowcase(type) : buildMiniShowcase(type));
        showcaseSlots.put(type, slot);
        return slot;
    }

    private void rebuildShowcase(MediaType type) {
        Pane slot = showcaseSlots.get(type);
        if (slot == null) return;
        boolean big = Boolean.TRUE.equals(slot.getUserData());
        slot.getChildren().setAll(big ? buildBigShowcase(type) : buildMiniShowcase(type));
    }

    private HBox showcaseHeader(MediaType type, int count) {
        String accent = MediaTypeTheme.accentHex(type);
        Circle dot = new Circle(5, javafx.scene.paint.Color.web(accent));
        Label name = new Label(MediaTypeTheme.label(type).equals("Música") ? "Música" : MediaTypeTheme.plural(type));
        name.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: " + accent + ";");
        Label countLabel = new Label(count + " / " + DashboardService.getMaxItemsForSection(type));
        countLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + DIM + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button edit = new Button();
        edit.setGraphic(Icons.of(Icons.EDIT, 16, TEXT));
        edit.setTooltip(new Tooltip("Elegir " + MediaTypeTheme.plural(type).toLowerCase()));
        edit.setStyle("-fx-background-color: " + PANEL_2 + "; -fx-background-radius: 10; -fx-min-width: 36; -fx-min-height: 36; -fx-cursor: hand;");
        edit.setOnAction(e -> openSectionEditor(type, MediaTypeTheme.plural(type)));

        HBox header = new HBox(10, dot, name, countLabel, spacer, edit);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /** Juegos / Anime / Series / Películas: favorito absoluto + ranking 2–5. */
    private Node buildBigShowcase(MediaType type) {
        List<FavoriteItem> items = DashboardService.getSectionItems(type);
        String accent = MediaTypeTheme.accentHex(type);

        VBox card = new VBox(16);
        card.setPadding(new Insets(20));
        card.setPrefWidth(SHOWCASE_W);
        card.setMaxWidth(SHOWCASE_W);
        card.setStyle(panelStyle(20));
        card.getChildren().add(showcaseHeader(type, items.size()));

        // Favorito absoluto
        Node hero;
        if (!items.isEmpty()) {
            FavoriteItem first = items.get(0);
            StackPane box = new StackPane(CollectionUI.poster(first.getImageUrl(), HERO_W, HERO_H, accent, 14, true));
            Label tag = new Label("FAVORITO ABSOLUTO");
            tag.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 10px; -fx-text-fill: " + accent + ";");
            Label t = new Label(first.getTitle());
            t.setWrapText(true);
            t.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;");
            VBox caption = new VBox(2, tag, t);
            caption.setPadding(new Insets(10, 12, 12, 12));
            caption.setMaxHeight(Region.USE_PREF_SIZE);
            caption.setStyle("-fx-background-color: #100c1cd9; -fx-background-radius: 0 0 14 14;");
            StackPane.setAlignment(caption, Pos.BOTTOM_CENTER);
            box.getChildren().add(caption);
            box.setMaxSize(HERO_W, HERO_H);
            makeTile(box, first, type, accent);
            hero = box;
        } else {
            hero = emptyTile(HERO_W, HERO_H, "Elige tu favorito\nabsoluto", type);
        }

        // Del 2º al 5º en ranking
        VBox ranking = new VBox(8);
        HBox.setHgrow(ranking, Priority.ALWAYS);
        int max = DashboardService.getMaxItemsForSection(type);
        for (int i = 1; i < max; i++) {
            ranking.getChildren().add(i < items.size()
                    ? rankRow(i + 1, items.get(i), type, accent)
                    : emptyRow(i + 1, type));
        }

        HBox body = new HBox(16, hero, ranking);
        card.getChildren().add(body);
        return card;
    }

    private Node rankRow(int rank, FavoriteItem fav, MediaType type, String accent) {
        Label n = new Label(String.valueOf(rank));
        n.setMinWidth(26);
        n.setAlignment(Pos.CENTER);
        n.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 20px; -fx-text-fill: " + DIM + ";");
        Label title = new Label(fav.getTitle());
        title.setStyle("-fx-font-family: 'Inter SemiBold'; -fx-font-size: 14px; -fx-text-fill: " + TEXT + ";");
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        HBox row = new HBox(12, n, CollectionUI.poster(fav.getImageUrl(), 34, 51, accent, 6, true), title);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 10, 6, 6));
        row.setStyle("-fx-background-color: " + PANEL_2 + "; -fx-background-radius: 12; -fx-cursor: hand;");
        makeTile(row, fav, type, accent);
        return row;
    }

    private Node emptyRow(int rank, MediaType type) {
        Label n = new Label(String.valueOf(rank));
        n.setMinWidth(26);
        n.setAlignment(Pos.CENTER);
        n.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 20px; -fx-text-fill: #4a4170;");
        Label hint = new Label("Añadir");
        hint.setGraphic(Icons.of(Icons.PLUS, 14, DIM));
        hint.setGraphicTextGap(6);
        hint.setStyle("-fx-font-size: 13px; -fx-text-fill: " + DIM + ";");
        HBox row = new HBox(12, n, hint);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(63);
        row.setPadding(new Insets(6, 10, 6, 6));
        row.setStyle("-fx-border-color: " + Palette.LINE_STRONG + "; -fx-border-style: segments(6, 4); -fx-border-radius: 12; -fx-cursor: hand;");
        row.setOnMouseClicked(e -> openSectionEditor(type, MediaTypeTheme.plural(type)));
        return row;
    }

    /** Música / Libros / Manga: 4 portadas compactas. */
    private Node buildMiniShowcase(MediaType type) {
        List<FavoriteItem> items = DashboardService.getSectionItems(type);
        String accent = MediaTypeTheme.accentHex(type);
        int max = DashboardService.getMaxItemsForSection(type);

        VBox card = new VBox(14);
        card.setPadding(new Insets(20));
        card.setPrefWidth(MINI_W);
        card.setMaxWidth(MINI_W);
        card.setStyle(panelStyle(20));
        card.getChildren().add(showcaseHeader(type, items.size()));

        double tileW = (MINI_W - 40 - 3 * 10) / 4;              // 82.5
        double tileH = type == MediaType.MUSIC ? tileW : tileW * 1.5;
        HBox tiles = new HBox(10);
        for (int i = 0; i < max; i++) {
            if (i < items.size()) {
                FavoriteItem fav = items.get(i);
                StackPane box = new StackPane(CollectionUI.poster(fav.getImageUrl(), tileW, tileH, accent, 10, true));
                box.setMaxSize(tileW, tileH);
                Tooltip.install(box, new Tooltip(fav.getTitle()));
                makeTile(box, fav, type, accent);
                tiles.getChildren().add(box);
            } else {
                tiles.getChildren().add(emptyTile(tileW, tileH, "", type));
            }
        }
        // Todas las tarjetas compactas miden lo mismo; las portadas
        // cuadradas de Música se centran en vez de dejar hueco abajo
        tiles.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(tiles, Priority.ALWAYS);
        card.getChildren().add(tiles);
        return card;
    }

    private Node emptyTile(double w, double h, String text, MediaType type) {
        Label label = new Label(text);
        label.setGraphic(Icons.of(Icons.PLUS, 18, DIM));
        label.setContentDisplay(ContentDisplay.TOP);
        label.setGraphicTextGap(8);
        label.setWrapText(true);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: " + DIM + ";");
        StackPane box = new StackPane(label);
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        box.setStyle("-fx-border-color: " + Palette.LINE_STRONG + "; -fx-border-style: segments(6, 4); -fx-border-radius: 12; -fx-cursor: hand;");
        box.setOnMouseClicked(e -> openSectionEditor(type, MediaTypeTheme.plural(type)));
        return box;
    }

    // ===========================================================
    // TARJETAS: imagen, clic y arrastrar para reordenar
    // ===========================================================

    private void fadeIn(Node n) {
        javafx.animation.FadeTransition f = new javafx.animation.FadeTransition(Motion.BASE, n);
        f.setToValue(1);
        f.setInterpolator(Motion.EASE_OUT);
        f.play();
    }

    /**
     * Convierte un nodo en tarjeta de vitrina: se eleva al pasar el ratón,
     * abre el detalle al hacer clic y se puede arrastrar sobre otra tarjeta
     * de la misma sección para cambiarla de posición (la nº1 es el
     * favorito absoluto).
     */
    private void makeTile(Node tile, FavoriteItem fav, MediaType type, String accent) {
        tile.getStyleClass().add("motion-lift");
        tile.setStyle((tile.getStyle() == null ? "" : tile.getStyle()) + "-fx-cursor: hand;");

        tile.setOnMouseClicked(e -> {
            if (!e.isStillSincePress()) return; // fue un arrastre
            if (onOpenDetailAction != null) onOpenDetailAction.accept(fav.toMediaItem());
        });

        tile.setOnDragDetected(e -> {
            draggedId = fav.getId();
            draggedType = type;
            Dragboard db = tile.startDragAndDrop(TransferMode.MOVE);
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            db.setDragView(tile.snapshot(params, null), e.getX(), e.getY());
            ClipboardContent content = new ClipboardContent();
            content.putString(fav.getId());
            db.setContent(content);
            tile.setOpacity(0.35);
            e.consume();
        });

        tile.setOnDragOver(e -> {
            if (draggedId != null && draggedType == type && !draggedId.equals(fav.getId())) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        tile.setOnDragEntered(e -> {
            if (draggedId != null && draggedType == type && !draggedId.equals(fav.getId())) {
                tile.setEffect(new javafx.scene.effect.DropShadow(18, javafx.scene.paint.Color.web(accent)));
            }
        });
        tile.setOnDragExited(e -> tile.setEffect(null));

        tile.setOnDragDropped(e -> {
            boolean ok = false;
            if (draggedId != null && draggedType == type) {
                List<String> ids = new ArrayList<>();
                for (FavoriteItem f : DashboardService.getSectionItems(type)) ids.add(f.getId());
                int to = ids.indexOf(fav.getId());
                if (ids.remove(draggedId) && to >= 0) {
                    ids.add(to, draggedId);
                    DashboardService.setSectionItems(type, ids);
                    ok = true;
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });

        tile.setOnDragDone(e -> {
            tile.setOpacity(1);
            boolean moved = e.isAccepted() && draggedType == type;
            draggedId = null;
            draggedType = null;
            if (moved) {
                rebuildShowcase(type);
                Pane slot = showcaseSlots.get(type);
                if (slot != null && !slot.getChildren().isEmpty()) Motion.enter(slot.getChildren().get(0));
            }
            e.consume();
        });
    }

    // ===========================================================
    // BOTONES, CHIPS Y ESTILOS COMUNES
    // ===========================================================

    private static String panelStyle(int radius) {
        return "-fx-background-color: " + PANEL + "; -fx-background-radius: " + radius + ";"
                + "-fx-border-color: " + LINE + "; -fx-border-radius: " + radius + "; -fx-border-width: 1;";
    }

    private Button primaryButton(String text, String icon, String color) {
        Button b = new Button(text);
        b.setGraphic(Icons.of(icon, 16, "#ffffff"));
        b.setGraphicTextGap(8);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-family: 'Inter'; -fx-font-weight: bold;"
                + "-fx-font-size: 14px; -fx-background-radius: 12; -fx-padding: 0 20; -fx-min-height: 44; -fx-cursor: hand;");
        return b;
    }

    private Button secondaryButton(String text, String icon) {
        Button b = new Button(text);
        b.setGraphic(Icons.of(icon, 16, TEXT));
        b.setGraphicTextGap(8);
        b.setStyle("-fx-background-color: transparent; -fx-border-color: " + Palette.LINE_STRONG + "; -fx-border-radius: 12; -fx-background-radius: 12;"
                + "-fx-text-fill: " + TEXT + "; -fx-font-family: 'Inter SemiBold'; -fx-font-size: 14px; -fx-padding: 0 18; -fx-min-height: 44; -fx-cursor: hand;");
        return b;
    }

    /** Un número grande con su etiqueta debajo (estadísticas del perfil). */
    private Node stat(String value, String label) {
        Label v = new Label(value);
        v.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 24px; -fx-text-fill: " + TEXT + ";");
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + DIM + ";");
        VBox box = new VBox(0, v, l);
        box.setAlignment(Pos.BOTTOM_CENTER); // número centrado sobre su texto
        return box;
    }

    private static String formatInt(int n) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es")).format(n);
    }

    private Node chip(String dotColor, String text) {
        Label l = new Label(text);
        l.setGraphic(new Circle(4, javafx.scene.paint.Color.web(dotColor)));
        l.setGraphicTextGap(8);
        l.setStyle("-fx-background-color: " + Palette.PANEL_3 + "; -fx-background-radius: 999; -fx-padding: 6 12; -fx-font-size: 13px; -fx-text-fill: " + Palette.TEXT_SOFT + ";");
        return l;
    }

    // ===========================================================
    // EDITAR Y COMPARTIR
    // ===========================================================

    /** "Editar vitrina": elige qué sección editar y abre su selector. */
    private void chooseSectionToEdit() {
        List<String> names = new ArrayList<>();
        for (MediaType t : BIG) names.add(MediaTypeTheme.plural(t));
        for (MediaType t : SMALL) names.add(MediaTypeTheme.plural(t));
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        ownDialog(dialog);
        dialog.setTitle("Editar vitrina");
        dialog.setHeaderText(null);
        dialog.setContentText("¿Qué sección quieres editar?");
        Dialogs.styleDark(dialog.getDialogPane());
        Dialogs.darkenWindow(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(choice -> {
            for (MediaType t : MediaType.values()) {
                if (MediaTypeTheme.plural(t).equals(choice)) openSectionEditor(t, choice);
            }
        });
    }

    /**
     * "Compartir perfil": guarda la vitrina completa como imagen PNG (al
     * doble de resolución, para que se vea nítida en redes o en Twitch).
     * Cuando MediaVerse sea red social, este botón compartirá el enlace
     * al perfil público.
     */
    private void exportVitrina() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Guardar mi vitrina como imagen");
        chooser.setInitialFileName("mi-mediaverse.png");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imagen PNG", "*.png"));
        File file = chooser.showSaveDialog(homeRoot.getScene().getWindow());
        if (file == null) return;

        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(javafx.scene.paint.Color.web(Palette.BG));
        params.setTransform(javafx.scene.transform.Transform.scale(2, 2));
        try {
            PngExporter.save(homeRoot.snapshot(params, null), file);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ownDialog(ok);
            Dialogs.styleDark(ok.getDialogPane());
            Dialogs.darkenWindow(ok.getDialogPane());
            ok.setHeaderText(null);
            ok.setContentText("Vitrina guardada en:\n" + file.getAbsolutePath());
            ok.show();
        } catch (Exception ex) {
            System.err.println("❌ Error al exportar la vitrina: " + ex.getMessage());
        }
    }

    /**
     * Abre un selector visual con tarjetas (imagen + título) de todos los
     * favoritos de ese tipo. Pinchar una tarjeta la marca/desmarca (borde
     * rosa cuando está seleccionada). Al llegar al límite de la sección,
     * las tarjetas no seleccionadas quedan atenuadas y no se pueden marcar
     * hasta que se quite alguna.
     */
    private void openSectionEditor(MediaType type, String sectionDisplayName) {
        List<FavoriteItem> candidates = FavoritesService.getFavoritesByType(type);
        String accentHex = MediaTypeTheme.accentHex(type);

        if (candidates.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            ownDialog(alert);
            Dialogs.styleDark(alert.getDialogPane());
            Dialogs.darkenWindow(alert.getDialogPane());
            alert.setHeaderText(null);
            alert.setContentText("Todavía no tienes favoritos de " + sectionDisplayName +
                    ".\nBusca contenido y añádelo a favoritos (⭐) para poder elegirlo aquí.");
            alert.show();
            return;
        }

        List<String> currentIds = DashboardService.getSectionIds(type);
        int max = DashboardService.getMaxItemsForSection(type);

        // Selección en curso, mutable mientras el diálogo está abierto
        List<String> selectedIds = new ArrayList<>(currentIds);

        Dialog<List<String>> dialog = new Dialog<>();
        ownDialog(dialog);
        dialog.setTitle("Elegir " + sectionDisplayName + " para Mi MediaVerse");
        Dialogs.styleDark(dialog.getDialogPane());

        Label limitLabel = new Label();
        limitLabel.getStyleClass().add("text-dim");
        limitLabel.setStyle("-fx-padding: 0 0 10 0;"); // espaciado puntual

        FlowPane grid = new FlowPane(10, 10);

        List<javafx.scene.layout.StackPane> cards = new ArrayList<>();

        // Cada cambio repinta TODAS las tarjetas: al quitar una, las demás se
        // desbloquean al momento y se puede elegir otra sin guardar y volver a abrir.
        Runnable updateLimitState = () -> {
            boolean full = selectedIds.size() >= max;
            limitLabel.setText("Seleccionados: " + selectedIds.size() + " / " + max
                    + (full ? "  ·  Quita uno para poder elegir otro" : ""));
            for (javafx.scene.layout.StackPane c : cards) {
                Object refresh = c.getProperties().get("refresh");
                if (refresh instanceof Runnable r) r.run();
            }
        };

        for (FavoriteItem fav : candidates) {
            javafx.scene.layout.StackPane card = buildSelectableCard(fav, selectedIds, max, updateLimitState, accentHex);
            cards.add(card);
            grid.getChildren().add(card);
        }

        updateLimitState.run();

        VBox content = new VBox(0, limitLabel, grid);
        content.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(560, 420);
        scroll.setStyle("-fx-background-color: " + Palette.PANEL_ALT + "; -fx-background: " + Palette.PANEL_ALT + "; -fx-border-color: transparent;");

        // El .viewport interno de un ScrollPane es un nodo aparte que no
        // hereda fondo del propio ScrollPane (mismo bug ya resuelto en
        // HomeController.applyViewportBackground() y DetailViewController
        // para los ScrollPane principales de la app) — aquí no existía
        // hasta ahora porque este ScrollPane se crea por código en vez de
        // venir de un FXML, así que nunca se le aplicó el mismo parche.
        scroll.applyCss();
        javafx.scene.Node viewport = scroll.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");
        } else {
            javafx.application.Platform.runLater(() -> {
                javafx.scene.Node vp = scroll.lookup(".viewport");
                if (vp != null) vp.setStyle("-fx-background-color: " + Palette.PANEL_ALT + ";");
            });
        }

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Los botones por defecto del diálogo se pintan claros; forzamos su estilo
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (okBtn != null) okBtn.getStyleClass().add("btn-pill-brand");
        if (cancelBtn != null) cancelBtn.getStyleClass().add("btn-pill-neutral");

        // El DialogPane (el contenido) ya se pinta oscuro con Dialogs.styleDark(),
        // pero la VENTANA que lo envuelve (su propio Stage/Scene, separado del
        // de la app) usa por defecto el chrome claro del sistema operativo
        // alrededor del contenido — por eso se veía un marco blanco rodeando
        // el selector. Forzamos también el fondo de esa Scene nativa.
        Dialogs.darkenWindow(dialog.getDialogPane());

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? selectedIds : null);

        dialog.showAndWait().ifPresent(finalIds -> {
            DashboardService.setSectionItems(type, finalIds);
            refreshAll();
        });
    }

    /**
     * Devuelve el hex de acento de la sección, según la paleta
     * "Constelaciones" de theme.css (duplicado aquí porque los colores
     * dinámicos de un Dialog no pueden referenciar lookup colors de CSS
     * directamente desde Java sin acceso a la Scene ya construida).
     */
    /** @deprecated Usar {@link MediaTypeTheme#accentHex(MediaType)} directamente. */
    @Deprecated
    /**
     * Tarjeta seleccionable para el selector de secciones: imagen + título,
     * con un botón circular en la esquina ("+" para añadir, "✓" cuando ya
     * está seleccionada) usando el color de acento de la sección actual
     * (cian para Juegos, violeta para Series, etc.) en vez de un rojo/verde
     * fijo, para que el selector se sienta parte de "esa" sección concreta.
     * Pinchar la imagen O el botón marca/desmarca igual.
     */
    private javafx.scene.layout.StackPane buildSelectableCard(
            FavoriteItem fav, List<String> selectedIds, int max, Runnable onChange, String accentHex) {

        double width = 100, height = 140;

        javafx.scene.layout.StackPane card = new javafx.scene.layout.StackPane();
        card.setPrefSize(width, height + 28);
        card.setStyle("-fx-cursor: hand; -fx-background-color: transparent;");

        VBox content = new VBox(4);
        content.setAlignment(Pos.TOP_CENTER);

        ImageView cover = new ImageView();
        cover.setFitWidth(width);
        cover.setFitHeight(height);
        cover.setPreserveRatio(false);
        cover.setSmooth(true);

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        cover.setClip(clip);

        if (fav.getImageUrl() != null && !fav.getImageUrl().isBlank()) {
            try {
                cover.setImage(new Image(fav.getImageUrl(), width * 2, height * 2, false, true, true));
            } catch (Exception ignored) {}
        }

        Label titleLabel = new Label(fav.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(width);
        titleLabel.getStyleClass().add("text-heading");
        titleLabel.setStyle("-fx-font-size: 10px; -fx-text-alignment: center;"); // tamaño/alineación puntuales
        titleLabel.setAlignment(Pos.TOP_CENTER);

        javafx.scene.layout.StackPane imageContainer = new javafx.scene.layout.StackPane(cover);
        imageContainer.setPrefSize(width, height);
        imageContainer.setMaxSize(width, height);

        // Botón circular de añadir/quitar, esquina superior derecha de la imagen.
        Label addButton = new Label();
        addButton.setMinSize(26, 26);
        addButton.setMaxSize(26, 26);
        addButton.setAlignment(Pos.CENTER);
        javafx.scene.layout.StackPane.setAlignment(addButton, Pos.TOP_RIGHT);
        javafx.scene.layout.StackPane.setMargin(addButton, new javafx.geometry.Insets(6));

        imageContainer.getChildren().add(addButton);

        content.getChildren().addAll(imageContainer, titleLabel);
        card.getChildren().add(content);

        Runnable updateBorder = () -> {
            boolean selected = selectedIds.contains(fav.getId());

            if (selected) {
                imageContainer.setStyle("-fx-border-color: " + accentHex + "; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
                addButton.setText("✓");
                addButton.setStyle(
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;" +
                                "-fx-background-color: " + accentHex + ";" +
                                "-fx-background-radius: 13;" +
                                "-fx-effect: dropshadow(gaussian, #000000aa, 4, 0, 0, 1);"
                );
            } else {
                imageContainer.setStyle("-fx-border-color: transparent; -fx-border-width: 3;");
                addButton.setText("+");
                addButton.setStyle(
                        "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;" +
                                "-fx-background-color: " + accentHex + ";" +
                                "-fx-background-radius: 13;" +
                                "-fx-effect: dropshadow(gaussian, #000000aa, 4, 0, 0, 1);"
                );
            }
            boolean limitReached = !selected && selectedIds.size() >= max;
            card.setOpacity(limitReached ? 0.35 : 1.0);
            card.setDisable(limitReached);
        };

        updateBorder.run();
        card.getProperties().put("refresh", updateBorder);

        card.setOnMouseClicked(e -> {
            boolean selected = selectedIds.contains(fav.getId());
            if (selected) {
                selectedIds.remove(fav.getId());
            } else {
                if (selectedIds.size() >= max) return; // protección extra
                selectedIds.add(fav.getId());
            }
            onChange.run(); // repinta esta y todas las demás
        });

        return card;
    }

}