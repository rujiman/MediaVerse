package com.rujiman.mediatracker.controllers;

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
    private static final double PAGE_W = 1240;
    private static final double GAP = 20;
    private static final double SHOWCASE_W = (PAGE_W - GAP) / 2;      // 610
    private static final double MINI_W = (PAGE_W - 2 * GAP) / 3;      // 400
    private static final double HERO_W = 176, HERO_H = 264;

    // ── Colores de superficie (mismos que theme.css) ───────────────────
    private static final String PANEL = "#1a1530";
    private static final String PANEL_2 = "#211a39";
    private static final String LINE = "#2a2347";
    private static final String TEXT = "#f0eef5";
    private static final String DIM = "#a59fc0";

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
        homeScroll.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) applyViewportBackground();
        });
        if (homeScroll.getSkin() != null) applyViewportBackground();

        DashboardService.pruneDeletedFavorites();
        refreshAll();
    }

    private void applyViewportBackground() {
        Node viewport = homeScroll.lookup(".viewport");
        if (viewport != null) viewport.setStyle("-fx-background-color: #100c1c;");
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

        Node cont = buildContinueSection();
        if (cont != null) homeRoot.getChildren().add(cont);

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
            cropToFill(bg, img, PAGE_W, coverH, () -> fadeIn(bg));
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
        Button share = primaryButton("Compartir perfil", Icons.SHARE, "#ec4d80");
        share.setOnAction(e -> exportVitrina());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox identity = new HBox(12, avatar, spacer, edit, share);
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
        nameBox.setPadding(new Insets(14, 32, 18, 32));

        // ── Contadores por tipo ─────────────────────────────────────────
        FlowPane chips = new FlowPane(8, 8);
        chips.setPadding(new Insets(0, 32, 26, 32));
        for (MediaType t : new MediaType[]{MediaType.GAME, MediaType.ANIME, MediaType.SERIES, MediaType.MOVIE,
                MediaType.MUSIC, MediaType.BOOK, MediaType.MANGA}) {
            int count = FavoritesService.getFavoritesByType(t).size();
            chips.getChildren().add(chip(MediaTypeTheme.accentHex(t), pluralName(t) + "  " + count));
        }

        banner.getChildren().addAll(cover, identity, nameBox, chips);
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

    /**
     * Recorta la imagen al centro para que RELLENE w×h sin deformarse
     * (como object-fit: cover en CSS). Si la imagen aún se está
     * descargando, espera a que termine. onReady se ejecuta al aplicarlo.
     */
    private void cropToFill(ImageView view, Image img, double w, double h, Runnable onReady) {
        Runnable apply = () -> {
            double iw = img.getWidth(), ih = img.getHeight();
            if (iw <= 0 || ih <= 0) return;
            double target = w / h;
            double cw = iw, ch = iw / target;
            if (ch > ih) { ch = ih; cw = ih * target; }
            // Para portadas verticales se centra un poco más arriba (las caras suelen estar arriba)
            double y = (ih - ch) * (ih > iw ? 0.3 : 0.5);
            view.setViewport(new javafx.geometry.Rectangle2D((iw - cw) / 2, y, cw, ch));
            if (onReady != null) onReady.run();
        };
        if (img.getProgress() >= 1.0 && !img.isError()) apply.run();
        else img.progressProperty().addListener((o, a, b) -> {
            if (b.doubleValue() >= 1.0 && !img.isError()) apply.run();
        });
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
            cropToFill(img, source, inner, inner, null);
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
            "#ec4d80", "#8b5cf6", "#ec4dc0", "#4dd9ec", "#4dec9e",
            "#ecb14d", "#f97316", "#ef4444", "#2b1f4a", "#1a1530"
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
        amountLabel.setStyle("-fx-text-fill: #a59fc0; -fx-font-size: 12px;");
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
        title.setStyle("-fx-text-fill: #a59fc0; -fx-font-size: 12px;");
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
        other.setStyle("-fx-background-color: #241d3d; -fx-text-fill: #f0eef5; -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand;");
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
        picker.setStyle("-fx-background-color: #241d3d; -fx-background-radius: 8;");
        Dialog<javafx.scene.paint.Color> dialog = new Dialog<>();
        ownDialog(dialog);
        dialog.setTitle("Color de la portada");
        styleDialogDark(dialog.getDialogPane());
        darkenDialogWindow(dialog.getDialogPane());
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
        styleDialogDark(dialog.getDialogPane());
        darkenDialogWindow(dialog.getDialogPane());

        FlowPane grid = new FlowPane(10, 10);
        grid.setPrefWrapLength(560);
        grid.setStyle("-fx-background-color: #1c1730;");
        for (FavoriteItem f : favs) {
            Node thumb = poster(f.getImageUrl(), 96, 144, 10, MediaTypeTheme.accentHex(f.getType()));
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
        scroll.setStyle("-fx-background-color: #1c1730; -fx-background: #1c1730; -fx-border-color: transparent;");
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

    private Node buildContinueCard(InProgress p, double width) {
        MediaType type = p.item().getType();
        String accent = MediaTypeTheme.accentHex(type);
        int done = p.progress().watchedEpisodes.size();
        int next = p.progress().watchedEpisodes.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;

        Node poster = poster(p.item().getImageUrl(), 52, 78, 8, accent);

        Label typeLabel = new Label(MediaTypeTheme.label(type).toUpperCase());
        typeLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + accent + ";");
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
        Label status = new Label(!byUnits
                ? MediaTypeTheme.inProgressLabel(type)
                : next <= p.total()
                    ? "Siguiente: " + unit.toLowerCase() + " " + next + " de " + p.total()
                    : done + " de " + p.total());
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + DIM + ";");

        VBox text = new VBox(6, typeLabel, title, bar, status);
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
        Label name = new Label(MediaTypeTheme.label(type).equals("Música") ? "Música" : pluralName(type));
        name.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: " + accent + ";");
        Label countLabel = new Label(count + " / " + DashboardService.getMaxItemsForSection(type));
        countLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + DIM + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button edit = new Button();
        edit.setGraphic(Icons.of(Icons.EDIT, 16, TEXT));
        edit.setTooltip(new Tooltip("Elegir " + pluralName(type).toLowerCase()));
        edit.setStyle("-fx-background-color: " + PANEL_2 + "; -fx-background-radius: 10; -fx-min-width: 36; -fx-min-height: 36; -fx-cursor: hand;");
        edit.setOnAction(e -> openSectionEditor(type, pluralName(type)));

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
            StackPane box = new StackPane(poster(first.getImageUrl(), HERO_W, HERO_H, 14, accent));
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

        HBox row = new HBox(12, n, poster(fav.getImageUrl(), 34, 51, 6, accent), title);
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
        row.setStyle("-fx-border-color: #3a3160; -fx-border-style: segments(6, 4); -fx-border-radius: 12; -fx-cursor: hand;");
        row.setOnMouseClicked(e -> openSectionEditor(type, pluralName(type)));
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
                StackPane box = new StackPane(poster(fav.getImageUrl(), tileW, tileH, 10, accent));
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
        box.setStyle("-fx-border-color: #3a3160; -fx-border-style: segments(6, 4); -fx-border-radius: 12; -fx-cursor: hand;");
        box.setOnMouseClicked(e -> openSectionEditor(type, pluralName(type)));
        return box;
    }

    // ===========================================================
    // TARJETAS: imagen, clic y arrastrar para reordenar
    // ===========================================================

    /**
     * Portada con esquinas redondeadas. La imagen se pide al doble de
     * resolución y en segundo plano (no bloquea la vitrina); mientras
     * carga se ve un rectángulo tintado del color de la sección.
     */
    private Node poster(String url, double w, double h, double radius, String accent) {
        StackPane box = new StackPane();
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        box.setStyle("-fx-background-color: " + accent + "22; -fx-background-radius: " + radius + ";");
        if (url != null && !url.isBlank()) {
            ImageView img = new ImageView();
            img.setFitWidth(w);
            img.setFitHeight(h);
            img.setPreserveRatio(false);
            img.setSmooth(true);
            Rectangle clip = new Rectangle(w, h);
            clip.setArcWidth(radius * 2);
            clip.setArcHeight(radius * 2);
            img.setClip(clip);
            try {
                // Se pide manteniendo proporción (a ~2x) y se recorta al
                // centro: así ninguna portada se ve aplastada o estirada
                Image image = new Image(url, w * 2, h * 2, true, true, true);
                img.setImage(image);
                img.setOpacity(0);
                cropToFill(img, image, w, h, () -> fadeIn(img));
            } catch (Exception ignored) {}
            box.getChildren().add(img);
        }
        return box;
    }

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
        b.setStyle("-fx-background-color: transparent; -fx-border-color: #3a3160; -fx-border-radius: 12; -fx-background-radius: 12;"
                + "-fx-text-fill: " + TEXT + "; -fx-font-family: 'Inter SemiBold'; -fx-font-size: 14px; -fx-padding: 0 18; -fx-min-height: 44; -fx-cursor: hand;");
        return b;
    }

    private Node chip(String dotColor, String text) {
        Label l = new Label(text);
        l.setGraphic(new Circle(4, javafx.scene.paint.Color.web(dotColor)));
        l.setGraphicTextGap(8);
        l.setStyle("-fx-background-color: #241d3d; -fx-background-radius: 999; -fx-padding: 6 12; -fx-font-size: 13px; -fx-text-fill: #e6e1f5;");
        return l;
    }

    private String pluralName(MediaType type) {
        return switch (type) {
            case GAME -> "Juegos";
            case SERIES -> "Series";
            case ANIME -> "Anime";
            case MUSIC -> "Música";
            case MOVIE -> "Películas";
            case BOOK -> "Libros";
            case MANGA -> "Manga";
        };
    }

    // ===========================================================
    // EDITAR Y COMPARTIR
    // ===========================================================

    /** "Editar vitrina": elige qué sección editar y abre su selector. */
    private void chooseSectionToEdit() {
        List<String> names = new ArrayList<>();
        for (MediaType t : BIG) names.add(pluralName(t));
        for (MediaType t : SMALL) names.add(pluralName(t));
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        ownDialog(dialog);
        dialog.setTitle("Editar vitrina");
        dialog.setHeaderText(null);
        dialog.setContentText("¿Qué sección quieres editar?");
        styleDialogDark(dialog.getDialogPane());
        darkenDialogWindow(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(choice -> {
            for (MediaType t : MediaType.values()) {
                if (pluralName(t).equals(choice)) openSectionEditor(t, choice);
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
        params.setFill(javafx.scene.paint.Color.web("#100c1c"));
        params.setTransform(javafx.scene.transform.Transform.scale(2, 2));
        try {
            PngExporter.save(homeRoot.snapshot(params, null), file);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ownDialog(ok);
            styleDialogDark(ok.getDialogPane());
            darkenDialogWindow(ok.getDialogPane());
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
        String accentHex = sectionAccentHex(type);

        if (candidates.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            ownDialog(alert);
            styleDialogDark(alert.getDialogPane());
            darkenDialogWindow(alert.getDialogPane());
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
        styleDialogDark(dialog.getDialogPane());

        Label limitLabel = new Label();
        limitLabel.getStyleClass().add("text-dim");
        limitLabel.setStyle("-fx-padding: 0 0 10 0;"); // espaciado puntual

        FlowPane grid = new FlowPane(10, 10);

        Runnable updateLimitState = () -> limitLabel.setText("Seleccionados: " + selectedIds.size() + " / " + max);

        List<javafx.scene.layout.StackPane> cards = new ArrayList<>();

        for (FavoriteItem fav : candidates) {
            javafx.scene.layout.StackPane card = buildSelectableCard(fav, selectedIds, max, updateLimitState, accentHex);
            cards.add(card);
            grid.getChildren().add(card);
        }

        updateLimitState.run();

        VBox content = new VBox(0, limitLabel, grid);
        content.setStyle("-fx-background-color: #1c1730;");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(560, 420);
        scroll.setStyle("-fx-background-color: #1c1730; -fx-background: #1c1730; -fx-border-color: transparent;");

        // El .viewport interno de un ScrollPane es un nodo aparte que no
        // hereda fondo del propio ScrollPane (mismo bug ya resuelto en
        // HomeController.applyViewportBackground() y DetailViewController
        // para los ScrollPane principales de la app) — aquí no existía
        // hasta ahora porque este ScrollPane se crea por código en vez de
        // venir de un FXML, así que nunca se le aplicó el mismo parche.
        scroll.applyCss();
        javafx.scene.Node viewport = scroll.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: #1c1730;");
        } else {
            javafx.application.Platform.runLater(() -> {
                javafx.scene.Node vp = scroll.lookup(".viewport");
                if (vp != null) vp.setStyle("-fx-background-color: #1c1730;");
            });
        }

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Los botones por defecto del diálogo se pintan claros; forzamos su estilo
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (okBtn != null) okBtn.getStyleClass().add("btn-pill-brand");
        if (cancelBtn != null) cancelBtn.getStyleClass().add("btn-pill-neutral");

        // El DialogPane (el contenido) ya se pinta oscuro con styleDialogDark(),
        // pero la VENTANA que lo envuelve (su propio Stage/Scene, separado del
        // de la app) usa por defecto el chrome claro del sistema operativo
        // alrededor del contenido — por eso se veía un marco blanco rodeando
        // el selector. Forzamos también el fondo de esa Scene nativa.
        darkenDialogWindow(dialog.getDialogPane());

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
    private String sectionAccentHex(MediaType type) {
        return MediaTypeTheme.accentHex(type);
    }

    /**
     * Fuerza fondo oscuro en la ventana (Stage/Scene) que envuelve un
     * Dialog/Alert, no solo en su DialogPane. Sin esto, el marco que
     * rodea el contenido del diálogo se queda con el tema claro por
     * defecto del sistema operativo.
     */
    private void darkenDialogWindow(DialogPane pane) {
        if (pane.getScene() != null) {
            pane.getScene().setFill(javafx.scene.paint.Color.web("#100c1c"));
        } else {
            // La Scene puede no existir todavía en el momento de llamar a
            // esto; nos suscribimos para aplicarlo en cuanto se asigne.
            pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setFill(javafx.scene.paint.Color.web("#100c1c"));
                }
            });
        }
    }

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

        card.setOnMouseClicked(e -> {
            boolean selected = selectedIds.contains(fav.getId());
            if (selected) {
                selectedIds.remove(fav.getId());
            } else {
                if (selectedIds.size() >= max) return; // protección extra
                selectedIds.add(fav.getId());
            }
            updateBorder.run();
            onChange.run();
        });

        return card;
    }

    /**
     * Fuerza la paleta oscura de la app en un DialogPane de JavaFX, que
     * por defecto usa el tema claro del sistema. El problema real no es
     * solo el DialogPane en sí, sino un "header-panel" interno que JavaFX
     * añade automáticamente por encima del contenido cuando el Dialog
     * tiene título (visible como una franja blanca entre la barra de
     * título del sistema y el contenido real) — ese header-panel no se
     * pinta con pane.setStyle() porque vive dentro del propio DialogPane
     * como un nodo hijo, así que hay que buscarlo explícitamente con
     * lookup() y forzar también su fondo.
     */
    private void styleDialogDark(DialogPane pane) {
        pane.getStylesheets().add(
                getClass().getResource("/com/rujiman/mediatracker/views/theme.css").toExternalForm()
        );
        pane.getStyleClass().add("bg-panel-flat");
        pane.setStyle(
                "-fx-background-color: #1c1730;" +
                        "-fx-text-fill: #f0eef5;"
        );

        // El header-panel (franja con el título grande/icono que JavaFX
        // pinta automáticamente) y el content.label no heredan el fondo
        // del DialogPane; hay que forzarlos aparte y, como no existen
        // hasta que el Dialog se muestra, nos suscribimos a su aparición.
        pane.applyCss();
        javafx.scene.Node headerPanel = pane.lookup(".header-panel");
        if (headerPanel != null) {
            headerPanel.setStyle("-fx-background-color: #1c1730;");
        } else {
            // Si el header-panel todavía no existe (Dialog recién creado,
            // sin mostrar), reintentamos en el próximo pulso de layout.
            javafx.application.Platform.runLater(() -> {
                javafx.scene.Node hp = pane.lookup(".header-panel");
                if (hp != null) hp.setStyle("-fx-background-color: #1c1730;");
            });
        }

        // El texto de contenido por defecto de un Alert vive anidado dentro
        // de un GridPane interno, no como hijo directo del DialogPane, así
        // que recorremos el árbol completo para no dejar texto invisible.
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
}