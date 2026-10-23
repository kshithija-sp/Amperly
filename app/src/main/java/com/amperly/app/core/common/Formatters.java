package com.amperly.app.core.common;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class Formatters {

    private Formatters() {
    }

    public static String money(double amount) {
        return String.format(Locale.US, "₹%.2f", amount);
    }

    public static String moneyShort(double amount) {
        return String.format(Locale.US, "₹%.0f", amount);
    }

    public static String kwh(double value) {
        return String.format(Locale.US, "%.2f kWh", value);
    }

    public static String percent(int value) {
        return value + "%";
    }

    /**
     * A rounded percentage that never claims zero when something did actually happen.
     *
     * <p>Utilization is a small number for a single-port host — two hours of charging across a
     * month of bookable hours is under 1% — and {@code %.0f} reported that as "0%", which reads
     * as broken rather than as tiny.
     */
    public static String percent(double value) {
        if (value <= 0.0) {
            return "0%";
        }
        if (value < 1.0) {
            return "<1%";
        }
        return String.format(Locale.US, "%.0f%%", value);
    }

    public static String minutes(int totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + " min";
        }
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }

    public static String clock(long timeMs) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timeMs));
    }

    public static String dateTime(long timeMs) {
        return new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(new Date(timeMs));
    }

    public static String dayAndSlot(long startMs, long endMs) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(startMs);
        Calendar today = Calendar.getInstance();
        String day;
        if (sameDay(start, today)) {
            day = "Today";
        } else {
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            day = sameDay(start, tomorrow) ? "Tomorrow"
                    : new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(startMs));
        }
        return day + ", " + clock(startMs) + " – " + clock(endMs);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Honest freshness label: never claim live hardware data we do not have. */
    public static String agoFromNow(long thenMs) {
        long now = System.currentTimeMillis();
        long diff = Math.max(0, now - thenMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        return days + (days == 1 ? " day ago" : " days ago");
    }

    public static String minutesFromMidnight(int minutesFromMidnight) {
        int hours = (minutesFromMidnight / 60) % 24;
        int minutes = minutesFromMidnight % 60;
        return String.format(Locale.US, "%02d:%02d", hours, minutes);
    }
}
