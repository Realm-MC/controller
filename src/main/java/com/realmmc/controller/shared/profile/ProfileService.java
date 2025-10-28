package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult; // Importar
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.preferences.Language;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.role.Role; // Importar Role
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.storage.mongodb.AbstractMongoRepository;
import com.realmmc.controller.shared.storage.mongodb.MongoRepository;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.messaging.Messages;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean; // Import AtomicBoolean
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProfileService {

    private final ProfileRepository repository = new ProfileRepository();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(ProfileService.class.getName());

    // --- Métodos de acesso a outros serviços ---

    private Optional<StatisticsService> getStatsService() {
        return ServiceRegistry.getInstance().getService(StatisticsService.class);
    }

    private Optional<PreferencesService> getPreferencesService() {
        return ServiceRegistry.getInstance().getService(PreferencesService.class);
    }

    private Optional<RoleService> getRoleService() {
        return ServiceRegistry.getInstance().getService(RoleService.class);
    }

    // --- Métodos de Busca no Repositório ---

    public Optional<Profile> getByUuid(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try {
            return repository.findByUuid(uuid);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "Erro MongoDB ao buscar perfil por UUID: " + uuid, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getById(int id) {
        try {
            return repository.findById(id);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "Erro MongoDB ao buscar perfil por ID numérico: " + id, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try {
            return repository.findByName(name); // Case-sensitive
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "Erro MongoDB ao buscar perfil por nome: " + name, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        try {
            return repository.findByUsername(username.toLowerCase()); // Case-insensitive
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "Erro MongoDB ao buscar perfil por username: " + username, e);
            return Optional.empty();
        }
    }

    public List<Profile> findByActiveRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return repository.findByActiveRoleName(roleName);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "Erro MongoDB ao buscar perfis por role ativo: " + roleName, e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro inesperado ao buscar perfis por role ativo: " + roleName, e);
            return Collections.emptyList();
        }
    }


    // --- Métodos de Modificação (save, delete, exists) ---

    public void save(Profile profile) {
        Objects.requireNonNull(profile, "Profile não pode ser nulo para salvar.");
        long now = System.currentTimeMillis();

        if (profile.getCreatedAt() == 0L) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);

        try {
            if (profile.getId() == null) {
                profile.setId(MongoSequences.getNext("profiles"));
                LOGGER.info("Atribuído novo ID sequencial (" + profile.getId() + ") para perfil UUID: " + profile.getUuid());
            }

            // Log detalhado antes de salvar
            LOGGER.log(Level.FINE, "[ProfileService SAVE] Tentando salvar perfil ID: {0}, UUID: {1}", new Object[]{profile.getId(), profile.getUuid()});
            LOGGER.log(Level.FINER, "[ProfileService SAVE] -> PrimaryRoleName: {0}", profile.getPrimaryRoleName());
            if (profile.getRoles() != null) {
                LOGGER.log(Level.FINER, "[ProfileService SAVE] -> Roles ({0} items):", profile.getRoles().size());
                profile.getRoles().forEach(pr -> LOGGER.log(Level.FINEST, "[ProfileService SAVE] ---> {0}", pr));
            } else {
                LOGGER.log(Level.FINER, "[ProfileService SAVE] -> Roles: null");
            }

            repository.upsert(profile); // Salva/Atualiza no MongoDB
            publish("upsert", profile); // Publica atualização via Redis APÓS salvar com sucesso
            LOGGER.fine("Perfil salvo (upsert) com sucesso para UUID: " + profile.getUuid() + ", ID: " + profile.getId());

        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "Erro MongoDB ao salvar (upsert) perfil para UUID: " + profile.getUuid() + ", ID: " + profile.getId(), e);
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro inesperado ao salvar (upsert) perfil para UUID: " + profile.getUuid() + ", ID: " + profile.getId(), e);
            throw new RuntimeException("Falha inesperada ao salvar perfil", e);
        }
    }

    public void delete(UUID uuid) {
        if (uuid == null) return;
        try {
            Optional<Profile> profileOpt = getByUuid(uuid);
            repository.deleteByUuid(uuid);
            LOGGER.info("Perfil deletado (se existia) para UUID: " + uuid);

            Profile dummy = new Profile();
            dummy.setUuid(uuid);
            profileOpt.ifPresent(p -> dummy.setId(p.getId()));
            publish("delete", dummy);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "Erro MongoDB ao deletar perfil para UUID: " + uuid, e);
        }
    }

    public boolean exists(UUID uuid) {
        if (uuid == null) return false;
        try {
            return repository.findOne(Filters.eq("uuid", uuid)).isPresent();
        } catch (MongoException e) {
            LOGGER.log(Level.WARNING, "Erro MongoDB ao verificar existência do perfil UUID: {0}", uuid);
            return false;
        }
    }

    // --- ensureProfile (Refatorado para confiar no UUID do login) ---
    public Profile ensureProfile(UUID loginUuid, String displayName, String username, String currentIp, String clientVersion, String clientType, boolean isPremium, Object playerObject) {
        Objects.requireNonNull(loginUuid, "loginUuid não pode ser nulo para ensureProfile");
        Objects.requireNonNull(displayName, "displayName não pode ser nulo para ensureProfile");
        Objects.requireNonNull(username, "username não pode ser nulo para ensureProfile");
        final String usernameLower = username.toLowerCase();
        final AtomicBoolean needsSave = new AtomicBoolean(false);
        Profile profileToReturn;

        Optional<Profile> profileOptByUuid = getByUuid(loginUuid);

        if (profileOptByUuid.isPresent()) {
            // --- Perfil ENCONTRADO pelo UUID do Login ---
            profileToReturn = profileOptByUuid.get();
            LOGGER.finest("Perfil encontrado via UUID de login: " + loginUuid + " para username: " + usernameLower);

            // Modifica a instância final 'profileToReturn' diretamente
            if (displayName != null && !displayName.isEmpty() && !displayName.equals(profileToReturn.getName())) {
                profileToReturn.setName(displayName);
                profileToReturn.setUsername(usernameLower);
                needsSave.set(true);
                LOGGER.log(Level.FINER, "Atualizando nome/username para {0} -> {1} ({2})", new Object[]{profileToReturn.getUuid(), displayName, usernameLower});
            }
            else if (usernameLower != null && !usernameLower.isEmpty() && !usernameLower.equals(profileToReturn.getUsername())) {
                profileToReturn.setUsername(usernameLower);
                needsSave.set(true);
                LOGGER.log(Level.FINER, "Atualizando username (sem mudança de nome) para {0} -> {1}", new Object[]{profileToReturn.getUuid(), usernameLower});
            }

            if (currentIp != null && !currentIp.isEmpty()) {
                if (!currentIp.equals(profileToReturn.getLastIp())) {
                    profileToReturn.setLastIp(currentIp);
                    needsSave.set(true);
                }
                if (profileToReturn.getIpHistory() == null) profileToReturn.setIpHistory(new ArrayList<>());
                if (!profileToReturn.getIpHistory().contains(currentIp)) {
                    profileToReturn.getIpHistory().add(currentIp);
                    needsSave.set(true);
                }
            }
            if (profileToReturn.isPremiumAccount() != isPremium) {
                profileToReturn.setPremiumAccount(isPremium);
                needsSave.set(true);
                LOGGER.log(Level.INFO, "Status premium atualizado para {0} no perfil existente de {1}", new Object[]{isPremium, usernameLower});
            }
            profileToReturn.setLastLogin(System.currentTimeMillis());
            profileToReturn.setLastClientVersion(clientVersion);
            profileToReturn.setLastClientType(clientType);
            needsSave.set(true);

            // Usa final UUID
            final UUID profileUuidFinal = profileToReturn.getUuid();
            if (profileToReturn.getRoles() == null) profileToReturn.setRoles(new ArrayList<>());
            boolean hasDefault = profileToReturn.getRoles().stream().anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()));
            if (!hasDefault) {
                profileToReturn.getRoles().add(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build());
                needsSave.set(true);
                LOGGER.log(Level.WARNING, "Perfil existente (UUID: {0}) sem grupo default encontrado. Adicionando.", profileUuidFinal);
            }

            // Usa a variável final/effectively final
            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> {
                prefs.ensurePreferences(finalProfileForServices, null);
                prefs.loadAndCacheLanguage(finalProfileForServices.getUuid());
            });

        } else {
            // --- CRIAÇÃO de Novo Perfil ---
            long now = System.currentTimeMillis();
            int profileId = MongoSequences.getNext("profiles");
            Language initialLang = Messages.determineInitialLanguage(playerObject);

            // Cria e atribui à variável final
            profileToReturn = Profile.builder()
                    .id(profileId)
                    .uuid(loginUuid)
                    .name(displayName)
                    .username(usernameLower)
                    .firstIp(currentIp)
                    .lastIp(currentIp)
                    .ipHistory(new ArrayList<>(currentIp != null ? List.of(currentIp) : List.of()))
                    .firstLogin(now)
                    .lastLogin(now)
                    .lastClientVersion(clientVersion)
                    .lastClientType(clientType)
                    .cash(0)
                    .roles(new ArrayList<>(List.of(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build())))
                    .primaryRoleName("default")
                    .premiumAccount(isPremium)
                    .createdAt(now)
                    .build();

            needsSave.set(true);
            LOGGER.log(Level.INFO, "Criado novo perfil ID {0} para {1} ({2}) com língua inicial {3}",
                    new Object[]{profileId, displayName, loginUuid, initialLang});

            // Usa a variável final
            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> prefs.ensurePreferences(finalProfileForServices, initialLang));
        }

        // --- Salvar Perfil (se necessário) ---
        // Usa a variável final/effectively final
        final Profile profileToSaveOrCheck = profileToReturn;
        if (needsSave.get()) {
            try {
                save(profileToSaveOrCheck); // Salva o perfil
                LOGGER.fine("Perfil " + (profileOptByUuid.isPresent() ? "atualizado" : "novo") + " salvo para UUID: " + profileToSaveOrCheck.getUuid());
            } catch (MongoException e) {
                if (e.getCode() == 11000 && e.getMessage() != null && (e.getMessage().contains("index: username_1") || e.getMessage().contains("index: uuid_1"))) {
                    LOGGER.log(Level.WARNING, "Erro de chave duplicada (UUID ou Username) ao salvar perfil para {0} (UUID: {1}). Outro processo pode ter criado/modificado o perfil concorrentemente. Tentando recarregar.", new Object[]{usernameLower, loginUuid});
                    // Tenta buscar pelo UUID novamente para retornar o perfil que causou o conflito
                    profileToReturn = getByUuid(loginUuid).orElse(profileToSaveOrCheck);
                } else {
                    LOGGER.log(Level.SEVERE, "Erro MongoDB CRÍTICO ao salvar perfil durante ensureProfile para " + usernameLower + " (UUID: " + loginUuid + ")", e);
                    throw e;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro inesperado CRÍTICO ao salvar perfil durante ensureProfile para " + usernameLower + " (UUID: " + loginUuid + ")", e);
                throw new RuntimeException("Falha inesperada ao salvar perfil", e);
            }
        } else {
            LOGGER.finest("Nenhuma alteração necessária para salvar em ensureProfile para UUID: " + loginUuid);
        }

        // Garante carga do cache de língua após ensureProfile
        final Profile finalProfileToReturn = profileToReturn;
        getPreferencesService().ifPresent(prefs -> prefs.loadAndCacheLanguage(finalProfileToReturn.getUuid()));

        return finalProfileToReturn;
    }
    // --- Fim ensureProfile ---

    // --- Métodos de Atualização Específicos ---
    public void updateName(UUID uuid, String newName) {
        if (uuid == null || newName == null || newName.isEmpty()) return;
        update(uuid, p -> {
            if (!newName.equals(p.getName())) {
                p.setName(newName);
                p.setUsername(newName.toLowerCase());
                getStatsService().ifPresent(stats -> stats.updateIdentification(p));
                getPreferencesService().ifPresent(prefs -> prefs.updateIdentification(p));
            }
        }, "update_name");
    }

    public void setUsername(UUID uuid, String username) {
        if (uuid == null || username == null || username.isEmpty()) return;
        final String usernameLower = username.toLowerCase();
        update(uuid, p -> {
            if (!usernameLower.equals(p.getUsername())) {
                p.setUsername(usernameLower);
            }
        }, "set_username");
    }
    public void incrementCash(UUID uuid, int delta) {
        if (uuid == null || delta == 0) return;
        update(uuid, p -> {
            long currentCash = p.getCash();
            long newCashLong = Math.max(0L, currentCash + delta);
            int finalCash = (int) Math.min(Integer.MAX_VALUE, newCashLong);
            p.setCash(finalCash);
        }, "cash_increment");
    }
    public void setCash(UUID uuid, int amount) {
        if (uuid == null) return;
        int finalAmount = Math.max(0, amount);
        update(uuid, p -> p.setCash(finalAmount), "cash_set");
    }

    private void update(UUID uuid, Consumer<Profile> modifier, String actionContext) {
        if (uuid == null || modifier == null) return;
        Optional<Profile> profileOpt = getByUuid(uuid);
        profileOpt.ifPresentOrElse(
                profile -> {
                    try {
                        modifier.accept(profile);
                        save(profile);
                        LOGGER.fine("Perfil atualizado via '" + actionContext + "' para UUID: " + uuid);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Erro ao executar '" + actionContext + "' para UUID: " + uuid, e);
                    }
                },
                () -> {
                    LOGGER.warning("Tentativa de '" + actionContext + "' falhou: Perfil não encontrado para UUID: " + uuid);
                }
        );
    }

    // --- PONTO 2: Métodos de Sincronização (Atualizado) ---
    private void publish(String action, Profile profile) {
        if (profile == null || profile.getUuid() == null || action == null) {
            UUID profileUuid = (profile != null) ? profile.getUuid() : null;
            LOGGER.warning("Tentativa de publicar sync para perfil inválido. UUID: " + profileUuid);
            return;
        }

        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());

            if ("upsert".equals(action)) {
                if(profile.getId() != null) node.put("id", profile.getId()); else node.putNull("id");
                node.put("name", profile.getName());
                node.put("username", profile.getUsername());
                node.put("cash", profile.getCash());
                node.put("premium", profile.isPremiumAccount());
                if(profile.getCashTopPosition() != null) node.put("cashTopPosition", profile.getCashTopPosition()); else node.putNull("cashTopPosition");
                if(profile.getCashTopPositionEnteredAt() != null) node.put("cashTopPositionEnteredAt", profile.getCashTopPositionEnteredAt()); else node.putNull("cashTopPositionEnteredAt");
                node.put("firstIp", profile.getFirstIp());
                node.put("lastIp", profile.getLastIp());
                if (profile.getIpHistory() != null) { ArrayNode ipHistoryNode = node.putArray("ipHistory"); profile.getIpHistory().forEach(ipHistoryNode::add); } else { node.putArray("ipHistory"); }
                node.put("firstLogin", profile.getFirstLogin());
                node.put("lastLogin", profile.getLastLogin());
                node.put("lastClientVersion", profile.getLastClientVersion());
                node.put("lastClientType", profile.getLastClientType());
                node.put("primaryRoleName", profile.getPrimaryRoleName());

                // <<< PONTO 2: Incluir lista de roles na sync >>>
                List<PlayerRole> roles = profile.getRoles();
                if (roles != null) {
                    ArrayNode rolesNode = node.putArray("roles");
                    for (PlayerRole pr : roles) {
                        if (pr == null || pr.getRoleName() == null) continue;
                        ObjectNode roleInfo = rolesNode.addObject();
                        roleInfo.put("roleName", pr.getRoleName());
                        roleInfo.put("status", pr.getStatus() != null ? pr.getStatus().name() : PlayerRole.Status.ACTIVE.name());
                        if (pr.getExpiresAt() != null) roleInfo.put("expiresAt", pr.getExpiresAt()); else roleInfo.putNull("expiresAt");
                        roleInfo.put("paused", pr.isPaused());
                        if (pr.getPausedTimeRemaining() != null) roleInfo.put("pausedTimeRemaining", pr.getPausedTimeRemaining()); else roleInfo.putNull("pausedTimeRemaining");
                        roleInfo.put("addedAt", pr.getAddedAt());
                        if (pr.getRemovedAt() != null) roleInfo.put("removedAt", pr.getRemovedAt()); else roleInfo.putNull("removedAt");
                    }
                } else {
                    node.putArray("roles");
                }
                // <<< FIM PONTO 2 >>>

                node.put("createdAt", profile.getCreatedAt());
                node.put("updatedAt", profile.getUpdatedAt());

            } else if ("delete".equals(action)) {
                if (profile.getId() != null) node.put("id", profile.getId());
            }

            String jsonMessage = node.toString();
            RedisPublisher.publish(RedisChannel.PROFILES_SYNC, jsonMessage);
            LOGGER.log(Level.FINER, "Mensagem profile sync ('{0}') publicada para {1}", new Object[]{action, profile.getUuid()});

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao serializar/publicar mensagem profile sync (action=" + action + ") para UUID: " + profile.getUuid(), e);
        }
    }

    public void invalidateProfileCache(UUID uuid) {
        if (uuid != null) {
            LOGGER.finest("Invalidação de cache solicitada para Profile UUID: " + uuid + " (nenhum cache local no ProfileService)");
        }
    }

    private void saveProfileWithCatch(Profile profile) {
        if (profile == null) return;
        try {
            save(profile);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "(Capturado em saveProfileWithCatch) Erro ao salvar perfil: " + profile.getUuid(), e);
        }
    }
}