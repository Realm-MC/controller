package com.realmmc.controller.shared.permission;

import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleService;
import com.realmmc.controller.shared.role.RoleType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionService {

    private final RoleService roleService;
    private final Map<UUID, Set<String>> permissionsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Role> primaryRoleCache = new ConcurrentHashMap<>();

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
        return permissionsCache.computeIfAbsent(profile.getUuid(), uuid -> {
            Set<String> permissions = new HashSet<>();
            for (int roleId : profile.getRoleIds()) {
                roleService.getById(roleId).ifPresent(role -> permissions.addAll(resolveRolePermissions(role, new HashSet<>())));
            }
            permissions.addAll(profile.getExtraPermissions());
            return permissions;
        });
    }

    public Role getPrimaryRole(Profile profile) {
        if (profile == null || profile.getRoleIds().isEmpty()) {
            return null;
        }
        return primaryRoleCache.computeIfAbsent(profile.getUuid(), uuid ->
                profile.getRoleIds().stream()
                        .map(roleService::getById)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .max(Comparator.comparingInt(Role::getWeight))
                        .orElse(null)
        );
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
        permissionsCache.remove(uuid);
        primaryRoleCache.remove(uuid);
    }

    public void clearAllCache() {
        permissionsCache.clear();
        primaryRoleCache.clear();
    }
}