package com.zycu.guishop.admin;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** UTC storage, English month abbreviations, and client-selected local presentation. */
public final class JournalDates {
    private JournalDates() {}
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MMM/yy", Locale.ENGLISH);
    private static final DateTimeFormatter LIST = DateTimeFormatter.ofPattern("hh:mm:ss a (dd/MMM/yy)", Locale.ENGLISH);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MMM/yy hh:mm:ss a z", Locale.ENGLISH);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ENGLISH);
    public static String date(String time, ZoneId zone) {
        try { return DATE.format(Instant.parse(time).atZone(zone)); } catch (RuntimeException invalid) { return "Unknown"; }
    }
    public static String display(String time, ZoneId zone) {
        try { return LIST.format(Instant.parse(time).atZone(zone)); } catch (RuntimeException invalid) { return "Unknown"; }
    }
    public static String full(String time, ZoneId zone) {
        try { return FULL.format(Instant.parse(time).atZone(zone)); } catch (RuntimeException invalid) { return time == null ? "Unknown" : time; }
    }
    public static String zoneLabel(ZoneId zone) { return DateTimeFormatter.ofPattern("z", Locale.ENGLISH).format(ZonedDateTime.now(zone)); }
    public static boolean matches(String time, String search, ZoneId local) {
        try {
            var instant = Instant.parse(time);
            for (var zone : new ZoneId[]{local, ZoneId.of("UTC")}) {
                var value = instant.atZone(zone);
                if ((DATE.format(value) + " " + LIST.format(value) + " " + FULL.format(value) + " " + ISO.format(value)).toLowerCase(Locale.ROOT).contains(search)) return true;
            }
        } catch (RuntimeException ignored) {}
        return false;
    }
}
