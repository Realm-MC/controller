package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.role.DefaultRole;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ProfileService {

    private final ProfileRepository repository = new ProfileRepository();
    private final ObjectMapper mapper = new ObjectMapper();

    private StatisticsService getStatsService() {
        return ServiceRegistry.getInstance().getService(StatisticsService.class)
                .orElseThrow(() -> new IllegalStateException("StatisticsService não está disponível ou não foi inicializado!"));
    }

    public Optional<Profile> getByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    public Optional<Profile> getByName(String name) {
        return repository.findByName(name);
    }

    public Optional<Profile> getByUsername(String username) {
        return repository.findByUsername(username);
    }

    public void save(Profile profile) {
        long now = System.currentTimeMillis();
        if (profile.getCreatedAt() == 0L) profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        repository.upsert(profile);
        publish("upsert", profile);
    }

    public void delete(UUID uuid) {
        repository.findByUuid(uuid).ifPresent(p -> {
            repository.deleteByUuid(uuid);
            publish("delete", p);
        });
    }

    public boolean exists(UUID uuid) {
        return repository.findByUuid(uuid).isPresent();
    }

    public Profile ensureProfile(UUID uuid, String displayName, String username, String firstIp, String clientVersion, String clientType, boolean isPremium) {
        Profile profile = repository.findByUuid(uuid).orElseGet(() -> {
            long now = System.currentTimeMillis();
            claimUsername(uuid, username);
            claimName(uuid, displayName);

            Profile newProfile = Profile.builder()
                    .id(MongoSequences.getNext("profiles"))
                    .uuid(uuid)
                    .name(displayName)
                    .username(username)
                    .firstIp(firstIp)
                    .lastIp(firstIp)
                    .firstLogin(now)
                    .lastLogin(now)
                    .lastClientVersion(clientVersion)
                    .lastClientType(clientType)
                    .roleId(DefaultRole.MEMBER.getId())
                    .premiumAccount(isPremium)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            if (firstIp != null) newProfile.getIpHistory().add(firstIp);

            getStatsService().ensureStatistics(newProfile);

            save(newProfile);
            return newProfile;
        });

        boolean needsSave = false;
        if (displayName != null && !displayName.isEmpty() && !displayName.equals(profile.getName())) {
            claimName(uuid, displayName);
            profile.setName(displayName);
            needsSave = true;
        }
        if (username != null && !username.isEmpty() && !username.equals(profile.getUsername())) {
            claimUsername(uuid, username);
            profile.setUsername(username);
            needsSave = true;
        }

        if (profile.getRoleId() == null) {
            profile.setRoleId(DefaultRole.MEMBER.getId());
            needsSave = true;
        }

        if (profile.isPremiumAccount() != isPremium) {
            profile.setPremiumAccount(isPremium);
            needsSave = true;
        }

        profile.setLastLogin(System.currentTimeMillis());
        profile.setLastIp(firstIp);
        profile.setLastClientVersion(clientVersion);
        profile.setLastClientType(clientType);

        getStatsService().updateIdentification(profile);

        if (needsSave) {
            save(profile);
        }

        return profile;
    }

    public void updateName(UUID uuid, String newName) {
        if (newName == null || newName.isEmpty()) return;
        update(uuid, p -> {
            if (!newName.equals(p.getName())) {
                claimName(uuid, newName);
                p.setName(newName);
                getStatsService().updateIdentification(p);
            }
        }, "update_name");
    }

    public void setUsername(UUID uuid, String username) {
        if (username == null || username.isEmpty()) return;
        update(uuid, p -> {
            if (!username.equals(p.getUsername())) {
                claimUsername(uuid, username);
                p.setUsername(username);
            }
        }, "set_username");
    }

    public void incrementCash(UUID uuid, long delta) {
        if (delta == 0) return;
        update(uuid, p -> {
            long cur = p.getCash();
            long newCash = Math.max(0L, cur + delta);
            p.setCash((int) Math.min(Integer.MAX_VALUE, newCash));
        }, "cash_increment");
    }

    public void setCash(UUID uuid, long amount) {
        long clamped = Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
        update(uuid, p -> p.setCash((int) clamped), "cash_set");
    }

    public void setRole(UUID uuid, int roleId) {
        update(uuid, p -> p.setRoleId(roleId), "set_role");
    }

    public void addPermission(UUID uuid, String permission) {
        if (permission == null || permission.isEmpty()) return;
        update(uuid, p -> {
            if (!p.getExtraPermissions().contains(permission)) {
                p.getExtraPermissions().add(permission);
            }
        }, "add_permission");
    }

    public void removePermission(UUID uuid, String permission) {
        if (permission == null || permission.isEmpty()) return;
        update(uuid, p -> p.getExtraPermissions().remove(permission), "remove_permission");
    }

    private void claimUsername(UUID ownerId, String username) {
        if (username == null || username.isEmpty()) return;
        repository.findByUsername(username).ifPresent(other -> {
            if (!ownerId.equals(other.getUuid())) {
                other.setUsername(null);
                save(other);
            }
        });
    }

    private void claimName(UUID ownerId, String name) {
        if (name == null || name.isEmpty()) return;
        repository.findByName(name).ifPresent(other -> {
            if (!ownerId.equals(other.getUuid())) {
                other.setName(null);
                save(other);
            }
        });
    }

    private void update(UUID uuid, Consumer<Profile> changer, String action) {
        repository.findByUuid(uuid).ifPresent(p -> {
            changer.accept(p);
            p.setUpdatedAt(System.currentTimeMillis());
            save(p);
        });
    }

    private void publish(String action, Profile profile) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());
            node.put("name", profile.getName());
            node.put("username", profile.getUsername());
            node.put("cash", profile.getCash());

            if (profile.getRoleId() != null) {
                node.put("roleId", profile.getRoleId());
            }
            if (profile.getExtraPermissions() != null && !profile.getExtraPermissions().isEmpty()) {
                var permsArray = mapper.createArrayNode();
                profile.getExtraPermissions().forEach(permsArray::add);
                node.set("extraPermissions", permsArray);
            }

            String json = mapper.writeValueAsString(node);
            RedisPublisher.publish(RedisChannel.PROFILES_SYNC, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}