package desk.ui;

import desk.model.Billing;

import java.util.Locale;

/** Formatos de texto para duraciones y dinero. Nunca redondea el tiempo: muestra los minutos tal cual. */
public final class Fmt {
    private Fmt() {}

    /** Tiempo exacto, sin redondear: "45 s", "12 min 12 s", "2 h 07 min", "1 h 07 min 05 s". */
    public static String duration(long seconds) {
        if (seconds <= 0) return "—";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder out = new StringBuilder();
        if (h > 0) out.append(h).append(" h");
        if (m > 0 || (h > 0 && s > 0)) {
            if (out.length() > 0) out.append(' ');
            out.append(h > 0 ? String.format(Locale.ROOT, "%02d", m) : String.valueOf(m)).append(" min");
        }
        if (s > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(out.length() > 0 ? String.format(Locale.ROOT, "%02d", s) : String.valueOf(s)).append(" s");
        }
        return out.toString();
    }

    /** Igual que {@link #duration} pero muestra "0 min" en vez de "—". */
    public static String durationOrZero(long seconds) {
        return seconds <= 0 ? "0 min" : duration(seconds);
    }

    /** Dinero exacto por esa cantidad de segundos, por ejemplo "$39.25". */
    public static String money(long seconds) {
        return String.format(Locale.US, "$%,.2f", Billing.money(seconds));
    }

    public static String count(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
