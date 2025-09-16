package com.realmmc.controller.shared.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class TimeUtils {
    private static final DateTimeFormatter DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    /**
     * Boolean para verificar se é dia 31/12 ou 01/01 para constatar ano novo.
     * @return
     */
    public boolean isNewYear() {
        int month = Calendar.getInstance().get(Calendar.MONTH);
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        return (month == Calendar.DECEMBER && day == 31) || (month == Calendar.JANUARY && day == 1);
    }
    /**
     * Boolean para verificar se é dia 24/12, 25/12 ou 26/12 para constatar natal.
     * @return
     */
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
}
