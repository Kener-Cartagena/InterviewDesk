package desk;

import desk.db.Database;
import desk.db.Repository;
import desk.service.Config;
import desk.service.SyncService;
import desk.service.ZipImporter;
import desk.ui.CompaniesView;
import desk.ui.HoursView;
import desk.ui.SettingsView;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public final class App extends Application {
    private Repository repo;
    private CompaniesView companies;
    private HoursView hours;
    private final Label status = new Label();

    // NOTA: El método main ya no va aquí, se movió a Launcher.java

    @Override
    public void start(Stage stage) {
        repo = new Repository(new Database(Path.of(System.getProperty("user.home"), "InterviewDesk", "desk.db")));
        hours = new HoursView(repo);
        companies = new CompaniesView(repo, getHostServices(), hours::reload);
        SettingsView settings = new SettingsView(repo, () -> {
            status.setText("Rutas guardadas");
            sync();
        });

        TabPane tabs = new TabPane(tab("Entrevistas", companies), tab("Horas", hours), tab("Ajustes", settings));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane root = new BorderPane();
        root.setTop(header(stage));
        root.setCenter(tabs);
        root.setPadding(new Insets(22, 26, 22, 26));

        Scene scene = new Scene(root, 1220, 780);
        scene.getStylesheets().add(getClass().getResource("/desk/style.css").toExternalForm());
        stage.setTitle("Interview Desk");
        stage.setMinWidth(760);
        stage.setMinHeight(500);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        companies.reload();
        hours.reload();
        sync();
    }

    private HBox header(Stage stage) {
        Label brand = new Label("interview desk");
        brand.getStyleClass().add("brand");
        Label dot = new Label("●");
        dot.getStyleClass().add("brand-dot");
        status.getStyleClass().add("muted");

        Button importZip = new Button("Importar ZIP");
        importZip.setOnAction(e -> importZip(stage));
        Button syncButton = new Button("Sincronizar");
        syncButton.getStyleClass().add("primary");
        syncButton.setOnAction(e -> sync());

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox box = new HBox(10, dot, brand, grow, status, importZip, syncButton);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 14, 0));
        return box;
    }

    private static Tab tab(String title, Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
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

    /** Ejecuta trabajo pesado fuera del hilo de la interfaz y refresca las vistas al terminar. */
    private void run(String busy, Callable<String> job) {
        status.setText(busy);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return job.call();
            }
        };
        task.setOnSucceeded(e -> {
            status.setText(task.getValue());
            companies.reload();
            hours.reload();
        });
        task.setOnFailed(e -> status.setText("Error: " + task.getException().getMessage()));
        Thread thread = new Thread(task, "desk-worker");
        thread.setDaemon(true);
        thread.start();
    }
}