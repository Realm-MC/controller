package com.palacesky.controller.shared.storage.redis;

import com.palacesky.controller.shared.utils.TaskScheduler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisSubscriber {
    private static final Logger LOGGER = Logger.getLogger(RedisSubscriber.class.getName());

    private static final int RECONNECT_DELAY_SECONDS = 2;

    private final Map<String, RedisMessageListener> listeners = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private CompletableFuture<Void> future;
    private volatile JedisPubSub pubSub;
    private ScheduledFuture<?> pingTask;

    public void registerListener(RedisChannel channel, RedisMessageListener listener) {
        Objects.requireNonNull(channel, "RedisChannel cannot be null");
        Objects.requireNonNull(listener, "RedisMessageListener cannot be null");
        registerListener(channel.getName(), listener);
    }

    public synchronized void registerListener(String channelName, RedisMessageListener listener) {
        Objects.requireNonNull(channelName, "Channel name cannot be null");
        Objects.requireNonNull(listener, "RedisMessageListener cannot be null");

        listeners.put(channelName, listener);
        LOGGER.info("Listener " + listener.getClass().getSimpleName() + " registrado para o canal: " + channelName);

        JedisPubSub currentPubSub = this.pubSub;
        if (running && currentPubSub != null && currentPubSub.isSubscribed()) {
            try {
                LOGGER.fine("Subscrevendo dinamicamente ao novo canal: " + channelName);
                currentPubSub.subscribe(channelName);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Falha ao subscrever dinamicamente no canal " + channelName, e);
            }
        }
    }

    public void unregisterListener(RedisChannel channel) {
        if (channel != null) {
            unregisterListener(channel.getName());
        }
    }

    public synchronized void unregisterListener(String channelName) {
        if (channelName != null) {
            RedisMessageListener removedListener = listeners.remove(channelName);
            if (removedListener != null) {
                LOGGER.fine("Listener removido do mapa: " + channelName);
                JedisPubSub currentPubSub = this.pubSub;
                if (currentPubSub != null && currentPubSub.isSubscribed()) {
                    try {
                        currentPubSub.unsubscribe(channelName);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Falha ao desinscrever dinamicamente do canal " + channelName, e);
                    }
                }
            }
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        LOGGER.info("Iniciando loop de subscrição do RedisSubscriber...");

        future = TaskScheduler.runAsync(() -> {
            Jedis resource = null;
            JedisPubSub currentPubSub = null;

            while (running) {
                try {
                    String[] channelsArray = listeners.keySet().toArray(new String[0]);
                    if (channelsArray.length == 0) {
                        try { TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS); } catch (InterruptedException ie) {
                            running = false;
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }

                    LOGGER.info("Conectando ao Redis para ouvir " + channelsArray.length + " canais...");
                    resource = RedisManager.getResource();

                    currentPubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            if (!running) return;
                            RedisMessageListener listener = listeners.get(channel);
                            if (listener != null) {
                                try {
                                    listener.onMessage(channel, message);
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Erro ao processar mensagem no canal " + channel, e);
                                }
                            }
                        }
                        @Override public void onSubscribe(String channel, int subscribedChannels) { LOGGER.info("Inscrito: " + channel); }
                        @Override public void onUnsubscribe(String channel, int subscribedChannels) { LOGGER.info("Desinscrito: " + channel); }
                    };

                    this.pubSub = currentPubSub;
                    startPingTask(currentPubSub);

                    resource.subscribe(currentPubSub, channelsArray);

                    LOGGER.info("RedisSubscriber saiu do modo de escuta.");
                    running = false;

                } catch (JedisConnectionException jce) {
                    LOGGER.log(Level.SEVERE, "Erro de conexão Redis. Reconectando em " + RECONNECT_DELAY_SECONDS + "s...");
                    this.pubSub = null;
                    stopPingTask();
                    if (resource != null) try { resource.close(); } catch (Exception ignored) {}
                    resource = null;

                    try {
                        if (running) TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS);
                    } catch (InterruptedException ie) {
                        running = false;
                        Thread.currentThread().interrupt();
                    }

                } catch (Exception e) {
                    if (running && !(e instanceof InterruptedException)) {
                        LOGGER.log(Level.SEVERE, "Erro inesperado no RedisSubscriber.", e);
                    }
                    running = false;
                } finally {
                    stopPingTask();
                    if (this.pubSub == currentPubSub) { this.pubSub = null; }
                    if (resource != null) { try { resource.close(); } catch (Exception ignored) {} }
                }
            }
            running = false;
            stopPingTask();
            this.pubSub = null;
        });

        future.exceptionally(ex -> {
            if (!(ex instanceof CancellationException)) { LOGGER.log(Level.SEVERE, "Falha na thread do RedisSubscriber", ex); }
            running = false;
            return null;
        });
    }

    public synchronized void stop() {
        if (!running && pubSub == null) return;
        LOGGER.info("Parando RedisSubscriber...");
        running = false;
        stopPingTask();

        JedisPubSub currentPubSub = this.pubSub;
        if (currentPubSub != null) {
            try {
                if (currentPubSub.isSubscribed()) currentPubSub.unsubscribe();
            } catch (Exception ignored) {}
            this.pubSub = null;
        }

        if (this.future != null) {
            this.future.cancel(true);
            this.future = null;
        }
    }

    private void startPingTask(final JedisPubSub pubSubInstance) {
        if (pingTask != null && !pingTask.isDone()) return;
        try {
            pingTask = TaskScheduler.runAsyncTimer(() -> {
                if (running && pubSubInstance != null && pubSubInstance == this.pubSub && pubSubInstance.isSubscribed()) {
                    try {
                        pubSubInstance.ping();
                    } catch (Exception e) {
                        LOGGER.warning("Falha no PING Redis. A conexão pode ter caído.");
                        stopPingTask();
                    }
                } else {
                    stopPingTask();
                }
            }, 1, 1, TimeUnit.MINUTES);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao agendar PING Redis", e);
        }
    }

    private void stopPingTask() {
        if (pingTask != null) {
            try { pingTask.cancel(false); } catch(Exception ignored) {}
            pingTask = null;
        }
    }
}