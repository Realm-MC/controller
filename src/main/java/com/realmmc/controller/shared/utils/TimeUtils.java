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

    // Formato padrão para datas (dd/MM/yyyy às HH:mm)
    private static final DateTimeFormatter DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    // Padrão regex para parsear durações como "10s", "5m", "1h", "3d", "1y"
    // Captura o número (grupo 1) e a unidade (grupo 2)
    // Permite espaços opcionais entre número e unidade (ex: "10 s")
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([smhdy])");

    /**
     * Verifica se a data atual é véspera ou dia de Ano Novo.
     * @return true se for 31 de Dezembro ou 1 de Janeiro.
     */
    public static boolean isNewYear() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH); // Janeiro é 0, Dezembro é 11
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return (month == Calendar.DECEMBER && day == 31) || (month == Calendar.JANUARY && day == 1);
    }

    /**
     * Verifica se a data atual está no período de Natal (24, 25 ou 26 de Dezembro).
     * @return true se for um dos dias de Natal.
     */
    public static boolean isChristmas() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH); // Dezembro é 11
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return month == Calendar.DECEMBER && (day >= 24 && day <= 26);
    }

    /**
     * Formata um timestamp (milissegundos desde a epoch) para uma string legível.
     * Exemplo: 1678886400000 -> "15/03/2023 às 10:00"
     * @param timestamp Objeto Long contendo o timestamp em milissegundos.
     * @return String formatada ou "" se o timestamp for nulo.
     */
    public static String formatDate(Long timestamp) {
        if (timestamp == null) return "";
        return formatDate(timestamp.longValue());
    }

    /**
     * Formata um timestamp (milissegundos desde a epoch) para uma string legível.
     * Exemplo: 1678886400000 -> "15/03/2023 às 10:00"
     * @param timestamp O timestamp primitivo long em milissegundos.
     * @return String formatada.
     */
    public static String formatDate(long timestamp) {
        try {
            LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            return DEFAULT_TIME_FORMAT.format(date);
        } catch (Exception e) {
            // Em caso de erro (timestamp inválido?), retorna o número
            return String.valueOf(timestamp);
        }
    }

    /**
     * Formata uma duração em milissegundos para uma string legível e concisa.
     * Exemplo: 90061000 -> "1 dia, 1 hora, 1 minuto"
     * @param millis A duração em milissegundos.
     * @return String formatada (ex: "3 dias, 2 horas", "5 minutos", "agora", "expirado").
     */
    public static String formatDuration(long millis) {
        if (millis < 0) return "expirado";
        if (millis < 1000) return "agora"; // Menos de 1 segundo

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + (days == 1 ? " dia" : " dias"));
        if (hours > 0) parts.add(hours + (hours == 1 ? " hora" : " horas"));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minuto" : " minutos"));

        // Só mostra segundos se for a única unidade ou se as outras forem zero E minutos > 0
        if (seconds > 0 && parts.isEmpty()) { // Se só sobrou segundos
            parts.add(seconds + (seconds == 1 ? " segundo" : " segundos"));
        } else if (seconds > 0 && days == 0 && hours == 0 && minutes > 0) {
            // Opcional: Mostrar segundos se só tiver minutos? Por agora não.
            // parts.add(seconds + (seconds == 1 ? " segundo" : " segundos"));
        }

        // Se, mesmo após tudo, a lista está vazia (caso raro < 1s), retorna "agora"
        if (parts.isEmpty()) {
            return "agora";
        }

        // Junta as partes com vírgula e espaço
        return String.join(", ", parts);
    }

    /**
     * Converte uma string de duração (ex: "30d", "1 h", "5m") em milissegundos.
     * @param durationStr A string da duração. Suporta s, m, h, d, y (ano aproximado). Case-insensitive.
     * @return A duração em milissegundos, ou -1 se o formato for inválido ou causar overflow.
     */
    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) {
            return -1; // String vazia ou nula
        }

        // Remove espaços extras e converte para minúsculas
        String input = durationStr.toLowerCase().trim();

        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return -1; // Formato não corresponde a "numero[espaço]Letra"
        }

        try {
            long value = Long.parseLong(matcher.group(1));
            // Previne valor negativo que passaria na regex mas não faz sentido aqui
            if (value < 0) return -1;

            String unit = matcher.group(2);

            long multiplier;
            long overflowCheck = Long.MAX_VALUE; // Limite para o valor ANTES da multiplicação

            switch (unit) {
                case "s":
                    multiplier = TimeUnit.SECONDS.toMillis(1); // 1000L
                    overflowCheck /= multiplier;
                    break;
                case "m":
                    multiplier = TimeUnit.MINUTES.toMillis(1); // 60 * 1000L
                    overflowCheck /= multiplier;
                    break;
                case "h":
                    multiplier = TimeUnit.HOURS.toMillis(1); // 60 * 60 * 1000L
                    overflowCheck /= multiplier;
                    break;
                case "d":
                    multiplier = TimeUnit.DAYS.toMillis(1); // 24 * 60 * 60 * 1000L
                    overflowCheck /= multiplier;
                    break;
                case "y":
                    // Ano aproximado (365 dias)
                    multiplier = TimeUnit.DAYS.toMillis(365);
                    overflowCheck /= multiplier;
                    break;
                default:
                    return -1; // Unidade inválida (não deve acontecer)
            }

            // Verifica overflow ANTES de multiplicar
            if (value > overflowCheck) {
                return -1; // Valor causa overflow
            }

            return value * multiplier;

        } catch (NumberFormatException e) {
            // O número era inválido ou muito grande para caber em um long ANTES da unidade
            return -1;
        } catch (ArithmeticException e) {
            // Overflow durante a multiplicação (embora as verificações devam prevenir)
            return -1;
        }
    }
}