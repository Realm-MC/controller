package com.palacesky.controller.shared.sounds;

/**
 * Representa as informações de um som de forma independente da plataforma.
 *
 * @param key    A chave de som do Minecraft (ex: "minecraft:block.note_block.bell").
 * @param source A categoria/fonte do som (usando nomes da API Adventure Sound.Source, ex: "RECORDS", "PLAYER", "MASTER").
 * @param volume O volume do som (tipicamente 0.0 a 1.0+).
 * @param pitch  A tonalidade do som (tipicamente 0.5 a 2.0).
 */
public record SoundInfo(String key, String source, float volume, float pitch) {
    public SoundInfo(String key, String source) {
        this(key, source, 1.0f, 1.0f);
    }
}