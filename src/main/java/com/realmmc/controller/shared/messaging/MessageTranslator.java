package com.realmmc.controller.shared.messaging;

import java.util.Locale;

public interface MessageTranslator {
    
    /**
     * Traduz uma mensagem usando o locale padrão.
     * 
     * @param message A mensagem a ser traduzida
     * @return A mensagem traduzida e formatada
     */
    String translate(Message message);
    
    /**
     * Traduz uma mensagem usando um locale específico.
     * 
     * @param message A mensagem a ser traduzida
     * @param locale O locale a ser usado
     * @return A mensagem traduzida e formatada
     */
    String translate(Message message, Locale locale);
    
    /**
     * Traduz uma mensagem simples sem placeholders.
     * 
     * @param key A chave da mensagem
     * @return A mensagem traduzida
     */
    String translate(MessageKey key);
    
    /**
     * Traduz uma mensagem simples com um locale específico.
     * 
     * @param key A chave da mensagem
     * @param locale O locale a ser usado
     * @return A mensagem traduzida
     */
    String translate(MessageKey key, Locale locale);

    void reload();
    
    /**
     * Obtém o locale padrão usado pelo translator.
     * 
     * @return O locale padrão
     */
    Locale getDefaultLocale();
    
    /**
     * Define o locale padrão do translator.
     * 
     * @param locale O novo locale padrão
     */
    void setDefaultLocale(Locale locale);
    
    /**
     * Verifica se uma chave de mensagem existe.
     * 
     * @param key A chave a ser verificada
     * @return true se a chave existe, false caso contrário
     */
    boolean hasMessage(MessageKey key);
    
    /**
     * Verifica se uma chave de mensagem existe para um locale específico.
     * 
     * @param key A chave a ser verificada
     * @param locale O locale a ser verificado
     * @return true se a chave existe para o locale, false caso contrário
     */
    boolean hasMessage(MessageKey key, Locale locale);
}