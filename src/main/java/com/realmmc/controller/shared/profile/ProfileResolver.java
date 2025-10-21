package com.realmmc.controller.shared.profile;

import com.realmmc.controller.core.services.ServiceRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitário para resolver um input de string (nome, UUID, ID) para um objeto Profile,
 * usando apenas a base de dados. Funciona em Spigot e Velocity.
 */
public class ProfileResolver {

    private static final Logger LOGGER = Logger.getLogger(ProfileResolver.class.getName());

    /**
     * Resolve um input para um perfil consultando a base de dados.
     * @param input A string a ser resolvida. Pode ser um nome de jogador (display ou username), "uuid:<uuid>" ou "id:<id>".
     * @return Um Optional contendo o Profile se encontrado na base de dados, ou vazio caso contrário.
     */
    public static Optional<Profile> resolve(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Optional.empty();
        }
        final String finalInput = input.trim();

        Optional<ProfileService> serviceOpt = ServiceRegistry.getInstance().getService(ProfileService.class);
        if (serviceOpt.isEmpty()) {
            LOGGER.log(Level.SEVERE, "ProfileResolver: ProfileService não está disponível!");
            return Optional.empty();
        }
        ProfileService profileService = serviceOpt.get();

        if (finalInput.toLowerCase().startsWith("uuid:")) {
            try {
                String uuidStr = finalInput.substring(5);
                UUID uuid = UUID.fromString(uuidStr);
                LOGGER.log(Level.FINE, "ProfileResolver: Tentando resolver por UUID: {0}", uuid);
                return profileService.getByUuid(uuid);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "ProfileResolver: Formato de UUID inválido em input: {0}", finalInput);
                return Optional.empty();
            }
        }

        if (finalInput.toLowerCase().startsWith("id:")) {
            try {
                String idStr = finalInput.substring(3);
                int id = Integer.parseInt(idStr);
                LOGGER.log(Level.FINE, "ProfileResolver: Tentando resolver por ID numérico: {0}", id);
                return profileService.getById(id);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "ProfileResolver: Formato de ID numérico inválido em input: {0}", finalInput);
                return Optional.empty();
            }
        }

        String inputLower = finalInput.toLowerCase();
        LOGGER.log(Level.FINE, "ProfileResolver: Tentando resolver por username/nome: {0}", finalInput);
        return profileService.getByUsername(inputLower)
                .or(() -> profileService.getByName(finalInput));
    }
}