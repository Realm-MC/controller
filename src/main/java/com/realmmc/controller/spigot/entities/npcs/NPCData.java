package com.realmmc.controller.spigot.entities.npcs;

import com.github.retrooper.packetevents.protocol.player.UserProfile;
import org.bukkit.Location;

import java.util.UUID;

public class NPCData {
    private final UUID uuid;
    private final int entityId;
    private final UserProfile profile;
    private final Location location;
    private final String name;
    private final String skin;

    public NPCData(UUID uuid, int entityId, UserProfile profile, Location location, String name, String skin) {
        this.uuid = uuid;
        this.entityId = entityId;
        this.profile = profile;
        this.location = location;
        this.name = name;
        this.skin = skin;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getEntityId() {
        return entityId;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public Location getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public String getSkin() {
        return skin;
    }
}