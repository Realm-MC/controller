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
// Imports para Cache Redis
import com.fasterxml.jackson.databind.ObjectMapper;
// <<< CORREÇÃO: Importações desnecessárias removidas >>>
// import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // REMOVIDO
import com.realmmc.controller.shared.storage.redis.RedisManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
// Imports de Plataforma
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.time.Instant; // Para PlayerSessionData (usado no .isAfter())
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RoleService {

    private final Logger logger;
    private final RoleRepository roleRepository = new RoleRepository();
    private final ProfileService profileService;
    private final ExecutorService asyncExecutor;

    private final Map<String, Role> roleCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSessionData> sessionCache = new ConcurrentHashMap<>(); // Cache Java (Nível 1)
    private final Map<UUID, CompletableFuture<PlayerSessionData>> preLoginFutures = new ConcurrentHashMap<>();

    // --- Cache Redis ---
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String REDIS_SESSION_PREFIX = "controller:session:";
    private static final int REDIS_SESSION_TTL_SECONDS = (int) TimeUnit.HOURS.toSeconds(1);
    // --- FIM Cache Redis ---

    private final Map<UUID, Set<String>> sentExpirationWarnings = new ConcurrentHashMap<>();
    private ScheduledFuture<?> expirationCheckTask = null;
    private static final long WARN_THRESHOLD_1_DAY = TimeUnit.DAYS.toMillis(1);
    private static final long WARN_THRESHOLD_1_HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long WARN_THRESHOLD_15_MIN = TimeUnit.MINUTES.toMillis(15);
    private static final long EXPIRATION_CHECK_INTERVAL_MINUTES = 5;

    public RoleService(Logger logger) {
        this.logger = logger;
        try {
            this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
            this.asyncExecutor = TaskScheduler.getAsyncExecutor();
            logger.info("RoleService inicializado usando ExecutorService do TaskScheduler.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro crítico: Serviço dependente (ProfileService ou TaskScheduler) não encontrado!", e);
            throw e;
        }

        // <<< CORREÇÃO: Linha removida (não precisamos mais do JavaTimeModule) >>>
        // jsonMapper.registerModule(new JavaTimeModule());

        loadRolesToCache();
        startExpirationWarningTask();
    }

    public ExecutorService getAsyncExecutor() {
        return this.asyncExecutor;
    }

    // --- Métodos de Gerenciamento de Roles ---
    public void setupDefaultRoles() {
        logger.info("Verificando e sincronizando grupos padrão (DefaultRole) com o MongoDB...");
        int createdCount = 0; int updatedCount = 0;
        for (DefaultRole defaultRole : DefaultRole.values()) {
            String roleNameLower = defaultRole.getName().toLowerCase();
            try {
                Optional<Role> existingRoleOpt = roleRepository.findByName(roleNameLower);
                Role enumRoleData = defaultRole.toRole();
                if (enumRoleData.getPermissions() == null) enumRoleData.setPermissions(new ArrayList<>());
                if (enumRoleData.getInheritance() == null) enumRoleData.setInheritance(new ArrayList<>());
                enumRoleData.setName(roleNameLower);
                if (existingRoleOpt.isEmpty()) {
                    roleRepository.upsert(enumRoleData); roleCache.put(roleNameLower, enumRoleData); createdCount++;
                    logger.fine("Grupo padrão '" + roleNameLower + "' criado no MongoDB.");
                } else {
                    Role existingRole = existingRoleOpt.get();
                    if (existingRole.getPermissions() == null) existingRole.setPermissions(new ArrayList<>());
                    if (existingRole.getInheritance() == null) existingRole.setInheritance(new ArrayList<>());
                    boolean needsUpdate = false;
                    if (existingRole.getWeight() != enumRoleData.getWeight() || !Objects.equals(existingRole.getDisplayName(), enumRoleData.getDisplayName()) || !Objects.equals(existingRole.getPrefix(), enumRoleData.getPrefix()) || !Objects.equals(existingRole.getSuffix(), enumRoleData.getSuffix()) || !Objects.equals(existingRole.getColor(), enumRoleData.getColor()) || existingRole.getType() != enumRoleData.getType() || !new HashSet<>(existingRole.getInheritance()).equals(new HashSet<>(enumRoleData.getInheritance()))) {
                        needsUpdate = true;
                        existingRole.setDisplayName(enumRoleData.getDisplayName()); existingRole.setPrefix(enumRoleData.getPrefix()); existingRole.setSuffix(enumRoleData.getSuffix()); existingRole.setColor(enumRoleData.getColor()); existingRole.setType(enumRoleData.getType()); existingRole.setWeight(enumRoleData.getWeight()); existingRole.setInheritance(new ArrayList<>(enumRoleData.getInheritance()));
                    }
                    Set<String> currentPermsSet = new HashSet<>(existingRole.getPermissions()); boolean permsChanged = false;
                    List<String> enumPerms = enumRoleData.getPermissions();
                    if (enumPerms != null) { for (String enumPerm : enumPerms) { if (enumPerm != null && currentPermsSet.add(enumPerm.toLowerCase())) { permsChanged = true; } } }
                    if (permsChanged) { existingRole.setPermissions(new ArrayList<>(currentPermsSet)); needsUpdate = true; }
                    if (needsUpdate) {
                        roleRepository.upsert(existingRole); roleCache.put(roleNameLower, existingRole); updatedCount++;
                        logger.fine("Grupo padrão '" + roleNameLower + "' atualizado no MongoDB.");
                    } else { roleCache.putIfAbsent(roleNameLower, existingRole); }
                }
            } catch (MongoException e) { logger.log(Level.SEVERE, "Erro de MongoDB ao sincronizar grupo padrão: " + roleNameLower, e);
            } catch (Exception e) { logger.log(Level.SEVERE, "Erro inesperado ao sincronizar grupo padrão: " + roleNameLower, e); }
        }
        if (createdCount > 0) logger.info(createdCount + " novos grupos padrão foram criados no MongoDB.");
        if (updatedCount > 0) logger.info(updatedCount + " grupos padrão foram atualizados no MongoDB.");
        if (createdCount == 0 && updatedCount == 0) logger.info("Grupos padrão do MongoDB estão sincronizados com o Enum.");
        logger.info("Total de grupos no cache após sincronização: " + roleCache.size());
    }
    public void loadRolesToCache() {
        roleCache.clear(); logger.info("Limpando cache de roles...");
        try {
            roleRepository.collection().find().forEach(role -> {
                if (role.getName() != null) {
                    if (role.getPermissions() == null) role.setPermissions(new ArrayList<>());
                    if (role.getInheritance() == null) role.setInheritance(new ArrayList<>());
                    roleCache.put(role.getName().toLowerCase(), role);
                } else { logger.warning("Role carregado do MongoDB sem nome (ID), ignorado."); }
            });
            logger.info("Carregados " + roleCache.size() + " grupos para o cache.");
        } catch (MongoException e) { logger.log(Level.SEVERE, "Erro de MongoDB ao carregar grupos para o cache!", e);
        } catch (Exception e) { logger.log(Level.SEVERE, "Erro inesperado ao carregar grupos para o cache!", e); }
        roleCache.computeIfAbsent("default", k -> {
            logger.warning("Role 'default' não encontrado no MongoDB! Usando fallback do Enum.");
            return DefaultRole.DEFAULT.toRole();
        });
    }
    public Optional<Role> getRole(String name) { if (name == null || name.isEmpty()) return Optional.empty(); return Optional.ofNullable(roleCache.get(name.toLowerCase())); }
    public Collection<Role> getAllCachedRoles() { return Collections.unmodifiableCollection(roleCache.values()); }

    // --- Métodos de Gerenciamento de Cache ---

    public void invalidateSession(UUID uuid) {
        if (uuid == null) return;
        sessionCache.remove(uuid);
        removePreLoginFuture(uuid);
        runRedisAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.del(REDIS_SESSION_PREFIX + uuid.toString());
                logger.finer("[invalidateSession] Cache Redis invalidado para " + uuid);
            } catch (JedisException e) {
                logger.log(Level.WARNING, "Falha ao invalidar cache Redis para " + uuid, e);
            }
        }, "invalidate-redis-" + uuid);
        logger.fine("Cache de sessão (Java + Redis) invalidado para " + uuid);
    }

    public Optional<PlayerSessionData> getSessionDataFromCache(UUID uuid) {
        return Optional.ofNullable(sessionCache.get(uuid));
    }

    public void startPreLoadingPlayerData(UUID uuid) {
        if (uuid == null) return;
        preLoginFutures.computeIfAbsent(uuid, id -> {
            logger.finer("Iniciando pré-carregamento de permissões (loadPlayerDataAsync) para " + id);
            return loadPlayerDataAsync(id).whenComplete((data, error) -> {
                if (error != null) { Throwable cause = (error instanceof CompletionException && error.getCause() != null) ? error.getCause() : error; logger.log(Level.WARNING, "Erro durante pré-carregamento para " + id, cause); }
                else { logger.finer("Pré-carregamento concluído para " + id); }
            });
        });
    }
    public Optional<CompletableFuture<PlayerSessionData>> getPreLoginFuture(UUID uuid) { if (uuid == null) return Optional.empty(); return Optional.ofNullable(preLoginFutures.get(uuid)); }
    public void removePreLoginFuture(UUID uuid) { if (uuid != null) { CompletableFuture<?> future = preLoginFutures.remove(uuid); if (future != null && !future.isDone()) future.cancel(false); } }

    // --- Método Principal de Carregamento/Cálculo ---
    public CompletableFuture<PlayerSessionData> loadPlayerDataAsync(UUID uuid) {
        if (uuid == null) {
            logger.warning("Tentativa de carregar PlayerData com UUID nulo. Retornando dados padrão.");
            return CompletableFuture.completedFuture(getDefaultSessionData(null));
        }

        PlayerSessionData cachedData = sessionCache.get(uuid);
        // A verificação do timestamp agora usa o getter helper getCalculatedAt()
        if (cachedData != null && cachedData.getCalculatedAt().isAfter(Instant.now().minus(1, TimeUnit.HOURS.toChronoUnit()))) {
            return CompletableFuture.completedFuture(cachedData);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                String jsonData = jedis.get(REDIS_SESSION_PREFIX + uuid.toString());
                if (jsonData != null && !jsonData.isEmpty()) {
                    try {
                        PlayerSessionData redisData = jsonMapper.readValue(jsonData, PlayerSessionData.class);
                        sessionCache.put(uuid, redisData);
                        logger.finest("[loadPlayerDataAsync] Cache Redis HIT para " + uuid);
                        return redisData;
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Falha ao desserializar PlayerSessionData do Redis para " + uuid + ". Recalculando.", e);
                    }
                }
            } catch (JedisException e) {
                logger.log(Level.SEVERE, "Erro de conexão Redis ao buscar sessão para " + uuid + ". Recalculando (fallback Mongo).", e);
            }

            logger.fine("[loadPlayerDataAsync] Cache MISS (Java+Redis) para " + uuid + ". Calculando via MongoDB.");
            try {
                final AtomicBoolean profileNeedsSave = new AtomicBoolean(false);
                final List<PlayerRole> playerRolesForCalc = fetchAndPreparePlayerRoles(uuid, profileNeedsSave);
                updatePauseStateForCalculation(uuid, playerRolesForCalc);
                PlayerSessionData calculatedData = calculatePermissionsExplicit(uuid, playerRolesForCalc);

                Optional<Profile> profileOpt = getProfileFromDb(uuid);
                if (profileOpt.isPresent()) {
                    Profile profile = profileOpt.get();
                    String newPrimaryName = calculatedData.getPrimaryRole().getName();
                    if (!Objects.equals(profile.getPrimaryRoleName(), newPrimaryName)) {
                        profile.setPrimaryRoleName(newPrimaryName);
                        profileNeedsSave.set(true);
                        logger.fine("[loadPlayerDataAsync] primaryRoleName atualizado para '" + newPrimaryName + "' para " + uuid + " (devido a cache miss/expiração).");
                    }
                }

                if (profileNeedsSave.get()) {
                    if (profileOpt.isPresent()) {
                        saveProfileWithCatch(profileOpt.get());
                        logger.fine("Perfil salvo (via cache miss) para " + uuid + " (roles expirados/default/primaryName).");
                    }
                }

                saveSessionToRedis(uuid, calculatedData);
                sessionCache.put(uuid, calculatedData);

                logger.fine("[loadPlayerDataAsync] Cache (Java+Redis) preenchido para " + uuid + " após cálculo.");
                return calculatedData;

            } catch (Exception ex) {
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
                logger.log(Level.SEVERE, "Erro fatal durante cálculo (Mongo) para " + uuid, cause);
                sessionCache.remove(uuid);
                return getDefaultSessionData(uuid);
            }
        }, asyncExecutor);
    }

    private List<PlayerRole> fetchAndPreparePlayerRoles(UUID uuid, AtomicBoolean profileNeedsSave) {
        Optional<Profile> profileOpt = getProfileFromDb(uuid);
        List<PlayerRole> playerRoles;

        if (profileOpt.isEmpty()) {
            logger.warning("Perfil não encontrado no DB para " + uuid + ". Usando grupo 'default' temporário para cálculo.");
            playerRoles = new ArrayList<>(List.of(PlayerRole.builder().roleName("default").build()));
        } else {
            Profile profile = profileOpt.get();
            if (profile.getRoles() == null) {
                profile.setRoles(new ArrayList<>());
            }
            playerRoles = profile.getRoles();

            boolean hasDefault = playerRoles.stream().anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()));
            if (!hasDefault) {
                playerRoles.add(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build());
                profileNeedsSave.set(true);
                logger.warning("Perfil sem 'default' encontrado para " + uuid + ". Adicionando.");
            }

            for (PlayerRole pr : playerRoles) {
                if (pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && !pr.isPaused() && pr.getExpiresAt() != null && System.currentTimeMillis() > pr.getExpiresAt()) {
                    pr.setStatus(PlayerRole.Status.EXPIRED);
                    profileNeedsSave.set(true);
                    logger.fine("Role '" + pr.getRoleName() + "' para " + uuid + " foi marcado como EXPIRED durante o fetch/prepare.");
                }
            }
        }
        return playerRoles;
    }


    // --- Método de Verificação de Permissão ---
    public boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null || permission.isEmpty()) { return false; }
        PlayerSessionData sessionData = sessionCache.get(uuid);
        if (sessionData == null) {
            logger.warning("Cache de sessão MISS (Java) para jogador: " + uuid + " ao verificar '" + permission + "'. Tentando carregar (Redis/Mongo)...");
            try {
                sessionData = loadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);
                if (sessionData != null) {
                    logger.info("Cache de sessão recuperado sync para " + uuid + " após miss.");
                    return sessionData.hasPermission(permission);
                } else {
                    logger.severe("Falha ao carregar dados sync após cache miss para " + uuid + ". Permissão negada.");
                    return false;
                }
            } catch (TimeoutException te) {
                logger.severe("Timeout durante carregamento sync após cache miss para " + uuid + ". Permissão negada.");
                return false;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro durante carregamento sync após cache miss para " + uuid, e);
                return false;
            }
        }
        try {
            return sessionData.hasPermission(permission);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao chamar sessionData.hasPermission('" + permission + "') para " + uuid, e);
            return false;
        }
    }

    // --- Lógica Interna de Cálculo e Pausa ---
    public boolean updatePauseState(UUID uuid, List<PlayerRole> playerRoles) {
        if (playerRoles == null || playerRoles.isEmpty()) return false;
        boolean stateChanged = false;
        boolean isStaff = playerRoles.stream().filter(pr -> pr != null && pr.isActive()).map(pr -> getRole(pr.getRoleName())).filter(Optional::isPresent).map(Optional::get).anyMatch(r -> r.getType() == RoleType.STAFF);
        for (PlayerRole pr : playerRoles) {
            if (pr == null || pr.getStatus() != PlayerRole.Status.ACTIVE || pr.hasExpiredTime()) continue;
            Optional<Role> roleOpt = getRole(pr.getRoleName()); if (roleOpt.isEmpty()) continue;
            Role role = roleOpt.get(); boolean initialState = pr.isPaused();
            boolean shouldBePaused = (role.getType() == RoleType.VIP && !pr.isPermanent() && isStaff);
            if (shouldBePaused && !initialState) { pr.setPaused(true); if (pr.getExpiresAt() != null) { long remaining = pr.getExpiresAt() - System.currentTimeMillis(); pr.setPausedTimeRemaining(Math.max(0, remaining)); logger.finer("Pausando role '" + pr.getRoleName() + "'... Tempo restante: " + pr.getPausedTimeRemaining() + "ms"); } else { pr.setPausedTimeRemaining(null); logger.finer("Pausando role permanente '" + pr.getRoleName() + "'."); } stateChanged = true; }
            else if (!shouldBePaused && initialState) { pr.setPaused(false); Long remainingTime = pr.getPausedTimeRemaining(); if (remainingTime != null && remainingTime > 0) { long newExpiresAt = System.currentTimeMillis() + remainingTime; pr.setExpiresAt(newExpiresAt); logger.finer("Despausando role '" + pr.getRoleName() + "'. Nova expiração: " + new Date(newExpiresAt)); } else if (remainingTime != null) { pr.setExpiresAt(System.currentTimeMillis() - 1); pr.setStatus(PlayerRole.Status.EXPIRED); logger.finer("Despausando role '" + pr.getRoleName() + "', marcado como expirado."); } else { pr.setExpiresAt(null); logger.finer("Despausando role permanente '" + pr.getRoleName() + "'."); } pr.setPausedTimeRemaining(null); stateChanged = true; }
        }
        return stateChanged;
    }

    private void updatePauseStateForCalculation(UUID uuid, List<PlayerRole> playerRoles) {
        if (playerRoles == null || playerRoles.isEmpty()) return;
        boolean isStaff = playerRoles.stream().filter(pr -> pr != null && pr.isActive()).map(pr -> getRole(pr.getRoleName())).filter(Optional::isPresent).map(Optional::get).anyMatch(r -> r.getType() == RoleType.STAFF);
        for (PlayerRole pr : playerRoles) {
            if (pr == null || pr.getStatus() != PlayerRole.Status.ACTIVE || pr.hasExpiredTime()) continue;
            Optional<Role> roleOpt = getRole(pr.getRoleName()); if(roleOpt.isEmpty()) continue;
            Role role = roleOpt.get();
            pr.setPaused(role.getType() == RoleType.VIP && !pr.isPermanent() && isStaff);
        }
    }

    public PlayerSessionData calculatePermissionsExplicit(UUID uuid, List<PlayerRole> playerRoles) {
        Set<String> effectivePermissions = new HashSet<>(); List<Role> activeRolesFound = new ArrayList<>();
        List<PlayerRole> rolesToProcess = (playerRoles == null) ? new ArrayList<>() : new ArrayList<>(playerRoles);
        for (PlayerRole pr : rolesToProcess) { if (pr != null && pr.isActive()) { getRole(pr.getRoleName()).ifPresent(activeRolesFound::add); } }
        if (activeRolesFound.isEmpty()) { getRole("default").ifPresentOrElse( activeRolesFound::add, () -> { logger.severe("CRÍTICO: Role 'default' NÃO ENCONTRADO no cache... UUID: " + uuid); activeRolesFound.add(Role.builder().name("default").displayName("Membro").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build()); } ); }
        Role primaryRole = activeRolesFound.stream().max(Comparator.comparingInt(Role::getWeight)).orElseGet(() -> roleCache.getOrDefault("default", Role.builder().name("default").displayName("Membro").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build()));
        Set<String> visitedRoles = new HashSet<>();
        for (Role activeRole : activeRolesFound) { collectPermissionsRecursiveExplicit(activeRole, effectivePermissions, visitedRoles); }
        return new PlayerSessionData(uuid, primaryRole, Set.copyOf(effectivePermissions));
    }

    public void collectPermissionsRecursiveExplicit(Role role, Set<String> permissions, Set<String> visited) {
        if (role == null || !visited.add(role.getName().toLowerCase())) { return; }
        if (role.getPermissions() != null) { role.getPermissions().stream().filter(Objects::nonNull).map(String::toLowerCase).forEach(permissions::add); }
        if (role.getInheritance() != null) { for (String inheritedRoleName : role.getInheritance()) { if (inheritedRoleName != null && !inheritedRoleName.isBlank()) { getRole(inheritedRoleName).ifPresent(parentRole -> collectPermissionsRecursiveExplicit(parentRole, permissions, visited)); } } }
    }

    // --- Métodos para Avisos de Expiração ---
    private synchronized void startExpirationWarningTask() {
        if (expirationCheckTask != null && !expirationCheckTask.isDone()) {
            logger.fine("Tarefa de aviso de expiração já está rodando.");
            return;
        }
        try {
            expirationCheckTask = TaskScheduler.runAsyncTimer(() -> {
                try {
                    checkAndSendExpirationWarnings();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Erro na tarefa periódica de verificação de expiração de roles.", e);
                }
            }, EXPIRATION_CHECK_INTERVAL_MINUTES, EXPIRATION_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
            logger.info("Tarefa de aviso de expiração de roles iniciada (intervalo: " + EXPIRATION_CHECK_INTERVAL_MINUTES + " minutos).");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao iniciar tarefa de aviso de expiração: TaskScheduler não disponível?", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao agendar tarefa de aviso de expiração.", e);
        }
    }

    private void checkAndSendExpirationWarnings() {
        long now = System.currentTimeMillis();
        Collection<? extends Object> onlinePlayersGeneric;
        try { // Spigot
            onlinePlayersGeneric = Bukkit.getOnlinePlayers();
        } catch (Exception | NoClassDefFoundError e) {
            try { // Velocity
                ProxyServer proxy = ServiceRegistry.getInstance().requireService(ProxyServer.class);
                onlinePlayersGeneric = proxy.getAllPlayers();
            } catch (Exception | NoClassDefFoundError e2) {
                return; // Nenhuma plataforma encontrada
            }
        }
        if (onlinePlayersGeneric.isEmpty()) return;

        for (Object playerObj : onlinePlayersGeneric) {
            UUID playerUuid = null;
            if (playerObj instanceof Player) { // Bukkit
                playerUuid = ((Player) playerObj).getUniqueId();
            } else if (playerObj instanceof com.velocitypowered.api.proxy.Player) { // Velocity
                playerUuid = ((com.velocitypowered.api.proxy.Player) playerObj).getUniqueId();
            }
            if (playerUuid == null) continue;

            final UUID finalUuid = playerUuid;
            final Object finalPlayerObj = playerObj;

            profileService.getByUuid(playerUuid).ifPresent(profile -> {
                if (profile.getRoles() == null) return;
                profile.getRoles().stream()
                        .filter(pr -> pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && !pr.isPermanent() && !pr.isPaused())
                        .forEach(expiringRole -> {
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
                });
            }
        }
    }

    public void clearSentWarnings(UUID uuid) { // Chamado no PlayerQuitEvent/DisconnectEvent
        if (uuid != null) {
            sentExpirationWarnings.remove(uuid);
            logger.finer("Limpado registro de avisos de expiração para: " + uuid);
        }
    }
    // --- Fim Métodos Avisos ---

    // --- Métodos Auxiliares ---
    private Optional<Profile> getProfileFromDb(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try { return profileService.getByUuid(uuid); }
        catch (MongoException me) { logger.log(Level.SEVERE, "Erro MongoDB ao buscar perfil: " + uuid, me); return Optional.empty(); }
        catch (Exception e) { logger.log(Level.SEVERE, "Erro inesperado ao buscar perfil: " + uuid, e); return Optional.empty(); }
    }
    private void saveProfileWithCatch(Profile profile) {
        if (profile == null) return;
        try { profileService.save(profile); }
        catch (Exception e) { logger.log(Level.SEVERE, "(Capturado em RoleService) Erro ao salvar perfil: " + profile.getUuid(), e); }
    }
    public void publishSync(UUID uuid) {
        if (uuid == null) return;
        try { RedisPublisher.publish(RedisChannel.ROLE_SYNC, uuid.toString()); logger.finer("Mensagem ROLE_SYNC publicada para " + uuid); }
        catch (Exception e) { logger.log(Level.WARNING, "Falha ao publicar mensagem ROLE_SYNC para " + uuid, e); }
    }

    public PlayerSessionData getDefaultSessionData(UUID uuid) {
        Role defaultRole = roleCache.getOrDefault("default", Role.builder().name("default").displayName("Membro").prefix("<gray>").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build());
        if (defaultRole.getPermissions() == null) defaultRole.setPermissions(new ArrayList<>());
        if (defaultRole.getInheritance() == null) defaultRole.setInheritance(new ArrayList<>());
        Set<String> defaultPerms = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collectPermissionsRecursiveExplicit(defaultRole, defaultPerms, visited);
        return new PlayerSessionData(uuid, defaultRole, Set.copyOf(defaultPerms));
    }

    public void shutdown() {
        if (expirationCheckTask != null) {
            try { expirationCheckTask.cancel(false); } catch(Exception e) { logger.log(Level.WARNING, "Erro ao cancelar tarefa de aviso de expiração.", e); }
            expirationCheckTask = null;
        }
        sentExpirationWarnings.clear();
        logger.info("RoleService shutdown chamado (Executor gerenciado externamente).");
        roleCache.clear();
        sessionCache.clear();
        preLoginFutures.clear();
        logger.info("Caches do RoleService limpos.");
    }

    // --- Métodos de Cache Redis ---
    /**
     * Salva o PlayerSessionData no Cache Redis com TTL.
     */
    private void saveSessionToRedis(UUID uuid, PlayerSessionData data) {
        if (uuid == null || data == null) return;
        runRedisAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                String jsonData = jsonMapper.writeValueAsString(data);
                jedis.setex(REDIS_SESSION_PREFIX + uuid.toString(), REDIS_SESSION_TTL_SECONDS, jsonData);
                logger.finer("[saveSessionToRedis] Cache Redis salvo para " + uuid);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao salvar PlayerSessionData no Redis para " + uuid, e);
            }
        }, "save-session-redis-" + uuid);
    }

    /**
     * Salva o PlayerSessionData no Cache Redis (usado pelo RoleCommand).
     */
    public void updateRedisCache(UUID uuid, PlayerSessionData data) {
        if (uuid == null || data == null) {
            logger.warning("[updateRedisCache] Tentativa de atualizar cache Redis com dados nulos para " + uuid);
            return;
        }
        saveSessionToRedis(uuid, data);
    }

    /**
     * Helper para rodar tarefas Redis simples assincronamente.
     */
    private void runRedisAsync(Runnable task, String taskName) {
        CompletableFuture.runAsync(task, asyncExecutor).exceptionally(ex -> {
            logger.log(Level.WARNING, "Erro ao executar tarefa Redis assíncrona: " + taskName, ex);
            return null;
        });
    }
}