package desk.ui;

import desk.db.Repository;
import desk.model.VideoSession;
import desk.service.Config;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
 */
public final class HoursView extends VBox {
    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE d MMM yyyy", ES);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final double HOURLY_RATE = 15.0; // Tarifa fija por hora a pagar

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
        final double hours;
        final BooleanProperty paid;

        DayRow(LocalDate date, int count, int seconds, double hours, boolean paid) {
            this.date = date;
            this.count = count;
            this.seconds = seconds;
            this.hours = hours;
            this.paid = new SimpleBooleanProperty(paid);
        }
    }

    private record CompanyRow(String name, int count, int seconds, double hours) {}

    private final Repository repo;
    private Mode mode = Mode.TWO_WEEKS;
    private LocalDate cursor = LocalDate.now();
    private boolean syncing;
    private Map<LocalDate, List<VideoSession>> grouped = new TreeMap<>();
    private List<VideoSession> currentPeriodSessions = new ArrayList<>();

    private final DatePicker picker = new DatePicker(LocalDate.now());
    private final Label rangeLabel = new Label();
    private final Label totalHours = new Label("0 h ($0.00)");
    private final Label rawTotalHours = new Label("—"); // Tarjeta independiente para el tiempo real en bruto
    private final Label paidHours = new Label("0 h ($0.00)");
    private final Label pendingHours = new Label("0 h ($0.00)");
    private final Button payAll = new Button();
    private final TableView<DayRow> dayTable = new TableView<>();
    private final TableView<VideoSession> detailTable = new TableView<>();
    private final TableView<CompanyRow> companyTable = new TableView<>();

    public HoursView(Repository repo) {
        this.repo = repo;
        setSpacing(14);
        setPadding(new Insets(18, 0, 0, 0));

        // --- selector de periodo y fecha ---
        ToggleGroup group = new ToggleGroup();
        HBox modes = new HBox(6);
        for (Mode m : Mode.values()) {
            ToggleButton b = new ToggleButton(m.label);
            b.setToggleGroup(group);
            b.setUserData(m);
            b.setSelected(m == mode);
            modes.getChildren().add(b);
        }
        group.selectedToggleProperty().addListener((o, previous, selected) -> {
            if (selected == null) {
                previous.setSelected(true); // siempre debe haber un periodo elegido
                return;
            }
            mode = (Mode) selected.getUserData();
            apply();
        });

        Button prev = new Button("‹");
        prev.setOnAction(e -> shift(-1));
        Button next = new Button("›");
        next.setOnAction(e -> shift(1));
        Button today = new Button("Hoy");
        today.setOnAction(e -> {
            cursor = LocalDate.now();
            apply();
        });
        picker.setPrefWidth(150);
        picker.valueProperty().addListener((o, was, date) -> {
            if (!syncing && date != null) {
                cursor = date;
                apply();
            }
        });

        payAll.getStyleClass().add("primary");
        payAll.setOnAction(e -> togglePayAll());
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(10, modes, prev, picker, next, today, grow, payAll);
        top.setAlignment(Pos.CENTER_LEFT);

        rangeLabel.getStyleClass().add("range");

        // --- totales (tarjetas independientes) ---
        pendingHours.getStyleClass().add("stat-pending");
        HBox cards = new HBox(14,
                card("Total del periodo", totalHours),
                card("Tiempo real (Bruto)", rawTotalHours),
                card("Pagadas", paidHours),
                card("Pendientes de pago", pendingHours));

        // --- tablas ---
        buildDayTable();
        buildDetailTable();
        buildCompanyTable();
        dayTable.setPrefHeight(220);
        dayTable.setMinHeight(120);
        
        VBox left = new VBox(8, caption("Por día"), dayTable,
                caption(getDetailCaptionText()), detailTable);
        VBox.setVgrow(detailTable, Priority.ALWAYS);
        VBox right = new VBox(8, caption("Por empresa (total del periodo)"), companyTable);
        VBox.setVgrow(companyTable, Priority.ALWAYS);
        HBox tables = new HBox(16, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(620);
        right.setPrefWidth(380);
        VBox.setVgrow(tables, Priority.ALWAYS);

        dayTable.getSelectionModel().selectedItemProperty().addListener((o, was, row) -> {
            if (mode == Mode.DAY) {
                List<VideoSession> meetings = new ArrayList<>();
                if (row != null) meetings.addAll(grouped.getOrDefault(row.date, List.of()));
                meetings.sort(Comparator.comparing(VideoSession::recordedAt));
                detailTable.getItems().setAll(meetings);
            }
        });

        getChildren().addAll(top, rangeLabel, cards, tables);
    }

    private String getDetailCaptionText() {
        return mode == Mode.DAY ? "Reuniones del día seleccionado" : "Reuniones del periodo completo";
    }

    // ---------- construcción ----------

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("caption");
        return label;
    }

    private static VBox card(String caption, Label value) {
        value.getStyleClass().add("stat");
        VBox box = new VBox(4, caption(caption), value);
        box.getStyleClass().add("card");
        box.setMinWidth(200);
        return box;
    }

    private void buildDayTable() {
        TableColumn<DayRow, String> date = new TableColumn<>("Fecha");
        date.setCellValueFactory(d -> new ReadOnlyStringWrapper(capitalize(d.getValue().date.format(LONG))));
        date.setMinWidth(190);
        TableColumn<DayRow, String> count = new TableColumn<>("Reuniones");
        count.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().count)));
        TableColumn<DayRow, String> real = new TableColumn<>("Tiempo real");
        real.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.duration(d.getValue().seconds)));
        
        // Columna "Horas a pagar" eliminada de aquí por solicitud

        TableColumn<DayRow, Boolean> paid = new TableColumn<>("Pagado");
        paid.setCellValueFactory(d -> d.getValue().paid);
        paid.setCellFactory(CheckBoxTableCell.forTableColumn(paid));
        paid.setEditable(true);
        
        dayTable.getColumns().addAll(date, count, real, paid);
        dayTable.setEditable(true);
        dayTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dayTable.setPlaceholder(new Label("No hay reuniones en este periodo."));
    }

    private void buildDetailTable() {
        TableColumn<VideoSession, String> dateCol = new TableColumn<>("Fecha");
        dateCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().recordedAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        TableColumn<VideoSession, String> time = new TableColumn<>("Hora");
        time.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().recordedAt().format(TIME)));
        TableColumn<VideoSession, String> company = new TableColumn<>("Empresa");
        company.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().companyName()));
        TableColumn<VideoSession, String> file = new TableColumn<>("Video");
        file.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().fileName()));
        TableColumn<VideoSession, String> length = new TableColumn<>("Duración");
        length.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.duration(d.getValue().durationSeconds())));
        
        // Columna "Horas" eliminada de aquí por solicitud

        detailTable.getColumns().addAll(dateCol, time, company, file, length);
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detailTable.setPlaceholder(new Label("No hay reuniones para mostrar."));
    }

    private void buildCompanyTable() {
        TableColumn<CompanyRow, String> name = new TableColumn<>("Empresa");
        name.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name()));
        TableColumn<CompanyRow, String> count = new TableColumn<>("Reuniones");
        count.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().count())));
        TableColumn<CompanyRow, String> hours = new TableColumn<>("Horas");
        hours.setCellValueFactory(d -> new ReadOnlyStringWrapper(Fmt.hours(d.getValue().hours())));
        companyTable.getColumns().addAll(name, count, hours);
        companyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        companyTable.setPlaceholder(new Label("No hay reuniones en este periodo."));
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
        rangeLabel.setText(mode == Mode.DAY
                ? capitalize(r[0].format(LONG))
                : "Del " + r[0].format(LONG) + " al " + last.format(LONG));

        List<VideoSession> rows = repo.sessionsBetween(r[0], r[1]);
        currentPeriodSessions = rows;

        Map<LocalDate, List<VideoSession>> byDay = new TreeMap<>(Comparator.reverseOrder());
        Map<String, List<VideoSession>> byCompany = new LinkedHashMap<>();
        
        double paid = 0;
        int rawTotalSeconds = 0;

        for (VideoSession s : rows) {
            byDay.computeIfAbsent(s.recordedAt().toLocalDate(), d -> new ArrayList<>()).add(s);
            byCompany.computeIfAbsent(s.companyName(), n -> new ArrayList<>()).add(s);
            rawTotalSeconds += s.durationSeconds(); // Acumula el tiempo real en bruto
            if (s.paid()) paid += s.paidHours();
        }
        grouped = byDay;

        List<DayRow> dayRows = new ArrayList<>();
        for (Map.Entry<LocalDate, List<VideoSession>> e : byDay.entrySet()) {
            List<VideoSession> list = e.getValue();
            DayRow row = new DayRow(
                    e.getKey(),
                    list.size(),
                    list.stream().mapToInt(VideoSession::durationSeconds).sum(),
                    list.stream().mapToDouble(VideoSession::paidHours).sum(),
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
            companyRows.add(new CompanyRow(
                    e.getKey(),
                    list.size(),
                    list.stream().mapToInt(VideoSession::durationSeconds).sum(),
                    list.stream().mapToDouble(VideoSession::paidHours).sum()));
        }
        companyRows.sort(Comparator.comparingDouble(CompanyRow::hours).reversed());

        dayTable.getItems().setAll(dayRows);
        companyTable.getItems().setAll(companyRows);

        if (mode == Mode.DAY) {
            DayRow selected = dayTable.getSelectionModel().getSelectedItem();
            LocalDate keep = selected == null ? null : selected.date;
            if (!dayRows.isEmpty()) {
                DayRow pick = dayRows.stream().filter(d -> d.date.equals(keep)).findFirst().orElse(dayRows.get(0));
                dayTable.getSelectionModel().select(pick);
            } else {
                detailTable.getItems().clear();
            }
        } else {
            List<VideoSession> allPeriodMeetings = new ArrayList<>(rows);
            allPeriodMeetings.sort(Comparator.comparing(VideoSession::recordedAt));
            detailTable.getItems().setAll(allPeriodMeetings);
        }

        // 1. Aplicamos la regla de 15 en 15 sobre el total de segundos en bruto del periodo
        double totalBillableHours = Fmt.calculateBillableHours(rawTotalSeconds);
        double totalMoney = totalBillableHours * HOURLY_RATE;

        // 2. Verificamos si todo el periodo está pagado (revisando si las sesiones están pagadas)
        boolean allPaid = rows.stream().allMatch(VideoSession::paid) && !rows.isEmpty();

        // 3. Si está pagado: 'Pagadas' muestra el total calculado y su dinero. 'Pendientes' muestra $0.00.
        //    Si NO está pagado: 'Pagadas' muestra 0 h ($0.00) y 'Pendientes' muestra el total cobrable.
        double paidHoursVal = allPaid ? totalBillableHours : 0.0;
        double paidMoney = allPaid ? totalMoney : 0.0;

        double pendingHoursVal = allPaid ? 0.0 : totalBillableHours;
        double pendingMoney = allPaid ? 0.0 : totalMoney;

        // Actualizamos las tarjetas principales limpiamente
        totalHours.setText(String.format("%s ($%.2f)", Fmt.hours(totalBillableHours), totalMoney));
        rawTotalHours.setText(Fmt.rawDuration(rawTotalSeconds)); // Tiempo real intacto en su tarjeta al lado
        
        paidHours.setText(String.format("%s ($%.2f)", Fmt.hours(paidHoursVal), paidMoney));
        pendingHours.setText(String.format("%s ($%.2f)", Fmt.hours(pendingHoursVal), pendingMoney));

        boolean pending = pendingMoney > 0;
        payAll.setUserData(pending);
        payAll.setText(pending ? "Marcar todo como pagado" : "Quitar pagos del periodo");
        payAll.setDisable(rows.isEmpty());
    }
}