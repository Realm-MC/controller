package com.realmmc.controller.shared.permission;

import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionService {

    private final RoleService roleService;
    private final Map<UUID, Set<String>> cachedPermissions = new ConcurrentHashMap<>();

    public PermissionService(RoleService roleService) {
        this.roleService = roleService;
    }

    public boolean hasPermission(Profile profile, String permission) {
        if (profile == null || permission == null || permission.isEmpty()) {
            return false;
        }
        var allPermissions = getEffectivePermissions(profile);
        if (allPermissions.contains("*")) {
            return true;
        }
        if (allPermissions.contains(permission)) {
            return true;
        }
        if (allPermissions.contains("-" + permission)) {
            return false;
        }
        return matchesWildcard(permission, allPermissions);
    }

    public Set<String> getEffectivePermissions(Profile profile) {
        if (profile == null) return Collections.emptySet();
        if (cachedPermissions.containsKey(profile.getUuid())) {
            return cachedPermissions.get(profile.getUuid());
        }

        var permissions = new HashSet<String>();
        if (profile.getRoleId() != null) {
            roleService.getById(profile.getRoleId()).ifPresent(r -> permissions.addAll(resolveRolePermissions(r, new HashSet<>())));
        }
        if (profile.getExtraPermissions() != null) {
            permissions.addAll(profile.getExtraPermissions());
        }
        cachedPermissions.put(profile.getUuid(), permissions);
        return permissions;
    }

    private Set<String> resolveRolePermissions(Role role, Set<Integer> visited) {
        if (role == null || visited.contains(role.getId())) {
            return Collections.emptySet();
        }
        visited.add(role.getId());
        var permissions = new HashSet<>(role.getPermissions());

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
                String base = p.substring(0, p.length() - 1);
                if (permission.startsWith(base)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void clearCache(UUID uuid) {
        cachedPermissions.remove(uuid);
    }
    public void clearAllCache() {
        cachedPermissions.clear();
    }
}