package com.palacesky.controller.shared.messaging;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Message {

    @Getter
    private final MessageKey key;
    private final Map<String, Object> placeholders;

    private Message(MessageKey key) {
        this.key = Objects.requireNonNull(key, "MessageKey cannot be null");
        this.placeholders = new HashMap<>();
    }

    /**
     * Cria uma nova mensagem com a chave especificada.
     * @param key A chave da mensagem
     * @return Nova instância de Message
     */
    public static Message of(MessageKey key) {
        return new Message(key);
    }

    /**
     * Cria uma nova mensagem com texto direto (sem tradução).
     * Útil para mensagens dinâmicas ou temporárias.
     * @param rawText Texto direto da mensagem
     * @return Nova instância de RawMessage
     */
    public static RawMessage raw(String rawText) {
        return new RawMessage(rawText);
    }

    /**
     * Adiciona um placeholder à mensagem.
     * @param placeholder Nome do placeholder (sem os símbolos {})
     * @param value       Valor a ser substituído
     * @return Esta instância para method chaining
     */
    public Message with(String placeholder, Object value) {
        this.placeholders.put(placeholder, value);
        return this;
    }

    /**
     * Adiciona múltiplos placeholders à mensagem.
     * @param placeholders Map com os placeholders e seus valores
     * @return Esta instância para method chaining
     */
    public Message with(Map<String, Object> placeholders) {
        this.placeholders.putAll(placeholders);
        return this;
    }

    /**
     * Obtém os placeholders da mensagem.
     * @return Map com os placeholders
     */
    public Map<String, Object> getPlaceholders() {
        return new HashMap<>(placeholders);
    }

    /**
     * Verifica se a mensagem tem placeholders.
     * @return true se tem placeholders, false caso contrário
     */
    public boolean hasPlaceholders() {
        return !placeholders.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(key, message.key) && Objects.equals(placeholders, message.placeholders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, placeholders);
    }

    @Override
    public String toString() {
        return "Message{" +
                "key=" + key +
                ", placeholders=" + placeholders +
                '}';
    }
}