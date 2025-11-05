package com.realmmc.controller.spigot.entities.particles.animator;

import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class AbstractAnimator extends BukkitRunnable implements Animator {

    protected final ParticleEntry entry;
    protected boolean isRunning = false;

    public AbstractAnimator(ParticleEntry entry) {
        this.entry = entry;
    }

    @Override
    public void start() {
        if (!isRunning) {
            this.runTaskTimer(Main.getInstance(), 0L, entry.getUpdateInterval());
            isRunning = true;
        }
    }

    @Override
    public void stop() {
        if (isRunning) {
            this.cancel();
            isRunning = false;
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    // O método run() será implementado por cada tipo de animação
}