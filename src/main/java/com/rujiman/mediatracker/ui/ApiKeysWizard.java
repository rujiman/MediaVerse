package com.rujiman.mediatracker.ui;

import com.rujiman.mediatracker.services.ApiKeyChecker;
import com.rujiman.mediatracker.services.ApiKeyChecker.Result;
import com.rujiman.mediatracker.services.ApiKeyChecker.Status;
import com.rujiman.mediatracker.services.GameService;
import com.rujiman.mediatracker.util.Browser;
import com.rujiman.mediatracker.util.ConfigLoader;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Asistente de claves de API, paso a paso:
 *   1. TMDB (películas y series)  — obligatoria
 *   2. Twitch / IGDB (juegos)     — obligatoria
 *   3. Google Books (libros)      — opcional
 *
 * Cada paso explica cómo conseguir la clave, abre la página con un botón,
 * y al pulsar "Comprobar" hace una petición real al servicio: solo se
 * avanza si la clave funciona (o si no hay conexión y el usuario decide
 * guardarla igualmente). Las claves se guardan con
 * ConfigLoader.saveUserKeys (userdata/claves_api.env), así que nadie
 * tiene que tocar el .env a mano.
 *
 * Se usa antes del login la primera vez (LoginController) y desde el
 * menú de perfil → "Claves de API" (SearchController).
 */
public final class ApiKeysWizard {

    private record Field(String key, String label, String prompt) {}

    private record Step(String tag, String accent, String title, String subtitle,
                        List<String> howTo, String url, String urlLabel,
                        List<Field> fields, boolean optional,
                        Function<Map<String, String>, Result> check) {}

    private final List<Step> steps = List.of(
            new Step("PELÍCULAS Y SERIES", "#b69cfa",
                    "Conecta TMDB",
                    "Las películas y series salen de TMDB. La clave es gratis y se saca en dos minutos.",
                    List.of("Crea una cuenta gratis en TMDB, o inicia sesión.",
                            "En Ajustes → API pulsa «Create» y elige «Developer». En el formulario vale cualquier descripción.",
                            "Copia la «API Key» (la corta, de 32 caracteres) y pégala aquí."),
                    "https://www.themoviedb.org/settings/api", "Abrir TMDB",
                    List.of(new Field("TMDB_API_KEY", "API Key", "32 letras y números")),
                    false,
                    v -> ApiKeyChecker.checkTmdb(v.get("TMDB_API_KEY"))),
            new Step("VIDEOJUEGOS", "#4dd9ec",
                    "Conecta Twitch (IGDB)",
                    "Los juegos salen de IGDB, que es de Twitch. Necesitas una cuenta de Twitch, también gratis.",
                    List.of("Entra en la consola de desarrolladores de Twitch. Twitch pide tener activada la verificación en dos pasos.",
                            "Pulsa «Register Your Application»: nombre el que quieras, OAuth Redirect URL http://localhost y categoría «Application Integration».",
                            "Abre la aplicación creada, copia el «Client ID», pulsa «New Secret» y copia el secret."),
                    "https://dev.twitch.tv/console/apps", "Abrir Twitch",
                    List.of(new Field("IGDB_CLIENT_ID", "Client ID", "Pega el Client ID"),
                            new Field("IGDB_CLIENT_SECRET", "Client Secret", "Pega el Client Secret")),
                    false,
                    v -> ApiKeyChecker.checkIgdb(v.get("IGDB_CLIENT_ID"), v.get("IGDB_CLIENT_SECRET"))),
            new Step("LIBROS · OPCIONAL", "#f97316",
                    "Sinopsis de libros",
                    "Sin esta clave los libros salen igual (de Open Library), pero sin sinopsis. Puedes saltarlo.",
                    List.of("Entra en Google Cloud con tu cuenta de Google y crea un proyecto.",
                            "Activa «Books API» con el botón «Habilitar» de la página que se abre.",
                            "En «Credenciales» pulsa «Crear credenciales → Clave de API». Si te pide elegir APIs, escribe «Books» en el filtro y marca «Books API». Copia la clave y pégala aquí."),
                    "https://console.cloud.google.com/apis/library/books.googleapis.com", "Abrir Google Cloud",
                    List.of(new Field("GOOGLE_BOOKS_API_KEY", "Clave de API", "Empieza por AIza…")),
                    true,
                    v -> ApiKeyChecker.checkGoogleBooks(v.get("GOOGLE_BOOKS_API_KEY")))
    );

    private final boolean firstRun;
    private final Runnable onDone;
    private final StackPane stage = new StackPane();
    private int index = 0;

    private ApiKeysWizard(boolean firstRun, Runnable onDone) {
        this.firstRun = firstRun;
        this.onDone = onDone;
    }

    /**
     * @param firstRun true antes del login (sin opción de cancelar)
     * @param onDone   se llama al terminar (o al cancelar, si no es firstRun)
     */
    public static Node create(boolean firstRun, Runnable onDone) {
        ApiKeysWizard w = new ApiKeysWizard(firstRun, onDone);
        w.stage.setAlignment(Pos.CENTER);
        w.stage.setPadding(new Insets(36, 24, 36, 24));
        w.show(0);

        ScrollPane scroll = new ScrollPane(w.stage);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: " + Palette.BG + ";");
        CollectionUI.darkViewport(scroll);
        return scroll;
    }

    // ===========================================================
    // PASOS
    // ===========================================================

    private void show(int i) {
        index = i;
        Node page = buildPage(steps.get(i));
        stage.getChildren().setAll(page);
        Motion.enter(page);
    }

    private void next() {
        if (index + 1 < steps.size()) show(index + 1);
        else onDone.run();
    }

    private Node buildPage(Step step) {
        VBox page = new VBox(18);
        page.setMaxWidth(420);
        page.setAlignment(Pos.CENTER_LEFT);

        // --- Cabecera: paso N de 3 + puntos
        HBox dots = new HBox(6);
        dots.setAlignment(Pos.CENTER_LEFT);
        for (int d = 0; d < steps.size(); d++) {
            Region dot = new Region();
            boolean active = d == index;
            dot.setMinSize(active ? 22 : 8, 8);
            dot.setMaxSize(active ? 22 : 8, 8);
            dot.setStyle("-fx-background-radius: 4; -fx-background-color: "
                    + (d <= index ? step.accent() : Palette.LINE_STRONG) + ";");
            dots.getChildren().add(dot);
        }
        Label tag = new Label("PASO " + (index + 1) + " DE " + steps.size() + "  ·  " + step.tag());
        tag.setStyle("-fx-font-family: 'Inter ExtraBold'; -fx-font-size: 11px; -fx-text-fill: " + step.accent() + ";");
        VBox header = new VBox(10, dots, tag);

        Label title = new Label(index == 0 && firstRun ? "Antes de empezar" : step.title());
        title.getStyleClass().add("login-title");
        title.setWrapText(true);
        String sub = index == 0 && firstRun
                ? "MediaVerse busca en servicios gratuitos que piden una clave personal. Te guiamos para conectarlos; solo se hace una vez.\n\n" + step.subtitle()
                : step.subtitle();
        Label subtitle = new Label(sub);
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setWrapText(true);

        // --- Instrucciones numeradas
        VBox howTo = new VBox(10);
        howTo.setPadding(new Insets(16));
        howTo.setStyle("-fx-background-color: " + Palette.PANEL + "; -fx-background-radius: 14;"
                + "-fx-border-color: " + Palette.LINE + "; -fx-border-radius: 14;");
        for (int n = 0; n < step.howTo().size(); n++) {
            Label num = new Label(String.valueOf(n + 1));
            num.setMinSize(22, 22);
            num.setMaxSize(22, 22);
            num.setAlignment(Pos.CENTER);
            num.setStyle("-fx-background-color: " + step.accent() + "33; -fx-background-radius: 11;"
                    + "-fx-text-fill: " + step.accent() + "; -fx-font-family: 'Inter ExtraBold'; -fx-font-size: 11px;");
            Label text = new Label(step.howTo().get(n));
            text.setWrapText(true);
            text.setStyle("-fx-text-fill: " + Palette.TEXT_MUTED + "; -fx-font-size: 13px;");
            HBox row = new HBox(10, num, text);
            row.setAlignment(Pos.TOP_LEFT);
            howTo.getChildren().add(row);
        }
        Button open = new Button(step.urlLabel() + "  ↗");
        open.getStyleClass().add("btn-secondary");
        open.setOnAction(e -> Browser.open(step.url()));
        howTo.getChildren().add(open);
        VBox.setMargin(open, new Insets(4, 0, 0, 32));

        // --- Campos
        VBox fieldsBox = new VBox(12);
        Map<String, TextField> inputs = new LinkedHashMap<>();
        for (Field f : step.fields()) {
            Label label = new Label(f.label());
            label.getStyleClass().add("login-label");
            TextField input = new TextField();
            input.getStyleClass().add("login-field");
            input.setPromptText(f.prompt());
            String current = ConfigLoader.get(f.key());
            if (current != null) input.setText(current);
            inputs.put(f.key(), input);
            fieldsBox.getChildren().add(new VBox(8, label, input));
        }

        Label status = new Label();
        status.setWrapText(true);
        status.setVisible(false);
        status.setManaged(false);

        Button saveAnyway = new Button("Guardar sin comprobar");
        saveAnyway.getStyleClass().add("login-link");
        saveAnyway.setVisible(false);
        saveAnyway.setManaged(false);

        Button primary = new Button(index + 1 < steps.size() ? "Comprobar y seguir" : "Comprobar y terminar");
        primary.getStyleClass().add("login-primary");
        primary.setMaxWidth(Double.MAX_VALUE);

        // --- Pie: atrás / saltar / cancelar
        HBox footer = new HBox(18);
        footer.setAlignment(Pos.CENTER);
        if (index > 0) {
            Button back = new Button("Atrás");
            back.getStyleClass().add("login-link");
            back.setOnAction(e -> show(index - 1));
            footer.getChildren().add(back);
        }
        if (step.optional()) {
            Button skip = new Button("Saltar este paso");
            skip.getStyleClass().add("login-link");
            skip.setOnAction(e -> next());
            footer.getChildren().add(skip);
        } else if (!firstRun && ConfigLoader.hasRequiredKeys()) {
            Button keep = new Button("Dejar como está");
            keep.getStyleClass().add("login-link");
            keep.setOnAction(e -> next());
            footer.getChildren().add(keep);
        }
        if (!firstRun) {
            Button cancel = new Button("Cerrar");
            cancel.getStyleClass().add("login-link");
            cancel.setOnAction(e -> onDone.run());
            footer.getChildren().add(cancel);
        }

        // --- Acciones
        Runnable save = () -> {
            Map<String, String> values = values(inputs);
            try {
                ConfigLoader.saveUserKeys(values);
                if (values.containsKey("IGDB_CLIENT_ID")) GameService.resetToken();
                next();
            } catch (Exception ex) {
                setStatus(status, false, "No se pudieron guardar las claves: " + ex.getMessage());
            }
        };
        saveAnyway.setOnAction(e -> save.run());

        primary.setOnAction(e -> {
            Map<String, String> values = values(inputs);
            primary.setDisable(true);
            primary.setText("Comprobando…");
            saveAnyway.setVisible(false);
            saveAnyway.setManaged(false);
            Task<Result> task = new Task<>() {
                @Override protected Result call() { return step.check().apply(values); }
            };
            task.setOnSucceeded(ev -> {
                Result r = task.getValue();
                primary.setDisable(false);
                primary.setText(index + 1 < steps.size() ? "Comprobar y seguir" : "Comprobar y terminar");
                if (r.status() == Status.OK) {
                    setStatus(status, true, "✓ " + r.message());
                    save.run();
                } else {
                    setStatus(status, false, r.message());
                    if (r.status() == Status.NETWORK) {
                        saveAnyway.setVisible(true);
                        saveAnyway.setManaged(true);
                    }
                }
            });
            task.setOnFailed(ev -> {
                primary.setDisable(false);
                primary.setText("Comprobar y seguir");
                setStatus(status, false, "Algo ha fallado al comprobar la clave.");
            });
            Thread t = new Thread(task, "api-key-check");
            t.setDaemon(true);
            t.start();
        });
        inputs.values().forEach(in -> in.setOnAction(e -> primary.fire()));

        List<Node> children = new ArrayList<>(List.of(header, new VBox(8, title, subtitle), howTo, fieldsBox,
                status, primary, saveAnyway));
        if (!footer.getChildren().isEmpty()) children.add(footer);
        page.getChildren().setAll(children);
        return page;
    }

    private static Map<String, String> values(Map<String, TextField> inputs) {
        Map<String, String> values = new LinkedHashMap<>();
        // Limpia espacios y comillas que se cuelan al copiar y pegar
        inputs.forEach((k, in) -> values.put(k, in.getText() == null ? ""
                : in.getText().trim().replaceAll("^[\"']|[\"']$", "")));
        return values;
    }

    private static void setStatus(Label status, boolean ok, String text) {
        status.setText(text);
        status.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (ok ? Palette.SUCCESS : "#f87171") + ";");
        status.setVisible(true);
        status.setManaged(true);
    }
}
