package com.realmmc.controller.shared.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RoleService {

    private final RoleRepository repository = new RoleRepository();
    private final ObjectMapper mapper = new ObjectMapper();

    public RoleService() {
        ensureDefaultRoles();
    }

    private void ensureDefaultRoles() {
        for (DefaultRole defaultRole : DefaultRole.values()) {
            if (repository.findById(defaultRole.getId()).isEmpty()) {
                Role role = defaultRole.toRole();
                repository.upsert(role);
                System.out.println("[RoleService] Cargo padrão criado: " + role.getName());
            }
        }
    }

    public Optional<Role> getById(int id) {
        return repository.findById(id);
    }

    public Optional<Role> getByName(String name) {
        return repository.findByName(name);
    }

    public List<Role> getAll() {
        return repository.findAll();
    }

    public void save(Role role) {
        long now = System.currentTimeMillis();
        if (role.getCreatedAt() == 0L) {
            role.setCreatedAt(now);
        }
        role.setUpdatedAt(now);
        repository.upsert(role);
        publish("upsert", role);
    }

    public Role create(String name, String displayName, String prefix, RoleType type, int weight) {
        var role = Role.builder()
                .id(MongoSequences.getNext("roles"))
                .name(name)
                .displayName(displayName)
                .prefix(prefix)
                .type(type)
                .weight(weight)
                .build();
        save(role);
        return role;
    }

    public void delete(int id) {
        repository.findById(id).ifPresent(role -> {
            repository.deleteById(id);
            publish("delete", role);
        });
    }

    private void publish(String action, Role role) {
        try {
            var node = new ObjectMapper().createObjectNode();
            node.put("action", action);
            node.put("id", role.getId());
            var json = node.toString();
            RedisPublisher.publish(RedisChannel.ROLES_SYNC, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}