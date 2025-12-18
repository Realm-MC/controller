package com.realmmc.controller.modules.server;

import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.data.*;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.Player;
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
    private final ServerTemplateManager templateManager;
    private final Optional<SoundPlayer> soundPlayerOpt;

    private static final long STARTUP_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    private static final String NOTIFICATION_PERMISSION = "controller.manager";

    private static final long SCALING_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);
    private final Map<ServerType, Long> lastScalingAction = new ConcurrentHashMap<>();

    private final Map<String, Long> emptySinceTimestamp = new ConcurrentHashMap<>();
    private final Map<String, Long> startingTimestamp = new ConcurrentHashMap<>();

    private ScheduledFuture<?> healthCheckTask = null;

    public ServerRegistryService(Logger logger) {
        this.logger = logger;
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.repository = new ServerInfoRepository();
        this.pterodactylService = ServiceRegistry.getInstance().requireService(PterodactylService.class);
        this.templateManager = ServiceRegistry.getInstance().requireService(ServerTemplateManager.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
    }

    public void initialize() {
        logger.info("[ServerRegistry] Initializing ServerRegistryService (Robust Start)...");
        setupDefaultServers();
        initializeStaticServers();
        startHealthCheckAndScalingTask();
    }

    public void reloadTemplates() {
        this.templateManager.load();
        logger.info("[ServerRegistry] Templates recarregados com sucesso.");
    }

    private void notifyStaff(MessageKey key, String serverName, String soundKey) {
        TaskScheduler.runAsync(() -> {
            for (Player player : proxyServer.getAllPlayers()) {
                if (player.hasPermission(NOTIFICATION_PERMISSION)) {
                    Messages.send(player, Message.of(key).with("server", serverName));
                    if (soundKey != null) soundPlayerOpt.ifPresent(sp -> sp.playSound(player, soundKey));
                }
            }
        });
    }

    public void updateServerHeartbeat(String serverName, ServerStatus status, GameState gameState, String mapName, boolean canShutdown, int players) {
        if (healthCheckTask == null) return;

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
                                notifyStaff(MessageKey.SERVER_NOTIFY_OPENED, server.getDisplayName(), SoundKeys.SUCCESS);
                            }
                        }
                    }
                    if (server.getGameState() != gameState) { server.setGameState(gameState); changed = true; }
                    if (!Objects.equals(server.getMapName(), mapName)) { server.setMapName(mapName); changed = true; }
                    if (server.isCanShutdown() != canShutdown) { server.setCanShutdown(canShutdown); changed = true; }
                    if (players >= 0 && server.getPlayerCount() != players) { server.setPlayerCount(players); changed = true; }

                    if (changed) repository.save(server);
                }
            } catch (Exception e) {
                if (!e.getMessage().contains("state should be: open")) {
                    logger.log(Level.SEVERE, "Erro ao atualizar heartbeat para " + serverName, e);
                }
            }
        });
    }

    private void setupDefaultServers() {
        try {
            for (DefaultServer defaultServer : DefaultServer.values()) {
                Optional<ServerInfo> existingOpt = repository.findByName(defaultServer.getName());
                ServerInfo defaultInfo = defaultServer.toServerInfo();
                if (existingOpt.isEmpty()) { repository.save(defaultInfo); }
                else {
                    ServerInfo existing = existingOpt.get();
                    if (!existing.getIp().equals(defaultInfo.getIp()) || existing.getPort() != defaultInfo.getPort()) {
                        existing.setIp(defaultInfo.getIp());
                        existing.setPort(defaultInfo.getPort());
                        repository.save(existing);
                    }
                }
            }
        } catch (MongoException e) { logger.log(Level.SEVERE, "Critical failure synchronizing default servers!", e); }
    }

    private void initializeStaticServers() {
        try {
            List<ServerInfo> allDbServers = repository.collection().find().into(new ArrayList<>());
            allDbServers.stream().filter(s -> isStaticDefault(s.getName())).forEach(server -> {
                if (server.getIp() != null && server.getPort() != 0 && !server.getIp().equals("0.0.0.0")) {
                    registerServerWithVelocity(server);
                }
                if (server.getStatus() != ServerStatus.ONLINE && server.getStatus() != ServerStatus.STARTING) {
                    scaleUpStaticServer(server);
                }
            });
        } catch (Exception e) { logger.log(Level.SEVERE, "Failed to load static servers.", e); }
    }

    private void startHealthCheckAndScalingTask() {
        if (healthCheckTask != null && !healthCheckTask.isDone()) return;
        healthCheckTask = TaskScheduler.runAsyncTimer(() -> {
            try {
                long now = System.currentTimeMillis();
                startingTimestamp.entrySet().removeIf(entry -> (now - entry.getValue()) > STARTUP_TIMEOUT_MS);

                runHealthCheck();

                for (ServerType type : ServerType.values()) {
                    if (type == ServerType.PERSISTENT) continue;
                    ServerTemplate template = templateManager.getTemplate(type);
                    if (template != null && template.getScaling() != null) {
                        runAutoScalingLogic(type, template.getScaling());
                    }
                }

            } catch (Exception e) { logger.log(Level.SEVERE, "Critical error in Health/Scaling loop.", e); }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void runHealthCheck() {
        List<ServerInfo> allDbServers;
        try { allDbServers = repository.collection().find().into(new ArrayList<>()); } catch (Exception e) { return; }

        for (ServerInfo server : allDbServers) {
            if ((isStaticDefault(server.getName()) && server.getStatus() == ServerStatus.STOPPING)
                    || server.getPterodactylId() == null || server.getPterodactylId().equals("NOT_SET")) continue;

            if (server.getStatus() == ServerStatus.STARTING && !isStaticDefault(server.getName())) {
                Long startData = startingTimestamp.get(server.getName());
                if (startData != null && (System.currentTimeMillis() - startData) > STARTUP_TIMEOUT_MS) {
                    logger.warning("[Watchdog] Timeout de inicialização para " + server.getName() + ". Deletando...");
                    startingTimestamp.remove(server.getName());
                    performServerDeletion(server);
                    continue;
                }
            }

            pterodactylService.getServerDetails(server.getPterodactylId()).whenComplete((detailsOpt, ex) -> {
                if (ex != null || detailsOpt.isEmpty()) {
                    if (!isStaticDefault(server.getName()) && detailsOpt.isEmpty()) {
                        repository.delete(Filters.eq("_id", server.getName()));
                        unregisterServerFromVelocity(server.getName());
                    }
                    return;
                }
                ServerStatus pteroStatus = parsePteroState(detailsOpt.get());
                ServerStatus dbStatus = server.getStatus();

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
                    if (proxyServer.getServer(server.getName()).isEmpty()) {
                        registerServerWithVelocity(server);
                        notifyStaff(MessageKey.SERVER_NOTIFY_OPENED, server.getDisplayName(), SoundKeys.SUCCESS);
                    }
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

    private void runAutoScalingLogic(ServerType type, ScalingRules rules) {
        try {
            long lastAction = lastScalingAction.getOrDefault(type, 0L);
            if (System.currentTimeMillis() - lastAction < SCALING_COOLDOWN_MS) {
                return;
            }

            List<ServerInfo> allServers = repository.findByType(type);
            Map<String, Integer> onlineCounts = getOnlineCounts();

            long starting = allServers.stream().filter(s -> s.getStatus() == ServerStatus.STARTING).count();
            if (starting > 0) return;

            long active = allServers.stream().filter(s -> s.getStatus() == ServerStatus.ONLINE).count();

            long available = allServers.stream()
                    .filter(s -> s.getStatus() == ServerStatus.ONLINE)
                    .filter(s -> onlineCounts.getOrDefault(s.getName(), 0) < (s.getMaxPlayers() * rules.getFullThreshold()))
                    .count();

            if (active < rules.getMaxTotal() && available < rules.getMinIdle()) {
                logger.info("[AutoScaler] Scaling UP " + type + ". Available: " + available + " < MinIdle: " + rules.getMinIdle());
                scaleUpNewServer(type);
                lastScalingAction.put(type, System.currentTimeMillis());
                return;
            }

            if (available > rules.getMinIdle()) {
                for (ServerInfo server : allServers) {
                    if (isStaticDefault(server.getName())) continue;

                    if (server.getStatus() == ServerStatus.ONLINE) {
                        int count = onlineCounts.getOrDefault(server.getName(), 0);
                        if (count == 0) {
                            long emptySince = emptySinceTimestamp.computeIfAbsent(server.getName(), k -> System.currentTimeMillis());
                            long shutdownDelayMs = TimeUnit.SECONDS.toMillis(rules.getEmptyShutdownSeconds());

                            if (System.currentTimeMillis() - emptySince > shutdownDelayMs) {
                                if (server.isCanShutdown()) {
                                    logger.info("[AutoScaler] Scaling DOWN " + server.getName());
                                    scaleDownDynamicServer(server);
                                    lastScalingAction.put(type, System.currentTimeMillis());
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
            logger.log(Level.SEVERE, "Erro na lógica de scaling para " + type, e);
        }
    }

    public void scaleUpNewServer(ServerType type) {
        String serverName = findNextAvailableName(type);
        if (serverName == null) {
            logger.warning("[AutoScaler] Limite de nomes atingido para " + type);
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

        notifyStaff(MessageKey.SERVER_NOTIFY_OPENING, displayName, SoundKeys.CLICK);

        pterodactylService.createPterodactylServer(serverName, type)
                .thenAccept(infoOpt -> {
                    if (infoOpt.isPresent()) {
                        ServerInfo info = infoOpt.get();
                        info.setDisplayName(formatDisplayName(info.getName()));
                        info.setStatus(ServerStatus.STARTING);
                        repository.save(info);
                        logger.info("[AutoScaler] Servidor " + info.getName() + " criado. Agendando start agressivo...");
                        scheduleStart(info.getPterodactylId(), 0);
                    } else {
                        logger.warning("[AutoScaler] Falha na criação via API. Limpando DB: " + serverName);
                        repository.delete(Filters.eq("_id", serverName));
                        startingTimestamp.remove(serverName);
                    }
                });
    }

    private void scheduleStart(String pteroId, int attempts) {
        if (attempts > 60) {
            logger.warning("[AutoScaler] Desistindo de iniciar " + pteroId + " após 5 minutos. O HealthCheck assumirá.");
            return;
        }

        TaskScheduler.runAsyncLater(() -> {
            pterodactylService.getServerDetails(pteroId).thenAccept(jsonOpt -> {
                if (jsonOpt.isPresent()) {
                    String state = jsonOpt.get().path("attributes").path("current_state").asText();
                    boolean isInstalling = jsonOpt.get().path("attributes").path("is_installing").asBoolean();

                    if (isInstalling) {
                        if (attempts % 6 == 0) {
                            logger.info("[AutoScaler] Aguardando instalação de " + pteroId + " (" + attempts + "/60)");
                        }
                        scheduleStart(pteroId, attempts + 1);
                    } else if (!state.equals("running") && !state.equals("starting")) {
                        logger.info("[AutoScaler] Enviando sinal START para " + pteroId + " (Status atual: " + state + ")");
                        pterodactylService.startServer(pteroId);
                        scheduleStart(pteroId, attempts + 1);
                    } else {
                        logger.info("[AutoScaler] Servidor " + pteroId + " reportou estado: " + state + ". Start agendado com sucesso.");
                    }
                } else {
                    scheduleStart(pteroId, attempts + 1);
                }
            });
        }, 5, TimeUnit.SECONDS);
    }

    private void scaleDownDynamicServer(ServerInfo server) {
        notifyStaff(MessageKey.SERVER_NOTIFY_CLOSED, server.getDisplayName(), SoundKeys.NOTIFICATION);
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
                        repository.delete(Filters.eq("_id", server.getName()));
                        startingTimestamp.remove(server.getName());
                    } else {
                        logger.warning("[AutoScaler] Falha ao deletar " + server.getName() + " via API.");
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

    private String findNextAvailableName(ServerType type) {
        String prefix = type.name().toLowerCase() + "-";
        for (int i = 1; i <= 100; i++) {
            String name = prefix + i;
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
            } else { return; }
        }
        InetSocketAddress address = new InetSocketAddress(serverInfo.getIp(), serverInfo.getPort());
        com.velocitypowered.api.proxy.server.ServerInfo vInfo = new com.velocitypowered.api.proxy.server.ServerInfo(serverInfo.getName(), address);
        proxyServer.registerServer(vInfo);
        logger.info("[ServerRegistry] Registered into Velocity: " + serverInfo.getName());
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

    private void updateRedisMaxPlayers(List<ServerInfo> servers) {
        int totalMaxPlayers = servers.stream()
                .filter(s -> s.getStatus() == ServerStatus.ONLINE)
                .mapToInt(ServerInfo::getMaxPlayersVip)
                .sum();
        try (Jedis jedis = RedisManager.getResource()) {
            jedis.setex(RedisChannel.GLOBAL_NETWORK_MAX_PLAYERS.getName(), 20, String.valueOf(Math.max(500, totalMaxPlayers)));
        } catch (Exception ignored) {}
    }

    private Map<String, Integer> getOnlineCounts() {
        return proxyServer.getAllPlayers().stream()
                .filter(p -> p.getCurrentServer().isPresent())
                .collect(Collectors.groupingBy(p -> p.getCurrentServer().get().getServerInfo().getName(), Collectors.counting()))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue()));
    }

    public void shutdown() {
        if (healthCheckTask != null) {
            healthCheckTask.cancel(true);
            healthCheckTask = null;
        }
        logger.info("[ServerRegistry] Serviço finalizado. Tarefas de heartbeat canceladas.");
    }
}