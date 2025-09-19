package com.realmmc.controller.shared.messaging;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RawMessage {
    @Getter
    private final String text;
    private final Map<String, Object> placeholders;
    
    public RawMessage(String text) {
        this.text = Objects.requireNonNull(text, "Text cannot be null");
        this.placeholders = new HashMap<>();
    }
    
    /**
     * Adiciona um placeholder à mensagem.
     * 
     * @param placeholder Nome do placeholder (sem os símbolos {})
     * @param value Valor a ser substituído
     * @return Esta instância para method chaining
     */
    public RawMessage placeholder(String placeholder, Object value) {
        this.placeholders.put(placeholder, value);
        return this;
    }
    
    /**
     * Adiciona múltiplos placeholders à mensagem.
     * 
     * @param placeholders Map com os placeholders e seus valores
     * @return Esta instância para method chaining
     */
    public RawMessage placeholders(Map<String, Object> placeholders) {
        this.placeholders.putAll(placeholders);
        return this;
    }

    /**
     * Obtém os placeholders da mensagem.
     * 
     * @return Map com os placeholders
     */
    public Map<String, Object> getPlaceholders() {
        return new HashMap<>(placeholders);
    }
    
    /**
     * Verifica se a mensagem tem placeholders.
     * 
     * @return true se tem placeholders, false caso contrário
     */
    public boolean hasPlaceholders() {
        return !placeholders.isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RawMessage that = (RawMessage) o;
        return Objects.equals(text, that.text) && Objects.equals(placeholders, that.placeholders);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(text, placeholders);
    }
    
    @Override
    public String toString() {
        return "RawMessage{" +
                "text='" + text + '\'' +
                ", placeholders=" + placeholders +
                '}';
    }
}