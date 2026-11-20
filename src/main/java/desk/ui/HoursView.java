package desk.ui;

import desk.db.Repository;
import desk.model.Billing;
import desk.model.VideoSession;
import desk.service.Config;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Horas por día y por empresa en un día, una semana o un periodo de pago (2 semanas).
 * El periodo siempre es el que contiene la fecha elegida arriba.
 * El tiempo se cuenta exacto, sin redondeo: el dinero sale de los segundos reales.
 */
public final class HoursView extends VBox {
    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", ES);
    private static final DateTimeFormatter MEDIUM = DateTimeFormatter.ofPattern("EEEE d MMM", ES);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE d MMM", ES);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private enum Mode {
        DAY("Día", 1), WEEK("Semana", 7), TWO_WEEKS("2 semanas", 14);

        final String label;
        final int days;

        Mode(String label, int days) {
            this.label = label;
            this.days = days;
        }
    }

    private static final class DayRow {
        final LocalDate date;
        final int count;
        final int seconds;
        final BooleanProperty paid;

        DayRow(LocalDate date, int count, int seconds, boolean paid) {
            this.date = date;
            this.count = count;
            this.seconds = seconds;
            this.paid = new SimpleBooleanProperty(paid);
        }
    }

    private record CompanyRow(String name, int count, int seconds) {}

    private final Repository repo;
    private Mode mode = Mode.TWO_WEEKS;
    private LocalDate cursor = LocalDate.now();
    private LocalDate dayFilter;
    private boolean syncing;
    private Map<LocalDate, List<VideoSession>> grouped = new TreeMap<>();
    private List<VideoSession> periodSessions = new ArrayList<>();

    private final DatePicker picker = new DatePicker(LocalDate.now());
    private final Label pageTitle = new Label("Horas y pagos");
    private final Label rangeLabel = new Label();

    private final Label totalMoney = new Label();
    private final Label totalSub = new Label();
    private final Label workedTime = new Label();
    private final Label workedSub = new Label();
    private final Label paidMoney = new Label();
    private final Label paidSub = new Label();
    private final Label pendingMoney = new Label();
    private final Label pendingSub = new Label();
    private final ProgressBar paidProgress = new ProgressBar(0);
    private final Label paidPercent = new Label();

    private final Button payAll = new Button();
    private final TableView<DayRow> dayTable = new TableView<>();
    private final TableView<VideoSession> detailTable = new TableView<>();
    private final TableView<CompanyRow> companyTable = new TableView<>();
    private final Label detailTitle = new Label("Reuniones");
    private final Label detailCount = Ui.badge("0", "muted");
    private final Label dayCount = Ui.badge("0", "muted");
    private final Label companyCount = Ui.badge("0", "muted");
    private final Button clearFilter = Ui.button("Ver todas", Ui.I_CALENDAR, "ghost", "small");
    private ToggleButton dayTab;
    private ToggleButton meetingsTab;

    public HoursView(Repository repo) {
        this.repo = repo;
        setSpacing(18);
        getStyleClass().add("page");

        // --- encabezado: título, navegación de fechas y selector de periodo ---
        ToggleGroup group = new ToggleGroup();
        HBox modes = segmented(group, Mode.values().length);
        for (Mode m : Mode.values()) {
            ToggleButton b = (ToggleButton) modes.getChildren().get(m.ordinal());
            b.setText(m.label);
            b.setUserData(m);
            b.setSelected(m == mode);
        }
        group.selectedToggleProperty().addListener((o, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true); // siempre debe haber un periodo elegido
                return;
            }
            mode = (Mode) selected.getUserData();
            dayFilter = null;
            apply();
        });

        Button prev = Ui.iconButton(Ui.I_LEFT, "Periodo anterior");
        prev.setOnAction(e -> shift(-1));
        Button next = Ui.iconButton(Ui.I_RIGHT, "Periodo siguiente");
        next.setOnAction(e -> shift(1));
        Button today = Ui.button("Hoy", Ui.I_CALENDAR);
        today.setOnAction(e -> {
            cursor = LocalDate.now();
            dayFilter = null;
            apply();
        });
        picker.setPrefWidth(150);
        picker.valueProperty().addListener((o, was, date) -> {
            if (!syncing && date != null) {
                cursor = date;
                dayFilter = null;
                apply();
            }
        });
        HBox dateNav = Ui.row(6, prev, picker, next, today);
        HBox header = Ui.pageHeader(pageTitle, rangeLabel, dateNav, modes);

        // --- tarjetas de totales ---
        paidProgress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(paidProgress, Priority.ALWAYS);
        paidPercent.getStyleClass().add("kpi-foot");
        HBox progressRow = Ui.row(10, paidProgress, paidPercent);

        VBox hero = kpi(Ui.I_MONEY, "Total del periodo", totalMoney, totalSub, "hero");
        hero.getChildren().add(progressRow);
        hero.setPrefWidth(340);

        payAll.setOnAction(e -> togglePayAll());
        payAll.getStyleClass().add("small");
        payAll.setMinWidth(Region.USE_PREF_SIZE);
        VBox pendingCard = kpi(Ui.I_CLOCK, "Pendiente de pago", pendingMoney, pendingSub, "warn");
        HBox pendingFoot = Ui.row(8, pendingSub, Ui.grow(), payAll);
        pendingCard.getChildren().add(pendingFoot);

        HBox cards = new HBox(16,
                hero,
                kpi(Ui.I_CLOCK, "Tiempo trabajado", workedTime, workedSub, "info"),
                kpi(Ui.I_CHECK_CIRCLE, "Pagado", paidMoney, paidSub, "ok"),
                pendingCard);
        pendingCard.setPrefWidth(320);

        // --- tablas: una tarjeta con pestañas "Por día" / "Reuniones" + tarjeta por empresa ---
        buildDayTable();
        buildDetailTable();
        buildCompanyTable();

        clearFilter.setOnAction(e -> {
            dayFilter = null;
            showDetail();
        });

        ToggleGroup views = new ToggleGroup();
        HBox viewSwitch = segmented(views, 2);
        dayTab = (ToggleButton) viewSwitch.getChildren().get(0);
        meetingsTab = (ToggleButton) viewSwitch.getChildren().get(1);
        dayTab.setText("Por día");
        dayTab.setGraphic(dayCount);
        meetingsTab.setText("Reuniones");
        meetingsTab.setGraphic(detailCount);
        for (ToggleButton t : List.of(dayTab, meetingsTab)) {
            t.setContentDisplay(ContentDisplay.RIGHT);
            t.setGraphicTextGap(8);
        }
        views.selectedToggleProperty().addListener((o, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true);
                return;
            }
            boolean days = selected == dayTab;
            dayTable.setVisible(days);
            detailTable.setVisible(!days);
            showDetail();
        });
        dayTab.setSelected(true);
        detailTable.setVisible(false);

        StackPane tableStack = new StackPane(dayTable, detailTable);
        detailTitle.getStyleClass().add("hint");
        HBox tabsHeader = Ui.row(12, viewSwitch, detailTitle, Ui.grow(), clearFilter);
        VBox mainCard = new VBox(12, tabsHeader, tableStack);
        mainCard.getStyleClass().add("card");
        VBox.setVgrow(tableStack, Priority.ALWAYS);
        VBox companyCard = Ui.section(Ui.I_WORK, "Por empresa", companyTable, companyCount);

        HBox tables = new HBox(16, mainCard, companyCard);
        HBox.setHgrow(mainCard, Priority.ALWAYS);
        HBox.setHgrow(companyCard, Priority.SOMETIMES);
        mainCard.setPrefWidth(760);
        companyCard.setPrefWidth(430);
        companyCard.setMinWidth(350);
        VBox.setVgrow(tables, Priority.ALWAYS);

        // Doble clic en un día: muestra solo sus reuniones.
        dayTable.setRowFactory(tv -> {
            TableRow<DayRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 2) {
                    dayFilter = row.getItem().date;
                    meetingsTab.setSelected(true);
                    showDetail();
                }
            });
            return row;
        });

        getChildren().addAll(header, cards, tables);
    }

    /** Grupo de botones pegados, tipo pestañas. */
    private static HBox segmented(ToggleGroup group, int n) {
        HBox box = new HBox();
        box.getStyleClass().add("segmented");
        box.setMaxHeight(Region.USE_PREF_SIZE);
        box.setMinWidth(Region.USE_PREF_SIZE);
        box.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < n; i++) {
            ToggleButton b = new ToggleButton();
            b.setToggleGroup(group);
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            box.getChildren().add(b);
        }
        return box;
    }

    // ---------- construcción ----------

    private static VBox kpi(String svg, String caption, Label value, Label sub, String tone) {
        value.getStyleClass().add("kpi-value");
        sub.getStyleClass().add("kpi-sub");
        HBox top = Ui.row(10, Ui.iconBubble(svg), Ui.label(caption, "kpi-caption"));
        VBox box = new VBox(8, top, value, sub);
        box.getStyleClass().addAll("kpi", "kpi-" + tone);
        box.setMinWidth(190);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void buildDayTable() {
        TableColumn<DayRow, String> date = new TableColumn<>("Fecha");
        date.setCellValueFactory(d -> new ReadOnlyStringWrapper(capitalize(d.getValue().date.format(MEDIUM))));
        date.setMinWidth(140);
        TableColumn<DayRow, String> count = new TableColumn<>("Reuniones");
        count.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().count)));
        count.getStyleClass().add("align-center");
        TableColumn<DayRow, String> real = new TableColumn<>("Tiempo");
        real.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.duration(d.getValue().seconds)));
        TableColumn<DayRow, String> money = new TableColumn<>("Monto");
        money.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.money(d.getValue().seconds)));
        money.getStyleClass().add("money-col");

        TableColumn<DayRow, Boolean> paid = new TableColumn<>("Pagado");
        paid.setCellValueFactory(d -> d.getValue().paid);
        paid.setCellFactory(CheckBoxTableCell.forTableColumn(paid));
        paid.setEditable(true);
        paid.getStyleClass().add("align-center");

        date.setPrefWidth(180);
        count.setPrefWidth(95);
        count.setMinWidth(85);
        real.setPrefWidth(140);
        real.setMinWidth(125);
        money.setPrefWidth(100);
        money.setMinWidth(80);
        paid.setPrefWidth(85);
        paid.setMinWidth(75);
        dayTable.getColumns().addAll(List.of(date, count, real, money, paid));
        dayTable.setEditable(true);
        dayTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        dayTable.setPlaceholder(Ui.tablePlaceholder("No hay reuniones en este periodo."));
    }

    private void buildDetailTable() {
        TableColumn<VideoSession, String> dateCol = new TableColumn<>("Fecha");
        dateCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(capitalize(d.getValue().recordedAt().format(SHORT))));
        TableColumn<VideoSession, String> time = new TableColumn<>("Hora");
        time.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().recordedAt().format(TIME)));
        TableColumn<VideoSession, String> company = new TableColumn<>("Empresa");
        company.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().companyName()));
        TableColumn<VideoSession, String> length = new TableColumn<>("Duración");
        length.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.duration(d.getValue().durationSeconds())));
        TableColumn<VideoSession, String> money = new TableColumn<>("Monto");
        money.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.money(d.getValue().durationSeconds())));
        money.getStyleClass().add("money-col");
        TableColumn<VideoSession, Boolean> state = new TableColumn<>("Estado");
        state.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().paid()));
        state.setCellFactory(c -> Cells.paidBadge());
        dateCol.setPrefWidth(110);
        dateCol.setMinWidth(100);
        time.setPrefWidth(65);
        time.setMinWidth(58);
        company.setPrefWidth(170);
        company.setMinWidth(110);
        length.setPrefWidth(120);
        length.setMinWidth(110);
        money.setPrefWidth(80);
        money.setMinWidth(72);
        state.setPrefWidth(105);
        state.setMinWidth(100);
        detailTable.getColumns().addAll(List.of(dateCol, time, company, length, money, state));
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        detailTable.setPlaceholder(Ui.tablePlaceholder("No hay reuniones para mostrar."));
    }

    private void buildCompanyTable() {
        TableColumn<CompanyRow, String> name = new TableColumn<>("Empresa");
        name.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name()));
        name.setCellFactory(c -> Cells.company());
        TableColumn<CompanyRow, String> time = new TableColumn<>("Tiempo");
        time.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.duration(d.getValue().seconds())));
        TableColumn<CompanyRow, String> money = new TableColumn<>("Monto");
        money.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.money(d.getValue().seconds())));
        money.getStyleClass().add("money-col");
        name.setPrefWidth(170);
        name.setMinWidth(110);
        time.setPrefWidth(125);
        time.setMinWidth(115);
        money.setPrefWidth(80);
        money.setMinWidth(72);
        companyTable.getColumns().addAll(List.of(name, time, money));
        companyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        companyTable.setPlaceholder(Ui.tablePlaceholder("No hay reuniones en este periodo."));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- periodo ----------

    private LocalDate anchor() {
        return Config.payAnchor(repo).orElseGet(() -> {
            LocalDate first = repo.firstSessionDate();
            LocalDate base = first == null ? LocalDate.now() : first;
            return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        });
    }

    private LocalDate[] range() {
        switch (mode) {
            case DAY:
                return new LocalDate[] {cursor, cursor.plusDays(1)};
            case WEEK: {
                LocalDate from = cursor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new LocalDate[] {from, from.plusDays(7)};
            }
            default: {
                LocalDate anchor = anchor();
                long blocks = Math.floorDiv(ChronoUnit.DAYS.between(anchor, cursor), 14L);
                LocalDate from = anchor.plusDays(blocks * 14);
                return new LocalDate[] {from, from.plusDays(14)};
            }
        }
    }

    private void shift(int direction) {
        cursor = cursor.plusDays((long) direction * mode.days);
        dayFilter = null;
        apply();
    }

    private void togglePayAll() {
        LocalDate[] r = range();
        boolean markPaid = payAll.getUserData() == Boolean.TRUE;
        repo.setPaidBetween(r[0], r[1], markPaid);
        apply();
    }

    // ---------- datos ----------

    public void reload() {
        apply();
    }

    private void apply() {
        LocalDate[] r = range();
        LocalDate last = r[1].minusDays(1);

        syncing = true;
        picker.setValue(cursor);
        syncing = false;

        pageTitle.setText(switch (mode) {
            case DAY -> "Horas del día";
            case WEEK -> "Horas de la semana";
            case TWO_WEEKS -> "Periodo de pago";
        });
        rangeLabel.setText(mode == Mode.DAY
                ? capitalize(r[0].format(LONG))
                : capitalize(r[0].format(MEDIUM)) + "  →  " + last.format(LONG));

        List<VideoSession> rows = repo.sessionsBetween(r[0], r[1]);
        periodSessions = rows;

        Map<LocalDate, List<VideoSession>> byDay = new TreeMap<>(Comparator.reverseOrder());
        Map<String, List<VideoSession>> byCompany = new LinkedHashMap<>();
        long totalSeconds = 0;
        long paidSeconds = 0;

        for (VideoSession s : rows) {
            byDay.computeIfAbsent(s.recordedAt().toLocalDate(), d -> new ArrayList<>()).add(s);
            byCompany.computeIfAbsent(s.companyName(), n -> new ArrayList<>()).add(s);
            totalSeconds += s.durationSeconds();
            if (s.paid()) paidSeconds += s.durationSeconds();
        }
        long pendingSeconds = totalSeconds - paidSeconds;
        grouped = byDay;

        List<DayRow> dayRows = new ArrayList<>();
        for (Map.Entry<LocalDate, List<VideoSession>> e : byDay.entrySet()) {
            List<VideoSession> list = e.getValue();
            DayRow row = new DayRow(
                    e.getKey(),
                    list.size(),
                    list.stream().mapToInt(VideoSession::durationSeconds).sum(),
                    list.stream().allMatch(VideoSession::paid));
            row.paid.addListener((o, was, isPaid) -> {
                repo.setPaidBetween(row.date, row.date.plusDays(1), isPaid);
                Platform.runLater(this::apply);
            });
            dayRows.add(row);
        }

        List<CompanyRow> companyRows = new ArrayList<>();
        for (Map.Entry<String, List<VideoSession>> e : byCompany.entrySet()) {
            List<VideoSession> list = e.getValue();
            companyRows.add(new CompanyRow(e.getKey(), list.size(),
                    list.stream().mapToInt(VideoSession::durationSeconds).sum()));
        }
        companyRows.sort(Comparator.comparingInt(CompanyRow::seconds).reversed());

        dayTable.getItems().setAll(dayRows);
        companyTable.getItems().setAll(companyRows);
        dayCount.setText(String.valueOf(dayRows.size()));
        companyCount.setText(String.valueOf(companyRows.size()));

        boolean dayMode = mode == Mode.DAY;
        dayTab.setVisible(!dayMode);
        dayTab.setManaged(!dayMode);
        if (dayMode) meetingsTab.setSelected(true);

        if (dayFilter != null && !byDay.containsKey(dayFilter)) dayFilter = null;
        showDetail();

        // --- totales: tiempo exacto, sin redondeo ---
        int meetings = rows.size();
        totalMoney.setText(Fmt.money(totalSeconds));
        totalSub.setText(Fmt.durationOrZero(totalSeconds) + "  ·  " + Fmt.count(meetings, "reunión", "reuniones"));
        // En grande horas y minutos; los segundos sueltos van justo debajo para que quepa.
        long extraSeconds = totalSeconds % 60;
        workedTime.setText(totalSeconds < 60 ? Fmt.durationOrZero(totalSeconds) : Fmt.duration(totalSeconds - extraSeconds));
        workedSub.setText((totalSeconds >= 60 && extraSeconds > 0 ? "y " + extraSeconds + " s  ·  " : "Exacto  ·  ")
                + String.format(Locale.US, "$%.0f por hora", Billing.HOURLY_RATE));
        paidMoney.setText(Fmt.money(paidSeconds));
        paidSub.setText(Fmt.durationOrZero(paidSeconds));
        pendingMoney.setText(Fmt.money(pendingSeconds));
        pendingSub.setText(Fmt.durationOrZero(pendingSeconds));

        double ratio = totalSeconds == 0 ? 0 : (double) paidSeconds / totalSeconds;
        paidProgress.setProgress(ratio);
        paidPercent.setText(totalSeconds == 0 ? "Sin reuniones" : Math.round(ratio * 100) + "% pagado");

        boolean pending = pendingSeconds > 0;
        payAll.setUserData(pending);
        payAll.setText(pending ? "Marcar pagado" : "Quitar pagos");
        payAll.setTooltip(new javafx.scene.control.Tooltip(pending
                ? "Marca todas las reuniones de este periodo como pagadas"
                : "Vuelve a dejar pendientes las reuniones de este periodo"));
        payAll.setGraphic(Ui.icon(pending ? Ui.I_CHECK : Ui.I_DELETE, 16));
        payAll.setGraphicTextGap(8);
        payAll.getStyleClass().removeAll("primary", "ghost");
        payAll.getStyleClass().add(pending ? "primary" : "ghost");
        payAll.setDisable(rows.isEmpty());
    }

    /** Llena la tabla de reuniones: todo el periodo, o solo el día elegido en "Por día". */
    private void showDetail() {
        List<VideoSession> meetings = new ArrayList<>(dayFilter == null
                ? periodSessions
                : grouped.getOrDefault(dayFilter, List.of()));
        meetings.sort(Comparator.comparing(VideoSession::recordedAt));
        detailTable.getItems().setAll(meetings);
        detailCount.setText(String.valueOf(meetings.size()));

        boolean showingMeetings = meetingsTab.isSelected();
        if (!showingMeetings) {
            detailTitle.setText("Doble clic en un día para ver sus reuniones");
        } else if (dayFilter != null) {
            detailTitle.setText("Solo el " + dayFilter.format(MEDIUM));
        } else {
            detailTitle.setText(mode == Mode.DAY ? "Todas las del día" : "Todas las del periodo");
        }
        boolean filtered = showingMeetings && dayFilter != null;
        clearFilter.setVisible(filtered);
        clearFilter.setManaged(filtered);
    }
}
