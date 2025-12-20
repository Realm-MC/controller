package com.realmmc.controller.modules.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import com.realmmc.controller.shared.storage.redis.packet.ArenaHeartbeatPacket;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

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
    private final ObjectMapper mapper = new ObjectMapper();

    private static final long STARTUP_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    private static final String NOTIFICATION_PERMISSION = "controller.manager";
    private static final long SCALING_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);

    private final Map<ServerType, Long> lastScalingAction = new ConcurrentHashMap<>();
    private final Map<String, Long> emptySinceTimestamp = new ConcurrentHashMap<>();
    private final Map<String, Long> startingTimestamp = new ConcurrentHashMap<>();
    private final Map<String, ArenaHeartbeatPacket> arenaCache = new ConcurrentHashMap<>();

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
        logger.info("[ServerRegistry] Initializing ServerRegistryService (Fix: Dynamic IP Fetch)...");
        setupDefaultServers();
        initializeStaticServers();
        startHealthCheckAndScalingTask();
    }

    public void reloadTemplates() {
        this.templateManager.load();
        logger.info("[ServerRegistry] Templates recarregados.");
    }

    public void updateServerHeartbeat(String serverName, ServerStatus status, GameState gameState, String mapName, boolean canShutdown, int players) {
        if (healthCheckTask == null) return;
        TaskScheduler.runAsync(() -> {
            try {
                Optional<ServerInfo> opt = repository.findByName(serverName);
                if (opt.isPresent()) {
                    ServerInfo server = opt.get();
                    boolean changed = false;

                    if (status == ServerStatus.ONLINE) {
                        if (proxyServer.getServer(serverName).isEmpty()) {
                            if (server.getPort() == 0 || server.getIp() == null) {
                                logger.info("[Registry] Servidor " + serverName + " online, mas sem IP/Porta. Aguardando HealthCheck.");
                            } else {
                                registerServerWithVelocity(server);
                            }
                        }
                    }

                    if (server.getStatus() != status) {
                        server.setStatus(status);
                        changed = true;
                        if (status == ServerStatus.ONLINE) startingTimestamp.remove(serverName);
                    }
                    if (server.getGameState() != gameState) { server.setGameState(gameState); changed = true; }
                    if (!Objects.equals(server.getMapName(), mapName)) { server.setMapName(mapName); changed = true; }
                    if (server.isCanShutdown() != canShutdown) { server.setCanShutdown(canShutdown); changed = true; }
                    if (players >= 0 && server.getPlayerCount() != players) { server.setPlayerCount(players); changed = true; }

                    if (changed) repository.save(server);
                }
            } catch (Exception e) {
                if (!e.getMessage().contains("state should be: open")) logger.log(Level.SEVERE, "Erro heartbeat " + serverName, e);
            }
        });
    }

    public void handleArenaHeartbeat(String json) {
        try {
            ArenaHeartbeatPacket packet = mapper.readValue(json, ArenaHeartbeatPacket.class);
            arenaCache.put(packet.getArenaId(), packet);
        } catch (Exception e) { e.printStackTrace(); }
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
            } catch (Exception e) { logger.log(Level.SEVERE, "Erro no loop HealthCheck.", e); }
        }, 5, 5, TimeUnit.SECONDS);
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
                    logger.warning("[Watchdog] Timeout " + server.getName() + ". Deletando...");
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

                JsonNode details = detailsOpt.get();
                ServerStatus pteroStatus = parsePteroState(details);
                ServerStatus dbStatus = server.getStatus();

                if (pteroStatus == ServerStatus.ONLINE || pteroStatus == ServerStatus.STARTING) {
                    updateServerAddressFromPtero(server, details);
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
                } else if (pteroStatus == ServerStatus.ONLINE) {
                    if (dbStatus != ServerStatus.ONLINE) {
                        server.setStatus(ServerStatus.ONLINE);
                        repository.save(server);
                        startingTimestamp.remove(server.getName());
                    }

                    if (proxyServer.getServer(server.getName()).isEmpty()) {
                        if (server.getPort() != 0 && server.getIp() != null) {
                            registerServerWithVelocity(server);
                            notifyStaff(MessageKey.SERVER_NOTIFY_OPENED, server.getDisplayName(), SoundKeys.SUCCESS);
                        } else {
                            updateServerAddressFromPtero(server, details);
                        }
                    }
                }
            });
        }
    }

    private void updateServerAddressFromPtero(ServerInfo server, JsonNode details) {
        try {
            JsonNode allocations = details.path("attributes").path("relationships").path("allocations").path("data");
            if (allocations.isArray() && allocations.size() > 0) {
                JsonNode primaryAttr = allocations.get(0).path("attributes");
                String ip = primaryAttr.path("ip").asText();
                int port = primaryAttr.path("port").asInt();

                String ipAlias = primaryAttr.path("ip_alias").asText();
                if (ipAlias != null && !ipAlias.isEmpty()) {
                }

                if (server.getPort() != port || !ip.equals(server.getIp())) {
                    server.setIp(ip);
                    server.setPort(port);
                    repository.save(server);
                    logger.info("[Registry] Atualizado endereço de " + server.getName() + " para " + ip + ":" + port);
                }
            }
        } catch (Exception e) {
            logger.warning("Falha ao ler alocação do Pterodactyl para " + server.getName());
        }
    }

    private void runAutoScalingLogic(ServerType type, ScalingRules rules) {
        try {
            long lastAction = lastScalingAction.getOrDefault(type, 0L);
            if (System.currentTimeMillis() - lastAction < SCALING_COOLDOWN_MS) return;

            if (type.isArcade()) {
                handleArcadeScaling(type, rules);
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
                scaleUpNewServer(type);
                lastScalingAction.put(type, System.currentTimeMillis());
                return;
            }
        } catch (Exception e) { logger.log(Level.SEVERE, "Erro scaling " + type, e); }
    }

    private void handleArcadeScaling(ServerType type, ScalingRules rules) {
        long waitingArenas = arenaCache.values().stream()
                .filter(a -> a.getGameType().equalsIgnoreCase(type.name()))
                .filter(a -> a.getState() == GameState.WAITING)
                .count();

        long startingNodes = repository.findByType(type).stream()
                .filter(s -> s.getStatus() == ServerStatus.STARTING)
                .count();

        long potentialAvailability = waitingArenas + (startingNodes * 5);

        if (potentialAvailability < rules.getMinIdle()) {
            long totalNodes = repository.findByType(type).size();
            if (totalNodes < rules.getMaxTotal()) {
                logger.info("[AutoScaler] Criando Node Arcade (" + waitingArenas + " livres).");
                scaleUpNewServer(type);
                lastScalingAction.put(type, System.currentTimeMillis());
            }
        }

        List<ServerInfo> onlineNodes = repository.findByTypeAndStatus(type, ServerStatus.ONLINE);
        for (ServerInfo node : onlineNodes) {
            if (node.getPlayerCount() == 0) {
                long emptySince = emptySinceTimestamp.computeIfAbsent(node.getName(), k -> System.currentTimeMillis());
                if (System.currentTimeMillis() - emptySince > TimeUnit.SECONDS.toMillis(rules.getEmptyShutdownSeconds())) {
                    if (waitingArenas > 15) {
                        scaleDownDynamicServer(node);
                        lastScalingAction.put(type, System.currentTimeMillis());
                        emptySinceTimestamp.remove(node.getName());
                        return;
                    }
                }
            } else { emptySinceTimestamp.remove(node.getName()); }
        }
    }

    public void scaleUpNewServer(ServerType type) {
        String serverName = findNextAvailableName(type);
        if (serverName == null) return;

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
                        scheduleStart(info.getPterodactylId(), 0);
                    } else {
                        repository.delete(Filters.eq("_id", serverName));
                        startingTimestamp.remove(serverName);
                    }
                });
    }

    private void scheduleStart(String pteroId, int attempts) {
        if (attempts > 60) return;
        TaskScheduler.runAsyncLater(() -> {
            pterodactylService.getServerDetails(pteroId).thenAccept(jsonOpt -> {
                if (jsonOpt.isPresent()) {
                    String state = jsonOpt.get().path("attributes").path("current_state").asText();
                    boolean isInstalling = jsonOpt.get().path("attributes").path("is_installing").asBoolean();
                    if (isInstalling) scheduleStart(pteroId, attempts + 1);
                    else if (!state.equals("running") && !state.equals("starting")) {
                        pterodactylService.startServer(pteroId);
                        scheduleStart(pteroId, attempts + 1);
                    }
                } else scheduleStart(pteroId, attempts + 1);
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
                    }
                });
    }

    public Optional<RegisteredServer> getBestLobby() {
        return repository.findByTypeAndStatus(ServerType.LOBBY, ServerStatus.ONLINE).stream()
                .sorted(Comparator.comparingInt(ServerInfo::getPlayerCount))
                .findFirst()
                .flatMap(info -> proxyServer.getServer(info.getName()));
    }

    public boolean isStaticDefault(String serverName) {
        if (serverName == null) return false;
        for (DefaultServer ds : DefaultServer.values()) if (ds.getName().equalsIgnoreCase(serverName)) return true;
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

    private void setupDefaultServers() {
        try {
            for (DefaultServer defaultServer : DefaultServer.values()) {
                Optional<ServerInfo> existingOpt = repository.findByName(defaultServer.getName());
                ServerInfo defaultInfo = defaultServer.toServerInfo();
                if (existingOpt.isEmpty()) { repository.save(defaultInfo); }
            }
        } catch (MongoException e) { logger.log(Level.SEVERE, "Erro DB default servers!", e); }
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

        InetSocketAddress address = new InetSocketAddress(serverInfo.getIp(), serverInfo.getPort());
        com.velocitypowered.api.proxy.server.ServerInfo vInfo = new com.velocitypowered.api.proxy.server.ServerInfo(serverInfo.getName(), address);

        Optional<RegisteredServer> existing = proxyServer.getServer(serverInfo.getName());
        if (existing.isPresent()) {
            if (!existing.get().getServerInfo().getAddress().equals(address)) {
                proxyServer.unregisterServer(existing.get().getServerInfo());
                proxyServer.registerServer(vInfo);
                logger.info("[Registry] Atualizado IP Velocity para: " + serverInfo.getName());
            }
        } else {
            proxyServer.registerServer(vInfo);
            logger.info("[Registry] Registrado no Velocity: " + serverInfo.getName() + " (" + serverInfo.getIp() + ":" + serverInfo.getPort() + ")");
        }
    }

    public void unregisterServerFromVelocity(String serverName) {
        proxyServer.getServer(serverName).ifPresent(s -> proxyServer.unregisterServer(s.getServerInfo()));
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
    }
}