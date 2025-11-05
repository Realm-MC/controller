package com.realmmc.controller.modules.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.utils.TaskScheduler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers; // Importa o BodyHandlers para clareza
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serviço para comunicar com a API do Pterodactyl.
 */
public class PterodactylService {

    private final Logger logger;
    private final String panelUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PterodactylService(Logger logger) {
        this.logger = logger;

        // Carrega as credenciais a partir das propriedades do sistema
        this.panelUrl = System.getProperty("PTERODACTYL_PANEL_URL", "https://vps.realmmc.com.br");
        this.apiKey = System.getProperty("PTERODACTYL_API_KEY"); // Deve ser definida no arranque!

        if (this.apiKey == null || this.apiKey.isEmpty()) {
            logger.severe("############################################################");
            logger.severe("### PTERODACTYL_API_KEY não definida!                   ###");
            logger.severe("### O ServerManagerModule não conseguirá ligar servidores. ###");
            logger.severe("############################################################");
            // Lança uma excepção para impedir o carregamento do módulo
            throw new IllegalStateException("PTERODACTYL_API_KEY não pode ser nula.");
        }

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    /**
     * Envia um comando de power (start, stop, kill) para um servidor.
     */
    private CompletableFuture<Boolean> sendPowerCommand(String pterodactylId, String command) {
        String url = String.format("%s/api/client/servers/%s/power", panelUrl, pterodactylId);
        String jsonPayload = String.format("{\"signal\": \"%s\"}", command);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 204) {
                        logger.info("Comando de power '" + command + "' enviado com sucesso para " + pterodactylId);
                        return true;
                    }

                    // Tratamento explícito de Autenticação (401)
                    if (status == 401) {
                        logger.log(Level.SEVERE, "ERRO 401 (NÃO AUTORIZADO) ao enviar comando '" + command + "' para {0}. Verifique a PTERODACTYL_API_KEY.", pterodactylId);
                        return false;
                    }

                    logger.warning("Erro ao enviar comando '" + command + "' para " + pterodactylId + ". Status: " + status + ", Body: " + response.body());
                    return false;
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao enviar comando '" + command + "' para " + pterodactylId, ex);
                    return false;
                });
    }

    /**
     * Liga um servidor no Pterodactyl.
     * @param pterodactylId O ID do servidor no Pterodactyl (ex: "e0d9ff")
     */
    public CompletableFuture<Boolean> startServer(String pterodactylId) {
        return sendPowerCommand(pterodactylId, "start");
    }

    /**
     * Desliga (graciosamente) um servidor no Pterodactyl.
     */
    public CompletableFuture<Boolean> stopServer(String pterodactylId) {
        return sendPowerCommand(pterodactylId, "stop");
    }

    /**
     * Busca os detalhes de um servidor (incluindo estado e IP/Porta).
     * Retorna um JsonNode para análise fácil.
     */
    public CompletableFuture<Optional<JsonNode>> getServerDetails(String pterodactylId) {
        // Usamos a API 'resources' que dá detalhes de estado e alocação
        String url = String.format("%s/api/client/servers/%s/resources", panelUrl, pterodactylId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            // Caminho 1: Sucesso
                            return Optional.of(objectMapper.readTree(response.body()));
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Falha ao processar JSON de detalhes do servidor " + pterodactylId, e);
                            // Caminho 2: Falha no parse
                            return Optional.<JsonNode>empty();
                        }
                    }

                    // Tratamento explícito de Autenticação (401)
                    if (response.statusCode() == 401) {
                        logger.log(Level.SEVERE, "ERRO 401 (NÃO AUTORIZADO) ao buscar detalhes para {0}. Verifique a PTERODACTYL_API_KEY.", pterodactylId);
                    } else {
                        logger.warning("Erro ao buscar detalhes do servidor " + pterodactylId + ". Status: " + response.statusCode() + ", Body: " + response.body());
                    }
                    // Caminho 3: Erro de HTTP
                    return Optional.<JsonNode>empty();
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exceção ao buscar detalhes do servidor " + pterodactylId, ex);

                    // Caminho 4: Exceção no request
                    return Optional.<JsonNode>empty();
                });
    }
}