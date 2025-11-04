package com.realmmc.controller.shared.utils;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.session.SessionTrackerService;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class NicknameFormatter {

    private static final Logger logger = Logger.getLogger(NicknameFormatter.class.getName());

    private static final RoleService roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
    private static final SessionTrackerService sessionTrackerService = ServiceRegistry.getInstance().requireService(SessionTrackerService.class);

    private NicknameFormatter() {
    }

    private static Optional<PlayerSessionData> getSessionDataFromCacheOnly(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        Optional<PlayerSessionData> cachedData = roleService.getSessionDataFromCache(uuid);
        if (cachedData.isEmpty()) {
            logger.finest("[NicknameFormatter] Cache miss (local) para PlayerSessionData (UUID: " + uuid + "). O nome não será formatado nesta passagem.");
        }
        return cachedData;
    }

    public static String getName(UUID uuid) {
        return sessionTrackerService.getSessionField(uuid, "username")
                .orElse("Unknown");
    }

    public static String getUsername(UUID uuid) {
        String name = getName(uuid);
        return name.equals("Unknown") ? "unknown" : name.toLowerCase();
    }

    public static String getNick(UUID uuid, boolean withPrefix) {
        String originalName = getName(uuid);
        if (originalName.equals("Unknown")) {
            return originalName;
        }

        Optional<PlayerSessionData> sessionDataOpt = getSessionDataFromCacheOnly(uuid);
        if (sessionDataOpt.isEmpty()) {
            logger.finer("[NicknameFormatter] Não foi possível obter dados de sessão (cache-only) para getNick(UUID: " + uuid + "). Retornando nome original.");
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

    public static String getTagsGroup(UUID uuid) {
        Optional<PlayerSessionData> sessionDataOpt = getSessionDataFromCacheOnly(uuid);
        if (sessionDataOpt.isEmpty()) {
            logger.finer("[NicknameFormatter] Não foi possível obter dados de sessão (cache-only) para getTagsGroup(UUID: " + uuid + "). Retornando string vazia.");
            return "";
        }

        Role primaryRole = sessionDataOpt.get().getPrimaryRole();
        String prefix = primaryRole.getPrefix();
        String suffix = primaryRole.getSuffix();

        StringBuilder tags = new StringBuilder();
        boolean hasPrefix = prefix != null && !prefix.isEmpty();
        boolean hasSuffix = suffix != null && !suffix.isEmpty();

        if (hasPrefix) {
            tags.append(prefix.trim());
        }
        if (hasPrefix && hasSuffix) {
            tags.append(" ");
        }
        if (hasSuffix) {
            tags.append(suffix.trim());
        }
        return tags.toString();
    }

    public static String getFullFormattedNick(UUID uuid) {
        String originalName = getName(uuid);
        if (originalName.equals("Unknown")) {
            return originalName;
        }

        Optional<PlayerSessionData> sessionDataOpt = getSessionDataFromCacheOnly(uuid);
        if (sessionDataOpt.isEmpty()) {
            logger.finer("[NicknameFormatter] Não foi possível obter dados de sessão (cache-only) para getFullFormattedNick(UUID: " + uuid + "). Retornando nome original.");
            return originalName;
        }

        Role primaryRole = sessionDataOpt.get().getPrimaryRole();
        String prefix = primaryRole.getPrefix();
        String suffix = primaryRole.getSuffix();

        StringBuilder formattedNick = new StringBuilder();

        boolean hasPrefix = prefix != null && !prefix.isEmpty();
        boolean hasSuffix = suffix != null && !suffix.isEmpty();

        if (hasPrefix) {
            formattedNick.append(prefix);
            if (!prefix.endsWith(" ") && !prefix.matches(".*<[^>]+>$")) {
                formattedNick.append(" ");
            }
        }

        formattedNick.append(originalName);

        if (hasSuffix) {
            if (!suffix.startsWith(" ")) {
                formattedNick.append(" ");
            }
            formattedNick.append(suffix);
        }

        return formattedNick.toString();
    }
}