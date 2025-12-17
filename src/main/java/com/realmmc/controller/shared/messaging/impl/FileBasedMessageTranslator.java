package com.realmmc.controller.shared.messaging.impl;

import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.MessageTranslator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FileBasedMessageTranslator implements MessageTranslator {

    private static final Logger LOGGER = Logger.getLogger(FileBasedMessageTranslator.class.getName());
    private static final String DEFAULT_MESSAGE_FORMAT = "<red>Message not found: {key}</red>";

    private final File messagesDirectory;
    private final Map<Locale, Properties> messageCache;
    private Locale defaultLocale;

    public FileBasedMessageTranslator(File messagesDirectory) {
        this.messagesDirectory = messagesDirectory;
        this.messageCache = new ConcurrentHashMap<>();
        this.defaultLocale = new Locale("pt", "BR");

        if (!messagesDirectory.exists()) {
            messagesDirectory.mkdirs();
        }

        reload();
    }

    @Override
    public String translate(Message message) {
        return translate(message, defaultLocale);
    }

    @Override
    public String translate(Message message, Locale locale) {
        String template = getRawMessage(message.getKey(), locale);

        if (message.hasPlaceholders()) {
            for (Map.Entry<String, Object> entry : message.getPlaceholders().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = String.valueOf(entry.getValue());
                template = template.replace(placeholder, value);
            }
        }

        return template;
    }

    @Override
    public String translate(MessageKey key) {
        return translate(key, defaultLocale);
    }

    @Override
    public String translate(MessageKey key, Locale locale) {
        return getRawMessage(key, locale);
    }

    @Override
    public void reload() {
        messageCache.clear();

        if (!messagesDirectory.exists() || !messagesDirectory.isDirectory()) {
            LOGGER.warning("[Translator] Messages directory does not exist: " + messagesDirectory.getAbsolutePath());
            return;
        }

        File[] files = messagesDirectory.listFiles((dir, name) -> name.endsWith(".properties"));
        if (files == null) {
            LOGGER.warning("[Translator] No message files found in: " + messagesDirectory.getAbsolutePath());
            return;
        }

        for (File file : files) {
            loadMessageFile(file);
        }

        if (!messageCache.containsKey(this.defaultLocale)) {
            LOGGER.warning("[Translator] Default locale " + this.defaultLocale + " not found, trying fallback to 'pt_BR' or 'en'...");
            if (messageCache.containsKey(new Locale("pt", "BR"))) {
                this.defaultLocale = new Locale("pt", "BR");
            } else if (messageCache.containsKey(Locale.ENGLISH)) {
                this.defaultLocale = Locale.ENGLISH;
                LOGGER.warning("[Translator] Setting 'en' as default fallback.");
            } else {
                LOGGER.severe("[Translator] No translation file (pt_BR or en) found!");
            }
        }

        LOGGER.info("[Translator] Loaded messages for " + messageCache.size() + " locales. Default locale set to: " + this.defaultLocale);
    }

    @Override
    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    @Override
    public void setDefaultLocale(Locale locale) {
        this.defaultLocale = locale;
    }

    @Override
    public boolean hasMessage(MessageKey key) {
        return hasMessage(key, defaultLocale);
    }

    @Override
    public boolean hasMessage(MessageKey key, Locale locale) {
        if (locale == null) locale = defaultLocale;
        Properties messages = messageCache.get(locale);
        return messages != null && messages.containsKey(key.getKey());
    }

    private String getRawMessage(MessageKey key, Locale locale) {
        if (key == null) return "<red>Error: null key</red>";

        if (locale == null) locale = defaultLocale;

        Properties messages = messageCache.get(locale);

        if (messages != null && messages.containsKey(key.getKey())) {
            return messages.getProperty(key.getKey());
        }

        if (!locale.equals(defaultLocale)) {
            messages = messageCache.get(defaultLocale);
            if (messages != null && messages.containsKey(key.getKey())) {
                return messages.getProperty(key.getKey());
            }
        }

        if (!defaultLocale.equals(Locale.ENGLISH)) {
            messages = messageCache.get(Locale.ENGLISH);
            if (messages != null && messages.containsKey(key.getKey())) {
                return messages.getProperty(key.getKey());
            }
        }

        return DEFAULT_MESSAGE_FORMAT.replace("{key}", key.getKey());
    }

    private void loadMessageFile(File file) {
        String fileName = file.getName();
        String localeString = fileName.substring(0, fileName.lastIndexOf('.'));

        Locale locale;
        if (localeString.contains("_")) {
            String[] parts = localeString.split("_");
            locale = new Locale(parts[0], parts[1]);
        } else {
            locale = new Locale(localeString);
        }

        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {

            properties.load(reader);
            messageCache.put(locale, properties);
            LOGGER.info("[Translator] Loaded " + properties.size() + " messages for locale: " + locale);
        } catch (IOException e) {
            LOGGER.severe("[Translator] Failed to load message file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }
}