package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.utils.TimeUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ProfileService {

    private final ProfileRepository repository = new ProfileRepository();
    private final ObjectMapper mapper = new ObjectMapper();

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

    public Profile create(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        Profile profile = Profile.builder()
                .id(MongoSequences.getNext("profiles"))
                .uuid(uuid)
                .name(name)
                .createdAt(now)
                .updatedAt(now)
                .build();
        repository.upsert(profile);
        publish("create", profile);
        return profile;
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

    public void updateName(UUID uuid, String newName) {
        repository.findByUuid(uuid).ifPresent(p -> {
            p.setName(newName);
            p.setUpdatedAt(System.currentTimeMillis());
            repository.upsert(p);
            publish("update_name", p);
        });
    }

    public void recordLogin(UUID uuid, String ip, String clientVersion, String clientType, String username) {
        update(uuid, p -> {
            long now = System.currentTimeMillis();
            if (p.getFirstLogin() == 0L) p.setFirstLogin(now);
            if (p.getFirstIp() == null) p.setFirstIp(ip);
            p.setLastLogin(now);
            p.setLastIp(ip);
            if (username != null && !username.isEmpty()) {
                claimUsername(uuid, username);
                p.setUsername(username);
            }
            if (clientVersion != null) p.setLastClientVersion(clientVersion);
            if (clientType != null) p.setLastClientType(clientType);
            if (ip != null) {
                if (p.getIpHistory().isEmpty() || !ip.equals(p.getIpHistory().get(p.getIpHistory().size() - 1))) {
                    p.getIpHistory().add(ip);
                }
            }
        }, "record_login");
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

    public void updateCashTopPosition(UUID uuid, Integer position, Long enteredAt) {
        update(uuid, p -> {
            p.setCashTopPosition(position);
            p.setCashTopPositionEnteredAt(enteredAt);
        }, "cash_top_update");
    }

    public void updateCashTopPositionAuto(UUID uuid, Integer position) {
        update(uuid, p -> {
            if (!Objects.equals(p.getCashTopPosition(), position)) {
                p.setCashTopPosition(position);
                p.setCashTopPositionEnteredAt(System.currentTimeMillis());
            }
        }, "cash_top_update");
    }

    public Optional<String> getCashTopPositionEnteredAtFormatted(UUID uuid) {
        return repository.findByUuid(uuid)
                .flatMap(p -> p.getCashTopPositionEnteredAt() == null
                        ? Optional.empty()
                        : Optional.of(TimeUtils.formatDate(p.getCashTopPositionEnteredAt())));
    }

    public Profile ensureProfile(UUID uuid, String displayName, String username, String firstIp, String clientVersion, String clientType) {
        Optional<Profile> existing = repository.findByUuid(uuid);
        if (existing.isPresent()) {
            Profile p = existing.get();
            if (displayName != null && !displayName.isEmpty() && !displayName.equals(p.getName())) {
                p.setName(displayName);
            }
            if (username != null && !username.isEmpty() && !username.equals(p.getUsername())) {
                claimUsername(uuid, username);
                p.setUsername(username);
            }
            long now = System.currentTimeMillis();
            if (p.getCreatedAt() == 0L) p.setCreatedAt(now);
            p.setUpdatedAt(now);
            if (p.getFirstLogin() == 0L) p.setFirstLogin(now);
            if (p.getFirstIp() == null && firstIp != null) p.setFirstIp(firstIp);
            p.setLastLogin(now);
            if (firstIp != null) p.setLastIp(firstIp);
            if (clientVersion != null) p.setLastClientVersion(clientVersion);
            if (clientType != null) p.setLastClientType(clientType);
            if (p.getId() == null) p.setId(MongoSequences.getNext("profiles"));
            repository.upsert(p);
            publish("ensure", p);
            return p;
        } else {
            long now = System.currentTimeMillis();
            if (username != null && !username.isEmpty()) {
                claimUsername(uuid, username);
            }
            Profile p = Profile.builder()
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
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            if (firstIp != null) p.getIpHistory().add(firstIp);
            repository.upsert(p);
            publish("create", p);
            return p;
        }
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

    private void claimUsername(UUID ownerId, String username) {
        repository.findByUsername(username).ifPresent(other -> {
            if (!ownerId.equals(other.getUuid())) {
                other.setUsername(null);
                repository.upsert(other);
                publish("username_unclaimed", other);
            }
        });
    }

    private void update(UUID uuid, Consumer<Profile> changer, String action) {
        repository.findByUuid(uuid).ifPresent(p -> {
            changer.accept(p);
            p.setUpdatedAt(System.currentTimeMillis());
            repository.upsert(p);
            publish(action, p);
        });
    }

    private void publish(String action, Profile profile) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("id", profile.getId());
            if (profile.getUuid() != null) node.put("uuid", profile.getUuid().toString());
            node.put("name", profile.getName());
            node.put("updatedAt", profile.getUpdatedAt());
            if (profile.getUsername() != null) node.put("username", profile.getUsername());
            if (profile.getLastIp() != null) node.put("lastIp", profile.getLastIp());
            if (profile.getLastClientVersion() != null) node.put("lastClientVersion", profile.getLastClientVersion());
            if (profile.getLastClientType() != null) node.put("lastClientType", profile.getLastClientType());
            node.put("cash", profile.getCash());
            if (profile.getCashTopPosition() != null) node.put("cashTopPosition", profile.getCashTopPosition());
            if (profile.getCashTopPositionEnteredAt() != null)
                node.put("cashTopPositionEnteredAt", profile.getCashTopPositionEnteredAt());
            String json = mapper.writeValueAsString(node);
            RedisPublisher.publish(RedisChannel.PROFILES_SYNC, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
