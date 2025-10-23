package com.realmmc.controller.shared.messaging;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.geoip.GeoIPService;
import com.realmmc.controller.shared.preferences.Language;
import com.realmmc.controller.shared.preferences.PreferencesService;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;


public class Messages {

    private static final MessagingSDK SDK = MessagingSDK.getInstance();
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final Locale EN_US = Locale.US;

    public static Language determineInitialLanguage(Object recipient) {
        Optional<GeoIPService> geoIPOpt = ServiceRegistry.getInstance().getService(GeoIPService.class);
        InetAddress ipAddress = null;
        Locale clientLocale = null;
        boolean isPlayer = false;
        UUID playerUuid = null;

        try {
            if (recipient instanceof Player) {
                isPlayer = true;
                Player spigotPlayer = (Player) recipient;
                playerUuid = spigotPlayer.getUniqueId();
                InetSocketAddress address = spigotPlayer.getAddress();
                if (address != null) {
                    ipAddress = address.getAddress();
                }
                String localeString = spigotPlayer.getLocale();
                if (localeString != null && !localeString.isEmpty()) {
                    clientLocale = parseLocaleString(localeString);
                }
            }
        } catch (NoClassDefFoundError | ClassCastException ignored) { }

        try {
            if (!isPlayer && recipient instanceof com.velocitypowered.api.proxy.Player) {
                isPlayer = true;
                com.velocitypowered.api.proxy.Player velocityPlayer = (com.velocitypowered.api.proxy.Player) recipient;
                playerUuid = velocityPlayer.getUniqueId();
                InetSocketAddress address = velocityPlayer.getRemoteAddress();
                if (address != null) {
                    ipAddress = address.getAddress();
                }
                clientLocale = velocityPlayer.getPlayerSettings().getLocale();
            }
        } catch (NoClassDefFoundError | ClassCastException ignored) { }

        if (isPlayer) {
            if (geoIPOpt.isPresent() && ipAddress != null) {
                Optional<String> countryCode = geoIPOpt.get().getCountryCode(ipAddress);
                if (countryCode.isPresent() && "BR".equalsIgnoreCase(countryCode.get())) {
                    return Language.PORTUGUESE;
                }
            }

            if (clientLocale != null && "pt".equalsIgnoreCase(clientLocale.getLanguage())) {
                return Language.PORTUGUESE;
            }
        }

        return Language.ENGLISH;
    }

    private static Locale parseLocaleString(String localeString) {
        if (localeString == null || localeString.isEmpty()) return null;
        String[] parts = localeString.split("[_\\-]");
        if (parts.length >= 2) {
            return new Locale(parts[0], parts[1]);
        } else if (parts.length == 1) {
            return new Locale(parts[0]);
        }
        return null;
    }


    private static Locale determineLocale(Object recipient) {
        UUID playerUuid = null;
        boolean isPlayer = false;

        try {
            if (recipient instanceof Player) {
                playerUuid = ((Player) recipient).getUniqueId();
                isPlayer = true;
            } else if (recipient instanceof com.velocitypowered.api.proxy.Player) {
                playerUuid = ((com.velocitypowered.api.proxy.Player) recipient).getUniqueId();
                isPlayer = true;
            }
        } catch (NoClassDefFoundError | ClassCastException ignored) { }

        if (isPlayer && playerUuid != null) {
            Optional<PreferencesService> prefsOpt = ServiceRegistry.getInstance().getService(PreferencesService.class);
            if (prefsOpt.isPresent()) {
                PreferencesService prefsService = prefsOpt.get();
                Optional<Language> cachedLang = prefsService.getCachedLanguage(playerUuid);
                if (cachedLang.isPresent()) {
                    return cachedLang.get().getLocale();
                }
                Language dbLang = prefsService.loadAndCacheLanguage(playerUuid);
                return dbLang.getLocale();
            }
        }
        return determineInitialLanguage(recipient).getLocale();
    }


    public static void send(Object recipient, String text) {
        SDK.sendRawMessage(recipient, text);
    }

    public static void send(Object recipient, MessageKey key) {
        send(recipient, Message.of(key));
    }

    public static void send(Object recipient, Message message) {
        Locale targetLocale = determineLocale(recipient);
        String translatedText = SDK.getTranslator().translate(message, targetLocale);
        SDK.sendRawMessage(recipient, translatedText);
    }

    public static void send(Object recipient, RawMessage rawMessage) {
        SDK.sendRawMessage(recipient, rawMessage);
    }

    public static void success(Object recipient, String text) {
        send(recipient, "<green>" + text + "</green>");
    }

    public static void error(Object recipient, String text) {
        send(recipient, "<red>" + text + "</red>");
    }

    public static void warning(Object recipient, String text) {
        send(recipient, "<yellow>" + text + "</yellow>");
    }

    public static void info(Object recipient, String text) {
        send(recipient, "<blue>" + text + "</blue>");
    }


    public static String translate(MessageKey key) {
        return SDK.getTranslator().translate(key, EN_US);
    }

    public static String translate(Message message) {
        return SDK.getTranslator().translate(message, EN_US);
    }

    public static String translate(MessageKey key, Locale locale) {
        return SDK.getTranslator().translate(key, locale);
    }

    public static String translate(Message message, Locale locale) {
        return SDK.getTranslator().translate(message, locale);
    }

    public static void broadcast(Iterable<?> recipients, String text) {
        for (Object recipient : recipients) {
            send(recipient, text);
        }
    }

    public static void broadcast(Iterable<?> recipients, MessageKey key) {
        broadcast(recipients, Message.of(key));
    }

    public static void broadcast(Iterable<?> recipients, Message message) {
        for (Object recipient : recipients) {
            send(recipient, message);
        }
    }
}