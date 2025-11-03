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
            logger.info("RoleService inicializado usando ExecutorService do TaskScheduler.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro crítico ao inicializar RoleService: Serviço dependente (ProfileService ou TaskScheduler) não encontrado!", e);
            throw e;
        }
        startExpirationWarningTask();
    }

    // --- Getters e Métodos Auxiliares ---

    public ExecutorService getAsyncExecutor() {
        return this.asyncExecutor;
    }

    private Optional<Profile> getProfileFromDb(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try {
            Optional<Profile> profileOpt = profileService.getByUuid(uuid);
            // <<< CORREÇÃO: Adicionada retentativa para mitigar race condition >>>
            if (profileOpt.isEmpty()) {
                try {
                    Thread.sleep(50); // Pequena espera
                } catch (InterruptedException ignored) {}
                profileOpt = profileService.getByUuid(uuid); // Segunda tentativa
                if (profileOpt.isEmpty()) {
                    logger.log(Level.FINER, "Perfil não encontrado no DB para {0} (mesmo após retentativa)", uuid);
                }
            }
            return profileOpt;
            // <<< FIM CORREÇÃO >>>
        }
        catch (MongoException me) { logger.log(Level.SEVERE, "Erro MongoDB ao buscar perfil: " + uuid, me); return Optional.empty(); }
        catch (NoSuchElementException nse) {
            logger.log(Level.FINE, "Perfil não encontrado no DB para {0} (NoSuchElementException)", uuid);
            return Optional.empty();
        }
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

    // --- Métodos de Gerenciamento de Roles ---

    public void setupDefaultRoles() {
        logger.info("Verificando e sincronizando grupos padrão (DefaultRole) com o MongoDB...");
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
                    logger.fine("Grupo padrão '" + roleNameLower + "' criado no MongoDB e cacheado.");
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
                        logger.fine("Grupo padrão '" + roleNameLower + "' atualizado no MongoDB e cacheado.");
                    } else {
                        roleCache.putIfAbsent(roleNameLower, existingRole);
                    }
                }
            } catch (MongoException e) {
                logger.log(Level.SEVERE, "Erro de MongoDB ao sincronizar grupo padrão: " + roleNameLower, e);
                errorCount++;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro inesperado ao sincronizar grupo padrão: " + roleNameLower, e);
                errorCount++;
            }
        }

        if (createdCount > 0) logger.info(createdCount + " novos grupos padrão foram criados no MongoDB.");
        if (updatedCount > 0) logger.info(updatedCount + " grupos padrão foram atualizados no MongoDB.");
        if (errorCount > 0) logger.severe(errorCount + " ERROS ocorreram durante a sincronização de grupos padrão!");
        if (createdCount == 0 && updatedCount == 0 && errorCount == 0) logger.info("Grupos padrão do MongoDB estão sincronizados com o Enum.");

        roleCache.computeIfAbsent("default", k -> {
            logger.warning("Role 'default' NÃO estava no cache APÓS setupDefaultRoles! Usando fallback do Enum.");
            return DefaultRole.DEFAULT.toRole();
        });

        loadRolesToCache();

        logger.info("Sincronização de roles padrão concluída. Total de grupos no cache: " + roleCache.size());
    }

    public void loadRolesToCache() {
        logger.info("Recarregando roles do MongoDB para o cache...");
        AtomicInteger loadedCount = new AtomicInteger();
        int errorCount = 0;
        Set<String> keysInDb = new HashSet<>();
        try {
            roleRepository.collection().find().forEach(role -> {
                if (role != null && role.getName() != null) {
                    String nameLower = role.getName().toLowerCase();
                    keysInDb.add(nameLower);
                    if (role.getPermissions() == null) role.setPermissions(new ArrayList<>());
                    if (role.getInheritance() == null) role.setInheritance(new ArrayList<>());
                    roleCache.put(nameLower, role);
                    loadedCount.getAndIncrement();
                } else {
                    logger.warning("Role carregado do MongoDB inválido (nulo ou sem nome), ignorado.");
                }
            });
            roleCache.entrySet().removeIf(entry ->
                    !keysInDb.contains(entry.getKey()) &&
                            Arrays.stream(DefaultRole.values()).noneMatch(dr -> dr.getName().equalsIgnoreCase(entry.getKey()))
            );
            logger.info("Carregados/Atualizados " + loadedCount + " grupos do MongoDB para o cache.");
        } catch (MongoException e) {
            logger.log(Level.SEVERE, "Erro de MongoDB ao carregar grupos para o cache!", e);
            errorCount++;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao carregar grupos para o cache!", e);
            errorCount++;
        }

        if (!roleCache.containsKey("default")) {
            logger.severe("CRÍTICO: Role 'default' NÃO presente no cache APÓS carregar do MongoDB! Usando fallback do Enum.");
            roleCache.put("default", DefaultRole.DEFAULT.toRole());
        }

        if (errorCount > 0) {
            logger.severe("Ocorreram erros ao carregar roles do MongoDB. O cache pode estar incompleto!");
        }
        logger.info("Total de grupos no cache após carregamento completo: " + roleCache.size());
    }

    public Optional<Role> getRole(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        return Optional.ofNullable(roleCache.get(name.toLowerCase()));
    }
    public Collection<Role> getAllCachedRoles() {
        return Collections.unmodifiableCollection(roleCache.values());
    }

    // --- Métodos de Gerenciamento de Cache de Sessão ---

    public void invalidateSession(UUID uuid) {
        if (uuid == null) return;
        sessionCache.remove(uuid);
        removePreLoginFuture(uuid);
        runRedisAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.del(REDIS_SESSION_PREFIX + uuid.toString());
                logger.finer("[invalidateSession] Cache Redis de DADOS da sessão invalidado para " + uuid);
            } catch (JedisException e) {
                logger.log(Level.WARNING, "Falha ao invalidar cache Redis de DADOS da sessão para " + uuid, e);
            }
        }, "invalidate-session-data-" + uuid);
        logger.fine("Cache de DADOS da sessão (Java + Redis) invalidado para " + uuid);
    }

    public Optional<PlayerSessionData> getSessionDataFromCache(UUID uuid) {
        return Optional.ofNullable(sessionCache.get(uuid));
    }

    // <<< Definição com 1 argumento >>>
    public void startPreLoadingPlayerData(UUID uuid) {
        if (uuid == null) return;
        final UUID finalUuid = uuid;
        preLoginFutures.computeIfAbsent(finalUuid, id -> {
            logger.finer("Iniciando pré-carregamento de permissões (loadPlayerDataAsync) para " + id);
            return loadPlayerDataAsync(id).whenComplete((data, error) -> {
                if (error != null) {
                    Throwable cause = (error instanceof CompletionException && error.getCause() != null) ? error.getCause() : error;
                    logger.log(Level.WARNING, "Erro durante pré-carregamento para " + id, cause);
                } else {
                    logger.finer("Pré-carregamento concluído para " + id);
                }
            });
        });
    }

    public Optional<CompletableFuture<PlayerSessionData>> getPreLoginFuture(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(preLoginFutures.get(uuid));
    }

    // <<< Definição com 1 argumento >>>
    public void removePreLoginFuture(UUID uuid) {
        if (uuid != null) {
            CompletableFuture<?> future = preLoginFutures.remove(uuid);
            if (future != null && !future.isDone()) {
                future.cancel(false);
                logger.finer("Futuro de pré-login cancelado/removido para " + uuid);
            } else if (future != null) {
                logger.finer("Futuro de pré-login já concluído removido para " + uuid);
            }
        }
    }

    // --- Método Principal de Carregamento/Cálculo ---
    public CompletableFuture<PlayerSessionData> loadPlayerDataAsync(UUID uuid) {
        if (uuid == null) {
            logger.warning("Tentativa de carregar PlayerData com UUID nulo. Retornando dados padrão.");
            return CompletableFuture.completedFuture(getDefaultSessionData(null));
        }

        PlayerSessionData cachedData = sessionCache.get(uuid);

        // <<< CORREÇÃO: Aumentar tempo de cache de 1 hora para 60 minutos (igual) >>>
        // (Vou mudar para 60 minutos para corresponder ao TTL do Redis)
        if (cachedData != null && cachedData.getCalculatedAt().isAfter(Instant.now().minus(60, TimeUnit.MINUTES.toChronoUnit()))) {
            logger.finest("[loadPlayerDataAsync] Cache Java HIT para " + uuid);
            return CompletableFuture.completedFuture(cachedData);
        }
        // <<< FIM CORREÇÃO >>>

        final UUID finalUuid = uuid;

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                String jsonData = jedis.get(REDIS_SESSION_PREFIX + finalUuid.toString());
                if (jsonData != null && !jsonData.isEmpty()) {
                    try {
                        PlayerSessionData redisData = jsonMapper.readValue(jsonData, PlayerSessionData.class);
                        // <<< CORREÇÃO: Aumentar tempo de cache de 1 hora para 60 minutos >>>
                        if (redisData.getCalculatedAt().isAfter(Instant.now().minus(60, TimeUnit.MINUTES.toChronoUnit()))) {
                            sessionCache.put(finalUuid, redisData);
                            logger.fine("[loadPlayerDataAsync] Cache Redis HIT para " + finalUuid);
                            return redisData;
                        } else {
                            logger.fine("[loadPlayerDataAsync] Cache Redis STALE para " + finalUuid + ". Recalculando.");
                        }
                        // <<< FIM CORREÇÃO >>>
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Falha ao desserializar PlayerSessionData do Redis para " + finalUuid + ". Recalculando.", e);
                        try { jedis.del(REDIS_SESSION_PREFIX + finalUuid.toString()); } catch (Exception ignored) {}
                    }
                }
            } catch (JedisException e) {
                logger.log(Level.SEVERE, "Erro de conexão Redis ao buscar DADOS da sessão para " + finalUuid + ". Recalculando (fallback Mongo).", e);
            }

            logger.fine("[loadPlayerDataAsync] Cache MISS (Java+Redis) ou STALE para " + finalUuid + ". Calculando via MongoDB.");
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
                        logger.fine("[loadPlayerDataAsync] primaryRoleName atualizado para '" + newPrimaryName + "' para " + finalUuid + " (devido a cache miss/recalculo).");
                    }
                } else {
                    if (profileNeedsSave.get()) {
                        logger.log(Level.WARNING, "[loadPlayerDataAsync] Perfil não encontrado para {0} DURANTE o cálculo final, mas alterações pendentes (roles expirados?) não serão salvas.", finalUuid);
                    } else {
                        logger.log(Level.WARNING, "[loadPlayerDataAsync] Perfil não encontrado para {0} durante o cálculo final. Usando dados calculados sem salvar primaryRoleName.", finalUuid);
                    }
                }

                if (profileNeedsSave.get()) {
                    if (profileOpt.isPresent()) {
                        saveProfileWithCatch(profileOpt.get());
                        logger.fine("Perfil salvo (via cache miss/recalculo) para " + finalUuid + ".");
                    } else {
                        logger.log(Level.WARNING, "[loadPlayerDataAsync] Perfil não encontrado para {0}, não foi possível salvar alterações (roles expirados/default/primaryName).", finalUuid);
                    }
                }

                saveSessionToRedis(finalUuid, calculatedData);
                sessionCache.put(finalUuid, calculatedData);

                logger.fine("[loadPlayerDataAsync] Cache (Java+Redis) preenchido para " + finalUuid + " após cálculo.");
                return calculatedData;

            } catch (Exception ex) {
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
                boolean profileNotFoundDuringLoad = false;
                // Ajuste para checar 'cause' por null
                if (cause != null && (cause instanceof NoSuchElementException ||
                        (cause.getMessage() != null && cause.getMessage().contains("Perfil não encontrado")))) {
                    profileNotFoundDuringLoad = true;
                    logger.log(Level.WARNING, "[loadPlayerDataAsync] Perfil não encontrado para {0} durante o cálculo. Usando dados default temporariamente.", finalUuid);
                } else {
                    logger.log(Level.SEVERE, "Erro fatal durante cálculo de permissões (Mongo) para " + finalUuid, cause);
                }

                sessionCache.remove(finalUuid);

                if (!profileNotFoundDuringLoad) {
                    logger.log(Level.SEVERE, "Retornando dados default como fallback devido a erro não relacionado a 'perfil não encontrado' para " + finalUuid);
                }
                return getDefaultSessionData(finalUuid);
            }
        }, asyncExecutor);
    }

    // --- Métodos Internos de Cálculo ---

    private List<PlayerRole> fetchAndPreparePlayerRoles(UUID uuid, AtomicBoolean profileNeedsSave) {
        Optional<Profile> profileOpt = getProfileFromDb(uuid);
        List<PlayerRole> playerRoles;

        if (profileOpt.isEmpty()) {
            logger.warning("Perfil não encontrado no DB para " + uuid + ". Usando grupo 'default' temporário para cálculo.");
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
                logger.warning("Perfil sem 'default' encontrado para " + uuid + ". Adicionando.");
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
                                logger.fine("Role 'default' reativado para " + uuid + " pois não havia outros roles ativos.");
                            });
                }
            }

            boolean expiredFound = false;
            for (PlayerRole pr : playerRoles) {
                if (pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && pr.hasExpiredTime()) {
                    pr.setStatus(PlayerRole.Status.EXPIRED);
                    profileNeedsSave.set(true);
                    expiredFound = true;
                    logger.fine("Role '" + pr.getRoleName() + "' para " + uuid + " foi marcado como EXPIRED durante o fetch/prepare.");
                }
            }
            if(expiredFound) {
                boolean hasOtherStillActive = playerRoles.stream()
                        .anyMatch(pr -> pr != null && !"default".equalsIgnoreCase(pr.getRoleName()) && pr.isActive());
                if (!hasOtherStillActive) {
                    playerRoles.stream()
                            .filter(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() != PlayerRole.Status.ACTIVE)
                            .findFirst().ifPresent(defaultRole -> {
                                defaultRole.setStatus(PlayerRole.Status.ACTIVE);
                                defaultRole.setRemovedAt(null);
                                defaultRole.setPaused(false);
                                defaultRole.setPausedTimeRemaining(null);
                                logger.fine("Role 'default' reativado para " + uuid + " após expiração de outros roles.");
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
                    logger.finer("Pausando role '" + pr.getRoleName() + "' para " + uuid +". Tempo restante: " + pr.getPausedTimeRemaining() + "ms");
                } else {
                    pr.setPausedTimeRemaining(null);
                    logger.finer("Pausando role VIP permanente '" + pr.getRoleName() + "' para " + uuid + ".");
                }
                stateChanged = true;
            } else if (!shouldBePaused && initialState) {
                pr.setPaused(false);
                Long remainingTime = pr.getPausedTimeRemaining();
                if (remainingTime != null && remainingTime > 0) {
                    long newExpiresAt = System.currentTimeMillis() + remainingTime;
                    pr.setExpiresAt(newExpiresAt);
                    logger.finer("Despausando role '" + pr.getRoleName() + "' para " + uuid + ". Nova expiração: " + new Date(newExpiresAt));
                } else if (remainingTime != null) {
                    pr.setExpiresAt(System.currentTimeMillis() - 1);
                    pr.setStatus(PlayerRole.Status.EXPIRED);
                    logger.finer("Despausando role '" + pr.getRoleName() + "' para " + uuid + ", marcado como expirado (tempo acabou durante pausa).");
                } else {
                    pr.setExpiresAt(null);
                    logger.finer("Despausando role VIP permanente '" + pr.getRoleName() + "' para " + uuid + ".");
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
                        logger.severe("CRÍTICO: Role 'default' NÃO ENCONTRADO no cache durante cálculo para UUID: " + uuid);
                        activeRolesFound.add(Role.builder().name("default").displayName("Membro").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build());
                    }
            );
        }

        Role primaryRole = activeRolesFound.stream()
                .max(Comparator.comparingInt(Role::getWeight))
                .orElseGet(() -> {
                    logger.warning("Nenhum role ativo encontrado para determinar primário para UUID: " + uuid + ". Usando default.");
                    return roleCache.getOrDefault("default", Role.builder().name("default").displayName("Membro").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build());
                });

        Set<String> visitedRoles = new HashSet<>();
        for (Role activeRole : activeRolesFound) {
            collectPermissionsRecursiveExplicit(activeRole, effectivePermissions, visitedRoles);
        }

        // <<< CORREÇÃO: Passar Set, não Set.copyOf() >>>
        // O construtor de PlayerSessionData (corrigido) agora espera um Set (ou Collection) e
        // cria um novo HashSet interno.
        return new PlayerSessionData(uuid, primaryRole, effectivePermissions);
        // <<< FIM CORREÇÃO >>>
    }

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

    // --- Método de Verificação de Permissão ---
    public boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null || permission.isEmpty()) { return false; }

        PlayerSessionData sessionData = sessionCache.get(uuid);

        // <<< CORREÇÃO: Aumentar tempo de cache de 1 minuto para 60 minutos >>>
        if (sessionData != null && sessionData.getCalculatedAt().isAfter(Instant.now().minus(60, TimeUnit.MINUTES.toChronoUnit()))) {
            // <<< FIM CORREÇÃO >>>
            try {
                return sessionData.hasPermission(permission);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao chamar sessionData.hasPermission('" + permission + "') para " + uuid + " (cache L1)", e);
                return false;
            }
        }

        logger.log(Level.WARNING, "Cache de sessão MISS (Java) ou STALE para jogador: {0} ao verificar ''{1}''. Tentando carregar/recalcular (Redis/Mongo)...", new Object[]{uuid, permission});
        try {
            PlayerSessionData loadedData = loadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);

            if (loadedData != null) {
                logger.log(Level.INFO, "Cache de sessão recuperado/recalculado sync para {0} após miss/stale.", uuid);
                return loadedData.hasPermission(permission);
            } else {
                logger.log(Level.SEVERE, "Falha CRÍTICA ao carregar/recalcular dados sync após cache miss/stale para {0}. Permissão negada.", uuid);
                return false;
            }
        } catch (TimeoutException te) {
            logger.log(Level.SEVERE, "Timeout durante carregamento/recalculo sync após cache miss/stale para {0}. Permissão negada.", uuid);
            return false;
        } catch (Exception e) {
            Throwable cause = (e instanceof ExecutionException && e.getCause() != null) ? e.getCause() : e;
            logger.log(Level.SEVERE, "Erro durante carregamento/recalculo sync após cache miss/stale para {0}", new Object[]{uuid});
            logger.log(Level.SEVERE, "Causa:", cause);
            return false;
        }
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
        Collection<?> onlinePlayersGeneric;

        try {
            onlinePlayersGeneric = Bukkit.getOnlinePlayers();
        } catch (Exception | NoClassDefFoundError e) {
            try {
                ProxyServer proxy = ServiceRegistry.getInstance().requireService(ProxyServer.class);
                onlinePlayersGeneric = proxy.getAllPlayers();
            } catch (Exception | NoClassDefFoundError e2) {
                logger.finest("Não foi possível obter lista de jogadores online para verificar expiração de roles.");
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
                    logger.fine("Aviso de expiração ("+ messageKey.getKey() +") enviado para " + playerUuid + " sobre role " + role.getRoleName());
                });
            }
        }
    }

    public void clearSentWarnings(UUID uuid) {
        if (uuid != null) {
            Set<String> removed = sentExpirationWarnings.remove(uuid);
            if (removed != null) {
                logger.finer("Limpado registro de avisos de expiração para: " + uuid);
            }
        }
    }
    // --- Fim Métodos Avisos ---


    // --- Métodos de Cache Redis (PlayerSessionData) ---

    private void saveSessionToRedis(UUID uuid, PlayerSessionData data) {
        if (uuid == null || data == null) return;
        runRedisAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                String jsonData = jsonMapper.writeValueAsString(data);
                jedis.setex(REDIS_SESSION_PREFIX + uuid.toString(), REDIS_SESSION_TTL_SECONDS, jsonData);
                logger.finer("[saveSessionToRedis] Cache Redis de DADOS da sessão salvo para " + uuid);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao salvar PlayerSessionData no Redis para " + uuid, e);
            }
        }, "save-session-redis-" + uuid);
    }

    public void updateRedisCache(UUID uuid, PlayerSessionData data) {
        if (uuid == null || data == null) {
            logger.warning("[updateRedisCache] Tentativa de atualizar cache Redis com dados nulos para " + uuid);
            return;
        }
        saveSessionToRedis(uuid, data);
    }

    private void runRedisAsync(Runnable task, String taskName) {
        CompletableFuture.runAsync(task, asyncExecutor).exceptionally(ex -> {
            logger.log(Level.WARNING, "Erro ao executar tarefa Redis assíncrona: " + taskName, ex);
            return null;
        });
    }

    // --- Métodos de Finalização e Fallback ---

    public PlayerSessionData getDefaultSessionData(UUID uuid) {
        Role defaultRole = roleCache.getOrDefault("default",
                Role.builder().name("default").displayName("Membro").prefix("<gray>").weight(0).permissions(new ArrayList<>()).inheritance(new ArrayList<>()).build()
        );
        if (defaultRole.getPermissions() == null) defaultRole.setPermissions(new ArrayList<>());
        if (defaultRole.getInheritance() == null) defaultRole.setInheritance(new ArrayList<>());
        Set<String> defaultPerms = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collectPermissionsRecursiveExplicit(defaultRole, defaultPerms, visited);
        // <<< CORREÇÃO: Passar Set, não Set.copyOf() >>>
        return new PlayerSessionData(uuid, defaultRole, defaultPerms);
        // <<< FIM CORREÇÃO >>>
    }


    public void shutdown() {
        if (expirationCheckTask != null) {
            try { expirationCheckTask.cancel(false); } catch(Exception e) { logger.log(Level.WARNING, "Erro ao cancelar tarefa de aviso de expiração.", e); }
            expirationCheckTask = null;
        }
        sentExpirationWarnings.clear();
        roleCache.clear();
        sessionCache.clear();
        preLoginFutures.clear();
        logger.info("RoleService finalizado. Caches limpos e tarefas paradas.");
    }
}