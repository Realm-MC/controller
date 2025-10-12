package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.permission.PermissionService;
import com.realmmc.controller.shared.role.DefaultRole;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleService;
import com.realmmc.controller.shared.role.RoleType;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;

import java.util.*;
import java.util.function.Consumer;

public class ProfileService {

    private final ProfileRepository repository = new ProfileRepository();
    private final ObjectMapper mapper = new ObjectMapper();

    private Optional<StatisticsService> getStatsService() {
        return ServiceRegistry.getInstance().getService(StatisticsService.class);
    }
    private Optional<RoleService> getRoleService() {
        return ServiceRegistry.getInstance().getService(RoleService.class);
    }
    private Optional<PermissionService> getPermissionService() {
        return ServiceRegistry.getInstance().getService(PermissionService.class);
    }

    public Optional<Profile> getByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    public Optional<Profile> getByName(String name) {
        return repository.findByName(name);
    }

    public Optional<Profile> getByUsername(String username) {
        return repository.findByUsername(username.toLowerCase());
    }

    public Optional<Profile> getById(int id) {
        return repository.findById(id);
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
            String usernameLower = username.toLowerCase();
            claimUsername(uuid, usernameLower);
            claimName(uuid, displayName);

            Profile newProfile = new Profile();
            newProfile.setId(MongoSequences.getNext("profiles"));
            newProfile.setUuid(uuid);
            newProfile.setName(displayName);
            newProfile.setUsername(usernameLower);
            newProfile.setFirstIp(firstIp);
            newProfile.setLastIp(firstIp);
            newProfile.setFirstLogin(now);
            newProfile.setLastLogin(now);
            newProfile.setLastClientVersion(clientVersion);
            newProfile.setLastClientType(clientType);
            newProfile.setCreatedAt(now);
            newProfile.setUpdatedAt(now);
            newProfile.setPremiumAccount(isPremium);
            newProfile.getRoleIds().add(DefaultRole.DEFAULT.getId());

            if (firstIp != null) newProfile.getIpHistory().add(firstIp);

            getStatsService().ifPresent(stats -> stats.ensureStatistics(newProfile));

            save(newProfile);
            return newProfile;
        });

        boolean needsSave = false;
        if (displayName != null && !displayName.isEmpty() && !displayName.equals(profile.getName())) {
            claimName(uuid, displayName);
            profile.setName(displayName);
            needsSave = true;
        }

        String usernameLower = username.toLowerCase();
        if (!usernameLower.equals(profile.getUsername())) {
            claimUsername(uuid, usernameLower);
            profile.setUsername(usernameLower);
            needsSave = true;
        }

        if (profile.getRoleIds() == null || profile.getRoleIds().isEmpty()) {
            profile.setRoleIds(new ArrayList<>(List.of(DefaultRole.DEFAULT.getId())));
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

        getStatsService().ifPresent(stats -> stats.updateIdentification(profile));

        if (reconcileRoleStates(profile)) {
            needsSave = true;
        }

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
                getStatsService().ifPresent(stats -> stats.updateIdentification(p));
            }
        }, "update_name");
    }

    public void setUsername(UUID uuid, String username) {
        if (username == null || username.isEmpty()) return;
        update(uuid, p -> {
            String usernameLower = username.toLowerCase();
            if (!usernameLower.equals(p.getUsername())) {
                claimUsername(uuid, usernameLower);
                p.setUsername(usernameLower);
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

    public void addRole(UUID uuid, int roleId, long durationMillis) {
        update(uuid, profile -> {
            if (!profile.getRoleIds().contains(roleId)) {
                profile.getRoleIds().add(roleId);
            }
            if (durationMillis > 0) {
                long expirationTime = System.currentTimeMillis() + durationMillis;
                profile.getRoleExpirations().put(String.valueOf(roleId), expirationTime);
            } else {
                profile.getRoleExpirations().remove(String.valueOf(roleId));
            }
            reconcileRoleStates(profile);
        }, "add_role");
    }

    public void removeRole(UUID uuid, int roleId) {
        update(uuid, profile -> {
            if (roleId == DefaultRole.DEFAULT.getId()) return;
            profile.getRoleIds().remove(Integer.valueOf(roleId));
            profile.getRoleExpirations().remove(String.valueOf(roleId));
            profile.getPausedRoleDurations().remove(String.valueOf(roleId));
            reconcileRoleStates(profile);
        }, "remove_role");
    }

    public void setRoles(UUID uuid, List<Integer> roleIds) {
        update(uuid, profile -> {
            profile.getRoleIds().clear();
            profile.getRoleExpirations().clear();
            profile.getPausedRoleDurations().clear();
            profile.getRoleIds().addAll(roleIds);
            if (!profile.getRoleIds().contains(DefaultRole.DEFAULT.getId())) {
                profile.getRoleIds().add(DefaultRole.DEFAULT.getId());
            }
            reconcileRoleStates(profile);
        }, "set_roles");
    }

    public void clearRoles(UUID uuid) {
        setRoles(uuid, new ArrayList<>());
    }

    public List<Profile> getProfilesInRole(int roleId) {
        return repository.findByRoleId(roleId);
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

    public boolean reconcileRoleStates(Profile profile) {
        Optional<RoleService> roleServiceOpt = getRoleService();
        if (roleServiceOpt.isEmpty()) return false;
        RoleService roleService = roleServiceOpt.get();

        boolean isStaff = profile.getRoleIds().stream()
                .map(roleService::getById)
                .filter(Optional::isPresent).map(Optional::get)
                .anyMatch(role -> role.getType() == RoleType.STAFF);

        long now = System.currentTimeMillis();
        boolean changed = false;

        if (isStaff) {
            Iterator<Map.Entry<String, Long>> iterator = profile.getRoleExpirations().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                int roleId = Integer.parseInt(entry.getKey());
                Optional<Role> roleOpt = roleService.getById(roleId);
                if (roleOpt.isPresent() && roleOpt.get().getType() == RoleType.VIP) {
                    long remainingDuration = entry.getValue() - now;
                    if (remainingDuration > 0) {
                        profile.getPausedRoleDurations().put(entry.getKey(), remainingDuration);
                        iterator.remove();
                        changed = true;
                    }
                }
            }
        } else {
            Iterator<Map.Entry<String, Long>> iterator = profile.getPausedRoleDurations().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                long newExpirationTime = now + entry.getValue();
                profile.getRoleExpirations().put(entry.getKey(), newExpirationTime);
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            getPermissionService().ifPresent(ps -> ps.clearCache(profile.getUuid()));
        }
        return changed;
    }

    public List<Role> getAndRemoveExpiredVipRoles(Profile profile) {
        long now = System.currentTimeMillis();
        List<Role> expiredRoles = new ArrayList<>();
        boolean changed = false;
        Optional<RoleService> roleServiceOpt = getRoleService();
        if (roleServiceOpt.isEmpty()) return expiredRoles;
        RoleService roleService = roleServiceOpt.get();

        Iterator<Integer> iterator = new ArrayList<>(profile.getRoleIds()).iterator();
        while (iterator.hasNext()) {
            int roleId = iterator.next();
            Long expirationTime = profile.getRoleExpirations().get(String.valueOf(roleId));

            if (expirationTime != null && now >= expirationTime) {
                roleService.getById(roleId).ifPresent(role -> {
                    if (role.getType() == RoleType.VIP) {
                        expiredRoles.add(role);
                    }
                });
                profile.getRoleIds().remove(Integer.valueOf(roleId));
                profile.getRoleExpirations().remove(String.valueOf(roleId));
                changed = true;
            }
        }

        if (changed) {
            save(profile);
        }
        return expiredRoles;
    }

    public boolean checkAndRemoveExpiredRoles(Profile profile) {
        long now = System.currentTimeMillis();
        boolean changed = false;

        Iterator<Integer> iterator = new ArrayList<>(profile.getRoleIds()).iterator();
        while (iterator.hasNext()) {
            int roleId = iterator.next();
            Long expirationTime = profile.getRoleExpirations().get(String.valueOf(roleId));

            if (expirationTime != null && now >= expirationTime) {
                profile.getRoleIds().remove(Integer.valueOf(roleId));
                profile.getRoleExpirations().remove(String.valueOf(roleId));
                changed = true;
            }
        }

        if (changed) {
            save(profile);
        }
        return changed;
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
            ObjectNode node = new ObjectMapper().createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());
            node.put("name", profile.getName());
            node.put("username", profile.getUsername());
            node.put("cash", profile.getCash());

            if (profile.getRoleIds() != null && !profile.getRoleIds().isEmpty()) {
                var rolesNode = node.putArray("roleIds");
                profile.getRoleIds().forEach(rolesNode::add);
            }
            if (profile.getExtraPermissions() != null && !profile.getExtraPermissions().isEmpty()) {
                var permsArray = node.putArray("extraPermissions");
                profile.getExtraPermissions().forEach(permsArray::add);
            }

            String json = node.toString();
            RedisPublisher.publish(RedisChannel.PROFILES_SYNC, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}