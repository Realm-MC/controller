package com.realmmc.controller.spigot.entities.particles.animator;

import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

public class HelixAnimator extends AbstractAnimator {

    private final World world;
    private final Location center;
    private final Particle particle;
    private final double radius;
    private final double height;
    private final double speed;
    private double angle = 0;

    public HelixAnimator(ParticleEntry entry, Player playerForParticle) {
        super(entry);
        this.world = Bukkit.getWorld(entry.getWorld());
        this.center = new Location(world, entry.getX(), entry.getY(), entry.getZ());
        this.particle = Particle.valueOf(entry.getParticleType());

        Map<String, String> props = entry.getAnimationProperties();
        this.radius = Double.parseDouble(props.getOrDefault("radius", "1.5"));
        this.height = Double.parseDouble(props.getOrDefault("height", "2.0"));
        this.speed = Double.parseDouble(props.getOrDefault("speed", "10.0"));
    }

    @Override
    public void run() {
        if (world == null) {
            stop();
            return;
        }

        angle += speed;
        if (angle >= 360) {
            angle -= 360;
        }

        double radians = Math.toRadians(angle);

        // Partícula 1
        double x1 = center.getX() + radius * Math.cos(radians);
        double z1 = center.getZ() + radius * Math.sin(radians);
        double y1 = center.getY() + (height / 2.0) * Math.sin(radians);
        Location loc1 = new Location(world, x1, y1, z1);

        // Partícula 2 (oposta)
        double radians2 = radians + Math.PI; // Adiciona 180 graus
        double x2 = center.getX() + radius * Math.cos(radians2);
        double z2 = center.getZ() + radius * Math.sin(radians2);
        double y2 = center.getY() + (height / 2.0) * Math.sin(radians2);
        Location loc2 = new Location(world, x2, y2, z2);

        world.spawnParticle(particle, loc1, 1, 0, 0, 0, 0, null, entry.isLongDistance());
        world.spawnParticle(particle, loc2, 1, 0, 0, 0, 0, null, entry.isLongDistance());
    }
}