package com.realmmc.controller.shared.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleService;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionService {

    private final RoleService roleService;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<UUID, Set<String>> localPermissionsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Role> localPrimaryRoleCache = new ConcurrentHashMap<>();

    private static final String PERMS_CACHE_PREFIX = "controller:perms_cache:";
    private static final String PRIMARY_ROLE_CACHE_PREFIX = "controller:primary_role_cache:";
    private static final int CACHE_EXPIRATION_SECONDS = 3600;

    public PermissionService(RoleService roleService) {
        this.roleService = roleService;
    }

    public boolean hasPermission(Profile profile, String permission) {
        if (profile == null || permission == null || permission.isEmpty()) {
            return false;
        }
        Set<String> effectivePermissions = getEffectivePermissions(profile);
        if (effectivePermissions.contains("*")) {
            return true;
        }
        if (effectivePermissions.contains("-" + permission)) {
            return false;
        }
        if (effectivePermissions.contains(permission)) {
            return true;
        }
        return matchesWildcard(permission, effectivePermissions);
    }

    public Set<String> getEffectivePermissions(Profile profile) {
        if (profile == null) return Collections.emptySet();
        UUID uuid = profile.getUuid();

        if (localPermissionsCache.containsKey(uuid)) {
            return localPermissionsCache.get(uuid);
        }

        String cacheKey = PERMS_CACHE_PREFIX + uuid;
        try (Jedis jedis = RedisManager.getResource()) {
            String cachedJson = jedis.get(cacheKey);
            if (cachedJson != null) {
                Set<String> permissions = mapper.readValue(cachedJson, new TypeReference<Set<String>>() {});
                localPermissionsCache.put(uuid, permissions);
                return permissions;
            }
        } catch (Exception e) {
            System.err.println("[PermissionService] Erro ao ler cache de permissões do Redis: " + e.getMessage());
        }

        Set<String> permissions = calculateEffectivePermissions(profile);

        try (Jedis jedis = RedisManager.getResource()) {
            String jsonToCache = mapper.writeValueAsString(permissions);
            jedis.setex(cacheKey, CACHE_EXPIRATION_SECONDS, jsonToCache);
        } catch (Exception e) {
            System.err.println("[PermissionService] Erro ao guardar cache de permissões no Redis: " + e.getMessage());
        }
        localPermissionsCache.put(uuid, permissions);

        return permissions;
    }

    private Set<String> calculateEffectivePermissions(Profile profile) {
        Set<String> permissions = new HashSet<>();
        for (int roleId : profile.getRoleIds()) {
            roleService.getById(roleId).ifPresent(role -> permissions.addAll(resolveRolePermissions(role, new HashSet<>())));
        }
        permissions.addAll(profile.getExtraPermissions());
        return permissions;
    }

    public Role getPrimaryRole(Profile profile) {
        if (profile == null || profile.getRoleIds().isEmpty()) {
            return null;
        }
        UUID uuid = profile.getUuid();

        if (localPrimaryRoleCache.containsKey(uuid)) {
            return localPrimaryRoleCache.get(uuid);
        }

        String cacheKey = PRIMARY_ROLE_CACHE_PREFIX + uuid;
        try (Jedis jedis = RedisManager.getResource()) {
            String cachedRoleIdStr = jedis.get(cacheKey);
            if (cachedRoleIdStr != null) {
                int roleId = Integer.parseInt(cachedRoleIdStr);
                Optional<Role> roleOpt = roleService.getById(roleId);
                if (roleOpt.isPresent()) {
                    localPrimaryRoleCache.put(uuid, roleOpt.get());
                    return roleOpt.get();
                }
            }
        } catch (Exception e) {
            System.err.println("[PermissionService] Erro ao ler cache de cargo primário do Redis: " + e.getMessage());
        }

        Role primaryRole = profile.getRoleIds().stream()
                .map(roleService::getById)
                .filter(Optional::isPresent).map(Optional::get)
                .max(Comparator.comparingInt(Role::getWeight))
                .orElse(null);

        if (primaryRole != null) {
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.setex(cacheKey, CACHE_EXPIRATION_SECONDS, String.valueOf(primaryRole.getId()));
            } catch (Exception e) {
                System.err.println("[PermissionService] Erro ao guardar cache de cargo primário no Redis: " + e.getMessage());
            }
            localPrimaryRoleCache.put(uuid, primaryRole);
        }

        return primaryRole;
    }

    private Set<String> resolveRolePermissions(Role role, Set<Integer> visited) {
        if (role == null || visited.contains(role.getId())) {
            return Collections.emptySet();
        }
        visited.add(role.getId());
        Set<String> permissions = new HashSet<>(role.getPermissions());

        if (role.getInherits() != null) {
            for (var inheritIdStr : role.getInherits()) {
                try {
                    int inheritId = Integer.parseInt(inheritIdStr);
                    roleService.getById(inheritId).ifPresent(r -> permissions.addAll(resolveRolePermissions(r, visited)));
                } catch (NumberFormatException ignored) {}
            }
        }
        return permissions;
    }

    private boolean matchesWildcard(String permission, Set<String> allPermissions) {
        for (String p : allPermissions) {
            if (p.endsWith(".*")) {
                if (permission.startsWith(p.substring(0, p.length() - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    public void clearCache(UUID uuid) {
        localPermissionsCache.remove(uuid);
        localPrimaryRoleCache.remove(uuid);
        try (Jedis jedis = RedisManager.getResource()) {
            jedis.del(PERMS_CACHE_PREFIX + uuid);
            jedis.del(PRIMARY_ROLE_CACHE_PREFIX + uuid);
        } catch (Exception e) {
            System.err.println("[PermissionService] Erro ao limpar cache do Redis para " + uuid + ": " + e.getMessage());
        }
    }

    public void clearAllCache() {
        localPermissionsCache.clear();
        localPrimaryRoleCache.clear();
    }
}