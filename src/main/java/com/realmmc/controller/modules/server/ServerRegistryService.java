package com.realmmc.controller.modules.server;

import com.fasterxml.jackson.databind.JsonNode; // Importar JsonNode
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.data.DefaultServer;
import com.realmmc.controller.modules.server.data.ServerInfo;
import com.realmmc.controller.modules.server.data.ServerInfoRepository;
import com.realmmc.controller.modules.server.data.ServerStatus;
import com.realmmc.controller.modules.server.data.ServerType;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.mongodb.MongoException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import redis.clients.jedis.Jedis;

/**
 * O serviço principal que gere o registo de servidores,
 * monitorização e auto-scaling.
 */
public class ServerRegistryService {

    private final Logger logger;
    private final ProxyServer proxyServer;
    private final ServerInfoRepository repository;
    private final PterodactylService pterodactylService;

    // Constantes de Scaling
    private static final double LOBBY_SCALE_UP_THRESHOLD = 0.70;
    private static final int GAME_BW_MIN_ROOMS = 0;
    private static final long SERVER_EMPTY_SHUTDOWN_MS = TimeUnit.SECONDS.toMillis(30);

    // Controlo de estado
    private final Map<String, Long> emptySinceTimestamp = new ConcurrentHashMap<>();

    // <<< NOVO: Tarefa de Health Check >>>
    private ScheduledFuture<?> healthCheckTask = null;

    public ServerRegistryService(Logger logger) {
        this.logger = logger;
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.repository = new ServerInfoRepository();
        this.pterodactylService = ServiceRegistry.getInstance().requireService(PterodactylService.class);
    }

    /**
     * Chamado quando o módulo é ativado.
     * Regista servidores estáticos e inicia as tarefas de monitorização.
     */
    public void initialize() {
        logger.info("Inicializando ServerRegistryService...");
        setupDefaultServers();
        initializeStaticServers();
        startMonitoringTask();
        startHealthCheckTask(); // <<< NOVO: Inicia a verificação de saúde >>>
    }

    /**
     * Garante que os servidores padrão definidos em DefaultServer existem no MongoDB.
     */
    private void setupDefaultServers() {
        try {
            logger.info("Sincronizando servidores padrão com o MongoDB...");
            for (DefaultServer defaultServer : DefaultServer.values()) {
                Optional<ServerInfo> existingOpt = repository.findByName(defaultServer.getName());

                if (existingOpt.isEmpty()) {
                    repository.save(defaultServer.toServerInfo());
                    logger.info("Servidor padrão '" + defaultServer.getName() + "' criado no DB.");
                } else {
                    ServerInfo existing = existingOpt.get();

                    boolean updated = false;
                    if (!existing.getMinGroup().equals(defaultServer.getMinGroup())) {
                        existing.setMinGroup(defaultServer.getMinGroup());
                        updated = true;
                    }
                    if (existing.getType() != defaultServer.getType()) {
                        existing.setType(defaultServer.getType());
                        updated = true;
                    }

                    if (updated) {
                        repository.save(existing);
                        logger.fine("Servidor padrão '" + existing.getName() + "' atualizado com defaults.");
                    }
                }
            }
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "Falha crítica ao sincronizar servidores padrão com MongoDB!", e);
        }
    }


    /**
     * Carrega todos os servidores estáticos (PERSISTENT, LOGIN, LOBBY) do DB,
     * regista-os no Velocity e tenta ligar os que estiverem offline. (Regra 2)
     */
    private void initializeStaticServers() {
        try {
            List<ServerInfo> persistentServers = repository.findByType(ServerType.PERSISTENT);
            List<ServerInfo> loginServers = repository.findByType(ServerType.LOGIN);
            List<ServerInfo> lobbyServers = repository.findByType(ServerType.LOBBY);

            List<ServerInfo> allStaticServers = new ArrayList<>();
            allStaticServers.addAll(persistentServers);
            allStaticServers.addAll(loginServers);
            allStaticServers.addAll(lobbyServers);

            logger.info("A carregar " + allStaticServers.size() + " servidores estáticos (PERSISTENT, LOGIN, LOBBY)...");

            for (ServerInfo server : allStaticServers) {
                if (server.getIp() == null || server.getPort() == 0) {
                    logger.warning("Servidor estático '" + server.getName() + "' está sem IP/Porta no DB. A ignorar.");
                    continue;
                }

                registerServerWithVelocity(server);

                if (server.getStatus() == ServerStatus.OFFLINE) {
                    logger.info("Servidor estático '" + server.getName() + "' está OFFLINE no DB. A tentar ligar...");
                    scaleUpStaticServer(server);
                }
                else if (server.getStatus() == ServerStatus.STARTING || server.getStatus() == ServerStatus.STOPPING) {
                    logger.warning("Servidor estático '" + server.getName() + "' encontrado em estado " + server.getStatus() + ". A forçar reinício.");
                    server.setStatus(ServerStatus.OFFLINE);
                    repository.save(server);
                    scaleUpStaticServer(server);
                }
                if (server.getPlayerCount() != 0) {
                    server.setPlayerCount(0);
                    repository.save(server);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao carregar servidores estáticos do MongoDB.", e);
        }
    }

    /**
     * Inicia a tarefa agendada principal de monitorização e scaling.
     */
    private void startMonitoringTask() {
        if (TaskScheduler.getAsyncExecutor() == null || TaskScheduler.getAsyncExecutor().isShutdown()) {
            logger.severe("TaskScheduler não disponível ou desligado. Não é possível iniciar a tarefa de monitorização.");
            return;
        }

        TaskScheduler.runAsyncTimer(() -> {
            try {
                checkServerScaling();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro no loop de monitorização de servidores.", e);
            }
        }, 15, 10, TimeUnit.SECONDS); // Roda a cada 10 segundos

        logger.info("Tarefa de monitorização e scaling de servidores iniciada.");
    }

    /**
     * Inicia uma tarefa assíncrona separada que verifica o estado real dos servidores
     * no Pterodactyl e atualiza o MongoDB se houver discrepância.
     */
    private void startHealthCheckTask() {
        if (healthCheckTask != null && !healthCheckTask.isDone()) {
            logger.fine("Health Check Task já está rodando.");
            return;
        }

        healthCheckTask = TaskScheduler.runAsyncTimer(() -> {
            try {
                runHealthCheck();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro crítico no loop de Health Check do Pterodactyl.", e);
            }
        }, 30, 60, TimeUnit.SECONDS); // Roda a cada 60 segundos (delay de 30s)

        logger.info("Tarefa de Health Check (Pterodactyl -> DB) iniciada.");
    }

    /**
     * Lógica do Health Check
     */
    private void runHealthCheck() {
        logger.fine("Executando Health Check (Pterodactyl -> DB)...");
        List<ServerInfo> allDbServers;
        try {
            allDbServers = repository.collection().find().into(new ArrayList<>());
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "Health Check falhou: Não foi possível ler servidores do MongoDB.", e);
            return;
        }

        for (ServerInfo server : allDbServers) {
            // Não verificamos servidores que já estão sendo desligados pelo Pterodactyl
            if (server.getStatus() == ServerStatus.STOPPING) continue;

            // Chama a API do Pterodactyl de forma assíncrona
            pterodactylService.getServerDetails(server.getPterodactylId())
                    .whenComplete((detailsOpt, ex) -> {
                        if (ex != null) {
                            logger.log(Level.WARNING, "Health Check: Falha ao obter detalhes do Pterodactyl para " + server.getName(), ex);
                            return;
                        }
                        if (detailsOpt.isEmpty()) {
                            logger.warning("Health Check: Não foram recebidos detalhes para " + server.getName() + " (API Pterodactyl falhou?).");
                            return;
                        }

                        // Parseia o status do Pterodactyl
                        ServerStatus pteroStatus = parsePteroState(detailsOpt.get());

                        // Compara com o status do MongoDB
                        if (pteroStatus != server.getStatus()) {
                            logger.info("[Health Check] Discrepância detetada para '" + server.getName() + "'. DB: " + server.getStatus() + ", Ptero: " + pteroStatus + ". Atualizando DB.");

                            // Atualiza o status no DB
                            server.setStatus(pteroStatus);
                            // Se o Ptero está offline, zera a contagem de jogadores no DB
                            if (pteroStatus == ServerStatus.OFFLINE) {
                                server.setPlayerCount(0);
                            }
                            repository.save(server);
                        } else {
                            logger.finer("Health Check: Status OK para " + server.getName() + " (" + pteroStatus + ")");
                        }
                    });
        }
    }

    /**
     * Helper para traduzir o estado do Pterodactyl para o nosso Enum
     */
    private ServerStatus parsePteroState(JsonNode details) {
        // <<< CORREÇÃO: Usar "current_state" em vez de "state" >>>
        String pteroState = details.path("attributes").path("current_state").asText("offline").toLowerCase();

        switch (pteroState) {
            case "running":
                return ServerStatus.ONLINE;
            case "starting":
                return ServerStatus.STARTING;
            case "stopping":
                return ServerStatus.STOPPING;
            case "offline":
            default:
                return ServerStatus.OFFLINE;
        }
    }


    /**
     * A lógica principal de scaling (executada periodicamente).
     */
    private void checkServerScaling() {
        try {
            List<ServerInfo> allDbServers = repository.collection().find().into(new ArrayList<>());

            int totalMaxPlayers = allDbServers.stream()
                    .filter(s -> s.getStatus() == ServerStatus.ONLINE || s.getStatus() == ServerStatus.STARTING)
                    .mapToInt(ServerInfo::getMaxPlayersVip)
                    .sum();

            try (Jedis jedis = RedisManager.getResource()) {
                jedis.setex(RedisChannel.GLOBAL_NETWORK_MAX_PLAYERS.getName(), 20, String.valueOf(totalMaxPlayers));
                logger.finer("Limite máximo da rede (" + totalMaxPlayers + ") recalculado e salvo no Redis.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Falha ao salvar limite máximo da rede no Redis.", e);
            }

            Map<String, Integer> onlinePlayerCounts = proxyServer.getAllPlayers().stream()
                    .filter(p -> p.getCurrentServer().isPresent())
                    .collect(Collectors.groupingBy(
                            p -> p.getCurrentServer().get().getServerInfo().getName(),
                            Collectors.counting()
                    ))
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue()));

            List<ServerInfo> allOnlineLobbies = allDbServers.stream()
                    .filter(s -> (s.getType() == ServerType.LOBBY || s.getType() == ServerType.LOBBY_AUTO) && s.getStatus() == ServerStatus.ONLINE)
                    .toList();

            List<ServerInfo> onlineGameBw = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.GAME_BW && s.getStatus() == ServerStatus.ONLINE)
                    .toList();

            // --- SECÇÃO 0: ATUALIZAR CONTAGEM DE JOGADORES (Regra 1) ---
            for (ServerInfo server : allDbServers) {
                boolean needsSave = false;
                int newCount = onlinePlayerCounts.getOrDefault(server.getName(), 0);
                if (server.getStatus() == ServerStatus.ONLINE) {
                    if (server.getPlayerCount() != newCount) {
                        server.setPlayerCount(newCount);
                        needsSave = true;
                    }
                }
                else if (server.getPlayerCount() != 0) {
                    server.setPlayerCount(0);
                    needsSave = true;
                }
                if (needsSave) {
                    repository.save(server);
                    logger.finer("Contagem de jogadores atualizada para '" + server.getName() + "': " + newCount);
                }
            }
            // --- FIM SECÇÃO 0 ---


            // --- SECÇÃO 1: VERIFICAÇÃO DE SERVIDORES ESTÁTICOS (Regra 2) ---
            List<ServerInfo> staticServers = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.LOBBY || s.getType() == ServerType.LOGIN || s.getType() == ServerType.PERSISTENT)
                    .toList();

            for (ServerInfo server : staticServers) {
                if (server.getStatus() == ServerStatus.OFFLINE) {
                    logger.warning("Servidor estático '" + server.getName() + "' está OFFLINE. A tentar reiniciar...");
                    scaleUpStaticServer(server); // Tenta ligar
                }
            }

            // --- SECÇÃO 2: LÓGICA DE SCALE-DOWN (Apenas Dinâmicos) (Regra 3) ---
            List<ServerInfo> dynamicServers = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.LOBBY_AUTO || s.getType() == ServerType.GAME_BW)
                    .toList();

            for (ServerInfo server : dynamicServers) {
                if (server.getStatus() != ServerStatus.ONLINE) {
                    emptySinceTimestamp.remove(server.getName());
                    continue;
                }
                int playerCount = onlinePlayerCounts.getOrDefault(server.getName(), 0);
                if (playerCount == 0) {
                    long emptySince = emptySinceTimestamp.computeIfAbsent(server.getName(), k -> System.currentTimeMillis());
                    if (System.currentTimeMillis() - emptySince > SERVER_EMPTY_SHUTDOWN_MS) {
                        logger.info("Servidor dinâmico '" + server.getName() + "' está vazio há > 30s. A desligar...");
                        scaleDownServer(server);
                        emptySinceTimestamp.remove(server.getName());
                    }
                } else {
                    emptySinceTimestamp.remove(server.getName());
                }
            }

            // --- SECÇÃO 3: LÓGICA DE SCALE-UP (Apenas Dinâmicos) (Regra 3 e 4) ---
            boolean triggerLobbyScaleUp = false;
            if (allOnlineLobbies.isEmpty()) {
                logger.warning("Não há lobbies online! (Nem mesmo o lobby-1 estático). Verifique o MongoDB.");
            } else {
                boolean allLobbiesFull = true;
                for (ServerInfo lobby : allOnlineLobbies) {
                    int playerCount = onlinePlayerCounts.getOrDefault(lobby.getName(), 0);
                    if (playerCount < (lobby.getMaxPlayers() * LOBBY_SCALE_UP_THRESHOLD)) {
                        allLobbiesFull = false;
                        break;
                    }
                }
                if (allLobbiesFull) {
                    triggerLobbyScaleUp = true;
                }
            }

            if (triggerLobbyScaleUp) {
                long startingLobbyAutoCount = allDbServers.stream()
                        .filter(s -> s.getType() == ServerType.LOBBY_AUTO && s.getStatus() == ServerStatus.STARTING)
                        .count();
                if (startingLobbyAutoCount == 0) {
                    logger.info("Todos os lobbies online estão acima de 70%. A tentar ligar um LOBBY_AUTO...");
                    scaleUpDynamicServer(ServerType.LOBBY_AUTO);
                } else {
                    logger.fine("Scaling-up de LOBBY_AUTO em pausa. " + startingLobbyAutoCount + " já em STARTING.");
                }
            }

            long startingGameBwCount = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.GAME_BW && s.getStatus() == ServerStatus.STARTING)
                    .count();
            if (onlineGameBw.size() < GAME_BW_MIN_ROOMS && startingGameBwCount == 0) {
                int needed = GAME_BW_MIN_ROOMS - onlineGameBw.size();
                logger.info("Abaixo do mínimo de " + GAME_BW_MIN_ROOMS + " salas de Jogo (BedWars). A ligar " + needed + "...");
                scaleUpDynamicServer(ServerType.GAME_BW);
            } else if (startingGameBwCount > 0) {
                logger.fine("Scaling-up de GAME_BW em pausa. " + startingGameBwCount + " já em STARTING.");
            }

        } catch (MongoException e) {
            logger.log(Level.SEVERE, "Erro de acesso ao MongoDB durante a monitorização de servidores. O ciclo atual foi ignorado.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado no loop de monitorização de servidores.", e);
        }
    }

    /**
     * Tenta encontrar um servidor DINÂMICO (LOBBY_AUTO, GAME_BW) OFFLINE e ligá-lo.
     */
    private void scaleUpDynamicServer(ServerType type) {
        Optional<ServerInfo> serverToStartOpt = repository.findByTypeAndStatus(type, ServerStatus.OFFLINE)
                .stream()
                .findFirst();

        if (serverToStartOpt.isEmpty()) {
            logger.warning("Pedido para ligar um servidor do tipo " + type + ", mas não há mais servidores OFFLINE disponíveis no DB.");
            return;
        }

        ServerInfo serverToStart = serverToStartOpt.get();

        serverToStart.setStatus(ServerStatus.STARTING);
        serverToStart.setPlayerCount(0); // Garante que começa com 0
        repository.save(serverToStart);
        logger.info("A ligar servidor dinâmico '" + serverToStart.getName() + "' (ID: " + serverToStart.getPterodactylId() + ")...");

        pterodactylService.startServer(serverToStart.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        registerServerWithVelocity(serverToStart);
                        // <<< CORREÇÃO: Não definir como ONLINE aqui, deixar o Health Check fazer isso >>>
                        logger.info("Comando 'start' enviado para '" + serverToStart.getName() + "'. Aguardando Health Check.");
                    } else {
                        logger.severe("Falha ao ligar servidor '" + serverToStart.getName() + "' via API Pterodactyl.");
                        serverToStart.setStatus(ServerStatus.OFFLINE);
                        repository.save(serverToStart);
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao ligar servidor " + serverToStart.getName(), ex);
                    serverToStart.setStatus(ServerStatus.OFFLINE);
                    repository.save(serverToStart);
                    return null;
                });
    }

    /**
     * Tenta ligar um servidor ESTÁTICO (LOBBY, LOGIN, PERSISTENT) que foi encontrado offline.
     */
    private void scaleUpStaticServer(ServerInfo server) {
        if (server.getStatus() != ServerStatus.OFFLINE) {
            server.setStatus(ServerStatus.OFFLINE);
        }

        server.setStatus(ServerStatus.STARTING);
        server.setPlayerCount(0); // Garante que começa com 0
        repository.save(server);
        logger.info("A (re)ligar servidor estático '" + server.getName() + "' (ID: " + server.getPterodactylId() + ")...");

        pterodactylService.startServer(server.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        registerServerWithVelocity(server);
                        // <<< CORREÇÃO: Não definir como ONLINE aqui, deixar o Health Check fazer isso >>>
                        logger.info("Comando 'start' enviado para servidor estático '" + server.getName() + "'. Aguardando Health Check.");
                    } else {
                        logger.severe("Falha ao (re)ligar servidor estático '" + server.getName() + "' via API.");
                        server.setStatus(ServerStatus.OFFLINE);
                        repository.save(server);
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao (re)ligar servidor estático " + server.getName(), ex);
                    server.setStatus(ServerStatus.OFFLINE);
                    repository.save(server);
                    return null;
                });
    }


    /**
     * Desliga um servidor DINÂMICO no Pterodactyl e remove-o do Velocity.
     */
    private void scaleDownServer(ServerInfo server) {
        server.setStatus(ServerStatus.STOPPING);
        server.setPlayerCount(0); // Define 0 no momento que decide desligar
        repository.save(server);

        unregisterServerFromVelocity(server.getName());
        logger.info("Servidor '" + server.getName() + "' desregistado do Velocity.");

        pterodactylService.stopServer(server.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        // Deixa o Health Check confirmar o OFFLINE
                        // server.setStatus(ServerStatus.OFFLINE);
                        // repository.save(server);
                        logger.info("Comando 'stop' enviado para '" + server.getName() + "'. Aguardando Health Check.");
                    } else {
                        logger.severe("Falha ao desligar servidor '" + server.getName() + "' via API Pterodactyl. A reverter status para ONLINE.");
                        server.setStatus(ServerStatus.ONLINE);
                        repository.save(server);
                        registerServerWithVelocity(server);
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao desligar servidor " + server.getName(), ex);
                    server.setStatus(ServerStatus.ONLINE); // Reverte
                    repository.save(server);
                    registerServerWithVelocity(server);
                    return null;
                });
    }


    /**
     * Regista um servidor no Velocity em tempo real.
     */
    private void registerServerWithVelocity(ServerInfo serverInfo) {
        InetSocketAddress address = new InetSocketAddress(serverInfo.getIp(), serverInfo.getPort());

        com.velocitypowered.api.proxy.server.ServerInfo velocityInfo =
                new com.velocitypowered.api.proxy.server.ServerInfo(serverInfo.getName(), address);

        if (proxyServer.getServer(serverInfo.getName()).isEmpty()) {
            proxyServer.registerServer(velocityInfo);
            logger.info("Servidor '" + serverInfo.getName() + "' adicionado ao Velocity runtime.");
        }
    }

    /**
     * Remove um servidor do Velocity em tempo real.
     */
    public void unregisterServerFromVelocity(String serverName) {
        Optional<RegisteredServer> server = proxyServer.getServer(serverName);
        if (server.isPresent()) {
            proxyServer.unregisterServer(server.get().getServerInfo());
            logger.finer("Servidor '" + serverName + "' removido do Velocity runtime.");
        }
    }

    /**
     * Encontra o melhor lobby disponível para um jogador se ligar.
     * Dá prioridade a lobbies online que não estejam cheios.
     * @return Optional contendo o RegisteredServer para onde enviar o jogador.
     */
    public Optional<RegisteredServer> getBestLobby() {
        List<ServerInfo> onlineLobbies = new ArrayList<>();
        onlineLobbies.addAll(repository.findByTypeAndStatus(ServerType.LOBBY, ServerStatus.ONLINE));
        onlineLobbies.addAll(repository.findByTypeAndStatus(ServerType.LOBBY_AUTO, ServerStatus.ONLINE));

        if (onlineLobbies.isEmpty()) {
            logger.warning("[getBestLobby] Um jogador tentou encontrar um lobby, mas nenhum está ONLINE.");
            scaleUpDynamicServer(ServerType.LOBBY_AUTO);
            return Optional.empty();
        }

        RegisteredServer bestChoice = null;
        int bestChoicePlayerCount = Integer.MAX_VALUE;

        for (ServerInfo lobbyInfo : onlineLobbies) {
            Optional<RegisteredServer> registeredServerOpt = proxyServer.getServer(lobbyInfo.getName());
            if (registeredServerOpt.isEmpty()) {
                continue;
            }

            RegisteredServer registeredServer = registeredServerOpt.get();

            try {
                int playerCount = registeredServer.getPlayersConnected().size();

                if (playerCount < lobbyInfo.getMaxPlayers()) {
                    if (playerCount < bestChoicePlayerCount) {
                        bestChoice = registeredServer;
                        bestChoicePlayerCount = playerCount;
                    }
                }

            } catch (Exception e) {
                logger.warning("Não foi possível obter contagem de jogadores para " + lobbyInfo.getName() + ": " + e.getMessage());
            }
        }

        if (bestChoice == null) {
            logger.warning("[getBestLobby] Todos os lobbies ONLINE (" + onlineLobbies.size() + ") estão cheios ou inacessíveis.");
            scaleUpDynamicServer(ServerType.LOBBY_AUTO);
        }

        return Optional.ofNullable(bestChoice);
    }

    /**
     * Chamado quando o módulo é desativado.
     */
    public void shutdown() {
        // <<< NOVO: Para a tarefa de Health Check >>>
        stopHealthCheckTask();
        logger.info("ServerRegistryService finalizado.");
    }

    /**
     * <<< NOVO: Método para parar a tarefa de Health Check >>>
     */
    private void stopHealthCheckTask() {
        if (healthCheckTask != null) {
            try {
                healthCheckTask.cancel(false);
                logger.info("Tarefa de Health Check (Pterodactyl) parada.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao parar Health Check Task", e);
            } finally {
                healthCheckTask = null;
            }
        }
    }
}