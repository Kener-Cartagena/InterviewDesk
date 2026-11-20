package desk.ui;

import desk.db.Repository;
import desk.model.Billing;
import desk.service.Config;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.Locale;

/** Rutas de las carpetas, periodo de pago y regla de horas. */
public final class SettingsView extends VBox {
    public SettingsView(Repository repo, Runnable onSaved) {
        setSpacing(18);
        getStyleClass().add("page");

        HBox header = Ui.pageHeader(new Label("Ajustes"),
                new Label("Dónde buscar tus entrevistas y videos, y cómo se agrupan los pagos."));

        // --- carpetas ---
        TextField interviews = new TextField(Config.interviewsDir(repo).toString());
        TextField videos = new TextField(Config.videosDir(repo).toString());
        VBox folders = new VBox(16,
                field("Carpeta de entrevistas", "Una subcarpeta por empresa con el CV (PDF), el enlace y la job description.",
                        pathRow(interviews, "Carpeta de entrevistas")),
                field("Carpeta de videos", "Una subcarpeta por empresa con los .mp4 de cada reunión.",
                        pathRow(videos, "Carpeta de videos")));
        VBox foldersCard = Ui.section(Ui.I_FOLDER, "Carpetas", folders);

        // --- periodo de pago ---
        DatePicker anchor = new DatePicker(Config.payAnchor(repo).orElse(null));
        anchor.setPromptText("Elige un día");
        anchor.setPrefWidth(200);
        VBox payCard = Ui.section(Ui.I_CALENDAR, "Periodo de pago",
                field("Primer día de un periodo de pago",
                        "Sirve para que \"2 semanas\" coincida con tus pagos. Elige el primer día de cualquier "
                                + "periodo, de este o de uno pasado.",
                        anchor));

        // --- regla de horas (solo informativa) ---
        Label rule = Ui.label(String.format(Locale.US,
                "Se cuenta el tiempo exacto de cada reunión, sin redondeos. Cada minuto se paga a $%.2f por hora "
                        + "(por ejemplo, 2 h 07 min = $%.2f). Puedes corregir la duración de una reunión con doble "
                        + "clic sobre ella en Entrevistas.",
                Billing.HOURLY_RATE, Billing.money(2 * 3600 + 7 * 60)), "muted");
        rule.setWrapText(true);
        VBox ruleCard = Ui.section(Ui.I_MONEY, "Cómo se cuentan las horas", rule);

        Label saved = Ui.label("", "saved-note");
        Button save = Ui.button("Guardar cambios", Ui.I_CHECK, "primary");
        save.setOnAction(e -> {
            repo.putSetting(Config.INTERVIEWS_KEY, interviews.getText().trim());
            repo.putSetting(Config.VIDEOS_KEY, videos.getText().trim());
            repo.putSetting(Config.PAY_ANCHOR_KEY, anchor.getValue() == null ? "" : anchor.getValue().toString());
            saved.setText("Cambios guardados");
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            pause.setOnFinished(ev -> saved.setText(""));
            pause.play();
            onSaved.run();
        });
        HBox footer = Ui.row(14, Ui.grow(), saved, save);

        VBox column = new VBox(16, foldersCard, payCard, ruleCard, footer);
        column.setMaxWidth(860);
        column.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(header, column);
    }

    private static VBox field(String label, String help, javafx.scene.Node control) {
        Label h = Ui.label(help, "muted");
        h.setWrapText(true);
        return new VBox(6, Ui.label(label, "field-label"), control, h);
    }

    private HBox pathRow(TextField field, String title) {
        Button browse = Ui.button("Elegir…", Ui.I_FOLDER);
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(title);
            File current = new File(field.getText());
            if (current.isDirectory()) chooser.setInitialDirectory(current);
            File chosen = chooser.showDialog(getScene().getWindow());
            if (chosen != null) field.setText(chosen.getAbsolutePath());
        });
        HBox.setHgrow(field, Priority.ALWAYS);
        return Ui.row(10, field, browse);
    }
}
