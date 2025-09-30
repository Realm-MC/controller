package com.realmmc.controller.velocity.messaging;

import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.MessagingSDK;
import com.realmmc.controller.shared.messaging.RawMessage;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

public class PlayerExtensions {

    private static final MessagingSDK SDK = MessagingSDK.getInstance();

    /**
     * Envia uma mensagem de texto puro para o player.
     *
     * @param player O player destinatário
     * @param text   O texto da mensagem (suporta MiniMessage)
     */
    public static void msg(Player player, String text) {
        SDK.sendRawMessage(player, text);
    }

    /**
     * Envia uma mensagem de texto puro para o CommandSource.
     *
     * @param source O source destinatário
     * @param text   O texto da mensagem (suporta MiniMessage)
     */
    public static void msg(CommandSource source, String text) {
        SDK.sendRawMessage(source, text);
    }

    /**
     * Envia uma mensagem configurável para o player.
     *
     * @param player O player destinatário
     * @param key    A chave da mensagem
     */
    public static void msg(Player player, MessageKey key) {
        SDK.sendMessage(player, key);
    }

    /**
     * Envia uma mensagem configurável para o CommandSource.
     *
     * @param source O source destinatário
     * @param key    A chave da mensagem
     */
    public static void msg(CommandSource source, MessageKey key) {
        SDK.sendMessage(source, key);
    }

    /**
     * Envia uma mensagem com placeholders para o player.
     *
     * @param player  O player destinatário
     * @param message A mensagem com placeholders
     */
    public static void msg(Player player, Message message) {
        SDK.sendMessage(player, message);
    }

    /**
     * Envia uma mensagem com placeholders para o CommandSource.
     *
     * @param source  O source destinatário
     * @param message A mensagem com placeholders
     */
    public static void msg(CommandSource source, Message message) {
        SDK.sendMessage(source, message);
    }

    /**
     * Envia uma mensagem de texto direto para o player.
     *
     * @param player     O player destinatário
     * @param rawMessage A mensagem de texto direto
     */
    public static void msg(Player player, RawMessage rawMessage) {
        SDK.sendRawMessage(player, rawMessage);
    }

    /**
     * Envia uma mensagem de texto direto para o CommandSource.
     *
     * @param source     O source destinatário
     * @param rawMessage A mensagem de texto direto
     */
    public static void msg(CommandSource source, RawMessage rawMessage) {
        SDK.sendRawMessage(source, rawMessage);
    }

    /**
     * Envia uma mensagem de sucesso (verde) para o player.
     *
     * @param player O player destinatário
     * @param text   O texto da mensagem
     */
    public static void success(Player player, String text) {
        msg(player, "<green>" + text + "</green>");
    }

    /**
     * Envia uma mensagem de sucesso (verde) para o CommandSource.
     *
     * @param source O source destinatário
     * @param text   O texto da mensagem
     */
    public static void success(CommandSource source, String text) {
        msg(source, "<green>" + text + "</green>");
    }

    /**
     * Envia uma mensagem de erro (vermelha) para o player.
     *
     * @param player O player destinatário
     * @param text   O texto da mensagem
     */
    public static void error(Player player, String text) {
        msg(player, "<red>" + text + "</red>");
    }

    /**
     * Envia uma mensagem de erro (vermelha) para o CommandSource.
     *
     * @param source O source destinatário
     * @param text   O texto da mensagem
     */
    public static void error(CommandSource source, String text) {
        msg(source, "<red>" + text + "</red>");
    }

    /**
     * Envia uma mensagem de aviso (amarela) para o player.
     *
     * @param player O player destinatário
     * @param text   O texto da mensagem
     */
    public static void warning(Player player, String text) {
        msg(player, "<yellow>" + text + "</yellow>");
    }

    /**
     * Envia uma mensagem de aviso (amarela) para o CommandSource.
     *
     * @param source O source destinatário
     * @param text   O texto da mensagem
     */
    public static void warning(CommandSource source, String text) {
        msg(source, "<yellow>" + text + "</yellow>");
    }

    /**
     * Envia uma mensagem de informação (azul) para o player.
     *
     * @param player O player destinatário
     * @param text   O texto da mensagem
     */
    public static void info(Player player, String text) {
        msg(player, "<blue>" + text + "</blue>");
    }

    /**
     * Envia uma mensagem de informação (azul) para o CommandSource.
     *
     * @param source O source destinatário
     * @param text   O texto da mensagem
     */
    public static void info(CommandSource source, String text) {
        msg(source, "<blue>" + text + "</blue>");
    }
}