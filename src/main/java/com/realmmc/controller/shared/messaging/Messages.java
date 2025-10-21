package com.realmmc.controller.shared.messaging;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.geoip.GeoIPService;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.Player;


public class Messages {

    private static final MessagingSDK SDK = MessagingSDK.getInstance();
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final Locale EN_US = Locale.US;

    /**
     * Determina o Locale apropriado para um destinatário.
     * Prioridade: IP Brasileiro -> pt_BR
     * Não-BR + Cliente PT -> pt_BR
     * Outros -> en_US
     * @param recipient O destinatário (Player, CommandSource, CommandSender, etc.)
     * @return O Locale determinado.
     */
    private static Locale determineLocale(Object recipient) {
        Optional<GeoIPService> geoIPOpt = ServiceRegistry.getInstance().getService(GeoIPService.class);
        InetAddress ipAddress = null;
        Locale clientLocale = null;
        boolean isPlayer = false;

        try {
            if (recipient instanceof Player) {
                isPlayer = true;
                Player spigotPlayer = (Player) recipient;
                InetSocketAddress address = spigotPlayer.getAddress();
                if (address != null) {
                    ipAddress = address.getAddress();
                }
                String localeString = spigotPlayer.getLocale();
                if (localeString != null && !localeString.isEmpty()) {
                    String[] parts = localeString.split("[_\\-]");
                    if (parts.length >= 2) {
                        clientLocale = new Locale(parts[0], parts[1]);
                    } else if (parts.length == 1) {
                        clientLocale = new Locale(parts[0]);
                    }
                }
            }
        } catch (NoClassDefFoundError | ClassCastException ignored) {
        }

        try {
            if (!isPlayer && recipient instanceof com.velocitypowered.api.proxy.Player) {
                isPlayer = true;
                com.velocitypowered.api.proxy.Player velocityPlayer = (com.velocitypowered.api.proxy.Player) recipient;
                InetSocketAddress address = velocityPlayer.getRemoteAddress();
                if (address != null) {
                    ipAddress = address.getAddress();
                }
                clientLocale = velocityPlayer.getPlayerSettings().getLocale();
            }
        } catch (NoClassDefFoundError | ClassCastException ignored) {
        }


        if (isPlayer) {
            if (geoIPOpt.isPresent() && ipAddress != null) {
                Optional<String> countryCode = geoIPOpt.get().getCountryCode(ipAddress);
                if (countryCode.isPresent() && "BR".equalsIgnoreCase(countryCode.get())) {
                    return PT_BR;
                }
            }

            if (clientLocale != null && "pt".equalsIgnoreCase(clientLocale.getLanguage())) {
                return PT_BR;
            }

            return EN_US;
        }

        return EN_US;
    }

    /**
     * Envia uma mensagem de texto puro para um destinatário.
     * @param recipient O destinatário
     * @param text      O texto da mensagem (suporta MiniMessage)
     */
    public static void send(Object recipient, String text) {
        SDK.sendRawMessage(recipient, text);
    }

    /**
     * Envia uma mensagem configurável para um destinatário, usando o Locale apropriado.
     * @param recipient O destinatário
     * @param key       A chave da mensagem
     */
    public static void send(Object recipient, MessageKey key) {
        send(recipient, Message.of(key));
    }

    /**
     * Envia uma mensagem com placeholders para um destinatário, usando o Locale apropriado.
     * @param recipient O destinatário
     * @param message   A mensagem com placeholders (tipo Message)
     */
    public static void send(Object recipient, Message message) {
        Locale targetLocale = determineLocale(recipient);
        String translatedText = SDK.getTranslator().translate(message, targetLocale);
        SDK.sendRawMessage(recipient, translatedText);
    }

    /**
     * Envia uma mensagem de texto direto (RawMessage) com placeholders para um destinatário.
     * @param recipient  O destinatário
     * @param rawMessage A mensagem de texto direto
     */
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


    /**
     * Traduz uma mensagem simples usando o Locale padrão (Inglês).
     * @param key A chave da mensagem
     * @return A mensagem traduzida
     */
    public static String translate(MessageKey key) {
        return SDK.getTranslator().translate(key, EN_US);
    }

    /**
     * Traduz uma mensagem com placeholders usando o Locale padrão (Inglês).
     * @param message A mensagem com placeholders (tipo Message)
     * @return A mensagem traduzida
     */
    public static String translate(Message message) {
        return SDK.getTranslator().translate(message, EN_US);
    }

    /**
     * Traduz uma mensagem simples para um Locale específico.
     * @param key A chave da mensagem
     * @param locale O Locale desejado
     * @return A mensagem traduzida
     */
    public static String translate(MessageKey key, Locale locale) {
        return SDK.getTranslator().translate(key, locale);
    }

    /**
     * Traduz uma mensagem com placeholders para um Locale específico.
     * @param message A mensagem com placeholders (tipo Message)
     * @param locale O Locale desejado
     * @return A mensagem traduzida
     */
    public static String translate(Message message, Locale locale) {
        return SDK.getTranslator().translate(message, locale);
    }

    /**
     * Envia uma mensagem de texto puro para múltiplos destinatários.
     * @param recipients Os destinatários
     * @param text       O texto da mensagem
     */
    public static void broadcast(Iterable<?> recipients, String text) {
        for (Object recipient : recipients) {
            send(recipient, text);
        }
    }

    /**
     * Envia uma mensagem configurável para múltiplos destinatários, traduzindo para o Locale de cada um.
     * @param recipients Os destinatários
     * @param key        A chave da mensagem
     */
    public static void broadcast(Iterable<?> recipients, MessageKey key) {
        broadcast(recipients, Message.of(key));
    }

    /**
     * Envia uma mensagem com placeholders para múltiplos destinatários, traduzindo para o Locale de cada um.
     * @param recipients Os destinatários
     * @param message    A mensagem com placeholders (tipo Message)
     */
    public static void broadcast(Iterable<?> recipients, Message message) {
        for (Object recipient : recipients) {
            send(recipient, message);
        }
    }
}