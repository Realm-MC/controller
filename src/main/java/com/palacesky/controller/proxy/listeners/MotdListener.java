package com.palacesky.controller.proxy.listeners;

import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class MotdListener {

    private static final Logger LOGGER = Logger.getLogger(MotdListener.class.getName());

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing.Builder builder = event.getPing().asBuilder();

        int onlinePlayers = 0;
        int maxPlayers = 500; // Valor padrão caso o Redis falhe

        try (Jedis jedis = RedisManager.getResource()) {
            // 1. Obter contagem de jogadores online (GLOBAL_PLAYER_COUNT)
            String onlineStr = jedis.get(RedisChannel.GLOBAL_PLAYER_COUNT.getName());
            if (onlineStr != null) {
                try {
                    onlinePlayers = Integer.parseInt(onlineStr);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Valor inválido para GLOBAL_PLAYER_COUNT no Redis: " + onlineStr);
                }
            }

            // 2. Obter contagem máxima de slots (GLOBAL_NETWORK_MAX_PLAYERS)
            String maxStr = jedis.get(RedisChannel.GLOBAL_NETWORK_MAX_PLAYERS.getName());
            if (maxStr != null) {
                try {
                    int calculatedMax = Integer.parseInt(maxStr);
                    // Garantir que o máximo não seja 0 se nenhum servidor estiver online
                    if (calculatedMax > 0) {
                        maxPlayers = calculatedMax;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("Valor inválido para GLOBAL_NETWORK_MAX_PLAYERS no Redis: " + maxStr);
                }
            }

            // 3. Aplicar ao MotD
            builder.onlinePlayers(onlinePlayers);
            builder.maximumPlayers(maxPlayers);

            event.setPing(builder.build());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao obter contagens de jogadores/slots do Redis para o MotD.", e);
            // O MotD usará os valores padrão (0 / 500) ou o que o Velocity definir.
        }
    }
}