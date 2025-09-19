package com.realmmc.controller.shared.messaging;

import com.realmmc.controller.shared.messaging.impl.FileBasedMessageTranslator;
import com.realmmc.controller.shared.messaging.impl.SpigotMessageSender;
import com.realmmc.controller.shared.messaging.impl.VelocityMessageSender;

import java.io.File;
import java.util.logging.Logger;

public class MessagingSDK {
    
    private static final Logger LOGGER = Logger.getLogger(MessagingSDK.class.getName());
    
    private static volatile MessagingSDK instance;
    private MessageTranslator translator;
    private MessageSender sender;
    private boolean initialized = false;
    
    private MessagingSDK() {
    }

    public static MessagingSDK getInstance() {
        if (instance == null) {
            synchronized (MessagingSDK.class) {
                if (instance == null) {
                    instance = new MessagingSDK();
                }
            }
        }
        return instance;
    }

    public void initializeForSpigot(File messagesDirectory) {
        if (initialized) {
            LOGGER.warning("MessagingSDK already initialized!");
            return;
        }
        
        this.translator = new FileBasedMessageTranslator(messagesDirectory);
        this.sender = new SpigotMessageSender(translator);
        this.initialized = true;
        
        LOGGER.info("MessagingSDK initialized for Spigot platform");
    }

    public void initializeForVelocity(File messagesDirectory) {
        if (initialized) {
            LOGGER.warning("MessagingSDK already initialized!");
            return;
        }
        
        this.translator = new FileBasedMessageTranslator(messagesDirectory);
        this.sender = new VelocityMessageSender(translator);
        this.initialized = true;
        
        LOGGER.info("MessagingSDK initialized for Velocity platform");
    }
    
    /**
     * Inicializa o SDK com implementações customizadas.
     * 
     * @param translator O translator a ser usado
     * @param sender O sender a ser usado
     */
    public void initialize(MessageTranslator translator, MessageSender sender) {
        if (initialized) {
            LOGGER.warning("MessagingSDK already initialized!");
            return;
        }
        
        this.translator = translator;
        this.sender = sender;
        this.initialized = true;
        
        LOGGER.info("MessagingSDK initialized with custom implementations");
    }

    /**
     * Obtém o translator atual.
     * 
     * @return O MessageTranslator
     * @throws IllegalStateException se o SDK não foi inicializado
     */
    public MessageTranslator getTranslator() {
        ensureInitialized();
        return translator;
    }
    
    /**
     * Obtém o sender atual.
     * 
     * @return O MessageSender
     * @throws IllegalStateException se o SDK não foi inicializado
     */
    public MessageSender getSender() {
        ensureInitialized();
        return sender;
    }
    
    /**
     * Envia uma mensagem para um destinatário.
     * 
     * @param recipient O destinatário
     * @param message A mensagem
     */
    public void sendMessage(Object recipient, Message message) {
        getSender().sendMessage(recipient, message);
    }
    
    /**
     * Envia uma mensagem simples para um destinatário.
     * 
     * @param recipient O destinatário
     * @param key A chave da mensagem
     */
    public void sendMessage(Object recipient, MessageKey key) {
        getSender().sendMessage(recipient, key);
    }
    
    /**
     * Envia uma mensagem de texto puro para um destinatário.
     * 
     * @param recipient O destinatário
     * @param text O texto
     */
    public void sendRawMessage(Object recipient, String text) {
        getSender().sendRawMessage(recipient, text);
    }
    
    /**
     * Envia uma mensagem de texto direto para um destinatário.
     * 
     * @param recipient O destinatário
     * @param rawMessage A mensagem de texto direto
     */
    public void sendRawMessage(Object recipient, RawMessage rawMessage) {
        String processedText = rawMessage.getText();

        if (rawMessage.hasPlaceholders()) {
            for (var entry : rawMessage.getPlaceholders().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = String.valueOf(entry.getValue());
                processedText = processedText.replace(placeholder, value);
            }
        }
        
        getSender().sendRawMessage(recipient, processedText);
    }
    
    /**
     * Traduz uma mensagem.
     * 
     * @param message A mensagem
     * @return A mensagem traduzida
     */
    public String translate(Message message) {
        return getTranslator().translate(message);
    }
    
    /**
     * Traduz uma mensagem simples.
     * 
     * @param key A chave da mensagem
     * @return A mensagem traduzida
     */
    public String translate(MessageKey key) {
        return getTranslator().translate(key);
    }

    public void reload() {
        ensureInitialized();
        translator.reload();
        LOGGER.info("MessagingSDK reloaded");
    }

    public void shutdown() {
        if (initialized) {
            this.translator = null;
            this.sender = null;
            this.initialized = false;
            LOGGER.info("MessagingSDK shutdown");
        }
    }
    
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("MessagingSDK not initialized! Call initialize() first.");
        }
    }
}