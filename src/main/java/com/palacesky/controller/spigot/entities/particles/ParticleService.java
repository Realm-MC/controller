package com.palacesky.controller.spigot.entities.particles;

import com.palacesky.controller.spigot.Main;
import com.palacesky.controller.spigot.entities.config.ParticleConfigLoader;
import com.palacesky.controller.spigot.entities.config.ParticleEntry;
import com.palacesky.controller.spigot.entities.particles.animator.AbstractAnimator;
import com.palacesky.controller.spigot.entities.particles.animator.CircleAnimator;
import com.palacesky.controller.spigot.entities.particles.animator.HelixAnimator;
import com.palacesky.controller.spigot.entities.particles.animator.SphereAnimator;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ParticleService {
    private final ParticleConfigLoader configLoader;
    private final Main plugin;
    private final Logger logger;
    private final Map<String, BukkitTask> activeParticleTasks = new HashMap<>();
    private final Map<String, AbstractAnimator> activeAnimators = new HashMap<>();

    public ParticleService(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configLoader = new ParticleConfigLoader(plugin);
        configLoader.load();
        startAllParticles();
    }

    public void startAllParticles() {
        stopAllParticles();
        for (ParticleEntry entry : configLoader.getEntries()) {
            if (entry.getAnimationType() != null && !entry.getAnimationType().isEmpty()) {
                startAnimation(entry);
            } else {
                scheduleParticleEffect(entry);
            }
        }
    }

    public void stopAllParticles() {
        activeParticleTasks.values().forEach(BukkitTask::cancel);
        activeParticleTasks.clear();
        activeAnimators.values().forEach(AbstractAnimator::stop);
        activeAnimators.clear();
        logger.info("Todas as tarefas de partículas e animações ativas foram paradas.");
    }

    public void reloadParticles() {
        stopAllParticles();
        configLoader.load();
        startAllParticles();
        logger.info("Serviço de partículas recarregado.");
    }

    private void startAnimation(ParticleEntry entry) {
        AbstractAnimator animator = null;
        switch (entry.getAnimationType().toLowerCase()) {
            case "circle":
                animator = new CircleAnimator(entry, null);
                break;
            case "helix":
                animator = new HelixAnimator(entry, null);
                break;
            case "sphere":
                animator = new SphereAnimator(entry, null);
                break;
            default:
                logger.warning("Tipo de animação desconhecido: '" + entry.getAnimationType() + "' para o efeito '" + entry.getId() + "'.");
                break;
        }

        if (animator != null) {
            animator.start();
            activeAnimators.put(entry.getId(), animator);
            logger.info("Animação '" + entry.getId() + "' do tipo '" + entry.getAnimationType() + "' iniciada.");
        }
    }

    public void stopAnimation(String id) {
        AbstractAnimator animator = activeAnimators.remove(id);
        if (animator != null) {
            animator.stop();
        }

        ParticleEntry entry = getParticleEntry(id);
        if (entry != null) {
            entry.setAnimationType(null);
            if (entry.getAnimationProperties() != null) {
                entry.getAnimationProperties().clear();
            }
            updateParticle(entry);
        }
    }

    private Object processParticleData(Particle particleType, String dataString, Location defaultLocation) {
        Class<?> dataType = particleType.getDataType();
        if (dataType.equals(Void.class)) {
            return null;
        }

        if (dataString != null && !dataString.isEmpty()) {
            String cleanedData = dataString.trim();
            if ((cleanedData.startsWith("\"") && cleanedData.endsWith("\"")) || (cleanedData.startsWith("'") && cleanedData.endsWith("'"))) {
                cleanedData = cleanedData.substring(1, cleanedData.length() - 1);
            }

            try {
                if (dataType.equals(Particle.DustOptions.class)) {
                    String[] parts = cleanedData.split(",");
                    if (parts.length >= 3) {
                        int r = Integer.parseInt(parts[0].trim());
                        int g = Integer.parseInt(parts[1].trim());
                        int b = Integer.parseInt(parts[2].trim());
                        float size = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1.0f;
                        return new Particle.DustOptions(Color.fromRGB(r, g, b), size);
                    }
                } else if (dataType.equals(BlockData.class)) {
                    return Material.valueOf(cleanedData.toUpperCase()).createBlockData();
                } else if (dataType.equals(ItemStack.class)) {
                    return new ItemStack(Objects.requireNonNull(Material.matchMaterial(cleanedData)));
                } else if (particleType == Particle.VIBRATION) {
                    int time = Integer.parseInt(cleanedData);
                    return new Vibration(new Vibration.Destination.BlockDestination(defaultLocation), time);
                }
            } catch (Exception e) {
                logger.warning("Dados de partícula inválidos ('" + cleanedData + "') para o tipo " + particleType.name() + ". Usando valor padrão.");
            }
        }

        if (dataType.equals(Particle.DustOptions.class)) {
            return new Particle.DustOptions(Color.RED, 1.0f);
        } else if (particleType == Particle.VIBRATION) {
            return new Vibration(new Vibration.Destination.BlockDestination(defaultLocation), 20);
        } else if (dataType.equals(BlockData.class)) {
            return Material.STONE.createBlockData();
        } else if (dataType.equals(ItemStack.class)) {
            return new ItemStack(Material.STONE);
        }
        return null;
    }

    private void scheduleParticleEffect(ParticleEntry entry) {
        if (entry.getUpdateInterval() <= 0) {
            return;
        }

        World world = Bukkit.getWorld(entry.getWorld());
        if (world == null) {
            return;
        }
        Location loc = new Location(world, entry.getX(), entry.getY(), entry.getZ());

        Particle particle;
        try {
            particle = Particle.valueOf(entry.getParticleType().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Tipo de partícula inválido '" + entry.getParticleType() + "' para '" + entry.getId() + "'.");
            return;
        }

        final Object finalParticleData = processParticleData(particle, entry.getParticleData(), loc);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                world.spawnParticle(particle, loc, entry.getAmount(), entry.getOffsetX(), entry.getOffsetY(), entry.getOffsetZ(), entry.getSpeed(), finalParticleData);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao gerar partícula para " + entry.getId() + ": " + e.getMessage(), e);
            }
        }, 0L, entry.getUpdateInterval());

        activeParticleTasks.put(entry.getId(), task);
    }

    public boolean spawnForPlayerOnce(Player player, String particleId) {
        ParticleEntry entry = getParticleEntry(particleId);
        if (entry == null) return false;

        Location loc = player.getLocation().add(0, 1, 0);
        Particle particle;
        try {
            particle = Particle.valueOf(entry.getParticleType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        Object finalParticleData = processParticleData(particle, entry.getParticleData(), loc);

        player.spawnParticle(particle, loc, entry.getAmount(), entry.getOffsetX(), entry.getOffsetY(), entry.getOffsetZ(), entry.getSpeed(), finalParticleData);
        return true;
    }

    public void createParticle(String id, Location location, String particleType, int amount, int updateInterval) {
        ParticleEntry entry = new ParticleEntry(id, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), particleType, amount, updateInterval);
        configLoader.addEntry(entry);
        configLoader.save();
        reloadParticles();
    }

    public void cloneParticle(String originalId, String newId, Location location) {
        ParticleEntry originalEntry = getParticleEntry(originalId);
        if (originalEntry == null) return;

        ParticleEntry newEntry = new ParticleEntry(
                newId,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                originalEntry.getParticleType(),
                originalEntry.getAmount(),
                originalEntry.getUpdateInterval()
        );

        newEntry.setOffsetX(originalEntry.getOffsetX());
        newEntry.setOffsetY(originalEntry.getOffsetY());
        newEntry.setOffsetZ(originalEntry.getOffsetZ());
        newEntry.setSpeed(originalEntry.getSpeed());
        newEntry.setParticleData(originalEntry.getParticleData());
        newEntry.setLongDistance(originalEntry.isLongDistance());
        newEntry.setAnimationType(originalEntry.getAnimationType());
        if (originalEntry.getAnimationProperties() != null) {
            newEntry.setAnimationProperties(new HashMap<>(originalEntry.getAnimationProperties()));
        }

        configLoader.addEntry(newEntry);
        configLoader.save();
        reloadParticles();
    }

    public void removeParticle(String id) {
        if (configLoader.removeEntry(id)) {
            stopAnimation(id);
            BukkitTask task = activeParticleTasks.remove(id);
            if (task != null) {
                task.cancel();
            }
            configLoader.save();
            logger.info("Partícula '" + id + "' removida e sua tarefa parada.");
        }
    }

    public void updateParticle(ParticleEntry entry) {
        if (configLoader.updateEntry(entry)) {
            configLoader.save();
            reloadParticles();
        }
    }

    public void teleportParticle(String id, Location newLocation) {
        ParticleEntry entry = getParticleEntry(id);
        if (entry != null) {
            entry.setWorld(newLocation.getWorld().getName());
            entry.setX(newLocation.getX());
            entry.setY(newLocation.getY());
            entry.setZ(newLocation.getZ());
            updateParticle(entry);
        }
    }

    public ParticleEntry getParticleEntry(String id) {
        return configLoader.getById(id);
    }

    public List<String> getAllParticleIds() {
        return configLoader.getEntries().stream()
                .map(ParticleEntry::getId)
                .collect(Collectors.toList());
    }
}