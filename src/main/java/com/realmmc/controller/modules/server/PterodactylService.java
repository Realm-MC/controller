package com.realmmc.controller.modules.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.modules.server.data.ServerInfo;
import com.realmmc.controller.modules.server.data.ServerStatus;
import com.realmmc.controller.modules.server.data.ServerType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PterodactylService {

    private final Logger logger;
    private final String panelUrl;
    private final String clientApiKey;
    private final String appApiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final int lobbyEggId;
    private final int lobbyNestId;
    private final int ownerUserId;
    private final int defaultLocationId;
    private final int defaultNodeId;

    private final String eggDockerImage = "ghcr.io/pterodactyl/yolks:java_21";
    private final String eggStartupCommand = "java -Dfile.encoding=UTF-8 -Xms1548M -XX:MaxRAMPercentage=90.0 -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.newgen.generation=1 -Dcontroller.serverId=${CONTROLLER_SERVER_ID} -Dmap.type=${MAP_TYPE} -DMONGO_URI=${MONGO_URI} -DMONGO_DB=${MONGO_DB} -DREDIS_HOST=${REDIS_HOST} -DREDIS_PORT=${REDIS_PORT} -DREDIS_PASSWORD=${REDIS_PASSWORD} -DREDIS_DATABASE=${REDIS_DATABASE} -DREDIS_SSL=${REDIS_SSL} -jar ${SERVER_JARFILE}";

    private final String mongoUri;
    private final String mongoDb;
    private final String redisHost;
    private final String redisPort;
    private final String redisPassword;
    private final String redisDatabase;
    private final String redisSsl;

    private record AllocationData(int id, String ip, int port, String ipAlias) {}

    public PterodactylService(Logger logger) {
        this.logger = logger;

        this.panelUrl = System.getProperty("PTERODACTYL_PANEL_URL");
        this.clientApiKey = System.getProperty("PTERODACTYL_API_KEY");
        this.appApiKey = System.getProperty("PTERODACTYL_APP_KEY");
        this.ownerUserId = Integer.parseInt(System.getProperty("PTERODACTYL_OWNER_USER_ID", "1"));

        int tempLobbyEggId = 0, tempLobbyNestId = 0, tempDefaultLocationId = 0, tempNodeId = 0;
        try {
            String lobbyEggIdStr = System.getProperty("PTERODACTYL_LOBBY_EGG_ID");
            String lobbyNestIdStr = System.getProperty("PTERODACTYL_LOBBY_NEST_ID");
            String defaultLocationIdStr = System.getProperty("PTERODACTYL_DEFAULT_LOCATION_ID");
            String nodeIdStr = System.getProperty("PTERODACTYL_NODE_ID");

            if (lobbyEggIdStr == null) throw new NullPointerException("Flag -DPTERODACTYL_LOBBY_EGG_ID não definida.");
            if (lobbyNestIdStr == null) throw new NullPointerException("Flag -DPTERODACTYL_LOBBY_NEST_ID não definida.");
            if (defaultLocationIdStr == null) throw new NullPointerException("Flag -DPTERODACTYL_DEFAULT_LOCATION_ID não definida.");
            if (nodeIdStr == null) throw new NullPointerException("Flag -DPTERODACTYL_NODE_ID não definida.");

            tempLobbyEggId = Integer.parseInt(lobbyEggIdStr);
            tempLobbyNestId = Integer.parseInt(lobbyNestIdStr);
            tempDefaultLocationId = Integer.parseInt(defaultLocationIdStr);
            tempNodeId = Integer.parseInt(nodeIdStr);

        } catch (NumberFormatException e) {
            logger.severe("IDs do Pterodactyl não são números válidos!");
        } catch (NullPointerException e) {
            logger.severe(e.getMessage());
        }
        this.lobbyEggId = tempLobbyEggId;
        this.lobbyNestId = tempLobbyNestId;
        this.defaultLocationId = tempDefaultLocationId;
        this.defaultNodeId = tempNodeId;

        this.mongoUri = System.getProperty("MONGO_URI");
        this.mongoDb = System.getProperty("MONGO_DB");
        this.redisHost = System.getProperty("REDIS_HOST");
        this.redisPort = System.getProperty("REDIS_PORT");
        this.redisPassword = System.getProperty("REDIS_PASSWORD");
        this.redisDatabase = System.getProperty("REDIS_DATABASE");
        this.redisSsl = System.getProperty("REDIS_SSL");

        if (this.panelUrl == null) throw new IllegalStateException("Flag -DPTERODACTYL_PANEL_URL não definida.");
        if (this.appApiKey == null) logger.severe("CRÍTICO: Flag -DPTERODACTYL_APP_KEY (Aplicação) não definida. O Auto-Scaling VAI FALHAR.");

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private CompletableFuture<AllocationData> getFreeAllocation(int nodeId) {
        String url = String.format("%s/api/application/nodes/%d/allocations?per_page=200", panelUrl, nodeId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + appApiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode root = objectMapper.readTree(response.body());
                            JsonNode data = root.path("data");
                            if (data.isArray()) {
                                for (JsonNode allocationNode : data) {
                                    JsonNode attributes = allocationNode.path("attributes");
                                    boolean assigned = attributes.path("assigned").asBoolean();

                                    if (!assigned) {
                                        int id = attributes.path("id").asInt();
                                        String ip = attributes.path("ip").asText();
                                        int port = attributes.path("port").asInt();
                                        String alias = attributes.path("ip_alias").asText(null);
                                        return new AllocationData(id, ip, port, alias);
                                    }
                                }
                                logger.severe("Nenhuma porta livre encontrada nas primeiras 200 portas do Node " + nodeId + ".");
                                return null;
                            }
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Erro ao processar JSON de alocações", e);
                        }
                    } else {
                        logger.warning("Erro ao buscar alocações no Node " + nodeId + ". Status: " + response.statusCode());
                    }
                    return null;
                });
    }

    public CompletableFuture<Optional<ServerInfo>> createPterodactylServer(String serverName, ServerType serverType) {
        if (this.appApiKey == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String templateUrl;
        String mapTypeStr;

        switch (serverType) {
            case LOBBY:
                templateUrl = "https://github.com/Realm-MC/server-files-default/releases/download/lobby-v.10/Lobby.zip";
                mapTypeStr = "lobby";
                break;
            case LOGIN:
                templateUrl = "https://github.com/Realm-MC/server-files-default/releases/download/login-v.10/Login.zip";
                mapTypeStr = "login";
                break;
            case PUNISHED:
                templateUrl = "https://github.com/Realm-MC/server-files-default/releases/download/punished-v.10/Punished.zip";
                mapTypeStr = "punished";
                break;
            case PERSISTENT:
                logger.warning("[AutoScaler] Tentativa de criar servidor PERSISTENT dinamicamente. Isso não é suportado pelo template automático.");
                return CompletableFuture.completedFuture(Optional.empty());
            default:
                logger.warning("[AutoScaler] Tipo de servidor desconhecido: " + serverType);
                return CompletableFuture.completedFuture(Optional.empty());
        }

        int targetEggId = this.lobbyEggId;
        int targetNestId = this.lobbyNestId;

        return getFreeAllocation(this.defaultNodeId).thenCompose(allocData -> {
            if (allocData == null) {
                logger.severe("[AutoScaler] Abortando criação de " + serverName + ": Nenhuma porta livre encontrada.");
                return CompletableFuture.completedFuture(Optional.empty());
            }

            String jsonPayload;
            try {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("name", serverName);
                payload.put("description", "Servidor Dinâmico " + serverType.name() + " gerido pelo Controller");
                payload.put("user", this.ownerUserId);
                payload.put("egg", targetEggId);
                payload.put("nest", targetNestId);
                payload.put("location", this.defaultLocationId);
                payload.put("docker_image", this.eggDockerImage);
                payload.put("startup", this.eggStartupCommand);

                payload.putObject("feature_limits").put("databases", 0).put("allocations", 1).put("backups", 0);
                payload.putObject("limits").put("memory", 3048).put("swap", 0).put("disk", 10120).put("io", 500).put("cpu", 100);

                ObjectNode environment = payload.putObject("environment");
                environment.put("SERVER_JARFILE", "server.jar");
                environment.put("MEMORY_ALLOCATION", "3048");
                environment.put("EULA", "true");

                environment.put("TEMPLATE_URL", templateUrl);

                environment.put("MINECRAFT_VERSION", "1.21.4");
                environment.put("BUILD_NUMBER", "latest");

                environment.put("CONTROLLER_SERVER_ID", serverName);
                environment.put("MAP_TYPE", mapTypeStr);
                environment.put("MONGO_URI", this.mongoUri);
                environment.put("MONGO_DB", this.mongoDb);
                environment.put("REDIS_HOST", this.redisHost);
                environment.put("REDIS_PORT", this.redisPort);
                environment.put("REDIS_PASSWORD", this.redisPassword);
                environment.put("REDIS_DATABASE", this.redisDatabase);
                environment.put("REDIS_SSL", this.redisSsl);

                payload.putObject("allocation").put("default", allocData.id());

                payload.put("start_on_completion", false);
                jsonPayload = payload.toString();

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao construir JSON payload", e);
                return CompletableFuture.completedFuture(Optional.empty());
            }

            String url = String.format("%s/api/application/servers", panelUrl);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + appApiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            return httpClient.sendAsync(request, BodyHandlers.ofString()).thenApply(response -> {
                int status = response.statusCode();
                if (status == 201) {
                    try {
                        JsonNode root = objectMapper.readTree(response.body()).path("attributes");
                        int internalId = root.path("id").asInt();
                        String uuid = root.path("uuid").asText();

                        String ip = (allocData.ipAlias() != null && !allocData.ipAlias().isEmpty()) ? allocData.ipAlias() : allocData.ip();
                        int port = allocData.port();

                        ServerInfo newServer = ServerInfo.builder()
                                .name(serverName)
                                .displayName(serverName)
                                .pterodactylId(uuid)
                                .internalPteroId(internalId)
                                .ip(ip)
                                .port(port)
                                .type(serverType)
                                .minGroup("default")
                                .maxPlayers(100)
                                .maxPlayersVip(120)
                                .status(ServerStatus.OFFLINE)
                                .build();

                        logger.info("Servidor Pterodactyl criado com sucesso: " + serverName + " (ID: " + internalId + ") em " + ip + ":" + port + " [Template: " + serverType + "]");
                        return Optional.of(newServer);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Falha ao processar resposta JSON de criação (parcial)", e);
                        return Optional.<ServerInfo>empty();
                    }
                } else {
                    logger.severe("Erro ao criar servidor Pterodactyl (" + serverName + "). Status: " + status + ", Body: " + response.body());
                    return Optional.<ServerInfo>empty();
                }
            });
        });
    }

    private CompletableFuture<Boolean> sendPowerCommand(String pterodactylId, String command) {
        String url = String.format("%s/api/client/servers/%s/power", panelUrl, pterodactylId);
        String jsonPayload = String.format("{\"signal\": \"%s\"}", command);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer " + clientApiKey).header("Accept", "application/json").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).timeout(Duration.ofSeconds(10)).build();
        return httpClient.sendAsync(request, BodyHandlers.ofString()).thenApply(r -> r.statusCode() == 204).exceptionally(ex -> false);
    }

    public CompletableFuture<Boolean> startServer(String pterodactylId) { return sendPowerCommand(pterodactylId, "start"); }
    public CompletableFuture<Boolean> stopServer(String pterodactylId) { return sendPowerCommand(pterodactylId, "stop"); }

    public CompletableFuture<Optional<JsonNode>> getServerDetails(String pterodactylId) {
        String url = String.format("%s/api/client/servers/%s/resources", panelUrl, pterodactylId);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer " + clientApiKey).header("Accept", "application/json").GET().timeout(Duration.ofSeconds(10)).build();
        return httpClient.sendAsync(request, BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 200) {
                try { return Optional.of(objectMapper.readTree(response.body())); } catch (Exception e) { return Optional.<JsonNode>empty(); }
            }
            return Optional.<JsonNode>empty();
        }).exceptionally(ex -> Optional.empty());
    }

    public CompletableFuture<Boolean> deletePterodactylServer(int internalPteroId) {
        if (this.appApiKey == null) return CompletableFuture.completedFuture(false);
        String url = String.format("%s/api/application/servers/%d", panelUrl, internalPteroId);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer " + appApiKey).header("Accept", "application/json").DELETE().timeout(Duration.ofSeconds(15)).build();
        return httpClient.sendAsync(request, BodyHandlers.ofString()).thenApply(r -> r.statusCode() == 204).exceptionally(ex -> false);
    }
}