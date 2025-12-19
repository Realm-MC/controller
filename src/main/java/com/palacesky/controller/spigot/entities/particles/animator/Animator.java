package com.palacesky.controller.spigot.entities.particles.animator;

public interface Animator extends Runnable {
    void start();
    void stop();
    boolean isRunning();
}