package com.realmmc.controller.shared.utils;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * Simple task scheduler facade for Velocity with sync (Velocity scheduler) and
 * async (own executor) helpers.
 */
public final class TaskScheduler {
    private static ProxyServer server;
    private static Object plugin;
    private static ScheduledExecutorService asyncPool;

    private TaskScheduler() {}

    public static synchronized void init(ProxyServer proxyServer, Object pluginInstance) {
        if (server != null) return;
        server = Objects.requireNonNull(proxyServer, "proxyServer");
        plugin = Objects.requireNonNull(pluginInstance, "pluginInstance");
        asyncPool = Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
            Thread t = new Thread(r, "Controller-Async");
            t.setDaemon(true);
            return t;
        });
    }

    public static ScheduledTask runSync(Runnable task) {
        ensureInit();
        return server.getScheduler().buildTask(plugin, task).schedule();
    }

    public static ScheduledTask runSyncLater(Runnable task, long delay, TimeUnit unit) {
        ensureInit();
        return server.getScheduler().buildTask(plugin, task).delay(delay, unit).schedule();
    }

    public static ScheduledTask runSyncTimer(Runnable task, long delay, long interval, TimeUnit unit) {
        ensureInit();
        return server.getScheduler().buildTask(plugin, task).delay(delay, unit).repeat(interval, unit).schedule();
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
        server = null;
        plugin = null;
    }

    private static void ensureInit() {
        if (server == null || plugin == null) {
            throw new IllegalStateException("TaskScheduler not initialized. Call TaskScheduler.init(server, plugin) first.");
        }
    }
}
