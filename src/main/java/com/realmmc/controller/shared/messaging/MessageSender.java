package com.realmmc.controller.shared.messaging;

public interface MessageSender {

    /**
     * Envia uma mensagem para um destinatário.
     *
     * @param recipient O destinatário da mensagem (Player, CommandSender, etc.)
     * @param message   A mensagem a ser enviada
     */
    void sendMessage(Object recipient, Message message);

    /**
     * Envia uma mensagem simples para um destinatário.
     *
     * @param recipient O destinatário da mensagem
     * @param key       A chave da mensagem
     */
    void sendMessage(Object recipient, MessageKey key);

    /**
     * Envia uma mensagem de texto puro para um destinatário.
     * Esta mensagem não passará pelo sistema de tradução.
     *
     * @param recipient O destinatário da mensagem
     * @param text      O texto a ser enviado
     */
    void sendRawMessage(Object recipient, String text);

    /**
     * Envia uma mensagem para múltiplos destinatários.
     *
     * @param recipients Os destinatários da mensagem
     * @param message    A mensagem a ser enviada
     */
    void broadcastMessage(Iterable<?> recipients, Message message);

    /**
     * Envia uma mensagem simples para múltiplos destinatários.
     *
     * @param recipients Os destinatários da mensagem
     * @param key        A chave da mensagem
     */
    void broadcastMessage(Iterable<?> recipients, MessageKey key);

    /**
     * Verifica se um objeto é um destinatário válido para esta plataforma.
     *
     * @param recipient O objeto a ser verificado
     * @return true se é um destinatário válido, false caso contrário
     */
    boolean isValidRecipient(Object recipient);

    /**
     * Obtém o translator usado por este sender.
     *
     * @return O MessageTranslator
     */
    MessageTranslator getTranslator();
}