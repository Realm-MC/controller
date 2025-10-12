package com.realmmc.controller.shared.profile;

import com.realmmc.controller.core.services.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Utilitário para resolver um input de string (nome, UUID, ID) para um objeto Profile.
 */
public class ProfileResolver {

    /**
     * Resolve um input para um perfil.
     * @param input A string a ser resolvida. Pode ser um nome de jogador, "uuid:<uuid>" ou "id:<id>".
     * @return Um Optional contendo o Profile se encontrado, ou vazio caso contrário.
     */
    public static Optional<Profile> resolve(String input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }

        ProfileService profileService = ServiceRegistry.getInstance().getService(ProfileService.class).orElse(null);
        if (profileService == null) {
            return Optional.empty();
        }

        if (input.toLowerCase().startsWith("uuid:")) {
            try {
                String uuidStr = input.substring(5);
                UUID uuid = UUID.fromString(uuidStr);
                return profileService.getByUuid(uuid);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        if (input.toLowerCase().startsWith("id:")) {
            try {
                String idStr = input.substring(3);
                int id = Integer.parseInt(idStr);
                return profileService.getById(id);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        Player onlinePlayer = Bukkit.getPlayer(input);
        if (onlinePlayer != null) {
            return profileService.getByUuid(onlinePlayer.getUniqueId());
        }

        return profileService.getByUsername(input).or(() -> profileService.getByName(input));
    }
}