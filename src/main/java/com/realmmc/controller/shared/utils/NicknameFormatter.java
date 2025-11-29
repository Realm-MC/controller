package com.realmmc.controller.shared.utils;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.cosmetics.medals.Medal;
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

    private static String resolveName(UUID uuid) {
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

    private static String resolveMedalId(UUID uuid) {
        String medalId = "none";
        if (sessionTrackerService != null) {
            medalId = sessionTrackerService.getSessionField(uuid, "medal").orElse("none");
        }

        if ("none".equals(medalId) && profileService != null) {
            try {
                Optional<Profile> p = profileService.getByUuid(uuid);
                if (p.isPresent()) medalId = p.get().getEquippedMedal();
            } catch (Exception ignored) {}
        }
        return medalId;
    }

    public static String getName(UUID uuid, boolean colored) {
        String name = resolveName(uuid);
        if (name.equals("Unknown")) return name;
        if (!colored) return name;

        Optional<PlayerSessionData> data = getSessionDataOrLoad(uuid);
        if (data.isEmpty()) return name;

        String color = data.get().getPrimaryRole().getColor();
        if (color == null) color = "<gray>";

        return color + name + "<reset>";
    }

    /**
     * Retorna o nickname completo formatado:
     * [MedalPrefix] [RolePrefix] Name [RoleSuffix] [MedalSuffix]
     */
    public static String getNickname(UUID uuid, boolean includePrefixes, String fallbackName) {
        String name = (fallbackName != null) ? fallbackName : resolveName(uuid);
        if (name.equals("Unknown")) return name;

        Optional<PlayerSessionData> data = getSessionDataOrLoad(uuid);
        if (data.isEmpty()) return name;

        Role role = data.get().getPrimaryRole();
        String rolePrefix = role.getPrefix() != null ? role.getPrefix() : "";
        String roleSuffix = role.getSuffix() != null ? role.getSuffix() : "";
        String color = role.getColor() != null ? role.getColor() : "<gray>";

        StringBuilder sb = new StringBuilder();

        if (includePrefixes) {
            String medalId = resolveMedalId(uuid);
            Medal medal = null;

            if (medalId != null && !medalId.isEmpty() && !medalId.equalsIgnoreCase("none")) {
                medal = Medal.fromId(medalId).orElse(null);
                if (medal != null && !medal.getPrefix().isEmpty()) {
                    sb.append(medal.getPrefix());
                }
            }

            if (!rolePrefix.isEmpty()) {
                sb.append(rolePrefix);
                if (!rolePrefix.endsWith(" ") && !rolePrefix.matches(".*<[^>]+>$")) {
                    sb.append(" ");
                }
            }

            sb.append(color).append(name).append("<reset>");

            if (!roleSuffix.isEmpty()) {
                if (!roleSuffix.startsWith(" ")) sb.append(" ");
                sb.append(roleSuffix);
            }

            if (medal != null && !medal.getSuffix().isEmpty()) {
                sb.append(medal.getSuffix());
            }
        } else {
            sb.append(color).append(name).append("<reset>");
        }

        return sb.toString();
    }

    public static String getNickname(UUID uuid, boolean includePrefixes) {
        return getNickname(uuid, includePrefixes, null);
    }

    public static String getFullFormattedNick(UUID uuid) {
        return getNickname(uuid, true, null);
    }
}