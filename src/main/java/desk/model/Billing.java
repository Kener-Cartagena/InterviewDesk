package desk.model;

/**
 * Regla de pago. Si cambia la regla, se cambia SOLO aquí.
 * Se cuenta el tiempo exacto de cada reunión, sin redondeos, a una tarifa fija por hora.
 */
public final class Billing {
    private Billing() {}

    /** Tarifa fija por hora. */
    public static final double HOURLY_RATE = 15.0;

    /** Horas exactas (con decimales) de una duración en segundos. */
    public static double hours(int durationSeconds) {
        return durationSeconds <= 0 ? 0 : durationSeconds / 3600.0;
    }

    /** Dinero exacto por una duración en segundos. */
    public static double money(long durationSeconds) {
        return durationSeconds <= 0 ? 0 : durationSeconds / 3600.0 * HOURLY_RATE;
    }
}
