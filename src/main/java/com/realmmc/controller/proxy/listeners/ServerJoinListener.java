package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.modules.server.ServerRegistryService;
import com.realmmc.controller.modules.server.data.ServerInfo;
import com.realmmc.controller.modules.server.data.ServerInfoRepository;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class ServerJoinListener {

    private final RoleService roleService;
    private final ServerInfoRepository serverRepo;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final ServerRegistryService serverRegistryService;
    private final Logger logger = Logger.getLogger(ServerJoinListener.class.getName());
    private final MiniMessage miniMessage = MiniMessage.miniMessage();


    public ServerJoinListener() {
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.serverRepo = new ServerInfoRepository();
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.serverRegistryService = ServiceRegistry.getInstance().requireService(ServerRegistryService.class);
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();

        RegisteredServer targetServer = event.getOriginalServer();

        if (!AuthenticationGuard.isAuthenticated(player.getUniqueId())) {
            if (AuthenticationGuard.isConnecting(player.getUniqueId())) {
                return;
            }
            return;
        }

        if (targetServer == null) {
            return;
        }

        com.velocitypowered.api.proxy.server.ServerInfo targetInfo = targetServer.getServerInfo();
        String targetName = targetInfo.getName();

        Optional<ServerInfo> serverInfoOpt = serverRepo.findByName(targetName);

        if (serverInfoOpt.isEmpty()) {
            return;
        }

        ServerInfo serverInfo = serverInfoOpt.get();

        Optional<PlayerSessionData> sessionDataOpt = roleService.getSessionDataFromCache(player.getUniqueId());

        if (sessionDataOpt.isEmpty()) {
            logger.warning("[ServerJoin] Session Data not found for " + player.getUsername() + " when trying to connect to " + targetName);
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            Messages.send(player, Message.of(MessageKey.AUTH_STILL_CONNECTING));
            return;
        }

        PlayerSessionData sessionData = sessionDataOpt.get();
        int playerWeight = sessionData.getPrimaryRole().getWeight();

        if (!serverInfo.getMinGroup().equalsIgnoreCase("default")) {
            Optional<Integer> minGroupWeightOpt = roleService.getRole(serverInfo.getMinGroup()).map(r -> r.getWeight());

            if (minGroupWeightOpt.isPresent() && playerWeight < minGroupWeightOpt.get()) {
                Message msg = Message.of(MessageKey.SERVER_JOIN_FAIL_MIN_GROUP)
                        .with("server", serverInfo.getDisplayName())
                        .with("min_group_display", roleService.getRole(serverInfo.getMinGroup()).map(r -> r.getDisplayName()).orElse(serverInfo.getMinGroup()));

                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                Messages.send(player, msg);
                sendFailureSound(player);
                return;
            }
        }

        int currentPlayers = targetServer.getPlayersConnected().size();

        int maxNormalSlots = serverInfo.getMaxPlayers();
        int maxVipSlots = serverInfo.getMaxPlayersVip();

        if (currentPlayers >= maxNormalSlots) {

            if (sessionData.getPrimaryRole().getType() == com.realmmc.controller.shared.role.RoleType.VIP || playerWeight > roleService.getRole("default").map(r->r.getWeight()).orElse(0)) {

                if (currentPlayers >= maxVipSlots) {
                    Message msg = Message.of(MessageKey.SERVER_JOIN_FAIL_FULL_NO_VIP)
                            .with("server", serverInfo.getDisplayName())
                            .with("max_slots", maxVipSlots);

                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    Messages.send(player, msg);
                    sendFailureSound(player);
                    return;
                }
                Messages.send(player,
                        Message.of(MessageKey.SERVER_JOIN_FAIL_FULL_VIP_SLOT)
                                .with("server", serverInfo.getDisplayName())
                                .with("max_players", maxNormalSlots)
                );

            } else {
                Message msg = Message.of(MessageKey.SERVER_JOIN_FAIL_FULL_NO_VIP)
                        .with("server", serverInfo.getDisplayName())
                        .with("max_slots", maxNormalSlots);

                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                Messages.send(player, msg);
                sendFailureSound(player);
                return;
            }
        }

        if (serverInfo.getType() == com.realmmc.controller.modules.server.data.ServerType.LOBBY_AUTO && serverInfo.getStatus() == com.realmmc.controller.modules.server.data.ServerStatus.STOPPING) {
            Optional<RegisteredServer> bestLobby = serverRegistryService.getBestLobby();
            if (bestLobby.isPresent()) {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(bestLobby.get()));
                Messages.send(player, MessageKey.SERVER_REDIRECT_LOBBY_CLOSED);
                return;
            } else {

                Message msg = Message.of(MessageKey.SERVER_JOIN_FAIL_NO_LOBBY);

                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                Messages.send(player, msg);
                sendFailureSound(player);
            }
        }
    }

    private void sendFailureSound(Player player) {
        soundPlayerOpt.ifPresent(sp -> sp.playSound(player, SoundKeys.ERROR));
    }
}