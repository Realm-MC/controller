package com.realmmc.controller.shared.sounds;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;

public record SoundInfo(Sound sound, SoundCategory category, float volume, float pitch) {
}