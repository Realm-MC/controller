package com.realmmc.controller.shared.utils; // Ou outro pacote compartilhado apropriado

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role; // Import Role

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe utilitária para formatar nicknames de jogadores
 * com base no seu perfil e grupo primário.
 */
public final class NicknameFormatter {

    // Logger para registrar avisos ou erros
    private static final Logger logger = Logger.getLogger(NicknameFormatter.class.getName());

    // Acesso aos serviços essenciais via ServiceRegistry
    private static final ProfileService profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
    private static final RoleService roleService = ServiceRegistry.getInstance().requireService(RoleService.class);

    // Construtor privado para impedir instanciação
    private NicknameFormatter() {
    }

    // --- Métodos Auxiliares ---

    /**
     * Busca o Perfil de um jogador pelo UUID.
     * Retorna Optional.empty() se não encontrado ou em caso de erro.
     */
    private static Optional<Profile> getProfile(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        try {
            return profileService.getByUuid(uuid);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro ao buscar perfil para formatação de nickname (UUID: " + uuid + ")", e);
            return Optional.empty();
        }
    }

    /**
     * Busca os Dados de Sessão (com grupo primário calculado) de um jogador online pelo UUID.
     * Tenta buscar do cache, com fallback para carregamento síncrono (com timeout curto) se houver cache miss.
     * Retorna Optional.empty() se não encontrado ou em caso de erro/timeout.
     */
    private static Optional<PlayerSessionData> getSessionData(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        Optional<PlayerSessionData> cachedData = roleService.getSessionDataFromCache(uuid);
        if (cachedData.isPresent()) {
            return cachedData;
        }
        logger.warning("[NicknameFormatter] Cache miss para PlayerSessionData (UUID: " + uuid + "). Tentando carregamento síncrono como fallback.");
        try {
            PlayerSessionData loadedData = roleService.loadPlayerDataAsync(uuid).get(3, TimeUnit.SECONDS); // Timeout de 3 segundos
            if (loadedData != null) {
                logger.info("[NicknameFormatter] Carregamento síncrono de PlayerSessionData bem-sucedido para " + uuid + " após cache miss.");
                return Optional.of(loadedData);
            } else {
                logger.severe("[NicknameFormatter] Carregamento síncrono de PlayerSessionData retornou null para " + uuid);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[NicknameFormatter] Falha ao carregar PlayerSessionData síncrono para " + uuid, e);
            return Optional.empty();
        }
    }

    // --- Métodos de Formatação Públicos ---

    /**
     * Retorna o nome de utilizador original do jogador (case preservado).
     * Ex: MrLucas127
     * Retorna "Unknown" se o perfil não for encontrado.
     *
     * @param uuid UUID do jogador.
     * @return O nome original ou "Unknown".
     */
    public static String getName(UUID uuid) {
        return getProfile(uuid)
                .map(Profile::getName) // Extrai o nome se o perfil existir
                .orElse("Unknown");     // Valor padrão se o perfil não for encontrado
    }

    /**
     * Retorna o nome de utilizador original em letras minúsculas.
     * Ex: mrlucas127
     * Retorna "unknown" se o perfil não for encontrado.
     *
     * @param uuid UUID do jogador.
     * @return O nome em minúsculas ou "unknown".
     */
    public static String getUsername(UUID uuid) {
        String name = getName(uuid); // Reutiliza o método getName
        return name.equals("Unknown") ? "unknown" : name.toLowerCase();
    }

    /**
     * Retorna o nickname formatado com base no grupo primário do jogador.
     *
     * @param uuid       UUID do jogador.
     * @param withPrefix Se true, inclui o prefixo do grupo (Ex: <gold>[Master] MrLucas127).
     * Se false, inclui apenas a cor do grupo (Ex: <gold>MrLucas127).
     * @return O nickname formatado ou o nome original se os dados do grupo não forem encontrados.
     */
    public static String getNick(UUID uuid, boolean withPrefix) {
        String originalName = getName(uuid);
        if (originalName.equals("Unknown")) {
            return originalName;
        }

        Optional<PlayerSessionData> sessionDataOpt = getSessionData(uuid);
        if (sessionDataOpt.isEmpty()) {
            logger.warning("[NicknameFormatter] Não foi possível obter dados de sessão para getNick(UUID: " + uuid + "). Retornando nome original.");
            return originalName;
        }

        Role primaryRole = sessionDataOpt.get().getPrimaryRole();

        if (withPrefix) {
            String prefix = primaryRole.getPrefix();
            return (prefix != null && !prefix.isEmpty()) ? prefix + originalName : originalName;
        } else {
            String color = primaryRole.getColor();
            return (color != null && !color.isEmpty()) ? color + originalName : originalName;
        }
    }

    /**
     * Retorna as "tags" visuais associadas ao grupo primário do jogador (prefixo e sufixo).
     * Ex: <gold>[Master] <blue>[ALPHA]
     *
     * @param uuid UUID do jogador.
     * @return Uma string contendo o prefixo e/ou sufixo formatados, ou string vazia se não houver dados.
     */
    public static String getTagsGroup(UUID uuid) {
        Optional<PlayerSessionData> sessionDataOpt = getSessionData(uuid);
        if (sessionDataOpt.isEmpty()) {
            logger.warning("[NicknameFormatter] Não foi possível obter dados de sessão para getTagsGroup(UUID: " + uuid + "). Retornando string vazia.");
            return "";
        }

        Role primaryRole = sessionDataOpt.get().getPrimaryRole();
        String prefix = primaryRole.getPrefix();
        String suffix = primaryRole.getSuffix();

        StringBuilder tags = new StringBuilder();
        boolean hasPrefix = prefix != null && !prefix.isEmpty();
        boolean hasSuffix = suffix != null && !suffix.isEmpty();

        if (hasPrefix) {
            tags.append(prefix.trim()); // Remove espaço extra
        }
        if (hasPrefix && hasSuffix) {
            tags.append(" "); // Adiciona espaço apenas se ambos existirem
        }
        if (hasSuffix) {
            tags.append(suffix.trim()); // Remove espaço extra
        }
        return tags.toString();
    }

    /**
     * <<< NOVO MÉTODO >>>
     * Retorna o nickname completo formatado com prefixo, nome original e sufixo.
     * Ex: <gold>[Master] MrLucas127 <blue>[ALPHA]
     * Retorna o nome original se os dados do grupo não forem encontrados.
     *
     * @param uuid UUID do jogador.
     * @return O nickname completo formatado ou o nome original/Unknown.
     */
    public static String getFullFormattedNick(UUID uuid) {
        String originalName = getName(uuid);
        if (originalName.equals("Unknown")) {
            return originalName; // Retorna "Unknown" se o perfil não foi encontrado
        }

        Optional<PlayerSessionData> sessionDataOpt = getSessionData(uuid);
        if (sessionDataOpt.isEmpty()) {
            logger.warning("[NicknameFormatter] Não foi possível obter dados de sessão para getFullFormattedNick(UUID: " + uuid + "). Retornando nome original.");
            return originalName; // Retorna nome original como fallback
        }

        Role primaryRole = sessionDataOpt.get().getPrimaryRole();
        String prefix = primaryRole.getPrefix(); // Pode ter espaço no final ex: "<tag> "
        String suffix = primaryRole.getSuffix(); // Pode ter espaço no início ex: " <tag>"

        StringBuilder formattedNick = new StringBuilder();

        boolean hasPrefix = prefix != null && !prefix.isEmpty();
        boolean hasSuffix = suffix != null && !suffix.isEmpty();

        if (hasPrefix) {
            formattedNick.append(prefix); // Adiciona prefixo (com possível espaço no final)
            // Garante um espaço APÓS o prefixo se ele não terminar com um
            if (!prefix.endsWith(" ") && !prefix.matches(".*<[^>]+>$")) { // Verifica se não termina com espaço ou tag colorida
                formattedNick.append(" ");
            }
        }

        formattedNick.append(originalName); // Adiciona o nome original

        if (hasSuffix) {
            // Garante um espaço ANTES do sufixo se ele não começar com um
            if (!suffix.startsWith(" ")) {
                formattedNick.append(" ");
            }
            formattedNick.append(suffix); // Adiciona sufixo (com possível espaço no início)
        }

        return formattedNick.toString();
    }
}