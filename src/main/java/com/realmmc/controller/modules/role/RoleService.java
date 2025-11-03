package com.realmmc.controller.modules.role;

import com.mongodb.MongoException;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.*;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.utils.TimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoleService {

    private final Logger logger;
    private final RoleRepository roleRepository;
    private final ProfileService profileService;
    private final ExecutorService asyncExecutor;

    private final Map<String, Role> roleCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSessionData> sessionCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerSessionData>> preLoginFutures = new ConcurrentHashMap<>();

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String REDIS_SESSION_PREFIX = "controller:session:data:";
    private static final int REDIS_SESSION_TTL_SECONDS = (int) TimeUnit.HOURS.toSeconds(1);

    private final Map<UUID, Set<String>> sentExpirationWarnings = new ConcurrentHashMap<>();
    private ScheduledFuture<?> expirationCheckTask = null;
    private static final long WARN_THRESHOLD_1_DAY = TimeUnit.DAYS.toMillis(1);
    private static final long WARN_THRESHOLD_1_HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long WARN_THRESHOLD_15_MIN = TimeUnit.MINUTES.toMillis(15);
    private static final long EXPIRATION_CHECK_INTERVAL_MINUTES = 5;

    public RoleService(Logger logger) {
        this.logger = logger;
        this.roleRepository = new RoleRepository();
        try {
            this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
            this.asyncExecutor = TaskScheduler.getAsyncExecutor();
            logger.info("[RoleService] Initialized using TaskScheduler ExecutorService.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[RoleService] Critical error initializing RoleService: Dependent service (ProfileService or TaskScheduler) not found!", e);
            throw e;
        }
        startExpirationWarningTask();
    }

    public ExecutorService getAsyncExecutor() {
        return this.asyncExecutor;
    }

    private Optional<Profile> getProfileFromDb(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try {
            Optional<Profile> profileOpt = profileService.getByUuid(uuid);
            if (profileOpt.isEmpty()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                profileOpt = profileService.getByUuid(uuid);
                if (profileOpt.isEmpty()) {
                    logger.log(Level.FINER, "[RoleService] Profile not found in DB for {0} (even after retry)", uuid);
                }
            }
            return profileOpt;
        }
        catch (MongoException me) { logger.log(Level.SEVERE, "[RoleService] MongoDB error fetching profile: " + uuid, me); return Optional.empty(); }
        catch (NoSuchElementException nse) {
            logger.log(Level.FINE, "[RoleService] Profile not found in DB for {0} (NoSuchElementException)", uuid);
            return Optional.empty();
        }
        catch (Exception e) { logger.log(Level.SEVERE, "[RoleService] Unexpected error fetching profile: " + uuid, e); return Optional.empty(); }
    }
    private void saveProfileWithCatch(Profile profile) {
        if (profile == null) return;
        try { profileService.save(profile); }
        catch (Exception e) { logger.log(Level.SEVERE, "[RoleService] (Caught) Error saving profile: " + profile.getUuid(), e); }
    }
    public void publishSync(UUID uuid) {
        if (uuid == null) return;
        try { RedisPublisher.publish(RedisChannel.ROLE_SYNC, uuid.toString()); logger.finer("[RoleService] ROLE_SYNC message published for " + uuid); }
        catch (Exception e) { logger.log(Level.WARNING, "[RoleService] Failed to publish ROLE_SYNC message for " + uuid, e); }
    }

    public void setupDefaultRoles() {
        logger.info("[RoleService] Verifying and synchronizing default groups (DefaultRole) with MongoDB...");
        int createdCount = 0;
        int updatedCount = 0;
        int errorCount = 0;

        for (DefaultRole defaultRole : DefaultRole.values()) {
            String roleNameLower = defaultRole.getName().toLowerCase();
            try {
                Optional<Role> existingRoleOpt = roleRepository.findByName(roleNameLower);
                Role enumRoleData = defaultRole.toRole();
                if (enumRoleData.getPermissions() == null) enumRoleData.setPermissions(new ArrayList<>());
                if (enumRoleData.getInheritance() == null) enumRoleData.setInheritance(new ArrayList<>());
                enumRoleData.setName(roleNameLower);

                if (existingRoleOpt.isEmpty()) {
                    roleRepository.upsert(enumRoleData);
                    roleCache.put(roleNameLower, enumRoleData);
                    createdCount++;
                    logger.fine("[RoleService] Default group '" + roleNameLower + "' created in MongoDB and cached.");
                } else {
                    Role existingRole = existingRoleOpt.get();
                    if (existingRole.getPermissions() == null) existingRole.setPermissions(new ArrayList<>());
                    if (existingRole.getInheritance() == null) existingRole.setInheritance(new ArrayList<>());

                    boolean needsUpdate = false;
                    if (existingRole.getWeight() != enumRoleData.getWeight() ||
                            !Objects.equals(existingRole.getDisplayName(), enumRoleData.getDisplayName()) ||
                            !Objects.equals(existingRole.getPrefix(), enumRoleData.getPrefix()) ||
                            !Objects.equals(existingRole.getSuffix(), enumRoleData.getSuffix()) ||
                            !Objects.equals(existingRole.getColor(), enumRoleData.getColor()) ||
                            existingRole.getType() != enumRoleData.getType()) {
                        needsUpdate = true;
                        existingRole.setDisplayName(enumRoleData.getDisplayName());
                        existingRole.setPrefix(enumRoleData.getPrefix());
                        existingRole.setSuffix(enumRoleData.getSuffix());
                        existingRole.setColor(enumRoleData.getColor());
                        existingRole.setType(enumRoleData.getType());
                        existingRole.setWeight(enumRoleData.getWeight());
                    }

                    Set<String> existingInheritance = new HashSet<>(existingRole.getInheritance());
                    Set<String> enumInheritance = new HashSet<>(enumRoleData.getInheritance());
                    if (!existingInheritance.equals(enumInheritance)) {
                        existingRole.setInheritance(new ArrayList<>(enumRoleData.getInheritance()));
                        needsUpdate = true;
                    }

                    Set<String> currentPermsSet = new HashSet<>(existingRole.getPermissions());
                    boolean permsChanged = false;
                    List<String> enumPerms = enumRoleData.getPermissions();
                    if (enumPerms != null) {
                        for (String enumPerm : enumPerms) {
                            if (enumPerm != null && currentPermsSet.add(enumPerm.toLowerCase())) {
                                permsChanged = true;
                            }
                        }
                    }
                    if (permsChanged) {
                        existingRole.setPermissions(new ArrayList<>(currentPermsSet));
                        needsUpdate = true;
                    }

                    if (needsUpdate) {
                        roleRepository.upsert(existingRole);
                        roleCache.put(roleNameLower, existingRole);
                        updatedCount++;
                        logger.fine("[RoleService] Default group '" + roleNameLower + "' updated in MongoDB and cached.");
                    } else {
                        roleCache.putIfAbsent(roleNameLower, existingRole);
                    }
                }
            } catch (MongoException e) {
                logger.log(Level.SEVERE, "[RoleService] MongoDB error synchronizing default group: " + roleNameLower, e);
                errorCount++;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[RoleService] Unexpected error synchronizing default group: " + roleNameLower, e);
                errorCount++;
            }
        }

        if (createdCount > 0) logger.info("[RoleService] " + createdCount + " new default groups were created in MongoDB.");
        if (updatedCount > 0) logger.info("[RoleService] " + updatedCount + " default groups were updated in MongoDB.");
        if (errorCount > 0) logger.severe("[RoleService] " + errorCount + " ERRORS occurred during default group synchronization!");
        if (createdCount == 0 && updatedCount == 0 && errorCount == 0) logger.info("[RoleService] Default groups in MongoDB are synchronized with the Enum.");

        roleCache.computeIfAbsent("default", k -> {
            logger.warning("[RoleService] 'default' Role NOT in cache AFTER setupDefaultRoles! Using Enum fallback.");
            return DefaultRole.DEFAULT.toRole();
        });

        loadRolesToCache();

        logger.info("[RoleService] Default roles synchronization complete. Total groups in cache: " + roleCache.size());
    }

    public void loadRolesToCache() {
        logger.info("[RoleService] Reloading roles from MongoDB to cache and recalculating inheritance...");
        AtomicInteger loadedCount = new AtomicInteger();
        int errorCount = 0;
        Set<String> keysInDb = new HashSet<>();
        try {
            List<Role> rolesFromDb = roleRepository.collection().find().into(new ArrayList<>());

            for(Role role : rolesFromDb) {
                if (role != null && role.getName() != null) {
                    String nameLower = role.getName().toLowerCase();
                    keysInDb.add(nameLower);
                    if (role.getPermissions() == null) role.setPermissions(new ArrayList<>());
                    if (role.getInheritance() == null) role.setInheritance(new ArrayList<>());
                    role.setCachedEffectivePermissions(null);
                    roleCache.put(nameLower, role);
                    loadedCount.getAndIncrement();
                } else {
                    logger.warning("[RoleService] Invalid role loaded from MongoDB (null or no name), ignored.");
                }
            }

            roleCache.entrySet().removeIf(entry ->
                    !keysInDb.contains(entry.getKey()) &&
                            Arrays.stream(DefaultRole.values()).noneMatch(dr -> dr.getName().equalsIgnoreCase(entry.getKey()))
            );

            for (Role role : rolesFromDb) {
                if (role != null && role.getName() != null) {
                    Set<String> calculatedPermissions = calculateEffectivePermissionsForRole(role);
                    role.setCachedEffectivePermissions(calculatedPermissions);
                    roleCache.put(role.getName().toLowerCase(), role);
                    logger.finer("[RoleService] Inheritance pre-calculated for role: " + role.getName() + " (Total perms: " + calculatedPermissions.size() + ")");
                }
            }

            logger.info("[RoleService] Loaded/Updated " + loadedCount + " groups from MongoDB to cache.");
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "[RoleService] MongoDB error loading groups to cache!", e);
            errorCount++;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[RoleService] Unexpected error loading groups to cache!", e);
            errorCount++;
        }

        if (!roleCache.containsKey("default")) {
            logger.severe("[RoleService] CRITICAL: 'default' Role NOT present in cache AFTER loading! Using Enum fallback.");
            Role defaultRole = DefaultRole.DEFAULT.toRole();
            defaultRole.setCachedEffectivePermissions(calculateEffectivePermissionsForRole(defaultRole));
            roleCache.put("default", defaultRole);
        }

        if (errorCount > 0) {
            logger.severe("[RoleService] Errors occurred while loading roles from MongoDB. Cache may be incomplete!");
        }
        logger.info("[RoleService] Total groups in cache after full load: " + roleCache.size());

        // Publicar sinal de que o cache global de roles foi atualizado (Bug C)
        try { RedisPublisher.publish(RedisChannel.ROLES_UPDATE, "reload"); }
        catch (Exception e) { logger.log(Level.WARNING, "[RoleService] Failed to publish ROLES_UPDATE signal.", e); }
    }

    public Optional<Role> getRole(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        return Optional.ofNullable(roleCache.get(name.toLowerCase()));
    }
    public Collection<Role> getAllCachedRoles() {
        return Collections.unmodifiableCollection(roleCache.values());
    }

    public void invalidateSession(UUID uuid) {
        if (uuid == null) return;
        sessionCache.remove(uuid);
        removePreLoginFuture(uuid);
        runRedisAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.del(REDIS_SESSION_PREFIX + uuid.toString());
                logger.finer("[RoleService:Invalidate] Redis DADOS session cache invalidated for " + uuid);
            } catch (JedisException e) {
                logger.log(Level.WARNING, "[RoleService:Invalidate] Failed to invalidate Redis DADOS session cache for " + uuid, e);
            }
        }, "invalidate-session-data-" + uuid);
        logger.fine("[RoleService:Invalidate] Session DADOS cache (Java + Redis) invalidated for " + uuid);
    }

    public Optional<PlayerSessionData> getSessionDataFromCache(UUID uuid) {
        return Optional.ofNullable(sessionCache.get(uuid));
    }

    public void startPreLoadingPlayerData(UUID uuid) {
        if (uuid == null) return;
        final UUID finalUuid = uuid;
        preLoginFutures.computeIfAbsent(finalUuid, id -> {
            logger.finer("[RoleService:PreLoad] Starting permission pre-loading (loadPlayerDataAsync) for " + id);
            return loadPlayerDataAsync(id).whenComplete((data, error) -> {
                if (error != null) {
                    Throwable cause = (error instanceof CompletionException && error.getCause() != null) ? error.getCause() : error;
                    logger.log(Level.WARNING, "[RoleService:PreLoad] Error during pre-loading for " + id, cause);
                } else {
                    logger.finer("[RoleService:PreLoad] Pre-loading completed for " + id);
                }
            });
        });
    }

    public Optional<CompletableFuture<PlayerSessionData>> getPreLoginFuture(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(preLoginFutures.get(uuid));
    }

    public void removePreLoginFuture(UUID uuid) {
        if (uuid != null) {
            CompletableFuture<?> future = preLoginFutures.remove(uuid);
            if (future != null && !future.isDone()) {
                future.cancel(false);
                logger.finer("[RoleService:PreLoad] Pre-login future canceled/removed for " + uuid);
            } else if (future != null) {
                logger.finer("[RoleService:PreLoad] Pre-login future already completed removed for " + uuid);
            }
        }
    }

    public CompletableFuture<PlayerSessionData> loadPlayerDataAsync(UUID uuid) {
        if (uuid == null) {
            logger.warning("[RoleService:Load] Attempting to load PlayerData with null UUID. Returning default data.");
            return CompletableFuture.completedFuture(getDefaultSessionData(null));
        }

        PlayerSessionData cachedData = sessionCache.get(uuid);

        if (cachedData != null && cachedData.getCalculatedAt().isAfter(Instant.now().minus(60, TimeUnit.MINUTES.toChronoUnit()))) {
            logger.finest("[RoleService:Load] Java Cache HIT for " + uuid);
            return CompletableFuture.completedFuture(cachedData);
        }

        final UUID finalUuid = uuid;

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                String jsonData = jedis.get(REDIS_SESSION_PREFIX + finalUuid.toString());
                if (jsonData != null && !jsonData.isEmpty()) {
                    try {
                        PlayerSessionData redisData = jsonMapper.readValue(jsonData, PlayerSessionData.class);
                        if (redisData.getCalculatedAt().isAfter(Instant.now().minus(60, TimeUnit.MINUTES.toChronoUnit()))) {
                            sessionCache.put(finalUuid, redisData);
                            logger.fine("[RoleService:Load] Redis Cache HIT for " + finalUuid);
                            return redisData;
                        } else {
                            logger.fine("[RoleService:Load] Redis Cache STALE for " + finalUuid + ". Recalculating.");
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "[RoleService:Load] Failed to deserialize PlayerSessionData from Redis for " + finalUuid + ". Recalculating.", e);
                        try { jedis.del(REDIS_SESSION_PREFIX + finalUuid.toString()); } catch (Exception ignored) {}
                    }
                }
            } catch (JedisException e) {
                logger.log(Level.SEVERE, "[RoleService:Load] Redis connection error fetching session DADOS for " + finalUuid + ". Recalculating (Mongo fallback).", e);
            }

            logger.fine("[RoleService:Load] Cache MISS (Java+Redis) or STALE for " + finalUuid + ". Calculating via MongoDB.");
            try {
                final AtomicBoolean profileNeedsSave = new AtomicBoolean(false);
                final List<PlayerRole> playerRolesForCalc = fetchAndPreparePlayerRoles(finalUuid, profileNeedsSave);
                updatePauseStateForCalculation(finalUuid, playerRolesForCalc);
                PlayerSessionData calculatedData = calculatePermissionsExplicit(finalUuid, playerRolesForCalc);

                Optional<Profile> profileOpt = getProfileFromDb(finalUuid);
                if (profileOpt.isPresent()) {
                    Profile profile = profileOpt.get();
                    String newPrimaryName = calculatedData.getPrimaryRole().getName();
                    if (!Objects.equals(profile.getPrimaryRoleName(), newPrimaryName)) {
                        profile.setPrimaryRoleName(newPrimaryName);
                        profileNeedsSave.set(true);
                        logger.fine("[RoleService:Load] primaryRoleName updated to '" + newPrimaryName + "' for " + finalUuid + " (due to cache miss/recalc).");
                    }
                } else {
                    if (profileNeedsSave.get()) {
                        logger.log(Level.WARNING, "[RoleService:Load] Profile not found for {0} DURING final calculation, but pending changes (expired roles?) will not be saved.", finalUuid);
                    } else {
                        logger.log(Level.WARNING, "[RoleService:Load] Profile not found for {0} during final calculation. Using calculated data without saving primaryRoleName.", finalUuid);
                    }
                }

                if (profileNeedsSave.get()) {
                    if (profileOpt.isPresent()) {
                        saveProfileWithCatch(profileOpt.get());
                        logger.fine("[RoleService:Load] Profile saved (via cache miss/recalc) for " + finalUuid + ".");
                    } else {
                        logger.log(Level.WARNING, "[RoleService:Load] Profile not found for {0}, failed to save changes (expired roles/default/primaryName).", finalUuid);
                    }
                }

                saveSessionToRedis(finalUuid, calculatedData);
                sessionCache.put(finalUuid, calculatedData);

                logger.fine("[RoleService:Load] Cache (Java+Redis) populated for " + finalUuid + " after calculation.");
                return calculatedData;

            } catch (Exception ex) {
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
                boolean profileNotFoundDuringLoad = false;
                if (cause != null && (cause instanceof NoSuchElementException ||
                        (cause.getMessage() != null && cause.getMessage().contains("Perfil não encontrado")))) {
                    profileNotFoundDuringLoad = true;
                    logger.log(Level.WARNING, "[RoleService:Load] Profile not found for {0} during calculation. Using default data temporarily.", finalUuid);
                } else {
                    logger.log(Level.SEVERE, "[RoleService:Load] Fatal error during permission calculation (Mongo) for " + finalUuid, cause);
                }

                sessionCache.remove(finalUuid);

                if (!profileNotFoundDuringLoad) {
                    logger.log(Level.SEVERE, "[RoleService:Load] Returning default data as fallback due to non-'profile not found' error for " + finalUuid);
                }
                return getDefaultSessionData(finalUuid);
            }
        }, asyncExecutor);
    }

    private List<PlayerRole> fetchAndPreparePlayerRoles(UUID uuid, AtomicBoolean profileNeedsSave) {
        Optional<Profile> profileOpt = getProfileFromDb(uuid);
        List<PlayerRole> playerRoles;

        if (profileOpt.isEmpty()) {
            logger.warning("[RoleService:Fetch] Profile not found in DB for " + uuid + ". Using temporary 'default' group for calculation.");
            playerRoles = new ArrayList<>(Collections.singletonList(
                    PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build()
            ));
        } else {
            Profile profile = profileOpt.get();
            playerRoles = (profile.getRoles() != null) ? new ArrayList<>(profile.getRoles()) : new ArrayList<>();

            boolean hasDefault = playerRoles.stream()
                    .anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()));
            boolean hasOtherActiveNonPaused = playerRoles.stream()
                    .anyMatch(pr -> pr != null && !"default".equalsIgnoreCase(pr.getRoleName()) && pr.isActive());

            if (!hasDefault) {
                playerRoles.add(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build());
                profileNeedsSave.set(true);
                logger.warning("[RoleService:Fetch] Profile for " + uuid + " without 'default' group found. Adding it.");
            } else {
                if (!hasOtherActiveNonPaused) {
                    playerRoles.stream()
                            .filter(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() != PlayerRole.Status.ACTIVE)
                            .findFirst().ifPresent(defaultRole -> {
                                defaultRole.setStatus(PlayerRole.Status.ACTIVE);
                                defaultRole.setRemovedAt(null);
                                defaultRole.setPaused(false);
                                defaultRole.setPausedTimeRemaining(null);
                                profileNeedsSave.set(true);
                                logger.fine("[RoleService:Fetch] 'default' Role reactivated for " + uuid + " as no other active roles were found.");
                            });
                }
            }

            boolean expiredFound = false;
            for (PlayerRole pr : playerRoles) {
                if (pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && pr.hasExpiredTime()) {
                    pr.setStatus(PlayerRole.Status.EXPIRED);
                    profileNeedsSave.set(true);
                    expiredFound = true;
                    logger.fine("[RoleService:Fetch] Role '" + pr.getRoleName() + "' for " + uuid + " was marked as EXPIRED during fetch/prepare.");
                }
            }
            if(expiredFound) {
                boolean hasOtherStillActive = playerRoles.stream()
                        .anyMatch(pr -> pr != null && !"default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() == PlayerRole.Status.ACTIVE);
                if (!hasOtherStillActive) {
                    playerRoles.stream()
                            .filter(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() != PlayerRole.Status.ACTIVE)
                            .findFirst().ifPresent(defaultRole -> {
                                defaultRole.setStatus(PlayerRole.Status.ACTIVE);
                                defaultRole.setRemovedAt(null);
                                defaultRole.setPaused(false);
                                defaultRole.setPausedTimeRemaining(null);
                                logger.fine("[RoleService:Fetch] 'default' Role reactivated for " + uuid + " after expiration of other roles.");
                            });
                }
            }
        }
        return playerRoles;
    }

    public boolean updatePauseState(UUID uuid, List<PlayerRole> playerRoles) {
        if (playerRoles == null || playerRoles.isEmpty()) return false;
        boolean stateChanged = false;
        boolean isStaff = playerRoles.stream()
                .filter(pr -> pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && !pr.isPaused())
                .map(pr -> getRole(pr.getRoleName()))
                .filter(Optional::isPresent).map(Optional::get)
                .anyMatch(r -> r.getType() == RoleType.STAFF);

        for (PlayerRole pr : playerRoles) {
            if (pr == null || pr.getStatus() != PlayerRole.Status.ACTIVE || pr.hasExpiredTime()) continue;
            Optional<Role> roleOpt = getRole(pr.getRoleName());
            if (roleOpt.isEmpty()) continue;
            Role role = roleOpt.get();
            boolean initialState = pr.isPaused();
            boolean shouldBePaused = (role.getType() == RoleType.VIP && !pr.isPermanent() && isStaff);

            if (shouldBePaused && !initialState) {
                pr.setPaused(true);
                if (pr.getExpiresAt() != null) {
                    long remaining = pr.getExpiresAt() - System.currentTimeMillis();
                    pr.setPausedTimeRemaining(Math.max(0, remaining));
                    logger.finer("[RoleService:Pause] Pausing role '" + pr.getRoleName() + "' for " + uuid +". Remaining time: " + pr.getPausedTimeRemaining() + "ms");
                } else {
                    pr.setPausedTimeRemaining(null);
                    logger.finer("[RoleService:Pause] Pausing permanent VIP role '" + pr.getRoleName() + "' for " + uuid + ".");
                }
                stateChanged = true;
            } else if (!shouldBePaused && initialState) {
                pr.setPaused(false);
                Long remainingTime = pr.getPausedTimeRemaining();
                if (remainingTime != null && remainingTime > 0) {
                    long newExpiresAt = System.currentTimeMillis() + remainingTime;
                    pr.setExpiresAt(newExpiresAt);
                    logger.finer("[RoleService:Pause] Unpausing role '" + pr.getRoleName() + "' for " + uuid + ". New expiration: " + new Date(newExpiresAt));
                } else if (remainingTime != null) {
                    pr.setExpiresAt(System.currentTimeMillis() - 1);
                    pr.setStatus(PlayerRole.Status.EXPIRED);
                    logger.finer("[RoleService:Pause] Unpausing role '" + pr.getRoleName() + "' for " + uuid + ", marked as expired (time ran out during pause).");
                } else {
                    pr.setExpiresAt(null);
                    logger.finer("[RoleService:Pause] Unpausing permanent VIP role '" + pr.getRoleName() + "' for " + uuid + ".");
                }
                pr.setPausedTimeRemaining(null);
                stateChanged = true;
            }
        }
        return stateChanged;
    }

    private void updatePauseStateForCalculation(UUID uuid, List<PlayerRole> playerRoles) {
        if (playerRoles == null || playerRoles.isEmpty()) return;
        boolean isStaff = playerRoles.stream()
                .filter(pr -> pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE)
                .map(pr -> getRole(pr.getRoleName()))
                .filter(Optional::isPresent).map(Optional::get)
                .anyMatch(r -> r.getType() == RoleType.STAFF);

        for (PlayerRole pr : playerRoles) {
            if (pr == null || pr.getStatus() != PlayerRole.Status.ACTIVE || pr.hasExpiredTime()) continue;
            Optional<Role> roleOpt = getRole(pr.getRoleName());
            if(roleOpt.isEmpty()) continue;
            Role role = roleOpt.get();
            pr.setPaused(role.getType() == RoleType.VIP && !pr.isPermanent() && isStaff);
        }
    }

    public PlayerSessionData calculatePermissionsExplicit(UUID uuid, List<PlayerRole> playerRoles) {
        Set<String> effectivePermissions = new HashSet<>();
        List<Role> activeRolesFound = new ArrayList<>();
        List<PlayerRole> rolesToProcess = (playerRoles == null) ? new ArrayList<>() : playerRoles;

        for (PlayerRole pr : rolesToProcess) {
            if (pr != null && pr.isActive()) {
                getRole(pr.getRoleName()).ifPresent(activeRolesFound::add);
            }
        }

        if (activeRolesFound.isEmpty()) {
            getRole("default").ifPresentOrElse( activeRolesFound::add, () -> {
                        logger.severe("[RoleService:Calc] CRITICAL: 'default' Role NOT FOUND in cache during calculation for UUID: " + uuid);
                        activeRolesFound.add(Role.builder().name("default").displayName("Membro").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build());
                    }
            );
        }

        Role primaryRole = activeRolesFound.stream()
                .max(Comparator.comparingInt(Role::getWeight))
                .orElseGet(() -> {
                    logger.warning("[RoleService:Calc] No active role found to determine primary for UUID: " + uuid + ". Using default.");
                    return roleCache.getOrDefault("default", Role.builder().name("default").displayName("Membro").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build());
                });

        // USAR O CACHE DE PERMISSÕES PRÉ-CALCULADAS (Otimização)
        for (Role activeRole : activeRolesFound) {
            Set<String> perms = activeRole.getCachedEffectivePermissions();
            if (perms != null) {
                effectivePermissions.addAll(perms);
            }
        }

        return new PlayerSessionData(uuid, primaryRole, effectivePermissions);
    }

    /**
     * Calcula as permissões de um grupo recursivamente (com herança).
     * Chamada APENAS durante o loadRolesToCache.
     */
    private Set<String> calculateEffectivePermissionsForRole(Role role) {
        Set<String> permissions = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collectPermissionsRecursiveExplicit(role, permissions, visited);
        return Collections.unmodifiableSet(permissions);
    }

    /**
     * Implementação recursiva para coletar permissões de um grupo e seus pais.
     * (Usado APENAS no loadRolesToCache para pré-cálculo)
     */
    public void collectPermissionsRecursiveExplicit(Role role, Set<String> permissions, Set<String> visited) {
        if (role == null || !visited.add(role.getName().toLowerCase())) {
            return;
        }
        if (role.getPermissions() != null) {
            role.getPermissions().stream()
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .forEach(permissions::add);
        }
        if (role.getInheritance() != null) {
            for (String inheritedRoleName : role.getInheritance()) {
                if (inheritedRoleName != null && !inheritedRoleName.isBlank()) {
                    getRole(inheritedRoleName).ifPresent(parentRole ->
                            collectPermissionsRecursiveExplicit(parentRole, permissions, visited)
                    );
                }
            }
        }
    }

    public boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null || permission.isEmpty()) { return false; }

        PlayerSessionData sessionData = sessionCache.get(uuid);

        if (sessionData != null && sessionData.getCalculatedAt().isAfter(Instant.now().minus(60, TimeUnit.MINUTES.toChronoUnit()))) {
            try {
                return sessionData.hasPermission(permission);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[RoleService:CheckPerm] Error calling sessionData.hasPermission('" + permission + "') for " + uuid + " (L1 cache)", e);
                return false;
            }
        }

        logger.log(Level.WARNING, "[RoleService:CheckPerm] Session cache MISS (Java) or STALE for player: {0} checking ''{1}''. Attempting to load/recalculate (Redis/Mongo)...", new Object[]{uuid, permission});
        try {
            PlayerSessionData loadedData = loadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);

            if (loadedData != null) {
                logger.log(Level.INFO, "[RoleService:CheckPerm] Session cache recovered/recalculated sync for {0} after miss/stale.", uuid);
                return loadedData.hasPermission(permission);
            } else {
                logger.log(Level.SEVERE, "[RoleService:CheckPerm] CRITICAL failure loading/recalculating data sync after cache miss/stale for {0}. Permission denied.", uuid);
                return false;
            }
        } catch (TimeoutException te) {
            logger.log(Level.SEVERE, "[RoleService:CheckPerm] Timeout during sync load/recalc after cache miss/stale for {0}. Permission denied.", uuid);
            return false;
        } catch (Exception e) {
            Throwable cause = (e instanceof ExecutionException && e.getCause() != null) ? e.getCause() : e;
            logger.log(Level.SEVERE, "[RoleService:CheckPerm] Error during sync load/recalc after cache miss/stale for {0}", new Object[]{uuid});
            logger.log(Level.SEVERE, "[RoleService:CheckPerm] Cause:", cause);
            return false;
        }
    }

    private synchronized void startExpirationWarningTask() {
        if (expirationCheckTask != null && !expirationCheckTask.isDone()) {
            logger.fine("[RoleService:Expiry] Expiration warning task is already running.");
            return;
        }
        try {
            expirationCheckTask = TaskScheduler.runAsyncTimer(() -> {
                try {
                    checkAndSendExpirationWarnings();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[RoleService:Expiry] Error in periodic role expiration check task.", e);
                }
            }, EXPIRATION_CHECK_INTERVAL_MINUTES, EXPIRATION_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
            logger.info("[RoleService:Expiry] Role expiration warning task started (interval: " + EXPIRATION_CHECK_INTERVAL_MINUTES + " minutes).");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[RoleService:Expiry] Failed to start expiration warning task: TaskScheduler not available?", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[RoleService:Expiry] Unexpected error scheduling expiration warning task.", e);
        }
    }

    private void checkAndSendExpirationWarnings() {
        long now = System.currentTimeMillis();
        Collection<?> onlinePlayersGeneric;

        try {
            onlinePlayersGeneric = Bukkit.getOnlinePlayers();
        } catch (Exception | NoClassDefFoundError e) {
            try {
                ProxyServer proxy = ServiceRegistry.getInstance().requireService(ProxyServer.class);
                onlinePlayersGeneric = proxy.getAllPlayers();
            } catch (Exception | NoClassDefFoundError e2) {
                logger.finest("[RoleService:Expiry] Could not get online player list to check role expiration.");
                return;
            }
        }

        if (onlinePlayersGeneric == null || onlinePlayersGeneric.isEmpty()) {
            return;
        }

        for (Object playerObj : onlinePlayersGeneric) {
            UUID playerUuid = null;
            Object platformPlayerObject = null;

            if (playerObj instanceof Player) {
                Player bukkitPlayer = (Player) playerObj;
                if (!bukkitPlayer.isOnline()) continue;
                playerUuid = bukkitPlayer.getUniqueId();
                platformPlayerObject = bukkitPlayer;
            } else if (playerObj instanceof com.velocitypowered.api.proxy.Player) {
                com.velocitypowered.api.proxy.Player velocityPlayer = (com.velocitypowered.api.proxy.Player) playerObj;
                if (!velocityPlayer.isActive()) continue;
                playerUuid = velocityPlayer.getUniqueId();
                platformPlayerObject = velocityPlayer;
            }

            if (playerUuid == null || platformPlayerObject == null) continue;

            final UUID finalUuid = playerUuid;
            final Object finalPlayerObj = platformPlayerObject;

            profileService.getByUuid(finalUuid).ifPresent(profile -> {
                if (profile.getRoles() == null) return;
                profile.getRoles().stream()
                        .filter(pr -> pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && !pr.isPermanent() && !pr.isPaused())
                        .forEach(expiringRole -> {
                            if (expiringRole.getExpiresAt() == null) return;
                            long expiresInMillis = expiringRole.getExpiresAt() - now;
                            checkAndSendWarning(finalPlayerObj, finalUuid, expiringRole, expiresInMillis, WARN_THRESHOLD_1_DAY, MessageKey.ROLE_WARN_EXPIRING_DAY);
                            checkAndSendWarning(finalPlayerObj, finalUuid, expiringRole, expiresInMillis, WARN_THRESHOLD_1_HOUR, MessageKey.ROLE_WARN_EXPIRING_HOUR);
                            checkAndSendWarning(finalPlayerObj, finalUuid, expiringRole, expiresInMillis, WARN_THRESHOLD_15_MIN, MessageKey.ROLE_WARN_EXPIRING_MINUTES);
                        });
            });
        }
    }

    private void checkAndSendWarning(Object playerObj, UUID playerUuid, PlayerRole role, long expiresInMillis, long thresholdMillis, MessageKey messageKey) {
        if (expiresInMillis > 0 && expiresInMillis <= thresholdMillis) {
            String warningId = role.getRoleName() + ":" + thresholdMillis;
            Set<String> playerWarnings = sentExpirationWarnings.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
            if (playerWarnings.add(warningId)) {
                getRole(role.getRoleName()).ifPresent(roleInfo -> {
                    String timeRemaining = TimeUtils.formatDuration(expiresInMillis);
                    Messages.send(playerObj, Message.of(messageKey)
                            .with("group", roleInfo.getDisplayName())
                            .with("time", timeRemaining));
                    logger.fine("[RoleService:Expiry] Expiration warning ("+ messageKey.getKey() +") sent to " + playerUuid + " about role " + role.getRoleName());
                });
            }
        }
    }

    public void clearSentWarnings(UUID uuid) {
        if (uuid != null) {
            Set<String> removed = sentExpirationWarnings.remove(uuid);
            if (removed != null) {
                logger.finer("[RoleService:Expiry] Cleared warning registry for: " + uuid);
            }
        }
    }

    private void saveSessionToRedis(UUID uuid, PlayerSessionData data) {
        if (uuid == null || data == null) return;
        runRedisAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                String jsonData = jsonMapper.writeValueAsString(data);
                jedis.setex(REDIS_SESSION_PREFIX + uuid.toString(), REDIS_SESSION_TTL_SECONDS, jsonData);
                logger.finer("[RoleService:Redis] Redis DADOS session cache saved for " + uuid);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[RoleService:Redis] Failed to save PlayerSessionData to Redis for " + uuid, e);
            }
        }, "save-session-redis-" + uuid);
    }

    public void updateRedisCache(UUID uuid, PlayerSessionData data) {
        if (uuid == null || data == null) {
            logger.warning("[RoleService:Redis] Attempting to update Redis cache with null data for " + uuid);
            return;
        }
        saveSessionToRedis(uuid, data);
    }

    private void runRedisAsync(Runnable task, String taskName) {
        CompletableFuture.runAsync(task, asyncExecutor).exceptionally(ex -> {
            logger.log(Level.WARNING, "[RoleService:Redis] Error executing async Redis task: " + taskName, ex);
            return null;
        });
    }

    public PlayerSessionData getDefaultSessionData(UUID uuid) {
        Role defaultRole = roleCache.getOrDefault("default",
                Role.builder().name("default").displayName("Membro").prefix("<gray>").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build()
        );
        if (defaultRole.getPermissions() == null) defaultRole.setPermissions(new ArrayList<>());
        if (defaultRole.getInheritance() == null) defaultRole.setInheritance(new ArrayList<>());
        Set<String> defaultPerms = calculateEffectivePermissionsForRole(defaultRole);
        return new PlayerSessionData(uuid, defaultRole, defaultPerms);
    }


    public void shutdown() {
        if (expirationCheckTask != null) {
            try { expirationCheckTask.cancel(false); } catch(Exception e) { logger.log(Level.WARNING, "[RoleService] Error canceling expiration warning task.", e); }
            expirationCheckTask = null;
        }
        sentExpirationWarnings.clear();
        roleCache.clear();
        sessionCache.clear();
        preLoginFutures.clear();
        logger.info("[RoleService] RoleService finalized. Caches cleared and tasks stopped.");
    }
}