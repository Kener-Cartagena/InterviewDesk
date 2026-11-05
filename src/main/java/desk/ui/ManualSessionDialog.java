package desk.ui;

import desk.db.Repository;
import desk.model.Company;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Registrar una reunión que no se grabó. */
final class ManualSessionDialog {
    private ManualSessionDialog() {}

    /** @return true si se guardó la reunión */
    static boolean add(Company company, Repository repo) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registrar reunión sin video");
        dialog.setHeaderText(company.name());
        dialog.getDialogPane().getStylesheets().add(Theme.css());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        DatePicker date = new DatePicker(LocalDate.now());
        TextField time = new TextField("10:00");
        Spinner<Integer> minutes = new Spinner<>(1, 1000, 30, 5);
        minutes.setEditable(true);
        Label error = new Label();
        error.getStyleClass().add("error");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12, 4, 4, 4));
        grid.addRow(0, new Label("Fecha"), date);
        grid.addRow(1, new Label("Hora (HH:mm)"), time);
        grid.addRow(2, new Label("Duración (min)"), minutes);
        grid.add(error, 0, 3, 2, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                minutes.increment(0); // confirma lo escrito a mano
                LocalTime.parse(time.getText().trim());
                if (date.getValue() == null) {
                    error.setText("Elige una fecha.");
                    ev.consume();
                }
            } catch (DateTimeParseException e) {
                error.setText("La hora debe tener formato HH:mm, por ejemplo 14:30.");
                ev.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return false;

        LocalDateTime when = LocalDateTime.of(date.getValue(), LocalTime.parse(time.getText().trim()));
        repo.insertManualSession(company.id(), when, minutes.getValue() * 60);
        return true;
    }
}
