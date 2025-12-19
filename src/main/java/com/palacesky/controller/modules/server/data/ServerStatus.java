package com.palacesky.controller.modules.server.data;

/**
 * Define o estado operacional de um servidor dinâmico.
 */
public enum ServerStatus {
    /** O servidor está online, registado no Velocity e a aceitar jogadores. */
    ONLINE,
    /** O servidor está desligado no Pterodactyl e não está no Velocity. */
    OFFLINE,
    /** O Controller enviou um comando para ligar, a aguardar que o servidor arranque. */
    STARTING,
    /** O Controller enviou um comando para desligar. */
    STOPPING
}