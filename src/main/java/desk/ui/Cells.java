package desk.ui;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/** Celdas de tabla con más estilo que texto plano. */
final class Cells {
    private Cells() {}

    /** Insignia verde "Pagado" o ámbar "Pendiente". */
    static <S> TableCell<S, Boolean> paidBadge() {
        return new TableCell<>() {
            private final Label badge = Ui.badge("", "ok");

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(Boolean paid, boolean empty) {
                super.updateItem(paid, empty);
                if (empty || paid == null) {
                    setGraphic(null);
                    return;
                }
                Ui.setBadge(badge, paid ? "Pagado" : "Pendiente", paid ? "ok" : "warn");
                setGraphic(badge);
            }
        };
    }

    /** Nombre de empresa con su avatar de iniciales. */
    static <S> TableCell<S, String> company() {
        return new TableCell<>() {
            private final StackPane avatar = Ui.avatar("", 24);
            private final Label name = new Label();
            private final HBox box = Ui.row(10, avatar, name);

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                Ui.updateAvatar(avatar, value);
                name.setText(value);
                setGraphic(box);
            }
        };
    }
}
