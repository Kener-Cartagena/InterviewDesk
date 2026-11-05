package desk.ui;

import desk.db.Repository;
import desk.model.VideoSession;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Editar fecha, duración y horas pagadas de una reunión. */
final class SessionDialog {
    private SessionDialog() {}

    /** @return true si se guardaron cambios */
    static boolean edit(VideoSession s, Repository repo) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar reunión");
        dialog.setHeaderText(s.companyName() + " · " + s.fileName());
        dialog.getDialogPane().getStylesheets().add(Theme.css());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        DatePicker date = new DatePicker(s.recordedAt().toLocalDate());
        TextField time = new TextField(s.recordedAt().toLocalTime().withSecond(0).withNano(0).toString());
        int originalMinutes = s.durationSeconds() / 60;
        Spinner<Integer> minutes = new Spinner<>(0, 1000, originalMinutes, 1);
        minutes.setEditable(true);
        TextField paid = new TextField(s.billedOverride() == null ? "" : String.valueOf(s.billedOverride()));
        paid.setPromptText("Automático: " + Fmt.hours(desk.model.Billing.hours(s.durationSeconds())));
        Label error = new Label();
        error.getStyleClass().add("error");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12, 4, 4, 4));
        grid.addRow(0, new Label("Fecha"), date);
        grid.addRow(1, new Label("Hora (HH:mm)"), time);
        grid.addRow(2, new Label("Duración (min)"), minutes);
        grid.addRow(3, new Label("Horas pagadas"), paid);
        grid.add(error, 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                minutes.increment(0); // confirma lo escrito a mano en el spinner
                LocalTime.parse(time.getText().trim());
                if (date.getValue() == null) throw new IllegalArgumentException("Elige una fecha.");
                String raw = paid.getText().trim().replace(',', '.');
                if (!raw.isEmpty()) Double.parseDouble(raw);
            } catch (DateTimeParseException e) {
                error.setText("La hora debe tener formato HH:mm, por ejemplo 14:30.");
                ev.consume();
            } catch (NumberFormatException e) {
                error.setText("Horas pagadas debe ser un número, por ejemplo 1 o 0.5.");
                ev.consume();
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                ev.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return false;

        LocalDateTime when = LocalDateTime.of(date.getValue(), LocalTime.parse(time.getText().trim()));
        // Si no tocaste los minutos, se conservan los segundos exactos leídos del video.
        int seconds = minutes.getValue() == originalMinutes ? s.durationSeconds() : minutes.getValue() * 60;
        String raw = paid.getText().trim().replace(',', '.');
        Double override = raw.isEmpty() ? null : Double.parseDouble(raw);
        repo.updateSession(s.id(), when, seconds, override);
        return true;
    }
}
