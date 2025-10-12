package com.realmmc.controller.shared.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {
    private static final DateTimeFormatter DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhd])");

    public boolean isNewYear() {
        int month = Calendar.getInstance().get(Calendar.MONTH);
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        return (month == Calendar.DECEMBER && day == 31) || (month == Calendar.JANUARY && day == 1);
    }

    public boolean isChristmas() {
        int month = Calendar.getInstance().get(Calendar.MONTH);
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        return month == Calendar.DECEMBER && (day == 24 || day == 25 || day == 26);
    }

    public static String formatDate(Long timestamp) {
        if (timestamp == null) return "";
        return formatDate(timestamp.longValue());
    }

    public static String formatDate(long timestamp) {
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return DEFAULT_TIME_FORMAT.format(date);
    }

    public static String formatDuration(long millis) {
        if (millis < 0) return "expirado";
        if (millis == 0) return "agora";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + (days == 1 ? " dia" : " dias"));
        if (hours > 0) parts.add(hours + (hours == 1 ? " hora" : " horas"));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minuto" : " minutos"));

        if (parts.isEmpty()) {
            long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
            if (seconds > 0) {
                parts.add(seconds + (seconds == 1 ? " segundo" : " segundos"));
            } else {
                return "agora";
            }
        }

        return String.join(", ", parts);
    }

    public static long parseDuration(String durationStr) {
        Matcher matcher = DURATION_PATTERN.matcher(durationStr.toLowerCase());
        if (!matcher.matches()) {
            return -1;
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "s" -> TimeUnit.SECONDS.toMillis(value);
            case "m" -> TimeUnit.MINUTES.toMillis(value);
            case "h" -> TimeUnit.HOURS.toMillis(value);
            case "d" -> TimeUnit.DAYS.toMillis(value);
            default -> -1;
        };
    }
}