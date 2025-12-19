package com.palacesky.controller.shared.utils;

import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    private static final DateTimeFormatter DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([smhdy])");

    private static final Locale FALLBACK_LOCALE = new Locale("pt", "BR");

    public static boolean isNewYear() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return (month == Calendar.DECEMBER && day == 31) || (month == Calendar.JANUARY && day == 1);
    }

    public static boolean isChristmas() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return month == Calendar.DECEMBER && (day >= 24 && day <= 26);
    }

    public static String formatDate(Long timestamp) {
        if (timestamp == null) return "";
        return formatDate(timestamp.longValue());
    }

    public static String formatDate(long timestamp) {
        try {
            LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            return DEFAULT_TIME_FORMAT.format(date);
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    public static String formatDuration(long millis) {
        return formatDuration(millis, FALLBACK_LOCALE);
    }

    public static String formatDuration(long millis, Locale locale) {
        Locale targetLocale = (locale != null) ? locale : FALLBACK_LOCALE;

        if (millis < 0) return Messages.translate(MessageKey.TIME_EXPIRED, targetLocale);
        if (millis < 1000) return Messages.translate(MessageKey.TIME_NOW, targetLocale);

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + " " + Messages.translate(days == 1 ? MessageKey.TIME_DAY_SINGULAR : MessageKey.TIME_DAY_PLURAL, targetLocale));
        if (hours > 0) parts.add(hours + " " + Messages.translate(hours == 1 ? MessageKey.TIME_HOUR_SINGULAR : MessageKey.TIME_HOUR_PLURAL, targetLocale));
        if (minutes > 0) parts.add(minutes + " " + Messages.translate(minutes == 1 ? MessageKey.TIME_MINUTE_SINGULAR : MessageKey.TIME_MINUTE_PLURAL, targetLocale));

        if (seconds > 0 && parts.isEmpty()) {
            parts.add(seconds + " " + Messages.translate(seconds == 1 ? MessageKey.TIME_SECOND_SINGULAR : MessageKey.TIME_SECOND_PLURAL, targetLocale));
        }

        if (parts.isEmpty()) {
            return Messages.translate(MessageKey.TIME_NOW, targetLocale);
        }

        return String.join(", ", parts);
    }

    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) return -1;
        String input = durationStr.toLowerCase().trim();
        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) return -1;

        try {
            long value = Long.parseLong(matcher.group(1));
            if (value < 0) return -1;
            String unit = matcher.group(2);
            long multiplier;
            long overflowCheck = Long.MAX_VALUE;

            switch (unit) {
                case "s": multiplier = TimeUnit.SECONDS.toMillis(1); break;
                case "m": multiplier = TimeUnit.MINUTES.toMillis(1); break;
                case "h": multiplier = TimeUnit.HOURS.toMillis(1); break;
                case "d": multiplier = TimeUnit.DAYS.toMillis(1); break;
                case "y": multiplier = TimeUnit.DAYS.toMillis(365); break;
                default: return -1;
            }
            overflowCheck /= multiplier;
            if (value > overflowCheck) return -1;
            return value * multiplier;
        } catch (Exception e) {
            return -1;
        }
    }
}