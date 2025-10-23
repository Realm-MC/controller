package com.realmmc.controller.shared.sounds;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registo central para mapear chaves lógicas a informações de som (SoundInfo).
 * Substitui a necessidade de SoundKeys hardcoded com enums Bukkit.
 */
public class SoundRegistry {

    private static final Logger LOGGER = Logger.getLogger(SoundRegistry.class.getName());
    private static final Map<String, SoundInfo> SOUND_MAP = new ConcurrentHashMap<>();

    static {
        // Fontes Comuns: MASTER, MUSIC, RECORDS, WEATHER, BLOCK, HOSTILE, NEUTRAL, PLAYER, AMBIENT, VOICE
        // Encontra as chaves aqui: https://minecraft.wiki/w/Sounds.json
        registerSound("NOTIFICATION", new SoundInfo("minecraft:block.note_block.bell", "RECORDS", 1.0f, 2.0f));
        registerSound("USAGE_ERROR", new SoundInfo("minecraft:block.note_block.bass", "RECORDS", 1.0f, 1.0f));
        registerSound("SUCCESS", new SoundInfo("minecraft:entity.player.levelup", "PLAYER", 0.8f, 1.5f));
        registerSound("ERROR", new SoundInfo("minecraft:entity.villager.no", "NEUTRAL", 1.0f, 1.0f));
        registerSound("CLICK", new SoundInfo("minecraft:ui.button.click", "MASTER", 0.5f, 1.0f));
        registerSound("SETTING_UPDATE", new SoundInfo("minecraft:block.note_block.pling", "RECORDS", 1.0f, 1.5f)); // Som de "pling" agudo
        registerSound("TELEPORT_WHOOSH", new SoundInfo("minecraft:entity.enderman.teleport", "PLAYER", 0.8f, 1.0f)); // Som clássico de teleporte
    }

    /**
     * Regista ou substitui um som no mapa.
     * A chave é convertida para maiúsculas.
     */
    public static void registerSound(String logicalKey, SoundInfo soundInfo) {
        if (logicalKey == null || soundInfo == null) {
            LOGGER.warning("Tentativa de registar som com chave ou info nula.");
            return;
        }
        SOUND_MAP.put(logicalKey.toUpperCase(), soundInfo);
        LOGGER.fine("Som registrado/atualizado: " + logicalKey.toUpperCase());
    }

    /**
     * Obtém as informações de som (SoundInfo) para uma chave lógica.
     * A pesquisa é case-insensitive.
     */
    public static Optional<SoundInfo> getSound(String logicalKey) {
        if (logicalKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SOUND_MAP.get(logicalKey.toUpperCase()));
    }

    /**
     * Remove um som do registo.
     * A pesquisa é case-insensitive.
     */
    public static void unregisterSound(String logicalKey) {
        if (logicalKey != null) {
            SOUND_MAP.remove(logicalKey.toUpperCase());
        }
    }

    /**
     * Limpa todos os sons registrados.
     */
    public static void clearRegistry() {
        SOUND_MAP.clear();
        LOGGER.info("Registo de sons limpo.");
    }
}