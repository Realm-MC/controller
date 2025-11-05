package com.realmmc.controller.spigot.entities.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
public class ParticleEntry {

    private String id;
    private String world;
    private double x;
    private double y;
    private double z;
    private String particleType;
    private int amount;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private double speed;
    private String particleData;
    private boolean longDistance;
    private int updateInterval;

    // NOVOS CAMPOS PARA ANIMAÇÃO
    private String animationType;
    private Map<String, String> animationProperties = new HashMap<>();

    public ParticleEntry(String id, String world, double x, double y, double z, String particleType, int amount, int updateInterval) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.particleType = particleType;
        this.amount = amount;
        this.updateInterval = updateInterval;
        this.offsetX = 0.0;
        this.offsetY = 0.0;
        this.offsetZ = 0.0;
        this.speed = 0.0;
        this.particleData = null;
        this.longDistance = false;
    }
}