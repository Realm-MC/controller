package com.realmmc.controller.spigot.entities.npcs;

import com.github.retrooper.packetevents.protocol.player.UserProfile;
import org.bukkit.Location;

import java.util.UUID;

public record NPCData(UUID uuid, int entityId, UserProfile profile, Location location, String name, String skin) {
}