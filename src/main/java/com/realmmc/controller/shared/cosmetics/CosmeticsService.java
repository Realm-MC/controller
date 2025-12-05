package com.realmmc.controller.shared.cosmetics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.cosmetics.medals.UnlockedMedal;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import com.realmmc.controller.shared.utils.TaskScheduler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CosmeticsService implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(CosmeticsService.class.getName());
    private final CosmeticsRepository repository = new CosmeticsRepository();
    private final Map<UUID, List<String>> activeMedalsCache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayer;

    public CosmeticsService() {
        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.registerListener(RedisChannel.COSMETICS_SYNC, this));

        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.soundPlayer = ServiceRegistry.getInstance().getService(SoundPlayer.class);

        startExpirationTask();
    }

    private void startExpirationTask() {
        TaskScheduler.runAsyncTimer(() -> {
            try {
                for (UUID uuid : activeMedalsCache.keySet()) {
                    checkAndRemoveExpired(uuid);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro na tarefa de verificação de expiração de cosméticos", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
        LOGGER.info("[CosmeticsService] Tarefa de verificação de expiração iniciada.");
    }

    private void checkAndRemoveExpired(UUID uuid) {
        Optional<Cosmetics> cosmeticsOpt = repository.findByUuid(uuid);
        if (cosmeticsOpt.isEmpty()) return;

        Cosmetics cosmetics = cosmeticsOpt.get();
        List<UnlockedMedal> medals = cosmetics.getUnlockedMedals();
        if (medals == null || medals.isEmpty()) return;

        List<String> expiredIds = new ArrayList<>();
        boolean changed = medals.removeIf(m -> {
            if (m.hasExpired()) {
                expiredIds.add(m.getMedalId());
                return true;
            }
            return false;
        });

        if (changed) {
            save(cosmetics);
            LOGGER.info("[CosmeticsService] Removidas " + expiredIds.size() + " medalhas expiradas de " + uuid);

            profileService.getByUuid(uuid).ifPresent(profile -> {
                String currentEquipped = profile.getEquippedMedal();

                if (expiredIds.contains(currentEquipped.toLowerCase())) {
                    profile.setEquippedMedal("none");
                    profileService.save(profile);

                    LOGGER.info("[CosmeticsService] Medalha equipada '" + currentEquipped + "' expirou e foi removida de " + profile.getName());

                    notifyPlayerExpiration(uuid, currentEquipped);
                }
            });
        }
    }

    private void notifyPlayerExpiration(UUID uuid, String medalId) {
        Object tempPlayerObj = null;

        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            tempPlayerObj = bukkitClass.getMethod("getPlayer", UUID.class).invoke(null, uuid);
        } catch (Exception e) {
            try {
                Class<?> proxyClass = Class.forName("com.realmmc.controller.proxy.Proxy");
                Object proxyInstance = proxyClass.getMethod("getInstance").invoke(null);
                Object server = proxyClass.getMethod("getServer").invoke(proxyInstance);
                Optional<?> pOpt = (Optional<?>) server.getClass().getMethod("getPlayer", UUID.class).invoke(server, uuid);
                if (pOpt.isPresent()) tempPlayerObj = pOpt.get();
            } catch (Exception ignored) {}
        }

        final Object finalPlayerObj = tempPlayerObj;

        if (finalPlayerObj != null) {
            Messages.send(finalPlayerObj, Message.of(MessageKey.MEDAL_UNEQUIPPED));
            soundPlayer.ifPresent(sp -> sp.playSound(finalPlayerObj, SoundKeys.COSMETIC_EXPIRE));
        }
    }

    public Cosmetics ensureCosmetics(Profile profile) {
        Optional<Cosmetics> existing = repository.findByUuid(profile.getUuid());

        if (existing.isPresent()) {
            Cosmetics c = existing.get();
            cleanupExpiredMedals(c);
            updateCache(c);
            return c;
        } else {
            Cosmetics newCosmetics = Cosmetics.builder()
                    .id(profile.getId())
                    .uuid(profile.getUuid())
                    .name(profile.getName())
                    .unlockedMedals(new ArrayList<>())
                    .build();
            save(newCosmetics);
            return newCosmetics;
        }
    }

    private void cleanupExpiredMedals(Cosmetics cosmetics) {
        if (cosmetics.getUnlockedMedals() == null) return;
        boolean changed = cosmetics.getUnlockedMedals().removeIf(UnlockedMedal::hasExpired);
        if (changed) {
            repository.upsert(cosmetics);
        }
    }

    public void save(Cosmetics cosmetics) {
        cleanupExpiredMedals(cosmetics);
        repository.upsert(cosmetics);
        updateCache(cosmetics);
        publishUpdate(cosmetics);
    }

    public boolean hasMedal(UUID uuid, String medalId) {
        List<String> owned = activeMedalsCache.get(uuid);
        if (owned == null) {
            Optional<Cosmetics> c = repository.findByUuid(uuid);
            if (c.isPresent()) {
                updateCache(c.get());
                return activeMedalsCache.get(uuid).contains(medalId.toLowerCase());
            }
            return false;
        }
        return owned.contains(medalId.toLowerCase());
    }

    public void addMedal(UUID uuid, String name, String medalId, Long durationMillis) {
        Optional<Cosmetics> opt = repository.findByUuid(uuid);
        Cosmetics cosmetics;
        if (opt.isEmpty()) {
            Profile profile = profileService.getByUuid(uuid)
                    .orElseThrow(() -> new IllegalStateException("Perfil não encontrado para criar cosméticos"));

            cosmetics = Cosmetics.builder()
                    .id(profile.getId())
                    .uuid(uuid)
                    .name(name)
                    .unlockedMedals(new ArrayList<>())
                    .build();
        } else {
            cosmetics = opt.get();
        }

        cosmetics.getUnlockedMedals().removeIf(m -> m.getMedalId().equalsIgnoreCase(medalId));

        Long expiresAt = (durationMillis != null && durationMillis > 0) ? System.currentTimeMillis() + durationMillis : null;

        cosmetics.getUnlockedMedals().add(new UnlockedMedal(medalId.toLowerCase(), System.currentTimeMillis(), expiresAt));
        save(cosmetics);
    }

    public void addMedal(UUID uuid, String name, String medalId) {
        addMedal(uuid, name, medalId, null);
    }

    public void removeMedal(UUID uuid, String medalId) {
        repository.findByUuid(uuid).ifPresent(cosmetics -> {
            if (cosmetics.getUnlockedMedals().removeIf(m -> m.getMedalId().equalsIgnoreCase(medalId))) {
                save(cosmetics);
            }
        });
    }

    private void updateCache(Cosmetics cosmetics) {
        List<String> activeIds = cosmetics.getUnlockedMedals().stream()
                .filter(m -> !m.hasExpired())
                .map(UnlockedMedal::getMedalId)
                .collect(Collectors.toList());
        activeMedalsCache.put(cosmetics.getUuid(), activeIds);
    }

    public List<String> getCachedMedals(UUID uuid) {
        return activeMedalsCache.getOrDefault(uuid, Collections.emptyList());
    }

    private void publishUpdate(Cosmetics cosmetics) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("uuid", cosmetics.getUuid().toString());
            var array = node.putArray("medals");
            cosmetics.getUnlockedMedals().stream()
                    .filter(m -> !m.hasExpired())
                    .forEach(m -> array.add(m.getMedalId()));

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
            activeMedalsCache.put(uuid, medals);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing cosmetics sync", e);
        }
    }
}