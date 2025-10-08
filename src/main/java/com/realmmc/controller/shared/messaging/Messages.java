package com.realmmc.controller.shared.messaging;

public class Messages {

    private static volatile PlatformMessenger messenger = null;
    private static final MessagingSDK SDK = MessagingSDK.getInstance();

    public static void register(PlatformMessenger platformMessenger) {
        messenger = platformMessenger;
    }

    public static boolean isRegistered() {
        return messenger != null;
    }

    /**
     * Envia uma mensagem de texto puro para um destinatário.
     * Usa PlatformMessenger se disponível, senão fallback para SDK
     *
     * @param recipient O destinatário (Player, CommandSender, CommandSource)
     * @param text      O texto da mensagem (suporta MiniMessage)
     */
    public static void send(Object recipient, String text) {
        PlatformMessenger m = messenger;
        if (m != null && m.isPlayerInstance(recipient)) {
            m.send(recipient, text);
        } else {
            SDK.sendRawMessage(recipient, text);
        }
    }

    /**
     * Envia uma mensagem configurável para um destinatário.
     *
     * @param recipient O destinatário
     * @param key       A chave da mensagem
     */
    public static void send(Object recipient, MessageKey key) {
        SDK.sendMessage(recipient, key);
    }

    /**
     * Envia uma mensagem com placeholders para um destinatário.
     *
     * @param recipient O destinatário
     * @param message   A mensagem com placeholders
     */
    public static void send(Object recipient, Message message) {
        SDK.sendMessage(recipient, message);
    }

    /**
     * Envia uma mensagem de texto direto para um destinatário.
     *
     * @param recipient  O destinatário
     * @param rawMessage A mensagem de texto direto
     */
    public static void send(Object recipient, RawMessage rawMessage) {
        SDK.sendRawMessage(recipient, rawMessage);
    }

    /**
     * Envia uma mensagem de sucesso (verde) para um destinatário.
     *
     * @param recipient O destinatário
     * @param text      O texto da mensagem
     */
    public static void success(Object recipient, String text) {
        send(recipient, "<green>" + text + "</green>");
    }

    /**
     * Envia uma mensagem de erro (vermelha) para um destinatário.
     *
     * @param recipient O destinatário
     * @param text      O texto da mensagem
     */
    public static void error(Object recipient, String text) {
        send(recipient, "<red>" + text + "</red>");
    }

    /**
     * Envia uma mensagem de aviso (amarela) para um destinatário.
     *
     * @param recipient O destinatário
     * @param text      O texto da mensagem
     */
    public static void warning(Object recipient, String text) {
        send(recipient, "<yellow>" + text + "</yellow>");
    }

    /**
     * Envia uma mensagem de informação (azul) para um destinatário.
     *
     * @param recipient O destinatário
     * @param text      O texto da mensagem
     */
    public static void info(Object recipient, String text) {
        send(recipient, "<blue>" + text + "</blue>");
    }

    /**
     * Traduz uma mensagem sem enviá-la.
     *
     * @param key A chave da mensagem
     * @return A mensagem traduzida
     */
    public static String translate(MessageKey key) {
        return SDK.translate(key);
    }

    /**
     * Traduz uma mensagem com placeholders sem enviá-la.
     *
     * @param message A mensagem com placeholders
     * @return A mensagem traduzida
     */
    public static String translate(Message message) {
        return SDK.translate(message);
    }

    /**
     * Envia uma mensagem para múltiplos destinatários.
     *
     * @param recipients Os destinatários
     * @param text       O texto da mensagem
     */
    public static void broadcast(Iterable<?> recipients, String text) {
        for (Object recipient : recipients) {
            send(recipient, text);
        }
    }

    /**
     * Envia uma mensagem configurável para múltiplos destinatários.
     *
     * @param recipients Os destinatários
     * @param key        A chave da mensagem
     */
    public static void broadcast(Iterable<?> recipients, MessageKey key) {
        SDK.getSender().broadcastMessage(recipients, key);
    }

    /**
     * Envia uma mensagem com placeholders para múltiplos destinatários.
     *
     * @param recipients Os destinatários
     * @param message    A mensagem com placeholders
     */
    public static void broadcast(Iterable<?> recipients, Message message) {
        SDK.getSender().broadcastMessage(recipients, message);
    }
}