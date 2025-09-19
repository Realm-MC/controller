package com.realmmc.controller.shared.utils;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.*;

public final class TaskScheduler {
    private static ProxyServer proxyServer;
    private static Plugin bukkitPlugin;
    private static Object plugin;
    private static ScheduledExecutorService asyncPool;
    private static boolean isSpigot = false;

    private TaskScheduler() {}

    public static synchronized void init(ProxyServer proxyServer, Object pluginInstance) {
        if (TaskScheduler.proxyServer != null || bukkitPlugin != null) return;
        TaskScheduler.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
        plugin = Objects.requireNonNull(pluginInstance, "pluginInstance");
        isSpigot = false;
        asyncPool = Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
            Thread t = new Thread(r, "Controller-Async");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized void init(Plugin bukkitPlugin) {
        if (proxyServer != null || TaskScheduler.bukkitPlugin != null) return;
        TaskScheduler.bukkitPlugin = Objects.requireNonNull(bukkitPlugin, "bukkitPlugin");
        isSpigot = true;
        asyncPool = Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
            Thread t = new Thread(r, "Controller-Async");
            t.setDaemon(true);
            return t;
        });
    }

    public static Object runSync(Runnable task) {
        ensureInit();
        if (isSpigot) {
            return bukkitPlugin.getServer().getScheduler().runTask(bukkitPlugin, task);
        } else {
            return proxyServer.getScheduler().buildTask(plugin, task).schedule();
        }
    }

    public static Object runSyncLater(Runnable task, long delay, TimeUnit unit) {
        ensureInit();
        if (isSpigot) {
            long ticks = unit.toMillis(delay) / 50;
            return bukkitPlugin.getServer().getScheduler().runTaskLater(bukkitPlugin, task, ticks);
        } else {
            return proxyServer.getScheduler().buildTask(plugin, task).delay(delay, unit).schedule();
        }
    }

    public static Object runSyncTimer(Runnable task, long delay, long interval, TimeUnit unit) {
        ensureInit();
        if (isSpigot) {
            long delayTicks = unit.toMillis(delay) / 50;
            long intervalTicks = unit.toMillis(interval) / 50;
            return bukkitPlugin.getServer().getScheduler().runTaskTimer(bukkitPlugin, task, delayTicks, intervalTicks);
        } else {
            return proxyServer.getScheduler().buildTask(plugin, task).delay(delay, unit).repeat(interval, unit).schedule();
        }
    }

    public static CompletableFuture<Void> runAsync(Runnable task) {
        ensureInit();
        return CompletableFuture.runAsync(task, asyncPool);
    }

    public static ScheduledFuture<?> runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        ensureInit();
        return asyncPool.schedule(task, delay, unit);
    }

    public static ScheduledFuture<?> runAsyncTimer(Runnable task, long delay, long interval, TimeUnit unit) {
        ensureInit();
        return asyncPool.scheduleAtFixedRate(task, unit.toMillis(delay), unit.toMillis(interval), TimeUnit.MILLISECONDS);
    }

    public static synchronized void shutdown() {
        if (asyncPool != null) {
            asyncPool.shutdownNow();
            asyncPool = null;
        }
        proxyServer = null;
        bukkitPlugin = null;
        plugin = null;
        isSpigot = false;
    }

    private static void ensureInit() {
        if (proxyServer == null && bukkitPlugin == null) {
            throw new IllegalStateException("TaskScheduler not initialized. Call TaskScheduler.init(server, plugin) first.");
        }
    }
}
