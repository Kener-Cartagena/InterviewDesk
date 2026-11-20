package desk.ui;

import desk.db.Repository;
import desk.model.Company;
import desk.model.VideoSession;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Lista de empresas a la izquierda; enlace, job description y reuniones a la derecha. */
public final class CompaniesView extends HBox {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy  ·  HH:mm", Locale.forLanguageTag("es"));

    private final Repository repo;
    private final HostServices host;
    private final Runnable onChanged;

    private final ObservableList<Company> items = FXCollections.observableArrayList();
    private final FilteredList<Company> filtered = new FilteredList<>(items, c -> true);
    private final ListView<Company> list = new ListView<>(filtered);
    private final TextField search = new TextField();
    private final Label count = Ui.badge("0", "muted");

    private final StackPane right = new StackPane();
    private final VBox detail = new VBox(16);
    private final VBox empty = Ui.emptyState(Ui.I_WORK, "Elige una entrevista",
            "Selecciona una empresa de la lista para ver su enlace, el CV, la job description y sus reuniones.");
    private final StackPane avatar = Ui.avatar("", 64);
    private final Label title = new Label();
    private final Label linkBadge = Ui.badge("", "ok");
    private final Label cvBadge = Ui.badge("", "info");
    private final Label videoOnlyBadge = Ui.badge("Solo video", "warn");
    private final Label linkText = new Label();
    private final Button copy = Ui.button("Copiar enlace", Ui.I_COPY, "primary");
    private final Button open = Ui.button("Abrir reunión", Ui.I_VIDEO);
    private final Button resume = Ui.button("Ver CV", Ui.I_DOC);
    private final Button addManual = Ui.button("Registrar sin video", Ui.I_ADD);
    private final Button deleteManual = Ui.button("Borrar registro manual", Ui.I_DELETE, "ghost");
    private final Button deleteCompany = Ui.button("Eliminar empresa", Ui.I_DELETE, "danger", "ghost");
    private final TextArea job = new TextArea();
    private final TableView<VideoSession> table = new TableView<>();
    private final Label meetingsTotal = Ui.badge("", "info");
    private final Label meetingsMoney = Ui.badge("", "ok");

    private Company current;

    public CompaniesView(Repository repo, HostServices host, Runnable onChanged) {
        this.repo = repo;
        this.host = host;
        this.onChanged = onChanged;
        setSpacing(20);
        getStyleClass().add("page");

        VBox left = buildLeft();
        buildDetail();
        right.getChildren().addAll(empty, detail);
        HBox.setHgrow(right, Priority.ALWAYS);
        getChildren().addAll(left, right);
        show(null);

        list.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> show(c));
        search.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.toLowerCase().trim();
            filtered.setPredicate(c -> q.isEmpty() || c.name().toLowerCase().contains(q));
        });
        filtered.addListener((javafx.collections.ListChangeListener<Company>) c ->
                count.setText(String.valueOf(filtered.size())));
    }

    // ---------- construcción ----------

    private VBox buildLeft() {
        search.setPromptText("Buscar empresa…");
        search.getStyleClass().add("search-field");
        StackPane searchBox = new StackPane(search, Ui.icon(Ui.I_SEARCH, 16));
        StackPane.setAlignment(searchBox.getChildren().get(1), Pos.CENTER_LEFT);
        StackPane.setMargin(searchBox.getChildren().get(1), new javafx.geometry.Insets(0, 0, 0, 12));
        searchBox.getChildren().get(1).setMouseTransparent(true);

        list.getStyleClass().add("company-list");
        list.setCellFactory(v -> new CompanyCell());
        list.setPlaceholder(Ui.tablePlaceholder("No hay empresas. Pulsa Sincronizar o importa un ZIP."));

        HBox header = Ui.row(10, Ui.label("Entrevistas", "section-title"), Ui.grow(), count);
        VBox left = new VBox(12, header, searchBox, list);
        left.getStyleClass().addAll("card", "list-card");
        left.setPrefWidth(330);
        left.setMinWidth(260);
        left.setMaxWidth(380);
        VBox.setVgrow(list, Priority.ALWAYS);
        return left;
    }

    private void buildDetail() {
        // --- tarjeta principal ---
        title.getStyleClass().add("hero-title");
        title.setWrapText(true);
        HBox badges = Ui.row(8, linkBadge, cvBadge, videoOnlyBadge);
        VBox titleBox = new VBox(8, title, badges, linkText);
        titleBox.setMinWidth(0);
        title.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        deleteCompany.setOnAction(e -> confirmDeleteCompany());
        HBox top = Ui.row(18, avatar, titleBox);
        top.setAlignment(Pos.CENTER_LEFT);

        copy.setOnAction(e -> copyLink());
        open.setOnAction(e -> {
            if (current != null && current.hasLink()) host.showDocument(current.meetingLink());
        });
        resume.setOnAction(e -> {
            if (current != null && current.resumePath() != null) {
                host.showDocument(Path.of(current.resumePath()).toUri().toString());
            }
        });
        linkText.getStyleClass().add("link-text");
        linkText.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        linkText.setGraphic(Ui.icon(Ui.I_LINK, 14));
        linkText.setGraphicTextGap(8);
        linkText.setMinWidth(0);
        linkText.setMaxWidth(Double.MAX_VALUE);
        HBox actions = Ui.row(10, copy, open, resume, Ui.grow(), deleteCompany);

        VBox hero = new VBox(18, top, actions);
        hero.getStyleClass().addAll("card", "hero-card");

        // --- job description ---
        job.setEditable(false);
        job.setWrapText(true);
        job.setPromptText("Esta empresa no tiene job description.");
        VBox jobCard = Ui.section(Ui.I_DOC, "Job description", job);

        // --- reuniones ---
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
        Label hint = Ui.label("Doble clic en una reunión para editarla", "hint");
        HBox bottom = Ui.row(10, addManual, deleteManual, Ui.grow(), hint);
        VBox meetingsBody = new VBox(12, table, bottom);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox meetingsCard = Ui.section(Ui.I_VIDEO, "Reuniones", meetingsBody, meetingsTotal, meetingsMoney);

        // Pantalla ancha: lado a lado. Pantalla pequeña: reuniones arriba, job description abajo.
        HBox wide = new HBox(16);
        VBox narrow = new VBox(16);
        VBox.setVgrow(wide, Priority.ALWAYS);
        VBox.setVgrow(narrow, Priority.ALWAYS);
        detail.getChildren().addAll(hero, narrow);
        detail.widthProperty().addListener((o, was, w) -> {
            boolean isWide = w.doubleValue() >= 1150;
            javafx.scene.layout.Pane target = isWide ? wide : narrow;
            if (detail.getChildren().get(1) == target) return;
            if (isWide) {
                wide.getChildren().setAll(jobCard, meetingsCard);
                HBox.setHgrow(jobCard, Priority.ALWAYS);
                HBox.setHgrow(meetingsCard, Priority.ALWAYS);
                jobCard.setPrefWidth(420);
                meetingsCard.setPrefWidth(640);
                VBox.setVgrow(meetingsCard, Priority.NEVER);
                VBox.setVgrow(jobCard, Priority.NEVER);
            } else {
                narrow.getChildren().setAll(meetingsCard, jobCard);
                meetingsCard.setPrefHeight(250);
                meetingsCard.setMinHeight(220);
                VBox.setVgrow(jobCard, Priority.ALWAYS);
                VBox.setVgrow(meetingsCard, Priority.NEVER);
            }
            detail.getChildren().set(1, target);
        });
        narrow.getChildren().setAll(meetingsCard, jobCard);
        meetingsCard.setPrefHeight(250);
        meetingsCard.setMinHeight(220);
        VBox.setVgrow(jobCard, Priority.ALWAYS);
    }

    private void buildTable() {
        TableColumn<VideoSession, String> date = new TableColumn<>("Fecha");
        date.setCellValueFactory(d -> new ReadOnlyStringWrapper(capitalize(d.getValue().recordedAt().format(DATE_TIME))));
        TableColumn<VideoSession, String> length = new TableColumn<>("Duración");
        length.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.duration(d.getValue().durationSeconds())));
        TableColumn<VideoSession, String> money = new TableColumn<>("Monto");
        money.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.money(d.getValue().durationSeconds())));
        money.getStyleClass().add("money-col");
        TableColumn<VideoSession, Boolean> paid = new TableColumn<>("Estado");
        paid.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().paid()));
        paid.setCellFactory(c -> Cells.paidBadge());
        TableColumn<VideoSession, String> file = new TableColumn<>("Origen");
        file.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().fileName()));
        file.getStyleClass().add("muted-col");
        date.setPrefWidth(200);
        length.setPrefWidth(120);
        length.setMinWidth(110);
        money.setPrefWidth(80);
        paid.setPrefWidth(105);
        paid.setMinWidth(100);
        file.setPrefWidth(150);
        table.getColumns().addAll(List.of(date, length, money, paid, file));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(Ui.tablePlaceholder(
                "Aún no hay reuniones. Se llenan solas con los videos o las registras a mano."));
        table.setMinHeight(110);
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

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Fila de la lista: avatar, nombre y una línea con lo que tiene la empresa. */
    private static final class CompanyCell extends ListCell<Company> {
        private final StackPane avatar = Ui.avatar("", 36);
        private final Label name = Ui.label("", "cell-title");
        private final Label sub = Ui.label("", "cell-sub");
        private final HBox box;

        CompanyCell() {
            VBox text = new VBox(2, name, sub);
            text.setMinWidth(0);
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            box = Ui.row(12, avatar, text);
            setText(null);
        }

        @Override
        protected void updateItem(Company c, boolean empty) {
            super.updateItem(c, empty);
            if (empty || c == null) {
                setGraphic(null);
                return;
            }
            Ui.updateAvatar(avatar, c.name());
            name.setText(c.name());
            if (c.folder() == null) {
                sub.setText("Solo video");
            } else {
                String s = c.hasLink() ? "Enlace listo" : "Sin enlace";
                if (c.resumePath() != null) s += "  ·  CV";
                sub.setText(s);
            }
            setGraphic(box);
        }
    }

    // ---------- comportamiento ----------

    public void reload() {
        long keep = current == null ? -1 : current.id();
        items.setAll(repo.companies());
        count.setText(String.valueOf(filtered.size()));
        Company again = items.stream().filter(c -> c.id() == keep).findFirst().orElse(null);
        if (again != null) {
            list.getSelectionModel().select(again);
        } else if (!filtered.isEmpty()) {
            list.getSelectionModel().select(0); // nunca arrancar con el panel vacío
        }
    }

    private void show(Company c) {
        current = c;
        detail.setVisible(c != null);
        empty.setVisible(c == null);
        if (c == null) return;

        Ui.updateAvatar(avatar, c.name());
        title.setText(c.name());
        Ui.setBadge(linkBadge, c.hasLink() ? "Enlace listo" : "Sin enlace", c.hasLink() ? "ok" : "muted");
        Ui.setBadge(cvBadge, c.resumePath() != null ? "CV disponible" : "Sin CV", c.resumePath() != null ? "info" : "muted");
        videoOnlyBadge.setVisible(c.folder() == null);
        videoOnlyBadge.setManaged(c.folder() == null);
        linkText.setText(c.hasLink() ? c.meetingLink() : "Sin enlace: falta el .txt de Meeting en la carpeta.");

        copy.setDisable(!c.hasLink());
        open.setDisable(!c.hasLink());
        resume.setDisable(c.resumePath() == null);
        job.setText(c.jobDescription() == null ? "" : c.jobDescription());

        List<VideoSession> sessions = repo.sessionsOfCompany(c.id());
        table.getItems().setAll(sessions);
        long seconds = sessions.stream().mapToLong(VideoSession::durationSeconds).sum();
        meetingsTotal.setText(Fmt.count(sessions.size(), "reunión", "reuniones") + " · " + Fmt.durationOrZero(seconds));
        meetingsMoney.setText(Fmt.money(seconds));
        deleteManual.setDisable(true);
    }

    private void copyLink() {
        if (current == null || !current.hasLink()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(current.meetingLink());
        Clipboard.getSystemClipboard().setContent(content);
        copy.setText("¡Copiado!");
        copy.setGraphic(Ui.icon(Ui.I_CHECK, 16));
        PauseTransition pause = new PauseTransition(Duration.seconds(1.4));
        pause.setOnFinished(e -> {
            copy.setText("Copiar enlace");
            copy.setGraphic(Ui.icon(Ui.I_COPY, 16));
        });
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
        show(null);
        reload();
        onChanged.run();
    }
}
