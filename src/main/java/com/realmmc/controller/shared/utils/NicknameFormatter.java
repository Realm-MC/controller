package com.realmmc.controller.shared.utils;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.session.SessionTrackerService;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NicknameFormatter {

    public static final Logger logger = Logger.getLogger(NicknameFormatter.class.getName());

    private static RoleService roleService;
    private static SessionTrackerService sessionTrackerService;
    private static ProfileService profileService;

    private NicknameFormatter() {
    }

    private static void ensureServices() {
        if (roleService == null) {
            ServiceRegistry.getInstance().getService(RoleService.class).ifPresent(s -> roleService = s);
        }
        if (sessionTrackerService == null) {
            ServiceRegistry.getInstance().getService(SessionTrackerService.class).ifPresent(s -> sessionTrackerService = s);
        }
        if (profileService == null) {
            ServiceRegistry.getInstance().getService(ProfileService.class).ifPresent(s -> profileService = s);
        }
    }

    private static Optional<PlayerSessionData> getSessionDataOrLoad(UUID uuid) {
        ensureServices();
        if (roleService == null || uuid == null) return Optional.empty();

        Optional<PlayerSessionData> cachedData = roleService.getSessionDataFromCache(uuid);
        if (cachedData.isPresent()) return cachedData;

        try {
            return Optional.ofNullable(roleService.loadPlayerDataAsync(uuid).join());
        } catch (Exception e) {
            logger.log(Level.WARNING, "[NicknameFormatter] Failed to load session data for " + uuid, e);
            return Optional.empty();
        }
    }

    public static String getName(UUID uuid) {
        ensureServices();
        if (uuid == null) return "Unknown";

        if (sessionTrackerService != null) {
            Optional<String> redisName = sessionTrackerService.getSessionField(uuid, "username");
            if (redisName.isPresent()) return redisName.get();
        }

        if (profileService != null) {
            try {
                Optional<Profile> profile = profileService.getByUuid(uuid);
                if (profile.isPresent()) return profile.get().getName();
            } catch (Exception e) {
                logger.log(Level.WARNING, "[NicknameFormatter] Error fetching name from profile: " + e.getMessage());
            }
        }

        return "Unknown";
    }

    public static String getUsername(UUID uuid) {
        String name = getName(uuid);
        return name.equals("Unknown") ? "unknown" : name.toLowerCase();
    }

    public static String getNick(UUID uuid, boolean withPrefix) {
        String originalName = getName(uuid);
        if (originalName.equals("Unknown")) return originalName;

        Optional<PlayerSessionData> sessionDataOpt = getSessionDataOrLoad(uuid);

        if (sessionDataOpt.isEmpty()) {
            return originalName;
        }

        Role primaryRole = sessionDataOpt.get().getPrimaryRole();

        if (withPrefix) {
            String prefix = primaryRole.getPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                if (!prefix.endsWith(" ") && !prefix.matches(".*<[^>]+>$")) {
                    return prefix + " " + originalName;
                }
                return prefix + originalName;
            }
            return originalName;

        } else {
            String color = primaryRole.getColor();
            return (color != null && !color.isEmpty()) ? color + originalName : originalName;
        }
    }

    public static String getTagsGroup(UUID uuid) {
        Optional<PlayerSessionData> sessionDataOpt = getSessionDataOrLoad(uuid);

        if (sessionDataOpt.isEmpty()) return "";

        Role primaryRole = sessionDataOpt.get().getPrimaryRole();
        String prefix = primaryRole.getPrefix();
        String suffix = primaryRole.getSuffix();

        StringBuilder tags = new StringBuilder();
        boolean hasPrefix = prefix != null && !prefix.isEmpty();
        boolean hasSuffix = suffix != null && !suffix.isEmpty();

        if (hasPrefix) tags.append(prefix.trim());
        if (hasPrefix && hasSuffix) tags.append(" ");
        if (hasSuffix) tags.append(suffix.trim());

        return tags.toString();
    }

    public static String getFullFormattedNick(UUID uuid) {
        return getFullFormattedNick(uuid, getName(uuid));
    }

    public static String getFullFormattedNick(UUID uuid, String fallbackName) {
        String originalName = (fallbackName != null && !fallbackName.isEmpty()) ? fallbackName : getName(uuid);
        if (originalName.equals("Unknown")) return originalName;

        Optional<PlayerSessionData> sessionDataOpt = getSessionDataOrLoad(uuid);

        if (sessionDataOpt.isEmpty()) {
            return originalName;
        }

        return format(originalName, sessionDataOpt.get().getPrimaryRole());
    }

    public static String format(String rawName, Role role) {
        if (role == null) return rawName;

        String prefix = role.getPrefix();
        String color = role.getColor();
        String suffix = role.getSuffix();

        if (prefix == null) prefix = "";
        if (color == null) color = "<gray>";
        if (suffix == null) suffix = "";

        StringBuilder sb = new StringBuilder();

        if (!prefix.isEmpty()) {
            sb.append(prefix);
            if (!prefix.endsWith(" ") && !prefix.matches(".*<[^>]+>$")) {
                sb.append(" ");
            }
        }

        sb.append(color).append(rawName).append("<reset>");

        if (!suffix.isEmpty()) {
            if (!suffix.startsWith(" ")) {
                sb.append(" ");
            }
            sb.append(suffix);
        }

        return sb.toString();
    }
}