package desk.ui;

import desk.db.Repository;
import desk.model.Company;
import desk.model.VideoSession;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/** Lista de empresas a la izquierda; enlace, job description y reuniones a la derecha. */
public final class CompaniesView extends HBox {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm");

    private final Repository repo;
    private final HostServices host;
    private final Runnable onChanged;

    private final ObservableList<Company> items = FXCollections.observableArrayList();
    private final FilteredList<Company> filtered = new FilteredList<>(items, c -> true);
    private final ListView<Company> list = new ListView<>(filtered);
    private final TextField search = new TextField();
    private final Label count = new Label();

    private final VBox detail = new VBox(14);
    private final Label title = new Label();
    private final TextField linkField = new TextField();
    private final Button copy = new Button("Copiar enlace");
    private final Button open = new Button("Abrir reunión");
    private final Button resume = new Button("Ver CV");
    private final Button addManual = new Button("Registrar reunión sin video");
    private final Button deleteManual = new Button("Borrar registro manual");
    private final Button deleteCompany = new Button("Eliminar empresa");
    private final TextArea job = new TextArea();
    private final TableView<VideoSession> table = new TableView<>();

    private Company current;

    public CompaniesView(Repository repo, HostServices host, Runnable onChanged) {
        this.repo = repo;
        this.host = host;
        this.onChanged = onChanged;
        setSpacing(18);
        setPadding(new Insets(18, 0, 0, 0));

        getChildren().addAll(buildLeft(), buildDetail());
        HBox.setHgrow(detail, Priority.ALWAYS);
        detail.setVisible(false);

        list.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> show(c));
        search.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.toLowerCase().trim();
            filtered.setPredicate(c -> q.isEmpty() || c.name().toLowerCase().contains(q));
        });
    }

    // ---------- construcción ----------

    private VBox buildLeft() {
        search.setPromptText("Buscar empresa");
        count.getStyleClass().add("muted");
        count.textProperty().bind(Bindings.size(filtered).asString().concat(" empresas"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Company c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.name() + (c.folder() == null ? "   · solo video" : ""));
            }
        });
        VBox left = new VBox(10, search, list, count);
        left.setPrefWidth(310);
        left.setMinWidth(240);
        VBox.setVgrow(list, Priority.ALWAYS);
        return left;
    }

    private VBox buildDetail() {
        title.getStyleClass().add("title");
        title.setWrapText(true);
        linkField.setEditable(false);
        linkField.setPromptText("Sin enlace: falta el .txt de Meeting en la carpeta.");

        copy.getStyleClass().add("primary");
        copy.setOnAction(e -> copyLink());
        open.setOnAction(e -> {
            if (current != null && current.hasLink()) host.showDocument(current.meetingLink());
        });
        resume.setOnAction(e -> {
            if (current != null && current.resumePath() != null) {
                host.showDocument(Path.of(current.resumePath()).toUri().toString());
            }
        });
        HBox links = new HBox(10, copy, open, resume);
        links.setAlignment(Pos.CENTER_LEFT);

        Label jobLabel = new Label("Job description");
        jobLabel.getStyleClass().add("caption");
        job.setEditable(false);
        job.setWrapText(true);
        job.setPromptText("Esta empresa no tiene job description.");
        VBox.setVgrow(job, Priority.ALWAYS);

        Label meetingsLabel = new Label("Reuniones");
        meetingsLabel.getStyleClass().add("caption");
        buildTable();

        addManual.setOnAction(e -> {
            if (current != null && ManualSessionDialog.add(current, repo)) {
                show(current);
                onChanged.run();
            }
        });
        deleteManual.setDisable(true);
        deleteManual.setOnAction(e -> {
            VideoSession s = table.getSelectionModel().getSelectedItem();
            if (s != null && s.manual()) {
                repo.deleteSession(s.id());
                show(current);
                onChanged.run();
            }
        });
        deleteCompany.getStyleClass().add("danger");
        deleteCompany.setOnAction(e -> confirmDeleteCompany());
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox bottom = new HBox(10, addManual, deleteManual, grow, deleteCompany);
        bottom.setAlignment(Pos.CENTER_LEFT);

        detail.getChildren().addAll(title, linkField, links, jobLabel, job, meetingsLabel, table, bottom);
        return detail;
    }

    private void buildTable() {
        TableColumn<VideoSession, String> date = new TableColumn<>("Fecha");
        date.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().recordedAt().format(DATE_TIME)));
        TableColumn<VideoSession, String> length = new TableColumn<>("Duración");
        length.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.duration(d.getValue().durationSeconds())));
        TableColumn<VideoSession, String> paidHours = new TableColumn<>("Horas");
        paidHours.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.hours(d.getValue().paidHours())));
        TableColumn<VideoSession, String> paid = new TableColumn<>("Pago");
        paid.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().paid() ? "Pagado" : "Pendiente"));
        TableColumn<VideoSession, String> file = new TableColumn<>("Origen");
        file.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().fileName()));
        table.getColumns().addAll(date, length, paidHours, paid, file);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aún no hay reuniones. Se llenan solas con los videos o las registras a mano."));
        table.setPrefHeight(180);
        table.setMinHeight(130);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, s) ->
                deleteManual.setDisable(s == null || !s.manual()));
        table.setRowFactory(tv -> {
            TableRow<VideoSession> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && SessionDialog.edit(row.getItem(), repo)) {
                    show(current);
                    onChanged.run();
                }
            });
            return row;
        });
    }

    // ---------- comportamiento ----------

    public void reload() {
        long keep = current == null ? -1 : current.id();
        items.setAll(repo.companies());
        items.stream().filter(c -> c.id() == keep).findFirst()
                .ifPresent(c -> list.getSelectionModel().select(c));
    }

    private void show(Company c) {
        current = c;
        detail.setVisible(c != null);
        if (c == null) return;
        title.setText(c.name());
        linkField.setText(c.hasLink() ? c.meetingLink() : "");
        copy.setDisable(!c.hasLink());
        open.setDisable(!c.hasLink());
        resume.setDisable(c.resumePath() == null);
        job.setText(c.jobDescription() == null ? "" : c.jobDescription());
        table.getItems().setAll(repo.sessionsOfCompany(c.id()));
        deleteManual.setDisable(true);
    }

    private void copyLink() {
        if (current == null || !current.hasLink()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(current.meetingLink());
        Clipboard.getSystemClipboard().setContent(content);
        copy.setText("Copiado");
        PauseTransition pause = new PauseTransition(Duration.seconds(1.4));
        pause.setOnFinished(e -> copy.setText("Copiar enlace"));
        pause.play();
    }

    private void confirmDeleteCompany() {
        if (current == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Se quita \"" + current.name() + "\" de la app, con sus reuniones y horas. "
                        + "Tus carpetas y videos no se borran.",
                ButtonType.CANCEL, ButtonType.OK);
        alert.setHeaderText("¿Eliminar esta empresa?");
        alert.getDialogPane().getStylesheets().add(Theme.css());
        if (alert.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;
        repo.hideCompany(current.id());
        current = null;
        detail.setVisible(false);
        reload();
        onChanged.run();
    }
}
