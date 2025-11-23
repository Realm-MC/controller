package com.realmmc.controller.shared.cosmetics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CosmeticsService implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(CosmeticsService.class.getName());
    private final CosmeticsRepository repository = new CosmeticsRepository();
    private final Map<UUID, List<String>> medalsCache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public CosmeticsService() {
        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.registerListener(RedisChannel.COSMETICS_SYNC, this));
    }

    public Cosmetics ensureCosmetics(Profile profile) {
        // Tenta encontrar no banco
        Optional<Cosmetics> existing = repository.findByUuid(profile.getUuid());

        if (existing.isPresent()) {
            Cosmetics c = existing.get();
            // CORREÇÃO: Se existe no banco, FORÇA a atualização do cache local agora
            updateCache(c.getUuid(), c.getUnlockedMedals());
            return c;
        } else {
            // Se não existe, cria um novo
            Cosmetics newCosmetics = Cosmetics.builder()
                    .id(MongoSequences.getNext("cosmetics"))
                    .uuid(profile.getUuid())
                    .name(profile.getName())
                    .unlockedMedals(new ArrayList<>())
                    .build();
            save(newCosmetics); // O save já atualiza o cache
            return newCosmetics;
        }
    }

    public void save(Cosmetics cosmetics) {
        repository.upsert(cosmetics);
        updateCache(cosmetics.getUuid(), cosmetics.getUnlockedMedals());
        publishUpdate(cosmetics);
    }

    public boolean hasMedal(UUID uuid, String medalId) {
        List<String> owned = medalsCache.get(uuid);
        if (owned == null) {
            // Cache miss: tenta carregar do banco
            Optional<Cosmetics> c = repository.findByUuid(uuid);
            if (c.isPresent()) {
                // Atualiza o cache para a próxima vez
                updateCache(uuid, c.get().getUnlockedMedals());
                return c.get().getUnlockedMedals().contains(medalId.toLowerCase());
            }
            return false;
        }
        return owned.contains(medalId.toLowerCase());
    }

    public void addMedal(UUID uuid, String name, String medalId) {
        Optional<Cosmetics> opt = repository.findByUuid(uuid);
        Cosmetics cosmetics;
        if (opt.isEmpty()) {
            cosmetics = Cosmetics.builder()
                    .id(MongoSequences.getNext("cosmetics"))
                    .uuid(uuid)
                    .name(name)
                    .unlockedMedals(new ArrayList<>())
                    .build();
        } else {
            cosmetics = opt.get();
        }

        if (!cosmetics.getUnlockedMedals().contains(medalId.toLowerCase())) {
            cosmetics.getUnlockedMedals().add(medalId.toLowerCase());
            save(cosmetics);
        }
    }

    public void removeMedal(UUID uuid, String medalId) {
        repository.findByUuid(uuid).ifPresent(cosmetics -> {
            if (cosmetics.getUnlockedMedals().remove(medalId.toLowerCase())) {
                save(cosmetics);
            }
        });
    }

    private void updateCache(UUID uuid, List<String> medals) {
        medalsCache.put(uuid, new ArrayList<>(medals));
    }

    public List<String> getCachedMedals(UUID uuid) {
        return medalsCache.getOrDefault(uuid, Collections.emptyList());
    }

    private void publishUpdate(Cosmetics cosmetics) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("uuid", cosmetics.getUuid().toString());
            var array = node.putArray("medals");
            cosmetics.getUnlockedMedals().forEach(array::add);
            RedisPublisher.publish(RedisChannel.COSMETICS_SYNC, node.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error publishing cosmetics sync", e);
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.COSMETICS_SYNC.getName().equals(channel)) return;
        try {
            JsonNode node = mapper.readTree(message);
            UUID uuid = UUID.fromString(node.get("uuid").asText());
            List<String> medals = new ArrayList<>();
            if (node.has("medals")) {
                node.get("medals").forEach(n -> medals.add(n.asText()));
            }
            updateCache(uuid, medals);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing cosmetics sync", e);
        }
    }
}