package desk;

import desk.db.Database;
import desk.db.Repository;
import desk.service.Config;
import desk.service.SyncService;
import desk.service.ZipImporter;
import desk.ui.CompaniesView;
import desk.ui.HoursView;
import desk.ui.SettingsView;
import desk.ui.Ui;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public final class App extends Application {
    private Repository repo;
    private CompaniesView companies;
    private HoursView hours;
    private final Label status = new Label("Listo");
    private final ProgressIndicator busy = new ProgressIndicator();
    private final Region statusDot = new Region();
    private Button syncButton;
    private Button importButton;
    private final StackPane content = new StackPane();

    // NOTA: El método main ya no va aquí, se movió a Launcher.java

    @Override
    public void start(Stage stage) {
        String dbPath = System.getProperty("desk.db");
        Path db = dbPath != null ? Path.of(dbPath) : Path.of(System.getProperty("user.home"), "InterviewDesk", "desk.db");
        repo = new Repository(new Database(db));
        hours = new HoursView(repo);
        companies = new CompaniesView(repo, getHostServices(), hours::reload);
        SettingsView settings = new SettingsView(repo, this::sync);

        ScrollPane settingsScroll = new ScrollPane(settings);
        settingsScroll.setFitToWidth(true);
        settingsScroll.getStyleClass().add("page-scroll");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app");
        root.setLeft(sidebar(stage, new Node[] {companies, hours, settingsScroll}));
        content.getStyleClass().add("content");
        root.setCenter(content);

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/desk/style.css").toExternalForm());
        stage.setTitle("Interview Desk");
        try (InputStream icon = getClass().getResourceAsStream("/assets/logo2.png")) {
            if (icon != null) stage.getIcons().add(new Image(icon));
        } catch (Exception ignored) {
            // sin icono no pasa nada
        }
        stage.setMinWidth(980);
        stage.setMinHeight(620);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        companies.reload();
        hours.reload();
        sync();
    }

    private VBox sidebar(Stage stage, Node[] pages) {
        // --- marca ---
        StackPane mark = new StackPane(Ui.icon(Ui.I_PLAY, 18));
        mark.getStyleClass().add("logo-mark");
        mark.setMinSize(40, 40);
        mark.setMaxSize(40, 40);
        VBox brandText = new VBox(0, Ui.label("Interview Desk", "brand"), Ui.label("Entrevistas y horas", "brand-sub"));
        HBox brand = Ui.row(12, mark, brandText);
        brand.getStyleClass().add("brand-box");

        // --- navegación ---
        content.getChildren().setAll(pages);
        String[][] nav = {
                {"Entrevistas", Ui.I_WORK},
                {"Horas y pagos", Ui.I_CLOCK},
                {"Ajustes", Ui.I_GEAR},
        };
        ToggleGroup group = new ToggleGroup();
        VBox navBox = new VBox(4);
        for (int i = 0; i < nav.length; i++) {
            ToggleButton item = Ui.withIcon(new ToggleButton(nav[i][0]), nav[i][1]);
            item.getStyleClass().add("nav-item");
            item.setMaxWidth(Double.MAX_VALUE);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setToggleGroup(group);
            item.setUserData(pages[i]);
            navBox.getChildren().add(item);
        }
        group.selectedToggleProperty().addListener((o, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
                return;
            }
            for (Node page : pages) page.setVisible(page == selected.getUserData());
        });
        group.selectToggle(group.getToggles().get(0));

        // --- acciones y estado ---
        syncButton = Ui.button("Sincronizar", Ui.I_SYNC, "primary");
        syncButton.setMaxWidth(Double.MAX_VALUE);
        syncButton.setOnAction(e -> sync());
        importButton = Ui.button("Importar ZIP", Ui.I_UPLOAD);
        importButton.setMaxWidth(Double.MAX_VALUE);
        importButton.setOnAction(e -> importZip(stage));

        busy.setPrefSize(14, 14);
        busy.setMaxSize(14, 14);
        busy.setVisible(false);
        busy.setManaged(false);
        statusDot.getStyleClass().add("status-dot");
        statusDot.setMinSize(8, 8);
        statusDot.setMaxSize(8, 8);
        status.getStyleClass().add("status-text");
        status.setWrapText(true);
        HBox statusRow = Ui.row(8, statusDot, busy, status);
        statusRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(status, Priority.ALWAYS);

        VBox actions = new VBox(8, syncButton, importButton, statusRow);
        actions.getStyleClass().add("sidebar-card");

        VBox side = new VBox(8, brand, Ui.label("MENÚ", "nav-caption"), navBox, Ui.grow(), actions);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(250);
        side.setMinWidth(230);
        return side;
    }

    private void sync() {
        run("Sincronizando…", () -> {
            var r = new SyncService(repo).sync(Config.interviewsDir(repo), Config.videosDir(repo));
            String text = r.companies() + " empresas, " + r.videos() + " videos";
            if (r.createdFromVideos() > 0) {
                text += " (" + r.createdFromVideos() + " solo con video)";
            }
            if (r.removed() > 0) {
                text += ", " + r.removed() + " limpiadas";
            }
            return text;
        });
    }

    private void importZip(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elige el .zip que te enviaron");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos ZIP", "*.zip"));
        File zip = chooser.showOpenDialog(stage);
        if (zip == null) return;
        run("Descomprimiendo…", () -> {
            int files = ZipImporter.extract(zip.toPath(), Config.interviewsDir(repo));
            var r = new SyncService(repo).sync(Config.interviewsDir(repo), Config.videosDir(repo));
            return files + " archivos descomprimidos, " + r.companies() + " empresas";
        });
    }

    private void setBusy(boolean on, String text, boolean error) {
        status.setText(text);
        busy.setVisible(on);
        busy.setManaged(on);
        statusDot.setVisible(!on);
        statusDot.setManaged(!on);
        statusDot.getStyleClass().removeAll("status-error");
        if (error) statusDot.getStyleClass().add("status-error");
        syncButton.setDisable(on);
        importButton.setDisable(on);
    }

    /** Ejecuta trabajo pesado fuera del hilo de la interfaz y refresca las vistas al terminar. */
    private void run(String busyText, Callable<String> job) {
        setBusy(true, busyText, false);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return job.call();
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false, task.getValue(), false);
            companies.reload();
            hours.reload();
        });
        task.setOnFailed(e -> setBusy(false, "Error: " + task.getException().getMessage(), true));
        Thread thread = new Thread(task, "desk-worker");
        thread.setDaemon(true);
        thread.start();
    }
}
