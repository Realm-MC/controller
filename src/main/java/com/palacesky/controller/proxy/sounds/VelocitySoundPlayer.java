package com.palacesky.controller.proxy.sounds;

import com.palacesky.controller.shared.sounds.SoundInfo;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.sounds.SoundRegistry;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.Optional;
import java.util.logging.Logger;

public class VelocitySoundPlayer implements SoundPlayer {

    private static final Logger LOGGER = Logger.getLogger(VelocitySoundPlayer.class.getName());

    @Override
    public void playSound(Object playerObject, String soundKey) {
        Optional<SoundInfo> soundInfoOpt = SoundRegistry.getSound(soundKey);
        if (soundInfoOpt.isEmpty()) {
            LOGGER.warning("Tentativa de tocar som não registrado: " + soundKey);
            return;
        }
        playSound(playerObject, soundInfoOpt.get());
    }

    @Override
    public void playSound(Object playerObject, SoundInfo soundInfo) {
        if (!(playerObject instanceof Player player)) {
            return;
        }

        if (soundInfo == null) {
            LOGGER.warning("Tentativa de tocar SoundInfo nulo para " + player.getUsername());
            return;
        }

        try {
            Key soundAdventureKey = Key.key(soundInfo.key());

            Sound.Source source;
            try {
                source = Sound.Source.valueOf(soundInfo.source().toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Categoria de som inválida '" + soundInfo.source() + "' para Adventure/Velocity. Usando MASTER como fallback.");
                source = Sound.Source.MASTER;
            }

            Sound adventureSound = Sound.sound(
                    soundAdventureKey,
                    source,
                    soundInfo.volume(),
                    soundInfo.pitch()
            );

            player.playSound(adventureSound, Sound.Emitter.self());

        } catch (Exception e) {
            LOGGER.severe("Erro ao tocar som '" + soundInfo.key() + "' para " + player.getUsername() + ": " + e.getMessage());
        }
    }
}