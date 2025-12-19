package com.palacesky.controller.shared.profile;

import com.palacesky.controller.core.services.ServiceRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitário para resolver um input de string (nome, UUID, ID) para um objeto Profile,
 * usando o ProfileService (que interage com a base de dados).
 */
public class ProfileResolver {

    private static final Logger LOGGER = Logger.getLogger(ProfileResolver.class.getName());

    /**
     * Resolve um input para um perfil consultando o ProfileService.
     * @param input A string a ser resolvida. Pode ser um nome de jogador (display ou username), "uuid:<uuid>" ou "id:<id>".
     * @return Um Optional contendo o Profile se encontrado, ou vazio caso contrário.
     */
    public static Optional<Profile> resolve(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Optional.empty();
        }
        final String finalInput = input.trim(); // Trim uma vez no início

        // Obtém o ProfileService
        Optional<ProfileService> serviceOpt = ServiceRegistry.getInstance().getService(ProfileService.class);
        if (serviceOpt.isEmpty()) {
            LOGGER.log(Level.SEVERE, "[ProfileResolver] ProfileService não está disponível! Não é possível resolver o input.");
            // Retorna vazio ou lança exceção, dependendo da criticidade
            return Optional.empty();
            // throw new IllegalStateException("ProfileService not available for ProfileResolver");
        }
        ProfileService profileService = serviceOpt.get(); // Seguro obter aqui

        // --- Lógica de Resolução ---

        // 1. Tenta por UUID (uuid:<uuid>)
        if (finalInput.toLowerCase().startsWith("uuid:") && finalInput.length() > 5) {
            try {
                String uuidStr = finalInput.substring(5);
                UUID uuid = UUID.fromString(uuidStr);
                LOGGER.log(Level.FINER, "[ProfileResolver] Tentando resolver por UUID: {0}", uuid);
                return profileService.getByUuid(uuid); // Delega para o serviço
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "[ProfileResolver] Formato de UUID inválido em input: {0}", finalInput);
                return Optional.empty(); // Formato inválido
            }
        }

        // 2. Tenta por ID numérico (id:<id>)
        if (finalInput.toLowerCase().startsWith("id:") && finalInput.length() > 3) {
            try {
                String idStr = finalInput.substring(3);
                int id = Integer.parseInt(idStr);
                LOGGER.log(Level.FINER, "[ProfileResolver] Tentando resolver por ID numérico: {0}", id);
                return profileService.getById(id); // Delega para o serviço
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "[ProfileResolver] Formato de ID numérico inválido em input: {0}", finalInput);
                return Optional.empty(); // Formato inválido
            }
        }

        // 3. Tenta por Username (case-insensitive via ProfileService)
        // Usar uma variável local para evitar múltiplas chamadas toLowerCase()
        String inputLower = finalInput.toLowerCase();
        LOGGER.log(Level.FINER, "[ProfileResolver] Tentando resolver por username (case-insensitive): {0}", finalInput);
        Optional<Profile> byUsername = profileService.getByUsername(inputLower); // Delega para o serviço
        if (byUsername.isPresent()) {
            return byUsername;
        }

        // 4. Fallback: Tenta por Nome (case-sensitive via ProfileService)
        LOGGER.log(Level.FINER, "[ProfileResolver] Tentando resolver por nome (case-sensitive): {0}", finalInput);
        // Usa orElse() ou or() (Java 9+) para encadear a busca
        return profileService.getByName(finalInput); // Delega para o serviço

        // Alternativa com Java 9+ or():
        // return profileService.getByUsername(inputLower)
        //       .or(() -> profileService.getByName(finalInput));
    }
}