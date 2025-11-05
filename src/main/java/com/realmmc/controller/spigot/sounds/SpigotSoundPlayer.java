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
            // <<< INÍCIO DA CORREÇÃO (MAPEAMENTO) >>>
            // Mapeia os nomes do Adventure (usados no SoundRegistry) para os nomes do Bukkit
            String adventureSource = soundInfo.source().toUpperCase();
            String bukkitCategoryName;

            switch (adventureSource) {
                case "RECORD":
                    bukkitCategoryName = "RECORDS";
                    break;
                case "PLAYER":
                    bukkitCategoryName = "PLAYERS";
                    break;
                // Adicione outros mapeamentos se necessário (ex: NEUTRAL, HOSTILE)
                // Se os nomes forem iguais (ex: MASTER, MUSIC, VOICE), o default funciona
                default:
                    bukkitCategoryName = adventureSource;
                    break;
            }

            category = SoundCategory.valueOf(bukkitCategoryName);
            // <<< FIM DA CORREÇÃO >>>
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Categoria de som '" + soundInfo.source() + "' inválida para Spigot (mesmo após mapeamento). Usando MASTER como fallback.");
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