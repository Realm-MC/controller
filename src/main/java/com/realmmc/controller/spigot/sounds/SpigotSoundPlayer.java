package com.realmmc.controller.spigot.sounds;

import com.realmmc.controller.shared.sounds.SoundInfo;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.sounds.SoundRegistry;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.logging.Logger;

public class SpigotSoundPlayer implements SoundPlayer {

    private static final Logger LOGGER = Logger.getLogger(SpigotSoundPlayer.class.getName());

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
            LOGGER.warning("Tentativa de tocar SoundInfo nulo para " + player.getName());
            return;
        }

        SoundCategory category;
        try {
            category = SoundCategory.valueOf(soundInfo.source().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Categoria de som inválida '" + soundInfo.source() + "' para Spigot. Usando MASTER como fallback.");
            category = SoundCategory.MASTER;
        }

        Location location = player.getLocation();
        try {
            player.playSound(location, soundInfo.key(), category, soundInfo.volume(), soundInfo.pitch());
        } catch (Exception e) {
            LOGGER.severe("Erro ao tocar som '" + soundInfo.key() + "' para " + player.getName() + ": " + e.getMessage());
        }
    }
}