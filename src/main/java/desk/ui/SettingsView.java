package desk.ui;

import desk.db.Repository;
import desk.service.Config;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;

/** Rutas de las carpetas, periodo de pago y regla de horas. */
public final class SettingsView extends VBox {
    public SettingsView(Repository repo, Runnable onSaved) {
        setSpacing(18);
        setPadding(new Insets(24, 0, 0, 0));
        setMaxWidth(780);

        TextField interviews = new TextField(Config.interviewsDir(repo).toString());
        TextField videos = new TextField(Config.videosDir(repo).toString());
        DatePicker anchor = new DatePicker(Config.payAnchor(repo).orElse(null));
        anchor.setPromptText("Elige un día");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        addPathRow(grid, 0, "Carpeta de entrevistas", interviews);
        addPathRow(grid, 1, "Carpeta de videos", videos);
        grid.add(new Label("Primer día de un periodo de pago"), 0, 2);
        grid.add(anchor, 1, 2);

        Label anchorHelp = new Label("Sirve para que \"2 semanas\" coincida con tus pagos. Elige el primer día "
                + "de cualquier periodo, de este o de uno pasado.");
        anchorHelp.getStyleClass().add("muted");
        anchorHelp.setWrapText(true);

        Button save = new Button("Guardar");
        save.getStyleClass().add("primary");
        save.setOnAction(e -> {
            repo.putSetting(Config.INTERVIEWS_KEY, interviews.getText().trim());
            repo.putSetting(Config.VIDEOS_KEY, videos.getText().trim());
            repo.putSetting(Config.PAY_ANCHOR_KEY, anchor.getValue() == null ? "" : anchor.getValue().toString());
            onSaved.run();
        });

        Label rule = new Label("Horas por reunión: hasta 30 min cuenta 0.5 h, de 31 a 60 min cuenta 1 h, "
                + "y cada hora empezada después suma 1 h. En cada reunión puedes poner las horas a mano "
                + "(doble clic sobre ella en la pestaña Entrevistas).");
        rule.getStyleClass().add("muted");
        rule.setWrapText(true);

        getChildren().addAll(grid, anchorHelp, save, rule);
    }

    private void addPathRow(GridPane grid, int row, String label, TextField field) {
        Button browse = new Button("Elegir…");
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(label);
            File current = new File(field.getText());
            if (current.isDirectory()) chooser.setInitialDirectory(current);
            File chosen = chooser.showDialog(getScene().getWindow());
            if (chosen != null) field.setText(chosen.getAbsolutePath());
        });
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
        grid.add(browse, 2, row);
    }
}
