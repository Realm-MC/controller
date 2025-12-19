package com.palacesky.controller.shared.messaging;

import com.palacesky.controller.shared.messaging.impl.FileBasedMessageTranslator;
import com.palacesky.controller.shared.messaging.impl.SpigotMessageSender;
import com.palacesky.controller.shared.messaging.impl.VelocityMessageSender;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;

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

    public boolean isInitialized() {
        return initialized;
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

    public MessageTranslator getTranslator() {
        ensureInitialized();
        return translator;
    }

    public MessageSender getSender() {
        ensureInitialized();
        return sender;
    }


    public void sendMessage(Object recipient, Message message) {
        getSender().sendMessage(recipient, message);
    }

    public void sendMessage(Object recipient, MessageKey key) {
        getSender().sendMessage(recipient, key);
    }

    public void sendRawMessage(Object recipient, String text) {
        getSender().sendRawMessage(recipient, text);
    }

    public void sendRawMessage(Object recipient, RawMessage rawMessage) {
        getSender().sendRawMessage(recipient, processRaw(rawMessage));
    }


    public void sendActionBar(Object recipient, Message message) {
        getSender().sendActionBar(recipient, message);
    }

    public void sendActionBar(Object recipient, MessageKey key) {
        getSender().sendActionBar(recipient, Message.of(key));
    }

    public void sendActionBar(Object recipient, RawMessage message) {
        getSender().sendActionBar(recipient, processRaw(message));
    }

    public void sendTitle(Object recipient, Message title, Message subtitle, Title.Times times) {
        getSender().sendTitle(recipient, title, subtitle, times);
    }

    public void sendTitle(Object recipient, RawMessage title, RawMessage subtitle, Title.Times times) {
        String titleText = processRaw(title);
        String subtitleText = processRaw(subtitle);
        getSender().sendTitle(recipient, titleText, subtitleText, times);
    }

    public void showBossBar(Object recipient, BossBar bar) {
        getSender().showBossBar(recipient, bar);
    }

    public void hideBossBar(Object recipient, BossBar bar) {
        getSender().hideBossBar(recipient, bar);
    }


    public String translate(Message message) {
        return getTranslator().translate(message);
    }

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

    private String processRaw(RawMessage raw) {
        if (raw == null) return null;
        String processedText = raw.getText();

        if (raw.hasPlaceholders()) {
            for (var entry : raw.getPlaceholders().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = String.valueOf(entry.getValue());
                processedText = processedText.replace(placeholder, value);
            }
        }
        return processedText;
    }
}