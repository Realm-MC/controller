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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileService {

    private final ProfileRepository repository = new ProfileRepository();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(ProfileService.class.getName());

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

    public Optional<Profile> getById(int id) {
        return repository.findById(id);
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
        getPermissionService().ifPresent(ps -> ps.clearCache(profile.getUuid()));
    }

    public void delete(UUID uuid) {
        repository.findByUuid(uuid).ifPresent(p -> {
            repository.deleteByUuid(uuid);
            publish("delete", p);
            getPermissionService().ifPresent(ps -> ps.clearCache(uuid));
        });
    }

    public boolean exists(UUID uuid) {
        return repository.findByUuid(uuid).isPresent();
    }

    public Profile ensureProfile(UUID uuid, String displayName, String username, String firstIp, String clientVersion, String clientType, boolean isPremium) {
        String usernameLower = username.toLowerCase();
        Profile profile = repository.findByUuid(uuid).orElseGet(() -> {
            long now = System.currentTimeMillis();
            claimUsername(uuid, usernameLower);
            claimName(uuid, displayName);

            Profile newProfile = Profile.builder()
                    .id(MongoSequences.getNext("profiles"))
                    .uuid(uuid)
                    .name(displayName)
                    .username(usernameLower)
                    .firstIp(firstIp)
                    .lastIp(firstIp)
                    .ipHistory(new ArrayList<>(firstIp != null ? List.of(firstIp) : List.of()))
                    .firstLogin(now)
                    .lastLogin(now)
                    .lastClientVersion(clientVersion)
                    .lastClientType(clientType)
                    .cash(0)
                    .premiumAccount(isPremium)
                    .roleIds(new ArrayList<>(List.of(DefaultRole.DEFAULT.getId()))) // Start with default role
                    .roleExpirations(new HashMap<>())
                    .pausedRoleDurations(new HashMap<>())
                    .extraPermissions(new ArrayList<>())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Profile finalNewProfile = newProfile;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalNewProfile));
            save(newProfile);
            LOGGER.log(Level.INFO, "Created new profile for {0} ({1})", new Object[]{displayName, uuid});
            return newProfile;
        });

        boolean needsSave = false;
        if (displayName != null && !displayName.isEmpty() && !displayName.equals(profile.getName())) {
            claimName(uuid, displayName);
            profile.setName(displayName);
            needsSave = true;
            LOGGER.log(Level.INFO, "Updating display name for {0} to {1}", new Object[]{uuid, displayName});
        }
        if (usernameLower != null && !usernameLower.isEmpty() && !usernameLower.equals(profile.getUsername())) {
            claimUsername(uuid, usernameLower);
            profile.setUsername(usernameLower);
            needsSave = true;
            LOGGER.log(Level.INFO, "Updating username for {0} to {1}", new Object[]{uuid, usernameLower});
        }

        if (profile.getRoleIds() == null) profile.setRoleIds(new ArrayList<>());
        if (profile.getRoleIds().isEmpty()) {
            profile.getRoleIds().add(DefaultRole.DEFAULT.getId());
            needsSave = true;
        }

        if (profile.getIpHistory() == null) profile.setIpHistory(new ArrayList<>());
        if (firstIp != null && !firstIp.isEmpty() && !profile.getIpHistory().contains(firstIp)) {
            profile.getIpHistory().add(firstIp);
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

        if (reconcileRoleStates(profile)) needsSave = true;
        if (checkAndRemoveExpiredRoles(profile)) needsSave = true; // Check expiration on login

        if (needsSave) save(profile);

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

    public void addRole(UUID uuid, int roleId, long durationMillis) {
        update(uuid, profile -> {
            if (profile.getRoleIds() == null) profile.setRoleIds(new ArrayList<>());
            if (!profile.getRoleIds().contains(roleId)) {
                profile.getRoleIds().add(roleId);
            }
            if (profile.getRoleExpirations() == null) profile.setRoleExpirations(new HashMap<>());

            if (durationMillis > 0) {
                long expirationTime = System.currentTimeMillis() + durationMillis;
                profile.getRoleExpirations().put(String.valueOf(roleId), expirationTime);
                LOGGER.log(Level.INFO, "Adding role {0} to {1} with expiration {2}", new Object[]{roleId, uuid, new Date(expirationTime)});
            } else {
                profile.getRoleExpirations().remove(String.valueOf(roleId));
                LOGGER.log(Level.INFO, "Adding permanent role {0} to {1}", new Object[]{roleId, uuid});
            }
            reconcileRoleStates(profile);
        }, "add_role");
    }

    public void removeRole(UUID uuid, int roleId) {
        update(uuid, profile -> {
            boolean removed = false;
            if (profile.getRoleIds() != null) removed = profile.getRoleIds().remove(Integer.valueOf(roleId));
            if (profile.getRoleExpirations() != null) profile.getRoleExpirations().remove(String.valueOf(roleId));
            if (profile.getPausedRoleDurations() != null) profile.getPausedRoleDurations().remove(String.valueOf(roleId));
            if(removed) {
                LOGGER.log(Level.INFO, "Removing role {0} from {1}", new Object[]{roleId, uuid});
                reconcileRoleStates(profile);
                if (profile.getRoleIds().isEmpty()) {
                    profile.getRoleIds().add(DefaultRole.DEFAULT.getId());
                    LOGGER.log(Level.WARNING, "Player {0} had no roles left, added default role.", uuid);
                }
            }
        }, "remove_role");
    }

    public void addPermission(UUID uuid, String permission) {
        if (permission == null || permission.isEmpty()) return;
        update(uuid, p -> {
            if (p.getExtraPermissions() == null) p.setExtraPermissions(new ArrayList<>());
            if (!p.getExtraPermissions().contains(permission)) {
                p.getExtraPermissions().add(permission);
                LOGGER.log(Level.INFO, "Adding permission '{0}' to {1}", new Object[]{permission, uuid});
            }
        }, "add_permission");
    }

    public void removePermission(UUID uuid, String permission) {
        if (permission == null || permission.isEmpty()) return;
        update(uuid, p -> {
            if (p.getExtraPermissions() != null && p.getExtraPermissions().remove(permission)) {
                LOGGER.log(Level.INFO, "Removing permission '{0}' from {1}", new Object[]{permission, uuid});
            }
        }, "remove_permission");
    }


    public boolean reconcileRoleStates(Profile profile) {
        Optional<RoleService> roleServiceOpt = getRoleService();
        if (roleServiceOpt.isEmpty()) return false;
        RoleService roleService = roleServiceOpt.get();

        if(profile.getRoleIds() == null) profile.setRoleIds(new ArrayList<>());

        boolean isStaff = profile.getRoleIds().stream()
                .map(roleService::getById).filter(Optional::isPresent).map(Optional::get)
                .anyMatch(role -> role.getType() == RoleType.STAFF);

        long now = System.currentTimeMillis();
        boolean changed = false;

        if(profile.getRoleExpirations() == null) profile.setRoleExpirations(new HashMap<>());
        if(profile.getPausedRoleDurations() == null) profile.setPausedRoleDurations(new HashMap<>());

        if (isStaff) {
            Iterator<Map.Entry<String, Long>> iterator = profile.getRoleExpirations().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                try {
                    int roleId = Integer.parseInt(entry.getKey());
                    Optional<Role> roleOpt = roleService.getById(roleId);
                    if (roleOpt.isPresent() && roleOpt.get().getType() == RoleType.VIP) {
                        long remainingDuration = entry.getValue() - now;
                        if (remainingDuration > 0) {
                            profile.getPausedRoleDurations().put(entry.getKey(), remainingDuration);
                            iterator.remove();
                            changed = true;
                            LOGGER.log(Level.INFO, "Pausing VIP role {0} for staff player {1}. Remaining: {2}ms", new Object[]{roleId, profile.getUuid(), remainingDuration});
                        } else {
                            iterator.remove();
                            profile.getRoleIds().remove(Integer.valueOf(roleId));
                            changed = true;
                            LOGGER.log(Level.INFO, "Expired VIP role {0} removed during staff check for player {1}.", new Object[]{roleId, profile.getUuid()});
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        } else {
            Iterator<Map.Entry<String, Long>> iterator = profile.getPausedRoleDurations().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                long pausedDuration = entry.getValue();
                if(pausedDuration > 0) {
                    long newExpirationTime = now + pausedDuration;
                    profile.getRoleExpirations().put(entry.getKey(), newExpirationTime);
                    iterator.remove();
                    changed = true;
                    LOGGER.log(Level.INFO, "Resuming VIP role {0} for non-staff player {1}. New expiration: {2}", new Object[]{entry.getKey(), profile.getUuid(), new Date(newExpirationTime)});
                } else {
                    iterator.remove();
                    changed = true;
                }
            }
        }

        if (changed) {
            getPermissionService().ifPresent(ps -> ps.clearCache(profile.getUuid()));
        }
        return changed;
    }

    /**
     * Checks and removes expired roles, returning the list of removed Role objects.
     * Saves the profile if changes were made.
     */
    public List<Role> getAndRemoveExpiredRoles(Profile profile) {
        long now = System.currentTimeMillis();
        List<Role> removedRoles = new ArrayList<>();
        boolean changed = false;
        Optional<RoleService> roleServiceOpt = getRoleService();
        if (roleServiceOpt.isEmpty()) {
            LOGGER.log(Level.SEVERE, "RoleService not found when checking expired roles for {0}!", profile.getUuid());
            return removedRoles;
        }
        RoleService roleService = roleServiceOpt.get();

        if (profile.getRoleIds() == null) profile.setRoleIds(new ArrayList<>());
        if (profile.getRoleExpirations() == null) profile.setRoleExpirations(new HashMap<>());

        Iterator<Map.Entry<String, Long>> iterator = profile.getRoleExpirations().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            String roleIdStr = entry.getKey();
            Long expirationTime = entry.getValue();

            if (expirationTime != null && now >= expirationTime) {
                try {
                    int roleId = Integer.parseInt(roleIdStr);
                    if (profile.getRoleIds().remove(Integer.valueOf(roleId))) {
                        roleService.getById(roleId).ifPresent(removedRoles::add);
                        iterator.remove();
                        changed = true;
                        LOGGER.log(Level.INFO, "Expired role {0} removed from player {1}.", new Object[]{roleId, profile.getUuid()});
                    } else {
                        iterator.remove();
                        changed = true;
                        LOGGER.log(Level.WARNING, "Expired role {0} in expirations but not in roleIds for player {1}. Cleaning up.", new Object[]{roleId, profile.getUuid()});
                    }
                } catch (NumberFormatException e) {
                    iterator.remove();
                    changed = true;
                    LOGGER.log(Level.WARNING, "Invalid expiration key found and removed: {0} for player {1}", new Object[]{roleIdStr, profile.getUuid()});
                }
            }
        }

        if (changed) {
            if (profile.getRoleIds().isEmpty()) {
                profile.getRoleIds().add(DefaultRole.DEFAULT.getId());
                LOGGER.log(Level.WARNING, "Player {0} had no roles left after expiration, added default role.", profile.getUuid());
            }
            getPermissionService().ifPresent(ps -> ps.clearCache(profile.getUuid()));
            save(profile);
        }
        return removedRoles;
    }

    /**
     * Gets roles expiring within the specified threshold. Does NOT remove them.
     */
    public List<Map.Entry<Role, Long>> getRolesExpiringSoon(Profile profile, long thresholdMillis) {
        long now = System.currentTimeMillis();
        long cutoffTime = now + thresholdMillis;
        List<Map.Entry<Role, Long>> expiringSoon = new ArrayList<>();

        Optional<RoleService> roleServiceOpt = getRoleService();
        if (roleServiceOpt.isEmpty()) return expiringSoon;
        RoleService roleService = roleServiceOpt.get();

        if (profile.getRoleExpirations() == null) return expiringSoon;

        for (Map.Entry<String, Long> entry : profile.getRoleExpirations().entrySet()) {
            Long expirationTime = entry.getValue();
            if (expirationTime != null && expirationTime > now && expirationTime <= cutoffTime) {
                long timeLeft = expirationTime - now;
                try {
                    int roleId = Integer.parseInt(entry.getKey());
                    roleService.getById(roleId).ifPresent(role -> expiringSoon.add(Map.entry(role, timeLeft)));
                } catch (NumberFormatException ignored) {}
            }
        }
        expiringSoon.sort(Map.Entry.comparingByValue());
        return expiringSoon;
    }

    /**
     * Simple check if any roles expired, calls getAndRemoveExpiredRoles internally.
     * Saves profile if changes were made.
     */
    public boolean checkAndRemoveExpiredRoles(Profile profile) {
        return !getAndRemoveExpiredRoles(profile).isEmpty();
    }



    private void claimUsername(UUID ownerId, String usernameLower) {
        if (usernameLower == null || usernameLower.isEmpty()) return;
        repository.findByUsername(usernameLower).ifPresent(other -> {
            if (!ownerId.equals(other.getUuid())) {
                LOGGER.log(Level.WARNING, "Username conflict: {0} was used by {1}, now claimed by {2}. Clearing old profile's username.", new Object[]{usernameLower, other.getUuid(), ownerId});
                other.setUsername(null);
                save(other);
            }
        });
    }

    private void claimName(UUID ownerId, String name) {
        if (name == null || name.isEmpty()) return;
        repository.findByName(name).ifPresent(other -> {
            if (!ownerId.equals(other.getUuid())) {
                LOGGER.log(Level.WARNING, "Display name conflict: {0} was used by {1}, now claimed by {2}. Clearing old profile's name.", new Object[]{name, other.getUuid(), ownerId});
                other.setName(null);
                save(other);
            }
        });
    }

    private void update(UUID uuid, Consumer<Profile> changer, String actionContext) {
        repository.findByUuid(uuid).ifPresent(p -> {
            changer.accept(p);
            save(p);
            LOGGER.log(Level.FINE, "Profile updated for {0} via {1}", new Object[]{uuid, actionContext});
        });
    }

    private void publish(String action, Profile profile) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());
            node.put("id", profile.getId());
            node.put("name", profile.getName());
            node.put("username", profile.getUsername());
            node.put("cash", profile.getCash());
            node.put("premium", profile.isPremiumAccount());

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
            LOGGER.log(Level.FINER, "Published profile sync message: {0}", json);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to publish profile sync message for " + profile.getUuid(), e);
        }
    }
}