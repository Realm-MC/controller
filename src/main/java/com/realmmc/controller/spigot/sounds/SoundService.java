package com.realmmc.controller.spigot.sounds;

import com.realmmc.controller.shared.sounds.SoundInfo;
import com.realmmc.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class SoundService {

    private final Map<String, SoundInfo> soundRegistry = new HashMap<>();
    private final Logger logger;
    private final Main plugin;

    public SoundService(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadSoundsFromCode();
    }

    private void loadSoundsFromCode() {
        soundRegistry.clear();

        // Adiciona os sons diretamente ao nosso "catálogo"
        soundRegistry.put(SoundKeys.NOTIFICATION, new SoundInfo(Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 1.0f, 2.0f));
        soundRegistry.put(SoundKeys.ERROR, new SoundInfo(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.MASTER, 1.0f, 0.8f));
        soundRegistry.put(SoundKeys.SUCCESS, new SoundInfo(Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.5f));
        soundRegistry.put(SoundKeys.MENU_CLICK, new SoundInfo(Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.7f, 1.0f));

        logger.info(soundRegistry.size() + " sons carregados com sucesso (hardcoded)!");
    }

    public void reloadSounds() {
        loadSoundsFromCode();
    }

    public Optional<SoundInfo> getSound(String key) {
        return Optional.ofNullable(soundRegistry.get(key.toLowerCase()));
    }

    public void playSound(Player player, String key) {
        getSound(key).ifPresent(soundInfo ->
                player.playSound(player.getLocation(), soundInfo.sound(), soundInfo.category(), soundInfo.volume(), soundInfo.pitch())
        );
    }

    public void playSound(Location location, String key) {
        getSound(key).ifPresent(soundInfo ->
                location.getWorld().playSound(location, soundInfo.sound(), soundInfo.category(), soundInfo.volume(), soundInfo.pitch())
        );
    }

    public void broadcastSound(String key) {
        getSound(key).ifPresent(soundInfo -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), soundInfo.sound(), soundInfo.category(), soundInfo.volume(), soundInfo.pitch());
            }
        });
    }
}