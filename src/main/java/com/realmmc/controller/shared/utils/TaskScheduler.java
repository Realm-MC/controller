package com.realmmc.controller.shared.utils;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask; // Import Velocity Task
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask; // Import Bukkit Task

import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level; // Import Level
import java.util.logging.Logger; // Import Logger

public final class TaskScheduler {
    private static ProxyServer proxyServer;
    private static Plugin bukkitPlugin;
    private static Object pluginInstance; // Instância do plugin Velocity (@Plugin)
    private static ScheduledExecutorService asyncPool;
    private static boolean isSpigot = false;
    private static boolean initialized = false; // Flag de inicialização
    private static final Logger LOGGER = Logger.getLogger(TaskScheduler.class.getName()); // Logger interno

    private TaskScheduler() {}

    // Inicialização para Velocity
    public static synchronized void init(ProxyServer proxy, Object plugin) {
        if (initialized) {
            LOGGER.warning("TaskScheduler já inicializado.");
            return;
        }
        proxyServer = Objects.requireNonNull(proxy, "ProxyServer não pode ser nulo");
        pluginInstance = Objects.requireNonNull(plugin, "Instância do Plugin Velocity não pode ser nula");
        isSpigot = false;
        asyncPool = createAsyncPool("Controller-Velocity-Async");
        initialized = true;
        LOGGER.info("TaskScheduler inicializado para Velocity.");
    }

    // Inicialização para Spigot
    public static synchronized void init(Plugin plugin) {
        if (initialized) {
            LOGGER.warning("TaskScheduler já inicializado.");
            return;
        }
        bukkitPlugin = Objects.requireNonNull(plugin, "Plugin Bukkit não pode ser nulo");
        isSpigot = true;
        asyncPool = createAsyncPool("Controller-Spigot-Async");
        initialized = true;
        LOGGER.info("TaskScheduler inicializado para Spigot.");
    }

    // Cria o pool de threads assíncronas
    private static ScheduledExecutorService createAsyncPool(String threadPrefix) {
        // Usa um número razoável de threads, mínimo 2
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        LOGGER.fine("Criando asyncPool com " + corePoolSize + " threads (prefixo: " + threadPrefix + ")");
        return Executors.newScheduledThreadPool(corePoolSize, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadPrefix + "-" + counter++);
                t.setDaemon(true); // Threads da pool devem ser daemon
                return t;
            }
        });
    }

    // Garante que o scheduler foi inicializado
    private static void ensureInit() {
        if (!initialized) {
            throw new IllegalStateException("TaskScheduler não inicializado! Chame TaskScheduler.init() primeiro.");
        }
    }

    // --- Métodos Síncronos ---

    public static Object runSync(Runnable task) {
        ensureInit();
        try {
            if (isSpigot) {
                return bukkitPlugin.getServer().getScheduler().runTask(bukkitPlugin, task);
            } else {
                return proxyServer.getScheduler().buildTask(pluginInstance, task).schedule();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao agendar tarefa síncrona", e);
            return null; // Retorna null ou lança exceção, dependendo da necessidade
        }
    }

    public static Object runSyncLater(Runnable task, long delay, TimeUnit unit) {
        ensureInit();
        try {
            if (isSpigot) {
                long ticks = unit.toMillis(delay) / 50; // Converte para ticks (assumindo 20 TPS)
                return bukkitPlugin.getServer().getScheduler().runTaskLater(bukkitPlugin, task, Math.max(1, ticks)); // Mínimo 1 tick
            } else {
                return proxyServer.getScheduler().buildTask(pluginInstance, task).delay(delay, unit).schedule();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao agendar tarefa síncrona com delay", e);
            return null;
        }
    }

    public static Object runSyncTimer(Runnable task, long delay, long interval, TimeUnit unit) {
        ensureInit();
        try {
            if (isSpigot) {
                long delayTicks = unit.toMillis(delay) / 50;
                long intervalTicks = unit.toMillis(interval) / 50;
                // Garante que delay e interval sejam pelo menos 1 tick
                return bukkitPlugin.getServer().getScheduler().runTaskTimer(bukkitPlugin, task, Math.max(0, delayTicks), Math.max(1, intervalTicks));
            } else {
                return proxyServer.getScheduler().buildTask(pluginInstance, task)
                        .delay(delay, unit)
                        .repeat(interval, unit)
                        .schedule();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao agendar tarefa síncrona periódica", e);
            return null;
        }
    }

    // --- Métodos Assíncronos ---

    public static CompletableFuture<Void> runAsync(Runnable task) {
        ensureInit();
        // Usa o asyncPool criado
        return CompletableFuture.runAsync(task, asyncPool);
    }

    public static ScheduledFuture<?> runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        ensureInit();
        // Usa o asyncPool criado
        return asyncPool.schedule(task, delay, unit);
    }

    public static ScheduledFuture<?> runAsyncTimer(Runnable task, long delay, long interval, TimeUnit unit) {
        ensureInit();
        // Usa o asyncPool criado
        // Converte para milissegundos para scheduleAtFixedRate
        long delayMs = unit.toMillis(delay);
        long intervalMs = unit.toMillis(interval);
        return asyncPool.scheduleAtFixedRate(task, delayMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    // --- Cancelamento ---

    /**
     * Tenta cancelar uma tarefa agendada.
     * @param taskObject O objeto retornado pelos métodos runSync/runAsync (BukkitTask ou ScheduledTask).
     */
    public static void cancelTask(Object taskObject) {
        if (taskObject == null) return;
        try {
            if (taskObject instanceof BukkitTask) {
                ((BukkitTask) taskObject).cancel();
            } else if (taskObject instanceof ScheduledTask) {
                ((ScheduledTask) taskObject).cancel();
            } else if (taskObject instanceof ScheduledFuture) {
                ((ScheduledFuture<?>) taskObject).cancel(false); // Não interrompe se já estiver rodando
            } else {
                LOGGER.warning("Tipo de tarefa desconhecido para cancelamento: " + taskObject.getClass().getName());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao cancelar tarefa", e);
        }
    }

    // --- Shutdown ---

    public static synchronized void shutdown() {
        if (!initialized) return;

        LOGGER.info("Finalizando TaskScheduler...");
        // Cancela tarefas síncronas (Bukkit/Velocity lidam com isso no disable)

        // Finaliza o pool assíncrono
        if (asyncPool != null) {
            asyncPool.shutdown(); // Inicia shutdown gracioso
            try {
                // Espera um pouco para tarefas terminarem
                if (!asyncPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    asyncPool.shutdownNow(); // Força o shutdown
                    LOGGER.warning("Pool assíncrono forçado a finalizar.");
                } else {
                    LOGGER.fine("Pool assíncrono finalizado graciosamente.");
                }
            } catch (InterruptedException e) {
                asyncPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Limpa referências
        proxyServer = null;
        bukkitPlugin = null;
        pluginInstance = null;
        asyncPool = null;
        initialized = false;
        LOGGER.info("TaskScheduler finalizado.");
    }

    // Opcional: Getter para o Executor Assíncrono se outros serviços precisarem dele
    public static ExecutorService getAsyncExecutor() {
        ensureInit();
        return asyncPool;
    }
}