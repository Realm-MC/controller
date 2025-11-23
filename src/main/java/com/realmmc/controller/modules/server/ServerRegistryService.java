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
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import redis.clients.jedis.Jedis;

public class ServerRegistryService {

    private final Logger logger;
    private final ProxyServer proxyServer;
    private final ServerInfoRepository repository;
    private final PterodactylService pterodactylService;

    private static final double LOBBY_SCALE_UP_THRESHOLD = 0.70;
    private static final long SERVER_EMPTY_SHUTDOWN_MS = TimeUnit.SECONDS.toMillis(30);
    private static final Pattern LOBBY_NAME_PATTERN = Pattern.compile("^lobby-(\\d+)$", Pattern.CASE_INSENSITIVE);

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
        startHealthCheckAndScalingTask();
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
                    boolean updated = false;
                    if (!existing.getMinGroup().equals(defaultInfo.getMinGroup())) {
                        existing.setMinGroup(defaultInfo.getMinGroup()); updated = true;
                    }
                    if (existing.getType() != defaultInfo.getType()) {
                        existing.setType(defaultInfo.getType()); updated = true;
                    }
                    if (!existing.getIp().equals(defaultInfo.getIp())) {
                        existing.setIp(defaultInfo.getIp()); updated = true;
                    }
                    if (existing.getPort() != defaultInfo.getPort()) {
                        existing.setPort(defaultInfo.getPort()); updated = true;
                    }
                    if (!existing.getPterodactylId().equals(defaultInfo.getPterodactylId())) {
                        existing.setPterodactylId(defaultInfo.getPterodactylId()); updated = true;
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
            List<ServerInfo> allDbServers = repository.collection().find().into(new ArrayList<>());
            List<ServerInfo> staticServersToStart = allDbServers.stream()
                    .filter(s -> isStaticDefault(s.getName()))
                    .toList();
            logger.info("[ServerRegistry] Loading " + staticServersToStart.size() + " static/default servers...");
            for (ServerInfo server : staticServersToStart) {
                if (server.getIp() == null || server.getPort() == 0 || server.getIp().equals("0.0.0.0")) {
                    logger.warning("[ServerRegistry] Static server '" + server.getName() + "' is missing IP/Port in DB. Skipping.");
                    continue;
                }
                registerServerWithVelocity(server);
                if (server.getStatus() != ServerStatus.ONLINE) {
                    logger.info("[ServerRegistry] Static server '" + server.getName() + "' is not ONLINE (" + server.getStatus() + "). Forcing start...");
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

    private void startHealthCheckAndScalingTask() {
        if (healthCheckTask != null && !healthCheckTask.isDone()) {
            return;
        }
        healthCheckTask = TaskScheduler.runAsyncTimer(() -> {
            try {
                runHealthCheck();
                checkServerScaling();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ServerRegistry] Critical error in combined Health/Scaling Check loop.", e);
            }
        }, 30, 15, TimeUnit.SECONDS);
        logger.info("[ServerRegistry] Health Check & Scaling Task (Pterodactyl <-> DB) started.");
    }

    public void handleServerReadySignal(String serverName) {
        TaskScheduler.runAsync(() -> {
            try {
                Optional<ServerInfo> opt = repository.findByName(serverName);
                if (opt.isPresent()) {
                    ServerInfo server = opt.get();

                    if (server.getStatus() == ServerStatus.ONLINE) return;

                    logger.info("[ServerRegistry] Servidor '" + serverName + "' confirmou inicialização completa via Redis. Marcando como ONLINE.");

                    server.setStatus(ServerStatus.ONLINE);
                    server.setPlayerCount(0);
                    repository.save(server);

                    registerServerWithVelocity(server);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao processar sinal READY para " + serverName, e);
            }
        });
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
            if (isStaticDefault(server.getName()) && server.getStatus() == ServerStatus.STOPPING) {
                continue;
            }
            if (server.getPterodactylId() == null || server.getPterodactylId().isEmpty() || server.getPterodactylId().equals("NOT_SET")) {
                continue;
            }
            if (server.getInternalPteroId() == 0 && !isStaticDefault(server.getName())) {
                logger.warning("[ServerRegistry] Health Check: Dynamic server '" + server.getName() + "' has missing InternalPteroId(0). Deleting from DB.");
                repository.delete(Filters.eq("_id", server.getName()));
                unregisterServerFromVelocity(server.getName());
                continue;
            }

            pterodactylService.getServerDetails(server.getPterodactylId())
                    .whenComplete((detailsOpt, ex) -> {
                        if (ex != null) {
                            logger.log(Level.WARNING, "[ServerRegistry] Health Check: Failed to get Pterodactyl details for " + server.getName(), ex);
                            return;
                        }

                        if (detailsOpt.isEmpty()) {
                            logger.warning("[ServerRegistry] Health Check: No details received for " + server.getName() + " (API failed or server deleted externally?).");
                            if (!isStaticDefault(server.getName())) {
                                logger.warning("[ServerRegistry] [Health Check] Deleting '" + server.getName() + "' from DB as it was not found on Pterodactyl.");
                                repository.delete(Filters.eq("_id", server.getName()));
                                unregisterServerFromVelocity(server.getName());
                            }
                            return;
                        }

                        ServerStatus pteroStatus = parsePteroState(detailsOpt.get());
                        ServerStatus dbStatus = server.getStatus();

                        if (pteroStatus == ServerStatus.OFFLINE || pteroStatus == ServerStatus.STOPPING) {
                            if (dbStatus != ServerStatus.OFFLINE) {
                                logger.info("[ServerRegistry] " + server.getName() + " morreu/parou no Pterodactyl. Marcando OFFLINE.");
                                server.setStatus(ServerStatus.OFFLINE);
                                server.setPlayerCount(0);
                                repository.save(server);
                                unregisterServerFromVelocity(server.getName());
                            }

                            if (!isStaticDefault(server.getName()) && dbStatus == ServerStatus.STOPPING) {
                                logger.info("[ServerRegistry] [Health Check] Dynamic server '" + server.getName() + "' is OFFLINE (was STOPPING). Deleting from Pterodactyl and MongoDB...");

                                pterodactylService.deletePterodactylServer(server.getInternalPteroId())
                                        .thenAccept(deleted -> {
                                            if (deleted) {
                                                repository.delete(Filters.eq("_id", server.getName()));
                                                logger.info("[ServerRegistry] [Health Check] Server '" + server.getName() + "' deleted successfully.");
                                            } else {
                                                logger.severe("[ServerRegistry] [Health Check] Failed to delete server '" + server.getName() + "' from Pterodactyl. Will retry later.");
                                            }
                                        });
                            }
                            return;
                        }

                        if (pteroStatus == ServerStatus.ONLINE) {
                            if (dbStatus == ServerStatus.OFFLINE) {
                                logger.info("[ServerRegistry] " + server.getName() + " detetado como 'running' no Pterodactyl. Marcando como STARTING (aguardando Redis).");
                                server.setStatus(ServerStatus.STARTING);
                                repository.save(server);
                            }

                            if (dbStatus == ServerStatus.ONLINE && proxyServer.getServer(server.getName()).isEmpty()) {
                                logger.info("[ServerRegistry] " + server.getName() + " está ONLINE no DB mas falta no Proxy. Registrando.");
                                registerServerWithVelocity(server);
                            }
                        }
                    });
        }
    }

    private ServerStatus parsePteroState(JsonNode details) {
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
                    .filter(s -> s.getStatus() == ServerStatus.ONLINE || s.getStatus() == ServerStatus.STARTING)
                    .mapToInt(ServerInfo::getMaxPlayersVip)
                    .sum();
            totalMaxPlayers = Math.max(500, totalMaxPlayers);

            try (Jedis jedis = RedisManager.getResource()) {
                jedis.setex(RedisChannel.GLOBAL_NETWORK_MAX_PLAYERS.getName(), 20, String.valueOf(totalMaxPlayers));
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

            for (ServerInfo server : allDbServers) {
                boolean needsSave = false;
                int newCount = onlinePlayerCounts.getOrDefault(server.getName(), 0);
                if (server.getStatus() == ServerStatus.ONLINE) {
                    if (server.getPlayerCount() != newCount) {
                        server.setPlayerCount(newCount);
                        needsSave = true;
                    }
                } else if (server.getPlayerCount() != 0) {
                    server.setPlayerCount(0);
                    needsSave = true;
                }
                if (needsSave) {
                    repository.save(server);
                }
            }

            List<ServerInfo> staticServers = allDbServers.stream()
                    .filter(s -> isStaticDefault(s.getName()))
                    .toList();
            for (ServerInfo server : staticServers) {
                if (server.getStatus() == ServerStatus.OFFLINE) {
                    logger.warning("[ServerRegistry] Static server '" + server.getName() + "' is OFFLINE. Attempting restart...");
                    scaleUpStaticServer(server);
                }
            }

            for (ServerInfo server : allDbServers) {
                if (isStaticDefault(server.getName()) || server.getType() != ServerType.LOBBY) {
                    continue;
                }
                if (server.getStatus() != ServerStatus.ONLINE) {
                    emptySinceTimestamp.remove(server.getName());
                    continue;
                }
                int playerCount = onlinePlayerCounts.getOrDefault(server.getName(), 0);
                if (playerCount == 0) {
                    long emptySince = emptySinceTimestamp.computeIfAbsent(server.getName(), k -> System.currentTimeMillis());
                    if (System.currentTimeMillis() - emptySince > SERVER_EMPTY_SHUTDOWN_MS) {
                        logger.info("[ServerRegistry] Dynamic server '" + server.getName() + "' empty for > 30s. Shutting down...");
                        scaleDownDynamicServer(server);
                        emptySinceTimestamp.remove(server.getName());
                    }
                } else {
                    emptySinceTimestamp.remove(server.getName());
                }
            }

            List<ServerInfo> allLobbyServers = allDbServers.stream()
                    .filter(s -> s.getType() == ServerType.LOBBY)
                    .toList();
            List<ServerInfo> onlineLobbies = allLobbyServers.stream()
                    .filter(s -> s.getStatus() == ServerStatus.ONLINE)
                    .toList();

            boolean triggerLobbyScaleUp = false;
            if (onlineLobbies.isEmpty()) {
                logger.warning("[ServerRegistry] No online lobbies! Triggering scale-up.");
                triggerLobbyScaleUp = true;
            } else {
                boolean allLobbiesFull = true;
                for (ServerInfo lobby : onlineLobbies) {
                    int playerCount = onlinePlayerCounts.getOrDefault(lobby.getName(), 0);
                    if (playerCount < (lobby.getMaxPlayers() * LOBBY_SCALE_UP_THRESHOLD)) {
                        allLobbiesFull = false;
                        break;
                    }
                }
                if (allLobbiesFull) {
                    logger.info("[ServerRegistry] All lobbies are at >" + (LOBBY_SCALE_UP_THRESHOLD * 100) + "% capacity. Triggering scale-up.");
                    triggerLobbyScaleUp = true;
                }
            }

            if (triggerLobbyScaleUp) {
                long startingLobbyCount = allLobbyServers.stream()
                        .filter(s -> s.getStatus() == ServerStatus.STARTING)
                        .count();

                if (startingLobbyCount < 2) {
                    logger.info("[ServerRegistry] Attempting to create a new LOBBY server...");
                    scaleUpNewServer(ServerType.LOBBY);
                } else {
                    logger.fine("[ServerRegistry] LOBBY scale-up paused. " + startingLobbyCount + " servers already STARTING.");
                }
            }

        } catch (MongoException e) {
            logger.log(Level.SEVERE, "[ServerRegistry] MongoDB error in scaling loop.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ServerRegistry] Unexpected error in scaling loop.", e);
        }
    }

    public boolean isStaticDefault(String serverName) {
        if (serverName == null) return false;
        for (DefaultServer ds : DefaultServer.values()) {
            if (ds.getName().equalsIgnoreCase(serverName)) {
                return true;
            }
        }
        return false;
    }

    private String findNextAvailableLobbyName() {
        try {
            Set<Integer> existingNumbers = new HashSet<>();

            repository.collection().find(Filters.regex("_id", "^lobby-(\\d+)$", "i"))
                    .projection(Projections.include("_id"))
                    .forEach(doc -> {
                        Matcher matcher = LOBBY_NAME_PATTERN.matcher(doc.getName());
                        if (matcher.matches()) {
                            try {
                                existingNumbers.add(Integer.parseInt(matcher.group(1)));
                            } catch (NumberFormatException e) {
                            }
                        }
                    });

            existingNumbers.add(1);

            int i = 1;
            while (existingNumbers.contains(i)) {
                i++;
            }

            String newName = "lobby-" + i;
            logger.info("[ServerRegistry] Found next available name: " + newName);
            return newName;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ServerRegistry] Failed to find next available lobby name", e);
            return null;
        }
    }


    private void scaleUpNewServer(ServerType type) {
        String serverName = findNextAvailableLobbyName();
        if (serverName == null) {
            logger.severe("[ServerRegistry] Failed to find an available server name. Aborting scale-up.");
            return;
        }

        logger.info("[ServerRegistry] Attempting to create Pterodactyl server for: " + serverName);

        pterodactylService.createPterodactylServer(serverName, type)
                .thenAccept(serverInfoOpt -> {
                    if (serverInfoOpt.isEmpty()) {
                        logger.severe("[ServerRegistry] Failed to CREATE Pterodactyl server for " + serverName);
                        return;
                    }

                    ServerInfo newServer = serverInfoOpt.get();
                    newServer.setStatus(ServerStatus.STARTING);

                    try {
                        repository.save(newServer);
                        logger.info("[ServerRegistry] New ServerInfo for '" + serverName + "' saved to MongoDB.");
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "[ServerRegistry] Failed to save new ServerInfo to MongoDB. Deleting Pterodactyl server...", e);
                        pterodactylService.deletePterodactylServer(newServer.getInternalPteroId());
                        return;
                    }

                    pterodactylService.startServer(newServer.getPterodactylId())
                            .thenAccept(success -> {
                                if (success) {
                                    logger.info("[ServerRegistry] 'start' command sent to new server '" + serverName + "'.");
                                } else {
                                    logger.severe("[ServerRegistry] Failed to send 'start' command to new server '" + serverName + "'. Deleting...");
                                    repository.delete(Filters.eq("_id", newServer.getName()));
                                    pterodactylService.deletePterodactylServer(newServer.getInternalPteroId());
                                }
                            });
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "[ServerRegistry] Exception in server creation chain for " + serverName, ex);
                    return null;
                });
    }

    private void scaleUpStaticServer(ServerInfo server) {
        if (!isStaticDefault(server.getName())) {
            logger.warning("scaleUpStaticServer called for a non-static server: " + server.getName());
            return;
        }

        server.setStatus(ServerStatus.STARTING);
        server.setPlayerCount(0);
        repository.save(server);
        logger.info("[ServerRegistry] (Re)starting static server '" + server.getName() + "'...");

        pterodactylService.startServer(server.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        logger.info("[ServerRegistry] 'start' command sent for static '" + server.getName() + "'.");
                    } else {
                        logger.severe("[ServerRegistry] Failed to (re)start static server '" + server.getName() + "'. Setting OFFLINE.");
                        server.setStatus(ServerStatus.OFFLINE);
                        repository.save(server);
                    }
                });
    }

    private void scaleDownDynamicServer(ServerInfo server) {
        if (isStaticDefault(server.getName())) {
            logger.warning("scaleDownDynamicServer called for static server: " + server.getName() + ". Ignoring.");
            return;
        }

        server.setStatus(ServerStatus.STOPPING);
        server.setPlayerCount(0);
        repository.save(server);

        unregisterServerFromVelocity(server.getName());
        logger.info("[ServerRegistry] Server '" + server.getName() + "' unregistered from Velocity.");

        pterodactylService.stopServer(server.getPterodactylId())
                .thenAccept(success -> {
                    if (success) {
                        logger.info("[ServerRegistry] 'stop' command sent for '" + server.getName() + "'. Awaiting Health Check to delete.");
                    } else {
                        logger.severe("[ServerRegistry] Failed to stop server '" + server.getName() + "'. Reverting to ONLINE.");
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
        if (serverInfo.getIp() == null || serverInfo.getIp().isEmpty() || serverInfo.getIp().equals("0.0.0.0") || serverInfo.getPort() == 0) {
            logger.warning("[ServerRegistry] Attempted to register server '" + serverInfo.getName() + "' but IP/Port is invalid. Waiting for Health Check.");
            return;
        }

        InetSocketAddress address = new InetSocketAddress(serverInfo.getIp(), serverInfo.getPort());
        com.velocitypowered.api.proxy.server.ServerInfo velocityInfo =
                new com.velocitypowered.api.proxy.server.ServerInfo(serverInfo.getName(), address);

        if (proxyServer.getServer(serverInfo.getName()).isPresent()) {
            proxyServer.unregisterServer(proxyServer.getServer(serverInfo.getName()).get().getServerInfo());
            logger.fine("[ServerRegistry] Server '" + serverInfo.getName() + "' removed from Velocity runtime for update.");
        }

        proxyServer.registerServer(velocityInfo);
        logger.info("[ServerRegistry] Server '" + serverInfo.getName() + "' added to Velocity runtime at " + address);
    }

    public void unregisterServerFromVelocity(String serverName) {
        Optional<RegisteredServer> server = proxyServer.getServer(serverName);
        if (server.isPresent()) {
            proxyServer.unregisterServer(server.get().getServerInfo());
            logger.info("[ServerRegistry] Server '" + serverName + "' removed from Velocity runtime.");
        }
    }

    public Optional<RegisteredServer> getBestLobby() {
        List<ServerInfo> onlineLobbies;
        try {
            onlineLobbies = repository.findByTypeAndStatus(ServerType.LOBBY, ServerStatus.ONLINE);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ServerRegistry] [getBestLobby] Failed to query MongoDB for lobbies", e);
            return proxyServer.getServer("lobby-1");
        }


        if (onlineLobbies.isEmpty()) {
            logger.warning("[ServerRegistry] [getBestLobby] Player tried to find a lobby, but none are ONLINE.");
            repository.findByName("lobby-1").ifPresent(lobby1 -> {
                if (lobby1.getStatus() == ServerStatus.OFFLINE) {
                    logger.warning("[ServerRegistry] [getBestLobby] lobby-1 is offline, attempting to start it.");
                    scaleUpStaticServer(lobby1);
                }
            });
            scaleUpNewServer(ServerType.LOBBY);
            return proxyServer.getServer("lobby-1");
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
            logger.warning("[ServerRegistry] [getBestLobby] All ONLINE lobbies (" + onlineLobbies.size() + ") are full. Triggering new server.");
            scaleUpNewServer(ServerType.LOBBY);
            return proxyServer.getServer("lobby-1").or(() -> onlineLobbies.stream()
                    .findFirst()
                    .flatMap(s -> proxyServer.getServer(s.getName())));
        }

        return Optional.of(bestChoice);
    }

    public void shutdown() {
        stopHealthCheckAndScalingTask();
        logger.info("[ServerRegistry] ServerRegistryService finalized.");
    }

    private void stopHealthCheckAndScalingTask() {
        if (healthCheckTask != null) {
            try {
                healthCheckTask.cancel(false);
                logger.info("[ServerRegistry] Health Check & Scaling Task (Pterodactyl) stopped.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[ServerRegistry] Error stopping Health Check Task", e);
            } finally {
                healthCheckTask = null;
            }
        }
    }
}