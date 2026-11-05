package desk.ui;

import java.util.Locale;

/** Formatos de texto para duraciones y horas. */
public final class Fmt {
    private Fmt() {}

    public static String duration(int seconds) {
        if (seconds <= 0) return "—";
        int minutes = seconds / 60;
        if (minutes < 60) return minutes + " min";
        return String.format(Locale.ROOT, "%d h %02d min", minutes / 60, minutes % 60);
    }

    public static String rawDuration(int totalSeconds) {
        if (totalSeconds <= 0) return "—";
        int minutes = totalSeconds / 60;
        if (minutes < 60) return minutes + " min";
        return String.format(Locale.ROOT, "%d h %02d min", minutes / 60, minutes % 60);
    }

    /**
     * Calcula las horas cobrables aplicando el redondeo de 15 en 15 sobre el total de segundos:
     * - Menos de 15 min -> :00 min
     * - De 15 a 29 min -> :30 min
     * - De 30 a 44 min -> :45 min
     * - De 45 a 59 min -> Siguiente hora (:00)
     */
    public static double calculateBillableHours(int totalSeconds) {
        if (totalSeconds <= 0) return 0.0;

        int totalMinutes = totalSeconds / 60;
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;

        int roundedMins;
        if (mins < 15) {
            roundedMins = 0;
        } else if (mins < 30) {
            roundedMins = 30;
        } else if (mins < 45) {
            roundedMins = 45;
        } else {
            hours += 1;
            roundedMins = 0;
        }

        return hours + (roundedMins / 60.0);
    }

    /** Muestra las horas en formato legible "X h Y min" o "X h" para evitar confusiones con decimales (.50). */
    public static String hours(double h) {
        if (h <= 0) return "0 h";
        int totalMins = (int) Math.round(h * 60);
        int hours = totalMins / 60;
        int mins = totalMins % 60;
        if (mins == 0) {
            return hours + " h";
        }
        return String.format(Locale.ROOT, "%d h %02d min", hours, mins);
    }
}