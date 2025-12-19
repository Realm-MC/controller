package com.palacesky.controller.shared.sounds;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registo central para mapear chaves lógicas a informações de som (SoundInfo).
 * Usa nomes de Categoria da API Adventure Sound.Source.
 */
public class SoundRegistry {

    private static final Logger LOGGER = Logger.getLogger(SoundRegistry.class.getName());
    private static final Map<String, SoundInfo> SOUND_MAP = new ConcurrentHashMap<>();

    static {
        registerSound("NOTIFICATION", new SoundInfo("minecraft:block.note_block.bell", "RECORD", 1.0f, 2.0f));
        registerSound("USAGE_ERROR", new SoundInfo("minecraft:block.note_block.bass", "RECORD", 1.0f, 1.0f));
        registerSound("SETTING_UPDATE", new SoundInfo("minecraft:block.note_block.pling", "RECORD", 1.0f, 1.5f));
        registerSound("SUCCESS", new SoundInfo("minecraft:entity.player.levelup", "PLAYER", 0.8f, 1.5f));
        registerSound("ERROR", new SoundInfo("minecraft:entity.villager.no", "NEUTRAL", 1.0f, 1.0f));
        registerSound("CLICK", new SoundInfo("minecraft:ui.button.click", "MASTER", 0.5f, 1.0f));
        registerSound("TELEPORT_WHOOSH", new SoundInfo("minecraft:entity.enderman.teleport", "PLAYER", 0.8f, 1.0f));
    }

    /**
     * Regista ou substitui um som no mapa.
     * A chave lógica é convertida para maiúsculas.
     * @param logicalKey A chave lógica (ex: "NOTIFICATION").
     * @param soundInfo As informações do som (chave Minecraft, Categoria Adventure, volume, pitch).
     */
    public static void registerSound(String logicalKey, SoundInfo soundInfo) {
        if (logicalKey == null || logicalKey.isBlank() || soundInfo == null) {
            LOGGER.warning("Tentativa de registar som com chave lógica ou SoundInfo inválido.");
            return;
        }
        // Valida a categoria de som (opcional, mas bom)
        try {
            // Tenta converter para o enum Adventure para validação
            net.kyori.adventure.sound.Sound.Source.valueOf(soundInfo.source().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Categoria de som '" + soundInfo.source() + "' inválida para Adventure API ao registrar a chave '" + logicalKey.toUpperCase() + "'. Verifique os nomes em Sound.Source.");
            // Pode optar por não registrar, ou registrar mesmo assim e deixar o Player dar erro/fallback
        }

        SOUND_MAP.put(logicalKey.toUpperCase(), soundInfo);
        LOGGER.fine("Som registrado/atualizado: " + logicalKey.toUpperCase() + " -> " + soundInfo);
    }

    /**
     * Obtém as informações de som (SoundInfo) para uma chave lógica.
     * A pesquisa é case-insensitive (usa a chave lógica em maiúsculas).
     * @param logicalKey A chave lógica (ex: "SUCCESS").
     * @return Optional contendo SoundInfo se encontrado.
     */
    public static Optional<SoundInfo> getSound(String logicalKey) {
        if (logicalKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SOUND_MAP.get(logicalKey.toUpperCase()));
    }

    /**
     * Remove um som do registo pela chave lógica (case-insensitive).
     * @param logicalKey A chave lógica a remover.
     */
    public static void unregisterSound(String logicalKey) {
        if (logicalKey != null) {
            SoundInfo removed = SOUND_MAP.remove(logicalKey.toUpperCase());
            if (removed != null) {
                LOGGER.fine("Som desregistrado: " + logicalKey.toUpperCase());
            }
        }
    }

    /**
     * Limpa completamente o registo de sons.
     */
    public static void clearRegistry() {
        SOUND_MAP.clear();
        LOGGER.info("Registo de sons (SoundRegistry) limpo.");
    }
}