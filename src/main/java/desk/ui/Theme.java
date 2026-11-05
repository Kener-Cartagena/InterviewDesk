package desk.ui;

/** Acceso a la hoja de estilos. */
final class Theme {
    private Theme() {}

    static String css() {
        return Theme.class.getResource("/desk/style.css").toExternalForm();
    }
}
