package com.realmmc.controller.modules.server;

import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.data.*;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServerRegistryService {

    private final Logger logger;
    private final ProxyServer proxyServer;
    private final ServerInfoRepository repository;
    private final PterodactylService pterodactylService;

    private static final int MIN_IDLE_LOBBIES = 1;
    private static final int MAX_LOBBIES = 7;
    private static final double SERVER_FULL_THRESHOLD = 0.70;
    private static final long SERVER_EMPTY_SHUTDOWN_MS = TimeUnit.SECONDS.toMillis(60);

    private static final long STARTUP_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);

    private final Map<String, Long> emptySinceTimestamp = new ConcurrentHashMap<>();
    private final Map<String, Long> startingTimestamp = new ConcurrentHashMap<>();

    private ScheduledFuture<?> healthCheckTask = null;

    public ServerRegistryService(Logger logger) {
        this.logger = logger;
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.repository = new ServerInfoRepository();
        this.pterodactylService = ServiceRegistry.getInstance().requireService(PterodactylService.class);
    }

    public void initialize() {
        logger.info("[ServerRegistry] Initializing ServerRegistryService (Final Version - AutoScaler & Beautifier)...");
        setupDefaultServers();
        initializeStaticServers();
        startHealthCheckAndScalingTask();
    }


    public void updateServerHeartbeat(String serverName, ServerStatus status, GameState gameState, String mapName, boolean canShutdown, int players) {
        TaskScheduler.runAsync(() -> {
            try {
                Optional<ServerInfo> opt = repository.findByName(serverName);
                if (opt.isPresent()) {
                    ServerInfo server = opt.get();
                    boolean changed = false;

                    if (server.getStatus() != status) {
                        server.setStatus(status);
                        changed = true;

                        if (status == ServerStatus.ONLINE) {
                            startingTimestamp.remove(serverName);
                            if (proxyServer.getServer(serverName).isEmpty()) {
                                registerServerWithVelocity(server);
                            }
                        }
                    }

                    if (server.getGameState() != gameState) {
                        server.setGameState(gameState);
                        changed = true;
                    }

                    if (!Objects.equals(server.getMapName(), mapName)) {
                        server.setMapName(mapName);
                        changed = true;
                    }

                    if (server.isCanShutdown() != canShutdown) {
                        server.setCanShutdown(canShutdown);
                        changed = true;
                    }

                    if (players >= 0 && server.getPlayerCount() != players) {
                        server.setPlayerCount(players);
                        changed = true;
                    }

                    if (changed) {
                        repository.save(server);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao atualizar heartbeat para " + serverName, e);
            }
        });
    }

    private void setupDefaultServers() {
        try {
            logger.info("[ServerRegistry] Synchronizing default servers with MongoDB...");
            for (DefaultServer defaultServer : DefaultServer.values()) {
                Optional<ServerInfo> existingOpt = repository.findByName(defaultServer.getName());
                ServerInfo defaultInfo = defaultServer.toServerInfo();
                if (existingOpt.isEmpty()) {
                    repository.save(defaultInfo);
                    logger.info("[ServerRegistry] Default server '" + defaultInfo.getName() + "' created in DB.");
                } else {
                    ServerInfo existing = existingOpt.get();
                    if (!existing.getIp().equals(defaultInfo.getIp()) || existing.getPort() != defaultInfo.getPort()) {
                        existing.setIp(defaultInfo.getIp());
                        existing.setPort(defaultInfo.getPort());
                        repository.save(existing);
                    }
                }
            }
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "[ServerRegistry] Critical failure synchronizing default servers with MongoDB!", e);
        }
    }

    private void initializeStaticServers() {
        try {
            List<ServerInfo> allDbServers = repository.collection().find().into(new ArrayList<>());
            List<ServerInfo> staticServersToStart = allDbServers.stream()
                    .filter(s -> isStaticDefault(s.getName()))
                    .toList();

            for (ServerInfo server : staticServersToStart) {
                if (server.getIp() != null && server.getPort() != 0 && !server.getIp().equals("0.0.0.0")) {
                    registerServerWithVelocity(server);
                }
                if (server.getStatus() != ServerStatus.ONLINE && server.getStatus() != ServerStatus.STARTING) {
                    scaleUpStaticServer(server);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ServerRegistry] Failed to load static servers from MongoDB.", e);
        }
    }

    private void startHealthCheckAndScalingTask() {
        if (healthCheckTask != null && !healthCheckTask.isDone()) return;

        healthCheckTask = TaskScheduler.runAsyncTimer(() -> {
            try {
                long now = System.currentTimeMillis();
                startingTimestamp.entrySet().removeIf(entry -> (now - entry.getValue()) > STARTUP_TIMEOUT_MS * 2);

                runHealthCheck();
                checkServerScaling();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ServerRegistry] Critical error in combined Health/Scaling Check loop.", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void runHealthCheck() {
        List<ServerInfo> allDbServers;
        try {
            allDbServers = repository.collection().find().into(new ArrayList<>());
        } catch (MongoException e) {
            return;
        }

        for (ServerInfo server : allDbServers) {
            if ((isStaticDefault(server.getName()) && server.getStatus() == ServerStatus.STOPPING)
                    || server.getPterodactylId() == null
                    || server.getPterodactylId().equals("NOT_SET")) continue;

            if (server.getStatus() == ServerStatus.STARTING && !isStaticDefault(server.getName())) {
                Long startData = startingTimestamp.get(server.getName());
                if (startData != null && (System.currentTimeMillis() - startData) > STARTUP_TIMEOUT_MS) {
                    logger.warning("[Watchdog] Servidor " + server.getName() + " excedeu tempo limite de inicialização (" + (STARTUP_TIMEOUT_MS/1000) + "s). Deletando...");
                    startingTimestamp.remove(server.getName());
                    performServerDeletion(server);
                    continue;
                }
            }

            pterodactylService.getServerDetails(server.getPterodactylId())
                    .whenComplete((detailsOpt, ex) -> {
                        if (ex != null || detailsOpt.isEmpty()) {
                            if (!isStaticDefault(server.getName()) && detailsOpt.isEmpty()) {
                                logger.warning("[HealthCheck] Servidor dinâmico " + server.getName() + " órfão. Removendo do DB.");
                                repository.delete(Filters.eq("_id", server.getName()));
                                unregisterServerFromVelocity(server.getName());
                            }
                            return;
                        }

                        ServerStatus pteroStatus = parsePteroState(detailsOpt.get());
                        ServerStatus dbStatus = server.getStatus();

                        if (dbStatus == ServerStatus.STARTING && pteroStatus == ServerStatus.OFFLINE) {
                            logger.fine("[HealthCheck] Servidor " + server.getName() + " consta STARTING no DB mas OFFLINE no Painel. Re-enviando start...");
                            pterodactylService.startServer(server.getPterodactylId());
                            return;
                        }

                        if (pteroStatus == ServerStatus.OFFLINE) {
                            if (dbStatus != ServerStatus.OFFLINE && dbStatus != ServerStatus.STARTING) {
                                server.setStatus(ServerStatus.OFFLINE);
                                server.setPlayerCount(0);
                                repository.save(server);
                                unregisterServerFromVelocity(server.getName());
                            }
                            if (!isStaticDefault(server.getName()) && dbStatus == ServerStatus.STOPPING) {
                                performServerDeletion(server);
                            }
                        } else if (pteroStatus == ServerStatus.ONLINE && dbStatus != ServerStatus.ONLINE) {
                            server.setStatus(ServerStatus.ONLINE);
                            repository.save(server);
                            startingTimestamp.remove(server.getName());
                            registerServerWithVelocity(server);
                        }
                    });
        }
    }

    private ServerStatus parsePteroState(com.fasterxml.jackson.databind.JsonNode details) {
        String pteroState = details.path("attributes").path("current_state").asText("offline").toLowerCase();
        return switch (pteroState) {
            case "running" -> ServerStatus.ONLINE;
            case "starting" -> ServerStatus.STARTING;
            case "stopping" -> ServerStatus.STOPPING;
            default -> ServerStatus.OFFLINE;
        };
    }

    private void checkServerScaling() {
        try {
            List<ServerInfo> allDbServers = repository.collection().find().into(new ArrayList<>());

            int totalMaxPlayers = allDbServers.stream()
                    .filter(s -> s.getStatus() == ServerStatus.ONLINE)
                    .mapToInt(ServerInfo::getMaxPlayersVip)
                    .sum();
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.setex(RedisChannel.GLOBAL_NETWORK_MAX_PLAYERS.getName(), 20, String.valueOf(Math.max(500, totalMaxPlayers)));
            } catch (Exception ignored) {}

            Map<String, Integer> onlineCounts = proxyServer.getAllPlayers().stream()
                    .filter(p -> p.getCurrentServer().isPresent())
                    .collect(Collectors.groupingBy(p -> p.getCurrentServer().get().getServerInfo().getName(), Collectors.counting()))
                    .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue()));

            List<ServerInfo> lobbies = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.LOBBY)
                    .toList();

            long startingLobbies = lobbies.stream().filter(s -> s.getStatus() == ServerStatus.STARTING).count();
            if (startingLobbies > 0) {
                return;
            }

            long activeLobbies = lobbies.stream().filter(s -> s.getStatus() == ServerStatus.ONLINE).count();
            long totalLobbies = activeLobbies;

            long availableLobbies = lobbies.stream()
                    .filter(s -> s.getStatus() == ServerStatus.ONLINE)
                    .filter(s -> onlineCounts.getOrDefault(s.getName(), 0) < (s.getMaxPlayers() * SERVER_FULL_THRESHOLD))
                    .count();

            if (totalLobbies < MAX_LOBBIES) {
                if (availableLobbies < MIN_IDLE_LOBBIES) {
                    logger.info("[AutoScaler] Warm Pool baixo (" + availableLobbies + " disponíveis). Criando novo LOBBY...");
                    scaleUpNewServer(ServerType.LOBBY);
                    return;
                }
            }

            if (availableLobbies > MIN_IDLE_LOBBIES) {
                for (ServerInfo server : lobbies) {
                    if (isStaticDefault(server.getName())) continue;

                    if (server.getStatus() == ServerStatus.ONLINE) {
                        int count = onlineCounts.getOrDefault(server.getName(), 0);

                        if (count == 0) {
                            long emptySince = emptySinceTimestamp.computeIfAbsent(server.getName(), k -> System.currentTimeMillis());

                            if (System.currentTimeMillis() - emptySince > SERVER_EMPTY_SHUTDOWN_MS) {
                                if (server.isCanShutdown()) {
                                    logger.info("[AutoScaler] Servidor " + server.getName() + " vazio e excedente. Iniciando deleção...");
                                    scaleDownDynamicServer(server);
                                    emptySinceTimestamp.remove(server.getName());
                                    return;
                                }
                            }
                        } else {
                            emptySinceTimestamp.remove(server.getName());
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in scaling logic", e);
        }
    }

    private void scaleUpNewServer(ServerType type) {
        String serverName = findNextAvailableLobbyName();
        if (serverName == null) {
            logger.warning("[AutoScaler] Limite de nomes atingido ou erro ao buscar nome.");
            return;
        }

        String displayName = formatDisplayName(serverName);

        ServerInfo tempInfo = ServerInfo.builder()
                .name(serverName)
                .displayName(displayName)
                .type(type)
                .status(ServerStatus.STARTING)
                .build();
        repository.save(tempInfo);

        startingTimestamp.put(serverName, System.currentTimeMillis());

        pterodactylService.createPterodactylServer(serverName, type)
                .thenAccept(infoOpt -> {
                    if (infoOpt.isPresent()) {
                        ServerInfo info = infoOpt.get();
                        info.setDisplayName(formatDisplayName(info.getName()));
                        info.setStatus(ServerStatus.STARTING);
                        repository.save(info);

                        logger.info("[AutoScaler] Servidor criado (" + info.getPterodactylId() + "). Agendando start agressivo...");
                        scheduleStart(info.getPterodactylId(), 0);
                    } else {
                        logger.warning("[AutoScaler] Falha ao criar no Pterodactyl. Limpando DB.");
                        repository.delete(Filters.eq("_id", serverName));
                        startingTimestamp.remove(serverName);
                    }
                });
    }

    private void scheduleStart(String pteroId, int attempts) {
        if (attempts > 10) {
            logger.warning("[AutoScaler] Start agressivo finalizado para " + pteroId + ". O HealthCheck assumirá se necessário.");
            return;
        }

        TaskScheduler.runAsyncLater(() -> {
            pterodactylService.getServerDetails(pteroId).thenAccept(jsonOpt -> {
                if (jsonOpt.isPresent()) {
                    String state = jsonOpt.get().path("attributes").path("current_state").asText();
                    boolean isInstalling = jsonOpt.get().path("attributes").path("is_installing").asBoolean();

                    if (isInstalling) {
                        scheduleStart(pteroId, attempts + 1);
                    } else if (!state.equals("running") && !state.equals("starting")) {
                        logger.info("[AutoScaler] Enviando comando START para " + pteroId + " (Tentativa " + (attempts + 1) + ")");
                        pterodactylService.startServer(pteroId);
                        scheduleStart(pteroId, attempts + 1);
                    }
                }
            });
        }, 5, TimeUnit.SECONDS);
    }

    private void scaleDownDynamicServer(ServerInfo server) {
        server.setStatus(ServerStatus.STOPPING);
        repository.save(server);
        unregisterServerFromVelocity(server.getName());
        performServerDeletion(server);
    }

    private void performServerDeletion(ServerInfo server) {
        if (server.getInternalPteroId() <= 0) {
            repository.delete(Filters.eq("_id", server.getName()));
            return;
        }

        pterodactylService.deletePterodactylServer(server.getInternalPteroId())
                .thenAccept(success -> {
                    if (success) {
                        logger.info("[AutoScaler] Servidor " + server.getName() + " deletado com sucesso da API.");
                        repository.delete(Filters.eq("_id", server.getName()));
                        startingTimestamp.remove(server.getName());
                    } else {
                        logger.warning("[AutoScaler] Falha ao deletar " + server.getName() + " da API. Tentaremos novamente no HealthCheck.");
                    }
                });
    }

    public boolean isStaticDefault(String serverName) {
        if (serverName == null) return false;
        for (DefaultServer ds : DefaultServer.values()) {
            if (ds.getName().equalsIgnoreCase(serverName)) return true;
        }
        return false;
    }

    private String findNextAvailableLobbyName() {
        for (int i = 1; i <= 100; i++) {
            String name = "lobby-" + i;
            if (repository.findByName(name).isEmpty()) return name;
        }
        return null;
    }

    private String formatDisplayName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return internalName;
        return internalName.substring(0, 1).toUpperCase() + internalName.substring(1).replace("-", " ");
    }

    private void scaleUpStaticServer(ServerInfo server) {
        server.setStatus(ServerStatus.STARTING);
        repository.save(server);
        if (server.getPterodactylId() != null && !server.getPterodactylId().equals("NOT_SET")) {
            pterodactylService.startServer(server.getPterodactylId());
        }
    }

    private void registerServerWithVelocity(ServerInfo serverInfo) {
        if (serverInfo.getIp() == null || serverInfo.getPort() == 0) return;

        Optional<RegisteredServer> existing = proxyServer.getServer(serverInfo.getName());
        if (existing.isPresent()) {
            if (!existing.get().getServerInfo().getAddress().getAddress().getHostAddress().equals(serverInfo.getIp()) ||
                    existing.get().getServerInfo().getAddress().getPort() != serverInfo.getPort()) {
                proxyServer.unregisterServer(existing.get().getServerInfo());
            } else {
                return;
            }
        }

        InetSocketAddress address = new InetSocketAddress(serverInfo.getIp(), serverInfo.getPort());
        com.velocitypowered.api.proxy.server.ServerInfo vInfo = new com.velocitypowered.api.proxy.server.ServerInfo(serverInfo.getName(), address);

        proxyServer.registerServer(vInfo);
        logger.info("[ServerRegistry] Registered into Velocity: " + serverInfo.getName() + " (" + address + ")");
    }

    public void unregisterServerFromVelocity(String serverName) {
        proxyServer.getServer(serverName).ifPresent(s -> proxyServer.unregisterServer(s.getServerInfo()));
    }

    public Optional<RegisteredServer> getBestLobby() {
        return repository.findByTypeAndStatus(ServerType.LOBBY, ServerStatus.ONLINE).stream()
                .sorted(Comparator.comparingInt(ServerInfo::getPlayerCount))
                .findFirst()
                .flatMap(info -> proxyServer.getServer(info.getName()));
    }

    public void shutdown() {
        if (healthCheckTask != null) healthCheckTask.cancel(true);
    }
}