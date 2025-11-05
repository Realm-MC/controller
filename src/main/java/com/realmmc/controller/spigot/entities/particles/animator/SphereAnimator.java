package com.realmmc.controller.spigot.entities.particles.animator;

import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Random;

public class SphereAnimator extends AbstractAnimator {

    private final World world;
    private final Location center;
    private final Particle particle;
    private final double radius;
    private final int density;
    private final Random random = new Random();

    public SphereAnimator(ParticleEntry entry, Player playerForParticle) {
        super(entry);
        this.world = Bukkit.getWorld(entry.getWorld());
        this.center = new Location(world, entry.getX(), entry.getY(), entry.getZ());
        this.particle = Particle.valueOf(entry.getParticleType());

        Map<String, String> props = entry.getAnimationProperties();
        this.radius = Double.parseDouble(props.getOrDefault("radius", "2.0"));
        this.density = Integer.parseInt(props.getOrDefault("density", "50"));
    }

    @Override
    public void run() {
        if (world == null) {
            stop();
            return;
        }

        for (int i = 0; i < density; i++) {
            // Gera um ponto aleatório na superfície de uma esfera
            double theta = 2 * Math.PI * random.nextDouble();
            double phi = Math.acos(1 - 2 * random.nextDouble());

            double x = center.getX() + radius * Math.sin(phi) * Math.cos(theta);
            double y = center.getY() + radius * Math.cos(phi);
            double z = center.getZ() + radius * Math.sin(phi) * Math.sin(theta);

            Location particleLoc = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0, null, entry.isLongDistance());
        }
    }
}