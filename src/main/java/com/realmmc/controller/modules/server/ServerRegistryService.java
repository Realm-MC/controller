package com.realmmc.controller.modules.server;

import com.fasterxml.jackson.databind.JsonNode;
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

public class ServerRegistryService {

    private final Logger logger;
    private final ProxyServer proxyServer;
    private final ServerInfoRepository repository;
    private final PterodactylService pterodactylService;

    private static final double LOBBY_SCALE_UP_THRESHOLD = 0.70;
    private static final int GAME_BW_MIN_ROOMS = 0;
    private static final long SERVER_EMPTY_SHUTDOWN_MS = TimeUnit.SECONDS.toMillis(30);

    private final Map<String, Long> emptySinceTimestamp = new ConcurrentHashMap<>();

    private ScheduledFuture<?> healthCheckTask = null;

    public ServerRegistryService(Logger logger) {
        this.logger = logger;
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.repository = new ServerInfoRepository();
        this.pterodactylService = ServiceRegistry.getInstance().requireService(PterodactylService.class);
    }

    public void initialize() {
        logger.info("[ServerRegistry] Initializing ServerRegistryService...");
        setupDefaultServers();
        initializeStaticServers();
        startMonitoringTask();
        startHealthCheckTask();
    }

    private void setupDefaultServers() {
        try {
            logger.info("[ServerRegistry] Synchronizing default servers with MongoDB...");
            for (DefaultServer defaultServer : DefaultServer.values()) {
                Optional<ServerInfo> existingOpt = repository.findByName(defaultServer.getName());

                if (existingOpt.isEmpty()) {
                    repository.save(defaultServer.toServerInfo());
                    logger.info("[ServerRegistry] Default server '" + defaultServer.getName() + "' created in DB.");
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
                        logger.fine("[ServerRegistry] Default server '" + existing.getName() + "' updated with defaults.");
                    }
                }
            }
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "[ServerRegistry] Critical failure synchronizing default servers with MongoDB!", e);
        }
    }

    private void initializeStaticServers() {
        try {
            List<ServerInfo> persistentServers = repository.findByType(ServerType.PERSISTENT);
            List<ServerInfo> loginServers = repository.findByType(ServerType.LOGIN);
            List<ServerInfo> lobbyServers = repository.findByType(ServerType.LOBBY);

            List<ServerInfo> allStaticServers = new ArrayList<>();
            allStaticServers.addAll(persistentServers);
            allStaticServers.addAll(loginServers);
            allStaticServers.addAll(lobbyServers);

            logger.info("[ServerRegistry] Loading " + allStaticServers.size() + " static servers (PERSISTENT, LOGIN, LOBBY)...");

            for (ServerInfo server : allStaticServers) {
                if (server.getIp() == null || server.getPort() == 0) {
                    logger.warning("[ServerRegistry] Static server '" + server.getName() + "' is missing IP/Port in DB. Skipping.");
                    continue;
                }

                registerServerWithVelocity(server);

                if (server.getStatus() == ServerStatus.OFFLINE) {
                    logger.info("[ServerRegistry] Static server '" + server.getName() + "' is OFFLINE in DB. Attempting to start...");
                    scaleUpStaticServer(server);
                }
                else if (server.getStatus() == ServerStatus.STARTING || server.getStatus() == ServerStatus.STOPPING) {
                    logger.warning("[ServerRegistry] Static server '" + server.getName() + "' found in state " + server.getStatus() + ". Forcing restart.");
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
            logger.log(Level.SEVERE, "[ServerRegistry] Failed to load static servers from MongoDB.", e);
        }
    }

    private void startMonitoringTask() {
        if (TaskScheduler.getAsyncExecutor() == null || TaskScheduler.getAsyncExecutor().isShutdown()) {
            logger.severe("[ServerRegistry] TaskScheduler not available. Cannot start monitoring task.");
            return;
        }

        TaskScheduler.runAsyncTimer(() -> {
            try {
                checkServerScaling();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ServerRegistry] Error in server monitoring loop.", e);
            }
        }, 15, 10, TimeUnit.SECONDS);

        logger.info("[ServerRegistry] Server monitoring and scaling task started.");
    }

    private void startHealthCheckTask() {
        if (healthCheckTask != null && !healthCheckTask.isDone()) {
            logger.fine("[ServerRegistry] Health Check Task is already running.");
            return;
        }

        healthCheckTask = TaskScheduler.runAsyncTimer(() -> {
            try {
                runHealthCheck();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ServerRegistry] Critical error in Pterodactyl Health Check loop.", e);
            }
        }, 30, 60, TimeUnit.SECONDS);

        logger.info("[ServerRegistry] Health Check Task (Pterodactyl -> DB) started.");
    }

    private void runHealthCheck() {
        logger.fine("[ServerRegistry] Executing Health Check (Pterodactyl -> DB)...");
        List<ServerInfo> allDbServers;
        try {
            allDbServers = repository.collection().find().into(new ArrayList<>());
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "[ServerRegistry] Health Check failed: Could not read servers from MongoDB.", e);
            return;
        }

        for (ServerInfo server : allDbServers) {
            if (server.getStatus() == ServerStatus.STOPPING) continue;

            pterodactylService.getServerDetails(server.getPterodactylId())
                    .whenComplete((detailsOpt, ex) -> {
                        if (ex != null) {
                            logger.log(Level.WARNING, "[ServerRegistry] Health Check: Failed to get Pterodactyl details for " + server.getName(), ex);
                            return;
                        }
                        if (detailsOpt.isEmpty()) {
                            logger.warning("[ServerRegistry] Health Check: No details received for " + server.getName() + " (Pterodactyl API failed?).");
                            return;
                        }

                        ServerStatus pteroStatus = parsePteroState(detailsOpt.get());
                        ServerStatus oldStatus = server.getStatus();

                        if (pteroStatus != oldStatus) {
                            logger.info("[ServerRegistry] [Health Check] Discrepancy detected for '" + server.getName() + "'. DB: " + oldStatus + ", Ptero: " + pteroStatus + ". Updating DB.");

                            server.setStatus(pteroStatus);
                            if (pteroStatus == ServerStatus.OFFLINE) {
                                server.setPlayerCount(0);
                                unregisterServerFromVelocity(server.getName());
                            }
                            repository.save(server);

                            if (pteroStatus == ServerStatus.ONLINE) {
                                registerServerWithVelocity(server);
                                logger.info("[ServerRegistry] [Health Check] Forçando re-registro no Velocity para '" + server.getName() + "' (ONLINE).");
                            }
                        } else {
                            logger.finer("[ServerRegistry] Health Check: Status OK for " + server.getName() + " (" + pteroStatus + ")");
                        }
                    });
        }
    }

    private ServerStatus parsePteroState(JsonNode details) {
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

    private void checkServerScaling() {
        try {
            List<ServerInfo> allDbServers = repository.collection().find().into(new ArrayList<>());

            int totalMaxPlayers = allDbServers.stream()
                    .filter(s -> s.getStatus() == ServerStatus.ONLINE || s.getStatus() == ServerStatus.STARTING)
                    .mapToInt(ServerInfo::getMaxPlayersVip)
                    .sum();

            try (Jedis jedis = RedisManager.getResource()) {
                jedis.setex(RedisChannel.GLOBAL_NETWORK_MAX_PLAYERS.getName(), 20, String.valueOf(totalMaxPlayers));
                logger.finer("[ServerRegistry] Network max players (" + totalMaxPlayers + ") recalculated and saved to Redis.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[ServerRegistry] Failed to save global network max players to Redis.", e);
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
                    logger.finer("[ServerRegistry] Player count updated for '" + server.getName() + "': " + newCount);
                }
            }

            List<ServerInfo> staticServers = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.LOBBY || s.getType() == ServerType.LOGIN || s.getType() == ServerType.PERSISTENT)
                    .toList();

            for (ServerInfo server : staticServers) {
                if (server.getStatus() == ServerStatus.OFFLINE) {
                    logger.warning("[ServerRegistry] Static server '" + server.getName() + "' is OFFLINE. Attempting restart...");
                    scaleUpStaticServer(server);
                }
            }

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
                        logger.info("[ServerRegistry] Dynamic server '" + server.getName() + "' empty for > 30s. Shutting down...");
                        scaleDownServer(server);
                        emptySinceTimestamp.remove(server.getName());
                    }
                } else {
                    emptySinceTimestamp.remove(server.getName());
                }
            }

            boolean triggerLobbyScaleUp = false;
            if (allOnlineLobbies.isEmpty()) {
                logger.warning("[ServerRegistry] No online lobbies! (Not even static lobby-1). Check MongoDB.");
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
                    logger.info("[ServerRegistry] All online lobbies are over 70%. Attempting to start a LOBBY_AUTO...");
                    scaleUpDynamicServer(ServerType.LOBBY_AUTO);
                } else {
                    logger.fine("[ServerRegistry] LOBBY_AUTO scaling-up paused. " + startingLobbyAutoCount + " already STARTING.");
                }
            }

            long startingGameBwCount = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.GAME_BW && s.getStatus() == ServerStatus.STARTING)
                    .count();
            if (onlineGameBw.size() < GAME_BW_MIN_ROOMS && startingGameBwCount == 0) {
                int needed = GAME_BW_MIN_ROOMS - onlineGameBw.size();
                logger.info("[ServerRegistry] Below minimum of " + GAME_BW_MIN_ROOMS + " Game rooms (BedWars). Starting " + needed + "...");
                scaleUpDynamicServer(ServerType.GAME_BW);
            } else if (startingGameBwCount > 0) {
                logger.fine("[ServerRegistry] GAME_BW scaling-up paused. " + startingGameBwCount + " already STARTING.");
            }

        } catch (MongoException e) {
            logger.log(Level.SEVERE, "[ServerRegistry] MongoDB access error during server monitoring. Current cycle skipped.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ServerRegistry] Unexpected error in server monitoring loop.", e);
        }
    }

    private void scaleUpDynamicServer(ServerType type) {
        Optional<ServerInfo> serverToStartOpt = repository.findByTypeAndStatus(type, ServerStatus.OFFLINE)
                .stream()
                .findFirst();

        if (serverToStartOpt.isEmpty()) {
            logger.warning("[ServerRegistry] Request to start server type " + type + ", but no more OFFLINE servers available in DB.");
            return;
        }

        ServerInfo serverToStart = serverToStartOpt.get();

        serverToStart.setStatus(ServerStatus.STARTING);
        serverToStart.setPlayerCount(0);
        repository.save(serverToStart);
        logger.info("[ServerRegistry] Starting dynamic server '" + serverToStart.getName() + "' (ID: " + serverToStart.getPterodactylId() + ")...");

        pterodactylService.startServer(serverToStart.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        logger.info("[ServerRegistry] 'start' command sent for '" + serverToStart.getName() + "'. Awaiting Health Check.");
                    } else {
                        logger.severe("[ServerRegistry] Failed to start server '" + serverToStart.getName() + "' via Pterodactyl API.");
                        serverToStart.setStatus(ServerStatus.OFFLINE);
                        repository.save(serverToStart);
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "[ServerRegistry] Exception while starting server " + serverToStart.getName(), ex);
                    serverToStart.setStatus(ServerStatus.OFFLINE);
                    repository.save(serverToStart);
                    return null;
                });
    }

    private void scaleUpStaticServer(ServerInfo server) {
        if (server.getStatus() != ServerStatus.OFFLINE) {
            server.setStatus(ServerStatus.OFFLINE);
        }

        server.setStatus(ServerStatus.STARTING);
        server.setPlayerCount(0);
        repository.save(server);
        logger.info("[ServerRegistry] Re-starting static server '" + server.getName() + "' (ID: " + server.getPterodactylId() + ")...");

        pterodactylService.startServer(server.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        logger.info("[ServerRegistry] 'start' command sent for static server '" + server.getName() + "'. Awaiting Health Check.");
                    } else {
                        logger.severe("[ServerRegistry] Failed to re-start static server '" + server.getName() + "' via API.");
                        server.setStatus(ServerStatus.OFFLINE);
                        repository.save(server);
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "[ServerRegistry] Exception while re-starting static server " + server.getName(), ex);
                    server.setStatus(ServerStatus.OFFLINE);
                    repository.save(server);
                    return null;
                });
    }

    private void scaleDownServer(ServerInfo server) {
        server.setStatus(ServerStatus.STOPPING);
        server.setPlayerCount(0);
        repository.save(server);

        unregisterServerFromVelocity(server.getName());
        logger.info("[ServerRegistry] Server '" + server.getName() + "' unregistered from Velocity.");

        pterodactylService.stopServer(server.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        logger.info("[ServerRegistry] 'stop' command sent for '" + server.getName() + "'. Awaiting Health Check.");
                    } else {
                        logger.severe("[ServerRegistry] Failed to stop server '" + server.getName() + "' via Pterodactyl API. Reverting status to ONLINE.");
                        server.setStatus(ServerStatus.ONLINE);
                        repository.save(server);
                        registerServerWithVelocity(server);
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "[ServerRegistry] Exception while stopping server " + server.getName(), ex);
                    server.setStatus(ServerStatus.ONLINE);
                    repository.save(server);
                    registerServerWithVelocity(server);
                    return null;
                });
    }


    private void registerServerWithVelocity(ServerInfo serverInfo) {
        InetSocketAddress address = new InetSocketAddress(serverInfo.getIp(), serverInfo.getPort());

        com.velocitypowered.api.proxy.server.ServerInfo velocityInfo =
                new com.velocitypowered.api.proxy.server.ServerInfo(serverInfo.getName(), address);

        if (proxyServer.getServer(serverInfo.getName()).isPresent()) {
            proxyServer.unregisterServer(proxyServer.getServer(serverInfo.getName()).get().getServerInfo());
            logger.fine("[ServerRegistry] Server '" + serverInfo.getName() + "' removed from Velocity runtime for update.");
        }

        proxyServer.registerServer(velocityInfo);
        logger.info("[ServerRegistry] Server '" + serverInfo.getName() + "' added to Velocity runtime.");
    }

    public void unregisterServerFromVelocity(String serverName) {
        Optional<RegisteredServer> server = proxyServer.getServer(serverName);
        if (server.isPresent()) {
            proxyServer.unregisterServer(server.get().getServerInfo());
            logger.finer("[ServerRegistry] Server '" + serverName + "' removed from Velocity runtime.");
        }
    }

    public Optional<RegisteredServer> getBestLobby() {
        List<ServerInfo> onlineLobbies = new ArrayList<>();
        onlineLobbies.addAll(repository.findByTypeAndStatus(ServerType.LOBBY, ServerStatus.ONLINE));
        onlineLobbies.addAll(repository.findByTypeAndStatus(ServerType.LOBBY_AUTO, ServerStatus.ONLINE));

        if (onlineLobbies.isEmpty()) {
            logger.warning("[ServerRegistry] [getBestLobby] Player tried to find a lobby, but none are ONLINE.");
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
                logger.warning("[ServerRegistry] Could not get player count for " + lobbyInfo.getName() + ": " + e.getMessage());
            }
        }

        if (bestChoice == null) {
            logger.warning("[ServerRegistry] [getBestLobby] All ONLINE lobbies (" + onlineLobbies.size() + ") are full or inaccessible.");
            scaleUpDynamicServer(ServerType.LOBBY_AUTO);
        }

        return Optional.ofNullable(bestChoice);
    }

    public void shutdown() {
        stopHealthCheckTask();
        logger.info("[ServerRegistry] ServerRegistryService finalized.");
    }

    private void stopHealthCheckTask() {
        if (healthCheckTask != null) {
            try {
                healthCheckTask.cancel(false);
                logger.info("[ServerRegistry] Health Check Task (Pterodactyl) stopped.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[ServerRegistry] Error stopping Health Check Task", e);
            } finally {
                healthCheckTask = null;
            }
        }
    }
}