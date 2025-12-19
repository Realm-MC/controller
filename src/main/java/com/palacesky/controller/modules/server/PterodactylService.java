package com.palacesky.controller.modules.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.server.data.ServerInfo;
import com.palacesky.controller.modules.server.data.ServerStatus;
import com.palacesky.controller.modules.server.data.ServerTemplate;
import com.palacesky.controller.modules.server.data.ServerType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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

    private final int ownerUserId;
    private final int defaultLocationId;
    private final int defaultNodeId;

    private final ServerTemplateManager templateManager;

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
        this.templateManager = ServiceRegistry.getInstance().requireService(ServerTemplateManager.class);

        this.panelUrl = System.getProperty("PTERODACTYL_PANEL_URL");
        this.clientApiKey = System.getProperty("PTERODACTYL_API_KEY");
        this.appApiKey = System.getProperty("PTERODACTYL_APP_KEY");
        this.ownerUserId = Integer.parseInt(System.getProperty("PTERODACTYL_OWNER_USER_ID", "1"));
        this.defaultLocationId = Integer.parseInt(System.getProperty("PTERODACTYL_DEFAULT_LOCATION_ID", "1"));
        this.defaultNodeId = Integer.parseInt(System.getProperty("PTERODACTYL_NODE_ID", "1"));

        this.mongoUri = System.getProperty("MONGO_URI");
        this.mongoDb = System.getProperty("MONGO_DB");
        this.redisHost = System.getProperty("REDIS_HOST");
        this.redisPort = System.getProperty("REDIS_PORT");
        this.redisPassword = System.getProperty("REDIS_PASSWORD");
        this.redisDatabase = System.getProperty("REDIS_DATABASE");
        this.redisSsl = System.getProperty("REDIS_SSL");

        if (this.panelUrl == null) throw new IllegalStateException("Flag -DPTERODACTYL_PANEL_URL não definida.");
        if (this.appApiKey == null) logger.severe("CRÍTICO: Flag -DPTERODACTYL_APP_KEY não definida.");

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
        if (this.appApiKey == null) return CompletableFuture.completedFuture(Optional.empty());

        ServerTemplate template = templateManager.getTemplate(serverType);
        if (template == null) {
            logger.severe("[AutoScaler] ERRO: Nenhum template configurado em server-templates.json para o tipo " + serverType);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return getFreeAllocation(this.defaultNodeId).thenCompose(allocData -> {
            if (allocData == null) {
                logger.severe("[AutoScaler] Abortando criação de " + serverName + ": Nenhuma porta livre.");
                return CompletableFuture.completedFuture(Optional.empty());
            }

            String jsonPayload;
            try {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("name", serverName);
                payload.put("description", "Servidor Dinâmico " + serverType.name() + " (Controller)");
                payload.put("user", this.ownerUserId);

                payload.put("egg", template.getEggId());
                payload.put("nest", template.getNestId());
                payload.put("location", this.defaultLocationId);
                payload.put("docker_image", template.getDockerImage());
                payload.put("startup", template.getStartupCommand());

                payload.putObject("feature_limits").put("databases", 0).put("allocations", 1).put("backups", 0);

                payload.putObject("limits")
                        .put("memory", template.getMemory())
                        .put("swap", 0)
                        .put("disk", template.getDisk())
                        .put("io", 500)
                        .put("cpu", template.getCpu());

                ObjectNode environment = payload.putObject("environment");

                if (template.getEnvironment() != null) {
                    template.getEnvironment().forEach(environment::put);
                }

                environment.put("CONTROLLER_SERVER_ID", serverName);
                environment.put("MEMORY_ALLOCATION", String.valueOf(template.getMemory()));
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

                        logger.info("Servidor Pterodactyl criado: " + serverName + " (ID: " + internalId + ") [Template: " + serverType + "]");
                        return Optional.of(newServer);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Falha ao processar resposta JSON de criação", e);
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