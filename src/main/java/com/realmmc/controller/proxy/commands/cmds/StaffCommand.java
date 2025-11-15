package com.realmmc.controller.proxy.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.modules.server.data.ServerInfo;
import com.realmmc.controller.modules.server.data.ServerInfoRepository;
import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.RoleType;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.utils.NicknameFormatter;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Cmd(cmd = "staff", aliases = {"equipe"}, onlyPlayer = false)
public class StaffCommand implements CommandInterface {

    private final String requiredPermission = "controller.helper";
    private final String requiredGroupName = "Ajudante";
    private final Logger logger;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final SessionTrackerService sessionTrackerService;
    private final ProfileService profileService;
    private final RoleService roleService;
    private final ServerInfoRepository serverInfoRepository;

    public StaffCommand() {
        this.logger = Logger.getLogger(StaffCommand.class.getName());
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.sessionTrackerService = ServiceRegistry.getInstance().requireService(SessionTrackerService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.serverInfoRepository = new ServerInfoRepository();
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (!sender.hasPermission(requiredPermission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length > 0) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        TaskScheduler.runAsync(() -> {
            try {
                Set<String> onlineUsernames = sessionTrackerService.getOnlineUsernames();
                if (onlineUsernames.isEmpty()) {
                    sendListToSender(sender, Collections.emptyList());
                    return;
                }

                List<CompletableFuture<StaffInfo>> futures = new ArrayList<>();

                for (String username : onlineUsernames) {
                    futures.add(
                            profileService.getByUsername(username)
                                    .map(profile ->
                                            roleService.loadPlayerDataAsync(profile.getUuid())
                                                    .thenApply(sessionData -> {
                                                        if (sessionData != null && sessionData.getPrimaryRole().getType() == RoleType.STAFF) {

                                                            String serverName = sessionTrackerService.getSessionField(profile.getUuid(), "currentServer")
                                                                    .map(id -> serverInfoRepository.findByName(id)
                                                                            .map(ServerInfo::getDisplayName)
                                                                            .orElse(id))
                                                                    .orElse("Desconhecido");

                                                            return new StaffInfo(profile.getUuid(), profile.getName(), sessionData.getPrimaryRole().getWeight(), serverName);
                                                        }
                                                        return null;
                                                    })
                                                    .exceptionally(ex -> {
                                                        logger.log(Level.WARNING, "Erro ao processar role data para " + username, ex);
                                                        return null;
                                                    })
                                    )
                                    .orElse(CompletableFuture.completedFuture(null))
                    );
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                List<StaffInfo> sortedStaff = futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(StaffInfo::getWeight).reversed())
                        .collect(Collectors.toList());

                sendListToSender(sender, sortedStaff);

            } catch (Exception e) {
                if (e.getCause() instanceof IllegalStateException && e.getCause().getMessage().contains("RedisManager not initialized")) {
                    logger.warning("[StaffCommand] Erro: O comando /staff foi executado durante o desligamento do servidor.");
                } else if (e instanceof java.util.concurrent.RejectedExecutionException) {
                    logger.warning("[StaffCommand] Erro: O comando /staff foi executado durante o desligamento do servidor (RejectedExecutionException).");
                } else {
                    logger.log(Level.SEVERE, "Erro ao executar /staff", e);
                }
                Messages.send(sender, MessageKey.COMMAND_ERROR);
                playSound(sender, SoundKeys.ERROR);
            }
        });
    }

    private void sendListToSender(CommandSource sender, List<StaffInfo> sortedStaff) {
        TaskScheduler.runSync(() -> {
            try {
                int count = sortedStaff.size();

                if (count == 0) {
                    Messages.send(sender, MessageKey.STAFF_LIST_HEADER_EMPTY);
                    Messages.send(sender, MessageKey.STAFF_LIST_HEADER_EMPTY_WARNING);
                } else if (count == 1) {
                    Messages.send(sender, MessageKey.STAFF_LIST_HEADER_ONE);
                } else {
                    Messages.send(sender, Message.of(MessageKey.STAFF_LIST_HEADER_MULTIPLE).with("count", count));
                }

                for (StaffInfo info : sortedStaff) {
                    String formattedName = NicknameFormatter.getFullFormattedNick(info.getUuid());

                    String lineFormat = Messages.translate(
                            Message.of(MessageKey.STAFF_LIST_LINE)
                                    .with("staff_member", formattedName)
                                    .with("server_name", info.getServerName())
                    );

                    Component lineComponent = miniMessage.deserialize(lineFormat);

                    Component lineWithClick = lineComponent.clickEvent(
                            ClickEvent.suggestCommand("/btp " + info.getUsername())
                    );

                    sender.sendMessage(lineWithClick);
                }

                Messages.send(sender, MessageKey.STAFF_LIST_FOOTER);
                playSound(sender, SoundKeys.NOTIFICATION);

            } catch (Exception e) {
                if (e instanceof IllegalStateException && e.getMessage().contains("MessagingSDK not initialized")) {
                    logger.warning("[StaffCommand] Falha ao enviar lista de staff (SDK desligado): " + e.getMessage());
                } else {
                    logger.log(Level.SEVERE, "Erro ao enviar a lista de staff para " + sender, e);
                }
            }
        });
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
        }
    }

    private static class StaffInfo {
        private final UUID uuid;
        private final String username;
        private final int weight;
        private final String serverName;

        public StaffInfo(UUID uuid, String username, int weight, String serverName) {
            this.uuid = uuid;
            this.username = username;
            this.weight = weight;
            this.serverName = serverName;
        }
        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public int getWeight() { return weight; }
        public String getServerName() { return serverName; }
    }
}