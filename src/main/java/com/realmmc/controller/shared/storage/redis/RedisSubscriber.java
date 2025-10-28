package com.realmmc.controller.shared.storage.redis;

import com.realmmc.controller.shared.utils.TaskScheduler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException; // Import specific exception

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException; // Import CancellationException
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerencia a subscrição a canais Redis e o dispatch de mensagens para listeners.
 * (Versão Corrigida: registerListener apenas adiciona ao mapa)
 */
public final class RedisSubscriber {
    private static final Logger LOGGER = Logger.getLogger(RedisSubscriber.class.getName());
    // Intervalo (em segundos) entre tentativas de reconexão
    private static final int RECONNECT_DELAY_SECONDS = 10;

    // Mapa de listeners: Channel Name -> Listener Instance
    private final Map<String, RedisMessageListener> listeners = new ConcurrentHashMap<>();
    // Flag volátil para controlar o estado de execução da thread principal
    private volatile boolean running = false;
    // Future representando a thread de subscrição principal
    private CompletableFuture<Void> future;
    // Instância ativa do JedisPubSub (usada para unsubscribe)
    private volatile JedisPubSub pubSub; // Tornar volátil para visibilidade entre threads
    // Tarefa agendada para enviar PINGs
    private ScheduledFuture<?> pingTask;

    // --- Gerenciamento de Listeners ---

    /**
     * Regista um listener para um canal Redis específico (usando Enum).
     * APENAS adiciona ao mapa. A subscrição ocorrerá no próximo (re)connect.
     * @param channel O canal (Enum) para ouvir.
     * @param listener A implementação que processará as mensagens.
     */
    public void registerListener(RedisChannel channel, RedisMessageListener listener) {
        Objects.requireNonNull(channel, "RedisChannel cannot be null");
        Objects.requireNonNull(listener, "RedisMessageListener cannot be null");
        String channelName = channel.getName();
        listeners.put(channelName, listener);
        LOGGER.info("Listener " + listener.getClass().getSimpleName() + " registrado (pronto para subscrição) para o canal: " + channelName);

        // Lógica de subscrição dinâmica REMOVIDA
    }

    /**
     * Regista um listener para um canal Redis específico (usando String).
     * @param channelName O nome do canal para ouvir.
     * @param listener A implementação que processará as mensagens.
     */
    public void registerListener(String channelName, RedisMessageListener listener) {
        Objects.requireNonNull(channelName, "Channel name cannot be null");
        Objects.requireNonNull(listener, "RedisMessageListener cannot be null");
        listeners.put(channelName, listener);
        LOGGER.info("Listener " + listener.getClass().getSimpleName() + " registrado (pronto para subscrição) para o canal: " + channelName);

        // Lógica de subscrição dinâmica REMOVIDA
    }

    /**
     * Remove um listener de um canal específico (usando Enum).
     * @param channel O canal do qual remover o listener.
     */
    public void unregisterListener(RedisChannel channel) {
        if (channel != null) {
            unregisterListener(channel.getName());
        }
    }

    /**
     * Remove um listener de um canal específico (por nome).
     * Tenta desinscrever dinamicamente se o subscriber estiver rodando.
     * @param channelName O nome do canal do qual remover o listener.
     */
    public synchronized void unregisterListener(String channelName) {
        if (channelName != null) {
            RedisMessageListener removedListener = listeners.remove(channelName);
            if (removedListener != null) {
                LOGGER.fine("Listener " + removedListener.getClass().getSimpleName() + " removido do mapa para o canal: " + channelName);
                // Tenta desinscrever dinamicamente (seguro fazer unsubscribe)
                JedisPubSub currentPubSub = this.pubSub; // Leitura volátil
                if (currentPubSub != null && currentPubSub.isSubscribed()) {
                    try {
                        currentPubSub.unsubscribe(channelName);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Falha ao desinscrever dinamicamente do canal " + channelName + " após desregistro.", e);
                    }
                }
            }
        }
    }

    // --- Lógica de Start/Stop e Reconexão ---

    /**
     * Inicia a thread de fundo que ouve os canais Redis registados.
     * Implementa um loop de reconexão automática em caso de perda de conexão.
     */
    public synchronized void start() {
        // Verifica se já está rodando
        if (running) {
            LOGGER.fine("RedisSubscriber já está rodando.");
            return;
        }

        running = true; // Define a flag de execução
        LOGGER.info("Iniciando loop de subscrição do RedisSubscriber...");

        // Usa TaskScheduler para rodar o loop de subscrição/reconexão de forma assíncrona
        future = TaskScheduler.runAsync(() -> {
            Jedis resource = null; // Conexão Jedis
            JedisPubSub currentPubSub = null; // Instância do handler de mensagens

            // Loop principal: continua enquanto 'running' for true
            while (running) {
                try {
                    // Pega a lista ATUAL de canais a cada iteração
                    String[] channelsArray = listeners.keySet().toArray(new String[0]);
                    if (channelsArray.length == 0) {
                        // Se não há listeners, espera antes de tentar de novo
                        // LOGGER.info("Nenhum canal para ouvir, RedisSubscriber entrando em espera...");
                        try { TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS); } catch (InterruptedException ie) {
                            LOGGER.info("Thread RedisSubscriber interrompida durante espera por canais. Parando.");
                            running = false; // Sinaliza para sair do loop
                            Thread.currentThread().interrupt(); // Restaura status de interrupção
                        }
                        continue; // Volta ao início do loop while(running)
                    }

                    LOGGER.info("Tentando conectar ao Redis e subscrever " + channelsArray.length + " canal(s)... (" + String.join(", ", channelsArray) + ")");
                    resource = RedisManager.getResource(); // Obtém conexão do pool

                    // Cria uma NOVA instância do JedisPubSub a cada tentativa de conexão
                    currentPubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            if (!running) return; // Não processa se já pediu para parar
                            RedisMessageListener listener = listeners.get(channel);
                            if (listener != null) {
                                try {
                                    listener.onMessage(channel, message);
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Erro ao processar mensagem do canal '" + channel + "' pelo listener " + listener.getClass().getSimpleName(), e);
                                }
                            } else {
                                // LOGGER.warning("Mensagem recebida para o canal '" + channel + "' mas nenhum listener encontrado.");
                            }
                        }
                        @Override public void onSubscribe(String channel, int subscribedChannels) { LOGGER.info("Inscrito no canal Redis: " + channel + " (Total: " + subscribedChannels + ")"); }
                        @Override public void onUnsubscribe(String channel, int subscribedChannels) { LOGGER.info("Desinscrito do canal Redis: " + channel + " (Total: " + subscribedChannels + ")"); }
                    };

                    // Define a instância global ANTES de iniciar o ping e subscribe
                    this.pubSub = currentPubSub;

                    // Inicia a tarefa de PING para esta conexão
                    startPingTask(currentPubSub);

                    LOGGER.info("RedisSubscriber entrando em modo de escuta...");
                    // *** CHAMADA BLOQUEANTE ***
                    // Fica aqui até a conexão cair ou unsubscribe() ser chamado
                    resource.subscribe(currentPubSub, channelsArray);

                    // Se chegou aqui, foi um unsubscribe() intencional (provavelmente vindo do stop())
                    LOGGER.info("RedisSubscriber saiu do modo de escuta (unsubscribe intencional).");
                    running = false; // Sinaliza para sair do loop while

                } catch (JedisConnectionException jce) {
                    // --- LÓGICA DE RECONEXÃO ---
                    LOGGER.log(Level.SEVERE, "Erro de conexão Redis: " + jce.getMessage() + ". Tentando reconectar em " + RECONNECT_DELAY_SECONDS + " segundos...");
                    // Limpa pubSub e para ping antes de esperar
                    this.pubSub = null;
                    stopPingTask();
                    // Fecha o recurso Jedis que falhou (se existir)
                    if (resource != null) try { resource.close(); } catch (Exception ignored) {}
                    resource = null; // Garante que resource é null para a próxima iteração

                    // Espera antes de tentar reconectar
                    try {
                        if (running) {
                            TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS);
                        }
                    } catch (InterruptedException ie) {
                        LOGGER.info("Thread RedisSubscriber interrompida durante espera para reconectar. Parando.");
                        running = false; // Sai do loop while
                        Thread.currentThread().interrupt(); // Restaura status
                    }
                    // O loop 'while(running)' continuará para a próxima tentativa

                } catch (Exception e) {
                    // Outros erros (Interrupção, etc.)
                    if (running && !(e instanceof InterruptedException) && !(e.getCause() instanceof InterruptedException)) {
                        LOGGER.log(Level.SEVERE, "Erro inesperado na thread do RedisSubscriber. Parando.", e);
                    } else if (!running) { // Se running já era false, foi intencional
                        if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException || e instanceof CancellationException || (e.getCause() instanceof CancellationException)) {
                            LOGGER.info("Thread RedisSubscriber interrompida ou cancelada (durante shutdown).");
                        } else {
                            LOGGER.log(Level.WARNING, "Exceção durante o processo de parada do RedisSubscriber.", e);
                        }
                    } else { // Running era true, mas foi interrupção
                        LOGGER.info("Thread RedisSubscriber interrompida inesperadamente enquanto 'running' era true. Parando.");
                    }
                    running = false; // Garante a saída do loop em caso de erro não tratado
                } finally {
                    // Limpeza DENTRO do loop
                    LOGGER.fine("Bloco finally da tentativa de subscrição alcançado.");
                    stopPingTask();
                    if (this.pubSub == currentPubSub) { this.pubSub = null; }
                    if (resource != null) { try { resource.close(); } catch (Exception e) { LOGGER.log(Level.WARNING, "Erro ao fechar recurso Jedis no finally do loop.", e); } }
                }
            } // Fim do loop while(running)

            // --- Limpeza Final APÓS o loop terminar ---
            LOGGER.fine("Loop principal do RedisSubscriber terminado.");
            running = false;
            stopPingTask();
            this.pubSub = null;
            if (this.future != null && !this.future.isDone() && !this.future.isCancelled()) { this.future.complete(null); }
            this.future = null;
            LOGGER.info("Thread do RedisSubscriber finalizada completamente.");

        }); // Fim do TaskScheduler.runAsync lambda

        future.exceptionally(ex -> {
            Throwable cause = ex;
            while (cause != null && cause.getCause() != null && cause != cause.getCause()) { cause = cause.getCause(); }
            if (!(cause instanceof CancellationException)) { LOGGER.log(Level.SEVERE, "Falha ao INICIAR a thread do RedisSubscriber via TaskScheduler", ex); }
            else { LOGGER.info("Inicialização da thread RedisSubscriber cancelada."); }
            running = false;
            this.future = null;
            return null;
        });
    }


    /**
     * Para a thread de subscrição de fundo e limpa os recursos.
     */
    public synchronized void stop() {
        if (!running && pubSub == null && future == null) {
            LOGGER.fine("RedisSubscriber já parado ou não iniciado.");
            return;
        }
        LOGGER.info("Parando RedisSubscriber...");
        running = false; // Sinaliza para o loop/thread parar
        stopPingTask(); // Para a tarefa de PING

        JedisPubSub currentPubSub = this.pubSub; // Leitura volátil
        if (currentPubSub != null) {
            try {
                if (currentPubSub.isSubscribed()) {
                    currentPubSub.unsubscribe();
                    LOGGER.fine("Comando unsubscribe enviado para JedisPubSub.");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao enviar comando unsubscribe durante stop()", e);
            }
            this.pubSub = null;
        }

        CompletableFuture<Void> currentFuture = this.future; // Leitura volátil
        if (currentFuture != null) {
            try {
                boolean cancelled = currentFuture.cancel(true); // Tenta interromper a thread
                LOGGER.fine("CompletableFuture cancel solicitado (interrupt=true): " + (cancelled ? "Sucesso" : "Falhou ou Já Concluído"));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao cancelar CompletableFuture durante stop()", e);
            }
            this.future = null;
        }

        LOGGER.info("Sequência de parada do RedisSubscriber concluída.");
    }

    // --- Tarefa de Ping ---

    /**
     * Inicia uma tarefa periódica para enviar PINGs via JedisPubSub.
     */
    private void startPingTask(final JedisPubSub pubSubInstance) {
        if (pingTask != null && !pingTask.isDone()) {
            return;
        }
        LOGGER.fine("Iniciando tarefa de Ping para JedisPubSub.");
        try {
            pingTask = TaskScheduler.runAsyncTimer(() -> {
                if (running && pubSubInstance != null && pubSubInstance == this.pubSub && pubSubInstance.isSubscribed()) {
                    try {
                        pubSubInstance.ping();
                    } catch (JedisConnectionException jce) {
                        LOGGER.log(Level.WARNING, "Erro de conexão ao enviar PING via JedisPubSub. Parando tarefa de ping.", jce);
                        stopPingTask();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Erro inesperado ao enviar PING via JedisPubSub. Parando tarefa de ping.", e);
                        stopPingTask();
                    }
                } else {
                    LOGGER.fine("Condições não atendidas para PING. Parando tarefa de PING.");
                    stopPingTask();
                }
            }, 1, 1, TimeUnit.MINUTES); // Delay 1 min, Repete 1 min
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao agendar a tarefa de PING do RedisSubscriber!", e);
        }
    }

    /**
     * Para a tarefa periódica de PING, se estiver ativa.
     */
    private void stopPingTask() {
        ScheduledFuture<?> currentPingTask = this.pingTask;
        if (currentPingTask != null) {
            try {
                if (!currentPingTask.isDone()) {
                    boolean cancelled = currentPingTask.cancel(false); // Não interrompe, só previne futuras
                    LOGGER.fine("Cancelamento da tarefa de PING solicitado: " + (cancelled ? "Sucesso" : "Falhou ou Já Concluído"));
                }
            } catch(Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao cancelar tarefa de PING.", e);
            } finally {
                this.pingTask = null;
            }
        }
    }
}