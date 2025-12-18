package com.realmmc.controller.modules.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.logs.LogRepository;
import com.realmmc.controller.shared.logs.LogType;
import com.realmmc.controller.shared.logs.RoleLog;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.DefaultRole;
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleRepository;
import com.realmmc.controller.shared.role.RoleType;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.utils.StringUtils;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.realmmc.controller.shared.utils.TimeUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoleService {

    private final Logger logger;
    private final RoleRepository roleRepository = new RoleRepository();
    private final LogRepository.Role logRepository = new LogRepository.Role();
    private final ProfileService profileService;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Role> roleCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSessionData> sessionCache = new ConcurrentHashMap<>();

    private final Map<UUID, CompletableFuture<PlayerSessionData>> preLoginFutures = new ConcurrentHashMap<>();
    private final Set<UUID> sentExpirationWarnings = ConcurrentHashMap.newKeySet();

    public RoleService(Logger logger) {
        this.logger = logger;
        try {
            this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        } catch (IllegalStateException e) {
            throw e;
        }

        setupDefaultRoles();
        startExpirationTask();
    }

    public LogRepository.Role getLogRepository() {
        return logRepository;
    }

    private void createLog(UUID targetUuid, String targetName, String source, LogType action, String roleName, String duration) {
        int logId = MongoSequences.getNext("logsRoles");

        String idStr = String.valueOf(logId);

        RoleLog log = RoleLog.builder()
                .id(idStr)
                .targetUuid(targetUuid)
                .targetName(targetName)
                .source(source != null ? source : "Console")
                .action(action)
                .roleName(roleName)
                .duration(duration)
                .timestamp(System.currentTimeMillis())
                .context("Global")
                .build();

        TaskScheduler.runAsync(() -> logRepository.insert(log));
    }

    public CompletableFuture<PlayerSessionData> loadPlayerDataAsync(UUID uuid) {
        if (sessionCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(sessionCache.get(uuid));
        }

        CompletableFuture<PlayerSessionData> existingFuture = preLoginFutures.get(uuid);
        if (existingFuture != null && !existingFuture.isDone()) {
            return existingFuture;
        }

        CompletableFuture<PlayerSessionData> future = CompletableFuture.supplyAsync(() -> {
            Profile p = profileService.getByUuid(uuid).orElse(null);
            if (p == null) return getDefaultSessionData(uuid);

            PlayerSessionData data = calculateSession(p);
            sessionCache.put(uuid, data);
            return data;
        }, TaskScheduler.getAsyncExecutor());

        future.whenComplete((res, ex) -> {
            preLoginFutures.remove(uuid, future);
        });

        preLoginFutures.put(uuid, future);
        return future;
    }

    public void startPreLoadingPlayerData(UUID uuid) {
        if (uuid != null) {
            loadPlayerDataAsync(uuid);
        }
    }

    public Optional<CompletableFuture<PlayerSessionData>> getPreLoginFuture(UUID uuid) {
        return Optional.ofNullable(preLoginFutures.get(uuid));
    }

    public void removePreLoginFuture(UUID uuid) {
        if (uuid != null) {
            CompletableFuture<PlayerSessionData> f = preLoginFutures.remove(uuid);
            if (f != null && !f.isDone()) {
                f.cancel(true);
            }
        }
    }

    public void clearSentWarnings(UUID uuid) {
        if (uuid != null) {
            sentExpirationWarnings.remove(uuid);
        }
    }

    public void checkAndSendLoginExpirationWarning(Object playerObj) {
        UUID uuid;
        try {
            uuid = (UUID) playerObj.getClass().getMethod("getUniqueId").invoke(playerObj);
        } catch (Exception e) { return; }

        if (uuid == null || sentExpirationWarnings.contains(uuid)) return;

        final UUID finalUuid = uuid;
        getSessionDataFromCache(uuid).ifPresentOrElse(sessionData -> {
            processExpirationWarning(playerObj, finalUuid);
        }, () -> {
            loadPlayerDataAsync(finalUuid).thenAccept(data -> {
                processExpirationWarning(playerObj, finalUuid);
            });
        });
    }

    private void processExpirationWarning(Object playerObj, UUID uuid) {
        // Implementação futura de avisos
    }

    private void updatePauseState(Profile profile) {
        if (profile.getRoles() == null) return;

        boolean hasActiveStaff = false;
        for (PlayerRole pr : profile.getRoles()) {
            if (pr.getStatus() == PlayerRole.Status.ACTIVE && !pr.hasExpired()) {
                Optional<Role> roleDef = getRole(pr.getRoleName());
                if (roleDef.isPresent() && roleDef.get().getType() == RoleType.STAFF) {
                    hasActiveStaff = true;
                    break;
                }
            }
        }

        long now = System.currentTimeMillis();

        for (PlayerRole pr : profile.getRoles()) {
            if (pr.getStatus() != PlayerRole.Status.ACTIVE) continue;

            Optional<Role> roleDefOpt = getRole(pr.getRoleName());
            if (roleDefOpt.isEmpty()) continue;
            Role roleDef = roleDefOpt.get();

            if (roleDef.getType() == RoleType.VIP && !pr.isPermanent()) {
                if (hasActiveStaff && !pr.isPaused()) {
                    long remaining = pr.getExpiresAt() - now;
                    if (remaining > 0) {
                        pr.setPaused(true);
                        pr.setPausedTimeRemaining(remaining);
                        pr.setExpiresAt(null);

                        createLog(profile.getUuid(), profile.getName(), "System", LogType.UPDATE, pr.getRoleName(), "PAUSED");
                    }
                } else if (!hasActiveStaff && pr.isPaused() && pr.getPausedTimeRemaining() != null) {
                    pr.setPaused(false);
                    pr.setExpiresAt(now + pr.getPausedTimeRemaining());
                    pr.setPausedTimeRemaining(null);

                    createLog(profile.getUuid(), profile.getName(), "System", LogType.UPDATE, pr.getRoleName(), "RESUMED");
                }
            }
        }
    }

    public void grantRole(UUID uuid, String roleName, Long durationMillis, String source) {
        if (uuid == null || roleName == null) return;
        String normalizedRoleName = roleName.toLowerCase();

        if (!roleCache.containsKey(normalizedRoleName)) {
            logger.warning("Tentativa de dar cargo inexistente: " + normalizedRoleName);
            return;
        }

        Long expiresAt = (durationMillis != null && durationMillis > 0) ? System.currentTimeMillis() + durationMillis : null;
        String durationStr = (durationMillis == null) ? "Permanente" : TimeUtils.formatDuration(durationMillis);

        profileService.getByUuid(uuid).ifPresent(profile -> {
            List<PlayerRole> roles = profile.getRoles();
            if (roles == null) roles = new ArrayList<>();

            roles.removeIf(pr -> pr.getRoleName().equalsIgnoreCase(normalizedRoleName));

            PlayerRole newRole = PlayerRole.builder()
                    .instanceId(StringUtils.generateId())
                    .roleName(normalizedRoleName)
                    .addedBy(source != null ? source : "Console")
                    .expiresAt(expiresAt)
                    .addedAt(System.currentTimeMillis())
                    .pendingNotification(true)
                    .build();

            roles.add(newRole);
            profile.setRoles(roles);

            updatePauseState(profile);
            recalculatePrimaryRole(profile);

            profileService.save(profile);
            publishNotification(uuid, normalizedRoleName);
            invalidateSession(uuid);

            createLog(uuid, profile.getName(), source, LogType.ADD, normalizedRoleName, durationStr);

            logger.info("[RoleService] Cargo " + normalizedRoleName + " adicionado a " + uuid + " por " + source);
        });
    }

    public void removeRole(UUID uuid, String roleName, String source) {
        if (uuid == null || roleName == null) return;
        String normalizedRoleName = roleName.toLowerCase();

        profileService.getByUuid(uuid).ifPresent(profile -> {
            List<PlayerRole> roles = profile.getRoles();
            if (roles == null) return;

            boolean removed = roles.removeIf(pr -> pr.getRoleName().equalsIgnoreCase(normalizedRoleName));

            if (removed) {
                updatePauseState(profile);
                ensureDefaultRole(profile);
                recalculatePrimaryRole(profile);

                profileService.save(profile);
                invalidateSession(uuid);
                publishSync(uuid);

                createLog(uuid, profile.getName(), source, LogType.REMOVE, normalizedRoleName, "N/A");

                logger.info("[RoleService] Cargo " + normalizedRoleName + " removido de " + uuid + " por " + source);
            }
        });
    }

    public void setRole(UUID uuid, String roleName, Long durationMillis, String source) {
        if (uuid == null || roleName == null) return;
        String normalizedRoleName = roleName.toLowerCase();

        if (!roleCache.containsKey(normalizedRoleName)) return;
        Long expiresAt = (durationMillis != null && durationMillis > 0) ? System.currentTimeMillis() + durationMillis : null;
        String durationStr = (durationMillis == null) ? "Permanente" : TimeUtils.formatDuration(durationMillis);

        profileService.getByUuid(uuid).ifPresent(profile -> {
            List<PlayerRole> roles = new ArrayList<>();

            PlayerRole newRole = PlayerRole.builder()
                    .instanceId(StringUtils.generateId())
                    .roleName(normalizedRoleName)
                    .addedBy(source != null ? source : "Console")
                    .expiresAt(expiresAt)
                    .addedAt(System.currentTimeMillis())
                    .pendingNotification(true)
                    .build();
            roles.add(newRole);

            if (!normalizedRoleName.equals("default")) {
                roles.add(PlayerRole.builder().roleName("default").pendingNotification(false).build());
            }

            profile.setRoles(roles);
            updatePauseState(profile);
            recalculatePrimaryRole(profile);

            profileService.save(profile);
            publishNotification(uuid, normalizedRoleName);
            invalidateSession(uuid);

            createLog(uuid, profile.getName(), source, LogType.SET, normalizedRoleName, durationStr);

            logger.info("[RoleService] Cargo de " + uuid + " definido para " + normalizedRoleName + " por " + source);
        });
    }

    public void clearRoles(UUID uuid, String source) {
        setRole(uuid, "default", null, source);
        profileService.getByUuid(uuid).ifPresent(p ->
                createLog(uuid, p.getName(), source, LogType.CLEAR, "ALL", "N/A")
        );
    }

    public void markNotificationAsRead(UUID uuid, String roleName) {
        profileService.getByUuid(uuid).ifPresent(profile -> {
            if (profile.getRoles() == null) return;

            boolean changed = false;
            for (PlayerRole pr : profile.getRoles()) {
                if (pr.getRoleName().equalsIgnoreCase(roleName) && pr.isPendingNotification()) {
                    pr.setPendingNotification(false);
                    changed = true;
                }
            }

            if (changed) {
                profileService.save(profile);
            }
        });
    }

    private void startExpirationTask() {
        TaskScheduler.runAsyncTimer(() -> {
            try {
                for (UUID uuid : sessionCache.keySet()) {
                    checkExpiration(uuid);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro na task de expiração de roles", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void checkExpiration(UUID uuid) {
        profileService.getByUuid(uuid).ifPresent(profile -> {
            if (profile.getRoles() == null) return;

            List<String> expiredRoles = new ArrayList<>();
            boolean changed = profile.getRoles().removeIf(pr -> {
                if (pr.hasExpired()) {
                    expiredRoles.add(pr.getRoleName());
                    createLog(uuid, profile.getName(), "System", LogType.EXPIRE, pr.getRoleName(), "Expired");
                    return true;
                }
                return false;
            });

            if (changed) {
                updatePauseState(profile);
                ensureDefaultRole(profile);
                recalculatePrimaryRole(profile);
                profileService.save(profile);
                invalidateSession(uuid);
                logger.info("[RoleService] Cargos expirados removidos de " + uuid + ": " + expiredRoles);
            }
        });
    }

    public void invalidateSession(UUID uuid) {
        sessionCache.remove(uuid);
        removePreLoginFuture(uuid);
    }

    public Optional<PlayerSessionData> getSessionDataFromCache(UUID uuid) {
        return Optional.ofNullable(sessionCache.get(uuid));
    }

    public boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null) return false;
        PlayerSessionData session = sessionCache.get(uuid);
        if (session == null) {
            loadPlayerDataAsync(uuid);
            return false;
        }
        return session.hasPermission(permission);
    }

    private void ensureDefaultRole(Profile profile) {
        if (profile.getRoles() == null) profile.setRoles(new ArrayList<>());
        boolean hasDefault = profile.getRoles().stream().anyMatch(pr -> pr.getRoleName().equalsIgnoreCase("default"));
        if (!hasDefault) {
            profile.getRoles().add(PlayerRole.builder().roleName("default").pendingNotification(false).build());
        }
    }

    private void recalculatePrimaryRole(Profile profile) {
        if (profile.getRoles() == null || profile.getRoles().isEmpty()) {
            profile.setPrimaryRoleName("default");
            return;
        }

        Role primary = profile.getRoles().stream()
                .filter(PlayerRole::isActive)
                .map(pr -> getRole(pr.getRoleName()).orElse(null))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Role::getWeight))
                .orElse(getRole("default").orElse(null));

        if (primary != null) {
            profile.setPrimaryRoleName(primary.getName());
        } else {
            profile.setPrimaryRoleName("default");
        }
    }

    private void publishNotification(UUID uuid, String roleName) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("uuid", uuid.toString());
            node.put("role", roleName);
            RedisPublisher.publish(RedisChannel.ROLE_NOTIFICATION, node.toString());
        } catch (Exception e) {
            logger.warning("Falha ao publicar notificação de role.");
        }
    }

    public void publishSync(UUID uuid) {
        if (uuid == null) return;
        RedisPublisher.publish(RedisChannel.ROLE_SYNC, uuid.toString());
    }

    public Optional<Role> getRole(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(roleCache.get(name.toLowerCase()));
    }

    public Collection<Role> getAllCachedRoles() {
        return Collections.unmodifiableCollection(roleCache.values());
    }

    public void setupDefaultRoles() {
        for (DefaultRole def : DefaultRole.values()) {
            Role r = def.toRole();
            roleRepository.upsert(r);
            roleCache.put(r.getName(), r);
        }
        loadRolesToCache();
    }

    public void loadRolesToCache() {
        roleCache.clear();
        roleRepository.collection().find().forEach(role -> {
            roleCache.put(role.getName().toLowerCase(), role);
        });
        for (Role role : roleCache.values()) {
            role.setCachedEffectivePermissions(calculateEffectivePermissionsForRole(role));
        }
        if (!roleCache.containsKey("default")) {
            Role def = DefaultRole.DEFAULT.toRole();
            def.setCachedEffectivePermissions(calculateEffectivePermissionsForRole(def));
            roleCache.put("default", def);
        }
        logger.info("[RoleService] Roles carregados e calculados: " + roleCache.size());
    }

    private Set<String> calculateEffectivePermissionsForRole(Role role) {
        return calculatePermissionsRecursive(role, new HashSet<>());
    }

    private Set<String> calculatePermissionsRecursive(Role role, Set<String> visitedRoles) {
        Set<String> perms = new HashSet<>();
        if (!visitedRoles.add(role.getName().toLowerCase())) {
            logger.warning("[RoleService] CICLO DE HERANÇA DETECTADO! Grupo '" + role.getName() + "' ignorado.");
            return perms;
        }
        if (role.getPermissions() != null) {
            perms.addAll(role.getPermissions());
        }
        if (role.getInheritance() != null) {
            for (String parentName : role.getInheritance()) {
                getRole(parentName).ifPresent(parentRole -> {
                    perms.addAll(calculatePermissionsRecursive(parentRole, visitedRoles));
                });
            }
        }
        return perms;
    }

    private PlayerSessionData calculateSession(Profile profile) {
        Set<String> finalPerms = new HashSet<>();
        if (profile.getRoles() != null) {
            for (PlayerRole pr : profile.getRoles()) {
                if (pr.isActive()) {
                    getRole(pr.getRoleName()).ifPresent(r -> finalPerms.addAll(r.getCachedEffectivePermissions()));
                }
            }
        }
        recalculatePrimaryRole(profile);
        Role primary = getRole(profile.getPrimaryRoleName()).orElse(getRole("default").get());
        return new PlayerSessionData(profile.getUuid(), primary, finalPerms);
    }

    public PlayerSessionData getDefaultSessionData(UUID uuid) {
        Role def = getRole("default").orElse(DefaultRole.DEFAULT.toRole());
        return new PlayerSessionData(uuid, def, new HashSet<>(def.getPermissions()));
    }

    public void shutdown() {
        preLoginFutures.clear();
        sessionCache.clear();
        sentExpirationWarnings.clear();
    }
}