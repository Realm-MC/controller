package com.realmmc.controller.shared.messaging;


public interface PlatformMessenger {

    boolean isPlayerInstance(Object any);

    void send(Object player, String message);

    void sendMany(Iterable<Object> players, String message);

    void broadcast(String message);

    default void sendSuccess(Object player, String message) {
        send(player, "<green>" + message + "</green>");
    }

    default void sendError(Object player, String message) {
        send(player, "<red>" + message + "</red>");
    }

    default void sendWarning(Object player, String message) {
        send(player, "<yellow>" + message + "</yellow>");
    }

    default void sendInfo(Object player, String message) {
        send(player, "<blue>" + message + "</blue>");
    }
}