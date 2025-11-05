package com.realmmc.controller.spigot.entities.particles.animator;

import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

public class CircleAnimator extends AbstractAnimator {

    private final World world;
    private final Location center;
    private final Particle particle;
    private final double radius;
    private final double speed; // Graus por tick
    private double angle = 0;

    public CircleAnimator(ParticleEntry entry, Player playerForParticle) {
        super(entry);
        this.world = Bukkit.getWorld(entry.getWorld());
        this.center = new Location(world, entry.getX(), entry.getY(), entry.getZ());
        this.particle = Particle.valueOf(entry.getParticleType());

        // Lê as propriedades da animação, com valores padrão
        Map<String, String> props = entry.getAnimationProperties();
        this.radius = Double.parseDouble(props.getOrDefault("radius", "1.0"));
        this.speed = Double.parseDouble(props.getOrDefault("speed", "5.0"));
    }

    @Override
    public void run() {
        if (world == null) {
            stop();
            return;
        }

        angle += speed;
        if (angle >= 360) {
            angle = 0;
        }

        double radians = Math.toRadians(angle);
        double x = center.getX() + radius * Math.cos(radians);
        double z = center.getZ() + radius * Math.sin(radians);

        Location particleLoc = new Location(world, x, center.getY(), z);

        // Usamos o método do mundo para que todos os jogadores próximos vejam
        world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0, null, entry.isLongDistance());
    }
}