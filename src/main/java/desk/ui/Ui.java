package desk.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/** Piezas visuales reutilizables: iconos, tarjetas, encabezados, insignias y avatares. */
public final class Ui {
    private Ui() {}

    // Iconos (24x24, estilo Material).
    public static final String I_WORK = "M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z";
    public static final String I_CLOCK = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z";
    public static final String I_GEAR = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    public static final String I_SYNC = "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z";
    public static final String I_UPLOAD = "M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z";
    public static final String I_SEARCH = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
    public static final String I_COPY = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z";
    public static final String I_VIDEO = "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z";
    public static final String I_DOC = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
    public static final String I_ADD = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    public static final String I_DELETE = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
    public static final String I_LEFT = "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z";
    public static final String I_RIGHT = "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z";
    public static final String I_CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    public static final String I_CHECK_CIRCLE = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z";
    public static final String I_MONEY = "M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z";
    public static final String I_FOLDER = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    public static final String I_CALENDAR = "M20 3h-1V1h-2v2H7V1H5v2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 18H4V8h16v13z";
    public static final String I_LINK = "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z";
    public static final String I_PLAY = "M8 5v14l11-7z";

    private static final int AVATAR_TONES = 8;

    /** Icono vectorial; su color se controla con -fx-background-color en CSS (clase "icon"). */
    public static Region icon(String svg, double size) {
        SVGPath path = new SVGPath();
        path.setContent(svg);
        Region r = new Region();
        r.setShape(path);
        r.setScaleShape(true);
        r.setMinSize(size, size);
        r.setPrefSize(size, size);
        r.setMaxSize(size, size);
        r.getStyleClass().add("icon");
        return r;
    }

    public static <T extends Labeled> T withIcon(T control, String svg) {
        control.setGraphic(icon(svg, 16));
        control.setGraphicTextGap(8);
        control.setContentDisplay(ContentDisplay.LEFT);
        return control;
    }

    public static Button button(String text, String svg, String... styles) {
        Button b = withIcon(new Button(text), svg);
        b.getStyleClass().addAll(styles);
        b.setMinWidth(Region.USE_PREF_SIZE); // que el texto nunca se corte con "…"
        return b;
    }

    /** Botón cuadrado solo con icono. */
    public static Button iconButton(String svg, String tooltip) {
        Button b = new Button();
        b.setGraphic(icon(svg, 18));
        b.getStyleClass().add("icon-button");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return b;
    }

    public static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Encabezado de página: título grande + subtítulo, y a la derecha lo que se pase como acciones. */
    public static HBox pageHeader(Label title, Label subtitle, Node... actions) {
        title.getStyleClass().add("page-title");
        subtitle.getStyleClass().add("page-subtitle");
        VBox text = new VBox(2, title, subtitle);
        HBox box = new HBox(12);
        box.getChildren().addAll(text, grow());
        box.getChildren().addAll(actions);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("page-header");
        return box;
    }

    /** Tarjeta con encabezado (icono + título + extra a la derecha) y contenido que crece. */
    public static VBox section(String svg, String title, Node content, Node... headerExtras) {
        return section(svg, new Label(title), content, headerExtras);
    }

    public static VBox section(String svg, Label title, Node content, Node... headerExtras) {
        title.getStyleClass().add("section-title");
        HBox header = row(10, iconBubble(svg), title, grow());
        header.getChildren().addAll(headerExtras);
        VBox card = new VBox(12, header, content);
        card.getStyleClass().add("card");
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    public static StackPane iconBubble(String svg) {
        StackPane p = new StackPane(icon(svg, 15));
        p.getStyleClass().add("icon-bubble");
        p.setMinSize(30, 30);
        p.setMaxSize(30, 30);
        return p;
    }

    /** Insignia de color: tonos "ok", "warn", "info", "muted". */
    public static Label badge(String text, String tone) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", "badge-" + tone);
        return l;
    }

    public static void setBadge(Label badge, String text, String tone) {
        badge.setText(text);
        badge.getStyleClass().removeIf(s -> s.startsWith("badge-"));
        badge.getStyleClass().add("badge-" + tone);
    }

    /** Círculo con iniciales; el color sale del nombre para que cada empresa tenga el suyo. */
    public static StackPane avatar(String name, double size) {
        Label initials = new Label(initials(name));
        initials.getStyleClass().add("avatar-text");
        initials.setStyle("-fx-font-size: " + Math.round(size * 0.38) + "px;");
        StackPane p = new StackPane(initials);
        p.getStyleClass().addAll("avatar", tone(name));
        p.setMinSize(size, size);
        p.setPrefSize(size, size);
        p.setMaxSize(size, size);
        return p;
    }

    public static void updateAvatar(StackPane avatar, String name) {
        ((Label) avatar.getChildren().get(0)).setText(initials(name));
        avatar.getStyleClass().removeIf(s -> s.startsWith("tone-"));
        avatar.getStyleClass().add(tone(name));
    }

    private static String tone(String name) {
        return "tone-" + Math.floorMod(name == null ? 0 : name.hashCode(), AVATAR_TONES);
    }

    static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return name.substring(0, 1).toUpperCase();
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[1].substring(0, 1) : (parts[0].length() > 1 ? parts[0].substring(1, 2) : "");
        return (first + second).toUpperCase();
    }

    /** Mensaje centrado para cuando no hay nada que mostrar. */
    public static VBox emptyState(String svg, String title, String text) {
        StackPane bubble = new StackPane(icon(svg, 30));
        bubble.getStyleClass().add("empty-bubble");
        bubble.setMinSize(72, 72);
        bubble.setMaxSize(72, 72);
        Label t = label(title, "empty-title");
        Label d = label(text, "muted");
        d.setWrapText(true);
        d.setMaxWidth(360);
        d.setAlignment(Pos.CENTER);
        d.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox box = new VBox(12, bubble, t, d);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        return box;
    }

    public static Label tablePlaceholder(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }
}
