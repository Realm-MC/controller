package com.palacesky.controller.proxy.listeners;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.PlayerSessionData;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.modules.server.ServerRegistryService;
import com.palacesky.controller.modules.server.data.ServerInfo;
import com.palacesky.controller.modules.server.data.ServerInfoRepository;
import com.palacesky.controller.modules.server.data.ServerStatus;
import com.palacesky.controller.modules.server.data.ServerType;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.auth.AuthenticationGuard;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.session.SessionTrackerService;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Listeners
public class ServerJoinListener {

    private final RoleService roleService;
    private final ServerInfoRepository serverRepo;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final ServerRegistryService serverRegistryService;
    private final SessionTrackerService sessionTrackerService;
    private final Logger logger = Logger.getLogger(ServerJoinListener.class.getName());
    private final MiniMessage miniMessage = MiniMessage.miniMessage();


    public ServerJoinListener() {
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.serverRepo = new ServerInfoRepository();
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.serverRegistryService = ServiceRegistry.getInstance().requireService(ServerRegistryService.class);
        this.sessionTrackerService = ServiceRegistry.getInstance().requireService(SessionTrackerService.class);
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer targetServer = event.getOriginalServer();

        if (targetServer == null) {
            return;
        }

        String newServerName = targetServer.getServerInfo().getName();
        String oldServerName = player.getCurrentServer()
                .map(ServerConnection::getServerInfo)
                .map(com.velocitypowered.api.proxy.server.ServerInfo::getName)
                .orElse(null);

        sessionTrackerService.updateServer(player.getUniqueId(), player.getUsername(), oldServerName, newServerName);

        if (!AuthenticationGuard.isAuthenticated(player.getUniqueId())) {
            if (AuthenticationGuard.isConnecting(player.getUniqueId())) {
                logger.finer("[ServerJoin] Denied connection for " + player.getUsername() + ": Still in CONNECTING state.");
            }
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
            Optional<CompletableFuture<PlayerSessionData>> futureOpt = roleService.getPreLoginFuture(player.getUniqueId());

            if (futureOpt.isPresent()) {
                try {
                    PlayerSessionData loadedData = futureOpt.get().get(3, TimeUnit.SECONDS);
                    sessionDataOpt = Optional.ofNullable(loadedData);
                    logger.info("[ServerJoin] Dados carregados via espera (Future) para " + player.getUsername());
                } catch (Exception e) {
                    logger.warning("[ServerJoin] Timeout ao esperar dados do jogador " + player.getUsername());
                }
            }
        }

        if (sessionDataOpt.isEmpty()) {
            logger.warning("[ServerJoin] Session Data not found for " + player.getUsername() + " when trying to connect to " + targetName + ". Denying.");
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
            boolean hasVipSlot = sessionData.getPrimaryRole().getType() == com.palacesky.controller.shared.role.RoleType.VIP ||
                    playerWeight > roleService.getRole("default").map(r->r.getWeight()).orElse(0);

            if (hasVipSlot) {
                if (currentPlayers >= maxVipSlots) {
                    Message msg = Message.of(MessageKey.SERVER_JOIN_FAIL_FULL_VIP_SLOT)
                            .with("server", serverInfo.getDisplayName())
                            .with("max_players", maxVipSlots);
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    Messages.send(player, msg);
                    sendFailureSound(player);
                    return;
                }
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

        boolean isDynamicLobby = serverInfo.getType() == ServerType.LOBBY &&
                !serverRegistryService.isStaticDefault(serverInfo.getName());

        if ((isDynamicLobby && serverInfo.getStatus() == ServerStatus.STOPPING) || serverInfo.getStatus() != ServerStatus.ONLINE) {

            if (player.getCurrentServer().isPresent()) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());

                MessageKey msgKey;
                if (serverInfo.getStatus() == ServerStatus.OFFLINE) {
                    msgKey = MessageKey.SERVER_CONNECT_OFFLINE;
                } else {
                    msgKey = MessageKey.SERVER_CONNECT_STARTING;
                }

                Messages.send(player, Message.of(msgKey).with("server", serverInfo.getDisplayName()));
                sendFailureSound(player);
                return;
            }

            logger.fine("[ServerJoin] Player " + player.getUsername() + " tried to join server " + serverInfo.getName() + " which is NOT ONLINE. Redirecting...");

            Optional<RegisteredServer> bestLobby = serverRegistryService.getBestLobby();

            if (bestLobby.isPresent() && !bestLobby.get().getServerInfo().getName().equals(targetName)) {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(bestLobby.get()));
                Messages.send(player, Message.of(MessageKey.SERVER_FALLBACK_REDIRECT)
                        .with("server", bestLobby.get().getServerInfo().getName()));
            } else {
                String kickMsg = Messages.translate(Message.of(MessageKey.SERVER_KICK_NETWORK_RESTARTING)
                        .with("server", serverInfo.getDisplayName())
                        .with("status", serverInfo.getStatus()));

                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.disconnect(miniMessage.deserialize(kickMsg));
            }
            return;
        }
    }

    private void sendFailureSound(Player player) {
        soundPlayerOpt.ifPresent(sp -> sp.playSound(player, SoundKeys.ERROR));
    }
}