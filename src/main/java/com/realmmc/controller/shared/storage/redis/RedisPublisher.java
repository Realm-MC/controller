package com.realmmc.controller.shared.storage.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.storage.redis.packet.RedisPacket;
import redis.clients.jedis.Jedis;

import java.util.logging.Logger;

public final class RedisPublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(RedisPublisher.class.getName());

    private RedisPublisher() {
    }

    public static void publish(RedisPacket packet) {
        if (packet == null) return;
        try {
            String json = MAPPER.writeValueAsString(packet);
            publish(packet.getChannel(), json);
        } catch (JsonProcessingException e) {
            LOGGER.severe("Falha ao serializar pacote Redis (" + packet.getClass().getSimpleName() + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void publish(RedisChannel channel, String message) {
        publish(channel.getName(), message);
    }

    public static void publish(String channel, String message) {
        try (Jedis jedis = RedisManager.getResource()) {
            jedis.publish(channel, message);
        } catch (Exception e) {
            LOGGER.severe("Erro ao publicar mensagem no Redis (Canal: " + channel + "): " + e.getMessage());
        }
    }
}