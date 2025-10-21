package com.realmmc.controller.shared.sounds;

/**
 * Interface para tocar sons registrados, abstraindo a plataforma (Spigot/Velocity).
 */
public interface SoundPlayer {

    /**
     * Toca um som registrado (pela sua chave lógica) para um jogador específico.
     * O som é geralmente tocado na localização do jogador.
     *
     * @param playerObject O objeto do jogador (org.bukkit.entity.Player ou com.velocitypowered.api.proxy.Player).
     * @param soundKey     A chave lógica do som registrada no SoundRegistry (ex: "NOTIFICATION", "SUCCESS").
     */
    void playSound(Object playerObject, String soundKey);

    /**
     * Toca um som específico (definido por SoundInfo) para um jogador específico.
     *
     * @param playerObject O objeto do jogador.
     * @param soundInfo    As informações do som a ser tocado.
     */
    void playSound(Object playerObject, SoundInfo soundInfo);

}