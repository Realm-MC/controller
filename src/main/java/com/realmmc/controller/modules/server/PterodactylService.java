package com.realmmc.controller.modules.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.modules.server.data.ServerInfo;
import com.realmmc.controller.modules.server.data.ServerStatus;
import com.realmmc.controller.modules.server.data.ServerType;
import com.realmmc.controller.shared.utils.TaskScheduler;
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

    private final String eggDockerImage = "ghcr.io/pterodactyl/yolks:java_21";
    private final String eggStartupCommand = "java -Dfile.encoding=UTF-8 -Xms1536M -XX:MaxRAMPercentage=90.0 -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.newgen.generation=1 -Dcontroller.serverId=${CONTROLLER_SERVER_ID} -Dmap.type=${MAP_TYPE} -DMONGO_URI=${MONGO_URI} -DMONGO_DB=${MONGO_DB} -DREDIS_HOST=${REDIS_HOST} -DREDIS_PORT=${REDIS_PORT} -DREDIS_PASSWORD=${REDIS_PASSWORD} -DREDIS_DATABASE=${REDIS_DATABASE} -DREDIS_SSL=${REDIS_SSL} -jar ${SERVER_JARFILE}";

    private final String mongoUri;
    private final String mongoDb;
    private final String redisHost;
    private final String redisPort;
    private final String redisPassword;
    private final String redisDatabase;
    private final String redisSsl;


    public PterodactylService(Logger logger) {
        this.logger = logger;

        this.panelUrl = System.getProperty("PTERODACTYL_PANEL_URL");
        this.clientApiKey = System.getProperty("PTERODACTYL_API_KEY");
        this.appApiKey = System.getProperty("PTERODACTYL_APP_KEY");
        this.ownerUserId = Integer.parseInt(System.getProperty("PTERODACTYL_OWNER_USER_ID", "1"));

        int tempLobbyEggId = 0, tempLobbyNestId = 0, tempDefaultLocationId = 0;
        try {
            String lobbyEggIdStr = System.getProperty("PTERODACTYL_LOBBY_EGG_ID");
            String lobbyNestIdStr = System.getProperty("PTERODACTYL_LOBBY_NEST_ID");
            String defaultLocationIdStr = System.getProperty("PTERODACTYL_DEFAULT_LOCATION_ID");

            if (lobbyEggIdStr == null) throw new NullPointerException("Flag -DPTERODACTYL_LOBBY_EGG_ID não definida.");
            if (lobbyNestIdStr == null) throw new NullPointerException("Flag -DPTERODACTYL_LOBBY_NEST_ID não definida.");
            if (defaultLocationIdStr == null) throw new NullPointerException("Flag -DPTERODACTYL_DEFAULT_LOCATION_ID não definida.");

            tempLobbyEggId = Integer.parseInt(lobbyEggIdStr);
            tempLobbyNestId = Integer.parseInt(lobbyNestIdStr);
            tempDefaultLocationId = Integer.parseInt(defaultLocationIdStr);

        } catch (NumberFormatException e) {
            logger.severe("IDs do Egg/Nest/Location de Lobby não são números válidos!");
        } catch (NullPointerException e) {
            logger.severe(e.getMessage());
        }
        this.lobbyEggId = tempLobbyEggId;
        this.lobbyNestId = tempLobbyNestId;
        this.defaultLocationId = tempDefaultLocationId;

        this.mongoUri = System.getProperty("MONGO_URI");
        this.mongoDb = System.getProperty("MONGO_DB");
        this.redisHost = System.getProperty("REDIS_HOST");
        this.redisPort = System.getProperty("REDIS_PORT");
        this.redisPassword = System.getProperty("REDIS_PASSWORD");
        this.redisDatabase = System.getProperty("REDIS_DATABASE");
        this.redisSsl = System.getProperty("REDIS_SSL");

        if (this.panelUrl == null) throw new IllegalStateException("Flag -DPTERODACTYL_PANEL_URL não definida.");
        if (this.clientApiKey == null) throw new IllegalStateException("Flag -DPTERODACTYL_API_KEY (Cliente) não definida.");
        if (this.appApiKey == null) {
            logger.severe("CRÍTICO: Flag -DPTERODACTYL_APP_KEY (Aplicação) não definida. O Auto-Scaling VAI FALHAR.");
        }
        if (this.lobbyEggId == 0 || this.lobbyNestId == 0 || this.defaultLocationId == 0) {
            throw new IllegalStateException("IDs do Egg/Nest/Location de Lobby não definidos ou inválidos.");
        }
        if (this.mongoUri == null || this.redisHost == null) {
            throw new IllegalStateException("Flags de DB (MONGO_URI, REDIS_HOST) não definidas no Proxy. Não é possível passá-las para novos servidores.");
        }

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }


    private CompletableFuture<Boolean> sendPowerCommand(String pterodactylId, String command) {
        String url = String.format("%s/api/client/servers/%s/power", panelUrl, pterodactylId);
        String jsonPayload = String.format("{\"signal\": \"%s\"}", command);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + clientApiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 204) {
                        return true;
                    }
                    if (status == 401) {
                        logger.log(Level.SEVERE, "ERRO 401 (NÃO AUTORIZADO) [CLIENT API] ao enviar comando '" + command + "' para {0}.", pterodactylId);
                    } else {
                        logger.warning("Erro ao enviar comando '" + command + "' para " + pterodactylId + ". Status: " + status + ", Body: " + response.body());
                    }
                    return false;
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao enviar comando '" + command + "' para " + pterodactylId, ex);
                    return false;
                });
    }

    public CompletableFuture<Boolean> startServer(String pterodactylId) {
        return sendPowerCommand(pterodactylId, "start");
    }

    public CompletableFuture<Boolean> stopServer(String pterodactylId) {
        return sendPowerCommand(pterodactylId, "stop");
    }

    public CompletableFuture<Optional<JsonNode>> getServerDetails(String pterodactylId) {
        String url = String.format("%s/api/client/servers/%s/resources", panelUrl, pterodactylId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + clientApiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            return Optional.of(objectMapper.readTree(response.body()));
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Falha ao processar JSON de detalhes do servidor " + pterodactylId, e);
                            return Optional.<JsonNode>empty();
                        }
                    }
                    if (response.statusCode() == 401) {
                        logger.log(Level.SEVERE, "ERRO 401 (NÃO AUTORIZADO) [CLIENT API] ao buscar detalhes para {0}.", pterodactylId);
                    } else {
                        if (response.statusCode() != 404) {
                            logger.warning("Erro ao buscar detalhes do servidor " + pterodactylId + ". Status: " + response.statusCode() + ", Body: " + response.body());
                        }
                    }
                    return Optional.<JsonNode>empty();
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao buscar detalhes do servidor " + pterodactylId, ex);
                    return Optional.<JsonNode>empty();
                });
    }



    public CompletableFuture<Optional<ServerInfo>> createPterodactylServer(String serverName, ServerType serverType) {
        if (this.appApiKey == null) {
            logger.severe("PTERODACTYL_APP_KEY não configurada. Impossível criar servidor.");
            return CompletableFuture.completedFuture(Optional.empty());
        }

        int eggId;
        int nestId;
        String mapType;

        if (serverType == ServerType.LOBBY) {
            eggId = this.lobbyEggId;
            nestId = this.lobbyNestId;
            mapType = "lobby";
        }
        else {
            logger.warning("Tentativa de criar servidor de tipo não suportado (sem Egg ID definido): " + serverType);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String jsonPayload;
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("name", serverName);
            payload.put("description", "Servidor Dinâmico " + serverType.name() + " gerido pelo Controller");
            payload.put("user", this.ownerUserId);
            payload.put("egg", eggId);
            payload.put("nest", nestId);

            payload.put("location", this.defaultLocationId);

            payload.put("docker_image", this.eggDockerImage);
            payload.put("startup", this.eggStartupCommand);

            payload.putObject("feature_limits")
                    .put("databases", 0)
                    .put("allocations", 1)
                    .put("backups", 0);

            payload.putObject("limits")
                    .put("memory", 1536)
                    .put("swap", 0)
                    .put("disk", 10120)
                    .put("io", 500)
                    .put("cpu", 100);

            ObjectNode environment = payload.putObject("environment");
            environment.put("SERVER_JARFILE", "server.jar");
            environment.put("MEMORY_ALLOCATION", "1536");
            environment.put("EULA", "true");

            environment.put("CONTROLLER_SERVER_ID", serverName);
            environment.put("MAP_TYPE", mapType);
            environment.put("MONGO_URI", this.mongoUri);
            environment.put("MONGO_DB", this.mongoDb);
            environment.put("REDIS_HOST", this.redisHost);
            environment.put("REDIS_PORT", this.redisPort);
            environment.put("REDIS_PASSWORD", this.redisPassword);
            environment.put("REDIS_DATABASE", this.redisDatabase);
            environment.put("REDIS_SSL", this.redisSsl);

            payload.putObject("allocation")
                    .put("default", 1);

            payload.put("start_on_completion", false);

            jsonPayload = payload.toString();
            logger.fine("Payload de Criação: " + jsonPayload);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao construir JSON payload para criar servidor", e);
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

        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    String body = response.body();

                    if (status == 201) {
                        try {
                            JsonNode root = objectMapper.readTree(body).path("attributes");

                            int internalId = root.path("id").asInt();
                            String uuid = root.path("uuid").asText();

                            JsonNode allocation = root.path("relationships").path("allocations").path("data").get(0).path("attributes");
                            String ip = allocation.path("ip_alias").asText(allocation.path("ip").asText());
                            int port = allocation.path("port").asInt();

                            if (ip.isEmpty() || port == 0 || uuid.isEmpty() || internalId == 0) {
                                logger.severe("API criou o servidor mas a resposta JSON não continha IP/Porta/IDs. Body: " + body);
                                return Optional.<ServerInfo>empty();
                            }

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

                            logger.info("Servidor Pterodactyl criado: " + serverName + " (ID: " + internalId + ") em " + ip + ":" + port);
                            return Optional.of(newServer);

                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Falha ao processar JSON de resposta do Pterodactyl (Criação). Body: " + body, e);
                            return Optional.<ServerInfo>empty();
                        }
                    } else {
                        logger.severe("Erro ao criar servidor Pterodactyl (" + serverName + "). Status: " + status + ", Body: " + body);
                        return Optional.<ServerInfo>empty();
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao enviar pedido de criação de servidor para " + serverName, ex);
                    return Optional.empty();
                });
    }

    /**
     * Apaga um servidor do Pterodactyl usando a API de Aplicação.
     * @param internalPteroId O ID numérico INTERNO do Pterodactyl.
     */
    public CompletableFuture<Boolean> deletePterodactylServer(int internalPteroId) {
        if (this.appApiKey == null) {
            logger.severe("PTERODACTYL_APP_KEY não configurada. Impossível apagar servidor.");
            return CompletableFuture.completedFuture(false);
        }

        String url = String.format("%s/api/application/servers/%d", panelUrl, internalPteroId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + appApiKey)
                .header("Accept", "application/json")
                .DELETE()
                .timeout(Duration.ofSeconds(15))
                .build();

        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 204) {
                        logger.info("Servidor Pterodactyl (ID Interno: " + internalPteroId + ") apagado com sucesso.");
                        return true;
                    }

                    logger.warning("Erro ao apagar servidor Pterodactyl (ID Interno: " + internalPteroId + "). Status: " + status + ", Body: " + response.body());
                    return false;
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao enviar pedido de delete para servidor " + internalPteroId, ex);
                    return false;
                });
    }
}