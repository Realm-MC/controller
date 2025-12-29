package com.palacesky.controller.proxy.listeners;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.motd.MotdService;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import redis.clients.jedis.Jedis;

@Listeners
public class MotdListener {

    private final MotdService motdService;

    private static final int REQUIRED_PROTOCOL = 769;
    private static final String VERSION_NAME = "PalaceSky 1.21.4";

    public MotdListener() {
        this.motdService = ServiceRegistry.getInstance().requireService(MotdService.class);
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing.Builder builder = event.getPing().asBuilder();
        int clientProtocol = event.getConnection().getProtocolVersion().getProtocol();

        int onlinePlayers = 0;
        int maxPlayers = 500;
        try (Jedis jedis = RedisManager.getResource()) {
            String onlineStr = jedis.get(RedisChannel.GLOBAL_PLAYER_COUNT.getName());
            if (onlineStr != null) onlinePlayers = Integer.parseInt(onlineStr);

            String maxStr = jedis.get(RedisChannel.GLOBAL_NETWORK_MAX_PLAYERS.getName());
            if (maxStr != null && Integer.parseInt(maxStr) > 0) maxPlayers = Integer.parseInt(maxStr);
        } catch (Exception ignored) {}

        builder.onlinePlayers(onlinePlayers);
        builder.maximumPlayers(maxPlayers);

        if (clientProtocol != REQUIRED_PROTOCOL) {
            builder.description(motdService.getWrongVersionMotd());
            builder.version(new ServerPing.Version(REQUIRED_PROTOCOL, VERSION_NAME));

            builder.samplePlayers(new ServerPing.SamplePlayer(
                    motdService.getWrongVersionHover().toString(),
                    java.util.UUID.randomUUID()
            ));
        } else {
            builder.description(motdService.getMotdComponent());
        }

        event.setPing(builder.build());
    }
}