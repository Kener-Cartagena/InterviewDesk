package desk.model;

/**
 * Regla de horas pagadas. Si cambia la regla, se cambia SOLO aquí.
 * Hasta 30 min = 0.5 h · de 31 a 60 min = 1 h · luego cada hora empezada suma 1 h.
 */
public final class Billing {
    private Billing() {}

    public static double hours(int durationSeconds) {
        double minutes = durationSeconds / 60.0;
        if (minutes <= 0) return 0;
        if (minutes <= 30) return 0.5;
        return Math.ceil(minutes / 60.0);
    }
}
