package com.realmmc.controller.shared.preferences;

import java.util.Locale;

public enum Language {
    PORTUGUESE("pt", "BR", new Locale("pt", "BR")),
    ENGLISH("en", "US", Locale.US);

    private final String code;
    private final String country;
    private final Locale locale;

    Language(String code, String country, Locale locale) {
        this.code = code;
        this.country = country;
        this.locale = locale;
    }

    public Locale getLocale() {
        return locale;
    }

    public static Language fromLocale(Locale locale) {
        if (locale == null) return ENGLISH;
        if ("pt".equalsIgnoreCase(locale.getLanguage())) {
            return PORTUGUESE;
        }
        return ENGLISH;
    }

    public static Language getDefault() {
        return ENGLISH;
    }
}